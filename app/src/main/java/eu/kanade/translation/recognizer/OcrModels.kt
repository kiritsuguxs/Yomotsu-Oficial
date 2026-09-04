package eu.kanade.translation.recognizer

import android.net.Uri
import eu.kanade.translation.detection.DbnetAssociationMetadata
import eu.kanade.translation.detection.DbnetCleanupMask
import java.io.InputStream

data class OcrPoint(
    val x: Float,
    val y: Float,
)

data class OcrTextBlock(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val symbolWidth: Float,
    val symbolHeight: Float,
    val angle: Float,
    val confidence: Float? = null,
    val dbnetCleanupMask: DbnetCleanupMask? = null,
)

data class OcrPage(
    val width: Int,
    val height: Int,
    val blocks: List<OcrTextBlock>,
    val metrics: OcrPerformanceMetrics? = null,
    val dbnetAssociation: DbnetAssociationMetadata? = null,
)

data class OcrPerformanceMetrics(
    val initializationTimeMs: Long,
    val ocrTimeMs: Long,
    val totalTimeMs: Long,
    val detectionTimeMs: Long? = null,
    val recognitionTimeMs: Long? = null,
)

class OcrImage(
    val uri: Uri,
    val openInputStream: () -> InputStream,
)
