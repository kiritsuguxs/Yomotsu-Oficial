package eu.kanade.translation.translator

internal fun postEditMachineTranslation(
    sourceText: String,
    translatedText: String,
    targetLanguage: TextTranslatorLanguage,
): String {
    if (targetLanguage != TextTranslatorLanguage.PORTUGUESE) return translatedText

    val source = normalizeOcrText(sourceText)
    if (fatherDadContrastRegex.containsMatchIn(source)) {
        return "“Pai”? Você não quis dizer “papai”?"
    }
    if (nightmareRegex.matches(source)) {
        val ellipsis = if (source.trimStart().startsWith("...") || source.trimStart().startsWith("…")) "..." else ""
        return "${ellipsis}Acho que tive um pesadelo."
    }

    var edited = translatedText.trim()
    if (liegeRegex.containsMatchIn(source)) {
        edited = translatedLiegeRegex.replace(edited) { match ->
            if (match.value.firstOrNull()?.isUpperCase() == true) "Meu senhor" else "meu senhor"
        }
    }
    if (jobDoneRegex.containsMatchIn(source)) {
        edited = translatedJobDoneRegex.replace(edited, "nosso trabalho aqui terminou")
        edited = lordSentenceBreakRegex.replace(edited, "Meu senhor, nosso trabalho")
    }
    if (shadowMonarchRegex.containsMatchIn(source)) {
        edited = translatedShadowMonarchRegex.replace(edited, "Monarca das Sombras")
    }
    return edited
}

private val fatherDadContrastRegex = Regex(
    pattern = """(?i)\bfather\b.*\bdon['’]?t\s+you\s+mean\b.*\bdad\b""",
)
private val nightmareRegex = Regex(
    pattern = """(?i)^[\s.…]*i\s+think\s+i\s+had\s+a\s+nightmare[\s.!?…]*$""",
)
private val liegeRegex = Regex(pattern = """(?i)\b(?:my\s+)?liege\b""")
private val translatedLiegeRegex = Regex(pattern = """(?i)\b(?:meu\s+)?liege\b""")
private val jobDoneRegex = Regex(pattern = """(?i)\bour\s+job\s+here\s+is\s+done\b""")
private val translatedJobDoneRegex = Regex(
    pattern = """(?i)nosso\s+trabalho\s+aqui\s+(?:está\s+feito|está\s+concluído|foi\s+feito)""",
)
private val lordSentenceBreakRegex = Regex(
    pattern = """(?i)meu\s+senhor\s*[.!]\s*nosso\s+trabalho""",
)
private val shadowMonarchRegex = Regex(pattern = """(?i)\bshadow\s+monarch\b""")
private val translatedShadowMonarchRegex = Regex(
    pattern = """(?i)\b(?:sombra\s+monarca|monarca\s+(?:(?:da|de|das)\s+)?sombra(?:s)?)\b""",
)
