package eu.kanade.presentation.more.settings.screen

import eu.kanade.translation.translator.TextTranslators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
