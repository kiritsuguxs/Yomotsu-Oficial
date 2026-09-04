package eu.kanade.translation.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp
import eu.kanade.translation.model.DbnetMaskCleanup
import eu.kanade.translation.model.LegacyCleanup
import eu.kanade.translation.model.NoCleanup
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.resolveCleanup

@Composable
fun TranslationCleanupBlock(
    block: TranslationBlock,
    scaleFactor: Float,
    pageWidth: Float,
    pageHeight: Float,
    experimentalMaskPageValid: Boolean = true,
) {
    when (val cleanup = block.resolveCleanup(pageWidth, pageHeight, experimentalMaskPageValid)) {
        is LegacyCleanup -> cleanup.patches.forEach { patch ->
            val region = patch.region
            val color = block.backgroundColor?.let { Color(it) } ?: Color.White
            val cleanupShape = if (block.balloonDetected || (!block.balloonDetected && block.backgroundColor == null)) {
                // Manual fallback blocks deliberately avoid reopening the page bitmap.
                // A soft oval hides their cleanup boundary inside common speech bubbles
                // without bringing back the archive decode that could close the reader.
                // The detected balloon background is rounded too. A near-square
                // cleanup mask exposed small white side tabs on oval balloons.
                RoundedCornerShape((patch.cornerRadius * scaleFactor).pxToDp())
            } else {
                RoundedCornerShape(minOf(3.dp, (patch.cornerRadius * scaleFactor).pxToDp()))
            }
            Box(
                modifier = Modifier
                    .offset((region.x * scaleFactor).pxToDp(), (region.y * scaleFactor).pxToDp())
                    .requiredSize(
                        (region.width * scaleFactor).pxToDp(),
                        (region.height * scaleFactor).pxToDp(),
                    )
                    // OCR bounds are axis-aligned. Rotating the eraser exposed their corners
                    // in Y8, so cleanup intentionally stays axis-aligned in Y9.
                    .background(color, cleanupShape),
            )
        }
        is DbnetMaskCleanup -> {
            val width = cleanup.mask.pageWidth
            val height = cleanup.mask.pageHeight
            val paint = Paint().apply {
                color = block.backgroundColor?.let { Color(it) } ?: Color.White
                isAntiAlias = false
            }
            Canvas(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(
                        (pageWidth * scaleFactor).pxToDp(),
                        (pageHeight * scaleFactor).pxToDp(),
                    ),
            ) {
                cleanup.mask.forEachRun(width, height) { y, x, length ->
                    drawContext.canvas.drawRect(
                        x * scaleFactor,
                        y * scaleFactor,
                        (x + length) * scaleFactor,
                        (y + 1) * scaleFactor,
                        paint,
                    )
                }
            }
        }
        NoCleanup -> Unit
    }
}
