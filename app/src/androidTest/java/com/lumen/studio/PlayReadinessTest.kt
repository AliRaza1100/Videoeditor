package com.lumen.studio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class PlayReadinessTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    override fun setUp() {
        super.setUp()
        assertTrue("Run publishing checks only in the isolated -PqaBuild=true package",context.packageName.endsWith(".qa"))
        AppPreferences.setLanguage(context,"en"); AppPreferences.setLight(context,false)
        context.getSharedPreferences("onboarding",0).edit().putString("accepted_terms",TermsConsent.VERSION).putLong("completed_version",1).commit()
    }
    private fun <T> main(block: ()->T): T { val result=AtomicReference<T>(); instrumentation.runOnMainSync { result.set(block()) }; return result.get() }
    private fun views(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).map { view.getChildAt(it) }.flatMap { views(it) } else emptyList()
    private fun all(activity: Activity)=views(activity.findViewById(android.R.id.content))
    private fun waitFor(message: String,seconds: Int=120,condition: ()->Boolean) { val deadline=System.currentTimeMillis()+seconds*1000; while(System.currentTimeMillis()<deadline) { if(main(condition)) return; Thread.sleep(100) }; fail(message) }
    private fun click(activity: Activity,label: String) {
        waitFor("Missing control: $label",15) { all(activity).any { it.isClickable && it.isShown && (it.contentDescription?.toString()==label || it is TextView && it.text.toString()==label) } }
        val target=main { all(activity).first { it.isClickable && it.isShown && (it.contentDescription?.toString()==label || it is TextView && it.text.toString()==label) } }
        main { target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true) }; instrumentation.waitForIdleSync()
        main { assertTrue("Disabled control: $label",target.isEnabled); target.performClick() }
    }
    private fun capture(name: String) { instrumentation.uiAutomation.takeScreenshot()?.let { image -> try { val file=File(context.filesDir,"play-readiness/$name.png"); file.parentFile!!.mkdirs(); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { image.recycle() } } }
    private fun galleryResult(action: String,mime: String?,result: Instrumentation.ActivityResult): Instrumentation.ActivityMonitor {
        val monitor=object: Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = if(intent.action==action && intent.type==mime) result else null
        }
        instrumentation.addMonitor(monitor); return monitor
    }
    fun testNoBroadMediaPermissions() {
        val permissions=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        listOf("android.permission.READ_EXTERNAL_STORAGE","android.permission.WRITE_EXTERNAL_STORAGE","android.permission.MANAGE_EXTERNAL_STORAGE","android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_VISUAL_USER_SELECTED").forEach { assertFalse("Unexpected permission: $it",it in permissions) }
        assertEquals(36,context.applicationInfo.targetSdkVersion)
    }
    fun testFingerStickerResizeAndTextTools() {
        val source=File(context.filesDir,"gesture-background.png")
        val sticker=File(context.filesDir,"gesture-sticker.png")
        for((file,color) in listOf(source to Color.DKGRAY,sticker to Color.GREEN)) {
            Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).also { bitmap -> try { bitmap.eraseColor(color); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() } }
        }
        val id="gesture-${UUID.randomUUID()}"
        val layer=ImageLayer(uri=Uri.fromFile(sticker).toString(),x=0f,y=0f,scale=.3f)
        ProjectStore.save(context,SavedProject(id,"Finger editing",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Background",false)),projectOverlays=listOf(layer))))
        val editor=instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
        fun saved()=ProjectStore.load(context,id)!!.state
        try {
            waitFor("Gesture preview missing",20) { editor.previewRendered }
            val surface=main { all(editor).filterIsInstance<EditorPreviewView>().single() }
            fun pinch(expand: Boolean) {
                val before=saved().projectOverlays!!.single()
                main {
                    val down=android.os.SystemClock.uptimeMillis(); var time=down
                    val cx=surface.width*(before.x+1)/2; val cy=surface.height*(1-before.y)/2
                    fun send(action: Int,span: Float,two: Boolean,dx: Float=0f) {
                        val count=if(two) 2 else 1
                        val properties=Array(count) { index -> android.view.MotionEvent.PointerProperties().apply { this.id=index; toolType=android.view.MotionEvent.TOOL_TYPE_FINGER } }
                        val coords=Array(count) { index -> android.view.MotionEvent.PointerCoords().apply { x=cx+dx+(if(index==0) -span else span); y=cy; pressure=1f; size=1f } }
                        val event=android.view.MotionEvent.obtain(down,time,action,count,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
                        surface.dispatchTouchEvent(event); event.recycle(); time+=32
                    }
                    val start=surface.width*(if(expand) .12f else .28f)
                    send(android.view.MotionEvent.ACTION_DOWN,start,false)
                    send(android.view.MotionEvent.ACTION_POINTER_DOWN or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),start,true)
                    repeat(8) { index -> send(android.view.MotionEvent.ACTION_MOVE,start*(if(expand) 1f+index*.16f else 1f-index*.08f),true,if(expand) index*4f else 0f) }
                    send(android.view.MotionEvent.ACTION_POINTER_UP or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),start,true)
                    send(android.view.MotionEvent.ACTION_UP,start,false)
                }
                waitFor("Gesture did not finish rendering",20) { editor.previewRendered }
            }
            pinch(true)
            val large=saved().projectOverlays!!.single()
            assertTrue("Pinch out did not enlarge sticker: $large",large.scale>.4f)
            assertTrue("Finger drag did not move sticker",large.x>0f)
            pinch(false)
            assertTrue("Pinch in did not shrink sticker",saved().projectOverlays!!.single().scale<large.scale*.8f)
            click(editor,"Back"); click(editor,"Edit"); click(editor,"Text"); click(editor,"Add text")
            assertTrue("New text was not immediately selectable",saved().projectTexts!!.single().text.isNotBlank())
            click(editor,"Font"); click(editor,"Font preset: Serif")
            assertEquals("Serif",saved().projectTexts!!.single().font)
            click(editor,"Style"); click(editor,"Style preset: Bold italic")
            assertEquals("Bold italic",saved().projectTexts!!.single().style)
            click(editor,"Color"); click(editor,"Vivid"); click(editor,"Color preset: Red")
            assertEquals(Color.RED,saved().projectTexts!!.single().color)
            click(editor,"Animation"); click(editor,"Text animation preset: Fade")
            assertEquals("Fade",saved().projectTexts!!.single().animation)
            capture("text-tools-v107")
        } finally { main { editor.finish() }; ProjectStore.delete(context,id); source.delete(); sticker.delete() }
    }
    fun testHomeImportAndRecentProjectActions() {
        val values=android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"lumen-qa-navigation.png")
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            if(Build.VERSION.SDK_INT>=29) put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        val bitmap=Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(Color.CYAN); context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
        val home=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var editor: Activity?=null
        try {
            val action=if(Build.VERSION.SDK_INT>=33) MediaStore.ACTION_PICK_IMAGES else Intent.ACTION_OPEN_DOCUMENT
            for((label,mime) in listOf("New Video Project" to "video/*","Edit Photo. Crop, text & filters" to "image/*")) {
                val native=galleryResult(action,if(mime=="image/*") mime else if(Build.VERSION.SDK_INT<33) "*/*" else null,Instrumentation.ActivityResult(if(mime=="image/*") Activity.RESULT_OK else Activity.RESULT_CANCELED,Intent().setData(uri)))
                val opened=instrumentation.addMonitor(MediaPickerActivity::class.java.name,null,false)
                val editing=instrumentation.addMonitor(EditorActivity::class.java.name,null,false)
                var picker: Activity?=null
                try {
                    click(home,label)
                    picker=opened.waitForActivityWithTimeout(10000) ?: error("Home did not open media selection: $label")
                    assertEquals(if(mime=="video/*") "all" else "photo",picker.intent.getStringExtra("mode"))
                    if(mime=="image/*") {
                        editor=editing.waitForActivityWithTimeout(15000) ?: error("Photo editor did not open directly")
                        waitFor("Imported photo did not render",20) { (editor as EditorActivity).previewRendered }
                        capture("home-photo-editor")
                        main { editor!!.finish() }; editor=null
                    }
                } finally { picker?.let { main { it.finish() } }; instrumentation.removeMonitor(native); instrumentation.removeMonitor(opened); instrumentation.removeMonitor(editing); instrumentation.waitForIdleSync(); Thread.sleep(500) }
            }
            waitFor("Imported photo was not saved",15) { ProjectStore.list(context).any { it.state.clips.any { clip -> clip.name=="lumen-qa-navigation.png" } } }
            val project=ProjectStore.list(context).first { it.state.clips.any { clip -> clip.name=="lumen-qa-navigation.png" } }
            for(page in listOf("Home","Projects")) {
                click(home,page)
                val opened=instrumentation.addMonitor(EditorActivity::class.java.name,null,false)
                try { click(home,"Open project: ${project.name}"); editor=opened.waitForActivityWithTimeout(15000) ?: error("$page did not open saved draft"); assertEquals(project.id,editor!!.intent.getStringExtra("project_id")); main { editor!!.finish() }; editor=null } finally { instrumentation.removeMonitor(opened) }
            }
            // A separate device's private files directory cannot enumerate A's projects.
            val isolated=object: android.content.ContextWrapper(context) { override fun getFilesDir()=File(context.cacheDir,"other-device").apply { mkdirs() } }
            assertTrue(ProjectStore.list(isolated).isEmpty())
            ProjectStore.delete(context,project.id)
        } finally { editor?.let { main { it.finish() } }; main { home.finish() }; context.contentResolver.delete(uri,null,null) }
    }
    fun testPrivacyStorageScreensAndPickerCancellation() {
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitFor("Home did not load") { all(activity).any { it.isClickable && it.contentDescription?.toString()=="Settings" } }
            click(activity,"Settings"); click(activity,"Privacy policy")
            waitFor("Privacy policy text missing") { all(activity).filterIsInstance<TextView>().any { it.text.toString().contains("TEST",true) && it.text.toString().contains("examples") } }
            capture("privacy-policy"); click(activity,"Storage & data")
            waitFor("Data retention explanation missing") { all(activity).filterIsInstance<TextView>().any { it.text.toString().contains("Drafts are kept until you delete them") } }
            capture("storage-data")
        } finally { main { activity.finish() } }
        val pickerAction=if(Build.VERSION.SDK_INT>=33) MediaStore.ACTION_PICK_IMAGES else Intent.ACTION_OPEN_DOCUMENT
        val monitor=instrumentation.addMonitor(IntentFilter(pickerAction).apply { addDataType("image/*"); if(Build.VERSION.SDK_INT<33) addCategory(Intent.CATEGORY_OPENABLE) },Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        val picker=instrumentation.startActivitySync(Intent(context,MediaPickerActivity::class.java).putExtra("mode","photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitFor("Picker cancellation did not recover") { all(picker).filterIsInstance<TextView>().any { it.text.toString().startsWith("No files selected.") } }
            assertTrue(main { all(picker).any { it is TextView && it.text.toString()=="Browse files" && it.isEnabled } })
            capture("selected-files-import")
        } finally { instrumentation.removeMonitor(monitor); main { picker.finish() } }
    }
    fun testNewVideoProjectImportsAndSavesVideo() {
        val source=File(context.cacheDir,"navigation-source.png")
        Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).also { image -> try { image.eraseColor(Color.GREEN); source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { image.recycle() } }
        val renderer=main { RenderedPlayback(context) }; val ready=CountDownLatch(1); val output=AtomicReference<File>(); val error=AtomicReference<Exception>()
        main { renderer.prepare(EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Video fixture",false,imageDurationMs=1000))),{}, { output.set(it); ready.countDown() }, { error.set(it); ready.countDown() }) }
        assertTrue(ready.await(60,TimeUnit.SECONDS)); assertNull(error.get())
        val values=android.content.ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME,"lumen-qa-video.mp4"); put(MediaStore.Video.Media.MIME_TYPE,"video/mp4"); if(Build.VERSION.SDK_INT>=29) put(MediaStore.Video.Media.IS_PENDING,1) }
        val uri=context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)!!
        output.get().inputStream().use { input -> context.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
        val home=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val action=if(Build.VERSION.SDK_INT>=33) MediaStore.ACTION_PICK_IMAGES else Intent.ACTION_OPEN_DOCUMENT
        val native=galleryResult(action,if(Build.VERSION.SDK_INT<33) "*/*" else null,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(uri)))
        val picking=instrumentation.addMonitor(MediaPickerActivity::class.java.name,null,false)
        val editing=instrumentation.addMonitor(EditorActivity::class.java.name,null,false)
        var picker: Activity?=null; var editor: Activity?=null
        try {
            click(home,"New Video Project"); picker=picking.waitForActivityWithTimeout(10000) ?: error("Video picker did not open")
            editor=editing.waitForActivityWithTimeout(15000) ?: error("Video editor did not open directly")
            waitFor("Imported video preview did not render",20) { (editor as EditorActivity).previewRendered }
            main { editor!!.finish() }; editor=null
            waitFor("Video project was not saved",15) { ProjectStore.list(context).any { it.state.clips.any { clip -> clip.video && clip.name=="lumen-qa-video.mp4" } } }
        } finally { editor?.let { main { it.finish() } }; picker?.let { main { it.finish() } }; main { home.finish(); renderer.release() }; instrumentation.removeMonitor(native); instrumentation.removeMonitor(picking); instrumentation.removeMonitor(editing); context.contentResolver.delete(uri,null,null); source.delete() }
    }
    fun testClearEditingDataPreservesOriginalsExportsAndAppearance() {
        assertFalse(VideoExport.busy); assertFalse(SoundLibrary.busy)
        val original=File(context.getExternalFilesDir(null),"original-preserved.png").apply { writeText("original") }
        val exported=File(context.getExternalFilesDir(null),"export-preserved.mp4").apply { writeText("export") }
        listOf("imports","sounds").forEach { folder -> File(context.filesDir,"$folder/private-copy").apply { parentFile!!.mkdirs(); writeText("private") } }
        File(context.filesDir,"sticker-test.png").writeText("generated")
        File(context.cacheDir,"playback/private.mp4").apply { parentFile!!.mkdirs(); writeText("cached") }
        ProjectStore.save(context,SavedProject("clear-test","Private draft",1,EditorState(listOf(EditorClip(Uri.fromFile(original).toString(),"Original",false)))))
        val previousEpoch=EditingStorage.epoch(context)
        AppPreferences.setLight(context,true)
        EditingStorage.clear(context)
        assertTrue(original.exists()); assertTrue(exported.exists()); assertTrue(AppPreferences.light(context)); assertTrue(ProjectStore.list(context).isEmpty())
        assertFalse(File(context.filesDir,"imports").exists()); assertFalse(File(context.filesDir,"sounds").exists()); assertFalse(File(context.filesDir,"sticker-test.png").exists()); assertFalse(File(context.cacheDir,"playback/private.mp4").exists())
        ProjectStore.save(context,SavedProject("stale-save","Old editor",2,EditorState(emptyList())),previousEpoch)
        assertTrue("A stale editor resurrected cleared data",ProjectStore.list(context).isEmpty())
        original.delete(); exported.delete()
    }
    fun testRenderedPlaybackCancelAndCacheReuse() {
        val source=File(context.filesDir,"proxy-test.png")
        Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).also { image -> try { image.eraseColor(Color.RED); source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { image.recycle() } }
        val state=EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Proxy fixture",false,imageDurationMs=1000,filter="Mono")))
        val renderer=main { RenderedPlayback(context) }; val finished=CountDownLatch(1); val result=AtomicReference<File>(); val error=AtomicReference<Exception>(); val stale=CountDownLatch(1)
        try {
            main {
                renderer.prepare(state,{}, { stale.countDown() }, { stale.countDown() }); renderer.cancel()
                renderer.prepare(state,{}, { result.set(it); finished.countDown() }, { error.set(it); finished.countDown() })
            }
            assertTrue("Playback proxy timed out",finished.await(120,TimeUnit.SECONDS)); assertNull(error.get()); assertNotNull(result.get()); assertTrue(result.get().length()>0)
            assertEquals("Cancelled generation delivered a callback",1L,stale.count)
            var reused: File?=null
            main { renderer.prepare(state,{}, { reused=it }, { fail(it.message) }) }
            assertEquals("Unchanged composition was not immediately reused",result.get(),reused)
        } finally { main { renderer.release() }; source.delete() }
    }
    fun testOnDeviceExportAndEditedEditorPlayback() {
        val check=instrumentation.startActivitySync(Intent(context,PlaybackCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as PlaybackCheckActivity
        var editor: EditorActivity?=null
        try {
            click(check,"Run video check"); waitFor("Encoded frame/audio check did not pass") { check.checkPassed }; capture("video-check-passed")
            val monitor=instrumentation.addMonitor(EditorActivity::class.java.name,null,false)
            try { click(check,"Open sample in editor"); editor=monitor.waitForActivityWithTimeout(15000) as? EditorActivity ?: error("Sample editor did not open") } finally { instrumentation.removeMonitor(monitor) }
            val current=editor!!
            waitFor("Paused edited frame was not visible") { current.previewRendered }
            click(current,"Play or pause")
            waitFor("Edited playback did not render and advance") {
                val still=all(current).filterIsInstance<ImageView>().first { it.contentDescription=="Processed preview" }
                still.visibility!=View.VISIBLE && all(current).filterIsInstance<TextView>().any { it.text.toString().startsWith("00:01 / 00:02") }
            }
            capture("edited-playback")
            click(current,"Play or pause")
            val surface=main { all(current).filterIsInstance<EditorPreviewView>().single() }
            val frame=main { surface.bitmap } ?: error("No decoded playback pixels")
            try { val c=frame.getPixel(frame.width/8,frame.height/8); assertTrue("Decoded playback frame was black",Color.red(c)+Color.green(c)+Color.blue(c)>60) } finally { frame.recycle() }
            val previewSize=main { surface.width to surface.height }
            click(current,"Filters")
            waitFor("Filter previews are too small",15) { all(current).filterIsInstance<PresetThumbnail>().any { it.width>=Ui.dp(context,108) && it.height>=Ui.dp(context,96) } }
            assertEquals("Opening filters resized the preview",previewSize,main { surface.width to surface.height })
            Thread.sleep(350); capture("larger-filters")
            click(current,"Back to tools"); click(current,"Edit"); click(current,"Effects")
            waitFor("Effect previews are too small",15) { all(current).filterIsInstance<PresetThumbnail>().any { it.width>=Ui.dp(context,108) && it.height>=Ui.dp(context,96) } }
            assertEquals("Opening effects resized the preview",previewSize,main { surface.width to surface.height })
            Thread.sleep(350); capture("larger-effects")
            click(current,"EXPORT")
            waitFor("Export top sheet missing",15) { current.toolsDialog?.isShowing==true }
            val sheet=main { current.toolsDialog!! }
            assertEquals(android.view.Gravity.TOP,main { sheet.window!!.attributes.gravity })
            waitFor("Resolution top sheet missing",15) { current.toolsDialog?.let { views(it.window!!.decorView).filterIsInstance<TextView>().any { text -> text.text.toString()=="720p" } }==true }
            Thread.sleep(350); capture("top-export-options"); main { current.toolsDialog!!.dismiss() }
        } finally { editor?.let { current -> main { current.finish() } }; main { check.finish() } }
    }
}
