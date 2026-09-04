package eu.kanade.translation.presentation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object TranslationRotationFit {

    fun scaleToFit(
        outerWidth: Float,
        outerHeight: Float,
        contentWidth: Float,
        contentHeight: Float,
        angleDegrees: Float,
    ): Float {
        if (outerWidth <= 0f || outerHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f) return 0f
        val radians = Math.toRadians(angleDegrees.toDouble())
        val cosine = abs(cos(radians)).toFloat()
        val sine = abs(sin(radians)).toFloat()
        val rotatedWidth = contentWidth * cosine + contentHeight * sine
        val rotatedHeight = contentWidth * sine + contentHeight * cosine
        return minOf(
            1f,
            outerWidth / rotatedWidth.coerceAtLeast(0.0001f),
            outerHeight / rotatedHeight.coerceAtLeast(0.0001f),
        ).coerceAtLeast(0f)
    }
}
