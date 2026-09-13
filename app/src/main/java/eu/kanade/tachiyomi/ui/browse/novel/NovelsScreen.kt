package eu.kanade.tachiyomi.ui.browse.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.components.BaseBrowseItem
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
    onOpenSource: ((Long) -> Unit)? = null,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
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
                    onClickInstall = { onInstallExtension(extension.plugin) },
                    onClickUninstall = { onUninstallExtension(extension.plugin.id) },
                    onClickItem = {
                        extension.sources.firstOrNull()?.let { onOpenSource?.invoke(it.id) }
                    },
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
                    onClickInstall = { onInstallExtension(extension.plugin) },
                    onClickUninstall = { onUninstallExtension(extension.plugin.id) }
                )
            }
        }
    }
}

@Composable
private fun NovelExtensionItem(
    extension: NovelExtension,
    onClickInstall: () -> Unit,
    onClickUninstall: () -> Unit,
    modifier: Modifier = Modifier,
    onClickItem: () -> Unit = {
        if (extension is NovelExtension.Available) onClickInstall()
    },
) {
    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onClickItem,
        icon = {
            if (!extension.plugin.iconUrl.isNullOrEmpty()) {
                coil3.compose.AsyncImage(
                    model = extension.plugin.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        action = {
            when (extension) {
                is NovelExtension.Installed -> {
                    IconButton(onClick = onClickUninstall) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_delete)
                        )
                    }
                }
                is NovelExtension.Available -> {
                    IconButton(onClick = onClickInstall) {
                        Icon(
                            imageVector = Icons.Outlined.GetApp,
                            contentDescription = stringResource(MR.strings.action_install)
                        )
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
                }
            }
        }
    }
}
