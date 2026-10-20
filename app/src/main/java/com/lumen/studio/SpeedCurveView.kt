package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/** Dots use source-time fractions; the same positions feed export's integrated speed mapping. */
class SpeedCurveView(context: Context,clip: EditorClip,private val changed: (List<Float>,List<Float>)->Unit): View(context) {
    private val points=ClipEdits.positions(clip).let { if(it.size>=2) it else listOf(0f,1f) }.toMutableList()
    private val speeds=clip.speedCurve.orEmpty().let { if(it.size>=2) it else listOf(clip.speed,clip.speed) }.toMutableList()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected=-1
    private var began=0L
    private var moved=false
    private val pad get()=Ui.dp(context,28).toFloat()
    private fun x(i: Int)=pad+points[i]*(width-2*pad)
    private fun y(speed: Float)=pad+(ln(4f/speed)/ln(16f))*(height-2*pad)
    init { contentDescription="Speed curve. Tap to add a dot; drag dots to change time and speed; hold a dot to remove it."; isClickable=true; minimumHeight=Ui.dp(context,170) }
    fun addDot() {
        if(points.size>=64) return
        val at=(if(selected<0) 0 else selected).coerceAtMost(points.lastIndex-1)
        if(points[at+1]-points[at]<.003f) return
        points.add(at+1,(points[at]+points[at+1])/2)
        speeds.add(at+1,(speeds[at]+speeds[at+1])/2)
        selected=at+1; changed(points.toList(),speeds.toList()); invalidate()
    }
    fun removeDot() {
        if(selected !in 1 until points.lastIndex) return
        points.removeAt(selected); speeds.removeAt(selected); selected=-1
        changed(points.toList(),speeds.toList()); invalidate()
    }
    override fun onDraw(c: Canvas) {
        c.drawColor(StudioDesign.surface(context)); paint.strokeWidth=Ui.dp(context,1).toFloat(); paint.textSize=Ui.dp(context,11).toFloat()
        for(speed in listOf(.25f,.5f,1f,2f,4f)) { paint.color=StudioDesign.border(context); c.drawLine(pad,y(speed),width-pad,y(speed),paint); paint.color=StudioDesign.muted(context); c.drawText("${speed}×",2f,y(speed),paint) }
        paint.color=StudioDesign.accent(context); paint.strokeWidth=Ui.dp(context,3).toFloat()
        for(i in 0 until points.lastIndex) c.drawLine(x(i),y(speeds[i]),x(i+1),y(speeds[i+1]),paint)
        points.indices.forEach { i -> paint.color=if(i==selected) StudioDesign.text(context) else StudioDesign.accent(context); c.drawCircle(x(i),y(speeds[i]),Ui.dp(context,7).toFloat(),paint) }
        paint.color=StudioDesign.muted(context); paint.textSize=Ui.dp(context,10).toFloat()
        for(i in 0..4) c.drawText("${i*25}%",pad+i*(width-2*pad)/4,height-6f,paint)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true); began=e.eventTime; moved=false
                selected=points.indices.minByOrNull { hypot(x(it)-e.x,y(speeds[it])-e.y) } ?: 0
                if(hypot(x(selected)-e.x,y(speeds[selected])-e.y)>Ui.dp(context,28) && points.size<64) {
                    val fraction=((e.x-pad)/(width-2*pad)).coerceIn(.001f,.999f)
                    val at=points.indexOfFirst { it>fraction }; if(at>0 && fraction-points[at-1]>.002f && points[at]-fraction>.002f) { points.add(at,fraction); speeds.add(at,1f); selected=at }
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(selected<0) return false
                if(selected>0 && selected<points.lastIndex) points[selected]=((e.x-pad)/(width-2*pad)).coerceIn(points[selected-1]+.001f,points[selected+1]-.001f)
                speeds[selected]=exp(ln(4.0)-((e.y-pad)/(height-2*pad)).coerceIn(0f,1f)*ln(16.0)).toFloat().coerceIn(.25f,4f)
                moved=true; invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                if(!moved && e.eventTime-began>600 && selected in 1 until points.lastIndex) { points.removeAt(selected); speeds.removeAt(selected) }
                changed(points.toList(),speeds.toList()); parent.requestDisallowInterceptTouchEvent(false); performClick(); invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false); return true }
        }; return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
