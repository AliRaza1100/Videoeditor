package com.lumen.studio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** On-demand playback proxy, using exactly the export composition instead of a second GL player. */
@UnstableApi
class RenderedPlayback(private val context: Context) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var revision=0
    private var transformer: Transformer?=null
    private var pending: File?=null
    private var cached: File?=null
    private var cachedState: EditorState?=null
    private var poll: Runnable?=null
    fun prepare(state: EditorState,onProgress: (Int)->Unit,onReady: (File)->Unit,onError: (Exception)->Unit) {
        check(Looper.myLooper()==Looper.getMainLooper())
        cancel(); val current=revision
        cached?.takeIf { cachedState==state && it.isFile && it.length()>0 }?.let { onReady(it); return }
        val directory=File(context.cacheDir,"playback").apply { mkdirs() }
        val output=File(directory,"${UUID.randomUUID()}.mp4"); pending=output
        onProgress(0)
        worker.execute {
            try {
                if(current!=revision) return@execute
                val size=TimelineEngine.canvasSize(context,state)
                val edge=(minOf(size.first,size.second)*minOf(1f,720f/maxOf(size.first,size.second))).toInt().coerceAtLeast(2)
                val plan=TimelineEngine.build(context,state,exportResolution=edge)
                main.post {
                    if(current!=revision) return@post
                    fun fail(error: Exception) {
                        if(current!=revision) return
                        transformer?.cancel(); transformer=null; output.delete(); pending=null
                        poll?.let { main.removeCallbacks(it) }; poll=null
                        onError(error)
                    }
                    try {
                        transformer=Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .addListener(object: Transformer.Listener {
                                override fun onCompleted(composition: Composition,result: ExportResult) {
                                    if(current!=revision) { output.delete(); return }
                                    poll?.let { main.removeCallbacks(it) }; poll=null; transformer=null; pending=null
                                    if(!output.isFile || output.length()==0L) { fail(IllegalStateException("Playback preparation produced an empty file.")); return }
                                    cached?.takeIf { it!=output }?.delete(); cached=output; cachedState=state; onReady(output)
                                }
                                override fun onError(composition: Composition,result: ExportResult,error: ExportException) { fail(error) }
                            }).build()
                        transformer!!.start(plan.composition,output.absolutePath)
                        var previous=-1; var changedAt=SystemClock.elapsedRealtime()
                        poll=object: Runnable {
                            override fun run() {
                                if(current!=revision || transformer==null) return
                                val holder=ProgressHolder()
                                if(transformer!!.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE) {
                                    if(holder.progress!=previous) { previous=holder.progress; changedAt=SystemClock.elapsedRealtime(); onProgress(previous) }
                                }
                                if(SystemClock.elapsedRealtime()-changedAt>60000) { fail(IllegalStateException("Playback preparation stalled. Try a shorter clip or another video format.")); return }
                                main.postDelayed(this,250)
                            }
                        }.also { main.post(it) }
                    } catch(error: Exception) { fail(error) }
                }
            } catch(error: Exception) { main.post { if(current==revision) { pending?.delete(); pending=null; onError(error) } } }
        }
    }
    fun cancel() {
        check(Looper.myLooper()==Looper.getMainLooper())
        revision++; poll?.let { main.removeCallbacks(it) }; poll=null
        transformer?.cancel(); transformer=null; pending?.delete(); pending=null
    }
    fun release() { cancel(); cached?.delete(); cached=null; cachedState=null; worker.shutdown() }
}
