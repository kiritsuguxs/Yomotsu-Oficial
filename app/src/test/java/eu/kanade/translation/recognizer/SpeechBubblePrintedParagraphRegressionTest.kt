package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.CURRENT_TRANSLATION_GEOMETRY_VERSION
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationBlockGrouper
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.sourceRegion
import eu.kanade.translation.model.resolvedCleanupPatches
import eu.kanade.translation.model.TranslationCleanupPatch
import io.mockk.clearMocks
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechBubblePrintedParagraphRegressionTest {
    private val bitmaps = mutableListOf<Bitmap>()

    @BeforeEach
    fun colors() {
        mockkStatic(Color::class)
        every { Color.alpha(any()) } answers { firstArg<Int>() ushr 24 and 0xff }
        every { Color.red(any()) } answers { firstArg<Int>() ushr 16 and 0xff }
        every { Color.green(any()) } answers { firstArg<Int>() ushr 8 and 0xff }
        every { Color.blue(any()) } answers { firstArg<Int>() and 0xff }
        every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers {
            0xff000000.toInt() or (firstArg<Int>() shl 16) or (secondArg<Int>() shl 8) or thirdArg<Int>()
        }
        excludeRecords { Color.alpha(any()); Color.red(any()); Color.green(any()); Color.blue(any()) }
    }

    @AfterEach
    fun cleanup() {
        bitmaps.forEach { clearMocks(it) }
        clearStaticMockk(Color::class)
        unmockkStatic(Color::class)
    }

    @Test
    fun `oval keeps useful paragraph space without crossing its curved boundary`() {
        val source = block("An ordinary sentence.", 148f, 52f)
        val analysis = SpeechBubbleAnalyzer(bitmap(emptyList()), 400, 400).analyze(source)
        assertTrue(analysis.balloonDetected)
        val r = analysis.layoutRegion
        assertTrue(r.width >= 140f && r.height >= 94f,
            "A 236 by 176 oval has room for a 140 by 94 safe paragraph, got $r")
        for (x in listOf(r.x, r.x + r.width)) {
            for (y in listOf(r.y, r.y + r.height)) {
                val dx = (x - 200f) / 118f
                val dy = (y - 180f) / 88f
                assertTrue(dx * dx + dy * dy <= 1f, "The larger paragraph must still fit inside the oval")
            }
        }
    }

    @Test
    fun `printed fragments inside one oval become a single paragraph`() {
        assertOnePrintedParagraph(188f, 30f)
    }

    @Test
    fun `almost touching printed lines do not reject each others background collar`() {
        for (y in listOf(161f, 162f)) assertOnePrintedParagraph(y, 30f)
    }

    private fun assertOnePrintedParagraph(secondY: Float, secondHeight: Float) {
        val first = block("First sentence.", 140f, 20f)
        val second = block("Another sentence.", secondY, secondHeight)
        val blocks = listOf(first, second)
        val analyzer = SpeechBubbleAnalyzer(bitmap(blocks), 400, 400, blocks.map { it.sourceRegion() })
        blocks.forEach { block ->
            val result = analyzer.analyze(block)
            block.cleanupRegion = result.cleanupRegion
            block.layoutRegion = result.layoutRegion
            block.backgroundColor = result.backgroundColor
            block.balloonDetected = result.balloonDetected
            block.geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION
        }
        val grouped = TranslationBlockGrouper.group(blocks, strict = true)
        assertEquals(1, grouped.size, "Lettering belonging to the same balloon must not act as its border: $blocks")
        assertEquals("First sentence. Another sentence.", grouped.single().text)
        assertEquals(2, grouped.single().sourceRegions.size)
        val final = analyzer.analyze(grouped.single())
        assertTrue(final.balloonDetected)
        val r = final.layoutRegion
        assertTrue(r.width >= 110f && r.height >= 50f, "Reanalysis must retain usable paragraph space: $r")
        for (x in listOf(r.x, r.x + r.width)) for (y in listOf(r.y, r.y + r.height)) {
            val dx = (x - 200f) / 118f
            val dy = (y - 180f) / 88f
            assertTrue(dx * dx + dy * dy <= 1f, "Final grouped layout must stay inside the oval")
        }
    }

    @Test
    fun `printed neighboring lobes keep separate paragraphs and layout regions`() {
        val first = block("First complete sentence.", 90f, 25f).copy(x = 100f, width = 80f)
        val second = block("Second complete sentence.", 210f, 25f).copy(x = 200f, width = 80f)
        val sources = listOf(first, second)
        val page = bitmap(sources) { x, y ->
            fun inside(cx: Float, cy: Float): Boolean {
                val dx = (x - cx) / 100f
                val dy = (y - cy) / 70f
                return dx * dx + dy * dy <= 1f
            }
            inside(140f, 110f) || inside(240f, 230f)
        }
        val analyzer = SpeechBubbleAnalyzer(page, 400, 400, sources.map { it.sourceRegion() })
        sources.forEach { source ->
            val result = analyzer.analyze(source)
            assertTrue(result.balloonDetected)
            source.layoutRegion = result.layoutRegion
            source.backgroundColor = result.backgroundColor
            source.balloonDetected = result.balloonDetected
        }
        val a = requireNotNull(first.layoutRegion)
        val b = requireNotNull(second.layoutRegion)
        assertTrue(a.y + a.height <= b.y || a.x + a.width <= b.x,
            "Even with peer lettering masked, neighboring lobes must not share layout space: $a / $b")
        assertEquals(2, TranslationBlockGrouper.group(sources, strict = true).size)
    }

    @Test
    fun `peer OCR box crossing a divider never hides the neighboring balloon contour`() {
        val source = block("An ordinary sentence.", 65f, 20f).copy(x = 100f, width = 120f, symWidth = 5f, symHeight = 8f)
        val page = bitmap(emptyList()) { x, y ->
            val first = x in 30..300 && y in 30..120 && x + y <= 382
            val neighbor = x in 230..340 && y in 80..200 && x + y >= 386
            first || neighbor
        }
        // The peer's center belongs to the neighbor; its empty upper-left OCR
        // box corner crosses the diagonal border of the first balloon.
        val peer = TranslationRegion(265f, 104f, 50f, 24f)
        val result = SpeechBubbleAnalyzer(page, 400, 400, listOf(source.sourceRegion(), peer)).analyze(source)

        assertTrue(result.balloonDetected)
        val r = result.layoutRegion
        assertTrue(r.x + r.width + r.y + r.height <= 382f,
            "OCR bounding boxes must not turn a real divider into background: $r")
    }

    @Test
    fun `cleanup near a curved shoulder covers source ink without erasing the outline`() {
        val source = block("A short line", 215f, 20f).copy(x = 245f, width = 30f, symWidth = 15f, symHeight = 20f)
        val analyzer = SpeechBubbleAnalyzer(bitmap(listOf(source)), 400, 400, listOf(source.sourceRegion()))
        source.cleanupRegion = analyzer.analyze(source).cleanupRegion
        val patches = source.resolvedCleanupPatches(400f, 400f)
        for (x in 245..275) for (y in 215..235) {
            assertTrue(patches.any { covers(it, x.toFloat(), y.toFloat()) }, "Original source must be covered")
        }
        for (x in 200..315) for (y in 180..270) {
            val dx = (x - 200f) / 118f
            val dy = (y - 180f) / 88f
            if (dx * dx + dy * dy > 1f) {
                assertTrue(patches.none { covers(it, x.toFloat(), y.toFloat()) }, "Cleanup erases the contour at $x,$y")
            }
        }
    }

    @Test
    fun `grouping retains individually bounded cleanup masks`() {
        val layout = TranslationRegion(80f, 80f, 230f, 180f)
        val a = block("One sentence.", 100f, 20f).copy(
            cleanupRegion = TranslationRegion(143f, 98f, 114f, 24f),
            layoutRegion = layout, balloonDetected = true,
        )
        val b = block("Second sentence.", 140f, 20f).copy(
            cleanupRegion = TranslationRegion(143f, 138f, 114f, 24f),
            layoutRegion = layout, balloonDetected = true,
        )
        val grouped = Json.decodeFromString<TranslationBlock>(
            Json.encodeToString(TranslationBlockGrouper.group(listOf(a, b)).single()),
        )
        // Reanalysis of the paragraph must not discard its original safe masks.
        grouped.cleanupRegion = TranslationRegion(130f, 85f, 140f, 90f)
        val patches = grouped.resolvedCleanupPatches(400f, 400f)
        assertTrue(patches.none { covers(it, 150f, 92f) || covers(it, 150f, 130f) },
            "Default padding must not replace the already bounded source masks")
        assertTrue(patches.any { covers(it, 150f, 105f) })
        assertTrue(patches.any { covers(it, 150f, 145f) })
    }

    private fun covers(patch: TranslationCleanupPatch, x: Float, y: Float): Boolean {
        val r = patch.region
        if (x < r.x || x > r.x + r.width || y < r.y || y > r.y + r.height) return false
        val radius = minOf(patch.cornerRadius, r.width / 2f, r.height / 2f)
        val dx = x - x.coerceIn(r.x + radius, r.x + r.width - radius)
        val dy = y - y.coerceIn(r.y + radius, r.y + r.height - radius)
        return dx * dx + dy * dy <= radius * radius
    }

    private fun block(text: String, y: Float, height: Float) = TranslationBlock(
        text = text, x = 145f, y = y, width = 110f, height = height,
        symWidth = 6f, symHeight = 10f, angle = 0f,
    )

    private fun bitmap(ink: List<TranslationBlock>, insideBalloon: (Int, Int) -> Boolean = { x, y ->
        val dx = (x - 200f) / 118f
        val dy = (y - 180f) / 88f
        dx * dx + dy * dy <= 1f
    }): Bitmap = mockk<Bitmap> {
        every { width } returns 400
        every { height } returns 400
        every { getPixel(any(), any()) } answers {
            val x = firstArg<Int>()
            val y = secondArg<Int>()
            val letter = ink.any { x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height } &&
                x % 12 < 8 && y % 14 < 10
            if (insideBalloon(x, y) && !letter) 0xffffffff.toInt() else 0xff000000.toInt()
        }
    }.also {
        bitmaps += it
        excludeRecords { it.width; it.height; it.getPixel(any(), any()) }
    }
}
