package com.lumen.studio

import android.graphics.Matrix
import android.graphics.RectF

/** Bounds for editor chrome only; these controls never enter exported frames. */
object LayerSelectionGeometry {
    fun bounds(width: Int,height: Int,text: TextLayer?,image: ImageLayer?): RectF? {
        val x=text?.x ?: image?.x ?: return null
        val y=text?.y ?: image!!.y
        val cx=width*(x+1)/2; val cy=height*(1-y)/2
        val halfW=if(text!=null) maxOf(24f,width*.3f) else width*image!!.scale/2
        val halfH=if(text!=null) maxOf(20f,text.size*width/360) else halfW
        return RectF(cx-halfW,cy-halfH,cx+halfW,cy+halfH).also {
            Matrix().apply { setRotate(text?.rotation ?: image!!.rotation,cx,cy) }.mapRect(it)
        }
    }
}
