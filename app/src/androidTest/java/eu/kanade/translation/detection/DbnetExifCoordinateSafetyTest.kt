package eu.kanade.translation.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import eu.kanade.translation.recognizer.OcrEngine
import eu.kanade.translation.recognizer.OcrEngineType
import eu.kanade.translation.recognizer.OcrImage
import eu.kanade.translation.recognizer.OcrPage
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DbnetExifCoordinateSafetyTest {
    @Test fun normalAndNoExifFixturesKeepTheExperimentalPage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = listOf(fixture(context, null), fixture(context, ExifInterface.ORIENTATION_NORMAL))
        try {
            fixtures.forEach { file ->
                assertMlKitMatchesRawPixels(context, file, rawX = 0, rawY = 0, mlKitX = 0, mlKitY = 0)
                val selected = RecordingEngine(page = OcrPage(6, 4, emptyList()))
                val experimental = OcrPage(6, 4, emptyList())
                var experimentalCalls = 0

                val result = engine(context, selected, experimental) { experimentalCalls++ }
                    .recognize(image(file))

                assertSame(experimental, result)
                assertEquals(1, experimentalCalls)
                assertEquals(0, selected.recognizeCalls)
            }
        } finally {
            fixtures.forEach(File::delete)
        }
    }

    @Test fun rotate180FixtureFallsThroughSelectedOcrBeforeExperimentalPageInvocation() = runBlocking {
        assertUnsafeExifFallsBackBeforeExperimentalPage(ExifInterface.ORIENTATION_ROTATE_180) { context, file ->
            assertMlKitMatchesRawPixels(context, file, rawX = 0, rawY = 0, mlKitX = 5, mlKitY = 3)
        }
    }

    @Test fun horizontalMirrorFixtureFallsThroughSelectedOcrBeforeExperimentalPageInvocation() = runBlocking {
        assertUnsafeExifFallsBackBeforeExperimentalPage(ExifInterface.ORIENTATION_FLIP_HORIZONTAL) { context, file ->
            assertMlKitMatchesRawPixels(context, file, rawX = 0, rawY = 0, mlKitX = 5, mlKitY = 0)
        }
    }

    @Test fun invalidExifOrientationFallsThroughSelectedOcrBeforeExperimentalPageInvocation() = runBlocking {
        assertUnsafeExifFallsBackBeforeExperimentalPage(9) { _, file ->
            assertEquals(9, ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
        }
    }

    @Test fun unreadableExifMetadataFallsThroughSelectedOcrBeforeExperimentalPageInvocation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedPage = OcrPage(6, 4, emptyList())
        val selected = RecordingEngine(page = selectedPage)
        val experimental = OcrPage(6, 4, emptyList())
        var experimentalCalls = 0

        val result = engine(context, selected, experimental) { experimentalCalls++ }
            .recognize(OcrImage(Uri.EMPTY) { throw IOException("unreadable test metadata") })

        assertSame(selectedPage, result)
        assertEquals(0, experimentalCalls)
        assertEquals(1, selected.recognizeCalls)
    }

    @Test fun midReadExifMetadataFailureFallsThroughSelectedOcrBeforeExperimentalPageInvocation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selectedPage = OcrPage(6, 4, emptyList())
        val selected = RecordingEngine(page = selectedPage)
        val experimental = OcrPage(6, 4, emptyList())
        var experimentalCalls = 0

        val input = PrefixThenIOExceptionInputStream()
        val result = engine(context, selected, experimental) { experimentalCalls++ }
            .recognize(OcrImage(Uri.EMPTY) { input })

        assertSame(selectedPage, result)
        assertEquals(0, experimentalCalls)
        assertEquals(1, selected.recognizeCalls)
        assertTrue("the EXIF reader must observe the injected stream failure", input.failureObserved)
    }

    private suspend fun assertUnsafeExifFallsBackBeforeExperimentalPage(
        orientation: Int,
        assertMlKitTransform: (Context, File) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture(context, orientation)
        try {
            assertMlKitTransform(context, file)
            val selectedPage = OcrPage(6, 4, emptyList())
            val selected = RecordingEngine(page = selectedPage)
            val experimental = OcrPage(6, 4, emptyList())
            var experimentalCalls = 0

            val result = engine(context, selected, experimental) { experimentalCalls++ }
                .recognize(image(file))

            assertSame(selectedPage, result)
            assertEquals(0, experimentalCalls)
            assertEquals(1, selected.recognizeCalls)
        } finally {
            file.delete()
        }
    }

    private fun engine(
        context: Context,
        selected: RecordingEngine,
        experimental: OcrPage,
        onExperimentalPage: () -> Unit,
    ) = ExperimentalDbnetOcrEngine(
        context = context,
        existing = selected,
        enabled = { true },
        deviceSupported = { true },
        createClient = { error("unsafe EXIF must be rejected before DBNet client creation") },
        createOwnedMlKit = { error("selected ML Kit does not need an owned session") },
        notify = {},
        emitDiagnostic = {},
        experimentalPage = DbnetExperimentalPageRecognizer { _, _, _, _ ->
            onExperimentalPage()
            experimental
        },
    )

    private fun fixture(context: Context, orientation: Int?): File {
        val file = File.createTempFile("dbnet-exif", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(6, 4, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xFFFFFFFF.toInt())
            bitmap.setPixel(0, 0, 0xFFF00000.toInt())
            bitmap.setPixel(5, 0, 0xFF00B000.toInt())
            bitmap.setPixel(0, 3, 0xFF0000E0.toInt())
            bitmap.setPixel(5, 3, 0xFFF0D000.toInt())
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        orientation?.let {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, it.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun image(file: File) = OcrImage(Uri.fromFile(file)) { file.inputStream() }

    private class PrefixThenIOExceptionInputStream : java.io.InputStream() {
        private val prefix = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22,
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
            'I'.code.toByte(), 'I'.code.toByte(), 0x2A, 0, 8, 0, 0, 0,
        )
        private var position = 0
        var failureObserved = false
            private set

        override fun read(): Int = nextByte()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position == prefix.size) failMidRead()
            val count = minOf(length, prefix.size - position)
            prefix.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        private fun nextByte(): Int {
            if (position == prefix.size) failMidRead()
            return prefix[position++].toInt() and 0xFF
        }

        private fun failMidRead(): Nothing {
            failureObserved = true
            throw IOException("mid-read EXIF metadata failure")
        }
    }

    private fun assertMlKitMatchesRawPixels(
        context: Context,
        file: File,
        rawX: Int,
        rawY: Int,
        mlKitX: Int,
        mlKitY: Int,
    ) {
        val raw = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        val mlKit = InputImage.fromFilePath(context, Uri.fromFile(file)).bitmapInternal
        assertNotNull(mlKit)
        try {
            assertColorClose(raw.getPixel(rawX, rawY), mlKit!!.getPixel(mlKitX, mlKitY))
        } finally {
            raw.recycle()
            if (mlKit != null && mlKit !== raw) mlKit.recycle()
        }
    }

    private fun assertColorClose(expected: Int, actual: Int) {
        fun channel(color: Int, shift: Int) = color shr shift and 0xFF
        assertTrue("red channel differs by more than 12", abs(channel(expected, 16) - channel(actual, 16)) <= 12)
        assertTrue("green channel differs by more than 12", abs(channel(expected, 8) - channel(actual, 8)) <= 12)
        assertTrue("blue channel differs by more than 12", abs(channel(expected, 0) - channel(actual, 0)) <= 12)
    }

    private class RecordingEngine(
        private val page: OcrPage,
    ) : OcrEngine {
        override val type = OcrEngineType.ML_KIT
        override val language = TextRecognizerLanguage.ENGLISH
        var recognizeCalls = 0

        override suspend fun recognize(image: OcrImage): OcrPage {
            recognizeCalls++
            return page
        }

        override suspend fun release() = Unit
    }
}
