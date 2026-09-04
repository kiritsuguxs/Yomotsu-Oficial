package eu.kanade.presentation.more.settings.screen

import eu.kanade.translation.translator.TextTranslators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.translation.TranslationLlmProvider

class TranslationEngineSettingsVisibilityTest {

    @Test
    fun `offline and Google engines hide API settings`() {
        assertEquals(emptyList<TranslationEngineSetting>(), translationEngineSettings(TextTranslators.MLKIT))
        assertEquals(emptyList<TranslationEngineSetting>(), translationEngineSettings(TextTranslators.GOOGLE))
    }

    @Test
    fun `LLM engines show shared API key and model settings`() {
        val expected = listOf(
            TranslationEngineSetting.LLM_API_KEY,
            TranslationEngineSetting.LLM_MODEL,
        )

        assertEquals(expected, translationEngineSettings(TextTranslators.GEMINI))
        assertEquals(expected, translationEngineSettings(TextTranslators.OPENROUTER))
    }

    @Test
    fun `DeepL shows only its own API key setting`() {
        assertEquals(
            listOf(TranslationEngineSetting.DEEPL_API_KEY),
            translationEngineSettings(TextTranslators.DEEPL),
        )
    }

    @Test
    fun `LLM engines map to independent provider preferences`() {
        assertEquals(TranslationLlmProvider.GEMINI, TextTranslators.GEMINI.llmProvider)
        assertEquals(TranslationLlmProvider.OPENROUTER, TextTranslators.OPENROUTER.llmProvider)
        assertNull(TextTranslators.MLKIT.llmProvider)
        assertNull(TextTranslators.GOOGLE.llmProvider)
        assertNull(TextTranslators.DEEPL.llmProvider)
    }
}
