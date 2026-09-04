package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationRegion
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

class SpeechBubbleSeparationRegressionTest {

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
    fun `nearby white balloons keep non overlapping layout regions`() {
        val bitmap: Bitmap = mockk {
            every { width } returns 240
            every { height } returns 240
            every { getPixel(any(), any()) } answers {
                val x = firstArg<Int>()
                val y = secondArg<Int>()
                val firstBalloon = x in 45..195 && y in 25..105
                val secondBalloon = x in 45..195 && y in 115..205
                if (firstBalloon || secondBalloon) 0xffffffff.toInt() else 0xff000000.toInt()
            }
        }
        val analyzer = SpeechBubbleAnalyzer(bitmap, 240, 240)
        val first = block(y = 70f, text = "OBRIGADO POR ME TRAZER ATE AQUI.")
        val second = block(y = 145f, text = "TA TUDO BEM! VOCE VEIO DE TAO LONGE!")

        val firstAnalysis = analyzer.analyze(first)
        val secondAnalysis = analyzer.analyze(second)

        assertTrue(firstAnalysis.balloonDetected)
        assertTrue(secondAnalysis.balloonDetected)
        assertTrue(
            overlapArea(firstAnalysis.layoutRegion, secondAnalysis.layoutRegion) == 0f,
            "Separate nearby balloons must not receive overlapping text layout regions",
        )
    }

    private fun overlapArea(first: TranslationRegion, second: TranslationRegion): Float {
        val left = max(first.x, second.x)
        val top = max(first.y, second.y)
        val right = min(first.x + first.width, second.x + second.width)
        val bottom = min(first.y + first.height, second.y + second.height)
        return (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    }

    private fun block(y: Float, text: String) = TranslationBlock(
        text = text,
        width = 80f,
        height = 20f,
        x = 80f,
        y = y,
        symHeight = 6f,
        symWidth = 4f,
        angle = 0f,
    )
}
