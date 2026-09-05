package eu.kanade.translation.detection

import eu.kanade.translation.model.DbnetMaskCleanup
import eu.kanade.translation.model.LegacyCleanup
import eu.kanade.translation.model.NoCleanup
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.resolveCleanup
import eu.kanade.translation.model.resolvedCleanupPatches
import eu.kanade.translation.recognizer.OcrPage
import eu.kanade.translation.recognizer.OcrTextBlock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbnetCleanupMaskTest {
    @Test fun `high confidence sparse large glyph mask gains only one extra pixel inside permission`() {
        val permission = line(2f, 2f, 30f, 30f).copy(confidence = 0.98f)
        val prepared = prepare(
            32, 32, mask(32, 32, 32, 32, setOf(15 to 15, 2 to 2)),
            listOf(group(0, permission)),
        )
        val erased = pixels(requireNotNull(prepared.blocks.single().dbnetCleanupMask), 32, 32)
        assertTrue(18 to 15 in erased, "Cover the third-pixel glyph fringe")
        assertFalse(19 to 15 in erased, "Never grow more than three pixels")
        assertTrue(erased.all { (x, y) -> x in 2..29 && y in 2..29 })
        assertTrue(erased.size < 100, "Sparse glyphs must not turn into a solid rectangle")
    }

    @Test fun `uncertain mask keeps the existing two pixel expansion`() {
        val prepared = prepare(
            32, 32, mask(32, 32, 32, 32, setOf(15 to 15)),
            listOf(group(0, line(2f, 2f, 30f, 30f).copy(confidence = 0.8f))),
        )
        val erased = pixels(requireNotNull(prepared.blocks.single().dbnetCleanupMask), 32, 32)
        assertTrue(17 to 15 in erased)
        assertFalse(18 to 15 in erased)
    }

    @Test fun `mask keeps glyphs inside translated member quads and removes outside pixels`() {
        val inside = line(0f, 0f, 4f, 4f)
        val prepared = prepare(
            width = 8,
            height = 8,
            mask = mask(8, 8, 8, 8, setOf(1 to 1, 6 to 6)),
            groups = listOf(group(0, inside)),
        )

        val pixels = pixels(requireNotNull(prepared.blocks.single().dbnetCleanupMask), 8, 8)
        assertTrue(1 to 1 in pixels, "The translated glyph pixel must remain erasable")
        assertFalse(6 to 6 in pixels, "An unassociated DBNet pixel must not be erased")
    }

    @Test fun `rotated member quad remains the exact permission instead of its group AABB`() {
        val diamond = TextRegion(
            DetectionPoint(4f, 1f), DetectionPoint(7f, 4f),
            DetectionPoint(4f, 7f), DetectionPoint(1f, 4f), 0.9f,
        )
        val prepared = prepare(
            width = 8,
            height = 8,
            mask = mask(8, 8, 8, 8, setOf(3 to 3, 4 to 3, 3 to 4, 4 to 4)),
            groups = listOf(group(0, diamond)),
        )

        val pixels = pixels(requireNotNull(prepared.blocks.single().dbnetCleanupMask), 8, 8)
        assertTrue(pixels.isNotEmpty())
        assertTrue(pixels.all { (x, y) -> contains(diamond.points, x + 0.5f, y + 0.5f) })
        assertFalse(1 to 1 in pixels, "The empty corner of the rotated group's AABB is artwork")
    }

    @Test fun `small residuals two pixels from a glyph are covered without escaping member permission`() {
        val permission = line(2f, 2f, 7f, 7f)
        val prepared = prepare(
            width = 8,
            height = 8,
            mask = mask(8, 8, 8, 8, setOf(4 to 4)),
            groups = listOf(group(0, permission)),
        )

        val pixels = pixels(requireNotNull(prepared.blocks.single().dbnetCleanupMask), 8, 8)
        assertTrue(4 to 4 in pixels)
        assertTrue(2 to 4 in pixels, "The bounded dilation must cover a small residual two pixels away")
        assertTrue(6 to 4 in pixels, "The bounded dilation must cover both glyph edges")
        assertFalse(1 to 4 in pixels, "Dilation must be clipped to the exact member quad")
        assertTrue(pixels.all { (x, y) -> x in 2..6 && y in 2..6 })
    }

    @Test fun `dense mask is rejected instead of becoming a large rectangular cleanup fill`() {
        val width = 20
        val height = 12
        val solid = (0 until height).flatMap { y -> (0 until width).map { x -> x to y } }.toSet()

        assertThrows(DbnetCleanupMaskException::class.java) {
            prepare(
                width,
                height,
                mask(width, height, width, height, solid),
                listOf(group(0, line(1f, 1f, 19f, 11f))),
            )
        }
    }

    @Test fun `DBNet pixel outside permission cannot seed dilation back inside`() {
        val permission = line(2f, 2f, 4f, 5f)
        assertThrows(DbnetCleanupMaskException::class.java) {
            prepare(
                width = 8,
                height = 8,
                mask = mask(8, 8, 8, 8, setOf(1 to 3)),
                groups = listOf(group(0, permission)),
            )
        }
    }

    @Test fun `non square native mask maps with detector input scale and ignores padding`() {
        val permission = line(0f, 0f, 1000f, 100f)
        val prepared = prepare(
            width = 1000,
            height = 100,
            mask = mask(1000, 100, 8, 4, setOf(4 to 0, 4 to 3)),
            groups = listOf(group(0, permission)),
        )

        val pixels = pixels(requireNotNull(prepared.blocks.single().dbnetCleanupMask), 1000, 100)
        assertTrue(550 to 20 in pixels, "The unpadded native row must map to original scale")
        assertFalse(pixels.any { (_, y) -> y >= 70 }, "The native row covering bottom padding is not page text")
    }

    @Test fun `invalid dimensions ratio byte count and bounded work are rejected before use`() {
        val permission = line(0f, 0f, 8f, 8f)
        val valid = mask(8, 8, 8, 8, setOf(1 to 1))
        val invalidMasks = listOf(
            DbnetMask(valid.width, valid.height, valid.inputWidth + 1, valid.inputHeight, valid.ratio, valid.bytes),
            DbnetMask(valid.width, valid.height, valid.inputWidth, valid.inputHeight, valid.ratio / 2f, valid.bytes),
            DbnetMask(valid.width, valid.height, valid.inputWidth, valid.inputHeight, valid.ratio, byteArrayOf()),
        )
        invalidMasks.forEach { invalid ->
            assertThrows(DbnetCleanupMaskException::class.java) {
                prepare(8, 8, invalid, listOf(group(0, permission)))
            }
        }

        val hugePermission = line(0f, 0f, 4000f, 3000f)
        assertThrows(DbnetCleanupMaskException::class.java) {
            prepare(4000, 3000, mask(4000, 3000, 1, 1, setOf(0 to 0)), listOf(group(0, hugePermission)))
        }
    }

    @Test fun `checkerboard source is rejected before intermediate runs exceed the page cap`() {
        assertThrows(DbnetCleanupMaskException::class.java) {
            prepare(
                1024,
                1024,
                checkerboardMask(1024, 1024),
                listOf(group(0, line(0f, 0f, 1024f, 1024f))),
            )
        }
    }

    @Test fun `valid empty persisted mask stays a safe explicit no erase mask`() {
        val cleanup = DbnetCleanupMask.fromRuns(8, 8, intArrayOf())
        assertTrue(cleanup.forEachRun(8, 8) { _, _, _ -> error("Empty mask emitted a run") })
        assertTrue(cleanup.isEmpty)
    }

    @Test fun `associated group with no usable glyph mask fails the experimental page`() {
        assertThrows(DbnetCleanupMaskException::class.java) {
            prepare(8, 8, mask(8, 8, 8, 8, emptySet()), listOf(group(0, line(0f, 0f, 8f, 8f))))
        }
    }

    @Test fun `one empty associated group rejects all prepared masks atomically`() {
        assertThrows(DbnetCleanupMaskException::class.java) {
            prepare(
                8,
                8,
                mask(8, 8, 8, 8, setOf(1 to 1)),
                listOf(group(0, line(0f, 0f, 3f, 3f)), group(1, line(5f, 5f, 8f, 8f))),
            )
        }
    }

    @Test fun `prepared mask ownership survives OCR normalization copy and filtering`() {
        val prepared = prepare(
            8,
            8,
            mask(8, 8, 8, 8, setOf(1 to 1, 6 to 6)),
            listOf(group(0, line(0f, 0f, 3f, 3f)), group(1, line(5f, 5f, 8f, 8f))),
            texts = listOf("A", " Keep me "),
        )

        val normalized = prepared.blocks.map { it.copy(text = it.text.trim()) }.filter { it.text.length > 1 }
        val pixels = pixels(requireNotNull(normalized.single().dbnetCleanupMask), 8, 8)
        assertTrue(6 to 6 in pixels)
        assertFalse(1 to 1 in pixels)
    }

    @Test fun `invalid mask preparation falls back before translation side effects`() = runTest {
        val valid = mask(8, 8, 8, 8, setOf(1 to 1))
        val bad = DbnetMask(valid.width, valid.height, valid.inputWidth, valid.inputHeight, Float.NaN, valid.bytes)
        var translationSideEffects = 0

        val result = ExperimentalDetectionRoute.execute(
            enabled = true,
            supported = true,
            experimental = {
                prepare(8, 8, bad, listOf(group(0, line(0f, 0f, 8f, 8f))))
                translationSideEffects++
                "experimental"
            },
            existing = { "whole-page fallback" },
            onFallback = {},
        )

        assertEquals("whole-page fallback", result)
        assertEquals(0, translationSideEffects)
    }

    @Test fun `mask round trips old JSON defaults null and corrupt optional payload fails closed`() {
        val cleanup = requireNotNull(
            prepare(8, 8, mask(8, 8, 8, 8, setOf(2 to 2)), listOf(group(0, line(0f, 0f, 5f, 5f))))
                .blocks.single().dbnetCleanupMask,
        )
        val block = translationBlock(cleanup)
        val encoded = Json.encodeToString(block)
        val restored = Json.decodeFromString<TranslationBlock>(encoded)
        assertEquals(pixels(cleanup, 8, 8), pixels(requireNotNull(restored.dbnetCleanupMask), 8, 8))

        val oldJson = encoded.replace(Regex(",?\\\"dbnetCleanupMask\\\":\\{.*?\\}(?=,|})"), "")
        assertNull(Json.decodeFromString<TranslationBlock>(oldJson).dbnetCleanupMask)

        val corruptJson = encoded.replace(Regex("\\\"checksum\\\":-?\\d+"), "\"checksum\":0")
        val corrupt = Json.decodeFromString<TranslationBlock>(corruptJson)
        assertNotNull(corrupt.dbnetCleanupMask)
        assertSame(NoCleanup, corrupt.copy(translation = "Traduzido").resolveCleanup(8f, 8f))

        val wrongTypeJson = encoded.replace(Regex("\\\"runs\\\":\\[[^]]*]"), "\"runs\":\"broken\"")
        val wrongType = Json.decodeFromString<TranslationBlock>(wrongTypeJson)
        assertNotNull(wrongType.dbnetCleanupMask)
        assertSame(NoCleanup, wrongType.copy(translation = "Traduzido").resolveCleanup(8f, 8f))
    }

    @Test fun `saved page aggregate mask budget fails closed without invoking legacy erasers`() {
        val runs = IntArray(50 * 3) { index ->
            when (index % 3) {
                0 -> index / 3
                1 -> 0
                else -> 100_000
            }
        }
        val first = DbnetCleanupMask.fromRuns(100_000, 100, runs)
        val second = DbnetCleanupMask.fromRuns(100_000, 100, runs.copyOf())
        assertTrue(DbnetCleanupMask.areValidForPage(listOf(first), 100_000, 100))
        assertFalse(DbnetCleanupMask.areValidForPage(listOf(first, second), 100_000, 100))

        val masked = translationBlock(first).copy(translation = "Traduzido")
        assertSame(NoCleanup, masked.resolveCleanup(100_000f, 100f, experimentalPageValid = false))
    }

    @Test fun `null mask uses the exact legacy cleanup patches and masked blank translation erases nothing`() {
        val legacy = translationBlock(null).copy(translation = "Traduzido")
        val expected = legacy.resolvedCleanupPatches(8f, 8f)
        val resolved = legacy.resolveCleanup(8f, 8f)
        assertTrue(resolved is LegacyCleanup)
        assertEquals(expected, (resolved as LegacyCleanup).patches)

        val cleanup = DbnetCleanupMask.fromRuns(8, 8, intArrayOf(2, 2, 1))
        assertSame(NoCleanup, translationBlock(cleanup).resolveCleanup(8f, 8f))
        assertTrue(translationBlock(cleanup).copy(translation = "Traduzido").resolveCleanup(8f, 8f) is DbnetMaskCleanup)
    }

    private fun prepare(
        width: Int,
        height: Int,
        mask: DbnetMask,
        groups: List<DbnetAssociatedGroup>,
        texts: List<String> = groups.indices.map { "Text $it" },
    ): OcrPage = DbnetCleanupMask.prepare(
        OcrPage(
            width,
            height,
            texts.mapIndexed { index, text -> block(text, groups[index].group.bounds.left, groups[index].group.bounds.top) },
            dbnetAssociation = DbnetAssociationMetadata(mask, groups),
        ),
    )

    private fun group(index: Int, vararg lines: TextRegion): DbnetAssociatedGroup = DbnetAssociatedGroup(
        index,
        DbnetLineGrouping.group(lines.toList()).single(),
    )

    private fun mask(
        pageWidth: Int,
        pageHeight: Int,
        width: Int,
        height: Int,
        set: Set<Pair<Int, Int>>,
    ): DbnetMask {
        val plan = DbnetResizePlan.create(pageWidth, pageHeight)
        val bytes = ByteArray((width * height + 7) / 8)
        set.forEach { (x, y) ->
            val index = y * width + x
            bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (index % 8))).toByte()
        }
        return DbnetMask(width, height, plan.inputWidth, plan.inputHeight, plan.ratio, bytes)
    }

    private fun checkerboardMask(width: Int, height: Int): DbnetMask {
        val plan = DbnetResizePlan.create(width, height)
        val bytes = ByteArray((width * height + 7) / 8)
        for (y in 0 until height) for (x in 0 until width) {
            if ((x + y) % 2 == 0) {
                val index = y * width + x
                bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (index % 8))).toByte()
            }
        }
        return DbnetMask(width, height, plan.inputWidth, plan.inputHeight, plan.ratio, bytes)
    }

    private fun pixels(mask: DbnetCleanupMask, width: Int, height: Int): Set<Pair<Int, Int>> = buildSet {
        assertTrue(mask.forEachRun(width, height) { y, x, length ->
            repeat(length) { offset -> add(x + offset to y) }
        })
    }

    private fun line(left: Float, top: Float, right: Float, bottom: Float) = TextRegion(
        DetectionPoint(left, top), DetectionPoint(right, top),
        DetectionPoint(right, bottom), DetectionPoint(left, bottom), 0.9f,
    )

    private fun block(text: String, x: Float, y: Float) = OcrTextBlock(
        text, x, y, 1f, 1f, 1f, 1f, 0f,
    )

    private fun translationBlock(mask: DbnetCleanupMask?) = TranslationBlock(
        text = "Source", width = 2f, height = 2f, x = 2f, y = 2f,
        symHeight = 1f, symWidth = 1f, angle = 0f, dbnetCleanupMask = mask,
    )

    private fun contains(points: List<DetectionPoint>, x: Float, y: Float): Boolean {
        var inside = false
        var previous = points.lastIndex
        for (current in points.indices) {
            val first = points[current]
            val second = points[previous]
            if ((first.y > y) != (second.y > y) &&
                x < (second.x - first.x) * (y - first.y) / (second.y - first.y) + first.x
            ) inside = !inside
            previous = current
        }
        return inside
    }
}
