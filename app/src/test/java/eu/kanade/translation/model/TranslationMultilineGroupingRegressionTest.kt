package eu.kanade.translation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationMultilineGroupingRegressionTest {
    @Test
    fun `multiline paragraph includes a short source fragment missed by balloon detection`() {
        for (strict in listOf(false, true)) {
            val paragraph = paragraph()
            val lastLine = lastLine()

            val grouped = TranslationBlockGrouper.group(listOf(paragraph, lastLine), strict)

            assertEquals(1, grouped.size, "Line count must not change apparent glyph width")
            assertEquals("WE WERE THERE\nWHEN SHE SAID\nIT WAS ALL OK\nBEFORE GOING ON THE WAY.", grouped.single().text)
            val patches = grouped.single().resolvedCleanupPatches(400f, 400f)
            assertTrue(patches.any { patch ->
                val r = patch.region
                r.x <= 110f && r.x + r.width >= 200f && r.y <= 185f && r.y + r.height >= 201f
            }, "The final source fragment must stay in the translated paragraph's cleanup")
        }
    }

    @Test
    fun `multiline comparison still rejects lettering of a genuinely different scale`() {
        val large = lastLine().copy(symWidth = 26f, symHeight = 40f)
        assertEquals(2, TranslationBlockGrouper.group(listOf(paragraph(), large)).size)
    }

    @Test
    fun `repeated merges do not make apparent glyph width progressively smaller`() {
        val paragraph = paragraph().copy(layoutRegion = TranslationRegion(75f, 80f, 170f, 260f))
        val lines = (0..4).map { lastLine().copy(y = 185f + it * 26f) }
        val grouped = TranslationBlockGrouper.group(listOf(paragraph) + lines)

        assertEquals(1, grouped.size, "Merged source text is not one progressively longer printed line")
        assertEquals(6, grouped.single().sourceRegions.size)
        assertEquals(6, grouped.single().resolvedCleanupPatches(400f, 400f).size)
    }

    @Test
    fun `multiline paragraph never pulls in a detached detected balloon`() {
        val separate = lastLine().copy(
            y = 270f, balloonDetected = true,
            layoutRegion = TranslationRegion(80f, 250f, 160f, 90f),
            backgroundColor = 0xffffffff.toInt(),
        )
        assertEquals(2, TranslationBlockGrouper.group(listOf(paragraph(), separate)).size)
    }

    @Test
    fun `a translated paragraph never absorbs cleanup for a failed translation`() {
        for (strict in listOf(false, true)) for (reverse in listOf(false, true)) for (translateParagraph in listOf(false, true)) {
            val paragraph = paragraph()
            val line = lastLine().copy(backgroundColor = 0xffffffff.toInt())
            val translated = (if (translateParagraph) paragraph else line).copy(translation = "Um trecho traduzido.")
            val failed = if (translateParagraph) line else paragraph
            val input = listOf(translated, failed).let { if (reverse) it.reversed() else it }

            val grouped = TranslationBlockGrouper.group(input, strict)

            assertEquals(2, grouped.size, "Do not erase source text for which the provider returned no translation")
            assertEquals(failed.text, grouped.single { it.translation.isBlank() }.text)
            assertEquals(translated.text, grouped.single { it.translation.isNotBlank() }.text)
        }
    }

    private fun paragraph() = TranslationBlock(
        text = "WE WERE THERE\nWHEN SHE SAID\nIT WAS ALL OK\nBEFORE GOING",
        x = 100f, y = 100f, width = 110f, height = 76f,
        symWidth = 8f, symHeight = 16f, angle = 0f,
        layoutRegion = TranslationRegion(75f, 80f, 170f, 150f),
        backgroundColor = 0xffffffff.toInt(), balloonDetected = true,
        geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
    )

    private fun lastLine() = TranslationBlock(
        text = "ON THE WAY.",
        x = 110f, y = 185f, width = 90f, height = 16f,
        symWidth = 8f, symHeight = 16f, angle = 0f,
        layoutRegion = TranslationRegion(96f, 166f, 118f, 54f),
        geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
    )
}
