package eu.kanade.translation.recognizer

import android.content.Context

object OcrEngineFactory {

    fun create(
        context: Context,
        type: OcrEngineType,
        language: TextRecognizerLanguage,
    ): OcrEngine {
        require(type.supports(language)) { "PaddleOCR requires English source text" }
        return when (type) {
            OcrEngineType.ML_KIT -> MlKitOcrEngine(context, language)
            OcrEngineType.PADDLE_OCR -> PaddleOcrEngine(context, language)
        }
    }
}
