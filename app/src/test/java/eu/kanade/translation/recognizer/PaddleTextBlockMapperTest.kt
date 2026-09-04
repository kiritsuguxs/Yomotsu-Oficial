package eu.kanade.translation.recognizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaddleTextBlockMapperTest {

    @Test
    fun `maps a rotated quad to neutral geometry`() {
        val block = PaddleTextBlockMapper.map(
            text = "Hello",
            confidence = 0.94f,
            points = listOf(
                OcrPoint(10f, 20f),
                OcrPoint(110f, 30f),
                OcrPoint(105f, 70f),
                OcrPoint(5f, 60f),
            ),
        )!!

        assertEquals(5f, block.x)
        assertEquals(20f, block.y)
        assertEquals(105f, block.width)
        assertEquals(50f, block.height)
        assertTrue(block.angle in 5f..6f)
        assertTrue(block.symbolWidth > 19f)
        assertEquals(0.94f, block.confidence)
    }

    @Test
    fun `rejects malformed paddle results`() {
        assertNull(PaddleTextBlockMapper.map("", 0.9f, emptyList()))
        assertNull(PaddleTextBlockMapper.map("Hi", 0.9f, listOf(OcrPoint(0f, 0f))))
        assertNull(
            PaddleTextBlockMapper.map(
                "Hi",
                0.9f,
                List(4) { OcrPoint(10f, 10f) },
            ),
        )
    }

    @Test
    fun `rejects low confidence paddle noise`() {
        assertNull(
            PaddleTextBlockMapper.map(
                text = "watermark",
                confidence = 0.30f,
                points = listOf(
                    OcrPoint(0f, 0f), OcrPoint(120f, 0f),
                    OcrPoint(120f, 30f), OcrPoint(0f, 30f),
                ),
            ),
        )
    }

    @Test
    fun `rejects implausibly sparse text boxes`() {
        assertNull(
            PaddleTextBlockMapper.map(
                text = "Hi",
                confidence = 0.95f,
                points = listOf(
                    OcrPoint(0f, 0f), OcrPoint(1200f, 0f),
                    OcrPoint(1200f, 600f), OcrPoint(0f, 600f),
                ),
            ),
        )
    }
}
