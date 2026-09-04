package eu.kanade.translation.context

import eu.kanade.translation.translator.ComicTranslationContext

/**
 * Small continuity memory for AI translators.
 *
 * Entries are scoped to the current manga + chapter so dialogue from another
 * chapter or title can never leak into the next translation. Google Translate
 * and ML Kit keep their existing grouped-context implementation and do not use
 * this experimental continuity memory.
 */
data class TranslationContextEntry(
    val source: String,
    val translation: String,
)

class TranslationContext(
    private val maxEntries: Int = 3,
) {
    private var scopeKey: String = ""
    private val entries = ArrayDeque<TranslationContextEntry>()

    fun enter(context: ComicTranslationContext) {
        val newScopeKey = listOf(context.mangaTitle.trim(), context.chapterName.trim())
            .joinToString("\u0000")
        if (scopeKey != newScopeKey) {
            scopeKey = newScopeKey
            entries.clear()
        }
    }

    fun remember(source: String, translation: String) {
        val cleanSource = source.trim()
        val cleanTranslation = translation.trim()
        if (cleanSource.isEmpty() || cleanTranslation.isEmpty()) return

        entries.removeAll { it.source == cleanSource }
        entries.addLast(TranslationContextEntry(cleanSource, cleanTranslation))
        while (entries.size > maxEntries) entries.removeFirst()
    }

    fun snapshot(): List<TranslationContextEntry> = entries.toList()

    fun promptPrefix(): String {
        if (entries.isEmpty()) return ""
        return buildString {
            appendLine("CONTINUITY CONTEXT (reference only; do not translate or return these lines):")
            entries.forEachIndexed { index, entry ->
                appendLine("${index + 1}. Source: ${sanitize(entry.source)}")
                appendLine("   Previous translation: ${sanitize(entry.translation)}")
            }
            appendLine("Use this only to resolve pronouns, speaker intent, terminology and dialogue continuity.")
            appendLine("Translate only the NEW chapter JSON that follows and keep its exact output structure.")
            appendLine()
        }
    }

    fun clear() {
        scopeKey = ""
        entries.clear()
    }

    private fun sanitize(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(500)
}
