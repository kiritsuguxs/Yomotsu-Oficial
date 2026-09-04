package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.context.TranslationContext
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GeminiTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
    private val modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {

    private val okHttpClient by lazy {
        Injekt.get<NetworkHelper>().client.newBuilder()
            .readTimeout(GEMINI_REQUEST_TIMEOUT_SECONDS.seconds.toJavaDuration())
            .callTimeout(GEMINI_REQUEST_TIMEOUT_SECONDS.seconds.toJavaDuration())
            .build()
    }
    private val continuity = TranslationContext()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        if (apiKey.isBlank()) throw TranslationProviderFailureMapper.missingKey(TranslationProviderId.GEMINI)
        if (modelName.isBlank()) throw TranslationProviderFailureMapper.missingModel(TranslationProviderId.GEMINI)
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
            val requestBody = buildGeminiRestRequestBody(
                systemInstruction = buildComicTranslationPrompt(fromLang, toLang),
                requestText = requestText,
                maxOutputTokens = maxOutputToken,
            )
            val request = Request.Builder()
                .url(buildGeminiRestUrl(modelName))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).await()
            val responseBody = response.use {
                val rawBody = it.body.string()
                if (!it.isSuccessful) {
                    throw TranslationProviderException(
                        TranslationProviderId.GEMINI,
                        TranslationFailureCategory.UNKNOWN,
                        it.code == 429 || it.code >= 500,
                        buildGeminiHttpDiagnostic(it.code, rawBody, apiKey),
                    )
                }
                rawBody
            }
            val responseJson = JSONObject(responseBody)
            val parts = responseJson.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val responseText = buildString {
                if (parts != null) for (index in 0 until parts.length()) append(parts.optJSONObject(index)?.optString("text").orEmpty())
            }
            if (responseText.isBlank()) {
                throw TranslationProviderFailureMapper.malformedResponse(
                    TranslationProviderId.GEMINI,
                    IllegalStateException("Gemini response contained no text"),
                )
            }

            val resJson = parseComicTranslationResponse(responseText)
            var globalIndex = 0
            for ((k, v) in pages) {
                v.blocks.forEachIndexed { i, b ->
                    val res = resJson.optJSONArray(k)?.optString(i, "NULL")
                    val raw = if (res == null || res == "NULL") b.text else res
                    val finalTranslation = TranslationGlossary.resolve(preparedTexts[globalIndex], raw, context)
                    b.translation = finalTranslation
                    if (cached.getOrNull(globalIndex) == null) TranslationCache.put(context, fromLang, toLang, normalizedTexts[globalIndex], finalTranslation)
                    continuity.remember(b.text, finalTranslation)
                    globalIndex++
                }
                v.blocks = v.blocks.filterNot { it.translation.contains("RTMTH") }.toMutableList()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: TranslationProviderException) {
            logcat(LogPriority.ERROR, error) { "Gemini translation failed: ${error.category}" }
            throw error
        } catch (error: Throwable) {
            val mapped = TranslationProviderFailureMapper.fromThrowable(TranslationProviderId.GEMINI, error)
            logcat(LogPriority.ERROR, mapped) { "Gemini translation failed: ${mapped.category}" }
            if (mapped.category == TranslationFailureCategory.CONNECTION) {
                throw TranslationProviderException(TranslationProviderId.GEMINI, TranslationFailureCategory.CONNECTION, true, buildGeminiNetworkDiagnostic(error, apiKey), error)
            }
            if (mapped.category == TranslationFailureCategory.UNKNOWN) {
                throw TranslationProviderException(TranslationProviderId.GEMINI, TranslationFailureCategory.UNKNOWN, false, buildGeminiUnknownDiagnostic(error, apiKey), error)
            }
            throw mapped
        }
    }

    override fun close() { continuity.clear() }
}

internal fun <T> chunkGeminiItems(items: List<T>, maxItems: Int = GEMINI_MAX_ITEMS_PER_REQUEST): List<List<T>> {
    require(maxItems > 0)
    return items.chunked(maxItems)
}

internal fun buildGeminiRestRequestBody(systemInstruction: String, requestText: String, maxOutputTokens: Int): String = buildJsonObject {
    put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", systemInstruction) }) }) })
    put("contents", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray { add(buildJsonObject { put("text", requestText) }) }) }) })
    put("generationConfig", buildJsonObject { put("maxOutputTokens", maxOutputTokens) })
}.toString()

internal fun buildGeminiRestUrl(modelName: String): String {
    val normalizedModel = modelName.trim().removePrefix("models/")
    return "https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent"
}

internal fun buildGeminiHttpDiagnostic(statusCode: Int, responseBody: String, apiKey: String): String {
    val parsed = runCatching {
        val error = JSONObject(responseBody).optJSONObject("error")
        listOf(error?.optString("status").orEmpty(), error?.optString("message").orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(": ")
    }.getOrDefault("")
    var detail = parsed.ifBlank { responseBody }.replace(apiKey, "[REDACTED]")
    detail = detail.replace(GEMINI_API_KEY_PATTERN, "[REDACTED]").replace(Regex("\\s+"), " ").trim().take(320)
    return if (detail.isBlank()) "Gemini HTTP $statusCode" else "Gemini HTTP $statusCode: $detail"
}

internal fun buildGeminiNetworkDiagnostic(error: Throwable, apiKey: String): String = buildGeminiDiagnostic("Gemini diagnóstico de rede", error, apiKey, true)
internal fun buildGeminiUnknownDiagnostic(error: Throwable, apiKey: String): String = buildGeminiDiagnostic("Gemini diagnóstico", error, apiKey, false)

private fun buildGeminiDiagnostic(prefix: String, error: Throwable, apiKey: String, preferIOException: Boolean): String {
    val causes = generateSequence(error) { it.cause }.toList()
    val detail = if (preferIOException) causes.firstOrNull { it is IOException } ?: causes.lastOrNull() ?: error else causes.lastOrNull() ?: error
    val type = detail::class.java.simpleName.ifBlank { "Throwable" }
    var detailMessage = detail.message.orEmpty()
    if (apiKey.isNotBlank()) detailMessage = detailMessage.replace(apiKey, "[REDACTED]")
    detailMessage = detailMessage.replace(GEMINI_API_KEY_PATTERN, "[REDACTED]").trim().take(220)
    return if (detailMessage.isBlank()) "$prefix: $type" else "$prefix: $type: $detailMessage"
}

private const val GEMINI_REQUEST_TIMEOUT_SECONDS = 30
private const val GEMINI_MAX_ITEMS_PER_REQUEST = 16
private val GEMINI_API_KEY_PATTERN = Regex("AIza[0-9A-Za-z_-]{10,}")
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
