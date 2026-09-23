package eu.kanade.translation.model

import kotlin.math.max

const val CURRENT_TRANSLATION_GEOMETRY_VERSION = 5

/** Directional coverage; invalid or empty rectangles carry no geometric evidence. */
fun TranslationRegion.overlapFraction(other: TranslationRegion): Float {
    val area = validArea()
    if (area == 0.0 || other.validArea() == 0.0) return 0f
    return (intersectionArea(other) / area).toFloat().coerceIn(0f, 1f)
}

fun TranslationRegion.intersectionOverUnion(other: TranslationRegion): Float {
    val first = validArea()
    val second = other.validArea()
    if (first == 0.0 || second == 0.0) return 0f
    val intersection = intersectionArea(other)
    return (intersection / (first + second - intersection)).toFloat().coerceIn(0f, 1f)
}

private fun TranslationRegion.validArea(): Double =
    if (x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        width.toDouble() * height
    } else {
        0.0
    }

private fun TranslationRegion.intersectionArea(other: TranslationRegion): Double =
    (minOf(x.toDouble() + width, other.x.toDouble() + other.width) - maxOf(x, other.x)).coerceAtLeast(0.0) *
        (minOf(y.toDouble() + height, other.y.toDouble() + other.height) - maxOf(y, other.y)).coerceAtLeast(0.0)

/**
 * Conservative fallback used by old translation files and whenever a balloon
 * cannot be detected from the page pixels.
 */
fun TranslationBlock.defaultCleanupRegion(
    pageWidth: Float = Float.MAX_VALUE,
    pageHeight: Float = Float.MAX_VALUE,
): TranslationRegion {
    // Meio-termo seguro: 20% (antes era 16%). Limpa os resquícios sem morder a borda do balão pequeno.
    val horizontalPadding = max(symWidth * 3.6f, width * 0.20f)
    val verticalPadding = max(symHeight * 2.4f, height * 0.20f)
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
    val w = width.coerceAtLeast(1f)
    val h = height.coerceAtLeast(1f)
    val cx = x + w / 2f
    val cy = y + h / 2f

    // Text inside manga bubbles looks best in a square or 1.2:1 ratio.
    // PaddleOCR often returns long/thin lines that distort the layout.
    // We reshape the region towards a square based on the text area.
    val area = w * h
    val targetArea = area * 1.8f // Portuguese is longer
    val targetSide = kotlin.math.sqrt(targetArea)

    // Blend the original shape with the ideal square shape.
    var blendedWidth = w * 0.4f + targetSide * 0.6f
    var blendedHeight = h * 0.4f + targetSide * 0.6f

    // ENFORCE ASPECT RATIO LIMITS!
    // Text in a comic bubble should never be an extreme rectangle.
    // A typesetter almost always formats text to be somewhat square.
    if (blendedWidth > blendedHeight * 1.4f) {
        blendedWidth = blendedHeight * 1.4f
    }
    if (blendedHeight > blendedWidth * 1.4f) {
        blendedHeight = blendedWidth * 1.4f
    }

    // Add sensible minimal padding so small words don't get choked
    val minPadX = symWidth * 1.5f
    val minPadY = symHeight * 1.5f

    val finalWidth = maxOf(blendedWidth, minPadX * 3f)
    val finalHeight = maxOf(blendedHeight, minPadY * 3f)

    return TranslationRegion(
        x = cx - finalWidth / 2f,
        y = cy - finalHeight / 2f,
        width = finalWidth,
        height = finalHeight
    ).clamped(pageWidth, pageHeight)
}

/**
 * Geometry saved by the first Y9 build was too generous. Falling back to the
 * OCR bounds upgrades those existing chapter files without deleting them.
 */
fun TranslationBlock.resolvedLayoutRegion(pageWidth: Float, pageHeight: Float): TranslationRegion {
    val defaultReg = defaultLayoutRegion(pageWidth, pageHeight)
    val balloonReg = layoutRegion?.takeIf { geometryVersion >= CURRENT_TRANSLATION_GEOMETRY_VERSION }
    
    if (balloonReg != null && balloonDetected) {
        // The detected balloon provides the perfect visual center.
        // However, compound balloons (intersecting circles) cause flood-fill to create a massive bounding box,
        // which forces the text to stretch into a thin, unreadable horizontal strip.
        // To fix this, we use the balloon's center, but constrain the size to our aspect-ratio-corrected default layout.
        // Wait! If the balloon is a compound figure-8, the balloon's center is the empty intersection!
        // The original text's center (defaultReg) is MUCH safer and perfectly placed in the correct lobe.
        // So we ONLY use the balloon's layout if it's NOT massively larger than our default text region!
        val areaRatio = balloonReg.width * balloonReg.height / (defaultReg.width * defaultReg.height).coerceAtLeast(1f)
        if (areaRatio < 4.0f) {
            // Balloon is reasonably sized, use its center but constrain its width so text doesn't stretch.
            val cx = balloonReg.x + balloonReg.width / 2f
            val cy = balloonReg.y + balloonReg.height / 2f
            val finalWidth = minOf(balloonReg.width, defaultReg.width * 1.25f)
            val finalHeight = minOf(balloonReg.height, defaultReg.height * 1.25f)
            return TranslationRegion(
                x = cx - finalWidth / 2f,
                y = cy - finalHeight / 2f,
                width = finalWidth,
                height = finalHeight
            ).clamped(pageWidth, pageHeight)
        }
    }
    
    // If balloon detection failed, OR the balloon is a massive compound bubble (areaRatio >= 4),
    // rely entirely on the perfectly aspect-ratio-corrected OCR bounds.
    return defaultReg
}

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
