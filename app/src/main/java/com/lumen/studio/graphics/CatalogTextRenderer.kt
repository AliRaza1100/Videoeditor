package com.lumen.studio.graphics

import android.content.res.AssetManager
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import com.lumen.studio.TextLayer

/** Thread-confined bounded layouts; GL preview and export never share mutable TextPaint. */
object CatalogTextRenderer {
    private lateinit var assets: AssetManager
    fun install(value: AssetManager) { assets=value }
    private data class FontKey(val family: String,val weight: Int,val italic: Boolean)
    private val fonts=object: LruCache<FontKey,Typeface>(48) {}
    private data class LayoutKey(val text: String,val width: Int,val size: Float,val family: String,val style: TypographyPreset,val alignment: String?,val spacing: Float,val lineSpacing: Float)
    private data class Prepared(val layout: StaticLayout,val paint: TextPaint)
    private class Resources {
        val layouts=object: LruCache<LayoutKey,Prepared>(512*1024) { override fun sizeOf(key: LayoutKey,value: Prepared)=512+key.text.length*4 }
        val background=Paint(Paint.ANTI_ALIAS_FLAG)
    }
    private val local=ThreadLocal.withInitial { Resources() }
    private fun typeface(family: String,weight: Int,italic: Boolean): Typeface = synchronized(fonts) {
        val key=FontKey(family,weight,italic)
        fonts.get(key) ?: run {
            val base=if(family.startsWith("assets/")) Typeface.createFromAsset(assets,family.removePrefix("assets/")) else Typeface.create(family,Typeface.NORMAL)
            val legacyStyle=when { weight>=600 && italic -> Typeface.BOLD_ITALIC; weight>=600 -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
            (if(Build.VERSION.SDK_INT>=28) Typeface.create(base,weight,italic) else Typeface.create(base,legacyStyle)).also { fonts.put(key,it) }
        }
    }
    fun draw(canvas: Canvas,layer: TextLayer,w: Int,h: Int,timeMs: Long) {
        if(timeMs<layer.startMs || (layer.endMs>0 && timeMs>=layer.endMs) || w<=0 || h<=0 || layer.text.isEmpty()) return
        val catalog=GraphicsCatalog.current ?: error("Install graphics catalog before drawing")
        val style=catalog.type(layer.style) ?: catalog.type("Regular") ?: error("Missing Regular typography")
        val pose=catalog.animation(layer.animation)?.pose((timeMs-layer.startMs+layer.animationOffsetMs).coerceAtLeast(0)) ?: OverlayPose()
        val content=layer.text.take(20_000)
        val family=if(layer.font==null || layer.font=="Sans") style.family else catalog.fonts[layer.font] ?: style.family
        val size=(layer.size*w/360f).coerceIn(1f,w*2f)
        val resources=local.get()!!
        val key=LayoutKey(content,(w*.9f).toInt().coerceAtLeast(1),size,family,style,layer.alignment,layer.letterSpacing+style.letterSpacing,layer.lineSpacing*w/360f)
        val prepared=resources.layouts.get(key) ?: run {
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize=size; typeface=typeface(family,style.weight,style.italic); letterSpacing=key.spacing
                isUnderlineText=style.underline; isStrikeThruText=style.strike
            }
            val alignment=when(layer.alignment) { "Left" -> Layout.Alignment.ALIGN_NORMAL; "Right" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_CENTER }
            val layout=StaticLayout.Builder.obtain(content,0,content.length,paint,key.width).setAlignment(alignment)
                .setIncludePad(true).setLineSpacing(key.lineSpacing,1f).setMaxLines(40).setEllipsize(TextUtils.TruncateAt.END).build()
            Prepared(layout,paint).also { resources.layouts.put(key,it) }
        }
        val layout=prepared.layout; val paint=prepared.paint; val scale=w/360f
        val checkpoint=canvas.save()
        try {
            canvas.translate(w*(layer.x+1)/2+pose.x*w,h*(1-layer.y)/2+pose.y*h)
            canvas.rotate(layer.rotation+pose.rotation); canvas.scale(pose.scaleX,pose.scaleY)
            canvas.translate(-layout.width/2f,-layout.height/2f)
            val alpha=((layer.opacity ?: 1f).coerceIn(0f,1f)*pose.alpha*255).toInt()
            val margin=(style.padding+maxOf(style.glowRadius,layer.outlineWidth,style.strokeWidth)+4)*scale
            canvas.saveLayerAlpha(-margin,-margin,layout.width+margin,layout.height+margin,alpha)
            if(pose.reveal<1f) {
                val end=content.offsetByCodePoints(0,(content.codePointCount(0,content.length)*pose.reveal).toInt())
                val line=layout.getLineForOffset(end)
                val path=Path()
                if(line>0) path.addRect(0f,0f,layout.width.toFloat(),layout.getLineTop(line).toFloat(),Path.Direction.CW)
                val caret=layout.getPrimaryHorizontal(end)
                if(layout.getParagraphDirection(line)<0) path.addRect(caret,layout.getLineTop(line).toFloat(),layout.getLineRight(line),layout.getLineBottom(line).toFloat(),Path.Direction.CW)
                else path.addRect(layout.getLineLeft(line),layout.getLineTop(line).toFloat(),caret,layout.getLineBottom(line).toFloat(),Path.Direction.CW)
                canvas.clipPath(path)
            }
            val background=if(layer.backgroundColor!=0) layer.backgroundColor else style.background
            if(Color.alpha(background)>0) {
                resources.background.color=background; val padding=style.padding*scale
                canvas.drawRoundRect(-padding,-padding,layout.width+padding,layout.height+padding,8*scale,8*scale,resources.background)
            }
            paint.clearShadowLayer(); paint.style=Paint.Style.FILL; paint.color=layer.color
            if(layer.shadow!=false) {
                if(style.glowRadius>0) paint.setShadowLayer(style.glowRadius*scale,0f,0f,style.glowColor)
                else paint.setShadowLayer(3*scale,scale,2*scale,Color.BLACK)
            }
            val stroke=maxOf(layer.outlineWidth,style.strokeWidth)*scale
            if(stroke>0) {
                paint.style=Paint.Style.STROKE; paint.strokeWidth=stroke; paint.strokeJoin=Paint.Join.ROUND; paint.color=style.strokeColor
                layout.draw(canvas); paint.style=Paint.Style.FILL; paint.color=layer.color
            }
            layout.draw(canvas)
        } finally { canvas.restoreToCount(checkpoint) }
    }
}
