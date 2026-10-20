package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View

/** The same typography and animation drawing serves preset thumbnails and encoded frames. */
object TextPresets {
    private val legacyAnimations=linkedMapOf("Basic" to listOf("None","Fade"),"Motion" to listOf("Slide","Rise","Bounce"),"Reveal" to listOf("Pop","Zoom","Typewriter"))
    val fonts=linkedMapOf("Classic" to listOf("Sans","Serif","Monospace"),"Creative" to listOf("Condensed","Casual","Cursive"))
    private val legacyStyles=linkedMapOf("Basic" to listOf("Regular","Bold","Italic","Bold italic"),"Titles" to listOf("Outline","Subtitle","Underline","Strikethrough"))
    val animations get()=com.lumen.studio.graphics.GraphicsCatalog.current?.animations?.filter { it.info.category!="Video motion" && !it.info.category.startsWith("Clip ") }?.groupBy { it.info.category }?.mapValues { (_,items) -> items.map { it.info.name } }?.toMap(linkedMapOf())?.apply { put("Basic",listOf("None")) } ?: legacyAnimations
    val styles get()=com.lumen.studio.graphics.GraphicsCatalog.current?.typography?.groupBy { it.info.category }?.mapValues { (_,items) -> items.map { it.info.name } }?.toMap(linkedMapOf()) ?: legacyStyles
    val colors=linkedMapOf("Neutral" to listOf("White","Black"),"Vivid" to listOf("Green","Yellow","Red","Blue"),"Soft" to listOf("Pink","Orange","Purple","Cyan"))
    fun color(name: String)=when(name) {
        "Black" -> Color.BLACK; "Green" -> Color.rgb(0,225,119); "Yellow" -> Color.YELLOW
        "Red" -> Color.RED; "Blue" -> Color.rgb(70,140,255); "Pink" -> Color.rgb(255,100,180)
        "Orange" -> Color.rgb(255,160,40); "Purple" -> Color.rgb(170,100,255); "Cyan" -> Color.CYAN
        else -> Color.WHITE
    }
    fun draw(canvas: Canvas,layer: TextLayer,w: Int,h: Int,timeMs: Long) {
        if(com.lumen.studio.graphics.GraphicsCatalog.current!=null) { com.lumen.studio.graphics.CatalogTextRenderer.draw(canvas,layer,w,h,timeMs); return }
        if(timeMs<layer.startMs || (layer.endMs>0 && timeMs>=layer.endMs)) return
        val progress=((timeMs-layer.startMs+layer.animationOffsetMs)/500f).coerceIn(0f,1f)
        val fadeOut=if(layer.endMs>0) ((layer.endMs-timeMs)/300f).coerceIn(0f,1f) else 1f
        val content=if(layer.animation=="Typewriter") layer.text.take((layer.text.length*progress).toInt()) else layer.text
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color=layer.color; textSize=layer.size*(w/360f)
            typeface=Typeface.create(when(layer.font) { "Serif" -> "serif"; "Monospace" -> "monospace"; "Condensed" -> "sans-serif-condensed"; "Casual" -> "casual"; "Cursive" -> "cursive"; else -> "sans-serif" },when(layer.style) { "Regular","Underline","Strikethrough" -> Typeface.NORMAL; "Italic" -> Typeface.ITALIC; "Bold italic" -> Typeface.BOLD_ITALIC; else -> Typeface.BOLD })
            isUnderlineText=layer.style=="Underline"; isStrikeThruText=layer.style=="Strikethrough"
            letterSpacing=layer.letterSpacing
            if(layer.shadow!=false) setShadowLayer(3f,1f,2f,Color.BLACK)
        }
        val alignment=when(layer.alignment) { "Left" -> Layout.Alignment.ALIGN_NORMAL; "Right" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_CENTER }
        val layout=StaticLayout.Builder.obtain(content,0,content.length,paint,(w*.9).toInt().coerceAtLeast(1)).setAlignment(alignment).setLineSpacing(layer.lineSpacing*w/360f,1f).build()
        canvas.save()
        val alpha=((layer.opacity ?: 1f).coerceIn(0f,1f)*255*(if(layer.animation=="Fade") minOf(progress,fadeOut) else 1f)).toInt()
        canvas.saveLayerAlpha(0f,0f,w.toFloat(),h.toFloat(),alpha)
        val dx=if(layer.animation=="Slide") (progress-1)*w*.2f else 0f
        val dy=if(layer.animation=="Rise") (1-progress)*h*.15f else if(layer.animation=="Bounce") -kotlin.math.sin(progress*Math.PI*3).toFloat()*(1-progress)*h*.12f else 0f
        canvas.translate(w*(layer.x+1)/2+dx,h*(1-layer.y)/2+dy)
        canvas.rotate(layer.rotation)
        if(layer.animation=="Pop" || layer.animation=="Zoom") { val scale=if(layer.animation=="Zoom") .2f+.8f*progress else .5f+.5f*progress; canvas.scale(scale,scale) }
        canvas.translate(-layout.width/2f,-layout.height/2f)
        if(layer.style=="Subtitle" || layer.backgroundColor!=0) canvas.drawRoundRect(RectF(-8f,-4f,layout.width+8f,layout.height+4f),8f,8f,Paint().apply { color=if(layer.backgroundColor!=0) layer.backgroundColor else Color.argb(180,0,0,0) })
        if(layer.style=="Outline" || layer.outlineWidth>0) { val fill=paint.color; paint.color=Color.BLACK; paint.style=Paint.Style.STROKE; paint.strokeWidth=(if(layer.outlineWidth>0) layer.outlineWidth else 3f)*w/360f; layout.draw(canvas); paint.style=Paint.Style.FILL; paint.color=fill }
        layout.draw(canvas); canvas.restore(); canvas.restore()
    }
}

class TextPresetThumbnail(context: Context,private val kind: String,private val preset: String): View(context) {
    private val began=android.os.SystemClock.uptimeMillis()
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if(kind in listOf("Color","Frame color") && preset=="Black") Color.rgb(229,235,240) else Color.rgb(45,51,62))
        val sample=TextLayer(text="Aa",size=100f,style="Regular")
        val layer=when(kind) {
            "Text animation" -> sample.copy(animation=preset)
            "Font" -> sample.copy(font=preset)
            "Style" -> sample.copy(style=preset)
            "Color","Frame color" -> sample.copy(color=TextPresets.color(preset))
            "Text background" -> sample.copy(backgroundColor=TextPresets.color(preset),color=if(preset in listOf("White","Yellow","Cyan","Green")) Color.BLACK else Color.WHITE)
            "Stickers" -> sample.copy(text=preset,size=if(preset.length>2) 80f else 180f,color=TextPresets.color("Green"))
            else -> sample.copy(text=preset,size=90f)
        }
        val time=if(kind=="Text animation") (android.os.SystemClock.uptimeMillis()-began)%1700 else 750L
        canvas.save()
        if(kind=="Rotate") canvas.rotate(preset.toFloatOrNull() ?: 0f,width/2f,height/2f)
        TextPresets.draw(canvas,if(kind=="Rotate") sample.copy(text="\u2191") else layer,width,height,time)
        canvas.restore()
        if(kind=="Text animation" && isAttachedToWindow && isShown) postInvalidateDelayed(33)
    }
}
