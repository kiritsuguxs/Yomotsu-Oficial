package eu.kanade.tachiyomi.extension.novel.translation

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.translation.translator.TextTranslators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.translation.TranslationLlmProvider
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object NovelTranslator {

    private val client: OkHttpClient by lazy { Injekt.get<NetworkHelper>().client }
    private val translationCache = ConcurrentHashMap<Long, String>()
    private val translationPreferences: TranslationPreferences by lazy { Injekt.get() }

    fun getCached(chapterId: Long): String? = translationCache[chapterId]

    suspend fun translate(
        chapterId: Long,
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "pt",
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        translationCache[chapterId]?.let { return@withContext it }

        val engineIndex = runCatching { translationPreferences.novelTranslationEngine().get() }.getOrDefault(1)
        val selectedEngine = TextTranslators.entries.getOrNull(engineIndex) ?: TextTranslators.GOOGLE

        val paragraphs = text.split("\n\n")
        val translatedParagraphs = paragraphs.map { paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) {
                ""
            } else if (trimmed.length > 1500) {
                // If a paragraph is too long, split by sentences or single newlines
                trimmed.split("\n").joinToString("\n") { line ->
                    if (line.isBlank()) "" else translateChunk(line.trim(), sourceLang, targetLang, selectedEngine)
                }
            } else {
                translateChunk(trimmed, sourceLang, targetLang, selectedEngine)
            }
        }

        val result = translatedParagraphs.joinToString("\n\n")
        translationCache[chapterId] = result
        result
    }

    private fun translateChunk(
        text: String,
        sl: String,
        tl: String,
        engine: TextTranslators = TextTranslators.GOOGLE,
    ): String {
        return when (engine) {
            TextTranslators.GEMINI -> translateWithGemini(text, tl) ?: translateWithGoogle(text, sl, tl)
            else -> translateWithGoogle(text, sl, tl)
        }
    }

    private fun translateWithGoogle(text: String, sl: String, tl: String): String {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.google.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return text
            val jsonArray = JSONArray(responseBody)
            val sentences = jsonArray.getJSONArray(0)

            buildString {
                for (i in 0 until sentences.length()) {
                    val sentence = sentences.optJSONArray(i)
                    if (sentence != null) {
                        append(sentence.optString(0, ""))
                    }
                }
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with Google: $text" }
            text
        }
    }

    private fun translateWithGemini(text: String, tl: String): String? {
        return try {
            val apiKey = translationPreferences.llmApiKey(TranslationLlmProvider.GEMINI).get()
            if (apiKey.isBlank()) return null

            val modelName = translationPreferences.llmModel(TranslationLlmProvider.GEMINI).get().ifBlank { "gemini-1.5-flash" }
            val normalizedModel = modelName.trim().removePrefix("models/")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent?key=$apiKey"

            val targetLanguageName = if (tl.startsWith("pt", ignoreCase = true)) "Portuguese (Brazil)" else tl
            val systemInstruction = "You are a professional literary translator. Translate the following text into natural $targetLanguageName. Output only the translated text, preserving formatting."

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemInstruction\n\n$text")
                            })
                        })
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            val resObj = JSONObject(responseBody)
            val parts = resObj.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val candidateText = buildString {
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        append(parts.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }
            }.trim()

            if (candidateText.isNotBlank()) candidateText else null
        } catch (e: Exception) {
            logcat(throwable = e) { "Failed to translate chunk with Gemini: $text" }
            null
        }
    }
}
