package eu.kanade.translation.recognizer

import kotlin.math.atan2
import kotlin.math.hypot

object PaddleTextBlockMapper {

    fun map(
        text: String,
        confidence: Float,
        points: List<OcrPoint>,
    ): OcrTextBlock? {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty() || points.size < REQUIRED_POINT_COUNT) return null
        if (!confidence.isFinite() || confidence < MIN_CONFIDENCE) return null

        val quad = points.take(REQUIRED_POINT_COUNT)
        if (quad.any { !it.x.isFinite() || !it.y.isFinite() }) return null

        val minX = quad.minOf(OcrPoint::x)
        val minY = quad.minOf(OcrPoint::y)
        val maxX = quad.maxOf(OcrPoint::x)
        val maxY = quad.maxOf(OcrPoint::y)
        val width = maxX - minX
        val height = maxY - minY
        if (width <= 0f || height <= 0f) return null

        val characterCount = normalizedText.count { !it.isWhitespace() }.coerceAtLeast(1)
        val boxArea = width * height
        val areaPerCharacter = boxArea / characterCount
        if (areaPerCharacter > MAX_AREA_PER_CHARACTER && characterCount <= SHORT_TEXT_LIMIT) return null

        val topLeft = quad[0]
        val topRight = quad[1]
        val bottomRight = quad[2]
        val bottomLeft = quad[3]
        val topEdge = distance(topLeft, topRight)
        val bottomEdge = distance(bottomLeft, bottomRight)
        val leftEdge = distance(topLeft, bottomLeft)
        val rightEdge = distance(topRight, bottomRight)
        val lineWidth = (topEdge + bottomEdge) / 2f
        val lineHeight = (leftEdge + rightEdge) / 2f

        return OcrTextBlock(
            text = normalizedText,
            x = minX,
            y = minY,
            width = width,
            height = height,
            symbolWidth = (lineWidth / characterCount).coerceAtLeast(1f),
            symbolHeight = lineHeight.coerceAtLeast(1f),
            angle = Math.toDegrees(
                atan2(
                    (topRight.y - topLeft.y).toDouble(),
                    (topRight.x - topLeft.x).toDouble(),
                ),
            ).toFloat(),
            confidence = confidence,
        )
    }

    private fun distance(first: OcrPoint, second: OcrPoint): Float =
        hypot(second.x - first.x, second.y - first.y)

    private const val REQUIRED_POINT_COUNT = 4
    private const val MIN_CONFIDENCE = 0.50f
    private const val SHORT_TEXT_LIMIT = 12
    private const val MAX_AREA_PER_CHARACTER = 20_000f
}
