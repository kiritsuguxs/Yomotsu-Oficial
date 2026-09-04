package eu.kanade.translation.translator

import java.util.Locale

private const val MARKER_PREFIX = "__YMX_BLOCK_"
private const val MARKER_SUFFIX = "__"
private const val MIN_SUSPICIOUS_TRANSLATION_LETTERS = 12
private const val MIN_SUSPICIOUS_TRANSLATION_WORDS = 2
private val markerRegex = Regex("${MARKER_PREFIX}(\\d{4})${MARKER_SUFFIX}", RegexOption.IGNORE_CASE)
private val comparisonNoiseRegex = Regex("[^\\p{L}\\p{N}]+")

internal fun buildMachineTranslationChunks(
    texts: List<String>,
    maxCharacters: Int,
    maxItems: Int,
): List<List<IndexedValue<String>>> {
    require(maxCharacters > 0)
    require(maxItems > 0)

    val chunks = mutableListOf<List<IndexedValue<String>>>()
    var currentChunk = mutableListOf<IndexedValue<String>>()
    var currentLength = 0

    texts.forEachIndexed { index, text ->
        val normalized = normalizeOcrText(text)
        val itemLength = machineTranslationMarker(index).length + normalized.length + 2
        val mustStartNextChunk = currentChunk.isNotEmpty() &&
            (currentChunk.size >= maxItems || currentLength + itemLength > maxCharacters)

        if (mustStartNextChunk) {
            chunks += currentChunk
            currentChunk = mutableListOf()
            currentLength = 0
        }

        currentChunk += IndexedValue(index, normalized)
        currentLength += itemLength
    }

    if (currentChunk.isNotEmpty()) {
        chunks += currentChunk
    }
    return chunks
}

internal fun buildMarkedMachineTranslationText(items: List<IndexedValue<String>>): String {
    return items.joinToString(separator = "\n") { item ->
        "${machineTranslationMarker(item.index)}\n${item.value}"
    }
}

internal fun parseMarkedMachineTranslations(
    translatedText: String,
    expectedItems: List<IndexedValue<String>>,
): Map<Int, String>? {
    val matches = markerRegex.findAll(translatedText).toList()
    if (matches.size != expectedItems.size) return null

    val translations = linkedMapOf<Int, String>()
    matches.forEachIndexed { position, match ->
        val expectedIndex = expectedItems[position].index
        val actualIndex = match.groupValues[1].toIntOrNull() ?: return null
        if (actualIndex != expectedIndex) return null

        val valueStart = match.range.last + 1
        val valueEnd = matches.getOrNull(position + 1)?.range?.first ?: translatedText.length
        val translatedValue = translatedText.substring(valueStart, valueEnd).trim()
        if (translatedValue.isBlank()) return null
        translations[actualIndex] = translatedValue
    }
    return translations
}

internal fun findSuspiciousDuplicateTranslationIndices(
    sourceTexts: List<String>,
    translations: List<String>,
): Set<Int> {
    require(sourceTexts.size == translations.size)

    return translations.indices
        .groupBy { index -> normalizeForMachineTranslationComparison(translations[index]) }
        .filterKeys { translation -> isSubstantiveMachineTranslation(translation) }
        .values
        .filter { indices ->
            indices.size > 1 &&
                indices
                    .map { index -> normalizeForMachineTranslationComparison(sourceTexts[index]) }
                    .distinct()
                    .size > 1
        }
        .flatten()
        .toSet()
}

private fun normalizeForMachineTranslationComparison(text: String): String {
    return normalizeOcrText(text)
        .lowercase(Locale.ROOT)
        .replace(comparisonNoiseRegex, " ")
        .trim()
}

private fun isSubstantiveMachineTranslation(text: String): Boolean {
    val letterCount = text.count(Char::isLetter)
    val wordCount = text.split(' ').count(String::isNotBlank)
    return letterCount >= MIN_SUSPICIOUS_TRANSLATION_LETTERS &&
        wordCount >= MIN_SUSPICIOUS_TRANSLATION_WORDS
}

private fun machineTranslationMarker(index: Int): String {
    return "$MARKER_PREFIX${index.toString().padStart(4, '0')}$MARKER_SUFFIX"
}
