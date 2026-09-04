// SPDX-License-Identifier: GPL-3.0-only
// Adapted 2026-08-31 from Houri/houri-engine (Yakuyomi) Grouping.kt and Geometry.kt,
// revision 85351aa3822fe2611f68cfd092972e6ac573f203. Changes: immutable Yomotsu
// TextRegion input, bounded Prim MST storage, and no OCR, translation, or renderer state.
// See docs/yakuyomi-dbnet-upstream.md for provenance and GPL notices.
package eu.kanade.translation.detection

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class DbnetTextDirection {
    HORIZONTAL,
    VERTICAL,
}

data class DbnetGroupBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class DbnetTextGroup(
    val memberLines: List<TextRegion>,
    val bounds: DbnetGroupBounds,
    val orientedBounds: List<DetectionPoint>,
    val direction: DbnetTextDirection,
    val angle: Float,
)

object DbnetLineGrouping {
    const val MAX_LINES = 2048

    /**
     * Groups DBNet line quads before recognition. Link candidates are intentionally permissive;
     * an anomalously large edge in each component's minimum spanning tree is then removed.
     */
    fun group(lines: List<TextRegion>): List<DbnetTextGroup> {
        require(lines.size <= MAX_LINES) { "DBNet line limit exceeded: $MAX_LINES" }
        if (lines.isEmpty()) return emptyList()

        val shapes = lines.map(::LineShape).sortedWith(LineShape.pageOrder)
        val parent = IntArray(shapes.size) { it }
        fun find(value: Int): Int {
            var root = value
            while (parent[root] != root) root = parent[root]
            var current = value
            while (parent[current] != current) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }
        fun join(first: Int, second: Int) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }

        for (first in shapes.indices) {
            for (second in first + 1 until shapes.size) {
                if (canLink(shapes[first], shapes[second])) join(first, second)
            }
        }
        val linked = linkedComponents(shapes.indices.toList(), ::find)
        return linked
            .flatMap { splitOutlierEdges(shapes, it) }
            .map { buildGroup(shapes, it) }
            .sortedWith(compareBy<DbnetTextGroup> { it.bounds.top }.thenBy { it.bounds.left }
                .thenBy { it.bounds.bottom }.thenBy { it.bounds.right })
    }

    private fun linkedComponents(indices: List<Int>, find: (Int) -> Int): List<List<Int>> =
        indices.groupBy(find).values.map { it.sorted() }

    private fun splitOutlierEdges(shapes: List<LineShape>, members: List<Int>): List<List<Int>> {
        if (members.size < 2) return listOf(members)
        val tree = primMst(shapes, members)
        val largest = tree.maxWithOrNull(compareBy<MstEdge> { it.distance }
            .thenBy { it.first }.thenBy { it.second }) ?: return listOf(members)
        val averageFont = members.map { shapes[it].fontSize }.average().toFloat()
        val isOutlier = if (tree.size == 1) {
            largest.distance > averageFont * 1.5f &&
                angularDistance(shapes[largest.first].angle, shapes[largest.second].angle) <= 15f
        } else {
            val remaining = tree.filterNot { it == largest }.map { it.distance }
            val mean = remaining.average().toFloat()
            val standardDeviation = sqrt(remaining.map { (it - mean) * (it - mean) }.average()).toFloat()
            largest.distance > averageFont * 1.5f &&
                largest.distance > mean + standardDeviation * 1.5f
        }
        if (!isOutlier) return listOf(members)

        val parent = IntArray(members.size) { it }
        fun find(value: Int): Int {
            var root = value
            while (parent[root] != root) root = parent[root]
            var current = value
            while (parent[current] != current) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }
        fun join(first: Int, second: Int) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }
        val localIndex = members.withIndex().associate { it.value to it.index }
        tree.filterNot { it == largest }.forEach { edge -> join(localIndex.getValue(edge.first), localIndex.getValue(edge.second)) }
        val split = members.groupBy { find(localIndex.getValue(it)) }.values.map { it.sorted() }
        return split.flatMap { splitOutlierEdges(shapes, it) }
    }

    /** Prim avoids materializing the complete graph's millions of edge objects. */
    private fun primMst(shapes: List<LineShape>, members: List<Int>): List<MstEdge> {
        val size = members.size
        val used = BooleanArray(size)
        val nearest = FloatArray(size) { Float.POSITIVE_INFINITY }
        val predecessor = IntArray(size) { -1 }
        nearest[0] = 0f
        val result = ArrayList<MstEdge>(size - 1)
        repeat(size) {
            var next = -1
            for (candidate in 0 until size) {
                if (!used[candidate] && (next == -1 || nearest[candidate] < nearest[next] ||
                        (nearest[candidate] == nearest[next] && members[candidate] < members[next]))
                ) {
                    next = candidate
                }
            }
            used[next] = true
            if (predecessor[next] >= 0) {
                result += MstEdge(members[predecessor[next]], members[next], nearest[next])
            }
            for (candidate in 0 until size) {
                if (used[candidate]) continue
                val distance = polygonDistance(shapes[members[next]].points, shapes[members[candidate]].points)
                if (distance < nearest[candidate] ||
                    (distance == nearest[candidate] && members[next] < members[predecessor[candidate]])
                ) {
                    nearest[candidate] = distance
                    predecessor[candidate] = next
                }
            }
        }
        return result
    }

    private fun canLink(first: LineShape, second: LineShape): Boolean {
        if (first.direction != second.direction) return false
        val smallerFont = min(first.fontSize, second.fontSize)
        if (smallerFont <= 0f || max(first.fontSize, second.fontSize) / smallerFont > 2f) return false
        if (angularDistance(first.angle, second.angle) > 15f) return false
        if (polygonDistance(first.points, second.points) >= smallerFont * 2f) return false
        val alongAxis = abs(dot(first.center - second.center, first.axis))
        return alongAxis < smallerFont * 3f
    }

    private fun buildGroup(shapes: List<LineShape>, members: List<Int>): DbnetTextGroup {
        val direction = shapes[members.first()].direction
        val axis = averageAxis(shapes, members, direction)
        val normal = Vector(-axis.y, axis.x)
        val ordered = members.sortedWith(compareBy<Int> { dot(shapes[it].center, normal) }
            .thenBy { dot(shapes[it].center, axis) })
        val points = members.flatMap { shapes[it].points }
        val left = points.minOf { it.x }
        val top = points.minOf { it.y }
        val right = points.maxOf { it.x }
        val bottom = points.maxOf { it.y }
        val minAxis = points.minOf { dot(Vector(it.x, it.y), axis) }
        val maxAxis = points.maxOf { dot(Vector(it.x, it.y), axis) }
        val minNormal = points.minOf { dot(Vector(it.x, it.y), normal) }
        val maxNormal = points.maxOf { dot(Vector(it.x, it.y), normal) }
        fun point(axisValue: Float, normalValue: Float) = DetectionPoint(
            axis.x * axisValue + normal.x * normalValue,
            axis.y * axisValue + normal.y * normalValue,
        )
        return DbnetTextGroup(
            memberLines = ordered.map { shapes[it].line },
            bounds = DbnetGroupBounds(left, top, right, bottom),
            orientedBounds = listOf(
                point(minAxis, minNormal), point(maxAxis, minNormal),
                point(maxAxis, maxNormal), point(minAxis, maxNormal),
            ),
            direction = direction,
            angle = normalizeAngle(Math.toDegrees(atan2(axis.y.toDouble(), axis.x.toDouble())).toFloat()),
        )
    }

    private fun averageAxis(shapes: List<LineShape>, members: List<Int>, direction: DbnetTextDirection): Vector {
        var x = 0f
        var y = 0f
        for (member in members) {
            val axis = shapes[member].axis
            x += axis.x
            y += axis.y
        }
        val length = hypot(x, y)
        if (length <= 1e-6f) return if (direction == DbnetTextDirection.HORIZONTAL) Vector(1f, 0f) else Vector(0f, 1f)
        return Vector(x / length, y / length)
    }

    private fun angularDistance(first: Float, second: Float): Float {
        val difference = abs(normalizeAngle(first - second))
        return min(difference, abs(180f - difference))
    }

    private fun normalizeAngle(value: Float): Float {
        var normalized = value % 360f
        if (normalized <= -180f) normalized += 360f
        if (normalized > 180f) normalized -= 360f
        return normalized
    }

    private data class MstEdge(val first: Int, val second: Int, val distance: Float)

    private data class Vector(val x: Float, val y: Float) {
        operator fun minus(other: Vector) = Vector(x - other.x, y - other.y)
    }

    private data class LineShape(val line: TextRegion) {
        val points = line.points
        val center = Vector(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)
        private val topLength = distance(points[0], points[1])
        private val sideLength = distance(points[0], points[3])
        private val majorIsTop = topLength >= sideLength
        val fontSize = if (majorIsTop) sideLength else topLength
        private val rawAxis = if (majorIsTop) points[1] - points[0] else points[3] - points[0]
        private val rawAxisLength = hypot(rawAxis.x, rawAxis.y)
        val axis = canonicalAxis(Vector(rawAxis.x / rawAxisLength, rawAxis.y / rawAxisLength))
        val direction = if (abs(axis.x) >= abs(axis.y)) DbnetTextDirection.HORIZONTAL else DbnetTextDirection.VERTICAL
        val angle = Math.toDegrees(atan2(axis.y.toDouble(), axis.x.toDouble())).toFloat()

        companion object {
            val pageOrder = compareBy<LineShape> { it.center.y }.thenBy { it.center.x }
                .thenBy { it.line.topLeft.y }.thenBy { it.line.topLeft.x }

            private fun canonicalAxis(axis: Vector): Vector = when {
                axis.x < -1e-6f -> Vector(-axis.x, -axis.y)
                abs(axis.x) <= 1e-6f && axis.y < 0f -> Vector(-axis.x, -axis.y)
                else -> axis
            }
        }
    }

    private operator fun DetectionPoint.minus(other: DetectionPoint) = Vector(x - other.x, y - other.y)
    private fun distance(first: DetectionPoint, second: DetectionPoint) = hypot(first.x - second.x, first.y - second.y)
    private fun hypot(x: Float, y: Float) = hypot(x.toDouble(), y.toDouble()).toFloat()
    private fun dot(first: Vector, second: Vector) = first.x * second.x + first.y * second.y

    private fun polygonDistance(first: List<DetectionPoint>, second: List<DetectionPoint>): Float {
        for (firstIndex in first.indices) {
            val firstNext = first[(firstIndex + 1) % first.size]
            for (secondIndex in second.indices) {
                val secondNext = second[(secondIndex + 1) % second.size]
                if (segmentsIntersect(first[firstIndex], firstNext, second[secondIndex], secondNext)) return 0f
            }
        }
        if (contains(first, second.first()) || contains(second, first.first())) return 0f
        var closest = Float.POSITIVE_INFINITY
        for (firstIndex in first.indices) {
            val firstNext = first[(firstIndex + 1) % first.size]
            for (point in second) closest = min(closest, pointSegmentDistance(point, first[firstIndex], firstNext))
        }
        for (secondIndex in second.indices) {
            val secondNext = second[(secondIndex + 1) % second.size]
            for (point in first) closest = min(closest, pointSegmentDistance(point, second[secondIndex], secondNext))
        }
        return closest
    }

    private fun contains(polygon: List<DetectionPoint>, point: DetectionPoint): Boolean {
        var inside = false
        var previous = polygon.lastIndex
        for (current in polygon.indices) {
            val a = polygon[current]
            val b = polygon[previous]
            if ((a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            ) inside = !inside
            previous = current
        }
        return inside
    }

    private fun segmentsIntersect(a: DetectionPoint, b: DetectionPoint, c: DetectionPoint, d: DetectionPoint): Boolean {
        fun cross(first: DetectionPoint, second: DetectionPoint, third: DetectionPoint): Float =
            (second.x - first.x) * (third.y - first.y) - (second.y - first.y) * (third.x - first.x)
        fun onSegment(point: DetectionPoint, start: DetectionPoint, end: DetectionPoint): Boolean =
            point.x in min(start.x, end.x)..max(start.x, end.x) &&
                point.y in min(start.y, end.y)..max(start.y, end.y)
        val first = cross(a, b, c)
        val second = cross(a, b, d)
        val third = cross(c, d, a)
        val fourth = cross(c, d, b)
        if (abs(first) <= 1e-6f && onSegment(c, a, b)) return true
        if (abs(second) <= 1e-6f && onSegment(d, a, b)) return true
        if (abs(third) <= 1e-6f && onSegment(a, c, d)) return true
        if (abs(fourth) <= 1e-6f && onSegment(b, c, d)) return true
        return (first > 0f) != (second > 0f) && (third > 0f) != (fourth > 0f)
    }

    private fun pointSegmentDistance(point: DetectionPoint, start: DetectionPoint, end: DetectionPoint): Float {
        val x = end.x - start.x
        val y = end.y - start.y
        val lengthSquared = x * x + y * y
        val fraction = if (lengthSquared == 0f) 0f else
            ((point.x - start.x) * x + (point.y - start.y) * y) / lengthSquared
        val clamped = fraction.coerceIn(0f, 1f)
        return hypot(point.x - (start.x + clamped * x), point.y - (start.y + clamped * y))
    }
}
