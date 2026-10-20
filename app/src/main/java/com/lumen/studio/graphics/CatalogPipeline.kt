package com.lumen.studio.graphics

import android.content.Context
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lumen.studio.*

@UnstableApi
object CatalogPipeline {
    /** One GPU pass per shader family used by this clip, independent of catalog/region count. */
    fun pixelEffects(clip: EditorClip,timelineOffsetMs: Long=0): List<Effect> {
        val catalog=GraphicsCatalog.current ?: return emptyList()
        val names=(listOf(clip.effect)+clip.visuals.orEmpty().filter { it.kind=="effect" }.map { it.name }).distinct()
        return names.mapNotNull { catalog.effect(it) }.groupBy { it.shader }.map { (shader,presets) ->
            CatalogShaderEffect(presets.first(),catalog.shaders.getValue(shader),timelineOffsetUs=timelineOffsetMs*1000,
                select={ timeUs ->
                    val timeMs=timeUs/1000
                    val selected=clip.visuals.orEmpty().lastOrNull { it.kind=="effect" && timeMs>=it.startMs && timeMs<it.endMs }?.name ?: clip.effect
                    catalog.effect(selected)?.takeIf { it.shader==shader }
                })
        }
    }
}

/** Public ExoPlayer surface path for an individual video. Call all methods on the main thread.
 * Full multi-track playback continues to use the app's rendered-composition preview cache.
 */
@UnstableApi
class SurfacePreviewSession(context: Context,surface: SurfaceView,listener: Player.Listener): AutoCloseable {
    private val app=context.applicationContext
    private var closed=false
    private val player=ExoPlayer.Builder(app).setLooper(Looper.getMainLooper()).build().apply { setVideoSurfaceView(surface); addListener(listener) }
    private fun checkThread() { check(Looper.myLooper()==Looper.getMainLooper()); check(!closed) }
    fun setClip(clip: EditorClip,state: EditorState,width: Int,height: Int) {
        checkThread()
        require(clip.video && clip.speed==1f && clip.speedCurve.orEmpty().size<2 && !clip.reverse) { "Use a prepared composition for still images, reverse or speed curves" }
        require(clip.audioFadeInMs==0L && clip.audioFadeOutMs==0L && clip.audioVolume in 0f..1f) { "Use a prepared composition for audio fades or amplified audio" }
        require(state.projectSounds.orEmpty().isEmpty() && clip.sounds.isEmpty() && state.projectTexts.orEmpty().isEmpty() && state.projectOverlays.orEmpty().isEmpty() && state.projectVisuals.orEmpty().isEmpty()) { "Use a prepared composition for project-level layers and mixed audio" }
        player.stop()
        player.setVideoEffects(TimelineEngine.effects(app,clip,state,width,height))
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs.toLong()).apply { if(clip.endMs>0) setEndPositionMs(clip.endMs.toLong()) }.build()).build())
        player.volume=if(clip.muted) 0f else clip.audioVolume.coerceIn(0f,1f); player.prepare()
    }
    fun play() { checkThread(); player.play() }
    fun pause() { checkThread(); player.pause() }
    fun seekTo(positionMs: Long) { checkThread(); player.seekTo(positionMs.coerceAtLeast(0)) }
    override fun close() { if(closed) return; checkThread(); closed=true; player.clearVideoSurface(); player.release() }
}
