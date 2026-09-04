package eu.kanade.translation.detection

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.presentation.TranslationCleanupBlock

class DbnetCleanupMaskTestActivity : ComponentActivity() {
    lateinit var composeView: ComposeView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scaleFactor = intent.getFloatExtra(EXTRA_SCALE_FACTOR, 1f)
        val cleanupMask = checkNotNull(
            DbnetCleanupMask.fromRuns(
                pageWidth = PAGE_SIZE,
                pageHeight = PAGE_SIZE,
                runs = intArrayOf(
                    10, 10, 4,
                    30, 30, 4,
                ),
            ),
        )
        val block = TranslationBlock(
            text = "Source",
            translation = "Translated",
            width = 1f,
            height = 1f,
            x = 0f,
            y = 0f,
            symHeight = 1f,
            symWidth = 1f,
            angle = 0f,
            backgroundColor = 0xFFFFFFFF.toInt(),
            dbnetCleanupMask = cleanupMask,
        )

        composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(VIEWPORT_SIZE, VIEWPORT_SIZE)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    ) {
                        TranslationCleanupBlock(
                            block = block,
                            scaleFactor = scaleFactor,
                            pageWidth = PAGE_SIZE.toFloat(),
                            pageHeight = PAGE_SIZE.toFloat(),
                        )
                    }
                }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(composeView)
            },
        )
    }

    companion object {
        const val EXTRA_SCALE_FACTOR = "scale_factor"
        const val PAGE_SIZE = 100
        const val VIEWPORT_SIZE = 100
    }
}
