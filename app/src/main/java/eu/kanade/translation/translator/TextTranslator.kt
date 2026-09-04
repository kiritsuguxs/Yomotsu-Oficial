package eu.kanade.translation.translator

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.translation.TranslationLlmProvider
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Closeable

interface TextTranslator : Closeable {
    val fromLang: TextRecognizerLanguage
    val toLang: TextTranslatorLanguage
    suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext = ComicTranslationContext.EMPTY,
    )
}

data class ComicTranslationContext(
    val mangaTitle: String,
    val chapterName: String,
) {
    companion object {
        val EMPTY = ComicTranslationContext(mangaTitle = "", chapterName = "")
    }
}

enum class TextTranslators(
    val label: String,
    val llmProvider: TranslationLlmProvider? = null,
) {
    MLKIT("MlKit (On Device)"),
    GOOGLE("Google Translate"),
    GEMINI("Gemini AI [API KEY]", TranslationLlmProvider.GEMINI),
    OPENROUTER("OpenRouter [API KEY]", TranslationLlmProvider.OPENROUTER),
    DEEPL("DeepL [API KEY]");

    fun build(
        pref: TranslationPreferences = Injekt.get(),
        fromLang: TextRecognizerLanguage = TextRecognizerLanguage.fromPref(pref.translateFromLanguage()),
        toLang: TextTranslatorLanguage = TextTranslatorLanguage.fromPref(pref.translateToLanguage()),
    ): TextTranslator {
        val delegate = buildRaw(pref, fromLang, toLang)
        val fallback = TranslationFallbackEngine
            .fromValue(pref.translationFallbackEngine().get())
            .resolve(
                primary = this,
                mlKitTargetSupported = toLang in TextTranslatorLanguage.mlkitSupportedLanguages(),
            )
        return ReliableTextTranslator(
            delegate = delegate,
            retryFactory = { buildRaw(pref, fromLang, toLang) },
            fallbackFactory = fallback?.let { engine ->
                { engine.buildRaw(pref, fromLang, toLang) }
            },
        )
    }

    private fun buildRaw(
        pref: TranslationPreferences,
        fromLang: TextRecognizerLanguage,
        toLang: TextTranslatorLanguage,
    ): TextTranslator {
        val maxOutputTokens = pref.translationEngineMaxOutputTokens().get().toIntOrNull() ?: 8914
        val temperature = pref.translationEngineTemperature().get().toFloatOrNull()
            ?.coerceIn(0f, 0.5f)
            ?: 0.3f
        llmProvider?.let(pref::migrateLegacyLlmSettings)
        val modelName = llmProvider?.let { pref.llmModel(it).get() }.orEmpty()
        val apiKey = llmProvider?.let { pref.llmApiKey(it).get() }.orEmpty()
        return when (this) {
            MLKIT -> MLKitTranslator(fromLang, toLang)
            GOOGLE -> GoogleTranslator(fromLang, toLang)
            GEMINI -> GeminiTranslator(fromLang, toLang, apiKey, modelName, maxOutputTokens, temperature)
            OPENROUTER -> OpenRouterTranslator(fromLang, toLang, apiKey, modelName, maxOutputTokens, temperature)
            DEEPL -> DeepLTranslator(fromLang, toLang, pref.deepLApiKey().get())
        }
    }

    companion object {
        fun fromPref(pref: Preference<Int>): TextTranslators {
            val translator = entries.getOrNull(pref.get())
            if (translator == null) {
                pref.set(0)
                return MLKIT
            }
            return translator
        }
    }
}
