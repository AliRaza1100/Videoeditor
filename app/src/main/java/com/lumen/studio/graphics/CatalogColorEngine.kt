package com.lumen.studio.graphics

import android.graphics.ColorMatrix
import kotlin.math.pow

object CatalogColorEngine {
    /** Media3 uploads the result as an RgbMatrix; Canvas uses the same coefficients for stills. */
    fun matrix(catalog: GraphicsCatalog,values: Map<String,Float>,filterId: String,intensity: Float): ColorMatrix {
        val result=ColorMatrix()
        fun apply(after: Boolean) {
            catalog.adjustments.filter { it.afterFilter==after }.forEach { adjustment ->
                val value=(values[adjustment.id] ?: adjustment.default).also { require(it.isFinite()) }.coerceIn(adjustment.minimum,adjustment.maximum)
                val factor=if(adjustment.exponential) 2.0.pow(value.toDouble()).toFloat() else value
                result.postConcat(ColorMatrix(FloatArray(20) { adjustment.base[it]+adjustment.slope[it]*factor }))
            }
        }
        apply(false)
        catalog.filter(filterId)?.let { filter ->
            val identity=ColorMatrix().array; val t=intensity.coerceIn(0f,1f)
            result.postConcat(ColorMatrix(FloatArray(20) { identity[it]*(1-t)+filter.matrix[it]*t }))
        }
        apply(true)
        return result
    }
}
