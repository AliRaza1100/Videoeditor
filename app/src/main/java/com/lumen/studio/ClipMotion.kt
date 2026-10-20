package com.lumen.studio

import com.lumen.studio.graphics.GraphicsCatalog
import com.lumen.studio.graphics.OverlayPose
import com.lumen.studio.graphics.AnimationPreset

/** The same pose drives paused Canvas frames and Media3 GPU matrices. */
object ClipMotion {
    fun pose(clip: EditorClip,timeMs: Long): OverlayPose {
        val out=com.lumen.studio.graphics.MotionFrame(); com.lumen.studio.graphics.ClipTimelineEvaluator(clip).evaluate(timeMs,out)
        return OverlayPose(out.x,out.y,out.scaleX,out.scaleY,out.rotation,out.alpha)
    }
    fun sample(preset: AnimationPreset?,durationMs: Long,clipDurationMs: Long,timeMs: Long): OverlayPose {
        if(preset==null) return OverlayPose()
        val duration=durationMs.coerceAtLeast(100).coerceAtMost(clipDurationMs.coerceAtLeast(1))
        val fraction=(timeMs.toDouble()/duration.coerceAtLeast(1)).coerceIn(0.0,1.0)
        return preset.pose((fraction*preset.durationMs).toLong())
    }
}
