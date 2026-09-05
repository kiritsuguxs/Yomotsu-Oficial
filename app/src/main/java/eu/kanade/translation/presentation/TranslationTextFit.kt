package eu.kanade.translation.presentation

object TranslationTextFit {
    data class Measurement(val fits: Boolean, val keepsWords: Boolean)

    fun maximumFontSize(sourceCeiling: Int, balloonDetected: Boolean): Int =
        if (balloonDetected) (sourceCeiling * 1.25f).toInt().coerceAtMost(48) else sourceCeiling

    fun select(
        minimum: Int,
        maximum: Int,
        measure: (Int) -> Measurement,
    ): TranslationFontSizeSearch.Selection {
        val measured = mutableMapOf<Int, Measurement>()
        fun measurement(size: Int) = measured.getOrPut(size) { measure(size) }
        val wholeWords = TranslationFontSizeSearch.largestFitting(minimum, maximum) {
            val result = measurement(it)
            result.fits && result.keepsWords
        }
        return wholeWords?.let { TranslationFontSizeSearch.Selection(it, true) }
            ?: TranslationFontSizeSearch.selectWithFloor(minimum, maximum) { measurement(it).fits }
    }

    fun keepsWords(text: String, lineEnds: List<Int>): Boolean = lineEnds.none { end ->
        end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
    }
}
