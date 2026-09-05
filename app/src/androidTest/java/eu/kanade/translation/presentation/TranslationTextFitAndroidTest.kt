package eu.kanade.translation.presentation

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.translation.data.TranslationFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class TranslationTextFitAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)

    private fun layout(text: String, size: Int, width: Int, font: FontFamily): TextLayoutResult = measurer.measure(
        text = text,
        style = translationTextStyle(Typography().bodyLarge, size, font),
        constraints = Constraints(minWidth = width, maxWidth = width),
    )

    private fun keepsWords(text: String, layout: TextLayoutResult) = TranslationTextFit.keepsWords(
        text,
        (0 until layout.lineCount - 1).map { layout.getLineEnd(it, visibleEnd = true) },
    )

    @Test
    fun growsOnlyInsideTheExistingSafeEnvelope() {
        TranslationFont.entries.forEach { entry ->
            val font = FontFamily(Font(entry.res))
            val selection = TranslationTextFit.select(6, TranslationTextFit.maximumFontSize(20, true)) {
                val paragraph = layout("SIM!", it, 240, font)
                TranslationTextFit.Measurement(!paragraph.hasVisualOverflow && paragraph.size.height <= 180, true)
            }
            assertTrue(selection.fits)
            assertEquals("Larger font for ${entry.label}", 25, selection.fontSizeSp)
        }
    }

    @Test
    fun arrependimentosStaysWholeWithEveryBundledFont() {
        val text = "ARREPENDIMENTOS"
        TranslationFont.entries.forEach { entry ->
            val font = FontFamily(Font(entry.res))
            assertFalse("Reproduce emergency word split", keepsWords(text, layout(text, 40, 140, font)))
            val selection = TranslationTextFit.select(6, 40) {
                val paragraph = layout(text, it, 140, font)
                TranslationTextFit.Measurement(
                    !paragraph.hasVisualOverflow && paragraph.size.height <= 200,
                    keepsWords(text, paragraph),
                )
            }
            val paragraph = layout(text, selection.fontSizeSp, 140, font)
            assertTrue(selection.fits)
            assertEquals("Whole word for ${entry.label}", 1, paragraph.lineCount)
            assertTrue(keepsWords(text, paragraph))
            assertFalse(paragraph.hasVisualOverflow)
        }
    }

    @Test
    fun crowdedAndRotatedTextStaysInsideTheOriginalEnvelope() {
        val text = "NÃO TENHO ARREPENDIMENTOS. VAMOS CONTINUAR JUNTOS!"
        TranslationFont.entries.forEach { entry ->
            val font = FontFamily(Font(entry.res))
            listOf(0f, 35f, 70f).forEach { angle ->
                val scale = TranslationRotationFit.scaleToFit(120f, 100f, 120f, 100f, angle)
                val width = (120 * scale).toInt()
                val height = (100 * scale).toInt()
                val selection = TranslationTextFit.select(4, 30) {
                    val paragraph = layout(text, it, width, font)
                    TranslationTextFit.Measurement(
                        !paragraph.hasVisualOverflow && paragraph.size.height <= height,
                        keepsWords(text, paragraph),
                    )
                }
                val paragraph = layout(text, selection.fontSizeSp, width, font)
                assertTrue("Fits ${entry.label} at $angle", selection.fits)
                assertFalse(paragraph.hasVisualOverflow)
                assertTrue(paragraph.size.height <= height)
                val radians = Math.toRadians(angle.toDouble())
                val rotatedWidth = width * abs(cos(radians)) + paragraph.size.height * abs(sin(radians))
                val rotatedHeight = width * abs(sin(radians)) + paragraph.size.height * abs(cos(radians))
                assertTrue("Cannot enter a neighboring envelope horizontally", rotatedWidth <= 120)
                assertTrue("Cannot enter a neighboring envelope vertically", rotatedHeight <= 100)
            }
        }
    }
}
