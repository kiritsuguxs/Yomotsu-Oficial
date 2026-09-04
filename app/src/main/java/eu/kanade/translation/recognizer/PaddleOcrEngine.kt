package eu.kanade.translation.recognizer

import android.content.Context
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class PaddleOcrEngine(
    context: Context,
    override val language: TextRecognizerLanguage,
) : OcrEngine {
    override val type = OcrEngineType.PADDLE_OCR

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var paddleOcr: PaddleOCR? = null

    init {
        require(type.supports(language)) { "PaddleOCR requires English source text" }
    }

    override suspend fun recognize(image: OcrImage): OcrPage = mutex.withLock {
        val pageStart = System.nanoTime()
        var initializationTimeMs = 0L
        try {
            val imageBytes = image.openInputStream().use { it.readBytes() }
            val engine = paddleOcr ?: run {
                val initializationStart = System.nanoTime()
                PaddleOcrRuntime.requireOpenCv { OpenCVUtils.init(appContext) }
                PaddleOCR.create(appContext).also {
                    paddleOcr = it
                    initializationTimeMs = elapsedMs(initializationStart)
                }
            }
            val result = engine.recognize(imageBytes)
            require(result.imageWidth > 0 && result.imageHeight > 0) { "Unable to decode OCR image dimensions" }
            val blocks = result.results.mapNotNull { block ->
                PaddleTextBlockMapper.map(
                    text = block.text,
                    confidence = block.confidence,
                    points = block.box.points.map { point -> OcrPoint(point.x, point.y) },
                )
            }
            val totalTimeMs = elapsedMs(pageStart)
            logcat(LogPriority.INFO) {
                "OCR page engine=PADDLE_OCR initMs=$initializationTimeMs ocrMs=${result.totalTimeMs} " +
                    "totalMs=$totalTimeMs blocks=${blocks.size} detMs=${result.detectionTimeMs} " +
                    "recMs=${result.recognitionTimeMs} detShape=${result.detInputShape.joinToString("x")}"
            }
            OcrPage(
                width = result.imageWidth,
                height = result.imageHeight,
                blocks = blocks,
                metrics = OcrPerformanceMetrics(
                    initializationTimeMs = initializationTimeMs,
                    ocrTimeMs = result.totalTimeMs,
                    totalTimeMs = totalTimeMs,
                    detectionTimeMs = result.detectionTimeMs,
                    recognitionTimeMs = result.recognitionTimeMs,
                ),
            )
        } catch (error: Throwable) {
            logcat(LogPriority.ERROR, error) {
                "OCR failure engine=PADDLE_OCR totalMs=${elapsedMs(pageStart)} error=${error::class.simpleName}"
            }
            throw error
        }
    }

    override suspend fun release() {
        mutex.withLock {
            try {
                paddleOcr?.release()
            } finally {
                paddleOcr = null
            }
        }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

internal object PaddleOcrRuntime {
    fun requireOpenCv(initializer: () -> Boolean) {
        check(initializer()) { "Unable to initialize OpenCV for PaddleOCR" }
    }
}
