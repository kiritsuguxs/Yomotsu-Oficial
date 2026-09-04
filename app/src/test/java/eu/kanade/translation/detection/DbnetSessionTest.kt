package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbnetSessionTest {
    private class Backend : DbnetBackend {
        var handle = 7L
        var status = 0
        var calls = 0
        var releases = 0
        override fun create(param: String, bin: String): Long = handle
        override fun infer(
            handle: Long,
            input: FloatArray,
            width: Int,
            height: Int,
            db: FloatArray,
            mask: FloatArray,
            dimensions: IntArray,
        ): Int {
            calls++
            intArrayOf(width, height, 2, width / 2, height / 2, 1).copyInto(dimensions)
            db[0] = 4f
            return status
        }
        override fun release(handle: Long) {
            releases++
        }
    }

    @Test fun `model load failure is reported before inference`() {
        assertThrows(IllegalStateException::class.java) { DbnetSession(Backend().apply { handle = 0 }, "p", "b") }
    }

    @Test fun `native error is recoverable and close releases once`() {
        val backend = Backend().apply { status = -3 }
        val session = DbnetSession(backend, "p", "b")
        assertThrows(IllegalStateException::class.java) { session.infer(FloatArray(3 * 256 * 256), 256, 256) }
        session.close()
        session.close()
        assertEquals(1, backend.releases)
    }

    @Test fun `native dynamic dimensions and tensors are preserved`() {
        val result = DbnetSession(Backend(), "p", "b").use { it.infer(FloatArray(3 * 256 * 512), 256, 512) }
        assertArrayEquals(intArrayOf(256, 512, 2, 128, 256, 1), result.dimensions)
        assertEquals(4f, result.db[0])
        assertEquals(256 * 512, result.mask.size)
    }

    @Test fun `closed session and invalid input never enter backend`() {
        val backend = Backend()
        val session = DbnetSession(backend, "p", "b")
        assertThrows(IllegalArgumentException::class.java) { session.infer(FloatArray(1), 256, 256) }
        session.close()
        assertThrows(IllegalStateException::class.java) { session.infer(FloatArray(3 * 256 * 256), 256, 256) }
        assertEquals(0, backend.calls)
    }
}
