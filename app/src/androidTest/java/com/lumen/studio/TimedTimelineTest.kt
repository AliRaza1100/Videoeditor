package com.lumen.studio

import android.graphics.*
import android.net.Uri
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.widget.FrameLayout
import java.io.File

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class TimedTimelineTest: InstrumentationTestCase() {
    fun testTimedFilterMatchesPausedFramesAndBounds() {
        val context=instrumentation.targetContext
        val file=File(context.cacheDir,"timed-filter-test.png")
        Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888).also { bitmap -> bitmap.eraseColor(Color.RED); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        try {
            val clip=EditorClip(Uri.fromFile(file).toString(),"Red",false,imageDurationMs=6000)
            val layer=VisualLayer(kind="filter",name="Mono",startMs=1000,endMs=3000)
            val state=EditorState(listOf(clip),projectVisuals=listOf(layer))
            for((time,mono) in listOf(500L to false,1500L to true,3500L to false)) {
                val frame=StillPreviewRenderer.render(context,state,time)
                try { val c=frame.getPixel(frame.width/2,frame.height/2); assertEquals(mono,kotlin.math.abs(Color.red(c)-Color.green(c))<10) } finally { frame.recycle() }
            }
            val plan=TimelineEngine.build(context,state)
            assertEquals(6000L,plan.duration)
            assertEquals("Original",TimelineEngine.visualAt(clip.copy(visuals=listOf(layer)),500).filter)
            assertEquals("Mono",TimelineEngine.visualAt(clip.copy(visuals=listOf(layer)),1500).filter)
        } finally { file.delete() }
    }
    fun testNativeTimelineEdgeDragAndSpeedDots() {
        instrumentation.runOnMainSync {
            val context=instrumentation.targetContext
            var interval=0L to 0L
            val parent=FrameLayout(context)
            val region=TimelineRegionView(context,"Text",1000,5000,0,10000,.1f,0,{}, { a,b -> interval=a to b })
            parent.addView(region,FrameLayout.LayoutParams(400,100)); parent.layout(0,0,1200,200); region.layout(0,0,400,100)
            val now=android.os.SystemClock.uptimeMillis()
            fun send(view: android.view.View,action: Int,x: Float,y: Float) { val event=MotionEvent.obtain(now,now+16,action,x,y,0); view.dispatchTouchEvent(event); event.recycle() }
            send(region,MotionEvent.ACTION_DOWN,397f,50f); send(region,MotionEvent.ACTION_MOVE,497f,50f); send(region,MotionEvent.ACTION_UP,497f,50f)
            assertEquals(1000L,interval.first); assertEquals(6000L,interval.second)
            var points=listOf<Float>(); var speeds=listOf<Float>()
            val graph=SpeedCurveView(context,EditorClip("file:///source.mp4","Source",true,sourceDurationMs=10000)) { p,s -> points=p; speeds=s }
            parent.addView(graph); graph.layout(0,0,600,300)
            send(graph,MotionEvent.ACTION_DOWN,300f,150f); send(graph,MotionEvent.ACTION_MOVE,380f,70f); send(graph,MotionEvent.ACTION_UP,380f,70f)
            assertEquals(3,points.size); assertTrue(points[1]>.5f); assertTrue(speeds[1]>1f)
        }
    }
}
