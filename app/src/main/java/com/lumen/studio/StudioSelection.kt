package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

/** One theme-aware selected state for thumbnails, swatches and preset cards. */
fun selectStudioCard(view: View,selected: Boolean) {
    view.isSelected=selected
    view.foreground=if(selected) StudioSelection(view.context) else null
    if(android.os.Build.VERSION.SDK_INT>=30) view.stateDescription=Ui.t(view.context,if(selected) "Selected" else "Not selected")
}
private class StudioSelection(private val context: Context): Drawable() {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val d=context.resources.displayMetrics.density
        val r=RectF(bounds).apply { inset(d,d) }
        ink.color=StudioDesign.accent(context);ink.style=Paint.Style.STROKE;ink.strokeWidth=2*d
        canvas.drawRoundRect(r,12*d,12*d,ink)
        val x=r.right-12*d;val y=r.top+12*d
        ink.style=Paint.Style.FILL;canvas.drawCircle(x,y,9*d,ink)
        ink.color=StudioDesign.surface(context);ink.style=Paint.Style.STROKE;ink.strokeWidth=2*d;ink.strokeCap=Paint.Cap.ROUND
        canvas.drawPath(Path().apply { moveTo(x-4*d,y);lineTo(x-d,y+3*d);lineTo(x+4*d,y-3*d) },ink)
    }
    override fun setAlpha(alpha: Int) { ink.alpha=alpha }
    override fun setColorFilter(filter: ColorFilter?) { ink.colorFilter=filter }
    @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
