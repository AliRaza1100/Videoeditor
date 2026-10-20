package com.lumen.studio

/** Semantic palette slots are plain ARGB values, independently contrast-tested. */
object ThemeColors {
    fun palette(name: String)=when(name) {
        "gold" -> intArrayOf(0xFFFFFFFF.toInt(),0xFFFFFFFF.toInt(),0xFFF5DA70.toInt(),0xFF29220F.toInt(),0xFF685C42.toInt(),0xFF785600.toInt(),0xFFD4AF37.toInt(),0xFFDCC890.toInt())
        "violet" -> intArrayOf(0xFFF5F1FF.toInt(),0xFFFFFFFF.toInt(),0xFFE9DFFF.toInt(),0xFF241443.toInt(),0xFF62517D.toInt(),0xFF6B36CF.toInt(),0xFF713BD9.toInt(),0xFFD7C5EE.toInt())
        "light" -> intArrayOf(0xFFFFFFFF.toInt(),0xFFFFFFFF.toInt(),0xFFF5F5F5.toInt(),0xFF000000.toInt(),0xFF333333.toInt(),0xFF000000.toInt(),0xFFE7E7E7.toInt(),0xFFD8D8D8.toInt())
        else -> intArrayOf(0xFF0F1118.toInt(),0xFF181B26.toInt(),0xFF222634.toInt(),0xFFF2F3FA.toInt(),0xFFA3ABC1.toInt(),0xFFB7A6FF.toInt(),0xFF7157DD.toInt(),0xFF303547.toInt())
    }
}

