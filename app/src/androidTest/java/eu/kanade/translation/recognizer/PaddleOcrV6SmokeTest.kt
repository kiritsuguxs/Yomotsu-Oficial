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
import com.paddle.ocr.preprocess.RecPreprocessResult
import com.paddle.ocr.preprocess.RecPreprocessor
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
            val repeated = ocr.recognize(bitmap)
            assertEquals("Reused sessions preserve text, boxes and confidence", result.results, repeated.results)
        } finally {
            ocr.release()
            bitmap.recycle()
        }
    }

    @Test
    fun reusedPreprocessBufferPreservesRgbNormalizationShapesAndPadding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(OpenCVUtils.init(context))
        val widths = listOf(14, 7, 29)
        // OpenCV is an implementation dependency of ppocr-sdk. Exercise its runtime
        // boundary without exposing it to the app or adding a test dependency.
        val matClass = Class.forName("org.opencv.core.Mat")
        val scalarClass = Class.forName("org.opencv.core.Scalar")
        val scalarConstructor = scalarClass.getConstructor(java.lang.Double.TYPE, java.lang.Double.TYPE, java.lang.Double.TYPE)
        val bgr = listOf(
            scalarConstructor.newInstance(0.0, 255.0, 0.0),
            scalarConstructor.newInstance(255.0, 0.0, 255.0),
            scalarConstructor.newInstance(0.0, 0.0, 255.0),
        )
        val rgb = listOf(floatArrayOf(-1f, 1f, -1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, -1f, -1f))
        val matConstructor = matClass.getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE, scalarClass)
        val cv8uc3 = Class.forName("org.opencv.core.CvType").getField("CV_8UC3").getInt(null)
        val crops = widths.mapIndexed { index, width -> matConstructor.newInstance(48, width, cv8uc3, bgr[index]) }
        try {
            val result = RecPreprocessor::class.java.getMethod("preprocessBatch", List::class.java)
                .invoke(RecPreprocessor, crops) as RecPreprocessResult
            assertTrue(longArrayOf(3, 3, 48, 29).contentEquals(result.shape))
            for (batch in 0..2) {
                for (channel in 0..2) {
                    for (row in 0 until 48) {
                        for (column in 0 until 29) {
                            val expected = if (column < widths[batch]) rgb[batch][channel] else 0f
                            assertEquals(expected, result.tensorData[((batch * 3 + channel) * 48 + row) * 29 + column], 0f)
                        }
                    }
                }
            }
        } finally {
            crops.forEach { matClass.getMethod("release").invoke(it) }
        }
    }
}
