package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationColorsTest {

    @Test
    fun `dark balloon receives white text`() {
        val darkBlue = 0xff17213b.toInt()

        assertEquals(
            TranslationColors.OPAQUE_WHITE,
            TranslationColors.contrastingForeground(darkBlue),
        )
    }

    @Test
    fun `light colored balloon receives black text`() {
        val lightYellow = 0xffffe58a.toInt()

        assertEquals(
            TranslationColors.OPAQUE_BLACK,
            TranslationColors.contrastingForeground(lightYellow),
        )
    }
}
