package eu.kanade.translation.recognizer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OcrEngineManager(
    private val factory: (OcrEngineType, TextRecognizerLanguage) -> OcrEngine,
) {
    private val mutex = Mutex()
    private var engine: OcrEngine? = null

    suspend fun <T> withEngine(
        type: OcrEngineType,
        language: TextRecognizerLanguage,
        block: suspend (OcrEngine) -> T,
    ): T = mutex.withLock {
        val currentEngine = engine
        val selectedEngine = if (currentEngine?.type == type && currentEngine.language == language) {
            currentEngine
        } else {
            engine = null
            currentEngine?.release()
            factory(type, language).also { engine = it }
        }
        block(selectedEngine)
    }

    suspend fun release() {
        mutex.withLock {
            val currentEngine = engine
            engine = null
            currentEngine?.release()
        }
    }
}
