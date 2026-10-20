package com.lumen.studio

import kotlin.math.*

data class FrameBounds(val left: Float,val top: Float,val right: Float,val bottom: Float) {
    val width get()=right-left
    val height get()=bottom-top
}

/** Outer border follows the visible, aspect-fitted media instead of the letterboxed canvas. */
object FrameGeometry {
    fun bounds(sourceWidth: Int,sourceHeight: Int,w: Int,h: Int,c: EditorClip,layer: VisualLayer?=null): FrameBounds {
        val sw=sourceWidth.coerceAtLeast(1)*(c.cropRight-c.cropLeft).coerceAtLeast(.001f)/2
        val sh=sourceHeight.coerceAtLeast(1)*(c.cropTop-c.cropBottom).coerceAtLeast(.001f)/2
        val radians=Math.toRadians(c.rotation.toDouble())
        val rw=(sw*abs(cos(radians))+sh*abs(sin(radians))).toFloat()
        val rh=(sw*abs(sin(radians))+sh*abs(cos(radians))).toFloat()
        val fit=minOf(w/rw,h/rh)*c.zoom
        val visibleW=minOf(w.toFloat(),rw*fit); val visibleH=minOf(h.toFloat(),rh*fit)
        val scale=(layer?.frameScale ?: 1f).coerceIn(.25f,1.5f)
        val cx=w/2f+(layer?.frameX ?: 0f)*w/2
        val cy=h/2f-(layer?.frameY ?: 0f)*h/2
        return FrameBounds(cx-visibleW*scale/2,cy-visibleH*scale/2,cx+visibleW*scale/2,cy+visibleH*scale/2)
    }
}

object FrameEditing {
    fun migrate(state: EditorState): EditorState {
        var offset=0L; val layers=state.projectVisuals.orEmpty().toMutableList()
        val clips=state.clips.map { c ->
            val start=offset; offset+=ClipEdits.duration(c)
            if(c.effect!="Frame") c else {
                val id="legacy-frame-${c.id}"
                if(layers.none { it.id==id }) layers.add(0,VisualLayer(id=id,kind="effect",name="Frame",startMs=start,endMs=offset))
                c.copy(effect="None")
            }
        }
        return state.copy(clips=clips,projectVisuals=layers)
    }
}
