package com.lumen.studio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** Probe source dimensions off the UI thread; never label a source as an export guarantee. */
object ProjectResolution {
    private val worker=Executors.newSingleThreadExecutor()
    private val cache=object: android.util.LruCache<String,String>(128) {}
    fun load(context: Context,project: SavedProject,target: TextView,prefix: String,compact: Boolean=false) {
        val clip=project.state.clips.firstOrNull() ?: return
        val reference=WeakReference(target); val app=context.applicationContext
        val key=clip.uri; target.setTag(R.id.project_resolution_key,key)
        fun apply(value: String) { reference.get()?.let { view -> if(view.getTag(R.id.project_resolution_key)==key) view.text=if(compact) "$value source" else "$prefix · $value source · ${project.state.ratio} canvas" } }
        cache.get(key)?.let { apply(it); return }
        worker.execute {
            val cached=cache.get(key)
            if(cached!=null) { android.os.Handler(android.os.Looper.getMainLooper()).post { apply(cached) }; return@execute }
            val value=runCatching {
                if(clip.video) {
                    val reader=MediaMetadataRetriever()
                    try { reader.setDataSource(app,Uri.parse(key)); val w=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0; val h=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0; if(w>0 && h>0) "${w}×$h" else null } finally { reader.release() }
                } else {
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    app.contentResolver.openInputStream(Uri.parse(key))?.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
                    if(bounds.outWidth>0 && bounds.outHeight>0) "${bounds.outWidth}×${bounds.outHeight}" else null
                }
            }.getOrNull()
            if(value!=null) { cache.put(key,value); android.os.Handler(android.os.Looper.getMainLooper()).post { apply(value) } }
        }
    }
}
