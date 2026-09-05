package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.ModelConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddleOcrV6SmokeTest {
    @Test
    fun officialSmallModelsInitializeDetectAndRecognize() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("OpenCV initialization", OpenCVUtils.init(context))
        val dictionary = ModelConfig.parse(context, "models/rec/inference.yml").characterList
        assertEquals("Official v6 dictionary plus space", 18709, dictionary.size)
        assertEquals("Supplementary Unicode dictionary entry", "🛅", dictionary[dictionary.lastIndex - 1])

        val bitmap = Bitmap.createBitmap(800, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 52f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        repeat(4) { line -> canvas.drawText("HELLO WORLD", 64f, 88f + line * 112f, paint) }

        val ocr = PaddleOCR.create(context)
        try {
            val result = ocr.recognize(bitmap)
            assertEquals("Detect four separated text lines", 4, result.lineCount)
            assertEquals("Recognize all detected lines", 4, result.results.size)
            result.results.forEach { line ->
                assertTrue("Recognized text: ${line.text}", line.text.contains("HELLO"))
                assertTrue("Recognized text: ${line.text}", line.text.contains("WORLD"))
            }
        } finally {
            ocr.release()
            bitmap.recycle()
        }
    }
}
