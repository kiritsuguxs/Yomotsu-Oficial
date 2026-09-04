package eu.kanade.translation.recognizer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeechBubbleSizePolicyTest {

    @Test
    fun `accepts a large round balloon around a short OCR line`() {
        assertTrue(
            SpeechBubbleSizePolicy.accepts(
                sourceWidth = 220f,
                sourceHeight = 20f,
                detectedWidth = 700f,
                detectedHeight = 680f,
            ),
        )
    }

    @Test
    fun `rejects a large wide panel around a short OCR line`() {
        assertFalse(
            SpeechBubbleSizePolicy.accepts(
                sourceWidth = 220f,
                sourceHeight = 20f,
                detectedWidth = 1_400f,
                detectedHeight = 300f,
            ),
        )
    }
}
