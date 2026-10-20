package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.view.View

/** A fixed-size control over the media canvas; it never changes preview layout. */
class SourceMuteView(context: Context): View(context) {
    var muted=false
        set(value) { field=value; contentDescription=Ui.t(context,if(value) "Unmute video" else "Mute video"); isSelected=value; invalidate() }
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable=true; isFocusable=true; muted=false }
    override fun onDraw(canvas: Canvas) {
        ink.style=Paint.Style.FILL; ink.color=Color.argb(190,15,18,23)
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),Ui.dp(context,10).toFloat(),Ui.dp(context,10).toFloat(),ink)
        canvas.save(); canvas.translate(width/2f,height/2f); canvas.scale(Ui.dp(context,24)/24f,Ui.dp(context,24)/24f); canvas.translate(-12f,-12f)
        ink.color=Color.WHITE; ink.style=Paint.Style.STROKE; ink.strokeWidth=1.8f; ink.strokeCap=Paint.Cap.ROUND
        canvas.drawPath(Path().apply { moveTo(3f,9f); lineTo(7f,9f); lineTo(12f,5f); lineTo(12f,19f); lineTo(7f,15f); lineTo(3f,15f); close() },ink)
        if(muted) { canvas.drawLine(16f,9f,22f,15f,ink); canvas.drawLine(16f,15f,22f,9f,ink) }
        else { canvas.drawArc(RectF(12f,7f,20f,17f),-55f,110f,false,ink); canvas.drawArc(RectF(11f,3f,24f,21f),-55f,110f,false,ink) }
        canvas.restore()
    }
}
