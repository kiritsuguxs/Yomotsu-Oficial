package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationSourceMetricsTest {
    @Test
    fun `a punctuation sized first symbol cannot set the whole paragraph font ceiling`() {
        val source = block("... An ordinary\nthree line\nparagraph.", height = 74f, symbolHeight = 2f)
        val result = source.withReliableSourceMetrics()

        assertTrue(result.symHeight in 18f..25f, "Use line scale, not the 2px dot or the 74px paragraph: ${result.symHeight}")
        assertTrue(result.symWidth > 4f)
        assertEquals(source.text, result.text)
        assertEquals(source.sourceRegion(), result.sourceRegion())
        assertEquals(2f, source.symHeight, "Do not mutate OCR input")
    }

    @Test
    fun `first symbol outlier does not prevent grouping adjacent source lines`() {
        val first = block("... An ordinary line", height = 20f, symbolHeight = 2f).withReliableSourceMetrics()
        val second = block("continues here.", height = 20f, symbolHeight = 20f)
            .copy(y = 125f, symWidth = 7f).withReliableSourceMetrics()

        val grouped = TranslationBlockGrouper.group(listOf(first, second))

        assertEquals(1, grouped.size)
        assertEquals("... An ordinary line continues here.", grouped.single().text)
        assertEquals(2, grouped.single().sourceRegions.size)
    }

    @Test
    fun `credible glyph metrics and vertical or rotated text stay unchanged`() {
        for (source in listOf(
            block("A normal line", 20f, 18f),
            block("A normal\nparagraph.", 44f, 18f),
            block("Rotated lettering", 44f, 2f).copy(angle = 35f),
            block("Vertical", 160f, 2f).copy(width = 20f),
        )) {
            assertEquals(source, source.withReliableSourceMetrics())
        }
    }

    @Test
    fun `grouped source boxes never become one falsely tall line`() {
        val source = block("First fragment Second fragment", 90f, 10f).copy(
            sourceRegions = listOf(TranslationRegion(100f, 100f, 120f, 20f), TranslationRegion(100f, 170f, 120f, 20f)),
        )
        assertEquals(source, source.withReliableSourceMetrics())
    }

    private fun block(text: String, height: Float, symbolHeight: Float) = TranslationBlock(
        text = text, x = 100f, y = 100f, width = 120f, height = height,
        symWidth = 2f, symHeight = symbolHeight, angle = 0f,
    )
}
