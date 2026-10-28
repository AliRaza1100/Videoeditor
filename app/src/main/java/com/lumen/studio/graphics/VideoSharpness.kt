package com.lumen.studio.graphics

import android.content.Context
import androidx.media3.common.util.UnstableApi

/** Five-tap unsharp mask applied to the same frames for preview and export. */
@UnstableApi
object VideoSharpness {
    fun effect(context: Context,amount: Float): CatalogShaderEffect {
        val vertex=context.assets.open("graphics/shaders/fullscreen.vert").bufferedReader().use { it.readText() }
        val fragment="""
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uResolution;
            uniform float uEnabled;
            uniform float uAmount;
            varying vec2 vTexCoord;
            void main() {
                vec4 center=texture2D(uTexSampler,vTexCoord);
                vec2 d=1.0/uResolution;
                vec3 neighbors=texture2D(uTexSampler,vTexCoord+vec2(d.x,0.0)).rgb
                    +texture2D(uTexSampler,vTexCoord-vec2(d.x,0.0)).rgb
                    +texture2D(uTexSampler,vTexCoord+vec2(0.0,d.y)).rgb
                    +texture2D(uTexSampler,vTexCoord-vec2(0.0,d.y)).rgb;
                gl_FragColor=vec4(clamp(center.rgb+uEnabled*uAmount*(4.0*center.rgb-neighbors),0.0,1.0),center.a);
            }
        """.trimIndent()
        return CatalogShaderEffect(ShaderPreset(PresetInfo("adjust.sharpness","Sharpness","Adjust"),"sharpness",mapOf("uAmount" to listOf(amount.coerceIn(0f,1f)*.5f))),ShaderSource(vertex,fragment))
    }
}
