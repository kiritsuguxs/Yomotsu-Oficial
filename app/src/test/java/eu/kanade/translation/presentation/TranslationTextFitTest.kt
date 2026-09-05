package eu.kanade.translation.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationTextFitTest {
    @Test
    fun `only a detected balloon permits bounded growth`() {
        assertEquals(20, TranslationTextFit.maximumFontSize(20, false))
        assertEquals(25, TranslationTextFit.maximumFontSize(20, true))
        assertEquals(48, TranslationTextFit.maximumFontSize(48, true))
    }

    @Test
    fun `uses larger font only when measured inside the same envelope`() {
        assertEquals(24, TranslationTextFit.select(6, 25) { size ->
            TranslationTextFit.Measurement(size <= 24, true)
        }.fontSizeSp)
        assertEquals(17, TranslationTextFit.select(6, 25) { size ->
            TranslationTextFit.Measurement(size <= 17, true)
        }.fontSizeSp)
    }

    @Test
    fun `prefers whole words over an unnecessarily split larger font`() {
        assertEquals(16, TranslationTextFit.select(6, 25) { size ->
            TranslationTextFit.Measurement(size <= 23, size <= 16)
        }.fontSizeSp)
        assertFalse(TranslationTextFit.keepsWords("ARREPENDIMENTOS", listOf(10)))
        assertTrue(TranslationTextFit.keepsWords("SEM ARREPENDIMENTOS", listOf(3)))
        assertTrue(TranslationTextFit.keepsWords("SEM ARREPENDIMENTOS", listOf(4)))
    }

    @Test
    fun `retains fitting fallback when a word cannot fit even at the floor`() {
        val result = TranslationTextFit.select(6, 25) { size ->
            TranslationTextFit.Measurement(size <= 13, false)
        }
        assertEquals(13, result.fontSizeSp)
        assertTrue(result.fits)
        assertFalse(result.keepsWords)
        assertFalse(TranslationTextFit.select(6, 25) {
            TranslationTextFit.Measurement(false, true)
        }.fits)
    }

    @Test
    fun `signals when another existing safe envelope can avoid a split`() {
        val narrow = TranslationTextFit.select(6, 25) { size ->
            TranslationTextFit.Measurement(size <= 13, false)
        }
        val wider = TranslationTextFit.select(5, 25) { size ->
            TranslationTextFit.Measurement(size <= 13, size <= 7)
        }
        assertTrue(narrow.fits)
        assertFalse(narrow.keepsWords)
        assertTrue(wider.fits)
        assertTrue(wider.keepsWords)
        assertEquals(7, wider.fontSizeSp)
    }
}
