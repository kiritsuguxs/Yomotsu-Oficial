package eu.kanade.translation.model

import eu.kanade.translation.detection.DbnetCleanupMask
import kotlinx.serialization.Serializable

@Serializable
data class PageTranslation(
    var blocks: MutableList<TranslationBlock> = mutableListOf(),
    var imgWidth: Float = 0f,
    var imgHeight: Float = 0f,
) {
    companion object {
        val EMPTY = PageTranslation()
    }
}

@Serializable
data class TranslationBlock(
    var text: String,
    var translation: String = "",
    var width: Float,
    var height: Float,
    var x: Float,
    var y: Float,
    var symHeight: Float,
    var symWidth: Float,
    val angle: Float,
    var cleanupRegion: TranslationRegion? = null,
    var layoutRegion: TranslationRegion? = null,
    var backgroundColor: Int? = null,
    var foregroundColor: Int? = null,
    var balloonDetected: Boolean = false,
    var geometryVersion: Int = 1,
    // Preserve the OCR footprints when several fragments become one paragraph.
    // Empty in older files and in blocks which have never been grouped.
    val sourceRegions: List<TranslationRegion> = emptyList(),
    // Safe erasers measured for those footprints before grouping/reanalysis.
    val sourceCleanupRegions: List<TranslationRegion> = emptyList(),
    val dbnetCleanupMask: DbnetCleanupMask? = null,
)

/**
 * A page-space rectangle stored alongside OCR data.
 *
 * [TranslationBlock.cleanupRegion] is intentionally separate from
 * [TranslationBlock.layoutRegion]: erasing should remain close to the original
 * glyphs, while translated text can use the larger free area of the balloon.
 */
@Serializable
data class TranslationRegion(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
