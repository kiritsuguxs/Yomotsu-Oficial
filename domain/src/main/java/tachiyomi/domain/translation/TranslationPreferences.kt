package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

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
}
