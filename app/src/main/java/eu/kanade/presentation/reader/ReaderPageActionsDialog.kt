package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.i18n.MR
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: () -> Unit,
    onShare: (Boolean) -> Unit,
    onSave: () -> Unit,
    onAddManualTranslation: ((String, String, (Result<Unit>) -> Unit) -> Unit)?,
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    var showManualTranslationDialog by remember { mutableStateOf(false) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            if (onAddManualTranslation != null) {
                ActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(ATMR.strings.translation_manual_action),
                    icon = Icons.Outlined.Translate,
                    onClick = { showManualTranslationDialog = true },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.set_as_cover),
                    icon = Icons.Outlined.Photo,
                    onClick = { showSetCoverDialog = true },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_copy_to_clipboard),
                    icon = Icons.Outlined.ContentCopy,
                    onClick = {
                        onShare(true)
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_share),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        onShare(false)
                        onDismissRequest()
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_save),
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onSave()
                        onDismissRequest()
                    },
                )
            }
        }
    }

    if (showSetCoverDialog) {
        SetCoverDialog(
            onConfirm = {
                onSetAsCover()
                showSetCoverDialog = false
            },
            onDismiss = { showSetCoverDialog = false },
        )
    }

    if (showManualTranslationDialog && onAddManualTranslation != null) {
        AddManualTranslationDialog(
            onSave = onAddManualTranslation,
            onDismiss = { showManualTranslationDialog = false },
            onSaved = {
                showManualTranslationDialog = false
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun AddManualTranslationDialog(
    onSave: (String, String, (Result<Unit>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var sourceText by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(stringResource(ATMR.strings.translation_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(stringResource(ATMR.strings.translation_manual_hint))
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    enabled = !isWorking,
                    label = { Text(stringResource(ATMR.strings.translation_manual_original_field)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
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
                enabled = !isWorking && translation.isNotBlank(),
                onClick = {
                    isWorking = true
                    errorMessage = null
                    onSave(sourceText, translation) { result ->
                        isWorking = false
                        result.onSuccess { onSaved() }
                            .onFailure { error -> errorMessage = error.localizedMessage.orEmpty() }
                    }
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isWorking,
                onClick = onDismiss,
            ) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun SetCoverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(stringResource(MR.strings.confirm_set_image_as_cover))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}
