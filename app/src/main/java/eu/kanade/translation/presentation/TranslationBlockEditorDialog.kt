package eu.kanade.translation.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationPageEditor
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TranslationBlockEditorDialog(
    blockIndex: Int,
    block: TranslationBlock,
    editor: TranslationPageEditor,
    onTranslationChanged: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var translation by remember(blockIndex) { mutableStateOf(block.translation) }
    var isWorking by remember(blockIndex) { mutableStateOf(false) }
    var errorMessage by remember(blockIndex) { mutableStateOf<String?>(null) }

    fun handleResult(result: Result<String>, dismissAfterSuccess: Boolean) {
        isWorking = false
        result.onSuccess { storedTranslation ->
            translation = storedTranslation
            onTranslationChanged(storedTranslation)
            if (dismissAfterSuccess) onDismissRequest()
        }.onFailure { error ->
            errorMessage = error.localizedMessage
                ?.takeIf(String::isNotBlank)
                ?: ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismissRequest() },
        title = { Text(stringResource(ATMR.strings.translation_editor_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(ATMR.strings.translation_editor_original, block.text),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = translation,
                    onValueChange = {
                        translation = it
                        errorMessage = null
                    },
                    enabled = !isWorking,
                    label = { Text(stringResource(ATMR.strings.translation_editor_field)) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { message ->
                        {
                            val visibleMessage = if (message.isBlank()) {
                                stringResource(ATMR.strings.translation_editor_error)
                            } else {
                                message
                            }
                            Text(visibleMessage)
                        }
                    },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isWorking && translation.isNotBlank() && translation.trim() != block.translation.trim(),
                onClick = {
                    isWorking = true
                    errorMessage = null
                    editor.saveTranslation(blockIndex, translation) { result ->
                        handleResult(result, dismissAfterSuccess = true)
                    }
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            Row {
                if (block.translation.isNotBlank()) {
                    TextButton(
                        enabled = !isWorking,
                        onClick = {
                            isWorking = true
                            errorMessage = null
                            editor.retranslate(blockIndex) { result ->
                                handleResult(result, dismissAfterSuccess = false)
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (isWorking) {
                                    ATMR.strings.translation_editor_retranslating
                                } else {
                                    ATMR.strings.translation_editor_retranslate
                                },
                            ),
                        )
                    }
                }
                TextButton(
                    enabled = !isWorking,
                    onClick = onDismissRequest,
                ) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
        },
    )
}
