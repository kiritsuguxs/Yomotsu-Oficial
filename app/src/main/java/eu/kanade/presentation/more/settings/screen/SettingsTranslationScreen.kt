package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.recognizer.OcrEngineType
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import eu.kanade.translation.translator.TranslationFallbackEngine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal enum class TranslationEngineSetting {
    LLM_API_KEY,
    LLM_MODEL,
    DEEPL_API_KEY,
}

internal fun translationEngineSettings(engine: TextTranslators): List<TranslationEngineSetting> {
    return when (engine) {
        TextTranslators.GEMINI,
        TextTranslators.OPENROUTER,
        -> listOf(
            TranslationEngineSetting.LLM_API_KEY,
            TranslationEngineSetting.LLM_MODEL,
        )
        TextTranslators.DEEPL -> listOf(TranslationEngineSetting.DEEPL_API_KEY)
        TextTranslators.MLKIT,
        TextTranslators.GOOGLE,
        -> emptyList()
    }
}

object SettingsTranslationScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ATMR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = translationPreferences.translateSoundEffects(),
                title = stringResource(ATMR.strings.pref_translate_sound_effects),
                subtitle = stringResource(ATMR.strings.pref_translate_sound_effects_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationFont(),
                title = stringResource(ATMR.strings.pref_reader_font),
                entries = TranslationFont.selectableEntries
                    .associate { it.preferenceValue to it.label }
                    .toImmutableMap(),
            ),
            getTranslationLangGroup(translationPreferences),
            getTranslatioEngineGroup(translationPreferences),
            getOcrEngineGroup(translationPreferences),
            getTranslatioAdvancedGroup(translationPreferences),
        )
    }

    @Composable
    private fun getOcrEngineGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(ATMR.strings.pref_group_ocr),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.ocrEngine(),
                    title = stringResource(ATMR.strings.pref_ocr_engine),
                    entries = OcrEngineType.entries
                        .associate { it.preferenceValue to getOcrEngineLabel(it) }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = translationPreferences.dbnetExperimental(),
                    title = "Detector DBNet (experimental)",
                    subtitle = "Somente inglês/ARM64. Primeiro uso baixa 153 MB separados do APK. Reconhecimento ML Kit de página inteira; em falha, usa o OCR selecionado acima.",
                ),
            ),
        )
    }

    @Composable
    private fun getTranslationLangGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val fromLangs = TextRecognizerLanguage.entries
        val toLangs = TextTranslatorLanguage.entries
        return Preference.PreferenceGroup(
            title = stringResource(ATMR.strings.pref_group_setup),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translateFromLanguage(),
                    title = stringResource(ATMR.strings.pref_translate_from),
                    entries = fromLangs.associate { it.name to getSourceLanguageLabel(it) }.toImmutableMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translateToLanguage(),
                    title = stringResource(ATMR.strings.pref_translate_to),
                    entries = toLangs.associate { it.name to it.label }.toImmutableMap(),
                ),
            ),
        )
    }

    @Composable
    private fun getTranslatioEngineGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        val engines = TextTranslators.entries
        val enginePreference = translationPreferences.translationEngine()
        val selectedEngineValue by enginePreference.collectAsState()
        val selectedEngine = engines.getOrNull(selectedEngineValue) ?: TextTranslators.MLKIT
        val engineSettings = translationEngineSettings(selectedEngine)
        val items = buildList<Preference.PreferenceItem<out Any, out Any>> {
            add(
                Preference.PreferenceItem.ListPreference(
                    preference = enginePreference,
                    title = stringResource(ATMR.strings.pref_translator_engine),
                    entries = engines.withIndex()
                        .associate { it.index to getTranslatorLabel(it.value) }
                        .toImmutableMap(),
                ),
            )
            add(
                Preference.PreferenceItem.ListPreference(
                    preference = translationPreferences.translationFallbackEngine(),
                    title = stringResource(ATMR.strings.pref_translation_fallback),
                    entries = TranslationFallbackEngine.entries
                        .associate { it.preferenceValue to getFallbackLabel(it) }
                        .toImmutableMap(),
                ),
            )
            if (TranslationEngineSetting.LLM_API_KEY in engineSettings) {
                add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.translationEngineApiKey(),
                        subtitle = stringResource(ATMR.strings.pref_sub_engine_api_key),
                        title = stringResource(ATMR.strings.pref_engine_api_key),
                    ),
                )
            }
            if (TranslationEngineSetting.LLM_MODEL in engineSettings) {
                add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.translationEngineModel(),
                        title = stringResource(ATMR.strings.pref_engine_model),
                    ),
                )
            }
            if (TranslationEngineSetting.DEEPL_API_KEY in engineSettings) {
                add(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = translationPreferences.deepLApiKey(),
                        subtitle = "Chave da API DeepL (Free ou Pro)",
                        title = "DeepL API Key",
                    ),
                )
            }
        }
        return Preference.PreferenceGroup(
            title = stringResource(ATMR.strings.pref_group_engine),
            preferenceItems = items,
        )
    }

    @Composable
    private fun getTranslatioAdvancedGroup(
        translationPreferences: TranslationPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(ATMR.strings.pref_group_advanced),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineTemperature(),
                    title = stringResource(ATMR.strings.pref_engine_temperature),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = translationPreferences.translationEngineMaxOutputTokens(),
                    title = stringResource(ATMR.strings.pref_engine_max_output),
                ),
            ),
        )
    }

    @Composable
    private fun getSourceLanguageLabel(language: TextRecognizerLanguage): String {
        return when (language) {
            TextRecognizerLanguage.CHINESE -> stringResource(ATMR.strings.language_chinese)
            TextRecognizerLanguage.JAPANESE -> stringResource(ATMR.strings.language_japanese)
            TextRecognizerLanguage.KOREAN -> stringResource(ATMR.strings.language_korean)
            TextRecognizerLanguage.ENGLISH -> stringResource(ATMR.strings.language_english)
        }
    }

    @Composable
    private fun getTranslatorLabel(translator: TextTranslators): String {
        return when (translator) {
            TextTranslators.MLKIT -> stringResource(ATMR.strings.translator_mlkit)
            TextTranslators.GOOGLE -> stringResource(ATMR.strings.translator_google)
            TextTranslators.GEMINI -> stringResource(ATMR.strings.translator_gemini)
            TextTranslators.OPENROUTER -> stringResource(ATMR.strings.translator_openrouter)
            TextTranslators.DEEPL -> "DeepL"
        }
    }

    @Composable
    private fun getFallbackLabel(fallback: TranslationFallbackEngine): String {
        return when (fallback) {
            TranslationFallbackEngine.NONE -> stringResource(ATMR.strings.translation_fallback_none)
            TranslationFallbackEngine.ML_KIT -> stringResource(ATMR.strings.translator_mlkit)
            TranslationFallbackEngine.GOOGLE -> stringResource(ATMR.strings.translator_google)
        }
    }

    @Composable
    private fun getOcrEngineLabel(engine: OcrEngineType): String {
        return when (engine) {
            OcrEngineType.ML_KIT -> stringResource(ATMR.strings.ocr_mlkit)
            OcrEngineType.PADDLE_OCR -> stringResource(ATMR.strings.ocr_paddle_english)
        }
    }
}
