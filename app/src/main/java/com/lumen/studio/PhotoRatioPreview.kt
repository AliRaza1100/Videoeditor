package com.lumen.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class PhotoRatioPreview(context: Context,private val ratio: String): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE }
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        paint.color=StudioDesign.accent(context); paint.strokeWidth=Ui.dp(context,2).toFloat()
        val r=ratio.split(':').takeIf { it.size==2 }?.let { it[0].toFloat()/it[1].toFloat() } ?: 1.3f
        val w=minOf(width*.65f,height*.72f*r); val h=w/r
        canvas.drawRoundRect((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2,5f,5f,paint)
    }
}
