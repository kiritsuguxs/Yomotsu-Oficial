package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationBlockVisualGroupingTest {

    @Test
    fun `aligned open ended fragments at comic spacing merge when visual size matches`() {
        val first = block(
            text = "The hunters were",
            x = 100f,
            y = 100f,
            symWidth = 12f,
            symHeight = 20f,
            layout = TranslationRegion(60f, 60f, 180f, 90f),
        )
        val second = block(
            text = "summoned together.",
            x = 103f,
            y = 175f,
            symWidth = 12.4f,
            symHeight = 20.5f,
            layout = TranslationRegion(62f, 170f, 180f, 90f),
        )

        val result = TranslationBlockGrouper.group(listOf(first, second))

        assertEquals(1, result.size)
        assertEquals("The hunters were summoned together.", result.single().text)
    }

    @Test
    fun `nearby aligned fragments with very different visual character size stay separate`() {
        val first = block(
            text = "A quiet line",
            x = 100f,
            y = 100f,
            symWidth = 10f,
            symHeight = 18f,
            detected = false,
            layout = null,
        )
        val second = block(
            text = "LOUD EFFECT",
            x = 102f,
            y = 150f,
            symWidth = 24f,
            symHeight = 38f,
            detected = false,
            layout = null,
        )

        val result = TranslationBlockGrouper.group(listOf(first, second))

        assertEquals(2, result.size)
    }

    private fun block(
        text: String,
        x: Float,
        y: Float,
        symWidth: Float,
        symHeight: Float,
        detected: Boolean = true,
        layout: TranslationRegion?,
    ) = TranslationBlock(
        text = text,
        width = 100f,
        height = 45f,
        x = x,
        y = y,
        symHeight = symHeight,
        symWidth = symWidth,
        angle = 0f,
        cleanupRegion = TranslationRegion(x - 8f, y - 5f, 116f, 55f),
        layoutRegion = layout,
        backgroundColor = 0xfff4f4f4.toInt(),
        foregroundColor = 0xff000000.toInt(),
        balloonDetected = detected,
        geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
    )
}
