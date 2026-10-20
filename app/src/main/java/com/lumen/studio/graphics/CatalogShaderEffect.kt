package com.lumen.studio.graphics

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.*
import androidx.media3.effect.*

/** Immutable per-composition configuration. GL resources are created/released on Media3's GL thread. */
@UnstableApi
class CatalogShaderEffect(
    private val preset: ShaderPreset,private val source: ShaderSource,
    private val active: (Long)->Boolean={ true },private val timelineOffsetUs: Long=0,
    private val select: ((Long)->ShaderPreset?)?=null, private val motion: ClipTimelineEvaluator?=null
): GlEffect {
    override fun toGlShaderProgram(context: Context,useHdr: Boolean): BaseGlShaderProgram {
        require(!useHdr) { "Catalog shaders require SDR; tone-map HDR input in the Composition" }
        return Program(preset,source,active,timelineOffsetUs,select,motion)
    }
    private class Program(preset: ShaderPreset,source: ShaderSource,private val active: (Long)->Boolean,private val offsetUs: Long,private val select: ((Long)->ShaderPreset?)?,private val motion: ClipTimelineEvaluator?): BaseGlShaderProgram(false,1) {
        private val frame=MotionFrame()
        private val program: GlProgram
        private val defaults=preset.uniforms.mapValues { it.value.toFloatArray() }
        private var lastPreset=preset
        init {
            try {
                program=GlProgram(source.vertex,source.fragment)
                try {
                    program.setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4)
                    require(program.getUniformLocation("uTexSampler")>=0 && program.getUniformLocation("uEnabled")>=0)
                    preset.uniforms.forEach { (name,value) ->
                        require(program.getUniformLocation(name)>=0) { "Missing shader uniform $name in ${preset.info.id}" }
                        if(value.size==1) program.setFloatUniform(name,value.single()) else program.setFloatsUniform(name,value.toFloatArray())
                    }
                } catch(error: Exception) { program.delete(); throw error }
            } catch(error: Exception) { throw VideoFrameProcessingException(error) }
        }
        override fun configure(inputWidth: Int,inputHeight: Int): Size {
            require(inputWidth>0 && inputHeight>0)
            if(program.getUniformLocation("uResolution")>=0) program.setFloatsUniform("uResolution",floatArrayOf(inputWidth.toFloat(),inputHeight.toFloat()))
            return Size(inputWidth,inputHeight)
        }
        override fun drawFrame(inputTexId: Int,presentationTimeUs: Long) {
            try {
                program.use()
                val chosen=select?.invoke(presentationTimeUs)
                if(chosen!=null && chosen!==lastPreset) {
                    require(chosen.uniforms.keys==defaults.keys) { "Presets sharing a timed shader must define the same uniforms" }
                    chosen.uniforms.forEach { (name,value) ->
                        val buffer=defaults.getValue(name)
                        require(buffer.size==value.size)
                        value.forEachIndexed { i,v -> buffer[i]=v }
                        if(buffer.size==1) program.setFloatUniform(name,buffer[0]) else program.setFloatsUniform(name,buffer)
                    }
                    lastPreset=chosen
                }
                motion?.let {
                    it.evaluate(presentationTimeUs/1000,frame)
                    program.setFloatUniform("uBlur",frame.blur); program.setFloatUniform("uWave",frame.wave); program.setFloatUniform("uGlitch",frame.glitch)
                }
                program.setSamplerTexIdUniform("uTexSampler",inputTexId,0)
                program.setFloatUniform("uEnabled",if(active(presentationTimeUs) && (select==null || chosen!=null)) 1f else 0f)
                if(program.getUniformLocation("uTime")>=0) program.setFloatUniform("uTime",((presentationTimeUs+offsetUs)/1_000_000.0).toFloat())
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GlUtil.checkGlError()
            } catch(error: Exception) { throw VideoFrameProcessingException(error,presentationTimeUs) }
        }
        override fun release() {
            try { program.delete() } catch(error: Exception) { throw VideoFrameProcessingException(error) } finally { super.release() }
        }
    }
}
