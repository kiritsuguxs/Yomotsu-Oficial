package eu.kanade.translation.detection

import android.content.Context
import android.net.Uri
import eu.kanade.translation.recognizer.OcrEngine
import eu.kanade.translation.recognizer.OcrEngineType
import eu.kanade.translation.recognizer.OcrImage
import eu.kanade.translation.recognizer.OcrPage
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class ExperimentalDbnetOcrEngineTest {
    @Test fun `bounded EXIF input rejects over-limit and negative skips`() {
        val input = DbnetBoundedInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), maxBytes = 2)

        assertThrows(IllegalArgumentException::class.java) { input.skip(-1) }
        assertEquals(2, input.read(ByteArray(2)))
        assertThrows(IOException::class.java) { input.read() }
    }

    @Test fun `compatible EXIF allows experimental work`() = runBlocking {
        val events = mutableListOf<String>()
        val selectedPage = OcrPage(100, 200, emptyList())
        val experimentalPage = OcrPage(100, 200, emptyList())
        val selected = RecordingEngine(OcrEngineType.PADDLE_OCR, selectedPage, events, "selected")
        var orientationChecks = 0
        var experimentalCalls = 0
        val engine = engine(
            selected = selected,
            clientFactory = { error("test page recognizer does not require a client") },
            ownedMlKit = RecordingEngine(OcrEngineType.ML_KIT, selectedPage, events, "owned"),
            notify = { error("compatible EXIF must not fall back") },
            orientationCheck = { orientationChecks++ },
            experimentalPage = DbnetExperimentalPageRecognizer { _, _, _, _ ->
                experimentalCalls++
                experimentalPage
            },
        )

        assertSame(experimentalPage, engine.recognize(image()))
        assertEquals(1, orientationChecks)
        assertEquals(1, experimentalCalls)
        assertEquals(0, selected.recognizeCalls)
    }

    @Test fun `invalid EXIF orientation falls back before experimental work`() = runBlocking {
        assertPreparationFailureFallsBack(IllegalArgumentException("unsupported EXIF orientation"))
    }

    @Test fun `EXIF read failure falls back before experimental work`() = runBlocking {
        assertPreparationFailureFallsBack(IOException("unreadable image metadata"))
    }

    @Test fun `EXIF read cancellation propagates without selected fallback`() {
        val events = mutableListOf<String>()
        val page = OcrPage(100, 200, emptyList())
        val selected = RecordingEngine(OcrEngineType.PADDLE_OCR, page, events, "selected")
        var experimentalCalls = 0

        val engine = engine(
            selected = selected,
            clientFactory = { error("EXIF cancellation must precede client creation") },
            ownedMlKit = RecordingEngine(OcrEngineType.ML_KIT, page, events, "owned"),
            notify = { error("EXIF cancellation must not notify fallback") },
            orientationCheck = { throw CancellationException("cancel metadata read") },
            experimentalPage = DbnetExperimentalPageRecognizer { _, _, _, _ ->
                experimentalCalls++
                error("EXIF cancellation must precede experimental page recognition")
            },
        )

        assertThrows(CancellationException::class.java) { runBlocking { engine.recognize(image()) } }
        assertEquals(0, experimentalCalls)
        assertEquals(0, selected.recognizeCalls)
    }

    @Test fun `technical failure closes and disables concrete wrapper then releases owned ML Kit after one fallback`() =
        runBlocking {
            val events = mutableListOf<String>()
            val selectedPage = OcrPage(100, 200, emptyList())
            val selected = RecordingEngine(OcrEngineType.PADDLE_OCR, selectedPage, events, "selected")
            val ownedMlKit = RecordingEngine(OcrEngineType.ML_KIT, selectedPage, events, "owned")
            val client = RecordingClient(events)
            var clientCreations = 0
            var experimentalCalls = 0
            var notifications = 0
            val image = image()
            val engine = engine(
                selected = selected,
                clientFactory = {
                    clientCreations++
                    client
                },
                ownedMlKit = ownedMlKit,
                notify = { notifications++ },
                experimentalPage = DbnetExperimentalPageRecognizer { input, attempt, _, acquireClient ->
                    assertSame(client, acquireClient())
                    experimentalCalls++
                    events += "experimental"
                    attempt.recognizeForAssociation(input)
                    throw IllegalStateException("native")
                },
            )

            assertSame(selectedPage, engine.recognize(image))
            assertEquals(1, experimentalCalls)
            assertEquals(1, clientCreations)
            assertEquals(1, client.closeCalls)
            assertEquals(1, selected.recognizeCalls)
            assertEquals(1, ownedMlKit.recognizeCalls)
            assertEquals(1, ownedMlKit.releaseCalls)
            assertEquals(1, notifications)
            assertEquals(
                listOf("experimental", "owned-recognize", "client-close", "selected-recognize", "owned-release"),
                events,
            )

            assertSame(selectedPage, engine.recognize(image))
            assertEquals(1, experimentalCalls, "technical failure must disable later DBNet attempts")
            assertEquals(1, clientCreations)
            assertEquals(2, selected.recognizeCalls)
        }

    @Test fun `cancellation leaves concrete wrapper client enabled without fallback or failure cleanup`() {
        val events = mutableListOf<String>()
        val page = OcrPage(100, 200, emptyList())
        val selected = RecordingEngine(OcrEngineType.PADDLE_OCR, page, events, "selected")
        val ownedMlKit = RecordingEngine(OcrEngineType.ML_KIT, page, events, "owned")
        val client = RecordingClient(events)
        var notifications = 0
        var experimentalCalls = 0
        val engine = engine(
            selected = selected,
            clientFactory = { client },
            ownedMlKit = ownedMlKit,
            notify = { notifications++ },
            experimentalPage = DbnetExperimentalPageRecognizer { input, attempt, _, acquireClient ->
                assertSame(client, acquireClient())
                experimentalCalls++
                attempt.recognizeForAssociation(input)
                throw CancellationException("cancel")
            },
        )

        assertThrows(CancellationException::class.java) { runBlocking { engine.recognize(image()) } }
        assertEquals(1, experimentalCalls)
        assertEquals(0, client.closeCalls)
        assertEquals(0, selected.recognizeCalls)
        assertEquals(1, ownedMlKit.recognizeCalls)
        assertEquals(0, ownedMlKit.releaseCalls)
        assertEquals(0, notifications)

        assertThrows(CancellationException::class.java) { runBlocking { engine.recognize(image()) } }
        assertEquals(2, experimentalCalls, "cancellation must not disable the DBNet route")
        assertEquals(2, ownedMlKit.recognizeCalls)
        assertEquals(0, ownedMlKit.releaseCalls)
    }

    private fun engine(
        selected: OcrEngine,
        clientFactory: (Context) -> DbnetDetectionClient,
        ownedMlKit: OcrEngine,
        notify: (String) -> Unit,
        orientationCheck: (OcrImage) -> Unit = {},
        experimentalPage: DbnetExperimentalPageRecognizer,
    ): ExperimentalDbnetOcrEngine {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        return ExperimentalDbnetOcrEngine(
            context = context,
            existing = selected,
            enabled = { true },
            deviceSupported = { true },
            createClient = clientFactory,
            createOwnedMlKit = { ownedMlKit },
            notify = notify,
            emitDiagnostic = {},
            validateExifOrientation = orientationCheck,
            experimentalPage = experimentalPage,
        )
    }

    private suspend fun assertPreparationFailureFallsBack(failure: Throwable) {
        val events = mutableListOf<String>()
        val selectedPage = OcrPage(100, 200, emptyList())
        val selected = RecordingEngine(OcrEngineType.PADDLE_OCR, selectedPage, events, "selected")
        var clientCreations = 0
        var experimentalCalls = 0
        var notifications = 0
        val engine = engine(
            selected = selected,
            clientFactory = {
                clientCreations++
                error("unsafe EXIF must precede client creation")
            },
            ownedMlKit = RecordingEngine(OcrEngineType.ML_KIT, selectedPage, events, "owned"),
            notify = { notifications++ },
            orientationCheck = { throw failure },
            experimentalPage = DbnetExperimentalPageRecognizer { _, _, _, _ ->
                experimentalCalls++
                error("unsafe EXIF must precede experimental page recognition")
            },
        )

        assertSame(selectedPage, engine.recognize(image()))
        assertEquals(0, clientCreations)
        assertEquals(0, experimentalCalls)
        assertEquals(1, selected.recognizeCalls)
        assertEquals(1, notifications)
    }

    private fun image() = OcrImage(mockk<Uri>(), { error("input stream must stay lazy") })

    private class RecordingClient(
        private val events: MutableList<String>,
    ) : DbnetDetectionClient {
        var closeCalls = 0

        override suspend fun detect(file: File): DetectionResult = error("test page recognizer owns the failure")

        override fun close() {
            closeCalls++
            events += "client-close"
        }
    }

    private class RecordingEngine(
        override val type: OcrEngineType,
        private val page: OcrPage,
        private val events: MutableList<String>,
        private val name: String,
    ) : OcrEngine {
        override val language = TextRecognizerLanguage.ENGLISH
        var recognizeCalls = 0
        var releaseCalls = 0

        override suspend fun recognize(image: OcrImage): OcrPage {
            recognizeCalls++
            events += "$name-recognize"
            return page
        }

        override suspend fun release() {
            releaseCalls++
            events += "$name-release"
        }
    }
}
