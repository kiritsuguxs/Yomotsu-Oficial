package eu.kanade.translation.presentation

object TranslationTextFit {
    data class Measurement(val fits: Boolean, val keepsWords: Boolean)
    data class Selection(val fontSizeSp: Int, val fits: Boolean, val keepsWords: Boolean)

    fun maximumFontSize(sourceCeiling: Int, balloonDetected: Boolean): Int =
        if (balloonDetected) (sourceCeiling * 1.25f).toInt().coerceAtMost(48) else sourceCeiling

    fun select(
        minimum: Int,
        maximum: Int,
        measure: (Int) -> Measurement,
    ): Selection {
        val measured = mutableMapOf<Int, Measurement>()
        fun measurement(size: Int) = measured.getOrPut(size) { measure(size) }
        val wholeWords = TranslationFontSizeSearch.largestFitting(minimum, maximum) {
            val result = measurement(it)
            result.fits && result.keepsWords
        }
        if (wholeWords != null) return Selection(wholeWords, true, true)
        val fallback = TranslationFontSizeSearch.selectWithFloor(minimum, maximum) { measurement(it).fits }
        return Selection(fallback.fontSizeSp, fallback.fits, false)
    }

    fun keepsWords(text: String, lineEnds: List<Int>): Boolean = lineEnds.none { end ->
        end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
    }
}
