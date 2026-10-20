package com.lumen.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import java.io.File
import java.util.UUID

@Suppress("DEPRECATION")
class EditorFeedbackTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).mapNotNull { v.getChildAt(it) }.flatMap { views(it) } else emptyList()
    private fun all(a: Activity)=views(a.findViewById(android.R.id.content))
    private fun waitFor(condition: ()->Boolean) { repeat(500) { if(condition()) return; Thread.sleep(80) }; fail("Editor feedback did not become ready") }
    private fun start(id: String,state: EditorState): EditorActivity {
        AppPreferences.setLight(context,false); AppPreferences.setLanguage(context,"en")
        ProjectStore.save(context,SavedProject(id,"Feedback test",System.currentTimeMillis(),state))
        return instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
    }
    private fun find(a: Activity,name: String)=all(a).firstOrNull { it.isShown && it.isClickable && (it.contentDescription==name || it is TextView && it.text.toString()==name) }
    private fun click(a: Activity,name: String) {
        waitFor { find(a,name)?.let { it.width>0 && it.height>0 }==true }
        val view=find(a,name)!!
        instrumentation.runOnMainSync { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
        Thread.sleep(100)
        assertTrue("Disabled $name",view.isEnabled)
        instrumentation.runOnMainSync { view.performClick() }
    }
    private fun snapshot(editor: EditorActivity)=((all(editor).filterIsInstance<ImageView>().first { it.contentDescription=="Processed preview" }).drawable as? BitmapDrawable)?.bitmap
    private fun greenX(image: Bitmap): Float {
        var sum=0L; var count=0
        for(y in 0 until image.height step 2) for(x in 0 until image.width step 2) { val c=image.getPixel(x,y); if(Color.green(c)>100 && Color.green(c)>Color.red(c)+50 && Color.green(c)>Color.blue(c)+40) { sum+=x; count++ } }
        return if(count>10) sum.toFloat()/count else -1f
    }
    private fun gesture(action: Int,down: Long,x: Float,y: Float) {
        val event=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0).apply { source=android.view.InputDevice.SOURCE_TOUCHSCREEN }
        assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)); event.recycle()
    }
    private fun fixture(name: String)=File(context.filesDir,"render-tests/$name").apply { assertTrue("Missing renderer fixture: $name",exists()) }
    fun testSliderAppliesBeforeReleaseAndPreviewStaysFixed() {
        val source=File(context.cacheDir,"instant-tools.png")
        Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888).also { image ->
            image.eraseColor(Color.rgb(170,120,90)); source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
        }
        val id="instant-${UUID.randomUUID()}"
        val editor=start(id,EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Instant image",false))))
        try {
            waitFor { editor.previewRendered && snapshot(editor)!=null }
            val surface=all(editor).filterIsInstance<EditorPreviewView>().single()
            val original=Rect(); instrumentation.runOnMainSync { surface.getGlobalVisibleRect(original) }
            click(editor,"Edit"); click(editor,"Opacity")
            waitFor { editor.toolPanel.isShown }
            val bar=views(editor.toolPanel).filterIsInstance<SeekBar>().single()
            val down=android.os.SystemClock.uptimeMillis()
            fun touch(action: Int,at: Long) { val event=MotionEvent.obtain(down,at,action,bar.width*.35f,bar.height/2f,0); try { bar.dispatchTouchEvent(event) } finally { event.recycle() } }
            instrumentation.runOnMainSync {
                touch(MotionEvent.ACTION_DOWN,down)
                touch(MotionEvent.ACTION_MOVE,down+16)
            }
            waitFor { ProjectStore.load(context,id)!!.state.clips[0].opacity<.8f && snapshot(editor)?.let { Color.red(it.getPixel(it.width/2,it.height/2))<150 }==true }
            val editing=Rect(); instrumentation.runOnMainSync { surface.getGlobalVisibleRect(editing) }
            assertEquals("Opening/editing tools resized the preview",original,editing)
            assertNull(find(editor,"Done")); assertNull(find(editor,"Apply"))
            instrumentation.runOnMainSync { touch(MotionEvent.ACTION_UP,down+32) }
            click(editor,"Back to tools")
            waitFor { !editor.toolPanel.isShown }
            val closed=Rect(); instrumentation.runOnMainSync { surface.getGlobalVisibleRect(closed) }
            assertEquals(original,closed)
        } finally { instrumentation.runOnMainSync { editor.finish() } }
    }
    fun testTextAppearanceAndPresetThumbnailsHaveDistinctPixels() {
        fun textBitmap(layer: TextLayer)=Bitmap.createBitmap(360,300,Bitmap.Config.ARGB_8888).apply { TextPresets.draw(android.graphics.Canvas(this),layer,360,300,750) }
        val base=TextLayer(text="First line\nSecond line",style="Regular",shadow=false)
        val original=textBitmap(base)
        listOf(base.copy(alignment="Left"),base.copy(alignment="Right"),base.copy(letterSpacing=.2f),base.copy(lineSpacing=20f),base.copy(rotation=30f),base.copy(opacity=.4f),base.copy(backgroundColor=Color.BLUE),base.copy(outlineWidth=4f),base.copy(style="Italic"),base.copy(style="Underline"),base.copy(style="Strikethrough")).forEach { layer ->
            val rendered=textBitmap(layer); assertFalse("Text property was ignored: $layer",original.sameAs(rendered)); rendered.recycle()
        }
        original.recycle()
        fun thumbnail(name: String,effect: Boolean): Bitmap {
            val bitmap=Bitmap.createBitmap(144,120,Bitmap.Config.ARGB_8888)
            instrumentation.runOnMainSync { PresetThumbnail(context,name,effect).apply { layout(0,0,144,120); draw(android.graphics.Canvas(bitmap)) } }
            return bitmap
        }
        listOf(false,true).forEach { effect ->
            val names=if(effect) EditorPresets.effects.values.flatten()+"None" else EditorPresets.filters.values.flatten()+"Original"
            val images=names.map { thumbnail(it,effect) }
            for(i in images.indices) for(j in 0 until i) assertFalse("Identical thumbnails: ${names[i]}, ${names[j]}",images[i].sameAs(images[j]))
            images.forEach { it.recycle() }
        }
    }
    fun testVideoEffectsAndStickerMoveAreVisibleWithoutDone() {
        val source=fixture("source.mp4"); val audio=fixture("sound.wav")
        val id="feedback-${UUID.randomUUID()}"
        val editor=start(id,EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Video",true)),projectSounds=listOf(SoundLayer(uri=Uri.fromFile(audio).toString(),name="Long track",endMs=20000))))
        try {
            waitFor { editor.previewRendered && snapshot(editor)!=null }
            val original=Color.red(snapshot(editor)!!.getPixel(2,2))
            click(editor,"Edit"); click(editor,"Effects"); click(editor,"Vignette")
            waitFor { ProjectStore.load(context,id)!!.state.clips[0].effect=="Vignette" && snapshot(editor)?.let { Color.red(it.getPixel(2,2))<original-25 }==true }
            assertNull(find(editor,"Done")); assertNull(find(editor,"Apply"))
            click(editor,"None: Effects")
            waitFor { snapshot(editor)?.let { Color.red(it.getPixel(2,2))>=original-5 }==true }
            click(editor,"Back to tools"); click(editor,"Stickers")
            click(editor,"Stickers preset: ${EditorPresets.stickers.first()}")
            waitFor { snapshot(editor)?.let { greenX(it)>0 }==true }
            click(editor,"Back to tools")
            val initial=greenX(snapshot(editor)!!)
            val surface=all(editor).filterIsInstance<EditorPreviewView>().single(); val r=Rect(); surface.getGlobalVisibleRect(r)
            val dx=Ui.dp(context,48).toFloat(); val down=android.os.SystemClock.uptimeMillis()
            gesture(MotionEvent.ACTION_DOWN,down,r.exactCenterX(),r.exactCenterY())
            for(i in 1..8) { gesture(MotionEvent.ACTION_MOVE,down,r.exactCenterX()+dx*i/8,r.exactCenterY()); Thread.sleep(30) }
            waitFor { snapshot(editor)?.let { greenX(it)>initial+10 }==true }
            assertEquals(0f,ProjectStore.load(context,id)!!.state.projectOverlays!!.single().x)
            gesture(MotionEvent.ACTION_UP,down,r.exactCenterX()+dx,r.exactCenterY())
            waitFor { ProjectStore.load(context,id)!!.state.projectOverlays!!.single().x>.1f }
            click(editor,"Undo"); waitFor { snapshot(editor)?.let { kotlin.math.abs(greenX(it)-initial)<3 }==true }
            click(editor,"None"); waitFor { ProjectStore.load(context,id)!!.state.projectOverlays!!.isEmpty() && snapshot(editor)?.let { greenX(it)<0 }==true }
            val screenshot=instrumentation.uiAutomation.takeScreenshot()!!
            File(context.filesDir,"ux-checks/feedback-v104.png").apply { parentFile!!.mkdirs(); outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) } }; screenshot.recycle()
        } finally { instrumentation.runOnMainSync { editor.finish() } }
    }
    fun testMuteAndHistoryControlsBelowPreview() {
        val id="mute-${UUID.randomUUID()}"; val file=fixture("edited.mp4")
        val editor=start(id,EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Audio video",true))))
        try {
            waitFor { editor.previewRendered }
            val source=all(editor).filterIsInstance<EditorPreviewView>().single(); val bounds=Rect(); source.getGlobalVisibleRect(bounds)
            val undo=find(editor,"Undo")!!; val u=Rect(); waitFor { undo.width>0 && undo.getGlobalVisibleRect(u) }
            assertTrue("History must be below media",u.top>=bounds.bottom)
            assertEquals("Timeline controls",(undo.parent as View).contentDescription)
            click(editor,"Mute video"); waitFor { ProjectStore.load(context,id)!!.state.clips[0].muted }
            assertNotNull(find(editor,"Unmute video"))
            click(editor,"Unmute video"); waitFor { !ProjectStore.load(context,id)!!.state.clips[0].muted }
        } finally { instrumentation.runOnMainSync { editor.finish() } }
    }
    fun testTrimRemovesOldTailFromTimelineAndSeek() {
        val id="trim-${UUID.randomUUID()}"; val source=fixture("source.mp4"); val sound=fixture("sound.wav")
        val editor=start(id,EditorState(listOf(EditorClip(Uri.fromFile(source).toString(),"Trim source",true)),projectSounds=listOf(SoundLayer(uri=Uri.fromFile(sound).toString(),name="Twenty seconds",endMs=20000))))
        try {
            waitFor { editor.previewRendered }; click(editor,"Clip 1: Trim source")
            val handle=all(editor).first { it.contentDescription=="Trim end handle" }; val r=Rect(); waitFor { handle.width>0 && handle.getGlobalVisibleRect(r) }
            val down=android.os.SystemClock.uptimeMillis(); val dx=Ui.dp(context,48).toFloat()
            gesture(MotionEvent.ACTION_DOWN,down,r.exactCenterX(),r.exactCenterY())
            gesture(MotionEvent.ACTION_MOVE,down,r.exactCenterX()-dx,r.exactCenterY())
            gesture(MotionEvent.ACTION_UP,down,r.exactCenterX()-dx,r.exactCenterY())
            waitFor { ProjectStore.load(context,id)!!.state.clips[0].endMs in 1800..2200 }
            val duration=TimelineBounds.duration(ProjectStore.load(context,id)!!.state).toInt()
            waitFor { all(editor).filterIsInstance<SeekBar>().first().max==duration }
            val scroll=all(editor).filterIsInstance<android.widget.HorizontalScrollView>().first { it.tag=="timeline-horizontal" }
            waitFor { kotlin.math.abs((scroll.getChildAt(0).width-scroll.width)-duration*editor.timelineZoom*context.resources.displayMetrics.density/1000f)<3 }
            val maximum=scroll.getChildAt(0).width-scroll.width
            assertTrue("Old audio tail extended timeline",kotlin.math.abs(maximum-duration*editor.timelineZoom*context.resources.displayMetrics.density/1000f)<3)
        } finally { instrumentation.runOnMainSync { editor.finish() } }
    }
}
