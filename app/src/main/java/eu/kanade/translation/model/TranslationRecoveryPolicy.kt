package eu.kanade.translation.model

import kotlin.math.abs

/** Final automatic-translation gate; rejected blocks remain manual candidates. */
internal object TranslationRecoveryPolicy {
    fun isTranslatable(block: TranslationBlock): Boolean {
        val layout = block.layoutRegion ?: return false
        val widthToHeight = layout.width / layout.height.coerceAtLeast(1f)
        // Keep the existing decision unchanged for previously accepted candidates.
        if (widthToHeight >= 0.38f) return block.balloonDetected || block.backgroundColor != null
        return hasStrongNarrowDialogueEvidence(block, layout)
    }

    private fun hasStrongNarrowDialogueEvidence(block: TranslationBlock, layout: TranslationRegion): Boolean {
        if (!block.balloonDetected || block.backgroundColor == null ||
            block.geometryVersion < CURRENT_TRANSLATION_GEOMETRY_VERSION ||
            !block.angle.isFinite() || abs(block.angle) > 12f ||
            !block.width.isFinite() || !block.height.isFinite() || block.width <= 0f || block.height <= 0f ||
            layout.width / layout.height !in 0.25f..0.38f
        ) return false
        val cleanup = block.cleanupRegion ?: return false
        val source = block.sourceRegion()
        // Containment alone accepts giant boxes; IoU also demands a close glyph eraser.
        if (source.overlapFraction(layout) < 0.98f || source.overlapFraction(cleanup) < 0.98f ||
            cleanup.overlapFraction(layout) < 0.98f || source.intersectionOverUnion(cleanup) < 0.55f
        ) return false

        val lines = block.text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return false
        val lineHeight = block.height / lines.size
        if (!block.symHeight.isFinite() || block.symHeight !in lineHeight * 0.5f..lineHeight * 1.2f ||
            !block.symWidth.isFinite() || block.symWidth <= 0f || block.symWidth > block.width * 0.5f
        ) return false

        val compact = block.text.filterNot(Char::isWhitespace)
        val letters = compact.count(Char::isLetter)
        if (letters < 12 || letters.toFloat() / compact.length < 0.8f) return false
        val words = block.text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.size < 3 || words.distinct().size < 3) return false
        if (words.all { it in soundEffects } || words.any { word ->
                word.length >= 4 && word.toSet().size <= 2
            }
        ) return false
        return true
    }

    private val soundEffects = setOf(
        "bam", "bang", "boom", "crash", "thud", "thump", "rumble", "whoosh", "swish", "crack",
        "smash", "pow", "bzzz", "buzz", "clang", "clank", "splash", "tap", "ha", "ah",
    )
}
