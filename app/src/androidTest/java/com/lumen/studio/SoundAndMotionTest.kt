package com.lumen.studio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class SoundAndMotionTest: InstrumentationTestCase() {
    private val context get()=instrumentation.targetContext
    override fun setUp() { super.setUp(); assertTrue(context.packageName.endsWith(".qa")); AppPreferences.setLanguage(context,"en") }
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    fun testZoomChangesPausedFrameAndAddsGpuTransformation() {
        val file=File(context.cacheDir,"motion-${UUID.randomUUID()}.png")
        val image=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(image); canvas.drawColor(Color.BLUE); canvas.drawRect(80f,80f,240f,240f,Paint().apply { color=Color.RED }); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { image.recycle() }
        try {
            val clip=EditorClip(Uri.fromFile(file).toString(),"Motion",false,imageDurationMs=5000,motion="clip.zoom-in")
            val state=EditorState(listOf(clip))
            val first=StillPreviewRenderer.render(context,state,0); val later=StillPreviewRenderer.render(context,state,4000)
            try { assertFalse("Zoom did not change pixels",first.sameAs(later)) } finally { first.recycle(); later.recycle() }
            val gpu=TimelineEngine.effects(context,clip,state,320,320).filterIsInstance<androidx.media3.effect.MatrixTransformation>()
            assertTrue("Export did not receive clip motion",gpu.any { val a=FloatArray(9); val b=FloatArray(9); it.getMatrix(0).getValues(a); it.getMatrix(4000000).getValues(b); !a.contentEquals(b) })
            assertEquals(1f,ClipMotion.pose(clip.copy(motion="clip.combo-zoom"),4000).scaleX)
        } finally { file.delete() }
    }
    fun testTapTextShowsRotateAndDeleteHandles() {
        val file=File(context.cacheDir,"handles-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888).also { b -> try { b.eraseColor(Color.BLUE); file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { b.recycle() } }
        val id="handles-${UUID.randomUUID()}"
        ProjectStore.save(context,SavedProject(id,"Handles",System.currentTimeMillis(),EditorState(listOf(EditorClip(Uri.fromFile(file).toString(),"Image",false)),projectTexts=listOf(TextLayer(text="Hello",startMs=0,endMs=4000)))))
        val editor=instrumentation.startActivitySync(Intent(context,EditorActivity::class.java).putExtra("project_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
        try {
            val deadline=System.currentTimeMillis()+20000; while(!editor.previewRendered && System.currentTimeMillis()<deadline) Thread.sleep(100)
            assertTrue(editor.previewRendered)
            instrumentation.runOnMainSync {
                val surface=views(editor.window.decorView).filterIsInstance<EditorPreviewView>().single()
                val now=android.os.SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) { val event=MotionEvent.obtain(now,now+20,action,surface.width/2f,surface.height/2f,0); surface.dispatchTouchEvent(event); event.recycle() }
                val rotate=views(editor.window.decorView).first { it.contentDescription=="Rotate selected layer" }
                assertTrue(rotate.isShown); rotate.performClick()
                assertEquals(15f,ProjectStore.load(context,id)!!.state.projectTexts!!.single().rotation)
                views(editor.window.decorView).first { it.contentDescription=="Delete selected layer" }.performClick()
                assertTrue(ProjectStore.load(context,id)!!.state.projectTexts.orEmpty().isEmpty())
            }
        } finally { instrumentation.runOnMainSync { editor.finish() }; ProjectStore.delete(context,id); file.delete() }
    }
    fun testImportedSoundSurvivesRemovalOfOriginal() {
        val source=File(context.cacheDir,"sound-${UUID.randomUUID()}.wav")
        source.outputStream().use { it.write(waveFileHeader(96000,48000,1)); it.write(ByteArray(96000)) }
        val done=CountDownLatch(1); var imported: LibrarySound?=null
        SoundLibrary.import(context,Uri.fromFile(source),false) { imported=it; done.countDown() }
        assertTrue("Sound import timed out",done.await(30,TimeUnit.SECONDS))
        source.delete(); val sound=imported ?: error(SoundLibrary.status)
        assertTrue(File(sound.path).isFile)
        assertTrue(SoundLibrary.list(context).any { it.id==sound.id })
        assertTrue(TimelineEngine.duration(context,Uri.fromFile(File(sound.path)).toString())>0)
        assertEquals(100,SoundLibrary.progress)
    }
}
