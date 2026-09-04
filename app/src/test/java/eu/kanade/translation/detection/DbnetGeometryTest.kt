package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbnetGeometryTest {
    @Test
    fun `resize upscales longest edge and pads right and bottom to 256`() {
        val plan = DbnetResizePlan.create(300, 200)
        assertEquals(300, plan.originalWidth)
        assertEquals(200, plan.originalHeight)
        assertEquals(1024, plan.resizedWidth)
        assertEquals(683, plan.resizedHeight)
        assertEquals(1024, plan.inputWidth)
        assertEquals(768, plan.inputHeight)
        assertEquals(1024f / 300, plan.ratio)
    }

    @Test
    fun `resize retains nominal ratio despite rounding and preserves portrait orientation`() {
        val plan = DbnetResizePlan.create(721, 2048)
        assertEquals(361, plan.resizedWidth)
        assertEquals(1024, plan.resizedHeight)
        assertEquals(512, plan.inputWidth)
        assertEquals(1024, plan.inputHeight)
        assertEquals(0.5f, plan.ratio)
        assertNotEquals(361f / 721, plan.ratio)
    }

    @Test
    fun `extremely narrow accepted input retains one resized pixel`() {
        val plan = DbnetResizePlan.create(1, 100_000)
        assertEquals(1, plan.resizedWidth)
        assertEquals(256, plan.inputWidth)
        assertEquals(1024, plan.inputHeight)
    }

    @Test
    fun `rejects nonpositive absurd and overflow dimensions before multiplication`() {
        listOf(0 to 1, 1 to -1, Int.MAX_VALUE to Int.MAX_VALUE, 100_001 to 1, 100_000 to 100_000)
            .forEach { (w, h) -> assertThrows(IllegalArgumentException::class.java) { DbnetResizePlan.create(w, h) } }
    }

    @Test
    fun `minimum rectangle follows rotated shape and ignores interior points`() {
        val points = listOf(
            DetectionPoint(10f, 10f),
            DetectionPoint(18f, 16f),
            DetectionPoint(15f, 20f),
            DetectionPoint(7f, 14f),
            DetectionPoint(12f, 15f),
        )
        val rect = DbnetGeometry.minAreaRect(points)!!
        assertEquals(50f, rect.width * rect.height, 0.001f)
        assertEquals(12.5f, rect.centerX, 0.001f)
        assertEquals(15f, rect.centerY, 0.001f)
        val region = TextDetection.normalize(rect.corners(), 1f, 100, 100)!!
        val expected = points.take(4)
        expected.zip(region.points).forEach { (a, b) ->
            assertEquals(a.x, b.x, 0.001f)
            assertEquals(a.y, b.y, 0.001f)
        }
    }

    @Test
    fun `unclip expands each edge by area times ratio divided by perimeter`() {
        val rect = DbnetRotatedRect(20f, 20f, 1f, 0f, 10f, 4f).unclip(2.3f)
        assertEquals(16.571428f, rect.width, 0.0001f)
        assertEquals(10.571428f, rect.height, 0.0001f)
        assertEquals(20f, rect.centerX)
        assertEquals(20f, rect.centerY)
    }

    @Test
    fun `empty singleton nonfinite and collinear geometry has no usable rectangle`() {
        val point = DetectionPoint(1f, 1f)
        assertNull(DbnetGeometry.minAreaRect(emptyList()))
        assertNull(DbnetGeometry.minAreaRect(listOf(point)))
        assertNull(DbnetGeometry.minAreaRect(listOf(point, point, point)))
        assertNull(DbnetGeometry.minAreaRect(listOf(point, DetectionPoint(2f, 2f), DetectionPoint(3f, 3f))))
        assertNull(DbnetGeometry.minAreaRect(listOf(point, DetectionPoint(Float.NaN, 2f))))
    }
}
