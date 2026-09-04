package eu.kanade.translation.model

import eu.kanade.translation.detection.DbnetCleanupMask
import kotlin.math.sqrt

data class TranslationCleanupPatch(val region: TranslationRegion, val cornerRadius: Float)

sealed interface ResolvedTranslationCleanup
data class LegacyCleanup(val patches: List<TranslationCleanupPatch>) : ResolvedTranslationCleanup
data class DbnetMaskCleanup(val mask: DbnetCleanupMask) : ResolvedTranslationCleanup
data object NoCleanup : ResolvedTranslationCleanup

fun TranslationBlock.resolveCleanup(
    pageWidth: Float,
    pageHeight: Float,
    experimentalPageValid: Boolean = true,
): ResolvedTranslationCleanup {
    val mask = dbnetCleanupMask ?: return LegacyCleanup(resolvedCleanupPatches(pageWidth, pageHeight))
    if (translation.isBlank() || !experimentalPageValid) return NoCleanup
    val width = DbnetCleanupMask.exactDimension(pageWidth) ?: return NoCleanup
    val height = DbnetCleanupMask.exactDimension(pageHeight) ?: return NoCleanup
    return if (mask.forEachRun(width, height) { _, _, _ -> }) DbnetMaskCleanup(mask) else NoCleanup
}

fun TranslationBlock.resolvedCleanupPatches(pageWidth: Float, pageHeight: Float): List<TranslationCleanupPatch> {
    val sources = sourceRegions.ifEmpty { listOf(sourceRegion()) }
    return sources.mapIndexed { index, source ->
        val measured = sourceCleanupRegions.takeIf { it.size == sources.size }?.get(index)
        val region = (measured ?: if (sourceRegions.isEmpty()) {
            cleanupRegion ?: defaultCleanupRegion(pageWidth, pageHeight)
        } else {
            // Re-analysis may replace the paragraph's enclosing cleanupRegion.
            // Erase each original footprint, never the blank space between them.
            copy(x = source.x, y = source.y, width = source.width, height = source.height)
                .defaultCleanupRegion(pageWidth, pageHeight)
        }).clamped(pageWidth, pageHeight)
        val horizontalPadding = minOf(source.x - region.x, region.x + region.width - source.x - source.width).coerceAtLeast(0f)
        val verticalPadding = minOf(source.y - region.y, region.y + region.height - source.y - source.height).coerceAtLeast(0f)
        // Largest round-corner radius whose arc still covers every source corner.
        val coveringRadius = horizontalPadding + verticalPadding + sqrt(2f * horizontalPadding * verticalPadding)
        TranslationCleanupPatch(region, minOf(minOf(region.width, region.height) * 0.32f, coveringRadius))
    }
}
