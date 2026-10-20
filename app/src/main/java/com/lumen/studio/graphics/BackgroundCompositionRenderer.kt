package com.lumen.studio.graphics

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import com.lumen.studio.EditorState
import com.lumen.studio.ExportQuality
import com.lumen.studio.TimelineEngine
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed interface RenderEvent {
    data class Progress(val percent: Int): RenderEvent
    /** Receiver owns the completed private file; save or delete it when finished. */
    data class Completed(val file: File,val width: Int,val height: Int): RenderEvent
    data class Failed(val cause: Exception): RenderEvent
    data object Cancelled: RenderEvent
}

/** UI-independent, single-job owner. Bind close to the owner lifecycle or keep in a ViewModel.
 * This is not an Android foreground service and does not promise process-death persistence.
 */
@UnstableApi
class BackgroundCompositionRenderer(context: Context,private val callbackExecutor: Executor,private val listener: (RenderEvent)->Unit): AutoCloseable {
    private val app=context.applicationContext
    private val thread=HandlerThread("LumenTransformer").apply { start() }
    private val handler=Handler(thread.looper)
    private val preparation=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(1),{ task -> Thread(task,"LumenComposition") },ThreadPoolExecutor.DiscardOldestPolicy())
    private val revision=AtomicLong()
    private val closed=AtomicBoolean()
    // Access only from handler's looper.
    private var transformer: Transformer?=null
    private var temporary: File?=null
    private fun emit(job: Long,event: RenderEvent) {
        if(!closed.get() && revision.get()==job) callbackExecutor.execute {
            if(!closed.get() && revision.get()==job) listener(event)
            else if(event is RenderEvent.Completed) event.file.delete()
        } else if(event is RenderEvent.Completed) event.file.delete()
    }
    @Synchronized fun render(state: EditorState,resolution: Int=720) {
        check(!closed.get()); require(resolution in 144..4320)
        val job=revision.incrementAndGet()
        handler.post { transformer?.cancel(); transformer=null; temporary?.delete(); temporary=null }
        preparation.execute {
            try {
                if(job!=revision.get() || closed.get()) return@execute
                val plan=TimelineEngine.build(app,state,exportResolution=resolution)
                handler.post {
                    if(job!=revision.get() || closed.get()) return@post
                    val output=File(app.cacheDir,"render-${UUID.randomUUID()}.mp4")
                    temporary=output
                    try {
                        val encoder=DefaultEncoderFactory.Builder(app).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(ExportQuality.bitrate(plan.width,plan.height,30f)).setiFrameIntervalSeconds(1f).build()).build()
                        val owner=Transformer.Builder(app).setLooper(thread.looper).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoder)
                            .addListener(object: Transformer.Listener {
                                override fun onCompleted(composition: Composition,result: ExportResult) {
                                    if(job!=revision.get() || closed.get()) { output.delete(); return }
                                    transformer=null; temporary=null
                                    if(result.width!=plan.width || result.height!=plan.height) {
                                        output.delete(); emit(job,RenderEvent.Failed(IllegalStateException("Encoder changed requested dimensions ${plan.width}×${plan.height} to ${result.width}×${result.height}")))
                                    } else emit(job,RenderEvent.Completed(output,plan.width,plan.height))
                                }
                                override fun onError(composition: Composition,result: ExportResult,error: ExportException) {
                                    output.delete()
                                    if(job==revision.get()) { transformer=null; temporary=null; emit(job,RenderEvent.Failed(error)) }
                                }
                            }).build()
                        transformer=owner; owner.start(plan.composition,output.absolutePath)
                        fun poll() {
                            if(job!=revision.get() || closed.get() || transformer!==owner) return
                            val holder=ProgressHolder()
                            if(owner.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE) emit(job,RenderEvent.Progress(holder.progress))
                            handler.postDelayed({ poll() },250)
                        }
                        poll()
                    } catch(error: Exception) { transformer?.cancel(); transformer=null; temporary=null; output.delete(); emit(job,RenderEvent.Failed(error)) }
                }
            } catch(error: Exception) { emit(job,RenderEvent.Failed(error)) }
        }
    }
    @Synchronized fun cancel() {
        if(closed.get()) return
        val job=revision.incrementAndGet()
        handler.post { transformer?.cancel(); transformer=null; temporary?.delete(); temporary=null; emit(job,RenderEvent.Cancelled) }
    }
    @Synchronized override fun close() {
        if(!closed.compareAndSet(false,true)) return
        revision.incrementAndGet(); preparation.shutdownNow()
        handler.post { try { transformer?.cancel(); transformer=null; temporary?.delete(); temporary=null } finally { thread.quitSafely() } }
    }
}
