package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DbnetPixelsTest {
    @Test fun `decode sample is positive bounded and preserves small images`() {
        assertEquals(1, DbnetPixels.sampleSize(600, 800, 2048))
        assertEquals(4, DbnetPixels.sampleSize(3000, 6000, 2048))
        assertThrows(IllegalArgumentException::class.java) { DbnetPixels.sampleSize(0, 5, 2048) }
    }

    @Test fun `pixels become RGB planes in minus one to one range`() {
        val input = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xffff0000.toInt())
        assertArrayEquals(floatArrayOf(-1f, 1f, 1f, -1f, 1f, -1f, -1f, 1f, -1f), DbnetPixels.toChw(input))
    }
}
