package eu.kanade.translation.model

import kotlin.math.abs

/** Correct a first-symbol outlier only at the Bubble boundary, leaving OCR untouched. */
internal fun TranslationBlock.withReliableSourceMetrics(): TranslationBlock {
    // A grouped bounding box includes inter-fragment gaps, not one tall line.
    if (sourceRegions.isNotEmpty() || abs(angle) > 12f || !width.isFinite() || !height.isFinite()) return this
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.isEmpty() || width <= 0f || height <= 0f || (lines.size == 1 && height > width)) return this
    val lineHeight = height / lines.size
    if (symHeight.isFinite() && symHeight >= lineHeight * 0.5f) return this

    // Multi-line bounds include leading. Keep a conservative glyph estimate;
    // a single horizontal line already provides its actual glyph height.
    val glyphHeight = lineHeight * if (lines.size == 1) 1f else 0.8f
    val longestLine = lines.maxOf { line -> line.count { !it.isWhitespace() } }.coerceAtLeast(1)
    return copy(symHeight = glyphHeight.coerceAtLeast(1f), symWidth = (width / longestLine).coerceAtLeast(1f))
}
