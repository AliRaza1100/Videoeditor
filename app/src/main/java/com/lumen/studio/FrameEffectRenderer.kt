package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

object FrameEffectRenderer {
    private val sizes=ConcurrentHashMap<String,Pair<Int,Int>>()
    fun clearMemory() { sizes.clear() }
    fun bounds(context: Context,c: EditorClip,layer: VisualLayer?,w: Int,h: Int): FrameBounds {
        val size=sizes[c.uri] ?: readSize(context,c).also { sizes[c.uri]=it }
        return FrameGeometry.bounds(size.first,size.second,w,h,c,layer)
    }
    private fun readSize(context: Context,c: EditorClip): Pair<Int,Int> {
        if(c.video) {
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(context,Uri.parse(c.uri))
                val w=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1
                val h=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1
                val rotation=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                return if(rotation%180==0) w to h else h to w
            } finally { reader.release() }
        }
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(Uri.parse(c.uri))?.use { BitmapFactory.decodeStream(it,null,options) }
        return options.outWidth.coerceAtLeast(1) to options.outHeight.coerceAtLeast(1)
    }
    fun draw(context: Context,canvas: Canvas,c: EditorClip,layer: VisualLayer?,w: Int,h: Int) {
        val box=bounds(context,c,layer,w,h)
        val thickness=(minOf(box.width,box.height)*(layer?.frameWidth ?: .025f)).coerceAtLeast(1f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=layer?.frameColor ?: Color.WHITE; style=Paint.Style.STROKE; strokeWidth=thickness }
        val rect=RectF(box.left+thickness/2,box.top+thickness/2,box.right-thickness/2,box.bottom-thickness/2)
        when(layer?.frameStyle ?: "Outline") {
            "Rounded" -> canvas.drawRoundRect(rect,minOf(box.width,box.height)*.08f,minOf(box.width,box.height)*.08f,paint)
            "Double" -> { canvas.drawRect(rect,paint); rect.inset(thickness*2,thickness*2); if(rect.width()>0 && rect.height()>0) canvas.drawRect(rect,paint) }
            else -> canvas.drawRect(rect,paint)
        }
    }
}
