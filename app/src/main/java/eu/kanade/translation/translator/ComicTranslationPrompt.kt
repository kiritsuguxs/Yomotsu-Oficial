package eu.kanade.translation.translator

import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.normalizeTranslationText
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import org.json.JSONObject

internal fun normalizeOcrText(text: String): String {
    return normalizeTranslationText(text)
}

internal fun TextTranslatorLanguage.translationApiCode(): String {
    return if (this == TextTranslatorLanguage.PORTUGUESE) "pt-BR" else code
}

internal fun buildComicInputJson(
    pages: Map<String, PageTranslation>,
    textForBlock: (TranslationBlock) -> String = { normalizeOcrText(it.text) },
): String {
    return buildJsonObject {
        pages.forEach { (fileName, page) ->
            putJsonArray(fileName) {
                page.blocks.forEach { block -> add(textForBlock(block)) }
            }
        }
    }.toString()
}

internal fun buildComicTranslationPrompt(
    fromLang: TextRecognizerLanguage,
    toLang: TextTranslatorLanguage,
): String {
    val targetLanguage = if (toLang == TextTranslatorLanguage.PORTUGUESE) {
        "Brazilian Portuguese (pt-BR)"
    } else {
        toLang.label
    }
    val brazilianPortugueseRules = if (toLang == TextTranslatorLanguage.PORTUGUESE) {
        """
        Brazilian Portuguese requirements:
        - Write exclusively in natural contemporary Brazilian Portuguese.
        - Use Brazilian vocabulary, pronouns, contractions, and dialogue rhythm.
        - Do not use European Portuguese vocabulary or grammar.
        - Prefer natural Brazilian phrasing over word-for-word translation.
        - Keep established proper names unchanged and translate kinship terms consistently.
        """.trimIndent()
    } else {
        ""
    }

    return """
        You are a professional translator and dialogue editor for manga, manhwa, and manhua.

        The source OCR language is ${fromLang.label}. Translate the chapter to $targetLanguage.
        The input is a JSON object. Keys are page filenames in reading order; each value is an array of text regions in visual reading order.

        Context and editing rules:
        - Read the entire sequential chapter before deciding ambiguous wording.
        - Use nearby regions and adjacent pages to infer speaker, gender, relationship, tone, pronouns, and implied subjects.
        - Treat line breaks inside an OCR region as visual wrapping, not separate sentences.
        - Silently repair only obvious OCR mistakes when the surrounding context makes the intended text clear.
        - Produce fluent comic dialogue that sounds written by a human editor, never a literal sequence of dictionary substitutions.
        - Preserve meaning, emotion, politeness level, jokes, names, honorifics, and recurring terminology consistently.
        - Do not invent information that is absent from the source.
        - Internally perform a second editing pass and rewrite any stiff, contradictory, or unnatural sentence before returning it.

        $brazilianPortugueseRules

        Output rules:
        - Return only one valid JSON object, without Markdown or explanations.
        - Preserve every input key, array order, and array length exactly.
        - Every output item must be a string containing only its final translation.
        - Replace watermarks, credits, and site links with the exact marker RTMTH.
    """.trimIndent()
}

internal fun buildComicTranslationRequest(
    context: ComicTranslationContext,
    inputJson: String,
): String {
    val workContext = buildList {
        context.mangaTitle.takeIf { it.isNotBlank() }?.let { add("Series: $it") }
        context.chapterName.takeIf { it.isNotBlank() }?.let { add("Chapter: $it") }
    }.joinToString("\n")
    val glossary = TranslationGlossary.instructions(context)

    return buildString {
        if (workContext.isNotBlank()) {
            appendLine(workContext)
        }
        if (glossary.isNotBlank()) {
            appendLine()
            appendLine(glossary)
            appendLine()
        }
        appendLine("Translate the following sequential chapter JSON:")
        append(inputJson)
    }
}

internal fun parseComicTranslationResponse(response: String): JSONObject {
    val firstBrace = response.indexOf('{')
    val lastBrace = response.lastIndexOf('}')
    require(firstBrace >= 0 && lastBrace > firstBrace) { "Translation response does not contain JSON" }
    return JSONObject(response.substring(firstBrace, lastBrace + 1))
}
