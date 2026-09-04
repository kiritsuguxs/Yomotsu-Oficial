package eu.kanade.translation.model

import kotlin.math.max

const val CURRENT_TRANSLATION_GEOMETRY_VERSION = 2

/**
 * Conservative fallback used by old translation files and whenever a balloon
 * cannot be detected from the page pixels.
 */
fun TranslationBlock.defaultCleanupRegion(
    pageWidth: Float = Float.MAX_VALUE,
    pageHeight: Float = Float.MAX_VALUE,
): TranslationRegion {
    val horizontalPadding = max(symWidth * 3.2f, width * 0.16f)
    val verticalPadding = max(symHeight * 2.0f, height * 0.16f)
    return sourceRegion().expanded(horizontalPadding, verticalPadding, pageWidth, pageHeight)
}

/**
 * Gives the translated paragraph more of the balloon than the OCR glyph box.
 * The detected region replaces this fallback for newly translated chapters.
 */
fun TranslationBlock.defaultLayoutRegion(
    pageWidth: Float = Float.MAX_VALUE,
    pageHeight: Float = Float.MAX_VALUE,
): TranslationRegion {
    val horizontalPadding = max(symWidth * 3.6f, width * 0.18f)
    val verticalPadding = max(symHeight * 2.4f, height * 0.20f)
    return sourceRegion().expanded(horizontalPadding, verticalPadding, pageWidth, pageHeight)
}

/**
 * Geometry saved by the first Y9 build was too generous. Falling back to the
 * OCR bounds upgrades those existing chapter files without deleting them.
 */
fun TranslationBlock.resolvedLayoutRegion(pageWidth: Float, pageHeight: Float): TranslationRegion =
    layoutRegion
        ?.takeIf { geometryVersion >= CURRENT_TRANSLATION_GEOMETRY_VERSION }
        ?.clamped(pageWidth, pageHeight)
        ?: defaultLayoutRegion(pageWidth, pageHeight)

fun TranslationBlock.sourceRegion(): TranslationRegion = TranslationRegion(
    x = x,
    y = y,
    width = width.coerceAtLeast(1f),
    height = height.coerceAtLeast(1f),
)

fun TranslationRegion.expanded(
    horizontalPadding: Float,
    verticalPadding: Float,
    pageWidth: Float,
    pageHeight: Float,
): TranslationRegion {
    val safePageWidth = pageWidth.takeIf { it.isFinite() && it > 0f } ?: Float.MAX_VALUE
    val safePageHeight = pageHeight.takeIf { it.isFinite() && it > 0f } ?: Float.MAX_VALUE
    val left = (x - horizontalPadding / 2f).coerceAtLeast(0f)
    val top = (y - verticalPadding / 2f).coerceAtLeast(0f)
    val right = (x + width + horizontalPadding / 2f).coerceAtMost(safePageWidth)
    val bottom = (y + height + verticalPadding / 2f).coerceAtMost(safePageHeight)
    return TranslationRegion(
        x = left,
        y = top,
        width = (right - left).coerceAtLeast(1f),
        height = (bottom - top).coerceAtLeast(1f),
    )
}

fun TranslationRegion.clamped(pageWidth: Float, pageHeight: Float): TranslationRegion {
    val safePageWidth = pageWidth.takeIf { it.isFinite() && it >= 1f } ?: 1f
    val safePageHeight = pageHeight.takeIf { it.isFinite() && it >= 1f } ?: 1f
    val safeX = x.takeIf(Float::isFinite) ?: 0f
    val safeY = y.takeIf(Float::isFinite) ?: 0f
    val safeWidth = width.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val safeHeight = height.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f

    val left = safeX.coerceIn(0f, safePageWidth - 1f)
    val top = safeY.coerceIn(0f, safePageHeight - 1f)
    val rawRight = (safeX + safeWidth).takeIf(Float::isFinite) ?: safePageWidth
    val rawBottom = (safeY + safeHeight).takeIf(Float::isFinite) ?: safePageHeight
    val right = rawRight.coerceIn(left + 1f, safePageWidth)
    val bottom = rawBottom.coerceIn(top + 1f, safePageHeight)
    return copy(
        x = left,
        y = top,
        width = right - left,
        height = bottom - top,
    )
}

fun TranslationRegion.inset(horizontalInset: Float, verticalInset: Float): TranslationRegion {
    val safeHorizontalInset = horizontalInset
        .coerceAtLeast(0f)
        .coerceAtMost(((width - 1f) / 2f).coerceAtLeast(0f))
    val safeVerticalInset = verticalInset
        .coerceAtLeast(0f)
        .coerceAtMost(((height - 1f) / 2f).coerceAtLeast(0f))
    return TranslationRegion(
        x = x + safeHorizontalInset,
        y = y + safeVerticalInset,
        width = (width - safeHorizontalInset * 2f).coerceAtLeast(1f),
        height = (height - safeVerticalInset * 2f).coerceAtLeast(1f),
    )
}
