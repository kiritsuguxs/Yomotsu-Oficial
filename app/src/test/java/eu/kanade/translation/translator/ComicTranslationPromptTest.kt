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
}
