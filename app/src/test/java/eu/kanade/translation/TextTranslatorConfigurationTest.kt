package eu.kanade.translation

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.translation.TranslationPreferences

class TextTranslatorConfigurationTest {

    @Test
    fun `changing fallback engine invalidates cached translator configuration`() {
        val preferences = mockk<TranslationPreferences>()
        every { preferences.translationEngine() } returns intPreference(1)
        every { preferences.translationFallbackEngine() } returns intPreference(0, 2)
        every { preferences.translationEngineModel() } returns stringPreference("model")
        every { preferences.translationEngineApiKey() } returns stringPreference("key")
        every { preferences.translationEngineTemperature() } returns stringPreference("0.3")
        every { preferences.translationEngineMaxOutputTokens() } returns stringPreference("8192")
        every { preferences.deepLApiKey() } returns stringPreference("deepl-key")

        val before = TextTranslatorConfiguration.from(
            preferences,
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
        )
        val after = TextTranslatorConfiguration.from(
            preferences,
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
        )

        assertNotEquals(before, after)
    }

    private fun intPreference(vararg values: Int): Preference<Int> = mockk {
        every { get() } returnsMany values.toList()
    }

    private fun stringPreference(value: String): Preference<String> = mockk {
        every { get() } returns value
    }
}
