package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeminiTranslatorTest {
    @Test
    fun `gemini request chunks keep a chapter request below the per-call block limit`() {
        val items = (1..45).map { "bubble-$it" }

        val chunks = chunkGeminiItems(items, maxItems = 16)

        assertEquals(listOf(16, 16, 13), chunks.map { it.size })
        assertEquals(items, chunks.flatten())
    }
}
