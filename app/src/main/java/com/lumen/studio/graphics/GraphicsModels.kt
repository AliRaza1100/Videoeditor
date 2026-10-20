package com.lumen.studio.graphics

import kotlin.math.pow

/** Asset IDs are durable; display names may change without invalidating new saved projects. */
data class PresetInfo(val id: String,val name: String,val category: String)
data class FilterPreset(val info: PresetInfo,val matrix: List<Float>)
/** Matrix = base + slope * (2^value when exponential, otherwise value). */
data class AdjustmentPreset(val id: String,val minimum: Float,val maximum: Float,val default: Float,val afterFilter: Boolean,val exponential: Boolean,val base: List<Float>,val slope: List<Float>)
data class ShaderPreset(val info: PresetInfo,val shader: String,val uniforms: Map<String,List<Float>>)
data class ShaderSource(val vertex: String,val fragment: String)
data class TypographyPreset(val info: PresetInfo,val family: String,val weight: Int,val italic: Boolean,
    val strokeColor: Int,val strokeWidth: Float,val glowColor: Int,val glowRadius: Float,
    val background: Int,val padding: Float,val letterSpacing: Float,val underline: Boolean=false,val strike: Boolean=false,val textColor: Int=-1)
enum class CurveKind { LINEAR, BEZIER, ELASTIC, BOUNCE, STEP, EASE_IN_OUT_CUBIC, ELASTIC_BOUNCE, BACK_EASE_IN, BACK_EASE_OUT, SPRING_PHYSICS }
data class Curve(val kind: CurveKind=CurveKind.LINEAR,val x1: Float=.25f,val y1: Float=.1f,val x2: Float=.25f,val y2: Float=1f)
data class Keyframe(val fraction: Float,val value: Float,val curve: Curve=Curve())
data class ScalarTrack(val keys: List<Keyframe>) {
    init {
        require(keys.isNotEmpty() && keys.all { it.fraction.isFinite() && it.value.isFinite() && it.fraction in 0f..1f })
        require(keys.zipWithNext().all { (a,b) -> a.fraction<b.fraction }) { "Keyframes must have unique, increasing times" }
    }
    fun sample(fraction: Float): Float {
        require(fraction.isFinite())
        if(fraction<=keys.first().fraction) return keys.first().value
        if(fraction>=keys.last().fraction) return keys.last().value
        var low=0; var high=keys.lastIndex
        while(high-low>1) { val mid=(low+high)/2; if(keys[mid].fraction<=fraction) low=mid else high=mid }
        val a=keys[low]; val b=keys[high]
        return a.value+(b.value-a.value)*Easing.evaluate((fraction-a.fraction)/(b.fraction-a.fraction),a.curve)
    }
}
data class AnimationPreset(val info: PresetInfo,val durationMs: Long,val tracks: Map<String,ScalarTrack>) {
    init { require(durationMs>0); require(tracks.keys.all { it in setOf("x","y","scaleX","scaleY","rotation","alpha","reveal","blur","wave","glitch") }) }
    fun pose(elapsedMs: Long): OverlayPose {
        val t=(elapsedMs.toDouble()/durationMs).coerceIn(0.0,1.0).toFloat()
        fun get(name: String,default: Float)=tracks[name]?.sample(t) ?: default
        return OverlayPose(get("x",0f),get("y",0f),get("scaleX",1f),get("scaleY",1f),get("rotation",0f),get("alpha",1f).coerceIn(0f,1f),get("reveal",1f).coerceIn(0f,1f))
    }
}
/** Translation is a fraction of the canvas; scale is dimensionless; rotation is degrees. */
data class OverlayPose(val x: Float=0f,val y: Float=0f,val scaleX: Float=1f,val scaleY: Float=1f,val rotation: Float=0f,val alpha: Float=1f,val reveal: Float=1f)

object Easing {
    fun evaluate(value: Float,curve: Curve): Float {
        val t=value.coerceIn(0f,1f)
        if(t==0f || t==1f) return t
        return when(curve.kind) {
            CurveKind.LINEAR -> t
            CurveKind.EASE_IN_OUT_CUBIC -> if(t<.5f) 4*t*t*t else 1-((-2*t+2).pow(3))/2
            CurveKind.BACK_EASE_IN -> 2.70158f*t*t*t-1.70158f*t*t
            CurveKind.BACK_EASE_OUT -> (t-1).let { 1+2.70158f*it*it*it+1.70158f*it*it }
            CurveKind.SPRING_PHYSICS -> (1-kotlin.math.exp(-6.0*t)*(kotlin.math.cos(11.0*t)+(6.0/11.0)*kotlin.math.sin(11.0*t))).toFloat()
            CurveKind.ELASTIC_BOUNCE -> .8f*(1-(2.0.pow((-10*t).toDouble())*kotlin.math.cos(t*4*Math.PI)).toFloat())+.2f*bounce(t)
            CurveKind.STEP -> 0f
            CurveKind.ELASTIC -> (2.0.pow((-10*t).toDouble())*kotlin.math.sin((t*10-.75)*2*Math.PI/3)+1).toFloat()
            CurveKind.BOUNCE -> bounce(t)
            CurveKind.BEZIER -> {
                // Invert x(u), then evaluate y(u). Evaluating y(t) directly is incorrect.
                var lo=0f; var hi=1f
                repeat(24) { val u=(lo+hi)/2; if(cubic(u,curve.x1,curve.x2)<t) lo=u else hi=u }
                cubic((lo+hi)/2,curve.y1,curve.y2)
            }
        }
    }
    private fun cubic(t: Float,a: Float,b: Float): Float { val s=1-t; return 3*s*s*t*a+3*s*t*t*b+t*t*t }
    private fun bounce(t: Float): Float {
        val n=7.5625f; val d=2.75f
        return when { t<1/d -> n*t*t; t<2/d -> (t-1.5f/d).let { n*it*it+.75f }; t<2.5f/d -> (t-2.25f/d).let { n*it*it+.9375f }; else -> (t-2.625f/d).let { n*it*it+.984375f } }
    }
}
