package eu.kanade.tachiyomi.extension.novel.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NovelSpeechSanitizerTest {

    @Test
    fun `removes translator notes in brackets and parens`() {
        val input = "Ele avançou com a espada [NT: Espada Lendária] e atacou (N/T: golpe rápido)."
        val expected = "Ele avançou com a espada e atacou ."
        assertEquals(expected, NovelSpeechSanitizer.sanitize(input))
    }

    @Test
    fun `collapses repeated dots to single ellipsis`() {
        val input = "Esperem por mim........ não me deixem aqui......"
        val expected = "Esperem por mim... não me deixem aqui... "
        assertEquals(expected, NovelSpeechSanitizer.sanitize(input).trim())
    }

    @Test
    fun `normalizes asian and european dialogue quotes`() {
        val input = "「Você tem certeza disso?」 perguntou ela. «Sim», respondeu ele."
        val expected = "\"Você tem certeza disso?\" perguntou ela. \"Sim\", respondeu ele."
        assertEquals(expected, NovelSpeechSanitizer.sanitize(input))
    }

    @Test
    fun `strips markdown formatting`() {
        val input = "O guerreiro usou sua **força suprema** e desferiu um ataque *fulminante*."
        val expected = "O guerreiro usou sua força suprema e desferiu um ataque fulminante."
        assertEquals(expected, NovelSpeechSanitizer.sanitize(input))
    }

    @Test
    fun `removes visual dividers and decorative stars`() {
        val input = "Capítulo 1\n---\n★ Ele despertou no mundo novo ★\n======\nTudo havia mudado."
        val sanitized = NovelSpeechSanitizer.sanitize(input)
        assert(!sanitized.contains("---"))
        assert(!sanitized.contains("★"))
        assert(!sanitized.contains("======"))
    }
}
