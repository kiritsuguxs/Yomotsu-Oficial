package eu.kanade.translation.model

/**
 * Produces text safe for OCR, translation storage, and Compose rendering.
 *
 * Paragraph boundaries are retained; whitespace is normalized only inside an
 * individual line so real word boundaries are never removed.
 */
internal fun normalizeTranslationText(text: String): String {
    val withoutInvisibleFormatting = buildString {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            when {
                codePoint == NON_BREAKING_SPACE || codePoint == NARROW_NON_BREAKING_SPACE -> append(' ')
                Character.getType(codePoint) != Character.FORMAT.toInt() -> appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
    }

    return withoutInvisibleFormatting
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
                .joinToString(separator = "\n", transform = ::normalizeLine)
        .trim()
}

private fun normalizeLine(line: String): String {
    val spacesNormalized = buildString {
        var previousWasSpace = false
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (!previousWasSpace) append(' ')
                previousWasSpace = true
            } else {
                appendCodePoint(codePoint)
                previousWasSpace = false
            }
            index += Character.charCount(codePoint)
        }
    }.trim()

    return spacesNormalized.replace(Regex(" +(?=\\p{P})"), "")
}

private const val NON_BREAKING_SPACE = 0x00A0
private const val NARROW_NON_BREAKING_SPACE = 0x202F
