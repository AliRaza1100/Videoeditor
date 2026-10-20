package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs

/** Drag either edge to trim; drag the body to move without changing its duration. */
class TimelineRegionView(context: Context,private val title: String,private val start: Long,private val end: Long,private val lower: Long,private val upper: Long,private val pixelsPerMs: Float,private val lead: Int,private val picked: ()->Unit,private val changed: (Long,Long)->Unit): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var anchor=0f; private var mode=0; private var a=start; private var b=end; private var moved=false
    init { isClickable=true; isFocusable=true; contentDescription="$title. Drag left or right edge to change duration; drag middle to move." }
    override fun setSelected(selected: Boolean) {
        val changed=isSelected!=selected
        super.setSelected(selected)
        if(changed) invalidate()
    }
    override fun onDraw(c: Canvas) {
        paint.style=Paint.Style.FILL
        paint.color=StudioDesign.raised(context); c.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),10f,10f,paint)
        val stroke=Ui.dp(context,if(isSelected) 2 else 1).toFloat()
        paint.style=Paint.Style.STROKE; paint.strokeWidth=stroke
        paint.color=if(isSelected) StudioDesign.accent(context) else StudioDesign.border(context)
        c.drawRoundRect(stroke/2,stroke/2,width-stroke/2,height-stroke/2,Ui.dp(context,6).toFloat(),Ui.dp(context,6).toFloat(),paint)
        paint.style=Paint.Style.FILL
        paint.color=if(isSelected) StudioDesign.accent(context) else StudioDesign.muted(context); paint.strokeWidth=Ui.dp(context,3).toFloat()
        c.drawLine(7f,8f,7f,height-8f,paint); c.drawLine(width-7f,8f,width-7f,height-8f,paint)
        paint.color=StudioDesign.text(context); paint.textSize=Ui.dp(context,11).toFloat()
        c.save(); c.clipRect(Ui.dp(context,12),0,width-Ui.dp(context,12),height)
        c.drawText(title,Ui.dp(context,14).toFloat(),height*.42f,paint)
        paint.textSize=Ui.dp(context,10).toFloat(); c.drawText("%.1fs".format((b-a)/1000f),Ui.dp(context,14).toFloat(),height*.8f,paint); c.restore()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { anchor=e.rawX; a=start; b=end; moved=false; mode=if(e.x<Ui.dp(context,22)) -1 else if(e.x>width-Ui.dp(context,22)) 1 else 0; parent.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> {
                val delta=(((e.rawX-anchor)/pixelsPerMs)/50).toLong()*50
                if(abs(e.rawX-anchor)>Ui.dp(context,3)) moved=true
                val length=end-start
                when(mode) {
                    -1 -> a=(start+delta).coerceIn(lower,(end-100).coerceAtLeast(lower))
                    1 -> b=(end+delta).coerceIn((start+100).coerceAtMost(upper),upper)
                    else -> { a=(start+delta).coerceIn(lower,(upper-length).coerceAtLeast(lower)); b=a+length }
                }
                layoutParams=(layoutParams as FrameLayout.LayoutParams).apply { leftMargin=lead+(a*pixelsPerMs).toInt(); width=maxOf(Ui.dp(context,60),((b-a)*pixelsPerMs).toInt()) }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false); if(moved) changed(a,b) else performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false); layoutParams=(layoutParams as FrameLayout.LayoutParams).apply { leftMargin=lead+(start*pixelsPerMs).toInt(); width=maxOf(Ui.dp(context,60),((end-start)*pixelsPerMs).toInt()) }; return true }
        }; return true
    }
    override fun performClick(): Boolean { super.performClick(); picked(); return true }
}
