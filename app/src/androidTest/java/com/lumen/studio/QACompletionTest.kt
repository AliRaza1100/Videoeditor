package com.lumen.studio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.*
import android.widget.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class QACompletionTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    private fun <T> main(block: ()->T): T { val value=AtomicReference<T>();instrumentation.runOnMainSync { value.set(block()) };return value.get() }
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(label: String,test: ()->Boolean) { val until=System.currentTimeMillis()+45000;while(System.currentTimeMillis()<until) { if(main(test)) return;Thread.sleep(100) };fail(label) }
    override fun setUp() { super.setUp();instrumentation.waitForIdleSync();assertTrue(context.packageName.endsWith(".qa"));AppPreferences.setLanguage(context,"en") }
    fun test01ColorSelectionAndCustomMixerInEveryTheme() {
        val previous=AppPreferences.theme(context)
        try { for(theme in listOf("gold","light","dark","violet")) { AppPreferences.setTheme(context,theme);main {
            var selected=Color.WHITE
            val picker=StudioColorPicker(context,Color.WHITE) { selected=it }
            val all=views(picker)
            val red=all.first { it.contentDescription=="Color #E83D55" };red.performClick()
            assertEquals(0xFFE83D55.toInt(),selected);assertTrue(red.isSelected);assertNotNull(red.foreground)
            val hex=all.filterIsInstance<EditText>().single();hex.setText("#123456")
            all.filterIsInstance<Button>().single { it.text.toString()=="Apply color" }.performClick()
            assertEquals(Color.rgb(18,52,86),selected)
            all.first { it.contentDescription=="Open RGB color mixer" }.performClick()
            assertEquals(3,all.filterIsInstance<SeekBar>().size)
        } } } finally { AppPreferences.setTheme(context,previous) }
    }
    fun test02PhotoUnifiedControlsFixedDoneAndEmptySlotGallery() {
        val file=File(context.cacheDir,"qa-completion-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888).also { bitmap -> try { bitmap.eraseColor(Color.GRAY);file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() } }
        val id="qa-completion-${UUID.randomUUID()}";val text=TextLayer(text="Studio")
        ProjectStore.save(context,SavedProject(id,"QA panels",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Photo",false)),photoEditor=true,photoLayout="columns2",ratio="1:1",projectTexts=listOf(text))))
        val editor=instrumentation.startActivitySync(Intent(context,PhotoEditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun all()=views(editor.window.decorView)
        val opened=AtomicReference<Intent>()
        val monitor=object: Instrumentation.ActivityMonitor() { override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? { if(intent.component?.className!=MediaPickerActivity::class.java.name) return null;opened.set(intent);return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null) } }
        try {
            waitFor("Empty collage-slot shortcut missing") { all().any { it.contentDescription=="Add photo to collage slot 2" && it.isShown } }
            instrumentation.addMonitor(monitor)
            main { all().first { it.contentDescription=="Add photo to collage slot 2" }.performClick() }
            assertNotNull(opened.get());assertEquals("photo",opened.get().getStringExtra("mode"))
            main {
                PhotoEditorActivity::class.java.getDeclaredField("selectedText").apply { isAccessible=true }.set(editor,text.id)
                PhotoEditorActivity::class.java.getDeclaredMethod("toolbar").apply { isAccessible=true }.invoke(editor)
                all().filterIsInstance<EditorToolItem>().first { it.title=="Text controls" }.performClick()
                val sliders=all().filterIsInstance<SeekBar>().map { it.contentDescription?.toString().orEmpty() }
                assertTrue(sliders.containsAll(listOf("Size","Opacity","Outline","Shadow","Spacing","Rotate text")))
                assertEquals(3,all().filterIsInstance<ImageButton>().count { it.contentDescription?.toString().orEmpty().startsWith("Align ") })
                val done=all().first { it.contentDescription=="Done: Text controls" }
                assertTrue(done.isShown)
                val panel=PhotoEditorActivity::class.java.getDeclaredField("panel").apply { isAccessible=true }.get(editor) as View
                assertFalse("Done must remain outside scrolling controls",views(panel).contains(done))
                all().filterIsInstance<Button>().first { it.text.toString()=="Font" }.performClick()
                assertTrue(all().any { it.contentDescription?.toString().orEmpty().startsWith("Font: ") && it.isClickable })
                assertTrue(all().any { it.isSelected && it.foreground!=null })
                all().first { it.contentDescription=="Done: Fonts" }.performClick()
            }
        } finally { instrumentation.removeMonitor(monitor);main { editor.finish() };ProjectStore.delete(context,id);file.delete() }
    }
    fun test03VideoMuteRailAndConfirmationActions() {
        val file=File(context.cacheDir,"qa-video-panel-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888).also { bitmap -> try { bitmap.eraseColor(Color.BLUE);file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() } }
        val id="qa-video-panel-${UUID.randomUUID()}"
        ProjectStore.save(context,SavedProject(id,"Video tools",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Photo",false)))))
        val editor=instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { waitFor("Video tools missing") { views(editor.window.decorView).filterIsInstance<EditorToolItem>().any { it.title=="Edit" } };main {
            val all=views(editor.window.decorView)
            val mute=all.filterIsInstance<SourceMuteView>().single();val parent=mute.parent as View
            assertEquals("Timeline track types",parent.contentDescription?.toString().orEmpty())
            val seek=all.filterIsInstance<SeekBar>().first()
            assertTrue("Seekbar row must not contain music or source-mute controls",views(seek.parent as View).none { it is SourceMuteView || it.contentDescription in listOf("Mute soundtrack","Unmute soundtrack") })
            EditorActivity::class.java.getDeclaredMethod("adjustmentPanel").apply { isAccessible=true }.invoke(editor)
            val controls=views(editor.window.decorView)
            assertTrue(controls.any { it.contentDescription=="Done: Adjust" && it.isShown })
            assertTrue(controls.filterIsInstance<SeekBar>().map { it.contentDescription?.toString().orEmpty() }.containsAll(listOf("Brightness","Contrast","Saturation","Sharpness")))
            assertEquals(7,controls.filterIsInstance<Button>().count { it.text.toString()=="Reset" })
        } } finally { main { editor.finish() };ProjectStore.delete(context,id);file.delete() }
    }
}
