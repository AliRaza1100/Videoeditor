package com.lumen.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageButton

fun studioIcon(c: Context,resource: Int,label: String,action: (() -> Unit)?=null)=ImageButton(c).apply {
    setImageResource(resource); imageTintList=ColorStateList.valueOf(StudioDesign.text(c))
    contentDescription=Ui.t(c,label); background=RippleDrawable(ColorStateList.valueOf(0x226B4EFF),StudioDesign.shape(c,StudioDesign.raised(c),12),null)
    val p=Ui.dp(c,12); setPadding(p,p,p,p)
    if(action!=null) setOnClickListener { action() } else { isClickable=false; isFocusable=false }
}

/** Lightweight animated editing storyboard; no video decoding or network assets. */
class StudioMotionHero(c: Context): View(c) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradient=LinearGradient(0f,0f,320f,154f,intArrayOf(0xFF21194B.toInt(),0xFF624298.toInt()),null,Shader.TileMode.CLAMP)
    private val playShape=Path().apply { moveTo(145f,35f); lineTo(145f,85f); lineTo(185f,60f); close() }
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val scale=minOf(width/320f,height/154f)
        canvas.save(); canvas.translate((width-320*scale)/2,(height-154*scale)/2); canvas.scale(scale,scale)
        ink.shader=gradient
        canvas.drawRoundRect(0f,0f,320f,154f,18f,18f,ink); ink.shader=null
        val animate=android.animation.ValueAnimator.areAnimatorsEnabled() && isShown
        val phase=if(animate) (android.os.SystemClock.uptimeMillis()%6000)/6000f else .5f
        ink.color=0xFFA28AFF.toInt(); canvas.drawCircle(250f-phase*35,40f,50f,ink)
        ink.color=0xFF30285E.toInt(); canvas.drawCircle(80f+phase*40,50f,68f,ink)
        ink.color=Color.WHITE
        canvas.drawPath(playShape,ink)
        for(i in 0..5) { ink.color=if(i%2==0) 0xFFA28AFF.toInt() else 0xFF6B4EFF.toInt(); canvas.drawRoundRect(16f+i*49,108f,59f+i*49,132f,5f,5f,ink) }
        ink.color=Color.WHITE; canvas.drawRect(16f+phase*288,102f,18f+phase*288,140f,ink)
        canvas.restore()
        if(animate) postInvalidateDelayed(33)
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if(visibility==VISIBLE) invalidate() }
}

fun projectGradient(c: Context)=RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
    android.graphics.drawable.LayerDrawable(arrayOf(
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xFF6B4EFF.toInt(),0xFF9B6DFF.toInt())).apply { cornerRadius=Ui.dp(c,14).toFloat() },
        StudioDesign.shape(c,0x33000000,14)
    )),null)
