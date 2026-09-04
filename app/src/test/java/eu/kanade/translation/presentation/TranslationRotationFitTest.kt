package eu.kanade.translation.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationRotationFitTest {

    @Test
    fun `reduces text area so a rotated paragraph remains inside its region`() {
        val scale = TranslationRotationFit.scaleToFit(
            outerWidth = 100f,
            outerHeight = 60f,
            contentWidth = 90f,
            contentHeight = 50f,
            angleDegrees = 45f,
        )

        assertEquals(0.606f, scale, 0.001f)
    }

    @Test
    fun `keeps the full text area when there is no rotation`() {
        val scale = TranslationRotationFit.scaleToFit(
            outerWidth = 100f,
            outerHeight = 60f,
            contentWidth = 90f,
            contentHeight = 50f,
            angleDegrees = 0f,
        )

        assertEquals(1f, scale)
    }
}
