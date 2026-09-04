package eu.kanade.translation.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationGeometryTest {

    @Test
    fun `cleanup region expands OCR bounds and stays inside page`() {
        val block = translationBlock(x = 4f, y = 6f)

        val region = block.defaultCleanupRegion(pageWidth = 200f, pageHeight = 120f)

        assertEquals(0f, region.x)
        assertEquals(0f, region.y)
        assertTrue(region.width > block.width)
        assertTrue(region.height > block.height)
        assertTrue(region.x + region.width <= 200f)
        assertTrue(region.y + region.height <= 120f)
    }

    @Test
    fun `layout region gives translation more room than cleanup region`() {
        val block = translationBlock()

        val cleanup = block.defaultCleanupRegion(500f, 500f)
        val layout = block.defaultLayoutRegion(500f, 500f)

        assertTrue(layout.width > cleanup.width)
        assertTrue(layout.height > cleanup.height)
        assertTrue(layout.x < cleanup.x)
        assertTrue(layout.y < cleanup.y)
    }

    @Test
    fun `inset keeps text away from rounded balloon edges`() {
        val region = TranslationRegion(x = 20f, y = 30f, width = 100f, height = 80f)

        val inset = region.inset(horizontalInset = 10f, verticalInset = 8f)

        assertEquals(30f, inset.x)
        assertEquals(38f, inset.y)
        assertEquals(80f, inset.width)
        assertEquals(64f, inset.height)
    }

    @Test
    fun `clamped region never crosses right or bottom page edge`() {
        val region = TranslationRegion(x = 470f, y = 780f, width = 80f, height = 60f)

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertEquals(470f, clamped.x)
        assertEquals(780f, clamped.y)
        assertEquals(30f, clamped.width)
        assertEquals(20f, clamped.height)
        assertTrue(clamped.x + clamped.width <= 500f)
        assertTrue(clamped.y + clamped.height <= 800f)
    }

    @Test
    fun `clamped region that starts beyond page edge is moved fully inside`() {
        val region = TranslationRegion(x = 540f, y = 830f, width = 50f, height = 40f)

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertEquals(499f, clamped.x)
        assertEquals(799f, clamped.y)
        assertEquals(1f, clamped.width)
        assertEquals(1f, clamped.height)
        assertTrue(clamped.x + clamped.width <= 500f)
        assertTrue(clamped.y + clamped.height <= 800f)
    }

    @Test
    fun `clamped sanitizes non finite geometry before rendering`() {
        val region = TranslationRegion(
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            width = Float.NaN,
            height = Float.NEGATIVE_INFINITY,
        )

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertTrue(clamped.x.isFinite())
        assertTrue(clamped.y.isFinite())
        assertTrue(clamped.width.isFinite() && clamped.width >= 1f)
        assertTrue(clamped.height.isFinite() && clamped.height >= 1f)
        assertTrue(clamped.x >= 0f && clamped.x + clamped.width <= 500f)
        assertTrue(clamped.y >= 0f && clamped.y + clamped.height <= 800f)
    }

    @Test
    fun `clamped keeps an already valid region unchanged`() {
        val region = TranslationRegion(x = 70f, y = 80f, width = 140f, height = 100f)

        val clamped = region.clamped(pageWidth = 500f, pageHeight = 800f)

        assertEquals(region, clamped)
    }

    @Test
    fun `layout saved by first Y9 falls back to safer geometry`() {
        val staleRegion = TranslationRegion(x = 10f, y = 10f, width = 300f, height = 240f)
        val block = translationBlock().copy(layoutRegion = staleRegion)

        val resolved = block.resolvedLayoutRegion(pageWidth = 500f, pageHeight = 500f)

        assertEquals(block.defaultLayoutRegion(500f, 500f), resolved)
    }

    @Test
    fun `current layout geometry keeps detected balloon region`() {
        val detectedRegion = TranslationRegion(x = 70f, y = 80f, width = 140f, height = 100f)
        val block = translationBlock().copy(
            layoutRegion = detectedRegion,
            geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
        )

        val resolved = block.resolvedLayoutRegion(pageWidth = 500f, pageHeight = 500f)

        assertEquals(detectedRegion, resolved)
    }

    @Test
    fun `Y8 translation json remains compatible with Y9 geometry`() {
        val block = Json.decodeFromString<TranslationBlock>(
            """{"text":"Hello","translation":"Olá","width":80.0,"height":30.0,"x":20.0,"y":40.0,"symHeight":15.0,"symWidth":10.0,"angle":0.0}""",
        )

        assertEquals("Olá", block.translation)
        assertNull(block.cleanupRegion)
        assertNull(block.layoutRegion)
        assertNull(block.backgroundColor)
        assertNull(block.foregroundColor)
        assertEquals(false, block.balloonDetected)
        assertEquals(1, block.geometryVersion)
    }

    private fun translationBlock(
        x: Float = 100f,
        y: Float = 100f,
    ) = TranslationBlock(
        text = "Hello world",
        translation = "Olá, mundo",
        width = 80f,
        height = 30f,
        x = x,
        y = y,
        symHeight = 15f,
        symWidth = 10f,
        angle = 0f,
    )
}
