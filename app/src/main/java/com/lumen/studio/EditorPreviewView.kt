package com.lumen.studio

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView

/** Owns the display surface for exactly this preview view, including resize and detach. */
class EditorPreviewView(context: Context,private val outputChanged: (EditorPreviewView)->Unit) : TextureView(context),TextureView.SurfaceTextureListener {
    var outputSurface: Surface?=null; private set
    init { isOpaque=true; surfaceTextureListener=this }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture,width: Int,height: Int) {
        texture.setDefaultBufferSize(width,height); outputSurface=Surface(texture); outputChanged(this)
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture,width: Int,height: Int) {
        texture.setDefaultBufferSize(width,height); outputChanged(this)
    }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        val old=outputSurface; outputSurface=null; outputChanged(this); old?.release(); return true
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
}
