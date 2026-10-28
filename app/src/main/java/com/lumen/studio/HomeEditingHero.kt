package com.lumen.studio

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

/** Bundled, silent motion artwork: no network request or playback outside Home. */
class HomeEditingHero(context: Context): FrameLayout(context) {
    private val video=VideoView(context)
    private val poster=ImageView(context).apply {
        tag="media"; setImageResource(R.drawable.home_editing_poster)
        scaleType=ImageView.ScaleType.CENTER_CROP
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var ready=false
    private var userPaused=false
    private var failed=false
    private val motionAllowed get()=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)!=0f
    private val toggle=Button(context).apply {
        tag="media";text="Pause";isAllCaps=false;minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0
        setOnClickListener { userPaused=!userPaused;updatePlayback() }
        textSize=12f; setTextColor(Color.WHITE)
        background=StudioDesign.shape(context,0xB3181B26.toInt(),22)
    }
    init {
        tag="media"; setBackgroundColor(0xFF181B26.toInt())
        contentDescription=Ui.t(context,"Video and photo editing showcase")
        addView(video,LayoutParams(-1,-1).apply { gravity=Gravity.CENTER })
        addView(poster,LayoutParams(-1,-1))
        video.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(toggle,LayoutParams(Ui.dp(context,76),Ui.dp(context,44),Gravity.TOP or Gravity.END).apply { topMargin=Ui.dp(context,10); marginEnd=Ui.dp(context,12) })
        video.setOnPreparedListener { player ->
            player.isLooping=true; player.setVolume(0f,0f); ready=true
            player.setOnInfoListener { _,what,_ -> if(what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) poster.visibility=View.GONE; false }
            updatePlayback()
        }
        video.setOnErrorListener { _,_,_ -> failed=true; ready=false; poster.visibility=View.VISIBLE; toggle.visibility=View.GONE; true }
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        // Full available width, cinematic 16:9 height on phones and tablets.
        val w=MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(w*9/16,MeasureSpec.EXACTLY))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        failed=false; video.setVideoURI(Uri.parse("android.resource://${context.packageName}/${R.raw.home_editing_showcase}"))
        updatePlayback()
    }
    private fun updatePlayback() {
        val play=motionAllowed && !userPaused && windowVisibility==View.VISIBLE && hasWindowFocus() && isShown
        toggle.visibility=if(motionAllowed && !failed) View.VISIBLE else View.GONE
        toggle.text=Ui.t(context,if(userPaused) "Play" else "Pause")
        toggle.contentDescription=Ui.t(context,if(userPaused) "Play editing showcase" else "Pause editing showcase")
        if(ready) { if(play) video.start() else video.pause() }
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); updatePlayback() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if(isAttachedToWindow) updatePlayback() }
    override fun onDetachedFromWindow() { video.stopPlayback(); ready=false; poster.visibility=View.VISIBLE; super.onDetachedFromWindow() }
}
