package eu.kanade.translation.recognizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PaddleOcrRuntimeTest {

    @Test
    fun `fails before inference when opencv cannot be initialized`() {
        var initializationAttempts = 0

        val error = assertThrows(IllegalStateException::class.java) {
            PaddleOcrRuntime.requireOpenCv {
                initializationAttempts++
                false
            }
        }

        assertEquals(1, initializationAttempts)
        assertEquals("Unable to initialize OpenCV for PaddleOCR", error.message)
    }
}
