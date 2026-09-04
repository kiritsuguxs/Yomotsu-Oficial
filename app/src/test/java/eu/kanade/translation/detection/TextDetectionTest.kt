package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TextDetectionTest {
    private fun point(x: Number, y: Number) = DetectionPoint(x.toFloat(), y.toFloat())
    private val box = listOf(point(10, 20), point(40, 20), point(40, 50), point(10, 50))

    @Test
    fun `normalizes reversed cyclic corners without changing crop winding`() {
        val region = TextDetection.normalize(box.reversed(), 0.9f, 100, 100)
        assertNotNull(region)
        assertEquals(box, region!!.points)
        assertEquals(0.9f, region.confidence)
    }

    @Test
    fun `rotates diamond corners while preserving each edge`() {
        val diamond = listOf(point(20, 0), point(40, 20), point(20, 40), point(0, 20))
        val region = TextDetection.normalize(diamond.drop(2) + diamond.take(2), 1f, 100, 100)
        assertEquals(diamond, region?.points)
    }

    @Test
    fun `clips corners to image extent and rejects fully collapsed padding box`() {
        val points = listOf(point(-10, -20), point(120, -20), point(120, 110), point(-10, 110))
        assertEquals(
            listOf(point(0, 0), point(100, 0), point(100, 100), point(0, 100)),
            TextDetection.normalize(points, 1f, 100, 100)?.points,
        )
        assertNull(TextDetection.normalize(box, 1f, 5, 5))
    }

    @Test
    fun `rejects missing duplicate concave crossed and collinear corners`() {
        val invalid = listOf(
            emptyList(),
            box.take(3),
            listOf(box[0], box[1], box[1], box[3]),
            listOf(point(0, 0), point(10, 0), point(2, 2), point(0, 10)),
            listOf(box[0], box[2], box[1], box[3]),
            listOf(point(0, 0), point(1, 1), point(2, 2), point(3, 3)),
        )
        invalid.forEach { assertNull(TextDetection.normalize(it, 1f, 100, 100)) }
    }

    @Test
    fun `rejects nonfinite points invalid confidence and invalid image size`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(TextDetection.normalize(listOf(point(value, 20)) + box.drop(1), 1f, 100, 100))
            assertNull(TextDetection.normalize(box, value, 100, 100))
        }
        assertNull(TextDetection.normalize(box, -0.1f, 100, 100))
        assertNull(TextDetection.normalize(box, 1.1f, 100, 100))
        assertNull(TextDetection.normalize(box, 1f, 0, 100))
    }

    @Test
    fun `normalization copies mutable input and returned point lists`() {
        val mutable = box.toMutableList()
        val region = TextDetection.normalize(mutable, 1f, 100, 100)!!
        mutable[0] = point(0, 0)
        val firstRead = region.points
        runCatching { (firstRead as MutableList<DetectionPoint>)[0] = point(99, 99) }
        assertEquals(box, region.points)
        assertEquals(point(10, 20), region.topLeft)
    }

    @Test
    fun `scales both axes before clipping and rejects invalid scaling`() {
        val region = TextRegion(box[0], box[1], box[2], box[3], 0.9f)
        assertEquals(
            listOf(point(20, 60), point(70, 60), point(70, 100), point(20, 100)),
            TextDetection.scale(region, 2f, 3f, 70, 100)?.points,
        )
        assertNull(TextDetection.scale(region, 0f, 1f, 100, 100))
        assertNull(TextDetection.scale(region, Float.NaN, 1f, 100, 100))
    }
}
