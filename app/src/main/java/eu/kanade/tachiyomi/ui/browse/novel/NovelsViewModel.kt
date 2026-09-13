package eu.kanade.tachiyomi.ui.browse.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelsViewModel(
    private val manager: NovelExtensionManager = Injekt.get()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery = _searchQuery.asStateFlow()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _searchQuery,
                manager.installedExtensions,
                manager.availableExtensions,
                manager.isRefreshing
            ) { query, installed, available, isRefreshing ->
                val filteredInstalled = installed.filter {
                    query.isNullOrBlank() || it.plugin.name.contains(query, ignoreCase = true)
                }
                val filteredAvailable = available.filter {
                    query.isNullOrBlank() || it.plugin.name.contains(query, ignoreCase = true)
                }
                State(
                    isLoading = false,
                    isRefreshing = isRefreshing,
                    installed = filteredInstalled,
                    available = filteredAvailable,
                    isEmpty = installed.isEmpty() && available.isEmpty()
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun search(query: String?) {
        _searchQuery.value = query
    }

    fun refresh() {
        manager.refreshAvailablePlugins()
    }

    fun installExtension(plugin: NovelPlugin) {
        manager.installPlugin(plugin) {
            // Toast or snackbar could be handled here or by UI observing state
        }
    }

    fun uninstallExtension(pluginId: String) {
        manager.uninstallPlugin(pluginId)
    }

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isEmpty: Boolean = true,
        val installed: List<NovelExtension.Installed> = emptyList(),
        val available: List<NovelExtension.Available> = emptyList(),
    )
}
