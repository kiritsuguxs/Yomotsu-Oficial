package eu.kanade.tachiyomi.ui.browse.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
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
    private val manager: NovelExtensionManager = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
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
                preferences.enabledLanguages.changes(),
                manager.isRefreshing,
            ) { query, installed, available, enabledLanguages, isRefreshing ->
                val filteredInstalled = installed.filter {
                    query.isNullOrBlank() || it.plugin.name.contains(query, ignoreCase = true)
                }
                val filteredAvailable = available.filter {
                    (query.isNullOrBlank() || it.plugin.name.contains(query, ignoreCase = true)) &&
                        matchesLanguage(it.plugin.lang, enabledLanguages)
                }
                State(
                    isLoading = false,
                    isRefreshing = isRefreshing,
                    isEmpty = filteredInstalled.isEmpty() && filteredAvailable.isEmpty(),
                    installed = filteredInstalled,
                    available = filteredAvailable,
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    private fun matchesLanguage(pluginLang: String, enabledLanguages: Set<String>): Boolean {
        if (enabledLanguages.isEmpty() || "all" in enabledLanguages) return true
        val normalized = pluginLang.trim().lowercase()
        val langCode = when {
            normalized.contains("portugu") || normalized == "pt" || normalized == "pt-br" -> "pt"
            normalized.contains("english") || normalized == "en" -> "en"
            normalized.contains("español") || normalized.contains("espanol") || normalized == "es" -> "es"
            normalized.contains("franç") || normalized.contains("franc") || normalized == "fr" -> "fr"
            normalized.contains("indonesia") || normalized == "id" -> "id"
            normalized.contains("polski") || normalized == "pl" -> "pl"
            normalized.contains("việt") || normalized.contains("viet") || normalized == "vi" -> "vi"
            normalized.contains("türk") || normalized.contains("turk") || normalized == "tr" -> "tr"
            normalized.contains("русский") || normalized == "ru" -> "ru"
            normalized.contains("укра") || normalized == "uk" -> "uk"
            normalized.contains("ไทย") || normalized == "th" -> "th"
            normalized.contains("عرب") || normalized == "ar" -> "ar"
            normalized.contains("中文") || normalized == "zh" -> "zh"
            normalized.contains("日本") || normalized == "ja" -> "ja"
            normalized.contains("한국") || normalized.contains("조선") || normalized == "ko" -> "ko"
            normalized.contains("multi") -> "all"
            else -> normalized
        }
        return langCode in enabledLanguages ||
            (langCode == "pt" && ("pt-BR" in enabledLanguages || "pt-PT" in enabledLanguages || "pt" in enabledLanguages)) ||
            (langCode == "zh" && ("zh-Hans" in enabledLanguages || "zh-Hant" in enabledLanguages)) ||
            langCode == "all" ||
            pluginLang in enabledLanguages
    }

    fun search(query: String?) {
        _searchQuery.value = query
    }

    fun refresh() {
        manager.refreshAvailablePlugins()
    }

    fun installExtension(plugin: NovelPlugin) {
        manager.installPlugin(plugin) {
            // Callback when install finishes
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
