package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ComicTranslationPromptTest {

    @Test
    fun `normalizes invisible Unicode spaces between words`() {
        assertEquals("Eu estou aqui", normalizeOcrText("Eu\u00A0\u200Bestou aqui"))
    }

    @Test
    fun `keeps ordinary text unchanged`() {
        assertEquals("Texto normal entre palavras", normalizeOcrText("Texto normal entre palavras"))
    }

    @Test
    fun `removes spaces before punctuation and collapses repeated spaces`() {
        assertEquals("Olá, mundo!", normalizeOcrText("Olá  ,  mundo !"))
    }

    @Test
    fun `removes zero width formatting characters`() {
        assertEquals("Texto limpo", normalizeOcrText("Te\u200Bxto\u2060 \uFEFFlimpo"))
    }

    @Test
    fun `normalizes non breaking spaces`() {
        assertEquals("Uma frase", normalizeOcrText("Uma\u202F\u00A0frase"))
    }

    @Test
    fun `preserves intentional paragraph breaks`() {
        assertEquals("Primeiro parágrafo\n\nSegundo parágrafo", normalizeOcrText("  Primeiro   parágrafo  \n\n  Segundo\u00A0parágrafo  "))
    }

    @Test
    fun `parses clean JSON response`() {
        val json = """{"001.jpg":["Olá mundo","Como vai?"],"002.jpg":["Tudo bem"]}"""
        val result = parseComicTranslationResponse(json)
        assertEquals(2, result.length())
        assertEquals("Olá mundo", result.getJSONArray("001.jpg").getString(0))
    }

    @Test
    fun `parses markdown wrapped JSON response`() {
        val markdown = """
            ```json
            {
              "page1.png": ["Fala 1", "Fala 2"]
            }
            ```
        """.trimIndent()
        val result = parseComicTranslationResponse(markdown)
        assertEquals(1, result.length())
        assertEquals("Fala 1", result.getJSONArray("page1.png").getString(0))
    }

    @Test
    fun `salvages completed pages from truncated JSON response`() {
        // Simulates LLM hitting token limit while generating 002.jpg
        val truncated = """
            {
              "001.jpg": ["Fala 1", "Fala 2"],
              "002.jpg": ["Fala 3", "Incomple
        """.trimIndent()
        val result = parseComicTranslationResponse(truncated)
        assertEquals(2, result.length())
        assertEquals(2, result.getJSONArray("001.jpg").length())
        assertEquals("Fala 3", result.getJSONArray("002.jpg").getString(0))
    }
}
