package eu.kanade.translation.model

import kotlin.math.pow

object TranslationColors {
    const val OPAQUE_BLACK: Int = -0x1000000
    const val OPAQUE_WHITE: Int = -0x1

    /** Chooses the WCAG color with the higher contrast against [background]. */
    fun contrastingForeground(background: Int): Int {
        val relativeLuminance =
            0.2126 * linearChannel(background.red()) +
                0.7152 * linearChannel(background.green()) +
                0.0722 * linearChannel(background.blue())
        val whiteContrast = 1.05 / (relativeLuminance + 0.05)
        val blackContrast = (relativeLuminance + 0.05) / 0.05
        return if (whiteContrast >= blackContrast) OPAQUE_WHITE else OPAQUE_BLACK
    }

    private fun linearChannel(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private fun Int.red(): Int = ushr(16) and 0xff
    private fun Int.green(): Int = ushr(8) and 0xff
    private fun Int.blue(): Int = this and 0xff
}
