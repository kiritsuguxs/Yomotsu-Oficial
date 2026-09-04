package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.TranslationBlock
import io.mockk.clearMocks
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechBubbleCornerBoundaryRegressionTest {

    private val bitmaps = mutableListOf<Bitmap>()

    @BeforeEach
    fun setUpAndroidColors() {
        mockkStatic(Color::class)
        every { Color.alpha(any()) } answers { firstArg<Int>() ushr 24 and 0xff }
        every { Color.red(any()) } answers { firstArg<Int>() ushr 16 and 0xff }
        every { Color.green(any()) } answers { firstArg<Int>() ushr 8 and 0xff }
        every { Color.blue(any()) } answers { firstArg<Int>() and 0xff }
        every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers {
            0xff000000.toInt() or (firstArg<Int>() shl 16) or (secondArg<Int>() shl 8) or thirdArg<Int>()
        }
        // This fixture checks geometry, not invocation counts. Avoid retaining
        // every sampled pixel/channel call while the contour is inspected.
        excludeRecords {
            Color.alpha(any()); Color.red(any()); Color.green(any()); Color.blue(any())
        }
    }

    @AfterEach
    fun tearDownAndroidColors() {
        bitmaps.forEach { clearMocks(it) }
        bitmaps.clear()
        clearStaticMockk(Color::class)
        unmockkStatic(Color::class)
    }

    @Test
    fun `diagonal boundary near another balloon limits only the invading corner`() {
        for (scale in listOf(1f, 2f)) {
            val bitmap = pageBitmap(cutCorner = true)
            val analysis = SpeechBubbleAnalyzer(bitmap, (400 * scale).toInt(), (400 * scale).toInt())
                .analyze(block(scale))
            assertTrue(analysis.balloonDetected)
            val r = analysis.layoutRegion
            val left = r.x / scale
            val top = r.y / scale
            val right = (r.x + r.width) / scale
            val bottom = (r.y + r.height) / scale
            assertTrue(right + bottom <= 382f, "The bottom-right corner crosses the first balloon boundary: $right + $bottom")
            assertTrue(left >= 30f && top >= 30f && right <= 300f && bottom <= 120f)
            assertTrue(r.width / scale >= 210f && r.height / scale >= 72f,
                "A small corner collision must keep the large usable area, not shrink the whole balloon")
        }
    }

    @Test
    fun `unobstructed wide balloon retains its existing layout dimensions`() {
        val analysis = SpeechBubbleAnalyzer(pageBitmap(cutCorner = false), 400, 400).analyze(block(1f))
        assertTrue(analysis.balloonDetected)
        assertEquals(226.8f, analysis.layoutRegion.width, 0.1f)
        assertEquals(75.6f, analysis.layoutRegion.height, 0.1f)
    }

    @Test
    fun `pixel rounding never replaces the constrained balloon with an unsafe fallback`() {
        val source = block(1f).copy(y = 50f, height = 50f, symHeight = 29.01f)
        val analysis = SpeechBubbleAnalyzer(pageBitmap(cutCorner = true, cornerCut = 381), 400, 400).analyze(source)

        assertTrue(analysis.balloonDetected, "A fraction of a pixel below the size threshold must not discard a detected contour")
        val r = analysis.layoutRegion
        assertTrue(r.y >= 30f && r.y + r.height <= 120f, "Fallback must not expand beyond the detected balloon")
        assertTrue(r.x + r.width + r.y + r.height <= 381f)
    }

    private fun pageBitmap(cutCorner: Boolean, cornerCut: Int = 382): Bitmap = mockk<Bitmap> {
        every { width } returns 400
        every { height } returns 400
        every { getPixel(any(), any()) } answers {
            val x = firstArg<Int>()
            val y = secondArg<Int>()
            val first = x in 30..300 && y in 30..120 && (!cutCorner || x + y <= cornerCut)
            val neighbor = cutCorner && x in 230..340 && y in 80..200 && x + y >= cornerCut + 4
            val sourceInk = x in 110..210 && y in 68..81 && x % 9 < 4
            if ((first || neighbor) && !sourceInk) 0xffffffff.toInt() else 0xff000000.toInt()
        }
    }.also { bitmap ->
        bitmaps += bitmap
        excludeRecords { bitmap.getPixel(any(), any()); bitmap.width; bitmap.height }
    }

    private fun block(scale: Float) = TranslationBlock(
        text = "Several source words", translation = "Um trecho traduzido",
        width = 120f * scale, height = 20f * scale, x = 100f * scale, y = 65f * scale,
        symWidth = 5f * scale, symHeight = 8f * scale, angle = 0f,
    )
}
