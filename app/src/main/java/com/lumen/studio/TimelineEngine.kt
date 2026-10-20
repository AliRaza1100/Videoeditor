package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.transformer.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
data class TimelinePlan(val state: EditorState,val composition: Composition,val starts: List<Long>,val duration: Long,val width: Int,val height: Int,val soundLengths: Map<String,Long> = emptyMap())

/** One composition builder for both real-time preview and MP4 export. */
@UnstableApi
object TimelineEngine {
    fun isVideo(context: Context,uri: String): Boolean {
        val mime=context.contentResolver.getType(Uri.parse(uri))
        return mime?.startsWith("video/") == true || (mime == null && uri.substringBefore('?').substringAfterLast('.').lowercase() in listOf("mp4","mov","mkv","webm","3gp","m4v","avi"))
    }
    fun metadata(context: Context,clip: EditorClip): EditorClip {
        if(!clip.video || (clip.sourceDurationMs > 0 && clip.hasAudio != null)) return clip
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context,Uri.parse(clip.uri))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Unable to read video duration.")
            require(duration > 0)
            clip.copy(sourceDurationMs = duration,hasAudio=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes")
        } finally { retriever.release() }
    }
    private fun hasAudio(context: Context,uri: String): Boolean {
        val retriever=MediaMetadataRetriever()
        return try { retriever.setDataSource(context,Uri.parse(uri)); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes" } finally { retriever.release() }
    }
    fun duration(context: Context,uri: String): Long {
        val retriever = MediaMetadataRetriever()
        return try { retriever.setDataSource(context,Uri.parse(uri)); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 }
        finally { retriever.release() }
    }
    fun build(context: Context,input: EditorState,onlySelected: Boolean = false,exportResolution: Int = 0): TimelinePlan {
        val original = input.normalized()
        val preparedClips = (if(onlySelected) listOf(original.clips[original.active]) else original.clips).map { metadata(context,it) }
        require(preparedClips.isNotEmpty()) { "Add media before exporting." }
        require(preparedClips.none { it.reverse }) { "Reverse encoding is not supported. Turn off reverse before exporting." }
        val state = FrameEditing.migrate(original.copy(clips = preparedClips,active = if(onlySelected) 0 else original.active))
        val clips=state.clips
        val base = canvasSize(context,state)
        val dimensions=if(exportResolution>0) { val scale=exportResolution.toDouble()/minOf(base.first,base.second); maxOf(2,(base.first*scale).toInt()/2*2) to maxOf(2,(base.second*scale).toInt()/2*2) } else base
        val w = dimensions.first; val h = dimensions.second
        val starts = mutableListOf<Long>()
        var length = 0L
        val totalLength=clips.sumOf { ClipEdits.duration(it) }
        val videoItems = clips.map { c ->
            starts.add(length); length += ClipEdits.duration(c)
            val media = MediaItem.Builder().setUri(c.uri).setMediaId(c.id)
            if(c.video) media.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs.toLong())
                .setEndPositionMs(if(c.endMs > 0) c.endMs.toLong() else C.TIME_END_OF_SOURCE).build())
            else media.setMimeType(imageMime(context,c.uri)).setImageDurationMs(c.imageDurationMs)
            val offset=starts.last()
            val renderClip=c.copy(visuals=c.visuals.orEmpty()+state.projectVisuals.orEmpty().filter { it.startMs<offset+ClipEdits.duration(c) && it.endMs>offset }.map { it.copy(startMs=(it.startMs-offset).coerceAtLeast(0),endMs=it.endMs-offset) },
                texts=c.texts+state.projectTexts.orEmpty().filter { it.startMs<offset+ClipEdits.duration(c) && (it.endMs==0L || it.endMs>offset) }.map { it.copy(startMs=(it.startMs-offset).coerceAtLeast(0),endMs=(if(it.endMs>0) it.endMs else totalLength)-offset,animationOffsetMs=(offset-it.startMs).coerceAtLeast(0)) },
                overlays=c.overlays+state.projectOverlays.orEmpty().filter { it.startMs<offset+ClipEdits.duration(c) && (it.endMs==0L || it.endMs>offset) }.map { it.copy(startMs=(it.startMs-offset).coerceAtLeast(0),endMs=(if(it.endMs>0) it.endMs else totalLength)-offset) })
            val audioEffects = mutableListOf<AudioProcessor>()
            val videoEffects = effects(context,renderClip,state,w,h,offset).toMutableList()
            if((c.speed != 1f || c.speedCurve.orEmpty().size>1) && c.video) {
                val speed = object : SpeedProvider {
                    override fun getSpeed(timeUs: Long) = ClipEdits.speedAt(c,(timeUs/20000*20000)/1000.0/ClipEdits.sourceLength(c))
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = if(c.speedCurve.orEmpty().size>1 && timeUs+20000 < ClipEdits.sourceLength(c)*1000) (timeUs/20000+1)*20000 else C.TIME_UNSET
                }
                if(!c.muted && c.hasAudio==true) audioEffects.add(if(c.speedCurve.orEmpty().size>1) SpeedChangingAudioProcessor(speed) else androidx.media3.common.audio.SonicAudioProcessor().apply { setSpeed(c.speed); setPitch(1f) }); videoEffects.add(0,TimestampAdjustment({ inputUs, consumer -> consumer.onTimestamp((ClipEdits.outputPosition(c,inputUs/1000.0)*1000).toLong()) },speed))
            }
            if(!c.muted && c.hasAudio==true) audioEffects.add(GainProcessor(c.audioVolume,c.audioFadeInMs,c.audioFadeOutMs,ClipEdits.duration(c)))
            EditedMediaItem.Builder(media.build()).setDurationUs((if(c.video) c.sourceDurationMs else c.imageDurationMs)*1000)
                .apply { if(!c.video) setFrameRate(30) }.setRemoveAudio(c.muted).setEffects(Effects(audioEffects,videoEffects)).build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(videoItems).experimentalSetForceAudioTrack(clips.any { it.video && !it.muted && hasAudio(context,it.uri) }).build())
        val soundLengths=mutableMapOf<String,Long>()
        fun addSound(sound: SoundLayer,extent: Long,origin: Long,key: String) {
            val sourceDuration=duration(context,sound.uri)
            val available=minOf(sourceDuration,if(sound.sourceEndMs>0) sound.sourceEndMs else sourceDuration)-sound.sourceStartMs
            val lengthMs=minOf((available/sound.speed.coerceAtLeast(.25f)).toLong(),extent-sound.startMs,if(sound.endMs>0) sound.endMs-sound.startMs else Long.MAX_VALUE)
            soundLengths[key]=lengthMs.coerceAtLeast(0)
            if(lengthMs<=0) return
            val media=MediaItem.Builder().setUri(sound.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(sound.sourceStartMs).setEndPositionMs(minOf(sourceDuration,sound.sourceStartMs+(lengthMs*sound.speed).toLong())).build()).build()
            val processors=mutableListOf<AudioProcessor>()
            if(sound.speed!=1f) processors.add(androidx.media3.common.audio.SonicAudioProcessor().apply { setSpeed(sound.speed); setPitch(1f) })
            processors.add(GainProcessor(if(state.musicMuted==true) 0f else sound.volume,sound.fadeInMs,sound.fadeOutMs,lengthMs))
            val audio=EditedMediaItem.Builder(media).setDurationUs(sourceDuration*1000).setRemoveVideo(true).setEffects(Effects(processors,emptyList())).build()
            val sequence=EditedMediaItemSequence.Builder().experimentalSetForceAudioTrack(true)
            val offset=origin+sound.startMs
            if(offset>0) sequence.addGap(offset*1000)
            sequence.addItem(audio)
            val after=length-offset-lengthMs
            if(after>0) sequence.addGap(after*1000)
            sequences.add(sequence.build())
        }
        clips.forEachIndexed { index,c -> c.sounds.forEach { addSound(it,ClipEdits.duration(c),starts[index],"${c.id}:${it.id}") } }
        state.projectSounds.orEmpty().forEach { addSound(it,length,0,"global:${it.id}") }
        val composition = Composition.Builder(sequences).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
        return TimelinePlan(state,composition,starts,length,w,h,soundLengths)
    }
    private fun imageMime(context: Context,uri: String): String = context.contentResolver.getType(Uri.parse(uri))
        ?: if(uri.substringBefore('?').endsWith(".png",true)) MimeTypes.IMAGE_PNG else MimeTypes.IMAGE_JPEG
    internal fun canvasSize(context: Context,state: EditorState): Pair<Int,Int> { return when(state.ratio) {
        "9:16" -> 720 to 1280
        "16:9" -> 1280 to 720
        "1:1" -> 720 to 720
        else -> {
            if(state.ratio.contains(':')) {
                val parts=state.ratio.split(':').map { it.toFloat() }; val scale=1280f/maxOf(parts[0],parts[1])
                return (parts[0]*scale).toInt()/2*2 to (parts[1]*scale).toInt()/2*2
            }
            val c = state.clips.first()
            var w = 1280; var h = 720
            if(c.video) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context,Uri.parse(c.uri))
                    w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: w
                    h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: h
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if(rotation % 180 != 0) { val old = w; w = h; h = old }
                } finally { retriever.release() }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(c.uri))?.use { BitmapFactory.decodeStream(it,null,bounds) }
                if(bounds.outWidth > 0 && bounds.outHeight > 0) { w = bounds.outWidth; h = bounds.outHeight }

            }
            val scale = minOf(1f,1280f/maxOf(w,h))
            (maxOf(2,(w*scale).toInt()/2*2)) to (maxOf(2,(h*scale).toInt()/2*2))
        }
    }
    }
    internal fun effects(context: Context,c: EditorClip,state: EditorState,w: Int,h: Int,timelineOffsetMs: Long=0): List<Effect> {
        val list = mutableListOf<Effect>()
        list.add(object : RgbMatrix {
            private val motion=com.lumen.studio.graphics.ClipTimelineEvaluator(c)
            private val frame=com.lumen.studio.graphics.MotionFrame()
            private var lastOpacity=-1f
            private var lastFilter: String?=null
            private val output=FloatArray(16)
            override fun getMatrix(presentationTimeUs: Long,useHdr: Boolean): FloatArray {
                val timeMs=presentationTimeUs/1000
                val filter=c.visuals.orEmpty().lastOrNull { it.kind=="filter" && timeMs>=it.startMs && timeMs<it.endMs }?.name ?: c.filter
                motion.evaluate(timeMs,frame); val opacity=c.opacity*frame.alpha
                if(filter==lastFilter && opacity==lastOpacity) return output
                lastOpacity=opacity
                val matrix = color(c.copy(filter=filter)).array
                for(row in 0..2) {
                    for(column in 0..2) output[column*4+row] = matrix[row*5+column]*opacity
                    val background = when(row) { 0 -> Color.red(state.background); 1 -> Color.green(state.background); else -> Color.blue(state.background) } / 255f
                    output[12+row] = matrix[row*5+4]/255f*opacity+background*(1-opacity)
                }
                output[15] = 1f
                lastFilter=filter
                return output
            }
        })
        if(c.cropLeft>-1f || c.cropRight<1f || c.cropBottom>-1f || c.cropTop<1f) list.add(Crop(c.cropLeft,c.cropRight,c.cropBottom,c.cropTop))
        list.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-c.rotation)
            .setScale(c.zoom*if(c.flip) -1f else 1f,c.zoom*if(c.flipVertical) -1f else 1f).build())
        list.add(Presentation.createForWidthAndHeight(w,h,Presentation.LAYOUT_SCALE_TO_FIT))
        if(!c.motion.isNullOrBlank() || c.animations!=null) list.add(object: MatrixTransformation {
            private val matrix=Matrix()
            private val evaluator=com.lumen.studio.graphics.ClipTimelineEvaluator(c)
            private val pose=com.lumen.studio.graphics.MotionFrame()
            override fun getMatrix(presentationTimeUs: Long): Matrix {
                evaluator.evaluate(presentationTimeUs/1000,pose)
                matrix.reset(); matrix.postScale(pose.scaleX*w,pose.scaleY*h); matrix.postRotate(-pose.rotation); matrix.postScale(1f/w,1f/h); matrix.postTranslate(pose.x*2,pose.y*2)
                return matrix
            }
        })
        val motionPixels=com.lumen.studio.graphics.ClipTimelineEvaluator(c)
        if(motionPixels.hasPixelEffects()) {
            val catalog=com.lumen.studio.graphics.GraphicsCatalog.current!!
            val preset=com.lumen.studio.graphics.ShaderPreset(com.lumen.studio.graphics.PresetInfo("clip-motion-pixels","Clip motion","Clip"),"motion",mapOf("uBlur" to listOf(0f),"uWave" to listOf(0f),"uGlitch" to listOf(0f)))
            list.add(com.lumen.studio.graphics.CatalogShaderEffect(preset,catalog.shaders.getValue("motion"),timelineOffsetUs=timelineOffsetMs*1000,motion=motionPixels))
        }
        list.addAll(com.lumen.studio.graphics.CatalogPipeline.pixelEffects(c,timelineOffsetMs))
        if(c.texts.isNotEmpty() || c.overlays.isNotEmpty() || c.effect != "None" || c.visuals.orEmpty().any { it.kind=="effect" }) {
            list.add(OverlayEffect(listOf(object: CanvasOverlay(true) {
                override fun onDraw(canvas: Canvas,presentationTimeUs: Long) {
                    canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
                    drawLayers(context,canvas,c,w,h,presentationTimeUs/1000)
                }
            })))
        }
        return list
    }
    internal fun visualAt(c: EditorClip,timeMs: Long): EditorClip {
        val active=c.visuals.orEmpty().filter { timeMs>=it.startMs && timeMs<it.endMs }
        return c.copy(filter=active.lastOrNull { it.kind=="filter" }?.name ?: c.filter,effect=active.lastOrNull { it.kind=="effect" }?.name ?: c.effect)
    }
    internal fun layers(context: Context,c: EditorClip,w: Int,h: Int,timeMs: Long = 0,base: Bitmap? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if(base != null) canvas.drawBitmap(base,0f,0f,null)
        drawLayers(context,canvas,c,w,h,timeMs,base==null)
        return bitmap
    }
    internal fun drawLayers(context: Context,canvas: Canvas,c: EditorClip,w: Int,h: Int,timeMs: Long,drawEffect: Boolean=true) {
        val active=visualAt(c,timeMs)
        if(drawEffect && active.effect != "None" && com.lumen.studio.graphics.GraphicsCatalog.current?.effect(active.effect)==null) {
            if(active.effect=="Frame") FrameEffectRenderer.draw(context,canvas,c,c.visuals.orEmpty().lastOrNull { it.kind=="effect" && timeMs>=it.startMs && timeMs<it.endMs },w,h)
            else EditorPresets.drawEffect(canvas,active.effect,w.toFloat(),h.toFloat())
        }
        c.overlays.forEach { layer ->
            if(timeMs<layer.startMs || (layer.endMs>0 && timeMs>=layer.endMs)) return@forEach
            val image = decode(context,layer.uri) ?: error("Unable to open an overlay image.")
            val width = w*layer.scale; val height = width*image.height/image.width
            val centerX = w*(layer.x+1)/2; val centerY = h*(1-layer.y)/2
            canvas.save(); canvas.rotate(layer.rotation,centerX,centerY); if(layer.flip) canvas.scale(-1f,1f,centerX,centerY)
            canvas.drawBitmap(image,null,RectF(centerX-width/2,centerY-height/2,centerX+width/2,centerY+height/2),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (layer.opacity*255).toInt() }); canvas.restore()
        }
        c.texts.forEach { layer -> TextPresets.draw(canvas,layer,w,h,timeMs) }

    }
    private val imageCache=object : android.util.LruCache<String,Bitmap>(24*1024) { override fun sizeOf(key: String,value: Bitmap)=(value.allocationByteCount/1024).coerceAtLeast(1) }
    private fun decode(context: Context,uri: String): Bitmap? {
        imageCache.get(uri)?.let { if(!it.isRecycled) return it }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it,null,options) }
        var sample = 1
        while(options.outWidth/sample > 2048 || options.outHeight/sample > 2048) sample *= 2
        return context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize = sample }) }?.also { imageCache.put(uri,it) }
    }
    fun color(c: EditorClip): ColorMatrix {
        com.lumen.studio.graphics.GraphicsCatalog.current?.let { catalog ->
            return com.lumen.studio.graphics.CatalogColorEngine.matrix(catalog,mapOf("saturation" to c.saturation,"contrast" to c.contrast,"brightness" to c.brightness,"exposure" to c.exposure,"temperature" to c.temperature,"tint" to c.tint)+c.catalogAdjustments.orEmpty(),c.filter,c.filterIntensity)
        }
        val matrix=ColorMatrix().apply { setSaturation(c.saturation) }
        val gain=c.contrast; val offset=c.brightness+128*(1-gain)
        matrix.postConcat(ColorMatrix(floatArrayOf(gain,0f,0f,0f,offset,0f,gain,0f,0f,offset,0f,0f,gain,0f,offset,0f,0f,0f,1f,0f)))
        val filter=EditorPresets.matrix(c.filter) ?: when(c.filter) {
            "Mono" -> ColorMatrix().apply { setSaturation(0f) }
            "Warm" -> ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,6f,0f,1f,0f,0f,0f,0f,0f,.85f,0f,0f,0f,0f,0f,1f,0f))
            "Cool" -> ColorMatrix(floatArrayOf(.85f,0f,0f,0f,0f,0f,1f,0f,0f,0f,0f,0f,1.1f,0f,6f,0f,0f,0f,1f,0f))
            "Vintage" -> ColorMatrix(floatArrayOf(.393f,.769f,.189f,0f,0f,.349f,.686f,.168f,0f,0f,.272f,.534f,.131f,0f,0f,0f,0f,0f,1f,0f))
            "Cinematic" -> ColorMatrix().apply { setSaturation(.8f); postConcat(ColorMatrix(floatArrayOf(1.15f,0f,0f,0f,-10f,0f,1.08f,0f,0f,-8f,0f,0f,1f,0f,-5f,0f,0f,0f,1f,0f))) }
            "Natural" -> ColorMatrix().apply { setSaturation(.9f) }
            "Bright" -> ColorMatrix(floatArrayOf(1f,0f,0f,0f,18f,0f,1f,0f,0f,18f,0f,0f,1f,0f,18f,0f,0f,0f,1f,0f))
            "High Contrast" -> ColorMatrix(floatArrayOf(1.25f,0f,0f,0f,-32f,0f,1.25f,0f,0f,-32f,0f,0f,1.25f,0f,-32f,0f,0f,0f,1f,0f))
            else -> ColorMatrix()
        }
        val original=ColorMatrix().array; val blend=filter.array; val t=c.filterIntensity.coerceIn(0f,1f)
        matrix.postConcat(ColorMatrix(FloatArray(20) { original[it]*(1-t)+blend[it]*t }))
        val exposure=Math.pow(2.0,c.exposure.toDouble()).toFloat()
        matrix.postConcat(ColorMatrix(floatArrayOf(exposure*(1+c.temperature*.15f),0f,0f,0f,0f,0f,exposure*(1-c.tint*.15f),0f,0f,0f,0f,0f,exposure*(1-c.temperature*.15f),0f,0f,0f,0f,0f,1f,0f)))
        return matrix
    }
}

@UnstableApi
private class GainProcessor(private val volume: Float,private val fadeInMs: Long=0,private val fadeOutMs: Long=0,private val durationMs: Long=0): BaseAudioProcessor() {
    private var samples=0L
    private var sampleRate=48000
    private var channels=1
    override fun onFlush() { samples=0 }
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if(inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        sampleRate=inputAudioFormat.sampleRate; channels=inputAudioFormat.channelCount
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        while(inputBuffer.remaining() >= 2) {
            val time=samples.toDouble()/channels/sampleRate*1000
            val fade=minOf(if(fadeInMs>0) (time/fadeInMs).coerceIn(0.0,1.0) else 1.0,if(fadeOutMs>0) ((durationMs-time)/fadeOutMs).coerceIn(0.0,1.0) else 1.0)
            output.putShort((inputBuffer.short*volume*fade).toInt().coerceIn(-32768,32767).toShort()); samples++
        }
        output.flip()
    }
}




