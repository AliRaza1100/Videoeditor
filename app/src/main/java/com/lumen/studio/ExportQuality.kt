package com.lumen.studio

/** Scale compression budget with pixels and motion; resolution alone is not image quality. */
object ExportQuality {
    fun bitrate(width: Int,height: Int,fps: Float=30f): Int =
        (width.toDouble()*height*(if(fps.isFinite()) fps.coerceIn(24f,120f) else 30f)*.24).toLong().coerceIn(2_000_000,80_000_000).toInt()
}
