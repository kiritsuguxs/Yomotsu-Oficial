package eu.kanade.translation.recognizer

import com.paddle.ocr.PaddleOCRConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaddleOCRConfigTest {

    @Test
    fun `keeps mobile detector input capped instead of scanning full resolution pages`() {
        val config = PaddleOCRConfig()

        assertEquals("max", config.detLimitType)
        assertEquals(960, config.detLimitSideLen)
    }
}
