package eu.kanade.translation.translator

import eu.kanade.translation.recognizer.TextRecognizerLanguage

/**
 * Conservative quality gate for automatic comic translations.
 *
 * It intentionally retries only obvious failures: blank output, unchanged output,
 * or an English -> Portuguese result that still overlaps heavily with the English
 * source. Names and very short phrases are left alone to avoid false positives.
 */
object TranslationQualityGuard {

    fun shouldRetry(
        sourceText: String,
        translatedText: String,
        fromLang: TextRecognizerLanguage,
        toLang: TextTranslatorLanguage,
    ): Boolean {
        val source = normalize(sourceText)
        val target = normalize(translatedText)

        if (source.isEmpty()) return false
        if (target.isEmpty()) return true
        if (fromLang.code.equals(toLang.code, ignoreCase = true)) return false
        if (source.equals(target, ignoreCase = true)) return true

        if (fromLang != TextRecognizerLanguage.ENGLISH || toLang != TextTranslatorLanguage.PORTUGUESE) {
            return false
        }

        val sourceWords = words(source)
        val targetWords = words(target)
        if (sourceWords.size < MIN_WORDS_FOR_LANGUAGE_CHECK || targetWords.size < MIN_WORDS_FOR_LANGUAGE_CHECK) {
            return false
        }

        val sharedWords = targetWords.count { targetWord -> sourceWords.any { it == targetWord } }
        val overlapRatio = sharedWords / targetWords.size.toFloat()
        val englishMarkers = targetWords.count(ENGLISH_MARKERS::contains)
        val portugueseMarkers = targetWords.count(PORTUGUESE_MARKERS::contains)

        return overlapRatio >= MIN_SOURCE_OVERLAP &&
            englishMarkers >= MIN_ENGLISH_MARKERS &&
            portugueseMarkers == 0
    }

    private fun normalize(text: String): String = text
        .trim()
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}'’]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun words(text: String): List<String> = text
        .split(' ')
        .map(String::trim)
        .filter { it.length >= 2 }

    private const val MIN_WORDS_FOR_LANGUAGE_CHECK = 4
    private const val MIN_SOURCE_OVERLAP = 0.65f
    private const val MIN_ENGLISH_MARKERS = 2

    private val ENGLISH_MARKERS = setOf(
        "the", "and", "you", "your", "this", "that", "with", "from", "for", "are", "was", "were",
        "have", "has", "not", "but", "what", "when", "where", "who", "why", "how", "can", "will",
        "would", "should", "could", "there", "here", "they", "them", "their", "our", "his", "her",
    )

    private val PORTUGUESE_MARKERS = setOf(
        "que", "não", "para", "com", "uma", "isso", "isto", "você", "seu", "sua", "meu", "minha",
        "ele", "ela", "eles", "elas", "aqui", "quando", "onde", "como", "porque", "mas", "por", "dos",
        "das", "nos", "nas", "está", "estão", "era", "foi", "ser", "ter", "tem",
    )
}
