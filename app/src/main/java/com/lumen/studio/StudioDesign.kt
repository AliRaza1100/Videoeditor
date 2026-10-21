package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Shared, semantic colors and controls. Media and exported pixels never use this palette. */
object StudioDesign {
    private fun palette(c: Context)=ThemeColors.palette(AppPreferences.theme(c))
    fun background(c: Context)=palette(c)[0]
    fun surface(c: Context)=palette(c)[1]
    fun raised(c: Context)=palette(c)[2]
    fun text(c: Context)=palette(c)[3]
    fun muted(c: Context)=palette(c)[4]
    fun accent(c: Context)=palette(c)[5]
    fun primary(c: Context)=palette(c)[6]
    fun border(c: Context)=palette(c)[7]
    fun primaryText(c: Context)=if(AppPreferences.theme(c) in listOf("gold","light")) text(c) else Color.WHITE
    fun shape(c: Context,color: Int,radius: Int=16,outline: Boolean=false)=GradientDrawable().apply {
        setColor(color); cornerRadius=Ui.dp(c,radius).toFloat()
        if(outline) setStroke(Ui.dp(c,1),border(c))
    }
    /** Reference-style actions follow the selected palette; gold keeps its warm gradient. */
    fun referenceAction(c: Context,button: android.widget.Button) {
        button.tag="primary-action"
        button.backgroundTintList=null
        button.background=if(AppPreferences.theme(c)=="gold") GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xFFEBC766.toInt(),0xFFC3962F.toInt())).apply { cornerRadius=Ui.dp(c,14).toFloat() } else shape(c,primary(c),14)
        button.setTextColor(primaryText(c))
    }
    fun label(c: Context,value: String,size: Float=14f,secondary: Boolean=false,bold: Boolean=false)=AppText(c).apply {
        text=value; textSize=size; setTextColor(if(secondary) muted(c) else StudioDesign.text(c)); includeFontPadding=false
        if(bold) typeface=Typeface.create("sans-serif",Typeface.BOLD)
        setLineSpacing(Ui.dp(c,3).toFloat(),1f)
    }
    fun button(c: Context,value: String,primary: Boolean=false,action: ()->Unit)=AppButton(c).apply {
        text=value; textSize=13f; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        tag=if(primary) "primary-action" else "secondary-action"
        setTextColor(if(primary) primaryText(c) else StudioDesign.text(c))
        backgroundTintList=null
        background=RippleDrawable(ColorStateList.valueOf(accent(c) and 0x00ffffff or 0x22000000),android.graphics.drawable.InsetDrawable(shape(c,if(primary) StudioDesign.primary(c) else surface(c),10,!primary),0,Ui.dp(c,3),0,Ui.dp(c,3)),null)
        setPadding(Ui.dp(c,12),Ui.dp(c,4),Ui.dp(c,12),Ui.dp(c,4))
        setOnClickListener { action() }
    }
}

class StudioMark(context: Context): View(context) {
    override fun onDraw(canvas: Canvas) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG); val side=minOf(width,height).toFloat()
        canvas.save(); canvas.translate((width-side)/2,(height-side)/2); canvas.scale(side/80,side/80)
        p.color=StudioDesign.primary(context); canvas.drawRoundRect(0f,0f,80f,80f,22f,22f,p)
        p.color=StudioDesign.primaryText(context); p.style=Paint.Style.STROKE; p.strokeWidth=5f; p.strokeCap=Paint.Cap.ROUND
        canvas.drawPath(Path().apply { moveTo(24f,22f); lineTo(24f,57f); lineTo(48f,57f) },p)
        p.style=Paint.Style.FILL
        canvas.drawPath(Path().apply { moveTo(41f,23f); lineTo(60f,35f); lineTo(41f,47f); close() },p)
        canvas.restore()
    }
}

/** Original vector artwork reflecting the actual preview, timeline and export workflow. */
class StudioIllustration(context: Context,private val page: Int=0): View(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val scale=minOf(width/340f,height/260f)
        canvas.save(); canvas.translate((width-340*scale)/2,(height-260*scale)/2); canvas.scale(scale,scale)
        fun rect(l: Float,t: Float,r: Float,b: Float,color: Int,radius: Float=10f) { ink.color=color; ink.shader=null; canvas.drawRoundRect(l,t,r,b,radius,radius,ink) }
        rect(8f,8f,332f,252f,StudioDesign.surface(context),20f)
        rect(24f,24f,316f,158f,Color.rgb(43,45,74),12f)
        ink.shader=LinearGradient(24f,24f,316f,158f,intArrayOf(Color.rgb(128,119,181),Color.rgb(53,65,104)),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(24f,24f,316f,158f,12f,12f,ink); ink.shader=null
        ink.color=Color.rgb(240,203,171); canvas.drawCircle(245f,64f,22f,ink)
        ink.color=Color.rgb(62,66,100); canvas.drawPath(Path().apply { moveTo(24f,145f); lineTo(95f,66f); lineTo(177f,158f); lineTo(24f,158f); close() },ink)
        ink.color=Color.rgb(38,46,75); canvas.drawPath(Path().apply { moveTo(95f,158f); lineTo(223f,89f); lineTo(316f,141f); lineTo(316f,158f); close() },ink)
        if(page==1) {
            rect(106f,110f,234f,140f,Color.argb(190,20,22,34),7f)
            ink.color=Color.WHITE; ink.textSize=14f; ink.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); ink.textAlign=Paint.Align.CENTER
            canvas.drawText("Your next story",170f,130f,ink)
        }
        rect(24f,174f,94f,201f,Color.rgb(135,115,213),5f); rect(98f,174f,220f,201f,Color.rgb(93,111,172),5f); rect(224f,174f,316f,201f,Color.rgb(173,133,164),5f)
        rect(24f,208f,316f,234f,StudioDesign.raised(context),5f)
        ink.color=StudioDesign.accent(context); ink.strokeWidth=2f
        for(i in 0..64) { val x=29f+i*4.4f; val amplitude=(4+kotlin.math.abs(kotlin.math.sin(i*.7))*6).toFloat(); canvas.drawLine(x,221-amplitude,x,221+amplitude,ink) }
        ink.color=Color.WHITE; ink.strokeWidth=2f; canvas.drawLine(167f,169f,167f,240f,ink)
        if(page==2) {
            rect(130f,58f,210f,132f,StudioDesign.primary(context),20f)
            ink.color=Color.WHITE; ink.style=Paint.Style.STROKE; ink.strokeWidth=5f; ink.strokeCap=Paint.Cap.ROUND
            canvas.drawPath(Path().apply { moveTo(151f,95f); lineTo(165f,108f); lineTo(190f,82f) },ink); ink.style=Paint.Style.FILL; ink.strokeCap=Paint.Cap.BUTT
        }
        canvas.restore()
    }
}

class StudioNavIcon(context: Context,private val name: String,private val active: Boolean): View(context) {
    override fun onDraw(canvas: Canvas) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=if(active) StudioDesign.accent(context) else StudioDesign.muted(context); style=Paint.Style.STROKE; strokeWidth=1.8f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
        canvas.save(); canvas.scale(width/24f,height/24f)
        when(name) {
            "home" -> { canvas.drawPath(Path().apply { moveTo(3f,10f); lineTo(12f,3f); lineTo(21f,10f); lineTo(21f,21f); lineTo(15f,21f); lineTo(15f,14f); lineTo(9f,14f); lineTo(9f,21f); lineTo(3f,21f); close() },p) }
            "projects" -> { canvas.drawPath(Path().apply { moveTo(3f,6f); lineTo(10f,6f); lineTo(12f,9f); lineTo(21f,9f); lineTo(21f,21f); lineTo(3f,21f); close() },p); canvas.drawLine(3f,12f,21f,12f,p) }
            else -> { canvas.drawCircle(12f,12f,7f,p); canvas.drawCircle(12f,12f,2.5f,p); for(i in 0..7) { canvas.save(); canvas.rotate(i*45f,12f,12f); canvas.drawLine(12f,2f,12f,5f,p); canvas.restore() } }
        }
        canvas.restore()
    }
}
