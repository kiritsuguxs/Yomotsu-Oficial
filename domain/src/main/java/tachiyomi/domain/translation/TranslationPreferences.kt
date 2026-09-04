package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

enum class TranslationLlmProvider(
    val preferenceKey: String,
    val defaultModel: String,
) {
    GEMINI("gemini", "gemini-1.5-pro"),
    OPENROUTER("openrouter", "gemini-1.5-pro"),
}

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    private val llmApiKeys = TranslationLlmProvider.entries.associateWith { provider ->
        preferenceStore.getString("translation_${provider.preferenceKey}_api_key", "")
    }
    private val llmModels = TranslationLlmProvider.entries.associateWith { provider ->
        preferenceStore.getString("translation_${provider.preferenceKey}_model", provider.defaultModel)
    }

    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)
    fun autoTranslateManga(mangaId: Long) =
        preferenceStore.getBoolean("auto_translate_manga_$mangaId", false)
    fun translateSoundEffects() = preferenceStore.getBoolean("translate_sound_effects", false)
    fun translateFromLanguage() = preferenceStore.getString("translate_language_from", "ENGLISH")
    fun translateToLanguage() = preferenceStore.getString("translate_language_to", "PORTUGUESE")
    fun translationFont() = preferenceStore.getInt("translation_font", 0)

    // Stable OCR values: 0 = ML Kit, 1 = PaddleOCR.
    fun dbnetExperimental() = preferenceStore.getBoolean("dbnet_experimental", false)

    fun ocrEngine() = preferenceStore.getInt("ocr_engine", 0)

    fun translationEngine() = preferenceStore.getInt("translation_engine", 0)
    fun translationFallbackEngine() = preferenceStore.getInt("translation_fallback_engine", 0)
    fun translationEngineModel() = preferenceStore.getString("translation_engine_model", "gemini-1.5-pro")
    fun translationEngineApiKey() = preferenceStore.getString("translation_engine_api_key", "")
    fun translationEngineTemperature() = preferenceStore.getString("translation_engine_temperature", "0.3")
    fun translationEngineMaxOutputTokens() = preferenceStore.getString("translation_engine_output_tokens", "8192")

    // DeepL uses its own authentication and translation endpoint rather than an LLM API.
    fun deepLApiKey() = preferenceStore.getString("deepl_api_key", "")

    fun llmApiKey(provider: TranslationLlmProvider) = llmApiKeys.getValue(provider)

    fun llmModel(provider: TranslationLlmProvider) = llmModels.getValue(provider)

    fun migrateLegacyLlmSettings(provider: TranslationLlmProvider) {
        val providerApiKey = llmApiKeys.getValue(provider)
        val legacyApiKey = translationEngineApiKey()
        if (!providerApiKey.isSet() && legacyApiKey.isSet()) {
            providerApiKey.set(legacyApiKey.get())
        }

        val providerModel = llmModels.getValue(provider)
        val legacyModel = translationEngineModel()
        if (!providerModel.isSet() && legacyModel.isSet()) {
            providerModel.set(legacyModel.get())
        }
    }
}
