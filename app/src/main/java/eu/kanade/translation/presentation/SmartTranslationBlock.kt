package eu.kanade.translation.presentation

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.normalizeTranslationText
import eu.kanade.translation.model.resolvedLayoutRegion
import eu.kanade.translation.model.withReliableSourceMetrics
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SmartTranslationBlock(
    modifier: Modifier = Modifier,
    block: TranslationBlock,
    scaleFactor: Float,
    pageWidth: Float,
    pageHeight: Float,
    fontFamily: FontFamily,
    onLongClick: (() -> Unit)? = null,
) {
    val region = block.resolvedLayoutRegion(pageWidth, pageHeight)
    val xPx = region.x * scaleFactor
    val yPx = region.y * scaleFactor
    val width = (region.width * scaleFactor).pxToDp()
    val height = (region.height * scaleFactor).pxToDp()
    val rotation = block.angle.takeIf { abs(it) in 12f..78f } ?: 0f
    val cleanText = normalizeTranslationText(block.translation)
    if (cleanText.isEmpty()) {
        if (onLongClick != null) {
            Box(
                modifier = modifier
                    .offset(xPx.pxToDp(), yPx.pxToDp())
                    .requiredSize(width, height)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick,
                    ),
            )
        }
        return
    }

    Box(
        modifier = modifier
            .offset(xPx.pxToDp(), yPx.pxToDp())
            .requiredSize(width, height)
            .clipToBounds()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        SubcomposeLayout { _ ->
            val outerWidthPx = with(density) { width.roundToPx() }
            val outerHeightPx = with(density) { height.roundToPx() }
            val sourceSymbolHeightSp = block.withReliableSourceMetrics().symHeight * scaleFactor /
                (density.density * density.fontScale).coerceAtLeast(0.0001f)
            val fontSizeCeiling = (sourceSymbolHeightSp * SOURCE_FONT_SIZE_MULTIPLIER)
                .roundToInt()
                .coerceIn(ABSOLUTE_MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)

            var fitted: FittedTranslationText? = null
            var readableFloorFallback: FittedTranslationText? = null
            val profiles = TranslationFitPolicy.progressiveProfiles(block)
            for ((profileIndex, profile) in profiles.withIndex()) {
                if (fitted != null) break
                val envelopeWidthPx = (outerWidthPx * profile.widthRatio).toInt().coerceAtLeast(1)
                val envelopeHeightPx = (outerHeightPx * profile.heightRatio).toInt().coerceAtLeast(1)
                val rotationScale = TranslationRotationFit.scaleToFit(
                    outerWidth = envelopeWidthPx.toFloat(),
                    outerHeight = envelopeHeightPx.toFloat(),
                    contentWidth = envelopeWidthPx.toFloat(),
                    contentHeight = envelopeHeightPx.toFloat(),
                    angleDegrees = rotation,
                )
                val innerWidthPx = (envelopeWidthPx * rotationScale).toInt().coerceAtLeast(1)
                val innerHeightPx = (envelopeHeightPx * rotationScale).toInt().coerceAtLeast(1)
                val innerWidth = with(density) { innerWidthPx.toDp() }
                val minimum = profile.minimumFontSizeSp.coerceAtMost(fontSizeCeiling)
                val selection = TranslationFontSizeSearch.selectWithFloor(
                    minimum = minimum,
                    maximum = fontSizeCeiling,
                ) { candidate ->
                    val paragraph = subcompose("paragraph-$profileIndex-$candidate") {
                        TranslationText(
                            text = cleanText,
                            fontSizeSp = candidate,
                            fontFamily = fontFamily,
                            color = block.foregroundColor?.let { Color(it) } ?: Color.Black,
                            width = innerWidth,
                        )
                    }[0].measure(Constraints(maxWidth = innerWidthPx))
                    paragraph.height <= innerHeightPx
                }
                val candidate = FittedTranslationText(innerWidthPx, innerHeightPx, selection.fontSizeSp)
                if (selection.fits) {
                    fitted = candidate
                } else {
                    readableFloorFallback = candidate
                }
            }

            val selected = fitted ?: requireNotNull(readableFloorFallback)
            val innerWidth = with(density) { selected.widthPx.toDp() }

            val placeable = subcompose("final") {
                TranslationText(
                    text = cleanText,
                    fontSizeSp = selected.fontSizeSp,
                    fontFamily = fontFamily,
                    color = block.foregroundColor?.let { Color(it) } ?: Color.Black,
                    width = innerWidth,
                    modifier = Modifier.rotate(rotation),
                )
            }[0].measure(Constraints(maxWidth = selected.widthPx))

            layout(outerWidthPx, outerHeightPx) {
                placeable.placeRelative(
                    x = (outerWidthPx - placeable.width) / 2,
                    y = (outerHeightPx - placeable.height) / 2,
                )
            }
        }
    }
}

@Composable
private fun TranslationText(
    text: String,
    fontSizeSp: Int,
    fontFamily: FontFamily,
    color: Color,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 0.98f).sp,
        fontFamily = fontFamily,
        color = color,
        softWrap = true,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        style = androidx.compose.ui.text.TextStyle(
            lineBreak = LineBreak.Paragraph,
            // Automatic hyphenation turns ordinary Portuguese words into
            // artifacts such as "CON- VOCADOS" in narrow speech balloons.
            // Compose still wraps naturally at spaces with hyphenation off.
            hyphens = Hyphens.None,
        ),
        modifier = modifier.width(width),
    )
}

private data class FittedTranslationText(
    val widthPx: Int,
    val heightPx: Int,
    val fontSizeSp: Int,
)

private const val MAX_FONT_SIZE_SP = 48
private const val ABSOLUTE_MIN_FONT_SIZE_SP = 4
private const val SOURCE_FONT_SIZE_MULTIPLIER = 1.45f
