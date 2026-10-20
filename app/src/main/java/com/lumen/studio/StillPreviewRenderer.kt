package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Process paused frames without depending on CompositionPlayer's display GL pipeline. */
@androidx.media3.common.util.UnstableApi
object StillPreviewRenderer {
    private val gpu=ThreadLocal<com.lumen.studio.graphics.BitmapShaderRenderer>()
    fun releaseThreadResources() { gpu.get()?.close(); gpu.remove() }
    fun clearMemory() { frames.evictAll() }
    private val frames=object: android.util.LruCache<String,Bitmap>(12*1024) {
        override fun sizeOf(key: String,value: Bitmap)=(value.allocationByteCount/1024).coerceAtLeast(1)
    }
    fun render(context: Context,state: EditorState,position: Long): Bitmap {
        require(state.clips.isNotEmpty())
        val at=TimelineBounds.position(state,position)
        var offset=0L
        var index=state.clips.lastIndex
        for(i in state.clips.indices) { val end=offset+ClipEdits.duration(state.clips[i]); if(at<end) { index=i; break }; offset=end }
        val c=state.clips[index]
        val local=(at-offset).coerceAtLeast(0)
        val sourceTime=if(c.video) ClipEdits.sourcePosition(c,local).toLong() else 0L
        val key="${c.uri}:$sourceTime"
        val source=frames.get(key) ?: read(context,c,sourceTime).also { frames.put(key,it) }
        val left=((c.cropLeft+1)/2*source.width).toInt().coerceIn(0,source.width-1)
        val top=((1-c.cropTop)/2*source.height).toInt().coerceIn(0,source.height-1)
        val right=((c.cropRight+1)/2*source.width).toInt().coerceIn(left+1,source.width)
        val bottom=((1-c.cropBottom)/2*source.height).toInt().coerceIn(top+1,source.height)
        val transform=Matrix().apply { postScale(if(c.flip) -1f else 1f,if(c.flipVertical) -1f else 1f); postRotate(c.rotation) }
        val media=Bitmap.createBitmap(source,left,top,right-left,bottom-top,transform,true)
        val dimensions=TimelineEngine.canvasSize(context,state)
        val scale=minOf(1f,720f/maxOf(dimensions.first,dimensions.second))
        val w=maxOf(2,(dimensions.first*scale).toInt()); val h=maxOf(2,(dimensions.second*scale).toInt())
        val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output); canvas.drawColor(state.background)
        val fit=minOf(w.toFloat()/media.width,h.toFloat()/media.height)*c.zoom
        val mw=media.width*fit; val mh=media.height*fit
        val motion=com.lumen.studio.graphics.MotionFrame(); com.lumen.studio.graphics.ClipTimelineEvaluator(c).evaluate(local,motion)
        canvas.save(); canvas.translate(motion.x*w,-motion.y*h); canvas.rotate(motion.rotation,w/2f,h/2f); canvas.scale(motion.scaleX,motion.scaleY,w/2f,h/2f)
        canvas.drawBitmap(media,null,RectF((w-mw)/2,(h-mh)/2,(w+mw)/2,(h+mh)/2),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter=ColorMatrixColorFilter(TimelineEngine.color(TimelineEngine.visualAt(c.copy(visuals=c.visuals.orEmpty()+state.projectVisuals.orEmpty().map { it.copy(startMs=it.startMs-offset,endMs=it.endMs-offset) }),local))); alpha=(c.opacity.coerceIn(0f,1f)*motion.alpha*255).toInt()
        })
        canvas.restore()
        if(media!==source) media.recycle()
        val length=TimelineBounds.duration(state)
        val rendered=c.copy(visuals=c.visuals.orEmpty()+state.projectVisuals.orEmpty().map { it.copy(startMs=it.startMs-offset,endMs=it.endMs-offset) },
            texts=c.texts+state.projectTexts.orEmpty().filter { it.startMs<offset+ClipEdits.duration(c) && (it.endMs==0L || it.endMs>offset) }.map { it.copy(startMs=(it.startMs-offset).coerceAtLeast(0),endMs=(if(it.endMs>0) it.endMs else length)-offset,animationOffsetMs=(offset-it.startMs).coerceAtLeast(0)) },
            overlays=c.overlays+state.projectOverlays.orEmpty().filter { it.startMs<offset+ClipEdits.duration(c) && (it.endMs==0L || it.endMs>offset) }.map { it.copy(startMs=(it.startMs-offset).coerceAtLeast(0),endMs=(if(it.endMs>0) it.endMs else length)-offset) })
        val preset=com.lumen.studio.graphics.GraphicsCatalog.current?.effect(TimelineEngine.visualAt(rendered,local).effect)
        var processed=if(preset!=null) {
            try {
                val renderer=gpu.get() ?: com.lumen.studio.graphics.BitmapShaderRenderer().also { gpu.set(it) }
                renderer.render(output,preset,at*1000)
            } finally { output.recycle() }
        } else output
        if(motion.blur>0 || motion.wave>0 || motion.glitch>0) {
            val renderer=gpu.get() ?: com.lumen.studio.graphics.BitmapShaderRenderer().also { gpu.set(it) }
            val pixels=com.lumen.studio.graphics.ShaderPreset(com.lumen.studio.graphics.PresetInfo("clip-motion-pixels","Motion","Clip"),"motion",mapOf("uBlur" to listOf(motion.blur),"uWave" to listOf(motion.wave),"uGlitch" to listOf(motion.glitch)))
            val original=processed
            processed=try { renderer.render(original,pixels,at*1000) } finally { original.recycle() }
        }
        TimelineEngine.drawLayers(context,Canvas(processed),rendered,w,h,local)
        return processed
    }
    private fun read(context: Context,c: EditorClip,time: Long): Bitmap {
        if(c.video) {
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(context,Uri.parse(c.uri))
                return (if(android.os.Build.VERSION.SDK_INT>=27) retriever.getScaledFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST,720,720)
                    else retriever.getFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST)) ?: error("Unable to decode this video frame")
            } finally { retriever.release() }
        }
        val uri=Uri.parse(c.uri)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        var sample=1; while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1440) sample*=2
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: error("Unable to open image")
    }
}
