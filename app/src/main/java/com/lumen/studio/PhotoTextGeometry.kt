package com.lumen.studio

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Tight selectable text bounds use the same layout width, font and spacing as image rendering. */
object PhotoTextGeometry {
    fun bounds(w: Int,h: Int,layer: TextLayer): RectF {
        val catalog=com.lumen.studio.graphics.GraphicsCatalog.current
        val style=catalog?.type(layer.style) ?: catalog?.type("Regular")
        val family=if(layer.font==null || layer.font=="Sans") style?.family ?: "sans-serif" else catalog?.fonts?.get(layer.font) ?: style?.family ?: "sans-serif"
        val fontStyle=when { (style?.weight ?: 400)>=600 && style?.italic==true -> Typeface.BOLD_ITALIC; (style?.weight ?: 400)>=600 -> Typeface.BOLD; style?.italic==true -> Typeface.ITALIC; else -> Typeface.NORMAL }
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=(layer.size*w/360f).coerceIn(1f,w*2f); typeface=Typeface.create(family,fontStyle); letterSpacing=layer.letterSpacing+(style?.letterSpacing ?: 0f) }
        val alignment=when(layer.alignment) { "Left" -> Layout.Alignment.ALIGN_NORMAL; "Right" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_CENTER }
        val layout=StaticLayout.Builder.obtain(layer.text,0,layer.text.length,paint,(w*.9f).toInt().coerceAtLeast(1)).setAlignment(alignment).setIncludePad(true).setLineSpacing(layer.lineSpacing*w/360f,1f).setMaxLines(40).build()
        var left=layout.width.toFloat(); var right=0f
        for(line in 0 until layout.lineCount) { left=minOf(left,layout.getLineLeft(line)); right=maxOf(right,layout.getLineRight(line)) }
        val cx=w*(layer.x+1)/2; val cy=h*(1-layer.y)/2
        val pad=maxOf(6f,(style?.padding ?: 0f)*w/360f,layer.outlineWidth*w/360f)
        return RectF(cx-layout.width/2+left-pad,cy-layout.height/2-pad,cx-layout.width/2+right+pad,cy+layout.height/2+pad).also { Matrix().apply { setRotate(layer.rotation,cx,cy) }.mapRect(it) }
    }
}
