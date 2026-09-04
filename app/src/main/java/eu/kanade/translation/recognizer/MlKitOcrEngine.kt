package eu.kanade.translation.recognizer

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class MlKitOcrEngine(
    context: Context,
    override val language: TextRecognizerLanguage,
) : OcrEngine {
    override val type = OcrEngineType.ML_KIT

    private val appContext = context.applicationContext
    private val initializationStart = System.nanoTime()
    private val recognizer = TextRecognition.getClient(
        when (language) {
            TextRecognizerLanguage.ENGLISH -> TextRecognizerOptions.DEFAULT_OPTIONS
            TextRecognizerLanguage.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
            TextRecognizerLanguage.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
            TextRecognizerLanguage.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
        },
    )
    private val initializationTimeMs = elapsedMs(initializationStart)
    private var initializationReported = false

    override suspend fun recognize(image: OcrImage): OcrPage = withContext(Dispatchers.IO) {
        val pageStart = System.nanoTime()
        try {
            val inputImage = InputImage.fromFilePath(appContext, image.uri)
            val ocrStart = System.nanoTime()
            val result = Tasks.await(recognizer.process(inputImage))
            val ocrTimeMs = elapsedMs(ocrStart)
            val blocks = result.textBlocks.mapNotNull { block ->
                val bounds = block.boundingBox ?: return@mapNotNull null
                val firstSymbolBounds = block.lines
                    .firstOrNull()
                    ?.elements
                    ?.firstOrNull()
                    ?.symbols
                    ?.firstOrNull()
                    ?.boundingBox

                OcrTextBlock(
                    text = block.text,
                    x = bounds.left.toFloat(),
                    y = bounds.top.toFloat(),
                    width = bounds.width().toFloat(),
                    height = bounds.height().toFloat(),
                    symbolWidth = firstSymbolBounds?.width()?.toFloat() ?: 1f,
                    symbolHeight = firstSymbolBounds?.height()?.toFloat() ?: 1f,
                    angle = block.lines.firstOrNull()?.angle ?: 0f,
                )
            }
            val reportedInitializationMs = if (initializationReported) 0L else initializationTimeMs
            initializationReported = true
            val totalTimeMs = elapsedMs(pageStart)
            logcat(LogPriority.INFO) {
                "OCR page engine=ML_KIT initMs=$reportedInitializationMs ocrMs=$ocrTimeMs " +
                    "totalMs=$totalTimeMs blocks=${blocks.size}"
            }
            OcrPage(
                width = inputImage.width,
                height = inputImage.height,
                blocks = blocks,
                metrics = OcrPerformanceMetrics(
                    initializationTimeMs = reportedInitializationMs,
                    ocrTimeMs = ocrTimeMs,
                    totalTimeMs = totalTimeMs,
                ),
            )
        } catch (error: Throwable) {
            logcat(LogPriority.ERROR, error) {
                "OCR failure engine=ML_KIT totalMs=${elapsedMs(pageStart)} error=${error::class.simpleName}"
            }
            throw error
        }
    }

    override suspend fun release() {
        recognizer.close()
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
