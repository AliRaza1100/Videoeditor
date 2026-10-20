package com.lumen.studio.graphics

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import androidx.media3.common.util.UnstableApi
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@UnstableApi
object GraphicsThumbnails {
    private val cache=object: LruCache<String,Bitmap>(3*1024*1024) { override fun sizeOf(key: String,value: Bitmap)=value.allocationByteCount }
    private val worker=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(32),{ task -> Thread(task,"LumenPresetCards") },ThreadPoolExecutor.DiscardOldestPolicy())
    private val main=Handler(Looper.getMainLooper())
    private var renderer: BitmapShaderRenderer?=null // worker-confined, application lifetime
    fun request(view: View,id: String,receive: (View,Bitmap)->Unit) {
        cache.get(id)?.let { receive(view,it); return }
        val target=WeakReference(view)
        worker.execute {
            if(target.get()==null) return@execute
            val preset=GraphicsCatalog.current?.effect(id) ?: return@execute
            val result=runCatching {
                val input=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888)
                try {
                    val canvas=Canvas(input)
                    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=LinearGradient(0f,0f,128f,96f,intArrayOf(Color.rgb(240,160,80),Color.rgb(50,130,140),Color.rgb(30,45,75)),null,Shader.TileMode.CLAMP) }
                    canvas.drawRect(0f,0f,128f,96f,paint); paint.shader=null; paint.color=Color.YELLOW
                    canvas.drawCircle(90f,24f,12f,paint); paint.color=Color.rgb(30,90,50)
                    canvas.drawRect(0f,68f,128f,96f,paint)
                    (renderer ?: BitmapShaderRenderer().also { renderer=it }).render(input,preset,750_000)
                } finally { input.recycle() }
            }.getOrElse { android.util.Log.w("LumenGraphics","Thumbnail shader failed: $id",it); return@execute }
            cache.put(id,result)
            main.post { target.get()?.takeIf { it.isAttachedToWindow }?.let { receive(it,result) } }
        }
    }
}
