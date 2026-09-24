package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InpainterTest {

    private class MockDbnetBackend : DbnetBackend {
        var createdParam: String? = null
        var createdBin: String? = null
        var handleToReturn = 42L
        var releaseCount = 0
        var releasedHandle = 0L

        override fun create(param: String, bin: String): Long {
            createdParam = param
            createdBin = bin
            return handleToReturn
        }

        override fun infer(
            handle: Long,
            input: FloatArray,
            width: Int,
            height: Int,
            db: FloatArray,
            mask: FloatArray,
            dimensions: IntArray,
        ): Int = 0

        override fun inpaintAot(
            handle: Long,
            img: FloatArray,
            mask: FloatArray,
            s: Int,
            out: FloatArray,
        ): Int {
            // Echo input back into out for testing
            img.copyInto(out)
            return 0
        }

        override fun release(handle: Long) {
            releaseCount++
            releasedHandle = handle
        }
    }

    @Test
    fun `dilate expands binary mask correctly`() {
        val w = 5
        val h = 5
        // Single white pixel at center (2, 2)
        val mask = IntArray(w * h) { -0x1000000 } // Color.BLACK = 0xFF000000
        mask[2 * w + 2] = -0x1 // Color.WHITE = 0xFFFFFFFF

        Inpainter.dilate(mask, w, h, 1)

        // Center and 4 neighbors must now be white
        assertTrue((mask[2 * w + 2] and 0xFF) > 127, "Center must remain white")
        assertTrue((mask[1 * w + 2] and 0xFF) > 127, "Top neighbor must be dilated to white")
        assertTrue((mask[3 * w + 2] and 0xFF) > 127, "Bottom neighbor must be dilated to white")
        assertTrue((mask[2 * w + 1] and 0xFF) > 127, "Left neighbor must be dilated to white")
        assertTrue((mask[2 * w + 3] and 0xFF) > 127, "Right neighbor must be dilated to white")
        // Far corners should remain black
        assertEquals(-0x1000000, mask[0])
    }

    @Test
    fun `dilate with zero radius is no-op`() {
        val w = 3
        val h = 3
        val mask = IntArray(w * h) { if (it == 4) -0x1 else -0x1000000 }
        val copy = mask.copyOf()
        Inpainter.dilate(mask, w, h, 0)
        assertTrue(mask.contentEquals(copy))
    }

    @Test
    fun `aotImageChwFromPixels zeroes holes and normalizes valid pixels to minus one and one`() {
        val n = 2
        val area = n * n
        // Pixel 0: pure white (0x00FFFFFF) with unmasked mask (0)
        // Pixel 1: pure black (0x00000000) with unmasked mask (0)
        // Pixel 2: masked hole (mask = 255)
        // Pixel 3: mid gray (0x00808080) with unmasked mask (0)
        val px = intArrayOf(
            0x00FFFFFF,
            0x00000000,
            0x00FF0000,
            0x00808080,
        )
        val mp = intArrayOf(
            0x00,
            0x00,
            0xFF,
            0x00,
        )

        val chw = Inpainter.aotImageChwFromPixels(px, mp, n)
        assertEquals(3 * area, chw.size)

        // Pixel 0 (white): normalized to 1.0f in all 3 channels
        assertEquals(1.0f, chw[0], 0.001f) // R
        assertEquals(1.0f, chw[area + 0], 0.001f) // G
        assertEquals(1.0f, chw[2 * area + 0], 0.001f) // B

        // Pixel 1 (black): normalized to -1.0f in all 3 channels
        assertEquals(-1.0f, chw[1], 0.001f)
        assertEquals(-1.0f, chw[area + 1], 0.001f)
        assertEquals(-1.0f, chw[2 * area + 1], 0.001f)

        // Pixel 2 (hole): zeroed out
        assertEquals(0.0f, chw[2], 0.001f)
        assertEquals(0.0f, chw[area + 2], 0.001f)
        assertEquals(0.0f, chw[2 * area + 2], 0.001f)

        // Pixel 3 (mid gray 128): (128 / 127.5) - 1.0 ~= 0.0039f
        assertEquals(128f / 127.5f - 1f, chw[3], 0.001f)
    }

    @Test
    fun `maskArrFromPixels generates float mask`() {
        val mp = intArrayOf(0x00, 0xFF, 0x80, 0x7F)
        val mask = Inpainter.maskArrFromPixels(mp, 2)
        assertEquals(0.0f, mask[0])
        assertEquals(1.0f, mask[1])
        assertEquals(1.0f, mask[2]) // 0x80 = 128 > 127
        assertEquals(0.0f, mask[3]) // 0x7F = 127 <= 127
    }

    @Test
    fun `aotArrToPixels reconstructs correct ARGB values`() {
        val n = 2
        val area = n * n
        val out = FloatArray(3 * area)
        // Pixel 0: 1.0f in all channels -> 255
        out[0] = 1.0f
        out[area + 0] = 1.0f
        out[2 * area + 0] = 1.0f

        // Pixel 1: -1.0f in all channels -> 0
        out[1] = -1.0f
        out[area + 1] = -1.0f
        out[2 * area + 1] = -1.0f

        // Pixel 2: 0.0f -> (0 + 1) * 127.5 = 127
        out[2] = 0.0f
        out[area + 2] = 0.0f
        out[2 * area + 2] = 0.0f

        val pixels = Inpainter.aotArrToPixels(out, n)
        assertEquals(4, pixels.size)

        // Check pixel 0 (white)
        assertEquals(0xFF, (pixels[0] ushr 24) and 0xFF)
        assertEquals(0xFF, (pixels[0] ushr 16) and 0xFF)
        assertEquals(0xFF, (pixels[0] ushr 8) and 0xFF)
        assertEquals(0xFF, pixels[0] and 0xFF)

        // Check pixel 1 (black)
        assertEquals(0xFF, (pixels[1] ushr 24) and 0xFF)
        assertEquals(0x00, (pixels[1] ushr 16) and 0xFF)
        assertEquals(0x00, (pixels[1] ushr 8) and 0xFF)
        assertEquals(0x00, pixels[1] and 0xFF)

        // Check pixel 2 (mid gray)
        assertEquals(127, (pixels[2] ushr 16) and 0xFF)
    }

    @Test
    fun `inpainter lifecycle initializes and releases backend`() {
        val backend = MockDbnetBackend()
        val inpainter = Inpainter(
            modelPath = "/path/to/mit_aot_fixed512.ncnn.param",
            cfg = InpainterConfig(method = InpainterConfig.METHOD_AOT),
            backend = backend,
        )

        assertTrue(inpainter.isModelLoaded)
        assertEquals("NCNN-CPU", inpainter.executionProvider)
        assertEquals("/path/to/mit_aot_fixed512.ncnn.param", backend.createdParam)
        assertEquals("/path/to/mit_aot_fixed512.ncnn.bin", backend.createdBin)

        inpainter.close()
        assertEquals(1, backend.releaseCount)
        assertEquals(42L, backend.releasedHandle)
        assertFalse(inpainter.isModelLoaded)

        // Idempotent close
        inpainter.close()
        assertEquals(1, backend.releaseCount)
    }

    @Test
    fun `inpainter falls back gracefully when model path is null or method is boxfill`() {
        val backend = MockDbnetBackend()
        val inpainter = Inpainter(
            modelPath = null,
            cfg = InpainterConfig(method = InpainterConfig.METHOD_BOXFILL),
            backend = backend,
        )

        assertFalse(inpainter.isModelLoaded)
        assertEquals("BOXFILL", inpainter.executionProvider)
        assertEquals(0, backend.releaseCount)
        inpainter.close()
    }
}
