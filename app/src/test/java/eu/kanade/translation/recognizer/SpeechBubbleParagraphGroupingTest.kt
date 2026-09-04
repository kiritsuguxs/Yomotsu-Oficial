package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.CURRENT_TRANSLATION_GEOMETRY_VERSION
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationBlockGrouper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechBubbleParagraphGroupingTest {

    @BeforeEach
    fun setUpAndroidColors() {
        mockkStatic(Color::class)
        every { Color.alpha(any()) } answers { firstArg<Int>() ushr 24 and 0xff }
        every { Color.red(any()) } answers { firstArg<Int>() ushr 16 and 0xff }
        every { Color.green(any()) } answers { firstArg<Int>() ushr 8 and 0xff }
        every { Color.blue(any()) } answers { firstArg<Int>() and 0xff }
        every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers {
            0xff000000.toInt() or
                (firstArg<Int>() shl 16) or
                (secondArg<Int>() shl 8) or
                thirdArg<Int>()
        }
    }

    @AfterEach
    fun tearDownAndroidColors() {
        unmockkStatic(Color::class)
    }

    @Test
    fun `complete sentences inside one large balloon become one paragraph`() {
        val bitmap = largeWhiteBalloonBitmap()
        val analyzer = SpeechBubbleAnalyzer(bitmap, PAGE_SIZE, PAGE_SIZE)
        val first = block(text = "ESTA CERTO.", y = 60f, height = 16f)
        val second = block(text = "E ASSIM QUE DEVE SER.", y = 110f, height = 24f)

        applyAnalysis(first, analyzer.analyze(first))
        applyAnalysis(second, analyzer.analyze(second))

        val result = TranslationBlockGrouper.group(listOf(first, second), strict = true)

        assertEquals(1, result.size)
        assertEquals("ESTA CERTO. E ASSIM QUE DEVE SER.", result.single().text)
    }

    private fun largeWhiteBalloonBitmap(): Bitmap = mockk {
        every { width } returns PAGE_SIZE
        every { height } returns PAGE_SIZE
        every { getPixel(any(), any()) } answers {
            val x = firstArg<Int>()
            val y = secondArg<Int>()
            if (x in BALLOON_START..BALLOON_END && y in BALLOON_START..BALLOON_END) {
                0xffffffff.toInt()
            } else {
                0xff000000.toInt()
            }
        }
    }

    private fun block(text: String, y: Float, height: Float) = TranslationBlock(
        text = text,
        width = 44f,
        height = height,
        x = 78f,
        y = y,
        symHeight = 6f,
        symWidth = 4f,
        angle = 0f,
    )

    private fun applyAnalysis(block: TranslationBlock, analysis: SpeechBubbleAnalysis) {
        block.cleanupRegion = analysis.cleanupRegion
        block.layoutRegion = analysis.layoutRegion
        block.backgroundColor = analysis.backgroundColor
        block.foregroundColor = analysis.foregroundColor
        block.balloonDetected = analysis.balloonDetected
        block.geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val BALLOON_START = 40
        const val BALLOON_END = 158
    }
}
