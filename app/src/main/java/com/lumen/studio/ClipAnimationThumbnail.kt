package com.lumen.studio

import android.content.Context
import android.graphics.Canvas
import android.widget.ImageView
import com.lumen.studio.graphics.*

/** Clip pixels, not text, demonstrate each motion. Drawing uses reusable math storage. */
class ClipAnimationThumbnail(c: Context,private val preset: AnimationPreset?): ImageView(c) {
    private val pose=MotionFrame()
    init { tag="media"; scaleType=ScaleType.CENTER_CROP; setBackgroundColor(0xFF20212A.toInt()); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val running=android.animation.ValueAnimator.areAnimatorsEnabled() && isShown
        val t=if(running) (android.os.SystemClock.uptimeMillis()%2400)/2400f else .5f
        KeyframeMatrixEngine.evaluate(preset,t,pose)
        val save=canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(pose.alpha*255).toInt())
        canvas.translate(pose.x*width,-pose.y*height); canvas.rotate(pose.rotation,width/2f,height/2f); canvas.scale(pose.scaleX,pose.scaleY,width/2f,height/2f)
        super.onDraw(canvas); canvas.restoreToCount(save)
        if(running && preset!=null) postInvalidateDelayed(33)
    }
}
