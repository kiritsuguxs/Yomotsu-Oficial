package tachiyomi.domain.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference

class TranslationLlmPreferencesTest {

    @Test
    fun `Gemini and OpenRouter retain independent API keys and models`() {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())
        val geminiApiKey = preferences.llmApiKey(TranslationLlmProvider.GEMINI)
        val geminiModel = preferences.llmModel(TranslationLlmProvider.GEMINI)
        val openRouterApiKey = preferences.llmApiKey(TranslationLlmProvider.OPENROUTER)
        val openRouterModel = preferences.llmModel(TranslationLlmProvider.OPENROUTER)

        assertNotEquals(geminiApiKey.key(), openRouterApiKey.key())
        assertNotEquals(geminiModel.key(), openRouterModel.key())

        geminiApiKey.set("gemini-key")
        geminiModel.set("gemini-model")
        openRouterApiKey.set("openrouter-key")
        openRouterModel.set("openrouter-model")

        assertEquals("gemini-key", preferences.llmApiKey(TranslationLlmProvider.GEMINI).get())
        assertEquals("gemini-model", preferences.llmModel(TranslationLlmProvider.GEMINI).get())
        assertEquals("openrouter-key", preferences.llmApiKey(TranslationLlmProvider.OPENROUTER).get())
        assertEquals("openrouter-model", preferences.llmModel(TranslationLlmProvider.OPENROUTER).get())
    }

    @Test
    fun `legacy shared values migrate only to the selected provider`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreference("translation_engine_api_key", "legacy-key", ""),
                InMemoryPreference("translation_engine_model", "legacy-model", "gemini-1.5-pro"),
            ),
        )
        val preferences = TranslationPreferences(store)

        preferences.migrateLegacyLlmSettings(TranslationLlmProvider.GEMINI)

        assertEquals("legacy-key", preferences.llmApiKey(TranslationLlmProvider.GEMINI).get())
        assertEquals("legacy-model", preferences.llmModel(TranslationLlmProvider.GEMINI).get())
        assertFalse(preferences.llmApiKey(TranslationLlmProvider.OPENROUTER).isSet())
        assertFalse(preferences.llmModel(TranslationLlmProvider.OPENROUTER).isSet())
    }

    @Test
    fun `legacy migration never overwrites provider values`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreference("translation_engine_api_key", "legacy-key", ""),
                InMemoryPreference("translation_engine_model", "legacy-model", "gemini-1.5-pro"),
                InMemoryPreference("translation_openrouter_api_key", "saved-key", ""),
                InMemoryPreference("translation_openrouter_model", "saved-model", "gemini-1.5-pro"),
            ),
        )
        val preferences = TranslationPreferences(store)

        preferences.migrateLegacyLlmSettings(TranslationLlmProvider.OPENROUTER)

        assertEquals("saved-key", preferences.llmApiKey(TranslationLlmProvider.OPENROUTER).get())
        assertEquals("saved-model", preferences.llmModel(TranslationLlmProvider.OPENROUTER).get())
    }
}
