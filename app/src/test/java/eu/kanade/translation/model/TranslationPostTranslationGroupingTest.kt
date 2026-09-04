package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationPostTranslationGroupingTest {

    @Test
    fun `translated connector never bridges two distinct detected balloons`() {
        val first = block(
            text = "First OCR region",
            translation = "Eu queria te contar que",
            x = 100f,
            y = 100f,
            layout = TranslationRegion(40f, 50f, 220f, 130f),
            cleanup = TranslationRegion(55f, 65f, 190f, 100f),
        )
        val second = block(
            text = "Second OCR region",
            translation = "isso aconteceu ontem.",
            x = 104f,
            y = 215f,
            layout = TranslationRegion(45f, 205f, 220f, 130f),
            cleanup = TranslationRegion(60f, 220f, 190f, 100f),
        )

        val result = TranslationBlockGrouper.group(listOf(first, second))

        assertEquals(2, result.size)
    }

    private fun block(
        text: String,
        translation: String,
        x: Float,
        y: Float,
        layout: TranslationRegion,
        cleanup: TranslationRegion,
    ) = TranslationBlock(
        text = text,
        translation = translation,
        width = 100f,
        height = 45f,
        x = x,
        y = y,
        symHeight = 20f,
        symWidth = 12f,
        angle = 0f,
        cleanupRegion = cleanup,
        layoutRegion = layout,
        backgroundColor = 0xfff4f4f4.toInt(),
        foregroundColor = 0xff000000.toInt(),
        balloonDetected = true,
        geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
    )
}
