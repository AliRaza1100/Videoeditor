package com.lumen.studio

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
class SqaBugRegressionTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    private fun <T> main(block: ()->T): T { val value=AtomicReference<T>(); instrumentation.runOnMainSync { value.set(block()) }; return value.get() }
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun waitFor(label: String,test: ()->Boolean) { val end=System.currentTimeMillis()+20000; while(System.currentTimeMillis()<end) { if(main(test)) return; Thread.sleep(100) }; fail(label) }
    private fun click(editor: EditorActivity,name: String) {
        waitFor("Missing control $name") { views(editor.window.decorView).any { it.isClickable && (it.contentDescription==name || it is TextView && it.text.toString()==name) } }
        main { val view=views(editor.window.decorView).first { it.isClickable && (it.contentDescription==name || it is TextView && it.text.toString()==name) }; view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true); view.performClick() }
    }
    private fun fullscreenIcon(editor: EditorActivity,label: String,resource: Int): Boolean {
        val control=views(editor.window.decorView).filterIsInstance<ImageButton>().firstOrNull { it.contentDescription==label } ?: return false
        val params=control.layoutParams as? FrameLayout.LayoutParams ?: return false
        if(params.gravity!=(Gravity.BOTTOM or Gravity.RIGHT)) return false
        val actual=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
        val expected=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
        val drawable=control.drawable; val previous=Rect(drawable.bounds)
        return try {
            drawable.setBounds(0,0,48,48); drawable.draw(Canvas(actual))
            context.getDrawable(resource)!!.mutate().apply { setTint(Color.WHITE); setBounds(0,0,48,48); draw(Canvas(expected)) }
            actual.sameAs(expected)
        } finally { drawable.bounds=previous; actual.recycle(); expected.recycle() }
    }
    fun testTC03AudioSelectionBorderAndSwitching() {
        assertTrue(context.packageName.endsWith(".qa")); AppPreferences.setTheme(context,"dark"); AppPreferences.setLanguage(context,"en")
        val image=File(context.cacheDir,"sqa-audio-background.png")
        val audio=File(context.cacheDir,"sqa-audio-track.wav")
        Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).also { bitmap -> try { bitmap.eraseColor(Color.BLUE); image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() } }
        audio.outputStream().use { it.write(waveFileHeader(48000*2*4,48000,1)); it.write(ByteArray(48000*2*4)) }
        val local=SoundLayer(uri=Uri.fromFile(audio).toString(),name="Clip sound",endMs=4000)
        val global=SoundLayer(uri=Uri.fromFile(audio).toString(),name="Project sound",endMs=4000)
        val id="sqa-audio-${UUID.randomUUID()}"
        ProjectStore.save(context,SavedProject(id,"Audio selection",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(image).toString(),"Image",false,imageDurationMs=6000,sounds=listOf(local))),projectSounds=listOf(global))))
        val editor=instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
        fun regions()=views(editor.window.decorView).filterIsInstance<TimelineRegionView>()
        fun region(name: String)=regions().first { it.contentDescription.toString().contains(name) }
        try {
            waitFor("Audio fixture did not load") { editor.previewRendered && regions().size>=2 }
            main { region("Project sound").performClick() }
            main {
                assertTrue(region("Project sound").isSelected); assertFalse(region("Clip sound").isSelected)
                val selected=region("Project sound"); val bitmap=Bitmap.createBitmap(400,120,Bitmap.Config.ARGB_8888)
                try {
                    selected.layout(0,0,400,120); selected.draw(Canvas(bitmap))
                    assertEquals("Selected audio border is not the theme accent",StudioDesign.accent(context),bitmap.getPixel(200,Ui.dp(context,1)))
                } finally { bitmap.recycle() }
                region("Clip sound").performClick()
            }
            main { assertTrue(region("Clip sound").isSelected); assertFalse(region("Project sound").isSelected) }
            click(editor,"Back")
            main { assertTrue("Audio border remained after deselect",regions().none { it.isSelected }) }
            main { region("Project sound").performClick() }
            click(editor,"Back")
            val video=main { views(editor.window.decorView).first { it.contentDescription.toString().startsWith("Clip 1:") && it.isClickable } }
            main { video.performClick(); assertTrue("Audio border remained when selecting video",regions().none { it.isSelected }) }
        } finally { main { editor.finish() }; ProjectStore.delete(context,id); image.delete(); audio.delete() }
    }
    fun testTC01FullscreenIconAndTC02EditableFrame() {
        assertTrue(context.packageName.endsWith(".qa")); AppPreferences.setTheme(context,"dark"); AppPreferences.setLanguage(context,"en")
        val file=File(context.cacheDir,"sqa-frame-source.png")
        Bitmap.createBitmap(180,100,Bitmap.Config.ARGB_8888).also { image -> try { image.eraseColor(Color.RED); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { image.recycle() } }
        val id="sqa-${UUID.randomUUID()}"
        val layer=VisualLayer(kind="effect",name="Frame",startMs=0,endMs=4000)
        val clip=EditorClip(Uri.fromFile(file).toString(),"Landscape",false,imageDurationMs=6000)
        val state=EditorState(listOf(clip),ratio="9:16",projectVisuals=listOf(layer))
        val preview=StillPreviewRenderer.render(context,state,1000)
        try {
            val bounds=FrameEffectRenderer.bounds(context,clip,layer,preview.width,preview.height)
            assertTrue("Frame touches letterboxing",bounds.top>100)
            val border=preview.getPixel(1,((bounds.top+bounds.bottom)/2).toInt())
            assertTrue("Frame is not aligned with visible video edge",Color.red(border)>230 && Color.green(border)>230 && Color.blue(border)>230)
            assertEquals("Frame incorrectly surrounds the output canvas",Color.BLACK,preview.getPixel(preview.width/2,1))
        } finally { preview.recycle() }
        ProjectStore.save(context,SavedProject(id,"SQA regression",System.currentTimeMillis(),state))
        val editor=instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
        try {
            waitFor("Preview not ready") { editor.previewRendered }
            click(editor,"Enter fullscreen preview")
            waitFor("Collapse icon missing") { fullscreenIcon(editor,"Exit fullscreen preview",R.drawable.ph_arrows_in) }
            click(editor,"Exit fullscreen preview")
            waitFor("Expand icon not restored") { fullscreenIcon(editor,"Enter fullscreen preview",R.drawable.ph_arrows_out) }
            waitFor("Frame timeline region missing") { views(editor.window.decorView).filterIsInstance<TimelineRegionView>().any { it.contentDescription.toString().startsWith("effect: Frame") } }
            main { views(editor.window.decorView).filterIsInstance<TimelineRegionView>().first { it.contentDescription.toString().startsWith("effect: Frame") }.performClick() }
            click(editor,"Resize")
            waitFor("Resize slider missing") { views(editor.toolPanel).filterIsInstance<SeekBar>().any { it.width>0 && it.isShown } }
            main {
                val bar=views(editor.toolPanel).filterIsInstance<SeekBar>().single(); val down=android.os.SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP)) { val event=MotionEvent.obtain(down,down+16,action,bar.width*.3f,bar.height/2f,0); bar.dispatchTouchEvent(event); event.recycle() }
            }
            waitFor("Frame size did not persist") { ProjectStore.load(context,id)!!.state.projectVisuals!!.single().frameScale!=null }
            click(editor,"Frame style"); click(editor,"Rounded")
            assertEquals("Rounded",ProjectStore.load(context,id)!!.state.projectVisuals!!.single().frameStyle)
            click(editor,"Frame color"); click(editor,"Vivid"); click(editor,"Frame color preset: Yellow")
            assertEquals(Color.YELLOW,ProjectStore.load(context,id)!!.state.projectVisuals!!.single().frameColor)
            click(editor,"Back to tools"); click(editor,"Delete")
            assertTrue(ProjectStore.load(context,id)!!.state.projectVisuals.orEmpty().isEmpty())
        } finally { main { editor.finish() }; ProjectStore.delete(context,id); file.delete() }
    }
}
