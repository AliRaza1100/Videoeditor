package com.lumen.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.util.concurrent.Executors

/** Small, cached thumbnails; full resolution media never decodes on the UI thread. */
object PreviewImages {
    fun clearMemory() { cache.evictAll() }
    private val worker=Executors.newFixedThreadPool(2)
    private val main=Handler(Looper.getMainLooper())
    private val cache=object : android.util.LruCache<String,Bitmap>(8*1024) {
        override fun sizeOf(key: String,value: Bitmap)=(value.allocationByteCount/1024).coerceAtLeast(1)
    }
    fun load(context: Context,value: String,video: Boolean,target: ImageView,positionMs: Long=0) {
        val key="$value:$positionMs"
        target.setTag(R.id.preview_uri,key)
        cache.get(key)?.let { target.setImageBitmap(it); return }
        val app=context.applicationContext
        worker.execute {
            val bitmap=try {
                val uri=Uri.parse(value)
                if(Build.VERSION.SDK_INT>=29 && uri.scheme=="content" && positionMs==0L) app.contentResolver.loadThumbnail(uri,android.util.Size(180,120),null)
                else if(video) {
                    val retriever=MediaMetadataRetriever()
                    try { retriever.setDataSource(app,uri); if(Build.VERSION.SDK_INT>=27) retriever.getScaledFrameAtTime(positionMs*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,180,120) else retriever.getFrameAtTime(positionMs*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } finally { retriever.release() }
                } else {
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
                    val sample=(maxOf(bounds.outWidth,bounds.outHeight)/180).coerceAtLeast(1)
                    app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) }
                }
            } catch(_: Exception) { null }
            if(bitmap!=null) { cache.put(key,bitmap); main.post { if(target.getTag(R.id.preview_uri)==key) target.setImageBitmap(bitmap) } }
        }
    }
}
