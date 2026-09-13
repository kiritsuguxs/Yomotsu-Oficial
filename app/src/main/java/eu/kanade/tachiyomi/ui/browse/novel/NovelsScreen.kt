package eu.kanade.tachiyomi.ui.browse.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun NovelsScreen(
    state: NovelsViewModel.State,
    contentPadding: PaddingValues,
    onInstallExtension: (NovelPlugin) -> Unit,
    onUninstallExtension: (String) -> Unit,
    onUpdateExtension: (NovelPlugin) -> Unit,
    onOpenWebView: (NovelPlugin) -> Unit,
) {
    var detailsExtension by remember { mutableStateOf<NovelExtension.Installed?>(null) }
    var uninstallConfirmPlugin by remember { mutableStateOf<NovelPlugin?>(null) }

    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        if (state.updates.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(MR.strings.ext_updates_pending),
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    style = MaterialTheme.typography.header,
                )
            }
            items(state.updates, key = { "update_${it.plugin.id}" }) { extension ->
                NovelExtensionItem(
                    extension = extension,
                    onOpenDetails = { detailsExtension = extension },
                    onAction = { onUpdateExtension(extension.plugin) },
                    onOpenWebView = { onOpenWebView(extension.plugin) },
                    isUpdate = true,
                )
            }
        }

        if (state.installed.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(MR.strings.ext_installed),
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    style = MaterialTheme.typography.header,
                )
            }
            items(state.installed, key = { "installed_${it.plugin.id}" }) { extension ->
                NovelExtensionItem(
                    extension = extension,
                    onOpenDetails = { detailsExtension = extension },
                    onAction = { detailsExtension = extension },
                    onOpenWebView = { onOpenWebView(extension.plugin) },
                    isUpdate = false,
                )
            }
        }

        if (state.available.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(MR.strings.ext_available),
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                    style = MaterialTheme.typography.header,
                )
            }
            items(state.available, key = { "available_${it.plugin.id}_${it.plugin.lang}" }) { extension ->
                NovelExtensionItem(
                    extension = extension,
                    onOpenDetails = { onInstallExtension(extension.plugin) },
                    onAction = { onInstallExtension(extension.plugin) },
                    onOpenWebView = { onOpenWebView(extension.plugin) },
                    isUpdate = false,
                )
            }
        }
    }

    detailsExtension?.let { installed ->
        NovelExtensionDetailsDialog(
            extension = installed,
            onDismissRequest = { detailsExtension = null },
            onUpdate = {
                detailsExtension = null
                onUpdateExtension(installed.plugin)
            },
            onUninstall = {
                detailsExtension = null
                uninstallConfirmPlugin = installed.plugin
            },
            onOpenWebView = {
                detailsExtension = null
                onOpenWebView(installed.plugin)
            },
        )
    }

    uninstallConfirmPlugin?.let { plugin ->
        AlertDialog(
            onDismissRequest = { uninstallConfirmPlugin = null },
            title = { Text(text = stringResource(MR.strings.ext_confirm_remove)) },
            text = { Text(text = plugin.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = plugin.id
                        uninstallConfirmPlugin = null
                        onUninstallExtension(id)
                    },
                ) {
                    Text(text = stringResource(MR.strings.ext_uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallConfirmPlugin = null }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun NovelExtensionItem(
    extension: NovelExtension,
    onOpenDetails: () -> Unit,
    onAction: () -> Unit,
    onOpenWebView: () -> Unit,
    isUpdate: Boolean,
    modifier: Modifier = Modifier,
) {
    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onOpenDetails,
        icon = {
            if (!extension.plugin.iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = extension.plugin.iconUrl,
                    contentDescription = null,
                    placeholder = ColorPainter(Color(0x1F888888)),
                    error = rememberResourceBitmapPainter(id = R.mipmap.ic_default_source),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (extension.plugin.site.isNotBlank()) {
                    IconButton(onClick = onOpenWebView) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = stringResource(MR.strings.action_open_in_web_view),
                        )
                    }
                }
                when {
                    isUpdate -> {
                        IconButton(onClick = onAction) {
                            Icon(
                                imageVector = Icons.Outlined.GetApp,
                                contentDescription = stringResource(MR.strings.ext_update),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    extension is NovelExtension.Installed -> {
                        IconButton(onClick = onAction) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(MR.strings.action_settings),
                            )
                        }
                    }
                    extension is NovelExtension.Available -> {
                        IconButton(onClick = onAction) {
                            Icon(
                                imageVector = Icons.Outlined.GetApp,
                                contentDescription = stringResource(MR.strings.ext_install),
                            )
                        }
                    }
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium),
        ) {
            Text(
                text = extension.plugin.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                modifier = Modifier.secondaryItemAlpha(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                    if (extension.plugin.lang.isNotEmpty()) {
                        Text(text = extension.plugin.lang)
                    }
                    if (extension.plugin.version.isNotEmpty()) {
                        Text(text = "•")
                        Text(text = extension.plugin.version)
                    }
                    if (isUpdate) {
                        Text(text = "•")
                        Text(
                            text = stringResource(MR.strings.ext_update),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelExtensionDetailsDialog(
    extension: NovelExtension.Installed,
    onDismissRequest: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onOpenWebView: () -> Unit,
) {
    val plugin = extension.plugin
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!plugin.iconUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = plugin.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                    )
                }
                Text(text = plugin.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plugin.lang.isNotBlank()) {
                    Text(
                        text = "Idioma: ${plugin.lang}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (plugin.version.isNotBlank()) {
                    Text(
                        text = "Versão: ${plugin.version}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (extension.hasUpdate) {
                    Text(
                        text = "Atualização disponível!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (plugin.site.isNotBlank()) {
                    OutlinedButton(
                        onClick = onOpenWebView,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(MR.strings.action_open_in_web_view))
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (extension.hasUpdate) {
                    Button(onClick = onUpdate) {
                        Text(text = stringResource(MR.strings.ext_update))
                    }
                }
                Button(
                    onClick = onUninstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = stringResource(MR.strings.ext_uninstall))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_close))
            }
        },
    )
}
