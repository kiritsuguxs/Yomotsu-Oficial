package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.memory.PreparedGlossaryText
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DeepLTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
) : TextTranslator {

    private val okHttpClient = OkHttpClient()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        if (apiKey.isBlank()) throw TranslationProviderFailureMapper.missingKey(TranslationProviderId.DEEPL)
        try {
            translateInternal(pages, context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw TranslationProviderFailureMapper.fromThrowable(TranslationProviderId.DEEPL, error)
        }
    }

    private suspend fun translateInternal(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        val blocks = pages.values.flatMap { it.blocks }
        if (blocks.isEmpty()) return

        val missing = mutableListOf<Triple<Int, String, PreparedGlossaryText>>()
        val resolved = arrayOfNulls<String>(blocks.size)

        blocks.forEachIndexed { index, block ->
            val source = normalizeOcrText(block.text)
            val cached = TranslationCache.get(context, fromLang, toLang, source)
            if (cached != null) {
                resolved[index] = cached
            } else {
                missing += Triple(index, source, TranslationGlossary.prepare(source, context))
            }
        }

        if (missing.isNotEmpty()) {
            val translated = translateTexts(missing.map { it.third.textForTranslation })
            missing.forEachIndexed { translatedIndex, (blockIndex, source, prepared) ->
                val block = blocks[blockIndex]
                val machineTranslation = postEditMachineTranslation(
                    sourceText = block.text,
                    translatedText = translated.getOrElse(translatedIndex) { block.text },
                    targetLanguage = toLang,
                )
                val finalTranslation = TranslationGlossary.resolve(
                    prepared = prepared,
                    translatedText = machineTranslation,
                    context = context,
                )
                resolved[blockIndex] = finalTranslation
                TranslationCache.put(context, fromLang, toLang, source, finalTranslation)
            }
        }

        blocks.forEachIndexed { index, block ->
            val translatedText = resolved[index] ?: block.text
            block.translation = TranslationGlossary.apply(
                sourceText = block.text,
                translatedText = translatedText,
                context = context,
            )
        }
    }

    private suspend fun translateTexts(texts: List<String>): List<String> {
        val result = mutableListOf<String>()
        texts.chunked(MAX_TEXTS_PER_REQUEST).forEach { chunk ->
            val form = FormBody.Builder().apply {
                add("target_lang", deepLTargetLanguage(toLang))
                deepLSourceLanguage(fromLang)?.let { add("source_lang", it) }
                chunk.forEach { add("text", it) }
            }.build()

            val request = Request.Builder()
                .url(endpointFor(apiKey))
                .header("Authorization", "DeepL-Auth-Key $apiKey")
                .post(form)
                .build()

            val response = okHttpClient.newCall(request).await()
            val body = response.use {
                val responseBody = it.body.string()
                if (!it.isSuccessful) {
                    throw TranslationProviderFailureMapper.fromHttp(
                        provider = TranslationProviderId.DEEPL,
                        statusCode = it.code,
                        responseBody = responseBody,
                    )
                }
                responseBody
            }

            val translations = JSONObject(body).getJSONArray("translations")
            for (i in 0 until translations.length()) {
                result += translations.getJSONObject(i).getString("text")
            }
        }
        return result
    }

    private fun endpointFor(key: String): String =
        if (key.endsWith(":fx")) FREE_ENDPOINT else PRO_ENDPOINT

    private fun deepLTargetLanguage(language: TextTranslatorLanguage): String = when (language) {
        TextTranslatorLanguage.PORTUGUESE -> "PT-BR"
        TextTranslatorLanguage.ENGLISH -> "EN-US"
        TextTranslatorLanguage.CHINESESIM -> "ZH-HANS"
        TextTranslatorLanguage.CHINESETRAD -> "ZH-HANT"
        else -> language.code.uppercase()
    }

    private fun deepLSourceLanguage(language: TextRecognizerLanguage): String? = when (language.code.lowercase()) {
        "zh", "zh-cn", "zh-tw" -> "ZH"
        "en" -> "EN"
        "ja" -> "JA"
        "ko" -> "KO"
        "pt" -> "PT"
        "de" -> "DE"
        "fr" -> "FR"
        "es" -> "ES"
        "it" -> "IT"
        "ru" -> "RU"
        else -> null
    }

    override fun close() = Unit

    private companion object {
        const val FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
        const val PRO_ENDPOINT = "https://api.deepl.com/v2/translate"
        const val MAX_TEXTS_PER_REQUEST = 40
    }
}
