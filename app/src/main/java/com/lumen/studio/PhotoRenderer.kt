package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import org.json.JSONArray
import kotlin.math.*

data class PhotoLayout(val id: String,val name: String,val cells: List<RectF>)
object PhotoLayouts {
    @Volatile private var catalog: List<PhotoLayout>?=null
    @Synchronized fun all(c: Context): List<PhotoLayout> = catalog ?: run {
        val json=JSONArray(c.assets.open("photo-layouts.json").bufferedReader().use { it.readText() })
        List(json.length()) { i -> val item=json.getJSONObject(i); val cells=item.getJSONArray("cells")
            PhotoLayout(item.getString("id"),item.getString("name"),List(cells.length()) { index ->
                val a=cells.getJSONArray(index); RectF(a.getDouble(0).toFloat(),a.getDouble(1).toFloat(),a.getDouble(2).toFloat(),a.getDouble(3).toFloat()).also { require(it.left>=0 && it.top>=0 && it.right<=1 && it.bottom<=1 && it.width()>0 && it.height()>0) }
            })
        }.also { require(it.map { p -> p.id }.distinct().size==it.size); catalog=it }
    }
    fun selected(c: Context,state: EditorState)=all(c).firstOrNull { it.id==state.photoLayout } ?: all(c).first()
}

/** Instance confined to its photo worker thread. Preview and export share the same drawing code. */
@androidx.media3.common.util.UnstableApi
class PhotoRenderer(private val context: Context): AutoCloseable {
    private val cache=object: android.util.LruCache<String,Bitmap>(24*1024) {
        override fun sizeOf(key: String,value: Bitmap)=(value.allocationByteCount/1024).coerceAtLeast(1)
        override fun entryRemoved(evicted: Boolean,key: String,old: Bitmap,new: Bitmap?) { if(old!==new) old.recycle() }
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val grades=object: android.util.LruCache<String,Bitmap>(12*1024) {
        override fun sizeOf(key: String,value: Bitmap)=(value.allocationByteCount/1024).coerceAtLeast(1)
        override fun entryRemoved(evicted: Boolean,key: String,old: Bitmap,new: Bitmap?) { if(old!==new) old.recycle() }
    }
    private fun grade(image: Bitmap,clip: EditorClip): Bitmap {
        // Placement and text edits reuse graded pixels rather than running a megapixel loop per gesture.
        val key=listOf(clip.uri,image.width,image.height,clip.filter,clip.filterIntensity,clip.brightness,clip.contrast,clip.saturation,clip.exposure,clip.temperature,clip.tint,clip.catalogAdjustments.orEmpty().toSortedMap()).toString()
        return grades.get(key) ?: PhotoAdjustments.render(image,clip).also { if(it.allocationByteCount/1024<=12*1024) grades.put(key,it) }
    }
    fun source(uri: String,edge: Int): Bitmap {
        val key="$uri:$edge"; cache.get(key)?.let { return it }
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it,null,options) }
        require(options.outWidth>0 && options.outHeight>0) { "Unable to read this photo." }
        var sample=1; while(maxOf(options.outWidth,options.outHeight)/sample>edge) sample*=2
        val bitmap=context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: error("Unable to open this photo.")
        val orientation=runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) } }.getOrNull()
        val transform=Matrix()
        when(orientation) {
            2 -> transform.setScale(-1f,1f)
            3 -> transform.setRotate(180f)
            4 -> transform.setScale(1f,-1f)
            5 -> { transform.setRotate(90f); transform.postScale(-1f,1f) }
            6 -> transform.setRotate(90f)
            7 -> { transform.setRotate(270f); transform.postScale(-1f,1f) }
            8 -> transform.setRotate(270f)
        }
        val oriented=if(transform.isIdentity) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,transform,true).also { if(it!==bitmap) bitmap.recycle() }
        // Oversized single images are returned to the caller until drawing completes; do not insert
        // an item larger than the cache budget (LruCache would recycle it immediately).
        if(oriented.allocationByteCount/1024<=24*1024) cache.put(key,oriented)
        return oriented
    }
    fun render(state: EditorState,edge: Int): Bitmap {
        require(state.clips.isNotEmpty() && state.clips.none { it.video })
        val layout=PhotoLayouts.selected(context,state)
        val first=state.clips.first(); val original=source(first.uri,minOf(edge,1536))
        val cropW=((first.cropRight-first.cropLeft)/2).coerceAtLeast(.01f)*original.width
        val cropH=((first.cropTop-first.cropBottom)/2).coerceAtLeast(.01f)*original.height
        val rads=Math.toRadians(first.rotation.toDouble())
        val nativeRatio=((abs(cropW*cos(rads))+abs(cropH*sin(rads)))/(abs(cropW*sin(rads))+abs(cropH*cos(rads)))).toFloat()
        val ratio=if(state.ratio.contains(':')) state.ratio.split(':').let { it[0].toFloat()/it[1].toFloat() } else if(layout.cells.size>1) 1f else nativeRatio
        val w=if(ratio>=1) edge else (edge*ratio).toInt().coerceAtLeast(1)
        val h=if(ratio>=1) (edge/ratio).toInt().coerceAtLeast(1) else edge
        val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); val canvas=Canvas(output); canvas.drawColor(state.background)
        if(state.photoCanvasStyle=="Gradient") {
            paint.colorFilter=null; paint.alpha=255
            paint.shader=LinearGradient(0f,0f,w.toFloat(),h.toFloat(),state.background,state.photoCanvasColorEnd ?: Color.DKGRAY,Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint); paint.shader=null
        } else if(state.photoCanvasStyle=="Blur") {
            // Downsampled backdrop is filtered back to canvas size; never modifies source pixels.
            val small=Bitmap.createScaledBitmap(original,24,(24f*original.height/original.width).toInt().coerceAtLeast(1),true)
            paint.colorFilter=null; paint.alpha=180
            val scale=maxOf(w.toFloat()/small.width,h.toFloat()/small.height)
            val bw=small.width*scale; val bh=small.height*scale
            canvas.drawBitmap(small,null,RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),paint)
            if(small!==original) small.recycle()
        }
        try {
            val gap=if(layout.cells.size==1) 0f else minOf(w,h)*(state.photoGap ?: 12).coerceIn(0,80)/1000f
            val radius=if(layout.cells.size==1) 0f else minOf(w,h)*(state.photoCorner ?: 12).coerceIn(0,100)/1000f
            layout.cells.forEachIndexed { index,cell ->
                val clip=state.clips.getOrNull(index) ?: return@forEachIndexed
                val source=source(clip.uri,edge)
                val image=grade(source,clip)
                try {
                    val box=RectF(cell.left*w+gap/2,cell.top*h+gap/2,cell.right*w-gap/2,cell.bottom*h-gap/2)
                    if(box.width()<=0 || box.height()<=0) return@forEachIndexed
                    val left=(clip.cropLeft+1)/2*image.width; val right=(clip.cropRight+1)/2*image.width
                    val top=(1-clip.cropTop)/2*image.height; val bottom=(1-clip.cropBottom)/2*image.height
                    val cw=(right-left).coerceAtLeast(1f); val ch=(bottom-top).coerceAtLeast(1f)
                    val angle=Math.toRadians(clip.rotation.toDouble()); val rw=(abs(cw*cos(angle))+abs(ch*sin(angle))).toFloat(); val rh=(abs(cw*sin(angle))+abs(ch*cos(angle))).toFloat()
                    val fit=(if(layout.cells.size==1 && state.photoCanvasFit!="Fill") minOf(box.width()/rw,box.height()/rh) else maxOf(box.width()/rw,box.height()/rh))*clip.zoom
                    val dx=maxOf(kotlin.math.abs(rw*fit-box.width())/2,box.width()*.25f)*(clip.photoPanX ?: 0f)
                    val dy=maxOf(kotlin.math.abs(rh*fit-box.height())/2,box.height()*.25f)*(clip.photoPanY ?: 0f)
                    canvas.save(); canvas.clipPath(Path().apply { addRoundRect(box,radius,radius,Path.Direction.CW) })
                    canvas.translate(box.centerX()+dx,box.centerY()+dy); canvas.scale(fit,fit); canvas.rotate(clip.rotation)
                    canvas.scale(if(clip.flip) -1f else 1f,if(clip.flipVertical) -1f else 1f); canvas.translate(-(left+right)/2,-(top+bottom)/2)
                    canvas.clipRect(left,top,right,bottom)
                    paint.colorFilter=null; paint.alpha=(clip.opacity*255).toInt().coerceIn(0,255)
                    canvas.drawBitmap(image,0f,0f,paint); canvas.restore()
                } finally { if(image.allocationByteCount/1024>12*1024) image.recycle(); if(source.allocationByteCount/1024>24*1024) source.recycle() }
            }
            (state.clips.first().texts+state.projectTexts.orEmpty()).forEach { text -> TextPresets.draw(canvas,text.copy(animation="None",startMs=0,endMs=0),w,h,750) }
            return output
        } catch(error: Throwable) { output.recycle(); throw error }
    }
    override fun close() { cache.evictAll(); grades.evictAll() }
}
