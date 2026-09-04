// SPDX-License-Identifier: GPL-3.0-only
// Adapted 2026-08-31 from Houri/houri-engine (Yakuyomi), revision
// 85351aa3822fe2611f68cfd092972e6ac573f203, Geometry.kt and ImageOps.kt.
// Upstream detector preprocessing credits manga-image-translator @ d5a3eee.
// Changes: Android-free types, bounded resize plan, strict degenerate rejection;
// only detector primitives retained. See docs/yakuyomi-dbnet-upstream.md.
package eu.kanade.translation.detection

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class DbnetResizePlan private constructor(
    val originalWidth: Int,
    val originalHeight: Int,
    val resizedWidth: Int,
    val resizedHeight: Int,
    val inputWidth: Int,
    val inputHeight: Int,
    val ratio: Float,
) {
    companion object {
        const val MAX_INPUT_SIDE = 1024
        const val MAX_INPUT_AREA = MAX_INPUT_SIDE * MAX_INPUT_SIDE
        const val MAX_ORIGINAL_SIDE = 100_000
        const val MAX_ORIGINAL_AREA = 100_000_000L

        /** Uses the upstream nominal ratio even when the resized short edge rounds. */
        fun create(width: Int, height: Int): DbnetResizePlan {
            require(width in 1..MAX_ORIGINAL_SIDE && height in 1..MAX_ORIGINAL_SIDE) {
                "Image dimensions must be positive and at most $MAX_ORIGINAL_SIDE"
            }
            require(width.toLong() * height <= MAX_ORIGINAL_AREA) { "Image pixel count exceeds $MAX_ORIGINAL_AREA" }
            val ratio = MAX_INPUT_SIDE.toFloat() / max(width, height)
            val resizedWidth = (width * ratio).roundToInt().coerceAtLeast(1)
            val resizedHeight = (height * ratio).roundToInt().coerceAtLeast(1)
            val inputWidth = (resizedWidth + 255) / 256 * 256
            val inputHeight = (resizedHeight + 255) / 256 * 256
            return DbnetResizePlan(width, height, resizedWidth, resizedHeight, inputWidth, inputHeight, ratio)
        }
    }
}

internal class DbnetRotatedRect(
    val centerX: Float,
    val centerY: Float,
    val axisX: Float,
    val axisY: Float,
    val width: Float,
    val height: Float,
) {
    fun corners(): List<DetectionPoint> {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        return listOf(
            DetectionPoint(
                centerX - halfWidth * axisX + halfHeight * axisY,
                centerY - halfWidth * axisY - halfHeight * axisX,
            ),
            DetectionPoint(
                centerX + halfWidth * axisX + halfHeight * axisY,
                centerY + halfWidth * axisY - halfHeight * axisX,
            ),
            DetectionPoint(
                centerX + halfWidth * axisX - halfHeight * axisY,
                centerY + halfWidth * axisY + halfHeight * axisX,
            ),
            DetectionPoint(
                centerX - halfWidth * axisX - halfHeight * axisY,
                centerY - halfWidth * axisY + halfHeight * axisX,
            ),
        )
    }

    /** Rectangular DB unclip: move each edge by area * ratio / perimeter. */
    fun unclip(ratio: Float): DbnetRotatedRect {
        val perimeter = 2f * (width + height)
        val offset = if (perimeter > 1e-6f) width * height * ratio / perimeter else 0f
        return DbnetRotatedRect(centerX, centerY, axisX, axisY, width + 2f * offset, height + 2f * offset)
    }
}

internal object DbnetGeometry {
    private fun cross(origin: DetectionPoint, a: DetectionPoint, b: DetectionPoint): Float =
        (a.x - origin.x) * (b.y - origin.y) - (a.y - origin.y) * (b.x - origin.x)

    /** Andrew monotone-chain hull; interior and collinear boundary points are omitted. */
    private fun convexHull(points: List<DetectionPoint>): List<DetectionPoint> {
        val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size < 3) return sorted
        val lower = ArrayList<DetectionPoint>()
        for (point in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= 0f) {
                lower.removeAt(lower.lastIndex)
            }
            lower.add(point)
        }
        val upper = ArrayList<DetectionPoint>()
        for (point in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= 0f) {
                upper.removeAt(upper.lastIndex)
            }
            upper.add(point)
        }
        lower.removeAt(lower.lastIndex)
        upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    /** Projects the convex hull onto each hull-edge frame and chooses the smallest rectangle. */
    fun minAreaRect(points: List<DetectionPoint>): DbnetRotatedRect? {
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val hull = convexHull(points)
        if (hull.size < 3) return null
        var bestArea = Float.MAX_VALUE
        var best: DbnetRotatedRect? = null
        for (i in hull.indices) {
            val origin = hull[i]
            val next = hull[(i + 1) % hull.size]
            val length = hypot(next.x - origin.x, next.y - origin.y)
            if (length <= 1e-6f || !length.isFinite()) continue
            val axisX = (next.x - origin.x) / length
            val axisY = (next.y - origin.y) / length
            var minU = Float.MAX_VALUE
            var maxU = -Float.MAX_VALUE
            var minV = Float.MAX_VALUE
            var maxV = -Float.MAX_VALUE
            for (point in hull) {
                val dx = point.x - origin.x
                val dy = point.y - origin.y
                val u = dx * axisX + dy * axisY
                val v = -dx * axisY + dy * axisX
                minU = minOf(minU, u)
                maxU = maxOf(maxU, u)
                minV = minOf(minV, v)
                maxV = maxOf(maxV, v)
            }
            val width = maxU - minU
            val height = maxV - minV
            val area = width * height
            if (width <= 1e-6f || height <= 1e-6f || area >= bestArea || !area.isFinite()) continue
            bestArea = area
            val centerU = (minU + maxU) / 2f
            val centerV = (minV + maxV) / 2f
            best = DbnetRotatedRect(
                origin.x + centerU * axisX - centerV * axisY,
                origin.y + centerU * axisY + centerV * axisX,
                axisX,
                axisY,
                width,
                height,
            )
        }
        return best
    }
}
