package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

/** A single shared portrait atlas; all layouts, masks, timeline and fades are native Canvas. */
class OnboardingArtwork(context: Context, private val page: Int): View(context) {
    companion object {
        @Volatile private var atlas: Bitmap?=null
        private fun portraits(context: Context): Bitmap = atlas ?: synchronized(this) {
            atlas ?: context.assets.open("onboarding/portraits.png").use { BitmapFactory.decodeStream(it)!! }.also { atlas=it }
        }
    }
    private val bitmap=portraits(context)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds=RectF()
    private val source=Rect()
    private val mask=Path()
    init { tag="media"; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat(); val h=height.toFloat()
        canvas.save(); canvas.scale(w/360f,h/420f)
        fun box(l: Float,t: Float,r: Float,b: Float,color: Int,radius: Float=10f) {
            paint.shader=null; paint.color=color; canvas.drawRoundRect(l,t,r,b,radius,radius,paint)
        }
        fun photo(cell: Int,l: Float,t: Float,r: Float,b: Float,radius: Float=16f) {
            val side=bitmap.width/2
            val targetRatio=(r-l)/(b-t)
            val sw=if(targetRatio<1) (side*targetRatio).toInt() else side
            val sh=if(targetRatio>1) (side/targetRatio).toInt() else side
            val x=(cell%2)*side+(side-sw)/2; val y=(cell/2)*side+(side-sh)/2
            source.set(x,y,x+sw,y+sh); bounds.set(l,t,r,b)
            mask.reset(); mask.addRoundRect(bounds,radius,radius,Path.Direction.CW)
            canvas.save(); canvas.clipPath(mask); paint.color=Color.WHITE; paint.shader=null
            canvas.drawBitmap(bitmap,source,bounds,paint); canvas.restore()
        }
        when(page) {
            0 -> {
                canvas.save(); canvas.rotate(-13f,180f,180f)
                val names=listOf("Warm","Portrait","Fresh","Golden","Soft","Classic")
                repeat(8) { i ->
                    val x=-30f+(i%2)*206f; val y=-55f+(i/2)*117f
                    photo(i%6,x,y,x+194f,y+105f,13f)
                    box(x,y+77,x+194,y+105,Color.argb(125,10,15,25),0f)
                    paint.color=Color.WHITE; paint.textSize=11f; paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
                    canvas.drawText(names[i%6],x+12,y+95,paint)
                }; canvas.restore()
            }
            1 -> {
                photo(4,0f,0f,360f,420f,0f)
                repeat(18) { i ->
                    paint.color=Color.argb(50+(i%3)*15,255,180+i%5*12,95)
                    canvas.drawCircle((i*83%370).toFloat(),(i*59%340).toFloat(),(9+i%5*6).toFloat(),paint)
                }
                paint.shader=LinearGradient(70f,330f,290f,330f,intArrayOf(Color.rgb(255,215,70),Color.rgb(201,98,255)),null,Shader.TileMode.CLAMP)
                paint.textSize=29f; paint.typeface=Typeface.create("sans-serif-black",Typeface.ITALIC); paint.textAlign=Paint.Align.CENTER
                canvas.drawText("CREATIVE FX",180f,325f,paint); paint.textAlign=Paint.Align.LEFT; paint.shader=null
            }
            2 -> {
                photo(3,-40f,-30f,91f,170f); photo(1,103f,-10f,263f,184f); photo(4,275f,-40f,406f,98f)
                photo(5,-40f,182f,91f,355f); photo(2,103f,196f,263f,395f); photo(0,275f,110f,406f,310f)
            }
            else -> {
                box(0f,0f,360f,420f,Color.rgb(19,31,43),0f)
                paint.color=Color.rgb(176,193,205); paint.textSize=10f
                repeat(5) { i -> canvas.drawText("00:0$i",18f+i*76,64f,paint) }
                repeat(7) { i -> photo(i%6,10f+i*53,85f,60f+i*53,143f,5f) }
                box(25f,162f,255f,199f,Color.rgb(14,115,100),8f)
                box(65f,212f,345f,249f,Color.rgb(110,49,178),8f)
                box(125f,263f,330f,300f,Color.rgb(100,140,32),8f)
                for(lane in 0..2) {
                    val start=listOf(35f,75f,135f)[lane]; val end=listOf(245f,335f,320f)[lane]; val y=180f+lane*51f
                    paint.color=Color.argb(150,230,255,236); paint.strokeWidth=1.3f
                    var x=start
                    while(x<end) { val a=3f+kotlin.math.abs(sin(x*.27f))*8f; canvas.drawLine(x,y-a,x,y+a,paint); x+=4f }
                }
                repeat(6) { i -> photo((i+2)%6,10f+i*61,318f,68f+i*61,366f,5f) }
                paint.color=Color.WHITE; paint.strokeWidth=1.4f; canvas.drawLine(184f,73f,184f,390f,paint)
                canvas.drawCircle(184f,75f,4f,paint)
            }
        }
        val background=StudioDesign.background(context)
        paint.shader=LinearGradient(0f,235f,0f,420f,intArrayOf(background and 0x00ffffff,background),floatArrayOf(0f,1f),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,230f,360f,420f,paint); paint.shader=null
        canvas.restore()
    }
}

