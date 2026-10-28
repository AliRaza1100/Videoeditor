package com.lumen.studio

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Pending crop coordinates are source-normalized. Applying is owned by the editor history. */
class CropEditorView(context: Context,initial: RectF) : View(context) {
    var onCropChanged: ((RectF)->Unit)?=null
    var onGestureStart: (() -> Unit)?=null
    var onGestureEnd: (() -> Unit)?=null
    val crop=RectF(initial)
    var image: Bitmap?=null
        set(value) { field=value; if(value!=null && ratio>0) setRatio(ratio); invalidate() }
    private var ratio=0f
    private var corner=-1
    private var sx=0f; private var sy=0f
    private val original=RectF()
    private val media=RectF()
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun restoreRatio(name: String?) {
        val parts=name?.split(':').orEmpty()
        ratio=if(parts.size==2) (parts[0].toFloatOrNull() ?: 0f)/(parts[1].toFloatOrNull() ?: 1f) else 0f
        if(!ratio.isFinite() || ratio<0) ratio=0f
        invalidate()
    }
    fun reset() { ratio=0f; crop.set(0f,0f,1f,1f); invalidate(); if(image!=null) onCropChanged?.invoke(RectF(crop)) }
    fun setRatio(value: Float) {
        require(value.isFinite() && value>=0)
        ratio=value
        if(value==0f) { invalidate(); return }
        val source=image?.let { it.width.toFloat()/it.height } ?: return
        val fraction=value/source
        val w=minOf(1f,fraction); val h=w/fraction
        crop.set((1-w)/2,(1-h)/2,(1+w)/2,(1+h)/2); invalidate(); onCropChanged?.invoke(RectF(crop))
    }
    private fun fit() {
        val bitmap=image ?: return
        val scale=minOf(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val w=bitmap.width*scale; val h=bitmap.height*scale
        media.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
    }
    override fun onDraw(canvas: Canvas) {
        fit(); canvas.drawColor(Color.BLACK); val bitmap=image ?: return
        ink.shader=null; ink.style=Paint.Style.FILL; ink.color=Color.WHITE; canvas.drawBitmap(bitmap,null,media,ink)
        val r=RectF(media.left+crop.left*media.width(),media.top+crop.top*media.height(),media.left+crop.right*media.width(),media.top+crop.bottom*media.height())
        ink.color=Color.argb(155,0,0,0)
        canvas.drawRect(media.left,media.top,media.right,r.top,ink); canvas.drawRect(media.left,r.bottom,media.right,media.bottom,ink)
        canvas.drawRect(media.left,r.top,r.left,r.bottom,ink); canvas.drawRect(r.right,r.top,media.right,r.bottom,ink)
        ink.color=Color.WHITE; ink.style=Paint.Style.STROKE; ink.strokeWidth=Ui.dp(context,2).toFloat(); canvas.drawRect(r,ink)
        ink.strokeWidth=Ui.dp(context,1).toFloat(); for(i in 1..2) { canvas.drawLine(r.left+r.width()*i/3,r.top,r.left+r.width()*i/3,r.bottom,ink); canvas.drawLine(r.left,r.top+r.height()*i/3,r.right,r.top+r.height()*i/3,ink) }
        ink.style=Paint.Style.FILL
        listOf(r.left to r.top,r.right to r.top,r.right to r.bottom,r.left to r.bottom).forEach { canvas.drawCircle(it.first,it.second,Ui.dp(context,6).toFloat(),ink) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(image==null || media.width()<=0 || media.height()<=0) return false
        val x=((event.x-media.left)/media.width()).coerceIn(0f,1f); val y=((event.y-media.top)/media.height()).coerceIn(0f,1f)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onGestureStart?.invoke()
                sx=x; sy=y; original.set(crop)
                val points=listOf(crop.left to crop.top,crop.right to crop.top,crop.right to crop.bottom,crop.left to crop.bottom)
                corner=points.indices.minByOrNull { i -> (points[i].first-x)*(points[i].first-x)+(points[i].second-y)*(points[i].second-y) } ?: 0
                val p=points[corner]; if((p.first-x)*(p.first-x)+(p.second-y)*(p.second-y)>.02f && crop.contains(x,y)) corner=4
                parent.requestDisallowInterceptTouchEvent(true); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(corner==4) {
                    val dx=(x-sx).coerceIn(-original.left,1-original.right); val dy=(y-sy).coerceIn(-original.top,1-original.bottom)
                    crop.set(original); crop.offset(dx,dy)
                } else {
                    val left=corner==0 || corner==3; val top=corner==0 || corner==1
                    if(left) crop.left=x.coerceAtMost(crop.right-.05f) else crop.right=x.coerceAtLeast(crop.left+.05f)
                    if(top) crop.top=y.coerceAtMost(crop.bottom-.05f) else crop.bottom=y.coerceAtLeast(crop.top+.05f)
                    if(ratio>0) {
                        val source=image!!.width.toFloat()/image!!.height
                        var w=crop.width(); var h=w/(ratio/source)
                        val anchorY=if(top) original.bottom else original.top
                        val maxH=if(top) anchorY else 1-anchorY
                        h=minOf(h,maxH); w=h*(ratio/source)
                        if(left) crop.left=crop.right-w else crop.right=crop.left+w
                        if(top) crop.top=anchorY-h else crop.bottom=anchorY+h
                    }
                    crop.left=crop.left.coerceIn(0f,.95f); crop.right=crop.right.coerceIn(crop.left+.05f,1f)
                    crop.top=crop.top.coerceIn(0f,.95f); crop.bottom=crop.bottom.coerceIn(crop.top+.05f,1f)
                }
                invalidate(); onCropChanged?.invoke(RectF(crop)); return true
            }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false); onCropChanged?.invoke(RectF(crop)); onGestureEnd?.invoke(); performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { crop.set(original); invalidate(); onCropChanged?.invoke(RectF(crop)); onGestureEnd?.invoke(); parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
