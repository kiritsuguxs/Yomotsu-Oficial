package eu.kanade.translation.detection

import eu.kanade.translation.recognizer.OcrPage
import eu.kanade.translation.recognizer.OcrTextBlock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DbnetAssociatedGroup(
    val blockIndex: Int,
    val group: DbnetTextGroup,
)

data class DbnetAssociationMetadata(
    val mask: DbnetMask,
    val groups: List<DbnetAssociatedGroup>,
)

class DbnetAssociationException(message: String) : Exception(message)

object DbnetMlKitAssociation {
    /**
     * Emits one block per matched detector group. Every meaningful ML Kit block must have one
     * unambiguous owner; detector-only groups are noise and receive no translation or cleanup.
     * Block-level ML Kit text is never split across groups because no line boxes are available.
     */
    fun associate(
        detection: DetectionResult.Success,
        groups: List<DbnetTextGroup>,
        mlKitPage: OcrPage,
    ): OcrPage {
        validateInputs(detection, groups, mlKitPage)
        val blocks = deduplicateBlocks(mlKitPage.blocks)
        if (blocks.isEmpty()) fail("ML Kit returned no usable text")

        val groupParent = IntArray(groups.size) { it }
        fun find(i: Int): Int {
            var root = i
            while (groupParent[root] != root) root = groupParent[root]
            var curr = i
            while (groupParent[curr] != curr) {
                val next = groupParent[curr]
                groupParent[curr] = root
                curr = next
            }
            return root
        }
        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) groupParent[rootJ] = rootI
        }

        for (block in blocks) {
            val matches = groups.indices.filter { groupIndex -> matches(block, groups[groupIndex]) }
            if (matches.size > 1) {
                val first = matches.first()
                for (other in matches.drop(1)) {
                    union(first, other)
                }
            }
        }

        val blocksByGroup = mutableMapOf<Int, MutableList<OcrTextBlock>>()
        for (block in blocks) {
            val matches = groups.indices.filter { groupIndex -> matches(block, groups[groupIndex]) }
            val match = matches.firstOrNull()
            if (match != null) {
                val root = find(match)
                blocksByGroup.getOrPut(root) { mutableListOf() }.add(block)
            }
        }
        if (blocksByGroup.isEmpty()) fail("DBNet and ML Kit produced no usable association")

        val orderedRoots = blocksByGroup.keys.sortedWith(
            compareBy<Int> { root -> groups[root].bounds.top }
                .thenBy { root -> groups[root].bounds.left }
                .thenBy { root -> groups[root].bounds.bottom }
                .thenBy { root -> groups[root].bounds.right },
        )
        val groupsByRoot = groups.indices.groupBy(::find)
        val associatedGroups = ArrayList<DbnetAssociatedGroup>(orderedRoots.size)
        val associatedBlocks = orderedRoots.mapIndexed { blockIndex, root ->
            val members = groupsByRoot[root] ?: listOf(root)
            val resolvedGroup = if (members.size == 1) {
                groups[members.single()].snapshot()
            } else {
                val memberGroups = members.map { groups[it] }
                val allLines = memberGroups.flatMap { it.memberLines }.distinct()
                val minLeft = memberGroups.minOf { it.bounds.left }
                val minTop = memberGroups.minOf { it.bounds.top }
                val maxRight = memberGroups.maxOf { it.bounds.right }
                val maxBottom = memberGroups.maxOf { it.bounds.bottom }
                val allPoints = memberGroups.flatMap { it.orientedBounds }
                val pLeft = allPoints.minOf { it.x }
                val pTop = allPoints.minOf { it.y }
                val pRight = allPoints.maxOf { it.x }
                val pBottom = allPoints.maxOf { it.y }
                DbnetTextGroup(
                    memberLines = allLines,
                    bounds = DbnetGroupBounds(minLeft, minTop, maxRight, maxBottom),
                    orientedBounds = listOf(
                        DetectionPoint(pLeft, pTop),
                        DetectionPoint(pRight, pTop),
                        DetectionPoint(pRight, pBottom),
                        DetectionPoint(pLeft, pBottom),
                    ),
                    direction = DbnetTextDirection.HORIZONTAL,
                    angle = groups[root].angle,
                )
            }
            associatedGroups += DbnetAssociatedGroup(blockIndex, resolvedGroup)
            merge(resolvedGroup, blocksByGroup.getValue(root))
        }
        return mlKitPage.copy(
            blocks = associatedBlocks,
            dbnetAssociation = DbnetAssociationMetadata(detection.mask, associatedGroups.toList()),
        )
    }

    private fun validateInputs(
        detection: DetectionResult.Success,
        groups: List<DbnetTextGroup>,
        page: OcrPage,
    ) {
        if (detection.width <= 0 || detection.height <= 0) fail("Invalid DBNet page dimensions")
        if (page.width != detection.width || page.height != detection.height) fail("DBNet and ML Kit dimensions differ")
        if (detection.regions.isEmpty() || groups.isEmpty()) fail("DBNet returned no text groups")
        detection.regions.forEach { validateLine(it, detection.width, detection.height) }

        val owners = mutableMapOf<TextRegion, Int>()
        groups.forEachIndexed { groupIndex, group ->
            if (group.memberLines.isEmpty()) fail("DBNet group has no member lines")
            if (!group.angle.isFinite()) fail("DBNet group angle is not finite")
            validateBounds(group.bounds, detection.width, detection.height)
            if (group.orientedBounds.size != 4) fail("DBNet group oriented bounds are invalid")
            group.orientedBounds.forEach { validatePoint(it, detection.width, detection.height) }
            if (polygonArea(group.orientedBounds) <= AREA_EPSILON) fail("DBNet group oriented bounds have no area")
            group.memberLines.forEach { line ->
                if (line !in detection.regions) fail("DBNet group contains an unknown line")
                val previous = owners.putIfAbsent(line, groupIndex)
                if (previous != null && previous != groupIndex) fail("DBNet line belongs to multiple groups")
            }
        }
        if (detection.regions.any { it !in owners }) fail("DBNet line has no group owner")
        page.blocks.forEach { validateBlock(it, page.width, page.height) }
    }

    private fun validateLine(line: TextRegion, width: Int, height: Int) {
        if (!line.confidence.isFinite() || line.confidence !in 0f..1f) fail("DBNet confidence is invalid")
        line.points.forEach { validatePoint(it, width, height) }
        if (polygonArea(line.points) <= AREA_EPSILON) fail("DBNet line has no area")
    }

    private fun validatePoint(point: DetectionPoint, width: Int, height: Int) {
        if (!point.x.isFinite() || !point.y.isFinite() ||
            point.x < 0f || point.y < 0f || point.x > width || point.y > height
        ) fail("DBNet geometry is outside the original page")
    }

    private fun validateBounds(bounds: DbnetGroupBounds, width: Int, height: Int) {
        val values = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (values.any { !it.isFinite() } || bounds.left < 0f || bounds.top < 0f ||
            bounds.right > width || bounds.bottom > height ||
            bounds.right <= bounds.left || bounds.bottom <= bounds.top
        ) fail("DBNet group bounds are invalid")
    }

    private fun validateBlock(block: OcrTextBlock, width: Int, height: Int) {
        if (block.text.isBlank()) fail("ML Kit text is blank")
        val values = listOf(
            block.x, block.y, block.width, block.height,
            block.symbolWidth, block.symbolHeight, block.angle,
        )
        if (values.any { !it.isFinite() } || block.width <= 0f || block.height <= 0f ||
            block.symbolWidth <= 0f || block.symbolHeight <= 0f ||
            block.x < 0f || block.y < 0f || block.x + block.width > width || block.y + block.height > height
        ) fail("ML Kit block geometry is invalid")
        if (block.confidence?.let { !it.isFinite() || it !in 0f..1f } == true) {
            fail("ML Kit confidence is invalid")
        }
    }

    private fun deduplicateBlocks(blocks: List<OcrTextBlock>): List<OcrTextBlock> {
        val unique = ArrayList<OcrTextBlock>(blocks.size)
        for (block in blocks) {
            val sameGeometry = unique.firstOrNull { existing -> sameGeometry(existing, block) }
            if (sameGeometry == null) {
                unique += block
            } else if (sameGeometry.text != block.text) {
                fail("Conflicting ML Kit text occupies the same geometry")
            }
        }
        return unique
    }

    private fun sameGeometry(first: OcrTextBlock, second: OcrTextBlock): Boolean =
        abs(first.x - second.x) <= GEOMETRY_EPSILON &&
            abs(first.y - second.y) <= GEOMETRY_EPSILON &&
            abs(first.width - second.width) <= GEOMETRY_EPSILON &&
            abs(first.height - second.height) <= GEOMETRY_EPSILON

    private fun matches(block: OcrTextBlock, group: DbnetTextGroup): Boolean {
        val rect = Rect(block.x, block.y, block.x + block.width, block.y + block.height)
        val lineArea = group.memberLines.sumOf { polygonArea(it.points).toDouble() }.toFloat()
        val intersection = group.memberLines
            .sumOf { intersectionArea(it.points, rect).toDouble() }
            .toFloat()
            .coerceAtMost(rect.area)
        val overlapRatio = intersection / min(rect.area, lineArea)
        if (overlapRatio >= MIN_OVERLAP_RATIO) return true

        val blockCenter = DetectionPoint((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f)
        return group.memberLines.any { line ->
            contains(line.points, blockCenter) || rect.contains(line.center())
        }
    }

    private fun matches(block: OcrTextBlock, line: TextRegion): Boolean {
        val rect = Rect(block.x, block.y, block.x + block.width, block.y + block.height)
        val points = line.points
        val lineArea = polygonArea(points)
        val intersection = intersectionArea(points, rect).coerceAtMost(rect.area)
        if (intersection / min(rect.area, lineArea) >= MIN_OVERLAP_RATIO) return true
        val blockCenter = DetectionPoint((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f)
        return contains(points, blockCenter) || rect.contains(center(points))
    }

    private fun merge(group: DbnetTextGroup, source: List<OcrTextBlock>): OcrTextBlock {
        val ordered = source.sortedWith(lineReadingOrder)
        if (ordered.isEmpty()) {
            return OcrTextBlock(
                text = "",
                x = group.bounds.left,
                y = group.bounds.top,
                width = group.bounds.right - group.bounds.left,
                height = group.bounds.bottom - group.bounds.top,
                symbolWidth = 1f,
                symbolHeight = 1f,
                angle = group.angle,
                confidence = null,
            )
        }
        val confidences = ordered.mapNotNull { it.confidence }
        return OcrTextBlock(
            text = ordered.joinToString("\n") { it.text },
            x = group.bounds.left,
            y = group.bounds.top,
            width = group.bounds.right - group.bounds.left,
            height = group.bounds.bottom - group.bounds.top,
            symbolWidth = ordered.map { it.symbolWidth }.average().toFloat(),
            symbolHeight = ordered.map { it.symbolHeight }.average().toFloat(),
            angle = group.angle,
            confidence = confidences.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        )
    }

    private val lineReadingOrder = Comparator<OcrTextBlock> { a, b ->
        val yDiff = a.y - b.y
        val lineThreshold = minOf(a.height, b.height) * 0.5f
        if (abs(yDiff) > lineThreshold) {
            yDiff.compareTo(0f)
        } else {
            a.x.compareTo(b.x)
        }
    }

    private fun DbnetTextGroup.snapshot() = copy(
        memberLines = memberLines.toList(),
        orientedBounds = orientedBounds.toList(),
    )

    private fun TextRegion.center() = center(points)

    private fun center(points: List<DetectionPoint>) = DetectionPoint(
        points.sumOf { it.x.toDouble() }.toFloat() / points.size,
        points.sumOf { it.y.toDouble() }.toFloat() / points.size,
    )

    private fun intersectionArea(polygon: List<DetectionPoint>, rect: Rect): Float {
        var clipped = polygon
        clipped = clipVertical(clipped, rect.left, keepGreater = true)
        clipped = clipVertical(clipped, rect.right, keepGreater = false)
        clipped = clipHorizontal(clipped, rect.top, keepGreater = true)
        clipped = clipHorizontal(clipped, rect.bottom, keepGreater = false)
        return polygonArea(clipped)
    }

    private fun clipVertical(
        polygon: List<DetectionPoint>,
        boundary: Float,
        keepGreater: Boolean,
    ): List<DetectionPoint> = clip(polygon, { point -> (point.x >= boundary) == keepGreater }) { start, end ->
        val fraction = (boundary - start.x) / (end.x - start.x)
        DetectionPoint(boundary, start.y + (end.y - start.y) * fraction)
    }

    private fun clipHorizontal(
        polygon: List<DetectionPoint>,
        boundary: Float,
        keepGreater: Boolean,
    ): List<DetectionPoint> = clip(polygon, { point -> (point.y >= boundary) == keepGreater }) { start, end ->
        val fraction = (boundary - start.y) / (end.y - start.y)
        DetectionPoint(start.x + (end.x - start.x) * fraction, boundary)
    }

    private fun clip(
        polygon: List<DetectionPoint>,
        inside: (DetectionPoint) -> Boolean,
        intersection: (DetectionPoint, DetectionPoint) -> DetectionPoint,
    ): List<DetectionPoint> {
        if (polygon.isEmpty()) return emptyList()
        val result = ArrayList<DetectionPoint>(polygon.size + 2)
        var previous = polygon.last()
        var previousInside = inside(previous)
        for (current in polygon) {
            val currentInside = inside(current)
            if (currentInside != previousInside) result += intersection(previous, current)
            if (currentInside) result += current
            previous = current
            previousInside = currentInside
        }
        return result
    }

    private fun polygonArea(points: List<DetectionPoint>): Float {
        if (points.size < 3) return 0f
        var twiceArea = 0.0
        for (index in points.indices) {
            val next = points[(index + 1) % points.size]
            twiceArea += points[index].x.toDouble() * next.y - next.x.toDouble() * points[index].y
        }
        return (abs(twiceArea) / 2.0).toFloat()
    }

    private fun contains(polygon: List<DetectionPoint>, point: DetectionPoint): Boolean {
        var inside = false
        var previous = polygon.lastIndex
        for (current in polygon.indices) {
            val first = polygon[current]
            val second = polygon[previous]
            if ((first.y > point.y) != (second.y > point.y) &&
                point.x < (second.x - first.x) * (point.y - first.y) / (second.y - first.y) + first.x
            ) inside = !inside
            previous = current
        }
        return inside
    }

    private fun fail(message: String): Nothing = throw DbnetAssociationException(message)

    private data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val area = max(0f, right - left) * max(0f, bottom - top)
        fun contains(point: DetectionPoint): Boolean =
            point.x in left..right && point.y in top..bottom
    }

    private const val AREA_EPSILON = 1e-3f
    private const val GEOMETRY_EPSILON = 0.5f
    private const val MIN_OVERLAP_RATIO = 0.1f
}
