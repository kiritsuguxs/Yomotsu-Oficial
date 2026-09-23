package eu.kanade.tachiyomi.extension.novel.translation

import android.app.Application
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
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object NovelTranslator {

    private val client: OkHttpClient by lazy { Injekt.get<NetworkHelper>().client }
    private val translationCache = ConcurrentHashMap<Long, String>()
    private val translationPreferences: TranslationPreferences by lazy { Injekt.get() }
    private val context: Application by lazy { Injekt.get() }
    private val cacheDir by lazy { File(context.cacheDir, "novel_translations").apply { mkdirs() } }

    fun getCached(chapterId: Long): String? {
        translationCache[chapterId]?.let { return it }
        val file = File(cacheDir, "$chapterId.txt")
        if (file.exists()) {
            val text = file.readText()
            translationCache[chapterId] = text
            return text
        }
        return null
    }

    suspend fun translate(
        chapterId: Long,
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "pt",
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        getCached(chapterId)?.let { return@withContext it }

        val engineIndex = runCatching { translationPreferences.novelTranslationEngine().get() }.getOrDefault(1)
        val selectedEngine = TextTranslators.entries.getOrNull(engineIndex) ?: TextTranslators.GOOGLE

        val result = if (selectedEngine == TextTranslators.GEMINI) {
            // O Gemini aguenta textos gigantescos de uma só vez
            translateChunk(text, sourceLang, targetLang, selectedEngine)
        } else {
            // O Google Tradutor precisa agrupar parágrafos em blocos grandes para ser rápido e não ser banido
            val chunks = chunkText(text, 3000)
            val translatedChunks = chunks.map { chunk ->
                if (chunk.isBlank()) "" else translateChunk(chunk, sourceLang, targetLang, selectedEngine)
            }
            translatedChunks.joinToString("\n\n")
        }

        translationCache[chapterId] = result
        File(cacheDir, "$chapterId.txt").writeText(result)
        result
    }

    private fun chunkText(text: String, maxLength: Int): List<String> {
        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (p in paragraphs) {
            if (currentChunk.length + p.length > maxLength && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            currentChunk.append(p).append("\n\n")
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks
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
