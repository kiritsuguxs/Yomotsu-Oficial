package eu.kanade.translation.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationCleanupCoverageTest {

    @Test
    fun `merge includes cleanup for an OCR fragment without saved cleanup geometry`() {
        val first = fragment(170f, 100f, 40f)
        val last = fragment(100f, 200f, 200f).copy(cleanupRegion = null)

        val merged = TranslationBlockGrouper.group(listOf(first, last)).single()
        val cleanup = requireNotNull(merged.cleanupRegion)

        assertEquals("Source fragment Source fragment", merged.text)
        assertTrue(cleanup.x <= 100f && cleanup.x + cleanup.width >= 300f)
        assertTrue(cleanup.y + cleanup.height >= 220f, "The last translated source line must be erased too")
    }

    @Test
    fun `grouped cleanup covers the corners of the wide final OCR fragment`() {
        val merged = TranslationBlockGrouper.group(listOf(fragment(170f, 100f, 40f), fragment(100f, 200f, 200f))).single()
        val patches = merged.resolvedCleanupPatches(400f, 400f)

        for (point in listOf(100.1f to 219.9f, 299.9f to 219.9f, 170.1f to 100.1f)) {
            assertTrue(patches.any { covers(it, point.first, point.second) }, "Source glyph corner $point is left visible")
        }
    }

    @Test
    fun `merging source fragments does not erase artwork in the gap between them`() {
        val merged = TranslationBlockGrouper.group(listOf(fragment(170f, 100f, 40f), fragment(100f, 200f, 200f))).single()
        val patches = merged.resolvedCleanupPatches(400f, 400f)

        assertFalse(patches.any { covers(it, 190f, 160f) }, "No source glyphs belong to the space between the fragments")
        assertFalse(patches.any { covers(it, 330f, 210f) }, "A neighboring balloon must remain untouched")
    }

    @Test
    fun `source footprints survive reanalysis serialization and a later merge`() {
        val first = fragment(170f, 100f, 40f)
        val last = fragment(100f, 200f, 200f)
        val merged = TranslationBlockGrouper.group(listOf(first, last)).single()
        // ChapterTranslator re-analyzes the grouped bounding box before translation.
        merged.cleanupRegion = merged.defaultCleanupRegion(400f, 400f)
        val restored = Json.decodeFromString<TranslationBlock>(Json.encodeToString(merged))
        val final = TranslationBlockGrouper.group(listOf(restored, fragment(180f, 245f, 40f))).single()
        val patches = final.resolvedCleanupPatches(400f, 400f)

        assertTrue(patches.any { covers(it, 100.1f, 219.9f) })
        assertTrue(patches.any { covers(it, 219.9f, 264.9f) })
        assertFalse(patches.any { covers(it, 190f, 160f) })
    }

    private fun fragment(x: Float, y: Float, width: Float): TranslationBlock = TranslationBlock(
        text = "Source fragment", translation = "Trecho traduzido",
        x = x, y = y, width = width, height = 20f, symWidth = 4f, symHeight = 10f, angle = 0f,
        layoutRegion = TranslationRegion(80f, 80f, 240f, 160f),
        balloonDetected = true, geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION,
    ).also { it.cleanupRegion = it.defaultCleanupRegion(400f, 400f) }

    // Independent raster oracle for the round rectangle sent to the renderer.
    private fun covers(patch: TranslationCleanupPatch, x: Float, y: Float): Boolean {
        val r = patch.region
        if (x < r.x || x > r.x + r.width || y < r.y || y > r.y + r.height) return false
        val radius = patch.cornerRadius
        val centerX = x.coerceIn(r.x + radius, r.x + r.width - radius)
        val centerY = y.coerceIn(r.y + radius, r.y + r.height - radius)
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }
}
