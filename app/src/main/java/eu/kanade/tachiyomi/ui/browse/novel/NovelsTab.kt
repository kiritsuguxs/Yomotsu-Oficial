package eu.kanade.tachiyomi.ui.browse.novel

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen

import androidx.compose.material.icons.outlined.TravelExplore
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalNovelSearchScreen

@Composable
fun novelsTab(
    viewModel: NovelsViewModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_novels,
        searchEnabled = true,
        actions = listOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.extensionStores),
                onClick = { navigator.push(ExtensionStoresScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            PullRefresh(
                refreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                enabled = !state.isLoading,
            ) {
                when {
                    state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                    state.isEmpty -> {
                        EmptyScreen(
                            stringRes = MR.strings.empty_screen,
                            modifier = Modifier.padding(contentPadding),
                            actions = listOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.extensionStores,
                                    icon = Icons.Outlined.Settings,
                                    onClick = { navigator.push(ExtensionStoresScreen()) },
                                ),
                            ),
                        )
                    }
                    else -> {
                        NovelsScreen(
                            state = state,
                            contentPadding = contentPadding,
                            onInstallExtension = viewModel::installExtension,
                            onUninstallExtension = viewModel::uninstallExtension,
                            onOpenSource = { sourceId ->
                                navigator.push(eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen(sourceId))
                            },
                        )
                    }
                }
            }
        },
    )
}
