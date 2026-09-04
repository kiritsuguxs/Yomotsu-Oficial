package eu.kanade.translation.memory

import eu.kanade.translation.translator.ComicTranslationContext

/**
 * Small UI-facing facade for managing a manga's translation glossary.
 * Keeping mutations here means a future Compose screen does not need to know
 * anything about PreferenceStore or how entries are persisted.
 */
object TranslationGlossaryManager {

    fun list(context: ComicTranslationContext): List<TranslationMemoryEntry> =
        TranslationMemory.glossaryEntries(context)

    fun learnedCorrectionCount(context: ComicTranslationContext): Int =
        TranslationMemory.correctionEntries(context).size

    fun save(
        context: ComicTranslationContext,
        source: String,
        target: String,
        type: TranslationMemoryEntryType = TranslationMemoryEntryType.TERM,
        isProtected: Boolean = false,
    ): GlossarySaveResult {
        val normalizedSource = source.trim()
        val normalizedTarget = target.trim()

        if (normalizedSource.isEmpty()) return GlossarySaveResult.INVALID_SOURCE
        if (normalizedTarget.isEmpty()) return GlossarySaveResult.INVALID_TARGET
        if (!isProtected && normalizedSource.equals(normalizedTarget, ignoreCase = true)) {
            return GlossarySaveResult.SAME_TEXT
        }

        val existed = TranslationMemory.glossaryEntries(context)
            .any { it.source.equals(normalizedSource, ignoreCase = true) }

        TranslationMemory.remember(
            context = context,
            source = normalizedSource,
            target = normalizedTarget,
            type = type,
            isProtected = isProtected,
        )
        return if (existed) GlossarySaveResult.UPDATED else GlossarySaveResult.CREATED
    }

    fun saveMany(
        context: ComicTranslationContext,
        text: String,
        type: TranslationMemoryEntryType = TranslationMemoryEntryType.TERM,
        isProtected: Boolean = false,
    ): GlossaryBatchSaveResult {
        val parsed = parseMany(text, type, isProtected)
        var created = 0
        var updated = 0
        var skipped = 0

        parsed.entries.forEach { entry ->
            when (
                save(
                    context = context,
                    source = entry.source,
                    target = entry.target,
                    type = entry.type,
                    isProtected = entry.isProtected,
                )
            ) {
                GlossarySaveResult.CREATED -> created++
                GlossarySaveResult.UPDATED -> updated++
                else -> skipped++
            }
        }
        return GlossaryBatchSaveResult(
            created = created,
            updated = updated,
            skipped = skipped + parsed.invalidLineNumbers.size,
            invalidLineNumbers = parsed.invalidLineNumbers,
        )
    }

    internal fun parseMany(
        text: String,
        type: TranslationMemoryEntryType,
        isProtected: Boolean,
    ): GlossaryBatchParseResult {
        val entries = mutableListOf<TranslationMemoryEntry>()
        val invalidLineNumbers = mutableListOf<Int>()

        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed

            val pair = splitBulkLine(line) ?: if (isProtected && type == TranslationMemoryEntryType.NAME) {
                line to line
            } else {
                invalidLineNumbers += index + 1
                return@forEachIndexed
            }
            val source = pair.first.trim()
            val target = pair.second.trim()
            if (source.isEmpty() || target.isEmpty() || (!isProtected && source.equals(target, true))) {
                invalidLineNumbers += index + 1
                return@forEachIndexed
            }
            entries += TranslationMemoryEntry(source, target, type, isProtected)
        }

        return GlossaryBatchParseResult(entries, invalidLineNumbers)
    }

    fun remove(context: ComicTranslationContext, source: String) {
        TranslationMemory.forget(context, source)
    }

    fun clear(context: ComicTranslationContext) {
        TranslationMemory.clear(context)
    }
}

enum class GlossarySaveResult {
    CREATED,
    UPDATED,
    INVALID_SOURCE,
    INVALID_TARGET,
    SAME_TEXT,
}

data class GlossaryBatchParseResult(
    val entries: List<TranslationMemoryEntry>,
    val invalidLineNumbers: List<Int>,
)

data class GlossaryBatchSaveResult(
    val created: Int,
    val updated: Int,
    val skipped: Int,
    val invalidLineNumbers: List<Int>,
) {
    val saved: Int
        get() = created + updated
}

private fun splitBulkLine(line: String): Pair<String, String>? {
    BULK_DELIMITERS.forEach { delimiter ->
        val delimiterIndex = line.indexOf(delimiter)
        if (delimiterIndex > 0) {
            return line.substring(0, delimiterIndex) to line.substring(delimiterIndex + delimiter.length)
        }
    }
    return null
}

private val BULK_DELIMITERS = listOf("=>", "->", "\t", "=")
