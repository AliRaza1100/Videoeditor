package com.lumen.studio

import android.graphics.Bitmap
import android.graphics.Color
import android.test.InstrumentationTestCase
import java.io.*

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class PhotoGradingTest: InstrumentationTestCase() {
    fun testShadowsTargetDarkTonesAndPreserveAlpha() {
        val bitmap=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0,0,Color.rgb(30,30,30)); bitmap.setPixel(1,0,Color.rgb(225,225,225))
        val result=PhotoAdjustments.render(bitmap,EditorClip("","",false,catalogAdjustments=mapOf("photo.shadows" to 100f)))
        try {
            val dark=Color.red(result.getPixel(0,0))-30; val light=Color.red(result.getPixel(1,0))-225
            assertTrue("Shadow lift must target dark regions",dark>40 && light<5)
            assertEquals(255,Color.alpha(result.getPixel(0,0)))
        } finally { result.recycle(); bitmap.recycle() }
    }
    fun testZeroFilterStrengthAndNeutralSharpenPreservePixels() {
        val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90,120,150)) }
        for(clip in listOf(EditorClip("","",false,filter="Photo Golden Light",filterIntensity=0f),EditorClip("","",false,catalogAdjustments=mapOf("photo.sharpen" to 100f)))) {
            val output=PhotoAdjustments.render(bitmap,clip)
            try { assertEquals(bitmap.getPixel(8,8),output.getPixel(8,8)) } finally { output.recycle() }
        }; bitmap.recycle()
    }
    fun testPhotoCanvasAndGradeSurviveDraftSerialization() {
        val state=EditorState(listOf(PhotoAdjustments.update(EditorClip("file:///photo.png","Photo",false),"photo.vibrance",35)),photoEditor=true,ratio="4:5",photoCanvasStyle="Gradient",photoCanvasFit="Fill",photoCanvasColorEnd=Color.BLUE)
        val bytes=ByteArrayOutputStream(); ObjectOutputStream(bytes).use { it.writeObject(state) }
        val loaded=ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as EditorState }
        assertEquals("Gradient",loaded.photoCanvasStyle); assertEquals("Fill",loaded.photoCanvasFit); assertEquals(Color.BLUE,loaded.photoCanvasColorEnd)
        assertEquals(35,PhotoAdjustments.value(loaded.clips.single(),"photo.vibrance"))
    }
    fun testPhotoTextHitBoundsFollowPositionAndRotation() {
        val base=TextLayer(text="Hello",size=28f,x=-.5f,y=.5f,rotation=0f)
        val a=PhotoTextGeometry.bounds(800,800,base)
        val b=PhotoTextGeometry.bounds(800,800,base.copy(x=.5f,y=-.5f))
        assertTrue(a.contains(200f,200f)); assertTrue(b.contains(600f,600f)); assertFalse(b.contains(200f,200f))
        val rotated=PhotoTextGeometry.bounds(800,800,base.copy(rotation=90f))
        assertTrue(kotlin.math.abs(a.width()-rotated.height())<2f)
    }
}
