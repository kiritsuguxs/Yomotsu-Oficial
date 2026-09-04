package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Color
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationColors
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.clamped
import eu.kanade.translation.model.defaultCleanupRegion
import eu.kanade.translation.model.defaultLayoutRegion
import eu.kanade.translation.model.inset
import eu.kanade.translation.model.sourceRegion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class SpeechBubbleAnalysis(
    val cleanupRegion: TranslationRegion,
    val layoutRegion: TranslationRegion,
    val backgroundColor: Int?,
    val foregroundColor: Int? = null,
    val balloonDetected: Boolean = false,
)

class SpeechBubbleAnalyzer(
    private val bitmap: Bitmap,
    private val pageWidth: Int,
    private val pageHeight: Int,
    textRegions: List<TranslationRegion> = emptyList(),
) {
    private val scaleX = bitmap.width / pageWidth.coerceAtLeast(1).toFloat()
    private val scaleY = bitmap.height / pageHeight.coerceAtLeast(1).toFloat()
    // Original OCR footprints, before grouping. A neighboring line of lettering
    // is not a balloon outline, but the space between the footprints still is
    // inspected normally so separate balloons cannot join through this mask.
    private val textBounds = textRegions.map {
        intArrayOf(toBitmapX(it.x), toBitmapY(it.y), toBitmapX(it.x + it.width), toBitmapY(it.y + it.height))
    }
    private val safeTextBoundsByColor = mutableMapOf<Int, List<IntArray>>()

    fun analyze(block: TranslationBlock): SpeechBubbleAnalysis {
        val cleanup = block.defaultCleanupRegion(pageWidth.toFloat(), pageHeight.toFloat())
        val fallbackLayout = block.defaultLayoutRegion(pageWidth.toFloat(), pageHeight.toFloat())
        val backgroundSample = sampleBackgroundColor(block)
        val detectedLayout = backgroundSample?.takeIf { it.confidence >= MIN_DETECTION_CONFIDENCE }?.let { detectBalloonInterior(block, it.color) }
        val overlayColor = backgroundSample?.takeIf { it.confidence >= MIN_OVERLAY_CONFIDENCE }?.color
        return SpeechBubbleAnalysis(
            cleanupRegion = overlayColor?.let {
                constrainToVisibleBoundary(cleanup, block.sourceRegion(), it, containSource = true)
            } ?: cleanup,
            layoutRegion = (detectedLayout ?: fallbackLayout).clamped(pageWidth.toFloat(), pageHeight.toFloat()),
            backgroundColor = overlayColor,
            foregroundColor = overlayColor?.let(TranslationColors::contrastingForeground) ?: Color.BLACK,
            balloonDetected = detectedLayout != null,
        )
    }

    private fun sampleBackgroundColor(block: TranslationBlock): BackgroundSample? {
        val samples = mutableListOf<Int>()
        val horizontal = mutableListOf<Int>()
        val vertical = mutableListOf<Int>()
        sampleRing(
            samples, block.sourceRegion(),
            max(2, (block.symWidth * scaleX * 0.65f).roundToInt()),
            max(2, (block.symHeight * scaleY * 0.65f).roundToInt()),
            horizontal, vertical,
        )
        val outer = dominantBackground(samples)
        // Preserve established samples supported on both axes. A nearby collar
        // alone can be a tight caption outline rather than its background.
        if (outer != null && hasColorSupport(horizontal, outer.color) && hasColorSupport(vertical, outer.color)) return outer

        // Peer OCR lines can mask the inner samples, leaving only lateral page
        // color outside the balloon. Recover evidence close to each ORIGINAL
        // source; the enclosing paragraph corners can also cross the contour.
        val nearby = mutableListOf<Int>()
        for (source in block.sourceRegions.ifEmpty { listOf(block.sourceRegion()) }) {
            for (margin in 1..2) sampleRing(nearby, source, margin, margin)
        }
        return dominantBackground(nearby)?.takeIf { it.confidence >= MIN_BACKGROUND_RATIO } ?: outer
    }

    private fun hasColorSupport(samples: List<Int>, color: Int): Boolean =
        samples.size >= MIN_COLOR_SAMPLES && samples.count { colorsMatch(it, color) } / samples.size.toFloat() >= MIN_BACKGROUND_RATIO

    private fun sampleRing(
        samples: MutableList<Int>, source: TranslationRegion, marginX: Int, marginY: Int,
        horizontal: MutableList<Int>? = null, vertical: MutableList<Int>? = null,
    ) {
        val left = toBitmapX(source.x); val right = toBitmapX(source.x + source.width)
        val top = toBitmapY(source.y); val bottom = toBitmapY(source.y + source.height)
        repeat(LINE_SAMPLE_COUNT) { index ->
            val fraction = index / (LINE_SAMPLE_COUNT - 1f); val x = lerp(left, right, fraction); val y = lerp(top, bottom, fraction)
            addPixel(samples, x, top - marginY)?.let { horizontal?.add(it) }
            addPixel(samples, x, bottom + marginY)?.let { horizontal?.add(it) }
            addPixel(samples, left - marginX, y)?.let { vertical?.add(it) }
            addPixel(samples, right + marginX, y)?.let { vertical?.add(it) }
        }
        // Corners still contribute to the original color cluster, but cannot
        // stand in for a missing axis after neighboring OCR samples are removed.
        addPixel(samples, left - marginX, top - marginY); addPixel(samples, right + marginX, top - marginY); addPixel(samples, left - marginX, bottom + marginY); addPixel(samples, right + marginX, bottom + marginY)
    }

    private fun dominantBackground(samples: List<Int>): BackgroundSample? {
        if (samples.size < MIN_COLOR_SAMPLES) return null
        val dominantBucket = samples.groupBy(::colorBucket).maxByOrNull { it.value.size }?.value.orEmpty()
        if (dominantBucket.size < MIN_DOMINANT_SAMPLES) return null
        val seed = Color.rgb(dominantBucket.map { Color.red(it) }.median(), dominantBucket.map { Color.green(it) }.median(), dominantBucket.map { Color.blue(it) }.median())
        val backgroundSamples = samples.filter { colorDistanceSquared(it, seed) <= MAX_CLUSTER_DISTANCE_SQUARED }
        if (backgroundSamples.size < MIN_DOMINANT_SAMPLES) return null
        val color = Color.rgb(backgroundSamples.map { Color.red(it) }.median(), backgroundSamples.map { Color.green(it) }.median(), backgroundSamples.map { Color.blue(it) }.median())
        return BackgroundSample(color, backgroundSamples.size / samples.size.toFloat())
    }

    private fun detectBalloonInterior(block: TranslationBlock, backgroundColor: Int): TranslationRegion? {
        val source = block.sourceRegion()
        val sourceLeft = toBitmapX(source.x); val sourceRight = toBitmapX(source.x + source.width); val sourceTop = toBitmapY(source.y); val sourceBottom = toBitmapY(source.y + source.height)
        val horizontalReach = max(block.width * 0.9f, block.symWidth * 6f); val verticalReach = max(block.height * 1.35f, block.symHeight * BALLOON_VERTICAL_REACH_IN_GLYPHS)
        val left = scanAxis(sourceLeft - 1, toBitmapX(source.x - horizontalReach), -1, bitmap.width - 1) { verticalMatchRatio(it, sourceTop, sourceBottom, backgroundColor) }
        val right = scanAxis(sourceRight + 1, toBitmapX(source.x + source.width + horizontalReach), 1, bitmap.width - 1) { verticalMatchRatio(it, sourceTop, sourceBottom, backgroundColor) }
        val top = scanAxis(sourceTop - 1, toBitmapY(source.y - verticalReach), -1, bitmap.height - 1) { horizontalMatchRatio(it, sourceLeft, sourceRight, backgroundColor) }
        val bottom = scanAxis(sourceBottom + 1, toBitmapY(source.y + source.height + verticalReach), 1, bitmap.height - 1) { horizontalMatchRatio(it, sourceLeft, sourceRight, backgroundColor) }
        if (listOf(left, right, top, bottom).count(ScanResult::foundBoundary) < MIN_REQUIRED_BOUNDARIES) return null
        val fallback = block.defaultLayoutRegion(pageWidth.toFloat(), pageHeight.toFloat())
        val fallbackLeft = toBitmapX(fallback.x); val fallbackRight = toBitmapX(fallback.x + fallback.width); val fallbackTop = toBitmapY(fallback.y); val fallbackBottom = toBitmapY(fallback.y + fallback.height)
        val mirroredLeft = (sourceLeft - (right.lastMatch - sourceRight).coerceAtLeast(0)).coerceIn(0, bitmap.width - 1)
        val mirroredRight = (sourceRight + (sourceLeft - left.lastMatch).coerceAtLeast(0)).coerceIn(0, bitmap.width - 1)
        val mirroredTop = (sourceTop - (bottom.lastMatch - sourceBottom).coerceAtLeast(0)).coerceIn(0, bitmap.height - 1)
        val mirroredBottom = (sourceBottom + (sourceTop - top.lastMatch).coerceAtLeast(0)).coerceIn(0, bitmap.height - 1)
        val detectedLeft = when { left.foundBoundary -> left.lastMatch; right.foundBoundary -> minOf(fallbackLeft, mirroredLeft); else -> fallbackLeft }
        val detectedRight = when { right.foundBoundary -> right.lastMatch; left.foundBoundary -> maxOf(fallbackRight, mirroredRight); else -> fallbackRight }
        val detectedTop = when { top.foundBoundary -> top.lastMatch; bottom.foundBoundary -> minOf(fallbackTop, mirroredTop); else -> fallbackTop }
        val detectedBottom = when { bottom.foundBoundary -> bottom.lastMatch; top.foundBoundary -> maxOf(fallbackBottom, mirroredBottom); else -> fallbackBottom }
        val detected = TranslationRegion(fromBitmapX(detectedLeft), fromBitmapY(detectedTop), fromBitmapX(detectedRight) - fromBitmapX(detectedLeft), fromBitmapY(detectedBottom) - fromBitmapY(detectedTop)).clamped(pageWidth.toFloat(), pageHeight.toFloat())
        if (detected.width < source.width || detected.height < source.height) return null
        if (!SpeechBubbleSizePolicy.accepts(source.width, source.height, detected.width, detected.height)) return null

        // A rectangle inset by ~(1 - 1/sqrt(2))/2 on EACH SIDE is the largest
        // centered rectangle whose corners stay inside an ellipse. Use that
        // only for rounded detected balloons; ordinary wide/tall balloons keep
        // the established conservative inset so existing paragraph fit is not
        // globally reduced.
        val aspect = detected.width / detected.height.coerceAtLeast(1f)
        val rounded = aspect in ROUNDED_SAFE_LAYOUT_MIN_ASPECT..ROUNDED_SAFE_LAYOUT_MAX_ASPECT
        val horizontalRatio = if (rounded) ROUNDED_SAFE_LAYOUT_INSET_RATIO else BALLOON_HORIZONTAL_INSET_RATIO
        val verticalRatio = if (rounded) ROUNDED_SAFE_LAYOUT_INSET_RATIO else BALLOON_VERTICAL_INSET_RATIO
        val safeLayout = detected.inset(
            horizontalInset = max(block.symWidth * 0.65f, detected.width * horizontalRatio),
            verticalInset = max(block.symHeight * 0.55f, detected.height * verticalRatio),
        )
        val minimumWidth = max(block.symWidth * 2.5f, source.width * MIN_SOURCE_DIMENSION_RATIO)
        val minimumHeight = max(block.symHeight * 2f, source.height * MIN_SOURCE_DIMENSION_RATIO)
        if (safeLayout.width < minimumWidth || safeLayout.height < minimumHeight) return null
        // Once accepted, keep the measured contour even if pixel rounding makes
        // it fractionally smaller. Falling back would expand outside this balloon.
        return constrainToVisibleBoundary(safeLayout, source, backgroundColor, block.sourceRegions.ifEmpty { listOf(source) })
    }

    private fun constrainToVisibleBoundary(
        layout: TranslationRegion,
        source: TranslationRegion,
        color: Int,
        originalSources: List<TranslationRegion> = listOf(source),
        containSource: Boolean = false,
    ): TranslationRegion {
        val left = ceil(layout.x * scaleX).toInt().coerceIn(0, bitmap.width - 1)
        val top = ceil(layout.y * scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val right = floor((layout.x + layout.width) * scaleX).toInt().coerceIn(left, bitmap.width - 1)
        val bottom = floor((layout.y + layout.height) * scaleY).toInt().coerceIn(top, bitmap.height - 1)
        val width = right - left + 1
        val height = bottom - top + 1
        val originalBounds = originalSources.map {
            intArrayOf(toBitmapX(it.x), toBitmapY(it.y), toBitmapX(it.x + it.width), toBitmapY(it.y + it.height))
        }
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        val referenceLuminance = (red * 0.299f + green * 0.587f + blue * 0.114f).roundToInt()
        fun isBackground(x: Int, y: Int): Boolean {
            if (originalBounds.any { x in it[0]..it[2] && y in it[1]..it[3] }) return true
            if (!containSource && isTextPixel(x, y, color)) return true
            // The candidate bounds above already clamp every coordinate.
            val pixel = bitmap.getPixel(x, y)
            val r = pixel ushr 16 and 0xff
            val g = pixel ushr 8 and 0xff
            val b = pixel and 0xff
            val distance = (r - red) * (r - red) + (g - green) * (g - green) + (b - blue) * (b - blue)
            return (pixel ushr 24 and 0xff) >= MIN_ALPHA && distance <= MAX_COLOR_DISTANCE_SQUARED &&
                abs((r * 0.299f + g * 0.587f + b * 0.114f).roundToInt() - referenceLuminance) <= MAX_LUMINANCE_DELTA
        }

        // Keep the existing Y17 layout exactly when its perimeter is already safe.
        // Axis probes alone miss a diagonal/curved border near a neighboring balloon.
        val perimeterIsClear = (left..right).all { isBackground(it, top) && isBackground(it, bottom) } &&
            (top..bottom).all { isBackground(left, it) && isBackground(right, it) }
        if (perimeterIsClear && !containSource) return layout

        val background = BooleanArray(width * height) { index -> isBackground(left + index % width, top + index / width) }
        // Cleanup may cover its source glyphs only. Every non-background pixel
        // outside that source is protected, including isolated art/other text.
        val boundary = BooleanArray(background.size) { containSource && !background[it] }
        val queue = IntArray(background.size)
        var head = 0
        var tail = 0
        fun enqueue(x: Int, y: Int) {
            val index = y * width + x
            if (!background[index] && !boundary[index]) {
                boundary[index] = true
                queue[tail++] = index
            }
        }
        for (x in 0 until width) { enqueue(x, 0); enqueue(x, height - 1) }
        for (y in 0 until height) { enqueue(0, y); enqueue(width - 1, y) }
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            for (ny in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                for (nx in maxOf(0, x - 1)..minOf(width - 1, x + 1)) enqueue(nx, ny)
            }
        }

        // Text layout ignores isolated interior ink; cleanup protects it outside
        // its source. Find the largest clear rectangle containing the source
        // center (layout) or its whole box (cleanup), without shrinking every side.
        val anchorX = (toBitmapX(source.x + source.width / 2f) - left).coerceIn(0, width - 1)
        val anchorY = (toBitmapY(source.y + source.height / 2f) - top).coerceIn(0, height - 1)
        val heights = IntArray(width)
        val stack = IntArray(width)
        var bestArea = 0
        var best = if (containSource) source.clamped(pageWidth.toFloat(), pageHeight.toFloat()) else layout
        for (y in 0 until height) {
            for (x in 0 until width) heights[x] = if (boundary[y * width + x]) 0 else heights[x] + 1
            var size = 0
            for (x in 0..width) {
                val currentHeight = if (x == width) 0 else heights[x]
                while (size > 0 && heights[stack[size - 1]] > currentHeight) {
                    val rectangleHeight = heights[stack[--size]]
                    val rectangleLeft = if (size == 0) 0 else stack[size - 1] + 1
                    val rectangleTop = y - rectangleHeight + 1
                    val area = (x - rectangleLeft - 1) * (rectangleHeight - 1)
                    val containsRequiredSource = if (containSource) {
                        left + rectangleLeft <= source.x * scaleX && left + x - 1 >= (source.x + source.width) * scaleX &&
                            top + rectangleTop <= source.y * scaleY && top + y >= (source.y + source.height) * scaleY
                    } else {
                        anchorX in rectangleLeft until x && anchorY in rectangleTop..y
                    }
                    if (area > bestArea && containsRequiredSource) {
                        bestArea = area
                        best = TranslationRegion(
                            fromBitmapX(left + rectangleLeft), fromBitmapY(top + rectangleTop),
                            (x - rectangleLeft - 1) / scaleX, (rectangleHeight - 1) / scaleY,
                        )
                    }
                }
                if (x < width) stack[size++] = x
            }
        }
        return best
    }

    private fun scanAxis(start: Int, limit: Int, step: Int, maximum: Int, matchRatio: (Int) -> Float): ScanResult {
        var coordinate = start.coerceIn(0, maximum); val safeLimit = limit.coerceIn(0, maximum); var lastMatch = coordinate; var consecutiveMisses = 0
        while (if (step < 0) coordinate >= safeLimit else coordinate <= safeLimit) {
            if (matchRatio(coordinate) >= MIN_BACKGROUND_RATIO) { lastMatch = coordinate; consecutiveMisses = 0 } else if (++consecutiveMisses >= REQUIRED_BOUNDARY_LINES) return ScanResult(lastMatch, true)
            coordinate += step
        }
        return ScanResult(lastMatch, false)
    }
    private fun verticalMatchRatio(x: Int, top: Int, bottom: Int, color: Int) = lineMatchRatio(top, bottom) { y -> isTextPixel(x, y, color) || colorsMatch(getPixel(x, y), color) }
    private fun horizontalMatchRatio(y: Int, left: Int, right: Int, color: Int) = lineMatchRatio(left, right) { x -> isTextPixel(x, y, color) || colorsMatch(getPixel(x, y), color) }
    private inline fun lineMatchRatio(start: Int, end: Int, matches: (Int) -> Boolean): Float { var matching = 0; repeat(LINE_SAMPLE_COUNT) { if (matches(lerp(start, end, it / (LINE_SAMPLE_COUNT - 1f)))) matching++ }; return matching / LINE_SAMPLE_COUNT.toFloat() }
    private fun isTextPixel(x: Int, y: Int, color: Int? = null): Boolean {
        val bounds = if (color == null) textBounds else safeTextBoundsByColor.getOrPut(color) {
            var candidates = textBounds
            while (true) {
                val safe = candidates.filter { hasBackgroundCollar(it, color, candidates) }
                if (safe.size == candidates.size) break
                candidates = safe
            }
            candidates
        }
        return bounds.any { x in it[0]..it[2] && y in it[1]..it[3] }
    }

    private fun hasBackgroundCollar(bounds: IntArray, color: Int, candidates: List<IntArray>): Boolean {
        // A rectangular OCR box may include an empty corner outside its balloon.
        // Only mask it when the surrounding pixels confirm an interior island;
        // otherwise its pixels remain visible to the contour detector.
        // Adjacent OCR lines can share that collar. Validate them together, and
        // recheck dependencies after rejecting a box which crosses a contour.
        fun clear(x: Int, y: Int) = candidates.any { x in it[0]..it[2] && y in it[1]..it[3] } ||
            colorsMatch(bitmap.getPixel(x, y), color)
        for (margin in 1..2) {
            val left = bounds[0] - margin
            val top = bounds[1] - margin
            val right = bounds[2] + margin
            val bottom = bounds[3] + margin
            if (left < 0 || top < 0 || right >= bitmap.width || bottom >= bitmap.height) return false
            if (!(left..right).all { clear(it, top) && clear(it, bottom) } ||
                !(top..bottom).all { clear(left, it) && clear(right, it) }) return false
        }
        return true
    }
    private fun colorsMatch(pixel: Int, reference: Int): Boolean { if (Color.alpha(pixel) < MIN_ALPHA) return false; val r=Color.red(pixel)-Color.red(reference); val g=Color.green(pixel)-Color.green(reference); val b=Color.blue(pixel)-Color.blue(reference); return r*r+g*g+b*b <= MAX_COLOR_DISTANCE_SQUARED && abs(luminance(pixel)-luminance(reference)) <= MAX_LUMINANCE_DELTA }
    private fun colorBucket(color: Int) = ((Color.red(color)/COLOR_BUCKET_SIZE) shl 8) or ((Color.green(color)/COLOR_BUCKET_SIZE) shl 4) or (Color.blue(color)/COLOR_BUCKET_SIZE)
    private fun colorDistanceSquared(first:Int, second:Int):Int { val r=Color.red(first)-Color.red(second); val g=Color.green(first)-Color.green(second); val b=Color.blue(first)-Color.blue(second); return r*r+g*g+b*b }
    private fun addPixel(target: MutableList<Int>, x: Int, y: Int): Int? {
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height || isTextPixel(x, y)) return null
        val pixel = bitmap.getPixel(x, y)
        if (Color.alpha(pixel) < MIN_ALPHA) return null
        target += pixel
        return pixel
    }
    private fun getPixel(x:Int,y:Int)=bitmap.getPixel(x.coerceIn(0,bitmap.width-1),y.coerceIn(0,bitmap.height-1))
    private fun toBitmapX(value:Float)=(value*scaleX).roundToInt().coerceIn(0,bitmap.width-1); private fun toBitmapY(value:Float)=(value*scaleY).roundToInt().coerceIn(0,bitmap.height-1)
    private fun fromBitmapX(value:Int)=value/scaleX.coerceAtLeast(0.0001f); private fun fromBitmapY(value:Int)=value/scaleY.coerceAtLeast(0.0001f)
    private fun luminance(color:Int)=(Color.red(color)*0.299f+Color.green(color)*0.587f+Color.blue(color)*0.114f).roundToInt()
    private fun List<Int>.median()=sorted()[size/2]; private fun lerp(start:Int,end:Int,fraction:Float)=(start+(end-start)*fraction).roundToInt()
    private data class ScanResult(val lastMatch:Int,val foundBoundary:Boolean); private data class BackgroundSample(val color:Int,val confidence:Float)
    private companion object {
        const val LINE_SAMPLE_COUNT=13; const val MIN_COLOR_SAMPLES=8; const val MIN_DOMINANT_SAMPLES=4; const val MIN_ALPHA=180; const val REQUIRED_BOUNDARY_LINES=2; const val MIN_REQUIRED_BOUNDARIES=3
        const val MIN_BACKGROUND_RATIO=0.62f; const val MIN_DETECTION_CONFIDENCE=0.42f; const val MIN_OVERLAY_CONFIDENCE=0.42f
        const val BALLOON_HORIZONTAL_INSET_RATIO=0.08f; const val BALLOON_VERTICAL_INSET_RATIO=0.08f
        const val ROUNDED_SAFE_LAYOUT_INSET_RATIO=0.1465f; const val ROUNDED_SAFE_LAYOUT_MIN_ASPECT=0.75f; const val ROUNDED_SAFE_LAYOUT_MAX_ASPECT=1.8f
        const val BALLOON_VERTICAL_REACH_IN_GLYPHS=14f; const val MIN_SOURCE_DIMENSION_RATIO=0.55f; const val MAX_COLOR_DISTANCE_SQUARED=6400; const val MAX_CLUSTER_DISTANCE_SQUARED=4900; const val MAX_LUMINANCE_DELTA=58; const val COLOR_BUCKET_SIZE=32
    }
}

internal object SpeechBubbleSizePolicy {
    fun accepts(sourceWidth:Float,sourceHeight:Float,detectedWidth:Float,detectedHeight:Float):Boolean { val sourceArea=(sourceWidth*sourceHeight).coerceAtLeast(1f); val detectedArea=detectedWidth*detectedHeight; if(detectedArea<=sourceArea*STANDARD_MAX_AREA_RATIO)return true; val aspectRatio=detectedWidth/detectedHeight.coerceAtLeast(1f); val resemblesRoundBalloon=aspectRatio in ROUND_BALLOON_MIN_ASPECT_RATIO..ROUND_BALLOON_MAX_ASPECT_RATIO; return resemblesRoundBalloon && detectedArea<=sourceArea*LARGE_ROUND_BALLOON_MAX_AREA_RATIO }
    private const val STANDARD_MAX_AREA_RATIO=14f; private const val LARGE_ROUND_BALLOON_MAX_AREA_RATIO=140f; private const val ROUND_BALLOON_MIN_ASPECT_RATIO=0.45f; private const val ROUND_BALLOON_MAX_ASPECT_RATIO=2.2f
}
