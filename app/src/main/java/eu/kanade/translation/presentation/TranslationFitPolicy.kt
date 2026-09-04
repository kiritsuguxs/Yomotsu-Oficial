package eu.kanade.translation.presentation

import eu.kanade.translation.model.TranslationBlock

data class TranslationFitProfile(
    val widthRatio: Float,
    val heightRatio: Float,
    val minimumFontSizeSp: Int,
)

/**
 * Chooses a conservative fitting profile before Compose measures the paragraph.
 * Long translations or large expansions get extra usable space and may shrink a
 * little further instead of being clipped at the old 6sp floor.
 */
object TranslationFitPolicy {

    fun progressiveProfiles(block: TranslationBlock): List<TranslationFitProfile> {
        return if (block.balloonDetected) {
            listOf(
                TranslationFitProfile(0.82f, 0.76f, 6),
                TranslationFitProfile(0.88f, 0.82f, 5),
                TranslationFitProfile(0.90f, 0.86f, 4),
            )
        } else {
            listOf(
                TranslationFitProfile(0.88f, 0.84f, 6),
                TranslationFitProfile(0.92f, 0.90f, 5),
                TranslationFitProfile(0.95f, 0.93f, 4),
            )
        }
    }

    fun profile(block: TranslationBlock): TranslationFitProfile {
        val sourceLength = block.text.count { !it.isWhitespace() }.coerceAtLeast(1)
        val translatedLength = block.translation.count { !it.isWhitespace() }
        val expansionRatio = translatedLength / sourceLength.toFloat()
        val difficult = translatedLength >= LONG_TRANSLATION_CHARACTERS ||
            expansionRatio >= LARGE_EXPANSION_RATIO

        return when {
            difficult && block.balloonDetected -> TranslationFitProfile(0.90f, 0.86f, 4)
            difficult -> TranslationFitProfile(0.92f, 0.90f, 4)
            block.balloonDetected -> TranslationFitProfile(0.82f, 0.76f, 6)
            else -> TranslationFitProfile(0.88f, 0.84f, 6)
        }
    }

    private const val LONG_TRANSLATION_CHARACTERS = 90
    private const val LARGE_EXPANSION_RATIO = 1.55f
}
