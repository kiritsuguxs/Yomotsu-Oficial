package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.TranslationBlock
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechBubbleOvalLayoutRegressionTest {

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
    }

    @AfterEach
    fun tearDownAndroidColors() = unmockkStatic(Color::class)

    @Test
    fun `narrow oval balloon keeps detected layout inside curved shoulders`() {
        val bitmap: Bitmap = mockk {
            every { width } returns PAGE_SIZE
            every { height } returns PAGE_SIZE
            every { getPixel(any(), any()) } answers {
                val x = firstArg<Int>()
                val y = secondArg<Int>()
                val dx = (x - CENTER_X) / RADIUS_X.toFloat()
                val dy = (y - CENTER_Y) / RADIUS_Y.toFloat()
                if (dx * dx + dy * dy <= 1f) 0xffffffff.toInt() else 0xff000000.toInt()
            }
        }
        val block = TranslationBlock(
            text = "EXCUSE ME",
            translation = "...COM LICENÇA",
            width = 54f,
            height = 18f,
            x = 73f,
            y = 91f,
            symHeight = 9f,
            symWidth = 6f,
            angle = 0f,
        )

        val analysis = SpeechBubbleAnalyzer(bitmap, PAGE_SIZE, PAGE_SIZE).analyze(block)

        assertTrue(analysis.balloonDetected)
        val layout = analysis.layoutRegion
        val topShoulderHalfWidth = ellipseHalfWidthAtY(layout.y)
        val bottomShoulderHalfWidth = ellipseHalfWidthAtY(layout.y + layout.height)
        val layoutHalfWidth = layout.width / 2f
        assertTrue(
            layoutHalfWidth <= minOf(topShoulderHalfWidth, bottomShoulderHalfWidth),
            "The rectangular text layout must fit inside the oval at its top and bottom edges",
        )
    }

    private fun ellipseHalfWidthAtY(y: Float): Float {
        val dy = ((y - CENTER_Y) / RADIUS_Y).coerceIn(-1f, 1f)
        return RADIUS_X * kotlin.math.sqrt(1f - dy * dy)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val CENTER_X = 100
        const val CENTER_Y = 100
        const val RADIUS_X = 58
        const val RADIUS_Y = 38
    }
}
