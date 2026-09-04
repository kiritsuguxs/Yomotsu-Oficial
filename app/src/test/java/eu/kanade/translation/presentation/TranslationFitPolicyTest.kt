package eu.kanade.translation.presentation

import eu.kanade.translation.model.TranslationBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationFitPolicyTest {

    @Test
    fun `overflow handling expands usable area before reaching emergency font size`() {
        val profiles = TranslationFitPolicy.progressiveProfiles(
            block("A considerably longer source sentence", "Uma tradução consideravelmente maior", detected = true),
        )

        assertEquals(listOf(6, 5, 4), profiles.map { it.minimumFontSizeSp })
        assertEquals(listOf(0.82f, 0.88f, 0.90f), profiles.map { it.widthRatio })
        assertEquals(listOf(0.76f, 0.82f, 0.86f), profiles.map { it.heightRatio })
    }

    @Test
    fun `normal detected balloon keeps conservative margins`() {
        val profile = TranslationFitPolicy.profile(block("Hello there", "Olá", detected = true))

        assertEquals(0.82f, profile.widthRatio)
        assertEquals(0.76f, profile.heightRatio)
        assertEquals(6, profile.minimumFontSizeSp)
    }

    @Test
    fun `large translation expansion enables safe fitting`() {
        val profile = TranslationFitPolicy.profile(
            block(
                source = "Run now",
                translation = "Corra para longe daqui imediatamente antes que seja tarde demais para escapar",
                detected = true,
            ),
        )

        assertEquals(0.90f, profile.widthRatio)
        assertEquals(0.86f, profile.heightRatio)
        assertEquals(4, profile.minimumFontSizeSp)
    }

    @Test
    fun `difficult irregular region gets largest safe area`() {
        val profile = TranslationFitPolicy.profile(
            block(
                source = "This is a longer original sentence for a difficult region",
                translation = "Esta é uma tradução muito longa que precisa de espaço adicional para continuar totalmente visível dentro de uma região complicada do quadrinho",
                detected = false,
            ),
        )

        assertEquals(0.92f, profile.widthRatio)
        assertEquals(0.90f, profile.heightRatio)
        assertEquals(4, profile.minimumFontSizeSp)
    }

    private fun block(source: String, translation: String, detected: Boolean) = TranslationBlock(
        text = source,
        translation = translation,
        width = 200f,
        height = 100f,
        x = 0f,
        y = 0f,
        symHeight = 20f,
        symWidth = 10f,
        angle = 0f,
        balloonDetected = detected,
    )
}
