package eu.kanade.translation.recognizer

import com.paddle.ocr.engine.FloatTensorOutput
import com.paddle.ocr.postprocess.CTCDecoder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FloatTensorOutputTest {
    @Test
    fun `reuses the owned ORT array without a second full output allocation`() {
        val data = FloatArray(4 * 100 * 18710) { (it % 101) / 100f }
        assertSame(data, FloatTensorOutput.read(FloatBuffer.wrap(data)))
    }

    @Test
    fun `sliced direct and read only buffers preserve exactly the tensor values`() {
        val expected = floatArrayOf(0f, -0f, Float.NaN, Float.POSITIVE_INFINITY, 0.75f)
        val direct = ByteBuffer.allocateDirect(expected.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        direct.put(expected)
        val sliced = FloatBuffer.wrap(floatArrayOf(99f) + expected + floatArrayOf(99f)).apply {
            position(1)
            limit(1 + expected.size)
        }.slice()
        listOf(direct, sliced, FloatBuffer.wrap(expected).asReadOnlyBuffer()).forEach {
            assertArrayEquals(expected, FloatTensorOutput.read(it))
        }
    }

    @Test
    fun `full and limited buffers keep the legacy rewind semantics`() {
        val buffer = FloatBuffer.wrap(floatArrayOf(1f, 2f, 3f, 4f)).apply {
            limit(3)
            position(2)
        }
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), FloatTensorOutput.read(buffer))
        assertEquals(2, buffer.position())
    }

    @Test
    fun `CTC text confidence blank repeats and ties are bit identical`() {
        val classes = 18710
        val shape = longArrayOf(2, 8, classes.toLong())
        val data = FloatArray(2 * 8 * classes)
        val indices = intArrayOf(0, 1, 1, 0, 1, 2, 18709, 0, 2, 2, 0, 1, 1, 0, 18709, 18709)
        indices.forEachIndexed { t, index -> data[t * classes + index] = 0.75f }
        data[classes + 2] = 0.75f // First maximum wins the tie, as before.
        val dictionary = List(classes - 1) { if (it == classes - 2) "🛅" else ('A'.code + it % 26).toChar().toString() }
        assertEquals(
            CTCDecoder.decode(data.copyOf(), shape, dictionary),
            CTCDecoder.decode(FloatTensorOutput.read(FloatBuffer.wrap(data)), shape, dictionary),
        )
    }
}
