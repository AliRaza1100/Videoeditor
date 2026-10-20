package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.view.View

/** Preset definitions are shared by cards, live composition, and exported frames. */
object EditorPresets {
    private val legacyFilters=linkedMapOf(
        "Warm" to listOf("Warm","Golden","Sunset","Honey","Peach"),
        "Cool" to listOf("Cool","Arctic","Ocean","Steel","Moonlight"),
        "Vintage" to listOf("Vintage","Sepia","Faded","Retro","Film"),
        "Cinema" to listOf("Cinematic","Teal","Noir","Drama","Matte"),
        "Essentials" to listOf("Natural","Bright","High Contrast","Mono","Vivid"))
    private val legacyEffects=linkedMapOf(
        "Lens" to listOf("Vignette","Soft vignette","Spotlight"),
        "Light" to listOf("Warm glow","Cool glow","Rose glow","Sun flare"),
        "Retro" to listOf("Film grain","Scanlines","Cinema bars","Frame"))
    val filters get()=com.lumen.studio.graphics.GraphicsCatalog.current?.filters?.groupBy { it.info.category }?.mapValues { (_,items) -> items.map { it.info.name } }?.toMap(linkedMapOf()) ?: legacyFilters
    val effects get()=linkedMapOf<String,List<String>>().apply {
        putAll(legacyEffects)
        com.lumen.studio.graphics.GraphicsCatalog.current?.effects?.groupBy { it.info.category }?.forEach { (category,items) -> put("GPU · $category",items.map { it.info.name }) }
    }
    val stickers=listOf("★","♥","✦","☀","❤","✨","🔥","🎉","😎","😊","😍","😂","🥳","👍","👏","💯","💥","🌈","🌸","🍀","⚡","🎵","🎬","📍","✅","❌","➜","⬆","▶","●","NEW","WOW","LOVE","OMG","SALE","2026")
    fun matrix(name: String): ColorMatrix? {
        com.lumen.studio.graphics.GraphicsCatalog.current?.filter(name)?.let { return ColorMatrix(it.matrix.toFloatArray()) }
        fun grade(r: Float,g: Float,b: Float,offset: Float=0f,sat: Float=1f)=ColorMatrix().apply {
            setSaturation(sat); postConcat(ColorMatrix(floatArrayOf(r,0f,0f,0f,offset,0f,g,0f,0f,offset,0f,0f,b,0f,offset,0f,0f,0f,1f,0f)))
        }
        return when(name) {
            "Golden" -> grade(1.15f,1.04f,.8f,8f); "Sunset" -> grade(1.2f,.95f,.8f,5f,1.15f)
            "Honey" -> grade(1.08f,1.02f,.85f,12f,.9f); "Peach" -> grade(1.12f,.99f,.95f,12f,.85f)
            "Arctic" -> grade(.85f,1.05f,1.2f,8f,.8f); "Ocean" -> grade(.8f,1.07f,1.15f,0f,1.1f)
            "Steel" -> grade(.9f,.97f,1.08f,-5f,.6f); "Moonlight" -> grade(.75f,.83f,1.1f,-8f,.7f)
            "Sepia" -> grade(1.2f,1f,.75f,0f,.25f); "Faded" -> grade(.85f,.85f,.85f,30f,.7f)
            "Retro" -> grade(1.08f,1.02f,.8f,10f,.7f); "Film" -> grade(1.1f,1.05f,.95f,-8f,.8f)
            "Teal" -> grade(.8f,1.1f,1.15f,-5f,.85f); "Noir" -> grade(1.35f,1.35f,1.35f,-35f,0f)
            "Drama" -> grade(1.3f,1.2f,1.15f,-25f,.8f); "Matte" -> grade(.85f,.9f,.95f,20f,.8f)
            "Vivid" -> grade(1.08f,1.08f,1.08f,-8f,1.4f)
            else -> null
        }
    }
    fun drawEffect(canvas: Canvas,name: String,w: Float,h: Float) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        when(name) {
            "None" -> return
            "Vignette","Soft vignette","Spotlight" -> {
                val alpha=if(name=="Soft vignette") 90 else if(name=="Spotlight") 220 else 170
                p.shader=RadialGradient(w/2,h/2,maxOf(w,h)*.65f,intArrayOf(Color.TRANSPARENT,Color.argb(alpha,0,0,0)),floatArrayOf(.25f,1f),Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,w,h,p)
            }
            "Cinema bars" -> { p.color=Color.BLACK; canvas.drawRect(0f,0f,w,h*.1f,p); canvas.drawRect(0f,h*.9f,w,h,p) }
            "Frame" -> { p.color=Color.WHITE; p.style=Paint.Style.STROKE; p.strokeWidth=minOf(w,h)*.025f; canvas.drawRect(p.strokeWidth/2,p.strokeWidth/2,w-p.strokeWidth/2,h-p.strokeWidth/2,p) }
            "Scanlines" -> { p.color=Color.argb(65,0,0,0); p.strokeWidth=maxOf(1f,h/360); var y=0f; while(y<h) { canvas.drawLine(0f,y,w,y,p); y+=maxOf(3f,h/120) } }
            "Film grain" -> { val random=java.util.Random(42); repeat(1800) { p.color=Color.argb(45,255,255,255); canvas.drawCircle(random.nextFloat()*w,random.nextFloat()*h,maxOf(1f,w/700),p) } }
            "Sun flare" -> { p.shader=RadialGradient(w*.15f,h*.1f,maxOf(w,h)*.7f,intArrayOf(Color.argb(130,255,210,130),Color.TRANSPARENT),null,Shader.TileMode.CLAMP); canvas.drawRect(0f,0f,w,h,p) }
            else -> { p.color=when(name) { "Cool glow" -> Color.argb(45,70,150,255); "Rose glow" -> Color.argb(45,255,80,140); else -> Color.argb(35,255,153,45) }; canvas.drawRect(0f,0f,w,h,p) }
        }
    }
}

@androidx.media3.common.util.UnstableApi
class PresetThumbnail(context: Context,private val preset: String,private val effect: Boolean): android.widget.ImageView(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var gpuThumbnail: Bitmap?=null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if(effect && com.lumen.studio.graphics.GraphicsCatalog.current?.effect(preset)!=null) {
            com.lumen.studio.graphics.GraphicsThumbnails.request(this,preset) { view,bitmap -> (view as PresetThumbnail).apply { gpuThumbnail=bitmap; invalidate() } }
        }
    }
    init {
        scaleType=ScaleType.CENTER_CROP
        if(!effect) colorFilter=ColorMatrixColorFilter(TimelineEngine.color(EditorClip("","",false,filter=preset)))
    }
    override fun onDraw(canvas: Canvas) {
        gpuThumbnail?.let { canvas.drawBitmap(it,null,RectF(0f,0f,width.toFloat(),height.toFloat()),paint); return }
        if(drawable==null) {
            paint.colorFilter=if(effect) null else colorFilter
            paint.shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),intArrayOf(Color.rgb(240,160,80),Color.rgb(50,130,140),Color.rgb(30,45,75)),null,Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
            paint.shader=null; paint.color=Color.rgb(255,220,130)
            canvas.drawCircle(width*.72f,height*.25f,height*.12f,paint)
            paint.color=Color.rgb(50,120,85)
            canvas.drawPath(Path().apply { moveTo(0f,height.toFloat()); lineTo(width*.3f,height*.4f); lineTo(width*.65f,height*.85f); lineTo(width*.85f,height*.5f); lineTo(width.toFloat(),height.toFloat()); close() },paint)
            paint.colorFilter=null
        }
        super.onDraw(canvas)
        // A small reference palette keeps grades distinguishable even on a black source frame.
        paint.colorFilter=if(effect) null else colorFilter
        listOf(Color.rgb(220,150,95),Color.rgb(70,165,145),Color.rgb(95,140,220),Color.LTGRAY).forEachIndexed { index,color ->
            paint.color=color
            canvas.drawRect(index*width/4f,height*.88f,(index+1)*width/4f,height.toFloat(),paint)
        }
        paint.colorFilter=null
        if(effect && com.lumen.studio.graphics.GraphicsCatalog.current?.effect(preset)==null) EditorPresets.drawEffect(canvas,preset,width.toFloat(),height.toFloat())
    }
}
