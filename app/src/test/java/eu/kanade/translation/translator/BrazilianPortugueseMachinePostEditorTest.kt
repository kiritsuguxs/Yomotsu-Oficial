package eu.kanade.translation.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrazilianPortugueseMachinePostEditorTest {

    @Test
    fun `preserves father and dad distinction`() {
        assertEquals(
            "“Pai”? Você não quis dizer “papai”?",
            edit(
                source = "\"Father\"? Don't you mean \"Dad\"?",
                translation = "\"Pai\"? Você não quer dizer \"pai\"?",
            ),
        )
    }

    @Test
    fun `corrects nightmare past tense`() {
        assertEquals(
            "...Acho que tive um pesadelo.",
            edit(
                source = "...I think I had a nightmare.",
                translation = "...Acho que tenho um pesadelo.",
            ),
        )
    }

    @Test
    fun `localizes liege and naturalizes completed job`() {
        assertEquals(
            "Meu senhor, nosso trabalho aqui terminou. Todos os nossos inimigos estão mortos.",
            edit(
                source = "My liege, our job here is done. All of our enemies are dead.",
                translation = "Meu liege. nosso trabalho aqui está feito. Todos os nossos inimigos estão mortos.",
            ),
        )
    }

    @Test
    fun `uses established Shadow Monarch title`() {
        assertEquals(
            "Monarca das Sombras?",
            edit(
                source = "Shadow Monarch?",
                translation = "Sombra Monarca?",
            ),
        )
    }

    @Test
    fun `does not change another target language`() {
        assertEquals(
            "Shadow Monarch?",
            postEditMachineTranslation(
                sourceText = "Shadow Monarch?",
                translatedText = "Shadow Monarch?",
                targetLanguage = TextTranslatorLanguage.ENGLISH,
            ),
        )
    }

    private fun edit(source: String, translation: String): String {
        return postEditMachineTranslation(
            sourceText = source,
            translatedText = translation,
            targetLanguage = TextTranslatorLanguage.PORTUGUESE,
        )
    }
}
