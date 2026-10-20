package com.lumen.studio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object VideoExport {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    @Volatile private var pendingTemporary: File? = null
    @Volatile private var generation = 0
    @Volatile var busy = false; private set
    @Volatile var progress = 0; private set
    @Volatile var message = ""; private set
    @Volatile var projectId = ""; private set
    @Volatile var outputSize = ""; private set
    @Volatile var destination = ""; private set
    fun start(context: Context,id: String,state: EditorState,destination: Uri,resolution: Int=720) {
        check(!busy)
        val app=context.applicationContext; val job=++generation
        busy=true; outputSize=""; this.destination=""; progress=0; message="Preparing export…"; projectId=id
        val temporary=File(app.cacheDir,"export-$job.mp4")
        pendingTemporary=temporary
        worker.execute {
            try {
                val plan=TimelineEngine.build(app,state,exportResolution=resolution)
                val fps=state.clips.filter { it.video }.map { clip ->
                    val reader=android.media.MediaMetadataRetriever()
                    try { reader.setDataSource(app,Uri.parse(clip.uri)); reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f } catch(_: Exception) { 30f } finally { reader.release() }
                }.maxOrNull() ?: 30f
                val bitrate=ExportQuality.bitrate(plan.width,plan.height,fps)
                main.post {
                    if(job != generation) { temporary.delete(); return@post }
                    try {
                        transformer=Transformer.Builder(app).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .setEncoderFactory(DefaultEncoderFactory.Builder(app).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).setiFrameIntervalSeconds(1f).build()).build())
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition,result: ExportResult) {
                                    if(job!=generation) { temporary.delete(); return }
                                    transformer=null
                                    worker.execute copy@{
                                        try {
                                            if(job!=generation) return@copy
                                            val metadata=android.media.MediaMetadataRetriever()
                                            try {
                                                metadata.setDataSource(temporary.absolutePath)
                                                val w=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                                val h=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                                val rotation=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                                                val width=w?.toIntOrNull() ?: error("Exported video has no width")
                                                val height=h?.toIntOrNull() ?: error("Exported video has no height")
                                                val actualWidth=if(rotation%180==0) width else height
                                                val actualHeight=if(rotation%180==0) height else width
                                                check(actualWidth==plan.width && actualHeight==plan.height) { "This device could not encode ${resolution}p at ${plan.width}×${plan.height} (produced ${actualWidth}×${actualHeight}). Choose a lower resolution." }
                                                outputSize=if(rotation%180==0) "$w x $h" else "$h x $w"
                                            } finally { metadata.release() }
                                            if(job!=generation) return@copy
                                            app.contentResolver.openOutputStream(destination,"w")?.use { output -> temporary.inputStream().use { it.copyTo(output) } } ?: error("Unable to write export destination.")
                                            if(job == generation) { pendingTemporary=null; this@VideoExport.destination=destination.toString(); progress=100; message="Video exported successfully."; busy=false; AdminConnection.event(app,"video_exported") }
                                        } catch(error: Exception) { fail(job,error) } finally { temporary.delete() }
                                    }
                                }
                                override fun onError(composition: Composition,result: ExportResult,error: ExportException) { temporary.delete(); if(job==generation) transformer=null; fail(job,error) }
                            }).build()
                        message="Exporting video…"; transformer!!.start(plan.composition,temporary.absolutePath); poll(job)
                    } catch(error: Exception) { temporary.delete(); fail(job,error) }
                }
            } catch(error: Exception) { temporary.delete(); fail(job,error) }
        }
    }
    private fun poll(job: Int) {
        if(job != generation || !busy) return
        val holder=ProgressHolder()
        transformer?.let { if(it.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress=holder.progress }
        main.postDelayed({ poll(job) },250)
    }
    private fun fail(job: Int,error: Exception) { android.util.Log.e("LumenExport","Export pipeline failed",error); if(job == generation) { pendingTemporary?.delete(); pendingTemporary=null; message="Export failed: ${error.message ?: "Unsupported media or insufficient storage."}"; busy=false } }
    fun cancel() { generation++; transformer?.cancel(); transformer=null; pendingTemporary?.delete(); pendingTemporary=null; busy=false; progress=0; destination=""; message="Export cancelled." }
}
