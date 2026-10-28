package com.lumen.studio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.*
import android.widget.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class BugSheetRegressionTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    private fun <T> main(block: ()->T): T { val value=AtomicReference<T>();instrumentation.runOnMainSync { value.set(block()) };return value.get() }
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(label: String,timeout: Long=30000,test: ()->Boolean) { val end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end) { if(main(test)) return;Thread.sleep(100) };fail(label) }
    override fun setUp() { super.setUp();instrumentation.waitForIdleSync();assertTrue(context.packageName.endsWith(".qa"));AppPreferences.setLanguage(context,"en") }
    private fun photo(): File=File(context.cacheDir,"bug-sheet-${UUID.randomUUID()}.png").also { file -> Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888).also { try { it.eraseColor(Color.DKGRAY);file.outputStream().use { out -> it.compress(Bitmap.CompressFormat.PNG,100,out) } } finally { it.recycle() } } }
    fun testHomeHighlightSortAndProjectTypeIconsInEveryTheme() {
        val file=photo();val photoId="bug-photo-${UUID.randomUUID()}";val videoId="bug-video-${UUID.randomUUID()}"
        val clip=EditorClip(Uri.fromFile(file).toString(),"Photo",false)
        ProjectStore.save(context,SavedProject(photoId,"Bug photo",System.currentTimeMillis(),EditorState(listOf(clip),photoEditor=true)))
        ProjectStore.save(context,SavedProject(videoId,"Bug video",System.currentTimeMillis(),EditorState(listOf(clip),photoEditor=false)))
        try { for(theme in listOf("gold","light","dark","violet")) {
            AppPreferences.setTheme(context,theme)
            main {
                val home=HomeView(context) {}
                val selected=views(home).single { it.contentDescription=="Home" && it.isClickable }
                assertTrue(selected.isSelected);assertNotNull("Home needs a selection background in $theme",selected.background)
                val projects=views(ProjectsView(context) {})
                assertTrue(projects.any { it.contentDescription=="Photo project" && it is ImageView })
                assertTrue(projects.any { it.contentDescription=="Video project" && it is ImageView })
                val sort=projects.filterIsInstance<TextView>().single { it.contentDescription=="Sort projects" }
                assertEquals("Sort: Newest",sort.text.toString())
            }
        } } finally { ProjectStore.delete(context,photoId);ProjectStore.delete(context,videoId);file.delete();AppPreferences.setTheme(context,"gold") }
    }
    fun testTightTextBackgroundPaddingAndFormatting() {
        fun draw(padding: Float)=Bitmap.createBitmap(360,240,Bitmap.Config.ARGB_8888).also { TextPresets.draw(Canvas(it),TextLayer(text="A",size=40f,style="Regular",color=Color.WHITE,backgroundColor=Color.RED,backgroundPadding=padding,shadow=false,bold=true,italic=true,underline=true),360,240,750) }
        fun redWidth(bitmap: Bitmap): Int { var left=bitmap.width;var right=-1;for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) if(bitmap.getPixel(x,y)==Color.RED) { left=minOf(left,x);right=maxOf(right,x) };return right-left+1 }
        val tight=draw(4f);val padded=draw(24f)
        try { assertTrue("Background must fit one letter",redWidth(tight) in 15..100);assertTrue("Padding must resize the highlight",redWidth(padded)>=redWidth(tight)+35) } finally { tight.recycle();padded.recycle() }
    }
    fun testPhotoCropDoneHandlesAndLastPhotoRemovalUndo() {
        val file=photo();val id="bug-workspace-${UUID.randomUUID()}"
        ProjectStore.save(context,SavedProject(id,"Bug workspace",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Photo",false)),photoEditor=true,projectTexts=listOf(TextLayer(text="A",size=12f)))))
        val editor=instrumentation.startActivitySync(Intent(context,PhotoEditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun all()=views(editor.window.decorView)
        fun tool(title: String)=all().filterIsInstance<EditorToolItem>().first { it.title==title }
        try {
            waitFor("Photo preview not ready") { all().filterIsInstance<ImageView>().first { it.contentDescription=="Edited photo preview" }.drawable!=null }
            main {
                val history=all().filterIsInstance<ImageButton>().filter { it.contentDescription in listOf("Undo","Redo") }
                assertEquals(2,history.size);history.forEach { assertEquals(Ui.dp(context,48),it.width);assertEquals(Ui.dp(context,48),it.height) }
                val image=all().filterIsInstance<ImageView>().first { it.contentDescription=="Edited photo preview" }
                val now=android.os.SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) { val event=MotionEvent.obtain(now,now,action,image.width/2f,image.height/2f,0);image.dispatchTouchEvent(event);event.recycle() }
            }
            waitFor("Text handles not selected") { all().any { it.contentDescription=="Delete selected photo text" && it.visibility==View.VISIBLE } }
            main {
                val handles=all().filterIsInstance<ImageButton>().filter { it.contentDescription in listOf("Delete selected photo text","Rotate selected photo text") }
                val rects=handles.map { Rect().also { r -> it.getGlobalVisibleRect(r) } }
                assertFalse("Single-letter text handles overlap",Rect.intersects(rects[0],rects[1]))
                tool("Done").performClick();tool("Crop").performClick()
            }
            waitFor("Fixed crop Done missing") { all().any { it.contentDescription=="Confirm photo crop" && it.isShown } }
            main { val done=all().first { it.contentDescription=="Confirm photo crop" };assertTrue(done.width>0);done.performClick();assertTrue(all().none { it is CropEditorView });tool("Remove photo").performClick() }
            waitFor("Remove confirmation missing") { android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { views(it) }.filterIsInstance<Button>().any { it.text.toString()=="Remove" && it.isShown } }
            main { android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { views(it) }.filterIsInstance<Button>().first { it.text.toString()=="Remove" && it.isShown }.performClick() }
            waitFor("Last photo was not removed") { all().filterIsInstance<TextView>().any { it.text.toString().startsWith("No photos.") } }
            main { assertTrue(all().filterIsInstance<EditorToolItem>().any { it.title=="Add photos" });all().first { it.contentDescription=="Undo" }.performClick() }
            waitFor("Undo did not restore the photo") { all().filterIsInstance<EditorToolItem>().any { it.title=="Crop" } }
        } finally { main { editor.finish() };ProjectStore.delete(context,id);file.delete() }
    }
    fun testLeftSoundButtonOpensLibrary() {
        val file=photo();val id="bug-sound-${UUID.randomUUID()}"
        ProjectStore.save(context,SavedProject(id,"Sound",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Photo",false)))))
        val editor=instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val captured=AtomicReference<Intent>()
        val monitor=object: Instrumentation.ActivityMonitor() { override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? { if(intent.component?.className!=SoundLibraryActivity::class.java.name) return null;captured.set(intent);return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null) } }
        instrumentation.addMonitor(monitor)
        try { waitFor("Left sound control missing") { views(editor.window.decorView).any { it is ImageButton && it.contentDescription=="Add sound" && it.isClickable } };main { views(editor.window.decorView).first { it is ImageButton && it.contentDescription=="Add sound" }.performClick() };waitFor("Sound library did not open") { captured.get()!=null } }
        finally { instrumentation.removeMonitor(monitor);main { editor.finish() };ProjectStore.delete(context,id);file.delete() }
    }
    fun testPhotoOnlyNewProjectRoutesToPhotoEditorAndRemindersSwitchOff() {
        TermsConsent.setAccepted(context,true)
        context.getSharedPreferences("onboarding",0).edit().putLong("completed_version",1).commit()
        val home=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val file=photo();val captured=AtomicReference<Intent>()
        val monitor=object: Instrumentation.ActivityMonitor() { override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? { if(intent.component?.className !in listOf(PhotoEditorActivity::class.java.name,EditorActivity::class.java.name)) return null;captured.set(intent);return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null) } }
        val old=Reminders.enabled(context)
        try {
            waitFor("Home not ready") { views(home.window.decorView).any { it is BottomNavigation } }
            instrumentation.addMonitor(monitor)
            main {
                val method=MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }
                method.invoke(home,100,Activity.RESULT_OK,Intent().putStringArrayListExtra("uris",arrayListOf(Uri.fromFile(file).toString())).putStringArrayListExtra("names",arrayListOf("Photo")).putExtra("videos",booleanArrayOf(false)))
            }
            assertEquals(PhotoEditorActivity::class.java.name,captured.get()?.component?.className)
            main {
                Reminders.setEnabled(context,true)
                home.setContentView(SettingsView(home) {})
                val toggle=views(home.window.decorView).filterIsInstance<Switch>().first { it.contentDescription=="Editing reminders" }
                assertTrue(toggle.isChecked);toggle.performClick();assertFalse(toggle.isChecked);assertFalse(Reminders.enabled(context))
            }
        } finally { instrumentation.removeMonitor(monitor);main { home.finish() };Reminders.setEnabled(context,old);Reminders.configure(context);file.delete() }
    }
    fun testFourTimesSpeedRetainsEndOfSourceVideoWithAndWithoutAudio() {
        val source=File(context.cacheDir,"speed-source-${UUID.randomUUID()}.mp4")
        RenderPipelineTest().createVideo(source,true)
        val audio=File(context.cacheDir,"speed-audio-${UUID.randomUUID()}.wav")
        audio.outputStream().use { out -> val bytes=ByteArray(48000*2*3);for(i in 0 until bytes.size/2) { val value=(kotlin.math.sin(i*2*Math.PI*440/48000)*6000).toInt();bytes[i*2]=value.toByte();bytes[i*2+1]=(value shr 8).toByte() };out.write(waveFileHeader(bytes.size,48000,1));out.write(bytes) }
        val withAudio=File(context.cacheDir,"speed-av-${UUID.randomUUID()}.mp4")
        fun export(state: EditorState,destination: File) {
            main { VideoExport.start(context,"bug-speed",state,Uri.fromFile(destination),360) }
            waitFor("Speed export timed out",90000) { !VideoExport.busy }
            assertEquals("Speed export failed: ${VideoExport.message}",100,VideoExport.progress)
        }
        try {
            export(EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Source",true)),projectSounds=listOf(SoundLayer(uri=Uri.fromFile(audio).toString(),name="Tone",endMs=3000))),withAudio)
            for(input in listOf(source,withAudio)) {
                val output=File(context.cacheDir,"speed-output-${UUID.randomUUID()}.mp4")
                val clip=EditorClip(Uri.fromFile(input).toString(),"Source",true,startMs=300,endMs=2700,speedCurve=listOf(4f,4f),speedPositions=listOf(0f,1f),catalogAdjustments=mapOf("video.sharpness" to .4f))
                try {
                    assertEquals(600L,ClipEdits.duration(clip))
                    export(EditorState(listOf(clip)),output)
                    val retriever=MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(output.absolutePath)
                        val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                        assertTrue("4x duration must be about 600 ms; got $duration",duration in 450..850)
                        val first=retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST)!!
                        val last=retriever.getFrameAtTime(duration*850,MediaMetadataRetriever.OPTION_CLOSEST)!!
                        try { val a=first.getPixel(first.width/2,first.height/2);val b=last.getPixel(last.width/2,last.height/2);assertTrue("First source content missing",Color.red(a)>Color.blue(a)+70);assertTrue("4x speed trimmed the blue end of the source",Color.blue(b)>Color.red(b)+70) } finally { first.recycle();last.recycle() }
                    } finally { retriever.release() }
                } finally { output.delete() }
            }
        } finally { main { if(VideoExport.busy) VideoExport.cancel() };source.delete();audio.delete();withAudio.delete() }
    }
}
