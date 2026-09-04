// SPDX-License-Identifier: GPL-3.0-only
// Adapted 2026-08-31 from Houri/houri-engine (Yakuyomi), Detector.kt at
// 85351aa3822fe2611f68cfd092972e6ac573f203 (upstream credits
// manga-image-translator/detection/default_utils/ @ d5a3eee).
// Changes: actual-shape validation, bounded outputs, neutral immutable quads,
// independent grid-axis scaling, and compact native mask transport.
// See docs/yakuyomi-dbnet-upstream.md for provenance and GPL notices.
package eu.kanade.translation.detection

import kotlin.math.exp
import kotlin.math.min

object DbnetPostprocessor {
    private const val BIN_THRESHOLD = 0.5f
    private const val SCORE_THRESHOLD = 0.7f
    private const val MIN_SIDE = 3f
    private const val UNCLIP_RATIO = 2.3f
    private const val MAX_REGIONS = 2048

    /**
     * DB is planar fp32 as validated by JNI. Only channel zero is consumed as raw logits.
     * Buffer capacities may exceed actual shapes, but never the bounded input capacities.
     * The mask's actual shape/capacity is checked independently and retained for safe IPC.
     */
    fun process(
        db: FloatArray,
        dbWidth: Int,
        dbHeight: Int,
        dbChannels: Int,
        maskWidth: Int,
        maskHeight: Int,
        maskChannels: Int,
        mask: FloatArray,
        plan: DbnetResizePlan,
    ): DetectionResult {
        val inputArea = plan.inputWidth * plan.inputHeight
        if (dbChannels != 2 || maskChannels != 1) return DetectionResult.Failure("Invalid DBNet output channels")
        if (dbWidth !in 1..plan.inputWidth || dbHeight !in 1..plan.inputHeight ||
            maskWidth !in 1..plan.inputWidth || maskHeight !in 1..plan.inputHeight
        ) {
            return DetectionResult.Failure("Invalid DBNet output dimensions")
        }
        // Dimensions have been bounded before multiplication.
        val area = dbWidth * dbHeight
        val maskArea = maskWidth * maskHeight
        if (area > DbnetResizePlan.MAX_INPUT_AREA || db.size < area * 2 || db.size > inputArea * 2 ||
            mask.size < maskArea || mask.size > inputArea
        ) {
            return DetectionResult.Failure("Invalid or truncated DBNet output buffer")
        }
        val compactMask = DbnetWire.packMask(mask, maskWidth, maskHeight, plan)
            ?: return DetectionResult.Failure("Invalid DBNet mask")

        val probabilities = FloatArray(area)
        for (i in 0 until area) {
            val logit = db[i]
            if (!logit.isFinite()) return DetectionResult.Failure("Nonfinite DBNet logit")
            probabilities[i] = 1f / (1f + exp(-logit))
        }
        val visited = BooleanArray(area)
        val stack = IntArray(area)
        val regions = ArrayList<TextRegion>()
        val boundary = ArrayList<DetectionPoint>()
        val scaleX = plan.inputWidth.toFloat() / dbWidth / plan.ratio
        val scaleY = plan.inputHeight.toFloat() / dbHeight / plan.ratio

        // Row-major seed order remains the externally visible region order.
        for (seed in probabilities.indices) {
            if (visited[seed] || probabilities[seed] <= BIN_THRESHOLD) continue
            var stackSize = 0
            stack[stackSize++] = seed
            visited[seed] = true
            boundary.clear()
            var sum = 0.0
            var count = 0
            while (stackSize > 0) {
                val index = stack[--stackSize]
                val x = index % dbWidth
                val y = index / dbWidth
                sum += probabilities[index]
                count++
                var isBoundary = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until dbWidth && ny in 0 until dbHeight) {
                            val neighbor = ny * dbWidth + nx
                            if (probabilities[neighbor] > BIN_THRESHOLD) {
                                if (!visited[neighbor]) {
                                    visited[neighbor] = true
                                    stack[stackSize++] = neighbor
                                }
                            } else if (dx == 0 || dy == 0) {
                                isBoundary = true
                            }
                        } else if (dx == 0 || dy == 0) {
                            isBoundary = true
                        }
                    }
                }
                if (isBoundary) boundary.add(DetectionPoint(x.toFloat(), y.toFloat()))
            }
            val score = (sum / count).toFloat()
            if (score < SCORE_THRESHOLD) continue
            val rect = DbnetGeometry.minAreaRect(boundary) ?: continue
            if (min(rect.width, rect.height) < MIN_SIDE) continue
            val points = rect.unclip(UNCLIP_RATIO).corners().map {
                DetectionPoint(it.x * scaleX, it.y * scaleY)
            }
            val region = TextDetection.normalize(points, score, plan.originalWidth, plan.originalHeight) ?: continue
            if (regions.size == MAX_REGIONS) return DetectionResult.Failure("DBNet region limit exceeded: $MAX_REGIONS")
            regions.add(region)
        }
        return DetectionResult.Success(plan.originalWidth, plan.originalHeight, regions.toList(), compactMask)
    }
}
