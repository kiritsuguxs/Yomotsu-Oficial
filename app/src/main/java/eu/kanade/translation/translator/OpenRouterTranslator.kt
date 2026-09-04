package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.context.TranslationContext
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OpenRouterTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    val apiKey: String,
    val modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {
    private val okHttpClient = OkHttpClient()
    private val continuity = TranslationContext()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        if (apiKey.isBlank()) throw TranslationProviderFailureMapper.missingKey(TranslationProviderId.OPENROUTER)
        if (modelName.isBlank()) throw TranslationProviderFailureMapper.missingModel(TranslationProviderId.OPENROUTER)
        try {
            translateInternal(pages, context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val mapped = TranslationProviderFailureMapper.fromThrowable(TranslationProviderId.OPENROUTER, error)
            logcat { "OpenRouter translation failed: ${mapped.category}" }
            throw mapped
        }
    }

    private suspend fun translateInternal(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        try {
            continuity.enter(context)
            val blocks = pages.values.flatMap { it.blocks }
            if (blocks.isEmpty()) return
            val normalizedTexts = blocks.map { normalizeOcrText(it.text) }
            val preparedTexts = normalizedTexts.map { TranslationGlossary.prepare(it, context) }
            val cached = normalizedTexts.map { TranslationCache.get(context, fromLang, toLang, it) }

            if (cached.all { it != null }) {
                blocks.forEachIndexed { index, block ->
                    block.translation = TranslationGlossary.apply(block.text, cached[index].orEmpty(), context)
                    continuity.remember(block.text, block.translation)
                }
                pages.values.forEach { page ->
                    page.blocks = page.blocks.filterNot { it.translation.contains("RTMTH") }.toMutableList()
                }
                return
            }

            val preparedIterator = preparedTexts.iterator()
            val inputJson = buildComicInputJson(pages) {
                preparedIterator.next().textForTranslation
            }
            val requestText = continuity.promptPrefix() + buildComicTranslationRequest(context, inputJson)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val jsonObject = buildJsonObject {
                put("model", modelName)
                putJsonObject("response_format") { put("type", "json_object") }
                put("top_p", 0.5f)
                put("top_k", 30)
                put("temperature", temp)
                put("max_tokens", maxOutputToken)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", buildComicTranslationPrompt(fromLang, toLang))
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", requestText)
                    }
                }
            }.toString()

            val body = jsonObject.toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            val response = okHttpClient.newCall(request).await()
            val responseBody = response.use {
                val rawBody = it.body.string()
                if (!it.isSuccessful) {
                    throw TranslationProviderFailureMapper.fromHttp(
                        provider = TranslationProviderId.OPENROUTER,
                        statusCode = it.code,
                        responseBody = rawBody,
                    )
                }
                rawBody
            }
            val json = JSONObject(responseBody)
            val resJson = parseComicTranslationResponse(
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content"),
            )

            var globalIndex = 0
            for ((pageKey, page) in pages) {
                page.blocks.forEachIndexed { index, block ->
                    val res = resJson.optJSONArray(pageKey)?.optString(index, "NULL")
                    val raw = if (res == null || res == "NULL") block.text else res
                    val finalTranslation = TranslationGlossary.resolve(
                        prepared = preparedTexts[globalIndex],
                        translatedText = raw,
                        context = context,
                    )
                    block.translation = finalTranslation
                    if (cached.getOrNull(globalIndex) == null) {
                        TranslationCache.put(context, fromLang, toLang, normalizedTexts[globalIndex], finalTranslation)
                    }
                    continuity.remember(block.text, finalTranslation)
                    globalIndex++
                }
                page.blocks = page.blocks.filterNot { it.translation.contains("RTMTH") }.toMutableList()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw error
        }
    }

    override fun close() {
        continuity.clear()
    }
}
