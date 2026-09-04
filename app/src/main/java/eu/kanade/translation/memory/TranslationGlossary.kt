package eu.kanade.translation.memory

import eu.kanade.translation.translator.ComicTranslationContext

data class PreparedGlossaryText internal constructor(
    val originalText: String,
    val textForTranslation: String,
    internal val protectedTerms: List<ProtectedGlossaryTerm>,
)

internal data class ProtectedGlossaryTerm(
    val token: String,
    val target: String,
)

/** Applies glossary and learned corrections as the final translation authority. */
object TranslationGlossary {

    fun apply(
        sourceText: String,
        translatedText: String,
        context: ComicTranslationContext,
    ): String = apply(sourceText, translatedText, TranslationMemory.entries(context))

    internal fun apply(
        sourceText: String,
        translatedText: String,
        entries: List<TranslationMemoryEntry>,
    ): String {
        var result = translatedText
        val source = sourceText.trim()
        if (source.isEmpty()) return result

        val normalizedSource = normalizeForWholeMatch(source)
        entries.firstOrNull { entry ->
            entry.isManualCorrection &&
                normalizedSource == normalizeForWholeMatch(entry.source)
        }?.let { return it.target }

        entries.firstOrNull { entry ->
            !entry.isManualCorrection &&
                normalizedSource == normalizeForWholeMatch(entry.source)
        }?.let { return it.target }

        if (result.isBlank()) return result

        entries.asSequence()
            .filterNot(TranslationMemoryEntry::isManualCorrection)
            .sortedByDescending { it.source.length }
            .forEach { entry ->
                if (!containsTerm(source, entry.source)) return@forEach

                // Whole-block glossary entries are authoritative even when the
                // translator changed punctuation or capitalization.
                if (containsTerm(result, entry.target)) return@forEach

                // Protected entries are normally restored from placeholders.
                // This fallback also covers engines that leave the source term
                // untouched and preserves the safe bounded replacement from Y11.
                result = replaceBounded(result, entry.source, entry.target)
            }
        return result
    }

    /**
     * Replaces protected names/titles/techniques with opaque tokens before an
     * engine sees the text. This makes the feature effective for ML Kit and
     * Google Translate too, not only prompt-based translators.
     */
    fun prepare(
        sourceText: String,
        context: ComicTranslationContext,
    ): PreparedGlossaryText = prepare(sourceText, TranslationMemory.glossaryEntries(context))

    internal fun prepare(
        sourceText: String,
        entries: List<TranslationMemoryEntry>,
    ): PreparedGlossaryText {
        var protectedText = sourceText
        val protectedTerms = mutableListOf<ProtectedGlossaryTerm>()

        entries.asSequence()
            .filter { it.isProtected && !it.isManualCorrection && it.source.isNotBlank() }
            .sortedByDescending { it.source.length }
            .take(MAX_PROTECTED_ENTRIES)
            .forEach { entry ->
                protectedText = boundedRegex(entry.source).replace(protectedText) {
                    val token = protectedToken(protectedTerms.size)
                    protectedTerms += ProtectedGlossaryTerm(token, entry.target)
                    token
                }
            }

        return PreparedGlossaryText(
            originalText = sourceText,
            textForTranslation = protectedText,
            protectedTerms = protectedTerms,
        )
    }

    fun resolve(
        prepared: PreparedGlossaryText,
        translatedText: String,
        context: ComicTranslationContext,
    ): String = resolve(prepared, translatedText, TranslationMemory.entries(context))

    internal fun resolve(
        prepared: PreparedGlossaryText,
        translatedText: String,
        entries: List<TranslationMemoryEntry>,
    ): String {
        var restored = translatedText
        prepared.protectedTerms.forEach { protectedTerm ->
            restored = tolerantTokenRegex(protectedTerm.token).replace(restored) {
                protectedTerm.target
            }
        }
        return apply(prepared.originalText, restored, entries)
    }

    fun instructions(context: ComicTranslationContext): String =
        instructions(TranslationMemory.glossaryEntries(context))

    internal fun instructions(entries: List<TranslationMemoryEntry>): String {
        val glossaryEntries = entries
            .filterNot(TranslationMemoryEntry::isManualCorrection)
            .take(MAX_PROMPT_ENTRIES)
        if (glossaryEntries.isEmpty()) return ""

        return buildString {
            appendLine("Use obrigatoriamente este glossário para esta obra:")
            glossaryEntries.forEach { entry ->
                append("- [")
                append(entry.type.promptLabel())
                if (entry.isProtected) append(" PROTEGIDO")
                append("] ")
                append(entry.source.singleLine())
                append(" => ")
                appendLine(entry.target.singleLine())
            }
            append("Mantenha esses termos consistentes e nunca altere itens marcados como PROTEGIDO.")
        }
    }

    private fun containsTerm(text: String, term: String): Boolean {
        if (term.isBlank()) return false
        return boundedRegex(term).containsMatchIn(text)
    }

    private fun replaceBounded(text: String, source: String, target: String): String {
        if (source.isBlank()) return text
        return boundedRegex(source).replace(text) { target }
    }

    private fun boundedRegex(term: String): Regex {
        val cleanTerm = term.trim()
        val escaped = Regex.escape(cleanTerm)
        val leftBoundary = if (cleanTerm.firstOrNull()?.isLetterOrDigit() == true) {
            "(?<![\\p{L}\\p{N}])"
        } else {
            ""
        }
        val rightBoundary = if (cleanTerm.lastOrNull()?.isLetterOrDigit() == true) {
            "(?![\\p{L}\\p{N}])"
        } else {
            ""
        }
        return Regex("$leftBoundary$escaped$rightBoundary", RegexOption.IGNORE_CASE)
    }

    private fun protectedToken(index: Int): String =
        "YOMOTSUTERM${index.toString().padStart(4, '0')}ZXQ"

    private fun tolerantTokenRegex(token: String): Regex {
        val pattern = token.map { character ->
            Regex.escape(character.toString())
        }.joinToString("[\\s_-]*")
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun normalizeForWholeMatch(text: String): String = text
        .trim()
        .lowercase()
        .replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$"), "")
        .replace(Regex("\\s+"), " ")

    private fun TranslationMemoryEntryType.promptLabel(): String = when (this) {
        TranslationMemoryEntryType.TERM -> "TERMO"
        TranslationMemoryEntryType.NAME -> "NOME"
        TranslationMemoryEntryType.TITLE -> "TÍTULO"
        TranslationMemoryEntryType.TECHNIQUE -> "TÉCNICA"
        TranslationMemoryEntryType.MANUAL_CORRECTION -> "CORREÇÃO"
    }

    private fun String.singleLine(): String = replace(Regex("\\s+"), " ").trim()

    private const val MAX_PROMPT_ENTRIES = 200
    private const val MAX_PROTECTED_ENTRIES = 128
}
