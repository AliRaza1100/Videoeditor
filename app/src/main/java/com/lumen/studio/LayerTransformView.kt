package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.view.View

/** Native, frame-synchronized manipulation; no media decoding during pointer moves. */
class LayerTransformView(context: Context): View(context) {
    var text: TextLayer?=null
    var image: ImageLayer?=null
    var frame: VisualLayer?=null
    var frameClip: EditorClip?=null
    private var bitmap: Bitmap?=null
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    init { visibility=GONE; isClickable=false; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setLayer(t: TextLayer?,i: ImageLayer?) {
        text=t; image=i; frame=null; frameClip=null; bitmap?.recycle(); bitmap=null
        if(i!=null) bitmap=runCatching {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            context.contentResolver.openInputStream(Uri.parse(i.uri))?.use { BitmapFactory.decodeStream(it,null,bounds) }
            val options=BitmapFactory.Options().apply { inSampleSize=1; while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>1024) inSampleSize*=2 }
            context.contentResolver.openInputStream(Uri.parse(i.uri))?.use { BitmapFactory.decodeStream(it,null,options) }
        }.getOrNull()
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        var box: RectF?=null
        frame?.let { layer -> frameClip?.let { clip ->
            FrameEffectRenderer.draw(context,canvas,clip,layer,width,height)
            val bounds=FrameEffectRenderer.bounds(context,clip,layer,width,height)
            box=RectF(bounds.left,bounds.top,bounds.right,bounds.bottom)
        } }
        image?.let { layer -> bitmap?.let { b ->
            val w=width*layer.scale; val h=w*b.height/b.width
            val x=width*(layer.x+1)/2; val y=height*(1-layer.y)/2
            box=RectF(x-w/2,y-h/2,x+w/2,y+h/2)
            canvas.save(); canvas.rotate(layer.rotation,x,y); if(layer.flip) canvas.scale(-1f,1f,x,y)
            ink.style=Paint.Style.FILL; ink.alpha=(layer.opacity*255).toInt(); canvas.drawBitmap(b,null,box!!,ink); canvas.restore()
        } }
        text?.let { layer -> TextPresets.draw(canvas,layer.copy(animation="None",startMs=0,endMs=0),width,height,750)
            val x=width*(layer.x+1)/2; val y=height*(1-layer.y)/2
            box=RectF(x-width*.3f,y-layer.size*width/360,x+width*.3f,y+layer.size*width/360)
        }
        box?.let { ink.alpha=255; ink.color=StudioDesign.accent(context); ink.style=Paint.Style.STROKE; ink.strokeWidth=Ui.dp(context,2).toFloat(); canvas.drawRoundRect(it,6f,6f,ink) }
    }
    override fun onDetachedFromWindow() { bitmap?.recycle(); bitmap=null; super.onDetachedFromWindow() }
}
