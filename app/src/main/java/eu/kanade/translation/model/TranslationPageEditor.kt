package eu.kanade.translation.model

/**
 * Reader-only actions for one persisted translated page.
 *
 * Both operations report the text that was actually stored so the overlay can
 * update only after the chapter JSON has been written successfully.
 */
class TranslationPageEditor(
    val saveTranslation: (blockIndex: Int, translation: String, onResult: (Result<String>) -> Unit) -> Unit,
    val retranslate: (blockIndex: Int, onResult: (Result<String>) -> Unit) -> Unit,
    val addManualTranslation: (
        position: ManualTranslationPosition,
        sourceText: String,
        translation: String,
        onResult: (Result<AddedManualTranslation>) -> Unit,
    ) -> Unit,
)

/**
 * Position of a long press in source-image coordinates.
 *
 * The page dimensions travel with the point because pages that ML Kit did not
 * recognize are intentionally absent from older translation files.
 */
data class ManualTranslationPosition(
    val x: Float,
    val y: Float,
    val pageWidth: Float,
    val pageHeight: Float,
)

/** Result stored by [TranslationPageEditor.addManualTranslation]. */
data class AddedManualTranslation(
    val blockIndex: Int,
    val block: TranslationBlock,
    val pageWidth: Float,
    val pageHeight: Float,
)
