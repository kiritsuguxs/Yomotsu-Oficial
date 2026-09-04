package eu.kanade.translation.model

import kotlin.math.abs

object TranslationBlockGrouper {

    fun group(blocks: List<TranslationBlock>, strict: Boolean = false): MutableList<TranslationBlock> {
        if (blocks.isEmpty()) return mutableListOf()
        val pending = blocks.sortedWith(readingOrder).toMutableList()
        val grouped = mutableListOf<TranslationBlock>()
        while (pending.isNotEmpty()) {
            var group = pending.removeAt(0)
            var changed: Boolean
            do {
                changed = false
                val iterator = pending.listIterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (shouldMerge(group, candidate, strict)) {
                        group = merge(group, candidate)
                        iterator.remove()
                        changed = true
                    }
                }
            } while (changed)
            grouped += group
        }
        return grouped.sortedWith(readingOrder).toMutableList()
    }

    private fun shouldMerge(a: TranslationBlock, b: TranslationBlock, strict: Boolean): Boolean {
        // Each experimental block owns a separately permission-clipped cleanup mask.
        // Merging would make ownership ambiguous and could authorize unrelated glyph removal.
        if (a.dbnetCleanupMask != null || b.dbnetCleanupMask != null) return false
        // After a partial provider response, do not erase an untranslated source
        // by attaching its cleanup to a paragraph which has a translation.
        if (a.translation.isBlank() != b.translation.isBlank()) return false
        if (abs(a.angle - b.angle) > MAX_ANGLE_DIFFERENCE) return false
        val visualSizeCompatible = visualTextSizeSimilar(a, b)
        val paragraphGlyphGap = if (strict) MAX_PADDLE_PARAGRAPH_GLYPH_GAP else MAX_COMIC_PARAGRAPH_GLYPH_GAP
        val continuousParagraph = visualSizeCompatible && isOpenEndedContinuation(a, b) && shouldMergeByProximity(a, b, paragraphGlyphGap)
        val connectorContinuation = visualSizeCompatible && !strict && endsWithConnector(a, b) && shouldMergeByProximity(a, b, MAX_CONNECTOR_GLYPH_GAP)

        if (a.balloonDetected || b.balloonDetected) {
            if (!colorsAreCompatible(a.backgroundColor, b.backgroundColor)) return false
            if (a.balloonDetected && b.balloonDetected) {
                val firstLayout = a.layoutRegion
                val secondLayout = b.layoutRegion
                if (firstLayout != null && secondLayout != null) {
                    val sharedArea = intersectionArea(firstLayout, secondLayout)
                    val smallerArea = minOf(firstLayout.area(), secondLayout.area()).coerceAtLeast(1f)
                    if (sharedArea / smallerArea >= MIN_SHARED_BALLOON_RATIO) return true
                    val layoutsClearlySeparated = detectedLayoutsClearlySeparated(a, b, firstLayout, secondLayout)
                    if (a.translation.isNotBlank() && b.translation.isNotBlank()) {
                        // After translation, source OCR punctuation must not pull two detached
                        // balloon regions back together. A connector may join only a very close
                        // split region; clearly separated detected balloons always stay apart.
                        if (layoutsClearlySeparated) return false
                        return connectorContinuation
                    }
                    if (continuousParagraph) return true
                    if (connectorContinuation && layoutsClearlySeparated) return false
                }
                return continuousParagraph || connectorContinuation
            }
            val detected = if (a.balloonDetected) a else b
            val candidate = if (a.balloonDetected) b else a
            val detectedLayout = detected.layoutRegion
            if (detectedLayout != null) {
                val candidateCenterX = candidate.x + candidate.width / 2f
                val candidateCenterY = candidate.y + candidate.height / 2f
                val centerIsInside = candidateCenterX in detectedLayout.x..(detectedLayout.x + detectedLayout.width) && candidateCenterY in detectedLayout.y..(detectedLayout.y + detectedLayout.height)
                if (centerIsInside && visualSizeCompatible) return true
            }
            return continuousParagraph || connectorContinuation
        }
        return visualSizeCompatible && shouldMergeByProximity(a, b)
    }

    private fun detectedLayoutsClearlySeparated(a: TranslationBlock, b: TranslationBlock, first: TranslationRegion, second: TranslationRegion): Boolean {
        val horizontalGap = maxOf(first.x - (second.x + second.width), second.x - (first.x + first.width), 0f)
        val verticalGap = maxOf(first.y - (second.y + second.height), second.y - (first.y + first.height), 0f)
        val glyphHeight = maxOf(a.symHeight, b.symHeight).coerceAtLeast(1f)
        return maxOf(horizontalGap, verticalGap) > glyphHeight * MAX_DETECTED_LAYOUT_GLYPH_GAP
    }

    private fun visualTextSizeSimilar(a: TranslationBlock, b: TranslationBlock): Boolean {
        val heightRatio = ratio(a.symHeight, b.symHeight)
        val widthRatio = ratio(apparentCharacterWidth(a), apparentCharacterWidth(b))
        return heightRatio <= MAX_VISUAL_SIZE_RATIO && widthRatio <= MAX_VISUAL_SIZE_RATIO
    }

    private fun apparentCharacterWidth(block: TranslationBlock): Float {
        // Grouped text joins physical lines with spaces. Its growing length is
        // no longer a line measurement; retain the original weighted glyph size.
        if (block.sourceRegions.isNotEmpty() && block.symWidth.isFinite() && block.symWidth > 0f) return block.symWidth
        // A paragraph's width belongs to its longest line, not all its lines.
        val compactLength = block.text.lineSequence()
            .maxOfOrNull { line -> line.count { !it.isWhitespace() } }
            ?.coerceAtLeast(1) ?: 1
        val measuredWidth = block.width.coerceAtLeast(1f) / compactLength
        return if (block.symWidth > 0f) (block.symWidth + measuredWidth) / 2f else measuredWidth
    }

    private fun ratio(first: Float, second: Float): Float {
        val smaller = minOf(first, second).coerceAtLeast(1f)
        return maxOf(first, second).coerceAtLeast(1f) / smaller
    }

    private fun isOpenEndedContinuation(a: TranslationBlock, b: TranslationBlock): Boolean {
        val first = listOf(a, b).sortedWith(readingOrder).first()
        val normalized = first.text.trimEnd().dropLastWhile { it in "\"'”’)]}" }
        return normalized.lastOrNull()?.let { it !in ".!?…:;" } == true
    }

    private fun endsWithConnector(a: TranslationBlock, b: TranslationBlock): Boolean {
        val lastWord = connectorText(a, b).trimEnd().substringAfterLast(' ').trim { !it.isLetter() }.lowercase()
        return lastWord in continuationConnectors
    }

    private fun connectorText(a: TranslationBlock, b: TranslationBlock): String {
        val first = listOf(a, b).sortedWith(readingOrder).first()
        return first.translation.takeIf(String::isNotBlank) ?: first.text
    }

    private fun shouldMergeByProximity(a: TranslationBlock, b: TranslationBlock, maxGlyphGap: Float = MAX_GLYPH_GAP): Boolean {
        val left = maxOf(a.x, b.x)
        val right = minOf(a.x + a.width, b.x + b.width)
        val horizontalOverlap = (right - left).coerceAtLeast(0f)
        val overlapRatio = horizontalOverlap / minOf(a.width, b.width).coerceAtLeast(1f)
        val centerA = a.x + a.width / 2f
        val centerB = b.x + b.width / 2f
        val centersAligned = abs(centerA - centerB) <= maxOf(a.width, b.width) * MAX_CENTER_OFFSET_RATIO
        val top = maxOf(a.y, b.y)
        val bottom = minOf(a.y + a.height, b.y + b.height)
        val verticalGap = (top - bottom).coerceAtLeast(0f)
        val glyphHeight = maxOf(a.symHeight, b.symHeight).coerceAtLeast(1f)
        return verticalGap <= glyphHeight * maxGlyphGap && (overlapRatio >= MIN_HORIZONTAL_OVERLAP || centersAligned)
    }

    private fun merge(a: TranslationBlock, b: TranslationBlock): TranslationBlock {
        val ordered = listOf(a, b).sortedWith(readingOrder)
        val source = enclosing(a.sourceRegion(), b.sourceRegion())
        val anyDetectedBalloon = a.balloonDetected || b.balloonDetected
        val mergedLayout = when {
            a.balloonDetected && b.balloonDetected -> enclosingNullable(a.layoutRegion, b.layoutRegion)
            a.balloonDetected -> a.layoutRegion
            b.balloonDetected -> b.layoutRegion
            else -> null
        }
        return TranslationBlock(
            text = ordered.joinToString(" ") { it.text.trim() }, translation = ordered.map { it.translation.trim() }.filter(String::isNotEmpty).joinToString(" "),
            width = source.width, height = source.height, x = source.x, y = source.y,
            symHeight = weightedAverage(a.symHeight, a.text.length, b.symHeight, b.text.length), symWidth = weightedAverage(a.symWidth, a.text.length, b.symWidth, b.text.length), angle = weightedAverage(a.angle, a.text.length, b.angle, b.text.length),
            cleanupRegion = enclosing(a.cleanupRegion ?: a.defaultCleanupRegion(), b.cleanupRegion ?: b.defaultCleanupRegion()), layoutRegion = mergedLayout,
            backgroundColor = averageColor(a.backgroundColor, b.backgroundColor), foregroundColor = averageColor(a.foregroundColor, b.foregroundColor), balloonDetected = anyDetectedBalloon, geometryVersion = maxOf(a.geometryVersion, b.geometryVersion),
            sourceRegions = a.sourceRegions.ifEmpty { listOf(a.sourceRegion()) } + b.sourceRegions.ifEmpty { listOf(b.sourceRegion()) },
            sourceCleanupRegions = (a.resolvedCleanupPatches(Float.MAX_VALUE, Float.MAX_VALUE) +
                b.resolvedCleanupPatches(Float.MAX_VALUE, Float.MAX_VALUE)).map { it.region },
        )
    }

    private fun weightedAverage(first: Float, firstWeight: Int, second: Float, secondWeight: Int): Float { val w1 = firstWeight.coerceAtLeast(1); val w2 = secondWeight.coerceAtLeast(1); return (first * w1 + second * w2) / (w1 + w2) }
    private fun colorsAreCompatible(first: Int?, second: Int?): Boolean { if (first == null || second == null) return true; val r = first.red() - second.red(); val g = first.green() - second.green(); val b = first.blue() - second.blue(); return r * r + g * g + b * b <= MAX_COLOR_DISTANCE_SQUARED }
    private fun averageColor(first: Int?, second: Int?): Int? = when { first == null -> second; second == null -> first; else -> ((average(first.alpha(), second.alpha()) shl 24) or (average(first.red(), second.red()) shl 16) or (average(first.green(), second.green()) shl 8) or average(first.blue(), second.blue())) }
    private fun average(first: Int, second: Int) = (first + second) / 2
    private fun Int.alpha() = ushr(24) and 0xff; private fun Int.red() = ushr(16) and 0xff; private fun Int.green() = ushr(8) and 0xff; private fun Int.blue() = this and 0xff
    private fun TranslationRegion.area() = width.coerceAtLeast(0f) * height.coerceAtLeast(0f)
    private fun intersectionArea(first: TranslationRegion, second: TranslationRegion): Float { val w = (minOf(first.x + first.width, second.x + second.width) - maxOf(first.x, second.x)).coerceAtLeast(0f); val h = (minOf(first.y + first.height, second.y + second.height) - maxOf(first.y, second.y)).coerceAtLeast(0f); return w * h }
    private fun enclosingNullable(first: TranslationRegion?, second: TranslationRegion?): TranslationRegion? = when { first == null -> second; second == null -> first; else -> enclosing(first, second) }
    private fun enclosing(first: TranslationRegion, second: TranslationRegion): TranslationRegion { val l = minOf(first.x, second.x); val t = minOf(first.y, second.y); val r = maxOf(first.x + first.width, second.x + second.width); val b = maxOf(first.y + first.height, second.y + second.height); return TranslationRegion(l, t, r - l, b - t) }
    private val readingOrder = compareBy<TranslationBlock> { it.y }.thenBy { it.x }
    private const val MAX_ANGLE_DIFFERENCE = 12f; private const val MIN_SHARED_BALLOON_RATIO = 0.20f; private const val MAX_CENTER_OFFSET_RATIO = 0.35f; private const val MAX_GLYPH_GAP = 1.45f; private const val MAX_COMIC_PARAGRAPH_GLYPH_GAP = 1.49f; private const val MAX_PADDLE_PARAGRAPH_GLYPH_GAP = 2.4f; private const val MAX_CONNECTOR_GLYPH_GAP = 3.5f; private const val MAX_DETECTED_LAYOUT_GLYPH_GAP = 0.75f; private const val MIN_HORIZONTAL_OVERLAP = 0.48f; private const val MAX_COLOR_DISTANCE_SQUARED = 3_600; private const val MAX_VISUAL_SIZE_RATIO = 1.65f
    private val continuationConnectors = setOf("a", "an", "the", "of", "to", "in", "on", "at", "from", "for", "with", "by", "and", "or", "but", "my", "your", "his", "her", "our", "their", "this", "that", "o", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "ao", "aos", "à", "às", "para", "por", "com", "sem", "pelo", "pela", "num", "numa", "e", "ou", "mas", "meu", "minha", "seu", "sua", "nosso", "nossa", "que")
}
