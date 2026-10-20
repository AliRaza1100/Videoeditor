package com.lumen.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView

/** Soft surfaces shared by the library and preference screens; exported media is unaffected. */
internal class ReferenceSurfaces(val context: Context) {
    val gold=AppPreferences.theme(context)=="gold"
    val accent=StudioDesign.accent(context)
    val soft=if(gold) 0xFFFAF5E9.toInt() else StudioDesign.raised(context)
    val selected=if(gold) 0xFFFFF4CF.toInt() else StudioDesign.raised(context)
    val border=if(gold) 0xFFECE5D7.toInt() else StudioDesign.border(context)
    fun dp(n: Int)=Ui.dp(context,n)
    fun shape(color: Int,radius: Int=18,stroke: Int?=null)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat();stroke?.let { setStroke(dp(1),it) }
    }
    fun card(active: Boolean=false,radius: Int=18)=RippleDrawable(
        ColorStateList.valueOf((accent and 0x00ffffff) or 0x16000000),
        shape(if(active) selected else StudioDesign.surface(context),radius,if(active) accent else border),shape(Color.WHITE,radius))
    fun icon(resource: Int)=ImageView(context).apply {
        setImageResource(resource);imageTintList=ColorStateList.valueOf(accent);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

/** Original vector theme thumbnails, drawn at native resolution rather than bitmap mockups. */
internal class ThemeSwatch(context: Context,private val theme: String): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val colors=when(theme) {
            "gold" -> intArrayOf(0xFFFFE8A5.toInt(),0xFFD7AA36.toInt(),0xFF8C6311.toInt())
            "light" -> intArrayOf(0xFFFFFFFF.toInt(),0xFFF3EFE7.toInt(),0xFFD7D2C7.toInt())
            "violet" -> intArrayOf(0xFFE9CEF5.toInt(),0xFFA279BD.toInt(),0xFF472858.toInt())
            else -> intArrayOf(0xFF484848.toInt(),0xFF222222.toInt(),0xFF090909.toInt())
        }
        canvas.save();canvas.scale(width/80f,height/64f)
        canvas.clipPath(Path().apply { addRoundRect(0f,0f,80f,64f,9f,9f,Path.Direction.CW) })
        paint.shader=LinearGradient(0f,0f,80f,64f,colors,null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,80f,64f,paint);paint.shader=null
        paint.color=colors[2];paint.alpha=130
        canvas.drawPath(Path().apply { moveTo(0f,55f);cubicTo(30f,65f,40f,8f,80f,15f);lineTo(80f,64f);lineTo(0f,64f);close() },paint)
        paint.color=colors[0];paint.alpha=140
        canvas.drawPath(Path().apply { moveTo(-5f,27f);cubicTo(23f,70f,45f,-3f,85f,10f);lineTo(85f,17f);cubicTo(44f,2f,27f,76f,-5f,34f);close() },paint)
        paint.alpha=255;canvas.restore()
    }
}

internal class ChoiceIndicator(context: Context,var checked: Boolean=false): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val r=minOf(width,height)*.38f;val x=width/2f;val y=height/2f
        paint.color=if(checked) StudioDesign.accent(context) else StudioDesign.muted(context)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=Ui.dp(context,2).toFloat();canvas.drawCircle(x,y,r,paint)
        if(checked) { paint.style=Paint.Style.FILL;canvas.drawCircle(x,y,r*.53f,paint) }
    }
}

internal class ProjectEmptyArt(context: Context): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val ui=ReferenceSurfaces(context);val a=ui.accent;val soft=ui.soft
        canvas.save();val scale=minOf(width/250f,height/195f);canvas.translate((width-250*scale)/2,(height-195*scale)/2);canvas.scale(scale,scale)
        paint.color=soft;canvas.drawOval(15f,5f,235f,182f,paint)
        paint.shader=LinearGradient(60f,45f,180f,155f,intArrayOf(if(ui.gold) 0xFFF9D46D.toInt() else StudioDesign.primary(context),a),null,Shader.TileMode.CLAMP)
        canvas.drawPath(Path().apply { moveTo(61f,60f);quadTo(61f,46f,75f,46f);lineTo(99f,46f);lineTo(114f,57f);lineTo(176f,57f);quadTo(189f,57f,189f,71f);lineTo(189f,145f);quadTo(189f,156f,177f,156f);lineTo(73f,156f);quadTo(61f,156f,61f,144f);close() },paint)
        paint.shader=null;paint.color=StudioDesign.surface(context);canvas.drawRoundRect(76f,66f,181f,142f,8f,8f,paint)
        paint.shader=LinearGradient(75f,75f,175f,159f,intArrayOf(if(ui.gold) 0xFFFFE6A0.toInt() else StudioDesign.raised(context),if(ui.gold) 0xFFEBC151.toInt() else StudioDesign.primary(context)),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(64f,78f,194f,157f,10f,10f,paint);paint.shader=null
        paint.color=StudioDesign.surface(context);canvas.drawCircle(193f,143f,27f,paint)
        paint.color=a;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;canvas.drawCircle(193f,143f,27f,paint);paint.style=Paint.Style.FILL
        canvas.drawRoundRect(179f,129f,207f,157f,4f,4f,paint);paint.color=StudioDesign.surface(context)
        canvas.drawPath(Path().apply { moveTo(189f,135f);lineTo(200f,143f);lineTo(189f,151f);close() },paint)
        paint.color=if(ui.gold) 0xFFE4B02B.toInt() else a;paint.strokeWidth=2f;paint.strokeCap=Paint.Cap.ROUND
        for((x,y) in listOf(43f to 40f,104f to 22f,218f to 57f,234f to 84f)) { canvas.drawLine(x-5f,y,x+5f,y,paint);canvas.drawLine(x,y-5f,x,y+5f,paint) }
        canvas.restore()
    }
}
