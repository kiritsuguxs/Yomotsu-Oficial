package eu.kanade.tachiyomi.extension.novel.tts

object NovelSpeechSanitizer {

    private val TRANSLATOR_NOTE_BRACKETS = Regex(
        """\[\s*(?:nt|n/t|nota(?:\s+do\s+tradutor|\s+da\s+scan)?|tl|t/n|ed|editor|tn)[\s:][^\]]*\]""",
        RegexOption.IGNORE_CASE,
    )

    private val TRANSLATOR_NOTE_PARENS = Regex(
        """\(\s*(?:nt|n/t|nota(?:\s+do\s+tradutor|\s+da\s+scan)?|tl|t/n|ed|editor|tn)[\s:][^\)]*\)""",
        RegexOption.IGNORE_CASE,
    )

    private val VISUAL_DIVIDERS = Regex("""(?:[-*_~=–—#]\s*){3,}""")

    private val REPEATED_DOTS = Regex("""\.{2,}|…+""")

    private val REPEATED_EXCLAMATION = Regex("""!{2,}""")

    private val REPEATED_QUESTION = Regex("""\?{2,}""")

    private val ASIAN_OPEN_QUOTES = Regex("""[「『《〈【]""")
    private val ASIAN_CLOSE_QUOTES = Regex("""[」』》〉】]""")
    private val EUROPEAN_QUOTES = Regex("""[«»]""")

    private val MARKDOWN_BOLD_STRIKE = Regex("""\*\*([^*]+)\*\*|__([^_]+)__|~~([^~]+)~~""")
    private val MARKDOWN_ITALIC = Regex("""\*([^*]+)\*|_([^_]+)_""")

    private val DECORATIVE_SYMBOLS = Regex("""[★☆◆◇▲△▼▽♪♫♥♡➔→←▶◀✓✔✕✖•◦§]""")

    private val MULTI_SPACE = Regex("""[^\S\r\n]{2,}""")

    fun sanitize(text: String): String {
        if (text.isBlank()) return ""

        var result = text

        // 1. Remove translator/editor notes
        result = TRANSLATOR_NOTE_BRACKETS.replace(result, "")
        result = TRANSLATOR_NOTE_PARENS.replace(result, "")

        // 2. Remove visual dividers and hr lines
        result = VISUAL_DIVIDERS.replace(result, " ")

        // 3. Normalize quotes and brackets to standard dialogue quotes
        result = ASIAN_OPEN_QUOTES.replace(result, "\"")
        result = ASIAN_CLOSE_QUOTES.replace(result, "\"")
        result = EUROPEAN_QUOTES.replace(result, "\"")

        // 4. Strip markdown formatting
        result = MARKDOWN_BOLD_STRIKE.replace(result) { mr ->
            mr.groupValues[1].ifEmpty { mr.groupValues[2] }.ifEmpty { mr.groupValues[3] }
        }
        result = MARKDOWN_ITALIC.replace(result) { mr ->
            mr.groupValues[1].ifEmpty { mr.groupValues[2] }
        }

        // 5. Remove decorative/sound symbols that engines read as literal names
        result = DECORATIVE_SYMBOLS.replace(result, " ")

        // 6. Normalize punctuation repetitions (prevent "ponto ponto ponto")
        result = REPEATED_DOTS.replace(result, "... ")
        result = REPEATED_EXCLAMATION.replace(result, "! ")
        result = REPEATED_QUESTION.replace(result, "? ")

        // 7. Normalize spacing
        result = MULTI_SPACE.replace(result, " ")

        return result.trim()
    }
}
