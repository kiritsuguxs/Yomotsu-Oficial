package eu.kanade.translation.recognizer

interface OcrEngine {
    val type: OcrEngineType
    val language: TextRecognizerLanguage

    suspend fun recognize(image: OcrImage): OcrPage

    suspend fun release()
}
