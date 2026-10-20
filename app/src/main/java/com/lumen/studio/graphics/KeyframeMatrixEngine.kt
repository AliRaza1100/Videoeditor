package com.lumen.studio.graphics

import com.lumen.studio.*

/** Reusable frame storage: neither key sampling nor timeline evaluation allocates per frame. */
class MotionFrame {
    var x=0f; var y=0f; var scaleX=1f; var scaleY=1f; var rotation=0f; var alpha=1f
    var blur=0f; var wave=0f; var glitch=0f
    fun reset() { x=0f; y=0f; scaleX=1f; scaleY=1f; rotation=0f; alpha=1f; blur=0f; wave=0f; glitch=0f }
}

object KeyframeMatrixEngine {
    /** Normalized t; translation is a fraction of the output canvas. */
    fun evaluate(preset: AnimationPreset?,t: Float,out: MotionFrame) {
        require(t.isFinite())
        out.reset(); if(preset==null) return
        val time=t.coerceIn(0f,1f)
        out.x=preset.tracks["x"]?.sample(time) ?: 0f
        out.y=preset.tracks["y"]?.sample(time) ?: 0f
        out.scaleX=preset.tracks["scaleX"]?.sample(time) ?: 1f
        out.scaleY=preset.tracks["scaleY"]?.sample(time) ?: 1f
        out.rotation=preset.tracks["rotation"]?.sample(time) ?: 0f
        out.alpha=(preset.tracks["alpha"]?.sample(time) ?: 1f).coerceIn(0f,1f)
        out.blur=(preset.tracks["blur"]?.sample(time) ?: 0f).coerceIn(0f,1f)
        out.wave=(preset.tracks["wave"]?.sample(time) ?: 0f).coerceIn(0f,1f)
        out.glitch=(preset.tracks["glitch"]?.sample(time) ?: 0f).coerceIn(0f,1f)
    }
    fun combine(target: MotionFrame,other: MotionFrame) {
        target.x+=other.x; target.y+=other.y; target.scaleX*=other.scaleX; target.scaleY*=other.scaleY
        target.rotation+=other.rotation; target.alpha*=other.alpha
        target.blur=maxOf(target.blur,other.blur); target.wave=maxOf(target.wave,other.wave); target.glitch=maxOf(target.glitch,other.glitch)
    }
}

/** One evaluator per renderer/GL pass; immutable presets are shared, scratch frames are not. */
class ClipTimelineEvaluator(private val clip: EditorClip,private val catalog: GraphicsCatalog?=GraphicsCatalog.current) {
    private val settings=clip.animations?.fittedTo(ClipEdits.duration(clip))
    private val incoming=catalog?.animation(settings?.incoming)
    private val outgoing=catalog?.animation(settings?.outgoing)
    private val combo=catalog?.animation(settings?.combo)
    private val legacy=if(settings==null) catalog?.animation(clip.motion) else null
    private val scratch=MotionFrame()
    private val length=ClipEdits.duration(clip).coerceAtLeast(1)
    private val inDuration=(settings?.inMs ?: 800).coerceIn(1,length)
    private val outDuration=(settings?.outMs ?: 800).coerceIn(1,length)
    fun evaluate(timeMs: Long,out: MotionFrame) {
        out.reset(); val at=timeMs.coerceIn(0,length)
        if(legacy!=null) {
            val duration=(clip.motionDurationMs ?: 4000).coerceAtLeast(1).coerceAtMost(length)
            KeyframeMatrixEngine.evaluate(legacy,at.toFloat()/duration,out); return
        }
        if(combo!=null) {
            val period=(settings?.periodMs ?: 4000).coerceAtLeast(1)
            KeyframeMatrixEngine.evaluate(combo,(at%period).toFloat()/period,out)
        }
        if(incoming!=null && at<=inDuration) {
            KeyframeMatrixEngine.evaluate(incoming,at.toFloat()/inDuration,scratch); KeyframeMatrixEngine.combine(out,scratch)
        }
        if(outgoing!=null && at>=length-outDuration) {
            KeyframeMatrixEngine.evaluate(outgoing,(at-length+outDuration).toFloat()/outDuration,scratch); KeyframeMatrixEngine.combine(out,scratch)
        }
    }
    fun hasPixelEffects()=listOfNotNull(incoming,outgoing,combo,legacy).any { p -> p.tracks.keys.any { it in setOf("blur","wave","glitch") } }
}
