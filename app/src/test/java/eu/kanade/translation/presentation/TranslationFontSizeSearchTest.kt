package eu.kanade.translation.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationFontSizeSearchTest {

    @Test
    fun `returns the largest font size that fits`() {
        val result = TranslationFontSizeSearch.largestFitting(
            minimum = 4,
            maximum = 12,
            fits = { size -> size <= 7 },
        )

        assertEquals(7, result)
    }

    @Test
    fun `returns null when even the minimum font size overflows`() {
        val result = TranslationFontSizeSearch.largestFitting(
            minimum = 4,
            maximum = 12,
            fits = { false },
        )

        assertNull(result)
    }

    @Test
    fun `selection keeps the largest fitting font above the floor`() {
        val result = TranslationFontSizeSearch.selectWithFloor(
            minimum = 4,
            maximum = 14,
            fits = { size -> size <= 9 },
        )

        assertEquals(9, result.fontSizeSp)
        assertTrue(result.fits)
    }

    @Test
    fun `selection stops at the readable floor when nothing fits`() {
        val result = TranslationFontSizeSearch.selectWithFloor(
            minimum = 4,
            maximum = 14,
            fits = { false },
        )

        assertEquals(4, result.fontSizeSp)
        assertFalse(result.fits)
    }
}
