package eu.kanade.translation.presentation

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.IntSize
import androidx.core.view.isVisible
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.detection.DbnetCleanupMask
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationPageEditor

class WebtoonTranslationsView : AbstractComposeView {

    private val translation: PageTranslation
    private val font: TranslationFont
    private val fontFamily: FontFamily
    private val editor: TranslationPageEditor?
    private var refreshRevision by mutableStateOf(0)

    fun refreshContent() {
        refreshRevision++
    }

    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        translation = PageTranslation.EMPTY
        font = TranslationFont.ANIME_ACE
        editor = null
        fontFamily = Font(resId = font.res, weight = FontWeight.Bold).toFontFamily()
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        translation: PageTranslation,
        font: TranslationFont? = null,
        editor: TranslationPageEditor? = null,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = translation
        this.font = font ?: TranslationFont.ANIME_ACE
        this.editor = editor
        fontFamily = Font(resId = this.font.res, weight = FontWeight.Bold).toFontFamily()
    }

    @Composable
    override fun Content() {
        var size by remember { mutableStateOf(IntSize.Zero) }
        var editingBlockIndex by remember { mutableStateOf<Int?>(null) }
        var contentRevision by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (size == IntSize.Zero) hide() else show()
                },
        ) {
            if (size == IntSize.Zero || translation.imgWidth <= 0f) return@Box
            val scaleFactor = size.width / translation.imgWidth
            val visibleRevision = contentRevision + refreshRevision
            key(visibleRevision) {
                TextBlockBackground(scaleFactor)
                TextBlockContent(
                    scaleFactor = scaleFactor,
                    contentRevision = visibleRevision,
                    onEdit = editor?.let {
                        { index -> editingBlockIndex = index }
                    },
                )
            }
        }
        val blockIndex = editingBlockIndex
        if (blockIndex != null) {
            val block = translation.blocks.getOrNull(blockIndex)
            val activeEditor = editor
            if (block != null && activeEditor != null) {
                TranslationBlockEditorDialog(
                    blockIndex = blockIndex,
                    block = block,
                    editor = activeEditor,
                    onTranslationChanged = { translatedText ->
                        block.translation = translatedText
                        contentRevision++
                    },
                    onDismissRequest = { editingBlockIndex = null },
                )
            }
        }
    }

    @Composable
    private fun TextBlockBackground(scaleFactor: Float) {
        val experimentalMaskPageValid = DbnetCleanupMask.areValidForPage(
            translation.blocks.asSequence().mapNotNull { it.dbnetCleanupMask },
            translation.imgWidth,
            translation.imgHeight,
        )
        translation.blocks.filter { it.translation.isNotBlank() }.forEach { block ->
            TranslationCleanupBlock(
                block = block,
                scaleFactor = scaleFactor,
                pageWidth = translation.imgWidth,
                pageHeight = translation.imgHeight,
                experimentalMaskPageValid = experimentalMaskPageValid,
            )
        }
    }

    @Composable
    private fun TextBlockContent(
        scaleFactor: Float,
        contentRevision: Int,
        onEdit: ((Int) -> Unit)?,
    ) {
        key(contentRevision) {
            translation.blocks.forEachIndexed { index, block ->
                SmartTranslationBlock(
                    block = block,
                    scaleFactor = scaleFactor,
                    pageWidth = translation.imgWidth,
                    pageHeight = translation.imgHeight,
                    fontFamily = fontFamily,
                    onLongClick = onEdit?.let { { it(index) } },
                )
            }
        }
    }

    fun show() {
        isVisible = true
    }
    fun hide() {
        isVisible = false
    }
}
