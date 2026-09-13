package eu.kanade.tachiyomi.extension.novel.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tachiyomi.core.common.util.system.logcat
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object NovelTranslator {

    private val client: OkHttpClient by lazy { Injekt.get<NetworkHelper>().client }
    private val translationCache = ConcurrentHashMap<Long, String>()

    fun getCached(chapterId: Long): String? = translationCache[chapterId]

    suspend fun translate(
        chapterId: Long,
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "pt",
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        translationCache[chapterId]?.let { return@withContext it }

        val paragraphs = text.split("\n\n")
        val translatedParagraphs = paragraphs.map { paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) {
                ""
            } else if (trimmed.length > 1500) {
                // If a paragraph is too long, split by sentences or single newlines
                trimmed.split("\n").joinToString("\n") { line ->
                    if (line.isBlank()) "" else translateChunk(line.trim(), sourceLang, targetLang)
                }
            } else {
                translateChunk(trimmed, sourceLang, targetLang)
            }
        }

        val result = translatedParagraphs.joinToString("\n\n")
        translationCache[chapterId] = result
        result
    }

    private fun translateChunk(text: String, sl: String, tl: String): String {
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
            logcat(throwable = e) { "Failed to translate chunk: $text" }
            text
        }
    }
}
