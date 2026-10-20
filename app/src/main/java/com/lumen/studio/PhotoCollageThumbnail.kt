package com.lumen.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.ImageView

/** Immediate layout diagram while the user's real-photo collage thumbnail renders off-thread. */
class PhotoCollageThumbnail(context: Context,private val layout: PhotoLayout): ImageView(context) {
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        if(drawable!=null) { super.onDraw(canvas); return }
        canvas.drawColor(StudioDesign.background(context))
        val gap=Ui.dp(context,3).toFloat()
        layout.cells.forEachIndexed { index,cell ->
            ink.color=if(index%2==0) StudioDesign.primary(context) else StudioDesign.raised(context)
            canvas.drawRoundRect(cell.left*width+gap,cell.top*height+gap,cell.right*width-gap,cell.bottom*height-gap,gap,gap,ink)
        }
    }
}
