package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Content-sized tools use a fixed icon and a minimum touch target, never equal flex widths. */
class EditorToolItem(context: Context,val title: String,available: Boolean=true,action: ()->Unit) : LinearLayout(context) {
    init {
        orientation=VERTICAL; gravity=Gravity.CENTER; minimumHeight=Ui.dp(context,56)
        setPadding(Ui.dp(context,8),Ui.dp(context,4),Ui.dp(context,8),Ui.dp(context,4))
        addView(ToolGlyph(context,title),LayoutParams(Ui.dp(context,20),Ui.dp(context,20)))
        addView(AppText(context).apply { text=if(available) title else "$title · Unavailable"; textSize=10f; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; gravity=Gravity.CENTER; setTextColor(StudioDesign.text(context)); includeFontPadding=false },LayoutParams(-1,-2))
        isEnabled=available; isFocusable=available; alpha=if(available) 1f else .35f
        contentDescription=Ui.t(context,if(available) title else "$title · Unavailable"); tag="toolbar-item"
        if(available) setOnClickListener { action() }
    }
}
private class ToolGlyph(context: Context,private val name: String) : View(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Ui.foreground(context,StudioDesign.text(context)); style=Paint.Style.STROKE; strokeWidth=1.7f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun onDraw(canvas: Canvas) {
        canvas.save(); canvas.scale(width/24f,height/24f)
        fun line(x: Float,y: Float,a: Float,b: Float)=canvas.drawLine(x,y,a,b,ink)
        when(name) {
            "Back" -> { line(20f,12f,4f,12f); line(4f,12f,10f,6f); line(4f,12f,10f,18f) }
            "Audio","Sound","Volume" -> { line(4f,10f,4f,14f); line(9f,6f,9f,18f); line(14f,9f,14f,15f); line(19f,4f,19f,20f) }
            "Delete","Delete text","Remove photo" -> { canvas.drawRect(6f,7f,18f,21f,ink); line(4f,4f,20f,4f); line(9f,2f,15f,2f); line(10f,10f,10f,18f); line(14f,10f,14f,18f) }
            "Text","Edit Text","Edit text","Font","Style" -> { line(4f,4f,20f,4f); line(12f,4f,12f,20f); line(8f,20f,16f,20f) }
            "Split" -> { canvas.drawRect(3f,5f,9f,19f,ink); canvas.drawRect(15f,5f,21f,19f,ink); line(12f,2f,12f,22f) }
            "Trim","Crop","Canvas" -> { line(5f,2f,5f,19f); line(5f,19f,22f,19f); line(2f,5f,19f,5f); line(19f,5f,19f,22f) }
            "Duplicate","Replace" -> { canvas.drawRect(7f,7f,21f,21f,ink); line(3f,17f,3f,3f); line(3f,3f,17f,3f) }
            "Filters","Adjust","Color","Text background" -> { line(5f,3f,5f,21f); line(12f,3f,12f,21f); line(19f,3f,19f,21f); canvas.drawCircle(5f,8f,2f,ink); canvas.drawCircle(12f,16f,2f,ink); canvas.drawCircle(19f,10f,2f,ink) }
            "Photos","Add photos","Collage" -> { canvas.drawRoundRect(3f,3f,21f,21f,2f,2f,ink); line(12f,3f,12f,21f); line(3f,12f,21f,12f) }
            "Done" -> { line(4f,12f,10f,18f); line(10f,18f,21f,5f) }
            "Stickers" -> { canvas.drawCircle(12f,12f,9f,ink); canvas.drawCircle(8f,9f,.7f,ink); canvas.drawCircle(16f,9f,.7f,ink); canvas.drawArc(RectF(7f,10f,17f,18f),0f,180f,false,ink) }
            "Speed","Duration" -> { canvas.drawCircle(12f,12f,9f,ink); line(12f,12f,16f,7f); line(12f,12f,7f,12f) }
            "Flip" -> { line(12f,2f,12f,22f); canvas.drawPath(Path().apply { moveTo(3f,18f); lineTo(3f,6f); lineTo(9f,12f); close() },ink); canvas.drawPath(Path().apply { moveTo(21f,18f); lineTo(21f,6f); lineTo(15f,12f); close() },ink) }
            "Rotate" -> { canvas.drawArc(RectF(4f,4f,20f,20f),35f,290f,false,ink); line(20f,4f,20f,10f); line(20f,10f,14f,10f) }
            "Fade" -> { line(3f,19f,21f,19f); line(3f,19f,21f,5f) }
            "Ratio","Add Media" -> { canvas.drawRect(3f,5f,21f,19f,ink); if(name=="Add Media") { line(8f,12f,16f,12f); line(12f,8f,12f,16f) } }
            "Edit" -> { canvas.drawCircle(6f,6f,3f,ink); canvas.drawCircle(6f,18f,3f,ink); line(8f,8f,21f,20f); line(8f,16f,21f,4f) }
            else -> { canvas.drawPath(Path().apply { moveTo(12f,2f); lineTo(15f,9f); lineTo(22f,12f); lineTo(15f,15f); lineTo(12f,22f); lineTo(9f,15f); lineTo(2f,12f); lineTo(9f,9f); close() },ink) }
        }
        canvas.restore()
    }
}

/** Repeat thumbnails at their native proportions rather than stretching a frame across a long clip. */
class RepeatingThumbnailView(context: Context) : ImageView(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(canvas: Canvas) {
        val bitmap=(drawable as? BitmapDrawable)?.bitmap ?: return
        if(bitmap.isRecycled || bitmap.height==0) return
        val shader=BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.CLAMP)
        val scale=height.toFloat()/bitmap.height
        shader.setLocalMatrix(Matrix().apply { setScale(scale,scale) }); ink.shader=shader
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),ink)
    }
}
