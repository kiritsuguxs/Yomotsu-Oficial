package eu.kanade.translation.detection

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbnetLineGroupingTest {
    private fun horizontal(x: Float, y: Float, width: Float = 40f, height: Float = 10f) =
        TextRegion(
            DetectionPoint(x, y),
            DetectionPoint(x + width, y),
            DetectionPoint(x + width, y + height),
            DetectionPoint(x, y + height),
            1f,
        )

    private fun rotated(
        centerX: Float,
        centerY: Float,
        width: Float = 40f,
        height: Float = 10f,
        degrees: Float,
    ): TextRegion {
        val radians = Math.toRadians(degrees.toDouble())
        val axisX = cos(radians).toFloat()
        val axisY = sin(radians).toFloat()
        fun point(longitudinal: Float, lateral: Float) = DetectionPoint(
            centerX + longitudinal * axisX - lateral * axisY,
            centerY + longitudinal * axisY + lateral * axisX,
        )
        return TextRegion(
            point(-width / 2f, -height / 2f),
            point(width / 2f, -height / 2f),
            point(width / 2f, height / 2f),
            point(-width / 2f, height / 2f),
            1f,
        )
    }

    @Test
    fun `merges aligned lines in one speech bubble and exposes their union geometry`() {
        val upper = horizontal(20f, 20f)
        val lower = horizontal(20f, 36f)

        val groups = DbnetLineGrouping.group(listOf(lower, upper))

        assertEquals(1, groups.size)
        assertEquals(listOf(upper, lower), groups.single().memberLines)
        assertEquals(DbnetTextDirection.HORIZONTAL, groups.single().direction)
        assertEquals(DbnetGroupBounds(20f, 20f, 60f, 46f), groups.single().bounds)
        assertEquals(4, groups.single().orientedBounds.size)
        assertEquals(0f, groups.single().angle)
    }

    @Test
    fun `keeps nearby but separately aligned speech bubbles apart`() {
        val left = listOf(horizontal(0f, 0f), horizontal(0f, 16f))
        val right = listOf(horizontal(55f, 0f), horizontal(55f, 16f))

        val groups = DbnetLineGrouping.group(listOf(left[1], right[0], left[0], right[1]))

        assertEquals(2, groups.size)
        assertEquals(listOf(left[0], left[1]), groups[0].memberLines)
        assertEquals(listOf(right[0], right[1]), groups[1].memberLines)
    }

    @Test
    fun `splits a permissively linked component at an anomalous MST gap`() {
        val firstBubble = listOf(0f, 14f, 28f, 42f).map { horizontal(10f, it) }
        val secondBubble = listOf(69f, 83f, 97f).map { horizontal(10f, it) }

        val groups = DbnetLineGrouping.group(firstBubble + secondBubble)

        assertEquals(2, groups.size)
        assertEquals(firstBubble, groups[0].memberLines)
        assertEquals(secondBubble, groups[1].memberLines)
    }

    @Test
    fun `splits two permissively linked one-line bubbles at a glyph-scale gap`() {
        val firstBubble = horizontal(0f, 0f)
        val secondBubble = horizontal(0f, 27f)

        val groups = DbnetLineGrouping.group(listOf(firstBubble, secondBubble))

        assertEquals(listOf(listOf(firstBubble), listOf(secondBubble)), groups.map { it.memberLines })
    }

    @Test
    fun `splits a three-line bridge by comparing its abnormal edge to the close pair`() {
        val firstBubble = listOf(horizontal(0f, 0f), horizontal(0f, 12f))
        val secondBubble = horizontal(0f, 39f)

        val groups = DbnetLineGrouping.group(firstBubble + secondBubble)

        assertEquals(listOf(firstBubble, listOf(secondBubble)), groups.map { it.memberLines })
    }

    @Test
    fun `splits a four-line bridge into its two close pairs`() {
        val firstBubble = listOf(horizontal(0f, 0f), horizontal(0f, 12f))
        val secondBubble = listOf(horizontal(0f, 39f), horizontal(0f, 51f))

        val groups = DbnetLineGrouping.group(firstBubble + secondBubble)

        assertEquals(listOf(firstBubble, secondBubble), groups.map { it.memberLines })
    }

    @Test
    fun `merges compatible rotated lines and retains their orientation`() {
        val first = rotated(100f, 100f, degrees = 30f)
        val second = rotated(91f, 115.6f, degrees = 30f)

        val group = DbnetLineGrouping.group(listOf(second, first)).single()

        assertEquals(listOf(first, second), group.memberLines)
        assertEquals(DbnetTextDirection.HORIZONTAL, group.direction)
        assertTrue(abs(group.angle - 30f) < 1f)
        assertEquals(4, group.orientedBounds.size)
        assertTrue(group.bounds.left <= group.memberLines.flatMap { it.points }.minOf { it.x })
        assertTrue(group.bounds.right >= group.memberLines.flatMap { it.points }.maxOf { it.x })
    }

    @Test
    fun `orders groups and lines deterministically independent of detector order`() {
        val leftTop = horizontal(0f, 0f)
        val leftBottom = horizontal(0f, 16f)
        val rightTop = horizontal(55f, 0f)
        val rightBottom = horizontal(55f, 16f)
        val shuffled = listOf(rightBottom, leftBottom, rightTop, leftTop)

        val groups = DbnetLineGrouping.group(shuffled)

        assertEquals(
            listOf(listOf(leftTop, leftBottom), listOf(rightTop, rightBottom)),
            groups.map { it.memberLines },
        )
    }

    @Test
    fun `rejects detector output beyond the bounded line limit`() {
        val lines = List(DbnetLineGrouping.MAX_LINES + 1) { horizontal(0f, 0f) }

        assertThrows(IllegalArgumentException::class.java) { DbnetLineGrouping.group(lines) }
    }
}
