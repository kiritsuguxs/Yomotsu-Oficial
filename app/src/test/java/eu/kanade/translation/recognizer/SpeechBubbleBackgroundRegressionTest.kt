package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationBlockGrouper
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.sourceRegion
import io.mockk.clearMocks
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechBubbleBackgroundRegressionTest {
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
    fun `wide white balloon line never samples the black page through gaps in peer masks`() {
        for (pageScale in listOf(1, 2, 4)) {
            val printed = lines()
            val sources = printed.map { it.copy(
                x = it.x * pageScale, y = it.y * pageScale,
                width = it.width * pageScale, height = it.height * pageScale,
                symWidth = it.symWidth * pageScale, symHeight = it.symHeight * pageScale,
            ) }
            val result = SpeechBubbleAnalyzer(
                bitmap(printed), 400 * pageScale, 400 * pageScale, sources.map { it.sourceRegion() },
            ).analyze(sources[1])

            assertEquals(WHITE, result.backgroundColor, "The black page outside the balloon is not its cleanup color")
            assertEquals(BLACK, result.foregroundColor)
        }
    }

    @Test
    fun `sampling follows the actual interior for dark and tinted balloons too`() {
        val lines = lines()
        for ((interior, exterior, foreground) in listOf(
            Triple(BLACK, WHITE, WHITE),
            Triple(0xffeadfc5.toInt(), BLACK, BLACK),
        )) {
            val result = SpeechBubbleAnalyzer(
                bitmap(lines, interior, exterior), 400, 400, lines.map { it.sourceRegion() },
            ).analyze(lines[1])

            assertEquals(interior, result.backgroundColor, "The fix must not force every balloon to white")
            assertEquals(foreground, result.foregroundColor)
        }
    }

    @Test
    fun `a tight dark outline is not the background of a white caption`() {
        val source = line("A printed caption", 100f, 100f, 120f, 20f)
        val page = bitmap { x, y ->
            val inside = x in 100..220 && y in 100..120
            val outline = x in 98..222 && y in 98..122 && !inside
            val ink = inside && x % 12 < 8 && y % 14 < 10
            if (outline || ink) BLACK else WHITE
        }
        val result = SpeechBubbleAnalyzer(page, 400, 400, listOf(source.sourceRegion())).analyze(source)

        assertEquals(WHITE, result.backgroundColor)
        assertEquals(BLACK, result.foregroundColor)
        assertEquals(TranslationRegion(100f, 100f, 120f, 20f), result.cleanupRegion,
            "Cleanup must stop inside the outline, not paint through it")
    }

    @Test
    fun `grouped sampling uses original footprints instead of the enclosing corners`() {
        val sources = listOf(
            line("A", 192f, 103f, 16f, 8f),
            line("WIDER PRINTED WORDS", 106f, 176f, 188f, 14f),
        )
        val grouped = sources.first().copy(
            text = "A WIDER PRINTED WORDS", x = 106f, y = 103f, width = 188f, height = 87f,
            sourceRegions = sources.map { it.sourceRegion() },
        )
        val page = bitmap { x, y ->
            val inside = (x - 200) * (x - 200) + (y - 200) * (y - 200) <= 100 * 100
            val ink = sources.any { x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height } &&
                x % 12 < 8 && y % 14 < 10
            if (inside && !ink) WHITE else BLACK
        }
        val result = SpeechBubbleAnalyzer(page, 400, 400, sources.map { it.sourceRegion() }).analyze(grouped)

        assertEquals(WHITE, result.backgroundColor, "The enclosing upper corners lie outside the round balloon")
        assertEquals(BLACK, result.foregroundColor)
    }

    @Test
    fun `a middle line on a dark page stays in its translated paragraph`() {
        val lines = lines()
        val analyzer = SpeechBubbleAnalyzer(bitmap(lines), 400, 400, lines.map { it.sourceRegion() })
        lines.forEach { block ->
            val result = analyzer.analyze(block)
            block.backgroundColor = result.backgroundColor
            block.foregroundColor = result.foregroundColor
            block.layoutRegion = result.layoutRegion
            block.cleanupRegion = result.cleanupRegion
            block.balloonDetected = result.balloonDetected
        }
        val grouped = TranslationBlockGrouper.group(lines, strict = true)

        assertEquals(1, grouped.size, "A wrong dark sample must not split the middle source from the white paragraph")
        assertEquals(3, grouped.single().sourceRegions.size)
        assertEquals(WHITE, analyzer.analyze(grouped.single()).backgroundColor)
    }

    private fun lines() = listOf(
        line("THE FIRST PRINTED LINE", 90f, 158f, 220f, 18f),
        line("A WIDER MIDDLE PRINTED LINE", 80f, 180f, 240f, 16f),
        line("THE LAST PRINTED LINE.", 90f, 200f, 220f, 18f),
    )

    private fun line(text: String, x: Float, y: Float, width: Float, height: Float) = TranslationBlock(
        text = text, x = x, y = y, width = width, height = height,
        symWidth = 10f, symHeight = 16f, angle = 0f,
    )

    private fun bitmap(lines: List<TranslationBlock>, background: Int = WHITE, outside: Int = BLACK): Bitmap = bitmap { x, y ->
        val dx = (x - 200f) / 125f
        val dy = (y - 190f) / 90f
        val ink = lines.any { x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height } &&
            x % 12 < 8 && y % 14 < 10
        if (dx * dx + dy * dy <= 1f && !ink) background else outside
    }

    private fun bitmap(pixel: (Int, Int) -> Int): Bitmap = mockk<Bitmap> {
        every { width } returns 400
        every { height } returns 400
        every { getPixel(any(), any()) } answers { pixel(firstArg(), secondArg()) }
    }.also {
        bitmaps += it
        excludeRecords { it.width; it.height; it.getPixel(any(), any()) }
    }

    private companion object {
        const val WHITE = -1
        const val BLACK = -0x1000000
    }
}
