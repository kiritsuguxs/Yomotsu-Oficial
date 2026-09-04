package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbnetWireTest {
    private fun mask(
        width: Int = 5,
        height: Int = 3,
        inputWidth: Int = 512,
        inputHeight: Int = 1024,
        ratio: Float = 5.12f,
        bytes: ByteArray = byteArrayOf(0b0001_0011, 0b0010_0001),
    ) = DbnetMask(width, height, inputWidth, inputHeight, ratio, bytes)

    private fun decodeWithMask(width: Int, height: Int, packed: FloatArray, mask: DbnetMask): DetectionResult =
        DbnetWire.decode(
            width,
            height,
            packed,
            mask.width,
            mask.height,
            mask.inputWidth,
            mask.inputHeight,
            mask.ratio,
            mask.bytes,
        )

    @Test fun `geometry round trip preserves dimensions coordinates order and confidence`() {
        val a = TextDetection.normalize(
            listOf(DetectionPoint(2f, 3f), DetectionPoint(8f, 3f), DetectionPoint(8f, 9f), DetectionPoint(2f, 9f)),
            .9f,
            100,
            200,
        )!!
        val expectedMask = mask()
        val original = DetectionResult.Success(100, 200, listOf(a, a.copy(confidence = .8f)), expectedMask)
        val decoded = decodeWithMask(
            100,
            200,
            DbnetWire.encode(original),
            expectedMask,
        )
        val success = decoded as DetectionResult.Success
        assertEquals(original.width, success.width)
        assertEquals(original.height, success.height)
        assertEquals(original.regions, success.regions)
        assertEquals(expectedMask, success.mask)
        assertArrayEquals(expectedMask.bytes, success.mask.bytes)
    }

    @Test fun `malformed and oversized IPC output fails without partial regions`() {
        for (packed in listOf(
            floatArrayOf(1f),
            FloatArray(2049 * 9),
            floatArrayOf(Float.NaN, 0f, 1f, 0f, 1f, 1f, 0f, 1f, .8f),
        )) {
            assertTrue(decodeWithMask(100, 200, packed, mask()) is DetectionResult.Failure)
        }
        assertTrue(decodeWithMask(-1, 200, FloatArray(0), mask()) is DetectionResult.Failure)
    }

    @Test fun `empty valid detection is represented explicitly`() {
        assertEquals(
            emptyList<TextRegion>(),
            (decodeWithMask(100, 200, FloatArray(0), mask()) as DetectionResult.Success).regions,
        )
    }

    @Test fun `malformed mask metadata and encoded length fail before mask decode`() {
        val malformed = listOf(
            mask(width = 0),
            mask(inputWidth = 1024),
            mask(ratio = Float.NaN),
            mask(bytes = byteArrayOf(0)),
            mask(bytes = byteArrayOf(0b0001_0011, 0b1010_0001.toByte())),
            mask(width = 1024, height = 1024, inputWidth = 1024, inputHeight = 1024, ratio = 1f, bytes = ByteArray(128 * 1024)),
        )
        malformed.forEach { mask ->
            assertTrue(decodeWithMask(100, 200, FloatArray(0), mask) is DetectionResult.Failure)
        }
    }

    @Test fun `full native resolution mask remains within the transport bound`() {
        val native = mask(
            width = 1024,
            height = 1024,
            inputWidth = 1024,
            inputHeight = 1024,
            ratio = 1f,
            bytes = ByteArray(128 * 1024),
        )
        assertTrue(decodeWithMask(1024, 1024, FloatArray(0), native) is DetectionResult.Success)
    }
}
