package com.lumen.studio

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.test.InstrumentationTestCase
import java.io.File

@Suppress("DEPRECATION")
@androidx.media3.common.util.UnstableApi
class StillPreviewRendererTest: InstrumentationTestCase() {
    fun testEffectsStickerMoveNoneAndAnimatedTextPixels() {
        StillRendererChecks.run(instrumentation.targetContext,File(instrumentation.targetContext.filesDir,"still-tests"))
    }
}

@androidx.media3.common.util.UnstableApi
object StillRendererChecks {
    private fun assertTrue(message: String,value: Boolean) { check(value) { message } }
    private fun assertEquals(expected: Int,actual: Int) { check(expected==actual) { "Expected $expected, got $actual" } }
    fun run(context: android.content.Context,folder: File) {
        folder.mkdirs()
        fun image(name: String,w: Int,h: Int,color: Int): String {
            val file=File(folder,name)
            Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply { eraseColor(color); file.outputStream().use { compress(Bitmap.CompressFormat.PNG,100,it) }; recycle() }
            return Uri.fromFile(file).toString()
        }
        val base=EditorState(listOf(EditorClip(image("source.png",320,240,Color.rgb(200,100,50)),"Source",false)))
        fun render(state: EditorState,time: Long=0)=StillPreviewRenderer.render(context,state,time)
        val original=render(base)
        val vignette=render(base.copy(clips=listOf(base.clips[0].copy(effect="Vignette"))))
        assertTrue("Vignette did not change preview pixels",Color.red(vignette.getPixel(2,2))<Color.red(original.getPixel(2,2))-25)
        val cool=render(base.copy(clips=listOf(base.clips[0].copy(filter="Cool"))))
        assertTrue("Filter did not change preview",Color.red(cool.getPixel(160,120))<Color.red(original.getPixel(160,120))-15)
        val overlay=ImageLayer(uri=image("sticker.png",40,40,Color.GREEN),x=0f,y=0f)
        val placed=render(base.copy(projectOverlays=listOf(overlay)))
        assertEquals(Color.GREEN,placed.getPixel(160,120))
        val moved=render(base.copy(projectOverlays=listOf(overlay.copy(x=.5f))))
        assertEquals(Color.GREEN,moved.getPixel(240,120))
        assertEquals(original.getPixel(160,120),moved.getPixel(160,120))
        val none=render(base.copy(projectOverlays=emptyList()))
        assertEquals(original.getPixel(160,120),none.getPixel(160,120))
        assertEquals(original.getPixel(2,2),none.getPixel(2,2))
        val text=TextLayer(text="Aa",size=50f,animation="Fade",startMs=1000,endMs=3000)
        val early=render(base.copy(projectTexts=listOf(text)),1000)
        val visible=render(base.copy(projectTexts=listOf(text)),1500)
        fun whites(bitmap: Bitmap): Int {
            var count=0
            for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) { val c=bitmap.getPixel(x,y); if(Color.red(c)>230 && Color.green(c)>230 && Color.blue(c)>230) count++ }
            return count
        }
        assertEquals(0,whites(early)); assertTrue("Animated text did not render",whites(visible)>30)
        val rotated=render(base.copy(clips=listOf(base.clips[0].copy(rotation=90f))))
        assertEquals(Color.BLACK,rotated.getPixel(2,2))
        for(bitmap in listOf(original,vignette,cool,placed,moved,none,early,visible,rotated)) bitmap.recycle()
    }
}

/** CPU-only Android smoke entry point; no Activity, player, or MediaCodec needed. */
@androidx.media3.common.util.UnstableApi
object RendererSmokeMain {
    @JvmStatic fun main(args: Array<String>) {
        android.os.Looper.prepareMainLooper()
        val type=Class.forName("android.app.ActivityThread")
        val thread=type.getDeclaredMethod("systemMain").invoke(null)
        val context=type.getDeclaredMethod("getSystemContext").invoke(thread) as android.content.Context
        StillRendererChecks.run(context,File("/data/local/tmp/lumen-render-smoke"))
        println("PASS: Android CPU preview pixels for effects, filter, sticker placement/move/None, text fade and rotation")
    }
}
