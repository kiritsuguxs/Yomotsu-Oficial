package eu.kanade.translation.recognizer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OcrEngineManagerTest {

    @Test
    fun `reuses a matching engine and releases it once`() = runTest {
        val created = mutableListOf<RecordingOcrEngine>()
        val manager = OcrEngineManager { type, language ->
            RecordingOcrEngine(type, language).also(created::add)
        }

        manager.withEngine(OcrEngineType.ML_KIT, TextRecognizerLanguage.ENGLISH) { }
        manager.withEngine(OcrEngineType.ML_KIT, TextRecognizerLanguage.ENGLISH) { }
        manager.release()
        manager.release()

        assertEquals(1, created.size)
        assertEquals(1, created.single().releaseCount)
    }

    @Test
    fun `replacement waits until the active engine use completes`() = runTest {
        val created = mutableListOf<RecordingOcrEngine>()
        val manager = OcrEngineManager { type, language ->
            RecordingOcrEngine(type, language).also(created::add)
        }
        val firstUseStarted = CompletableDeferred<Unit>()
        val finishFirstUse = CompletableDeferred<Unit>()

        val firstUse = launch {
            manager.withEngine(OcrEngineType.ML_KIT, TextRecognizerLanguage.ENGLISH) {
                firstUseStarted.complete(Unit)
                finishFirstUse.await()
            }
        }
        firstUseStarted.await()
        val replacement = async {
            manager.withEngine(OcrEngineType.PADDLE_OCR, TextRecognizerLanguage.ENGLISH) { it.type }
        }
        runCurrent()

        assertEquals(1, created.size)
        assertFalse(created.single().released)

        finishFirstUse.complete(Unit)
        firstUse.join()
        assertEquals(OcrEngineType.PADDLE_OCR, replacement.await())
        assertEquals(2, created.size)
        assertTrue(created.first().released)
    }
}

private class RecordingOcrEngine(
    override val type: OcrEngineType,
    override val language: TextRecognizerLanguage,
) : OcrEngine {
    var releaseCount = 0
        private set

    val released: Boolean
        get() = releaseCount > 0

    override suspend fun recognize(image: OcrImage): OcrPage = error("Not used by this test")

    override suspend fun release() {
        releaseCount++
    }
}
