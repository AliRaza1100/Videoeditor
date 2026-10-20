package com.lumen.studio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors

/** An on-device check users can run without granting media access or uploading anything. */
@UnstableApi
class PlaybackCheckActivity: BaseActivity() {
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var run: Button
    private lateinit var watch: Button
    private lateinit var editor: Button
    private var sample: EditorState?=null
    private var output: File?=null
    private var job=""
    private var revision=0
    var checkPassed=false; private set
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val dp={ n: Int -> Ui.dp(this,n) }
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(StudioDesign.background(this@PlaybackCheckActivity)); setPadding(dp(20),dp(12),dp(20),dp(24)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(StudioDesign.button(this,"←") { finish() }.apply { background=null; contentDescription="Back" },LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(StudioDesign.label(this,"Check video support",23f,bold=true),LinearLayout.LayoutParams(0,-2,1f)); root.addView(header)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        body.addView(StudioDesign.label(this,"A quick check on this device",21f,bold=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(24) })
        body.addView(StudioDesign.label(this,"Create a short sample with two clips, a filter, timed text and sound. The app checks its exported frames, duration and audio track. Everything stays on this device.",14f,secondary=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12); bottomMargin=dp(24) })
        status=StudioDesign.label(this,"Ready to check. No media permission is needed.",14f); body.addView(status,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(20) })
        run=StudioDesign.button(this,"Run video check",primary=true) { beginCheck() }; body.addView(run,LinearLayout.LayoutParams(-1,dp(54)))
        watch=StudioDesign.button(this,"Play test video") { output?.takeIf { it.isFile }?.let { startActivity(Intent(this,VideoPlayerActivity::class.java).putExtra("video_uri",Uri.fromFile(it).toString())) } }.apply { isEnabled=false }
        body.addView(watch,LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(12) })
        editor=StudioDesign.button(this,"Open sample in editor") {
            sample?.let { state -> val id="check-${UUID.randomUUID()}"; ProjectStore.save(this,SavedProject(id,"Playback check",System.currentTimeMillis(),state)); startActivity(Intent(this,EditorActivity::class.java).putExtra("project_id",id)) }
        }.apply { isEnabled=false }; body.addView(editor,LinearLayout.LayoutParams(-1,dp(52)).apply { topMargin=dp(12) })
        body.addView(StudioDesign.label(this,"After a successful check, play the test video and confirm that you can see the picture and hear the tone. Open the sample in the editor to check Play, Pause, seeking and editing. A sample check does not cover every imported format or a full device test matrix.",13f,secondary=true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(24) })
        root.addView(ScrollView(this).apply { addView(body) },LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }
    private fun beginCheck() {
        if(VideoExport.busy || SoundLibrary.busy) { status.text="Finish the current export or audio import first."; return }
        val current=++revision; checkPassed=false; run.isEnabled=false; watch.isEnabled=false; editor.isEnabled=false; status.text="Creating the sample…"
        worker.execute {
            val result=runCatching {
                val folder=File(filesDir,"diagnostics").apply { mkdirs() }
                fun image(name: String,color: Int): String {
                    val file=File(folder,name)
                    Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888).also { bitmap -> try { bitmap.eraseColor(color); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() } }
                    return Uri.fromFile(file).toString()
                }
                val first=image("warm.png",Color.rgb(210,90,40)); val second=image("blue.png",Color.rgb(30,70,220))
                val sound=File(folder,"tone.wav"); val samples=48000*2; val pcm=ByteBuffer.allocate(samples*2).order(ByteOrder.LITTLE_ENDIAN)
                repeat(samples) { pcm.putShort((kotlin.math.sin(it*2*Math.PI*440/48000)*3000).toInt().toShort()) }
                sound.outputStream().use { it.write(waveFileHeader(pcm.array().size,48000,1)); it.write(pcm.array()) }
                EditorState(listOf(EditorClip(first,"Warm frame",false,imageDurationMs=1000,filter="Mono"),EditorClip(second,"Blue frame",false,imageDurationMs=1000)),projectTexts=listOf(TextLayer(text="LUMEN",size=24f,startMs=400,endMs=1600)),projectSounds=listOf(SoundLayer(uri=Uri.fromFile(sound).toString(),name="Test tone",volume=.3f,endMs=2000)))
            }
            runOnUiThread {
                if(isDestroyed || current!=revision) return@runOnUiThread
                result.fold(onSuccess={ state ->
                    try {
                        sample=state; output=File(cacheDir,"video-support-check.mp4"); job="check-${UUID.randomUUID()}"
                        VideoExport.start(this,job,state,Uri.fromFile(output!!),360); poll(current)
                    } catch(error: Exception) { failed(error) }
                },onFailure={ failed(it) })
            }
        }
    }
    private fun poll(current: Int) {
        if(isDestroyed || current!=revision) return
        if(VideoExport.projectId!=job) { failed(IllegalStateException("The check was interrupted. Try again.")); return }
        if(VideoExport.busy) { status.text="${Ui.t(this,"Checking video export")} ${VideoExport.progress}%"; handler.postDelayed({ poll(current) },250); return }
        if(VideoExport.progress!=100 || VideoExport.destination.isEmpty()) { failed(IllegalStateException(VideoExport.message)); return }
        val file=output ?: return
        status.text="Checking the encoded picture and audio…"
        worker.execute {
            val result=runCatching { verify(file) }
            runOnUiThread { if(!isDestroyed && current==revision) result.fold(onSuccess={ checkPassed=true; status.text="Video export check passed: two clips, filtered frame, duration and audio track. Play the video to confirm picture and sound."; run.isEnabled=true; watch.isEnabled=true; editor.isEnabled=true },onFailure={ failed(it) }) }
        }
    }
    private fun verify(file: File) {
        val metadata=MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.absolutePath)
            val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            check(duration in 1800..2600) { "The exported duration was unexpected." }
            val first=metadata.getFrameAtTime(200000,MediaMetadataRetriever.OPTION_CLOSEST) ?: error("No first video frame decoded.")
            val second=metadata.getFrameAtTime(1250000,MediaMetadataRetriever.OPTION_CLOSEST) ?: error("No second video frame decoded.")
            try {
                val gray=first.getPixel(first.width/8,first.height/8); val blue=second.getPixel(second.width/8,second.height/8)
                check(Color.red(gray)>40 && kotlin.math.abs(Color.red(gray)-Color.green(gray))<20 && kotlin.math.abs(Color.green(gray)-Color.blue(gray))<20) { "The filter was missing in the exported frame." }
                check(Color.blue(blue)>Color.red(blue)+70) { "The second clip was missing in the exported frame." }
            } finally { first.recycle(); second.recycle() }
        } finally { metadata.release() }
        val extractor=MediaExtractor()
        try { extractor.setDataSource(file.absolutePath); check((0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/")==true }) { "The exported audio track was missing." } } finally { extractor.release() }
    }
    private fun failed(error: Throwable) { status.text="Video check did not pass: ${error.message ?: "Please try again."}"; run.isEnabled=true; watch.isEnabled=false; editor.isEnabled=false }
    override fun onDestroy() { revision++; handler.removeCallbacksAndMessages(null); if(VideoExport.busy && VideoExport.projectId==job) VideoExport.cancel(); worker.shutdown(); super.onDestroy() }
}
