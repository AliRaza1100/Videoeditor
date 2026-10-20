package com.lumen.studio.graphics

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.*
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offscreen GPU path for paused frames/thumbnails only. Never read pixels during surface playback.
 * Own and close on the creating worker thread; programs are bounded to eight shader families.
 */
@UnstableApi
class BitmapShaderRenderer: AutoCloseable {
    private val owner=Thread.currentThread()
    private val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var context=EGL14.EGL_NO_CONTEXT
    private var surface=EGL14.EGL_NO_SURFACE
    private val programs=LinkedHashMap<String,GlProgram>(8,.75f,true)
    init {
        try {
            check(display!=EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
            val configs=arrayOfNulls<android.opengl.EGLConfig>(1); val count=IntArray(1)
            val attributes=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE)
            check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0)
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE),0)
            check(context!=EGL14.EGL_NO_CONTEXT && surface!=EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display,surface,surface,context))
        } catch(error: Exception) { close(); throw error }
    }
    fun render(input: Bitmap,preset: ShaderPreset,timeUs: Long): Bitmap {
        check(Thread.currentThread()===owner && context!=EGL14.EGL_NO_CONTEXT)
        check(EGL14.eglMakeCurrent(display,surface,surface,context))
        val catalog=GraphicsCatalog.current ?: error("Catalog not installed")
        val program=programs[preset.shader] ?: run {
            val source=catalog.shaders.getValue(preset.shader)
            GlProgram(source.vertex,source.fragment).also { created ->
                created.setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4)
                programs[preset.shader]=created
                if(programs.size>8) { val first=programs.entries.first(); first.value.delete(); programs.remove(first.key) }
            }
        }
        val textures=IntArray(2); val framebuffer=IntArray(1)
        GLES20.glGenTextures(2,textures,0); GLES20.glGenFramebuffers(1,framebuffer,0)
        try {
            textures.forEach { id -> GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE) }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[0])
            val flipped=Bitmap.createBitmap(input,0,0,input.width,input.height,Matrix().apply { setScale(1f,-1f) },false)
            try { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,flipped,0) } finally { if(flipped!==input) flipped.recycle() }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[1])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,input.width,input.height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer[0]); GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textures[1],0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE)
            GLES20.glViewport(0,0,input.width,input.height); program.use()
            program.setSamplerTexIdUniform("uTexSampler",textures[0],0); program.setFloatUniform("uEnabled",1f)
            if(program.getUniformLocation("uBitmap")>=0) program.setFloatUniform("uBitmap",1f)
            if(program.getUniformLocation("uTime")>=0) program.setFloatUniform("uTime",(timeUs/1_000_000.0).toFloat())
            if(program.getUniformLocation("uResolution")>=0) program.setFloatsUniform("uResolution",floatArrayOf(input.width.toFloat(),input.height.toFloat()))
            preset.uniforms.forEach { (name,value) -> if(value.size==1) program.setFloatUniform(name,value[0]) else program.setFloatsUniform(name,value.toFloatArray()) }
            program.bindAttributesAndUniforms(); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            val buffer=ByteBuffer.allocateDirect(input.width*input.height*4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0,0,input.width,input.height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer); GlUtil.checkGlError(); buffer.rewind()
            val bottomUp=Bitmap.createBitmap(input.width,input.height,Bitmap.Config.ARGB_8888)
            bottomUp.copyPixelsFromBuffer(buffer)
            return try { Bitmap.createBitmap(bottomUp,0,0,input.width,input.height,Matrix().apply { setScale(1f,-1f) },false) } finally { bottomUp.recycle() }
        } finally { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0); GLES20.glDeleteFramebuffers(1,framebuffer,0); GLES20.glDeleteTextures(2,textures,0) }
    }
    override fun close() {
        check(Thread.currentThread()===owner)
        if(context!=EGL14.EGL_NO_CONTEXT) {
            if(EGL14.eglMakeCurrent(display,surface,surface,context)) programs.values.forEach { it.delete() }
            programs.clear(); EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(display,context); context=EGL14.EGL_NO_CONTEXT
        }
        if(surface!=EGL14.EGL_NO_SURFACE) { EGL14.eglDestroySurface(display,surface); surface=EGL14.EGL_NO_SURFACE }
        if(display!=EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()
    }
}
