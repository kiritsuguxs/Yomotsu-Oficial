package eu.kanade.translation.presentation

object TranslationFontSizeSearch {

    data class Selection(
        val fontSizeSp: Int,
        val fits: Boolean,
    )

    fun largestFitting(
        minimum: Int,
        maximum: Int,
        fits: (Int) -> Boolean,
    ): Int? {
        if (minimum > maximum) return null

        var low = minimum
        var high = maximum
        var best: Int? = null
        while (low <= high) {
            val candidate = (low + high) / 2
            if (fits(candidate)) {
                best = candidate
                low = candidate + 1
            } else {
                high = candidate - 1
            }
        }
        return best
    }

    fun selectWithFloor(
        minimum: Int,
        maximum: Int,
        fits: (Int) -> Boolean,
    ): Selection {
        val best = largestFitting(minimum, maximum, fits)
        return Selection(
            fontSizeSp = best ?: minimum.coerceAtMost(maximum),
            fits = best != null,
        )
    }
}
