// SPDX-License-Identifier: GPL-3.0-only
// Yomotsu detector-only geometry contract, 2026-08-31.
// Used with the Houri/houri-engine (Yakuyomi) detector adaptation; see
// docs/yakuyomi-dbnet-upstream.md for provenance and distribution notices.
package eu.kanade.translation.detection

import kotlin.math.abs

data class DetectionPoint(val x: Float, val y: Float)

/**
 * Bit-packed native DBNet mask. Its grid spans the padded detector input; [ratio] is the
 * nominal original-page resize ratio so consumers can project it without assuming square input.
 */
class DbnetMask(
    val width: Int,
    val height: Int,
    val inputWidth: Int,
    val inputHeight: Int,
    val ratio: Float,
    bytes: ByteArray,
) {
    private val packedBytes: ByteArray = bytes.copyOf()
    val bytes: ByteArray get() = packedBytes.copyOf()

    override fun equals(other: Any?): Boolean =
        other is DbnetMask && width == other.width && height == other.height &&
            inputWidth == other.inputWidth && inputHeight == other.inputHeight && ratio == other.ratio &&
            packedBytes.contentEquals(other.packedBytes)

    override fun hashCode(): Int =
        (((((width * 31 + height) * 31 + inputWidth) * 31 + inputHeight) * 31 + ratio.hashCode()) * 31 +
            packedBytes.contentHashCode())

    override fun toString(): String =
        "DbnetMask(width=$width, height=$height, inputWidth=$inputWidth, inputHeight=$inputHeight, ratio=$ratio, bytes=${packedBytes.size})"
}

/** Immutable crop quad. Use [TextDetection.normalize] at untrusted geometry boundaries. */
data class TextRegion(
    val topLeft: DetectionPoint,
    val topRight: DetectionPoint,
    val bottomRight: DetectionPoint,
    val bottomLeft: DetectionPoint,
    val confidence: Float,
) {
    val points: List<DetectionPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)
}

sealed interface DetectionResult {
    val workerTimings: DbnetWorkerTimings?

    data class Success(
        val width: Int,
        val height: Int,
        val regions: List<TextRegion>,
        val mask: DbnetMask,
        override val workerTimings: DbnetWorkerTimings? = null,
    ) : DetectionResult
    data class Failure(
        val reason: String,
        override val workerTimings: DbnetWorkerTimings? = null,
    ) : DetectionResult
}

enum class DbnetStageStatus {
    UNREACHED,
    COMPLETED,
    FAILED,
}

data class DbnetStageTiming(
    val status: DbnetStageStatus,
    val durationMillis: Long?,
) {
    init {
        require(durationMillis == null || durationMillis >= 0)
        require((status == DbnetStageStatus.UNREACHED) == (durationMillis == null))
    }

    companion object {
        val UNREACHED = DbnetStageTiming(DbnetStageStatus.UNREACHED, null)
        fun completed(durationMillis: Long) = DbnetStageTiming(DbnetStageStatus.COMPLETED, durationMillis)
        fun failed(durationMillis: Long) = DbnetStageTiming(DbnetStageStatus.FAILED, durationMillis)
    }
}

data class DbnetWorkerTimings(
    val preparation: DbnetStageTiming,
    val inference: DbnetStageTiming,
    val postprocess: DbnetStageTiming,
    val shape: DbnetWorkerShape? = null,
)

data class DbnetWorkerShape(
    val inputWidth: Int,
    val inputHeight: Int,
    val dbWidth: Int,
    val dbHeight: Int,
    val maskWidth: Int,
    val maskHeight: Int,
) {
    init {
        require(inputWidth in 1..DbnetResizePlan.MAX_INPUT_SIDE)
        require(inputHeight in 1..DbnetResizePlan.MAX_INPUT_SIDE)
        require(dbWidth in 1..inputWidth && dbHeight in 1..inputHeight)
        require(maskWidth in 1..inputWidth && maskHeight in 1..inputHeight)
    }
}

object TextDetection {
    /**
     * Accepts four cyclic convex corners in either winding. Crossed/concave input is rejected,
     * not repaired by sorting. Clips to the image extent, then rotates/reverses the cycle into
     * TL/TR/BR/BL (clockwise in image coordinates). TL minimizes x+y, breaking ties by y then x.
     * This definition also makes diamond-shaped crops deterministic without losing their edges.
     */
    fun normalize(points: List<DetectionPoint>, confidence: Float, width: Int, height: Int): TextRegion? {
        if (width <= 0 || height <= 0 || !confidence.isFinite() || confidence !in 0f..1f) return null
        if (points.size != 4 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        if (winding(points) == 0) return null
        val clipped = points.map {
            DetectionPoint(it.x.coerceIn(0f, width.toFloat()), it.y.coerceIn(0f, height.toFloat()))
        }
        val winding = winding(clipped)
        if (winding == 0) return null
        val clockwise = if (winding > 0) clipped else clipped.reversed()
        val first = clockwise.indices.minWithOrNull(
            compareBy<Int> { clockwise[it].x.toDouble() + clockwise[it].y }
                .thenBy { clockwise[it].y }.thenBy { clockwise[it].x },
        ) ?: return null
        return TextRegion(
            clockwise[first],
            clockwise[(first + 1) % 4],
            clockwise[(first + 2) % 4],
            clockwise[(first + 3) % 4],
            confidence,
        )
    }

    fun scale(region: TextRegion, scaleX: Float, scaleY: Float, width: Int, height: Int): TextRegion? {
        if (!scaleX.isFinite() || !scaleY.isFinite() || scaleX <= 0f || scaleY <= 0f) return null
        return normalize(
            region.points.map { DetectionPoint(it.x * scaleX, it.y * scaleY) },
            region.confidence,
            width,
            height,
        )
    }

    /** Strict convexity also rejects duplicates, collinear sides, and self-crossing quads. */
    private fun winding(points: List<DetectionPoint>): Int {
        var sign = 0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            val cross = (b.x.toDouble() - a.x) * (c.y.toDouble() - b.y) -
                (b.y.toDouble() - a.y) * (c.x.toDouble() - b.x)
            if (abs(cross) <= 1e-6) return 0
            val current = if (cross > 0) 1 else -1
            if (sign != 0 && sign != current) return 0
            sign = current
        }
        return sign
    }
}
