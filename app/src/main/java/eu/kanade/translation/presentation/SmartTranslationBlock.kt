package eu.kanade.translation.presentation

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
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
    val textMeasurer = rememberTextMeasurer()
    val baseTextStyle = LocalTextStyle.current
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
            val sourceFontSizeCeiling = (sourceSymbolHeightSp * SOURCE_FONT_SIZE_MULTIPLIER)
                .roundToInt()
                .coerceIn(ABSOLUTE_MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            val fontSizeCeiling = TranslationTextFit.maximumFontSize(sourceFontSizeCeiling, block.balloonDetected)

            var fitted: FittedTranslationText? = null
            var splitWordFallback: FittedTranslationText? = null
            var readableFloorFallback: FittedTranslationText? = null
            val profiles = TranslationFitPolicy.progressiveProfiles(block)
            for (profile in profiles) {
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
                val minimum = profile.minimumFontSizeSp.coerceAtMost(fontSizeCeiling)
                val selection = TranslationTextFit.select(
                    minimum = minimum,
                    maximum = fontSizeCeiling,
                ) { candidate ->
                    val paragraph = textMeasurer.measure(
                        text = cleanText,
                        style = translationTextStyle(baseTextStyle, candidate, fontFamily),
                        constraints = Constraints(minWidth = innerWidthPx, maxWidth = innerWidthPx),
                        softWrap = true,
                        overflow = TextOverflow.Clip,
                    )
                    TranslationTextFit.Measurement(
                        fits = !paragraph.hasVisualOverflow && paragraph.size.height <= innerHeightPx,
                        keepsWords = TranslationTextFit.keepsWords(
                            cleanText,
                            (0 until paragraph.lineCount - 1).map { paragraph.getLineEnd(it, visibleEnd = true) },
                        ),
                    )
                }
                val candidate = FittedTranslationText(innerWidthPx, innerHeightPx, selection.fontSizeSp)
                if (selection.fits && selection.keepsWords) {
                    fitted = candidate
                } else if (selection.fits) {
                    // Try the remaining existing safe envelopes before accepting
                    // an emergency word split; retain the original fitting fallback.
                    if (splitWordFallback == null) splitWordFallback = candidate
                } else {
                    readableFloorFallback = candidate
                }
            }

            val selected = fitted ?: splitWordFallback ?: requireNotNull(readableFloorFallback)
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
        style = translationTextStyle(LocalTextStyle.current, fontSizeSp, fontFamily),
        modifier = modifier.width(width),
    )
}

internal fun translationTextStyle(base: TextStyle, fontSizeSp: Int, fontFamily: FontFamily): TextStyle = base.copy(
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp * 0.98f).sp,
    fontFamily = fontFamily,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Paragraph,
    hyphens = Hyphens.None,
)

private data class FittedTranslationText(
    val widthPx: Int,
    val heightPx: Int,
    val fontSizeSp: Int,
)

private const val MAX_FONT_SIZE_SP = 48
private const val ABSOLUTE_MIN_FONT_SIZE_SP = 4
private const val SOURCE_FONT_SIZE_MULTIPLIER = 1.45f
