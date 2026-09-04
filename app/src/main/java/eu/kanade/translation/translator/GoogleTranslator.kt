package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tachiyomi.core.common.util.system.logcat
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class GoogleTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
) : TextTranslator {
    private val client1 = "gtx"
    private val okHttpClient = OkHttpClient()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        val sequentialBlocks = pages.values.flatMap { it.blocks }
        if (sequentialBlocks.isEmpty()) return

        val normalizedTexts = sequentialBlocks.map { normalizeOcrText(it.text) }
        val preparedTexts = normalizedTexts.map { TranslationGlossary.prepare(it, context) }
        val cached = normalizedTexts.map { TranslationCache.get(context, fromLang, toLang, it) }

        // Google groups nearby bubbles to preserve context. If an entire batch is
        // cached we skip the network completely. Otherwise the original full
        // context is preserved so cache hits never degrade neighboring dialogue.
        val translations = if (cached.all { it != null }) {
            cached.map { it.orEmpty() }
        } else {
            translateBlocksWithContext(
                texts = preparedTexts.map { it.textForTranslation },
                sourceTextsForPostEdit = normalizedTexts,
            )
        }

        sequentialBlocks.forEachIndexed { index, block ->
            val finalTranslation = cached[index] ?: TranslationGlossary.resolve(
                prepared = preparedTexts[index],
                translatedText = translations[index],
                context = context,
            )
            block.translation = TranslationGlossary.apply(
                sourceText = block.text,
                translatedText = finalTranslation,
                context = context,
            )
            if (cached[index] == null) {
                TranslationCache.put(
                    context = context,
                    fromLang = fromLang,
                    toLang = toLang,
                    source = normalizedTexts[index],
                    target = block.translation,
                )
            }
        }
    }

    private suspend fun translateBlocksWithContext(
        texts: List<String>,
        sourceTextsForPostEdit: List<String> = texts,
    ): List<String> {
        val normalizedTexts = texts.map(::normalizeOcrText)
        val translations = MutableList(normalizedTexts.size) { "" }
        val chunks = buildMachineTranslationChunks(
            texts = normalizedTexts,
            maxCharacters = GOOGLE_CONTEXT_MAX_CHARACTERS,
            maxItems = GOOGLE_CONTEXT_MAX_ITEMS,
        )

        chunks.forEach { chunk ->
            val contextualInput = buildMarkedMachineTranslationText(chunk)
            val contextualResult = runCatching {
                translateText(
                    sourceLang = fromLang.code,
                    targetLang = toLang.translationApiCode(),
                    text = contextualInput,
                )
            }.getOrNull()
            val parsedTranslations = contextualResult?.let {
                parseMarkedMachineTranslations(it, chunk)
            }

            if (parsedTranslations != null) {
                parsedTranslations.forEach { (index, value) -> translations[index] = value }
            } else {
                chunk.forEach { item ->
                    translations[item.index] = translateText(
                        sourceLang = fromLang.code,
                        targetLang = toLang.translationApiCode(),
                        text = item.value,
                    )
                }
            }
        }

        findSuspiciousDuplicateTranslationIndices(normalizedTexts, translations).forEach { index ->
            val individualTranslation = runCatching {
                translateText(
                    sourceLang = fromLang.code,
                    targetLang = toLang.translationApiCode(),
                    text = normalizedTexts[index],
                )
            }.getOrNull()
            if (!individualTranslation.isNullOrBlank()) {
                translations[index] = individualTranslation
            }
        }

        return translations.mapIndexed { index, translated ->
            val finalTranslation = translated.takeIf { it.isNotBlank() } ?: normalizedTexts[index]
            postEditMachineTranslation(
                sourceText = sourceTextsForPostEdit[index],
                translatedText = finalTranslation,
                targetLanguage = toLang,
            )
        }
    }

    private suspend fun translateText(sourceLang: String, targetLang: String, text: String): String {
        val access = getTranslateUrl(sourceLang, targetLang, text)
        val build: Request = Request.Builder().url(access).build()
        val newCall = okHttpClient.newCall(build)
        val response = newCall.await()
        val responseText = response.use {
            check(it.isSuccessful) { "Google Translate HTTP ${it.code}" }
            it.body.string()
        }
        try {
            val segments = JSONArray(responseText).getJSONArray(0)
            return buildString {
                for (index in 0 until segments.length()) {
                    val segment = segments.optJSONArray(index) ?: continue
                    append(segment.optString(0, ""))
                }
            }.trim()
        } catch (e: Exception) {
            logcat { "Image Translation Error : $e" }
        }
        return ""
    }

    private fun getTranslateUrl(sourceLang: String, targetLang: String, text: String): String {
        try {
            val client = client1
            val calculateToken = calculateToken(text)
            val encode: String = URLEncoder.encode(text, "utf-8")
            return "https://translate.google.com/translate_a/single?client=$client&sl=$sourceLang&tl=$targetLang&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk=$calculateToken&q=$encode"
        } catch (unused: UnsupportedEncodingException) {
            val calculateToken2 = calculateToken(text)
            return "https://translate.google.com/translate_a/single?client=$client1&sl=$sourceLang&tl=$targetLang&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk=$calculateToken2&q=$text"
        }
    }

    private fun calculateToken(str: String): String {
        val list = mutableListOf<Int>()
        var i = 0

        while (i < str.length) {
            val charCodeAt = str.codePointAt(i)
            when {
                charCodeAt < 128 -> list.add(charCodeAt)
                charCodeAt < 2048 -> {
                    list.add((charCodeAt shr 6) or 192)
                    list.add((charCodeAt and 63) or 128)
                }
                charCodeAt in 55296..57343 && i + 1 < str.length -> {
                    val nextChar = str.codePointAt(i + 1)
                    if (nextChar in 56320..57343) {
                        val codePoint = ((charCodeAt and 1023) shl 10) + (nextChar and 1023) + 65536
                        list.add((codePoint shr 18) or 240)
                        list.add(((codePoint shr 12) and 63) or 128)
                        list.add(((codePoint shr 6) and 63) or 128)
                        list.add((codePoint and 63) or 128)
                        i++
                    }
                }
                else -> {
                    list.add((charCodeAt shr 12) or 224)
                    list.add(((charCodeAt shr 6) and 63) or 128)
                    list.add((charCodeAt and 63) or 128)
                }
            }
            i++
        }

        var j: Long = 406644
        for (num in list) {
            j = RL(j + num.toLong(), "+-a^+6")
        }
        var rL = RL(j, "+-3^+b+-f") xor 3293161072L
        if (rL < 0) {
            rL = (rL and 2147483647L) + 2147483648L
        }
        val j2 = rL % 1000000L
        return "$j2.${406644L xor j2}"
    }

    private fun RL(j: Long, str: String): Long {
        var result = j
        var i = 0
        while (i < str.length - 2) {
            val shift = if (str[i + 2] in 'a'..'z') str[i + 2].code - 'W'.code else str[i + 2].digitToInt()
            val shiftValue = if (str[i + 1] == '+') result ushr shift else result shl shift
            result = if (str[i] == '+') (result + shiftValue) and 4294967295L else result xor shiftValue
            i += 3
        }
        return result
    }

    override fun close() {
    }

    private companion object {
        const val GOOGLE_CONTEXT_MAX_CHARACTERS = 1_800
        const val GOOGLE_CONTEXT_MAX_ITEMS = 10
    }
}
