package com.lumen.studio

import android.graphics.*
import kotlin.math.*

/** Photo-only, worker-confined pixel grading; preview and image export use the same engine. */
object PhotoAdjustments {
    data class Control(val id: String,val name: String,val symbol: String,val min: Int=-100,val max: Int=100,val default: Int=0)
    val controls=listOf(
        Control("brightness","Brightness","☀"),Control("contrast","Contrast","◐",-100,200),
        Control("saturation","Saturation","◉",-100,200),Control("exposure","Exposure","±",-200,200),
        Control("temperature","Temperature","♨"),Control("tint","Tint","◈"),
        Control("photo.highlights","Highlights","◑"),Control("photo.shadows","Shadows","◒"),
        Control("photo.whites","Whites","○"),Control("photo.blacks","Blacks","●"),
        Control("photo.brilliance","Brilliance","✦"),Control("photo.vibrance","Vibrance","❋"),
        Control("photo.hue","Hue","◌",-180,180),Control("photo.sharpen","Sharpen","△",0,100),
        Control("photo.clarity","Clarity","◇",0,100),Control("photo.fade","Fade","▧",0,100),
        Control("photo.vignette","Vignette","◎",0,100),Control("photo.grain","Grain","⠿",0,100))
    fun value(c: EditorClip,id: String): Int = when(id) {
        "brightness" -> c.brightness/2.55f; "contrast" -> (c.contrast-1)*100
        "saturation" -> (c.saturation-1)*100; "exposure" -> c.exposure*100
        "temperature" -> c.temperature*100; "tint" -> c.tint*100
        else -> c.catalogAdjustments.orEmpty()[id] ?: 0f
    }.roundToInt()
    fun update(c: EditorClip,id: String,value: Int): EditorClip = when(id) {
        "brightness" -> c.copy(brightness=value*2.55f); "contrast" -> c.copy(contrast=1+value/100f)
        "saturation" -> c.copy(saturation=1+value/100f); "exposure" -> c.copy(exposure=value/100f)
        "temperature" -> c.copy(temperature=value/100f); "tint" -> c.copy(tint=value/100f)
        else -> c.copy(catalogAdjustments=c.catalogAdjustments.orEmpty()+(id to value.toFloat()))
    }
    fun reset(c: EditorClip)=c.copy(brightness=0f,contrast=1f,saturation=1f,exposure=0f,temperature=0f,tint=0f,catalogAdjustments=c.catalogAdjustments.orEmpty().filterKeys { !it.startsWith("photo.") })
    @androidx.media3.common.util.UnstableApi
    fun render(source: Bitmap,clip: EditorClip): Bitmap {
        val out=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter=ColorMatrixColorFilter(TimelineEngine.color(clip)) })
        val values=clip.catalogAdjustments.orEmpty()
        if(values.none { it.key.startsWith("photo.") && it.value!=0f }) return out
        fun v(id: String)=(values["photo.$id"] ?: 0f)/100f
        val w=out.width; val h=out.height; val pixels=IntArray(w*h); out.getPixels(pixels,0,w,0,0,w,h)
        val detail=if(v("sharpen")>0 || v("clarity")>0) pixels.clone() else null
        val hsv=FloatArray(3)
        val hi=v("highlights"); val sh=v("shadows"); val whites=v("whites"); val blacks=v("blacks")
        val brilliance=v("brilliance"); val vibrance=v("vibrance"); val hue=v("hue")*100
        val fade=v("fade"); val vignette=v("vignette"); val grain=v("grain")
        val tone=FloatArray(256) { index ->
            val l=index/255f
            (sh*(1-l).pow(3)+hi*l.pow(3))*.38f+(whites*l.pow(6)+blacks*(1-l).pow(6))*.28f+brilliance*sin(l*Math.PI).toFloat()*.15f
        }
        val radius=(w/400).coerceIn(1,8)
        for(y in 0 until h) for(x in 0 until w) {
            val i=y*w+x; val p=pixels[i]
            var r=Color.red(p)/255f; var g=Color.green(p)/255f; var b=Color.blue(p)/255f
            val lum=.2126f*r+.7152f*g+.0722f*b
            val lift=tone[(lum*255).roundToInt().coerceIn(0,255)]
            r+=lift; g+=lift; b+=lift
            if(vibrance!=0f) { val sat=maxOf(r,g,b)-minOf(r,g,b); val gain=1+vibrance*(1-sat.coerceIn(0f,1f)); r=lum+(r-lum)*gain; g=lum+(g-lum)*gain; b=lum+(b-lum)*gain }
            if(detail!=null) {
                val a=detail[y*w+(x-radius).coerceAtLeast(0)]; val c=detail[y*w+(x+radius).coerceAtMost(w-1)]
                val d=detail[(y-radius).coerceAtLeast(0)*w+x]; val e=detail[(y+radius).coerceAtMost(h-1)*w+x]
                val amount=v("sharpen")*1.1f+v("clarity")*.6f
                r+=(Color.red(p)-(Color.red(a)+Color.red(c)+Color.red(d)+Color.red(e))/4f)/255*amount
                g+=(Color.green(p)-(Color.green(a)+Color.green(c)+Color.green(d)+Color.green(e))/4f)/255*amount
                b+=(Color.blue(p)-(Color.blue(a)+Color.blue(c)+Color.blue(d)+Color.blue(e))/4f)/255*amount
            }
            if(hue!=0f) { Color.RGBToHSV((r*255).toInt().coerceIn(0,255),(g*255).toInt().coerceIn(0,255),(b*255).toInt().coerceIn(0,255),hsv); hsv[0]=(hsv[0]+hue+360)%360; val shifted=Color.HSVToColor(hsv); r=Color.red(shifted)/255f; g=Color.green(shifted)/255f; b=Color.blue(shifted)/255f }
            val nx=x.toFloat()/w*2-1; val ny=y.toFloat()/h*2-1
            val shade=if(vignette==0f) 1f else 1-vignette*((nx*nx+ny*ny)/2).pow(1.5f)*.8f
            val hash=(x*374761393+y*668265263); val noise=(((hash xor (hash ushr 13)) and 255)/255f-.5f)*grain*.16f
            fun channel(c: Float)=(((c*(1-fade*.35f)+fade*.12f)*shade+noise)*255).roundToInt().coerceIn(0,255)
            pixels[i]=Color.argb(Color.alpha(p),channel(r),channel(g),channel(b))
        }
        out.setPixels(pixels,0,w,0,0,w,h); return out
    }
}
