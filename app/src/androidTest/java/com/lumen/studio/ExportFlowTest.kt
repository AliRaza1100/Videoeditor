package com.lumen.studio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class ExportFlowTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    private fun <T> main(block: ()->T): T { val result=AtomicReference<T>(); instrumentation.runOnMainSync { result.set(block()) }; return result.get() }
    private fun views(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun waitFor(label: String,block: ()->Boolean) { val end=System.currentTimeMillis()+60000; while(System.currentTimeMillis()<end) { if(main(block)) return; Thread.sleep(100) }; fail(label) }
    override fun setUp() { super.setUp(); check(context.packageName.endsWith(".qa")); AppPreferences.setLanguage(context,"en") }
    private fun fixture(): File {
        val file=File(context.filesDir,"export-resolution-test.png")
        Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).also { bitmap -> try { bitmap.eraseColor(Color.MAGENTA); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() } }
        return file
    }
    fun testExportOpensReadableResolutionsInEveryTheme() {
        val source=fixture()
        try { for(theme in listOf("light","dark","violet","gold")) {
            AppPreferences.setTheme(context,theme)
            val id="export-ui-${UUID.randomUUID()}"
            ProjectStore.save(context,SavedProject(id,"Export UI",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Photo",false)))))
            val editor=instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
            val picked=AtomicReference<Intent>()
            val monitor=object: Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if(intent.action!=Intent.ACTION_CREATE_DOCUMENT) return null
                    picked.set(intent); return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                waitFor("Preview missing") { editor.previewRendered }
                main { views(editor.window.decorView).filterIsInstance<TextView>().first { it.text.toString()=="EXPORT" }.performClick() }
                waitFor("Direct resolution sheet missing") { editor.toolsDialog?.isShowing==true }
                val sheet=main { editor.toolsDialog!! }
                val options=main { views(sheet.window!!.decorView).filterIsInstance<TextView>().filter { it.isClickable && it.text.toString() in listOf("360p","480p","720p","1020p","2000p") } }
                assertEquals(5,options.size)
                main { options.forEach { assertEquals("Unreadable label in theme=$theme",StudioDesign.primaryText(context),it.currentTextColor); assertTrue(it.isEnabled) }; options.first { it.text.toString()=="720p" }.performClick() }
                waitFor("Destination picker missing") { picked.get()!=null }
                assertEquals("video/mp4",picked.get().type)
                assertTrue(picked.get().getStringExtra(Intent.EXTRA_TITLE)!!.contains("720p"))
            } finally { instrumentation.removeMonitor(monitor); main { editor.toolsDialog?.dismiss(); editor.finish() }; ProjectStore.delete(context,id) }
        } } finally { source.delete(); AppPreferences.setLight(context,false) }
    }
    fun testEncodedResolutionNeverSilentlyFallsBack() {
        val source=fixture()
        val state=EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Resolution fixture",false,imageDurationMs=500)),ratio="9:16")
        try { for(resolution in listOf(360,480,720,1020,2000)) {
            val destination=File(context.cacheDir,"resolution-$resolution.mp4").apply { delete() }
            try {
                main { VideoExport.start(context,"resolution-test",state,Uri.fromFile(destination),resolution) }
                waitFor("${resolution}p export timed out") { !VideoExport.busy }
                if(VideoExport.progress!=100) {
                    assertTrue(VideoExport.message.startsWith("Export failed:"))
                    assertFalse("A failed resolution was copied to the destination",destination.exists())
                    assertTrue("Baseline ${resolution}p must encode on the test device: ${VideoExport.message}",resolution>720)
                    continue
                }
                val plan=TimelineEngine.build(context,state,exportResolution=resolution)
                val retriever=MediaMetadataRetriever()
                try { retriever.setDataSource(destination.absolutePath)
                    assertEquals(plan.width,retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt())
                    assertEquals(plan.height,retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt())
                } finally { retriever.release() }
            } finally { main { if(VideoExport.busy) VideoExport.cancel() }; destination.delete() }
        } } finally { source.delete() }
    }
}
