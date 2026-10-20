package com.lumen.studio

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*

/** Watch an exported file inside the app, with its original aspect ratio. */
class VideoPlayerActivity : BaseActivity() {
    private lateinit var video: VideoView
    private lateinit var frame: FrameLayout
    private lateinit var status: TextView
    private lateinit var play: Button
    private lateinit var seek: SeekBar
    private val handler=Handler(Looper.getMainLooper())
    private var uri=""
    private var position=0
    private var width=0
    private var height=0
    var prepared=false; private set
    private fun dp(value: Int)=Ui.dp(this,value)
    private fun action(value: String,click: ()->Unit)=AppButton(this).apply { text=value; textSize=13f; setTextColor(Color.rgb(0,225,119)); background=null; setOnClickListener { click() } }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        uri=intent.getStringExtra("video_uri") ?: run { finish(); return }
        position=saved?.getInt("position",0) ?: 0
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(12,14,18)) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(action("←") { finish() },LinearLayout.LayoutParams(dp(44),dp(48)))
        header.addView(AppText(this).apply { text="Exported video"; setTextColor(Color.WHITE); textSize=17f },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(action("Share") {
            if(Uri.parse(uri).scheme=="content") startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="video/mp4"; putExtra(Intent.EXTRA_STREAM,Uri.parse(uri)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },Ui.t(this,"Share video")))
        },LinearLayout.LayoutParams(dp(64),dp(48)))
        root.addView(header)
        frame=FrameLayout(this).apply { tag="media"; setBackgroundColor(Color.BLACK) }
        video=VideoView(this); frame.addView(video,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        frame.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> fitVideo() }
        status=AppText(this).apply { text="Loading video…"; setTextColor(Color.WHITE); textSize=12f; setPadding(dp(12),dp(8),dp(12),dp(8)) }; root.addView(status)
        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        play=action("Ⅱ") { if(prepared) { if(video.isPlaying) video.pause() else video.start() } }.apply { contentDescription="Play or pause" }
        controls.addView(play,LinearLayout.LayoutParams(dp(56),dp(48)))
        seek=SeekBar(this).apply { setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { if(user && prepared) video.seekTo(value) }; override fun onStartTrackingTouch(bar: SeekBar?) {}; override fun onStopTrackingTouch(bar: SeekBar?) {} }) }
        controls.addView(seek,LinearLayout.LayoutParams(0,dp(48),1f)); root.addView(controls)
        setContentView(root)
        video.setOnPreparedListener { player -> prepared=true; width=player.videoWidth; height=player.videoHeight; fitVideo(); seek.max=video.duration; video.seekTo(position); video.start(); handler.post(tick) }
        video.setOnCompletionListener { play.text="▶"; video.seekTo(0) }
        video.setOnErrorListener { _,_,_ -> prepared=false; status.text="Unable to play this export. The file may have moved or access was revoked."; true }
        video.setVideoURI(Uri.parse(uri))
    }
    private fun fitVideo() {
        if(width<=0 || height<=0 || frame.width<=0 || frame.height<=0) return
        val scale=minOf(frame.width.toFloat()/width,frame.height.toFloat()/height)
        val w=(width*scale).toInt().coerceAtLeast(1); val h=(height*scale).toInt().coerceAtLeast(1)
        if(video.width!=w || video.height!=h) video.layoutParams=FrameLayout.LayoutParams(w,h,Gravity.CENTER)
    }
    private val tick=object : Runnable { override fun run() { if(!isDestroyed && prepared) { seek.progress=video.currentPosition; play.text=if(video.isPlaying) "Ⅱ" else "▶"; status.text="%02d:%02d / %02d:%02d".format(video.currentPosition/60000,video.currentPosition/1000%60,video.duration/60000,video.duration/1000%60); handler.postDelayed(this,250) } } }
    override fun onPause() { if(::video.isInitialized) { position=video.currentPosition; video.pause() }; super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { if(::video.isInitialized) out.putInt("position",video.currentPosition); super.onSaveInstanceState(out) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); if(::video.isInitialized) video.stopPlayback(); super.onDestroy() }
}
