package eu.kanade.tachiyomi.extension.novel

import android.app.Application
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.extension.repository.ExtensionStoreRepository
import okio.buffer
import okio.sink
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.CancellationException

class NovelExtensionManager(
    private val app: Application = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val extensionStoreRepository: ExtensionStoreRepository = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pluginsDir = File(app.filesDir, "novel_plugins").apply { mkdirs() }

    private val _installedExtensions = MutableStateFlow<List<NovelExtension.Installed>>(emptyList())
    val installedExtensions: StateFlow<List<NovelExtension.Installed>> = _installedExtensions.asStateFlow()

    private val _availableExtensions = MutableStateFlow<List<NovelExtension.Available>>(emptyList())
    val availableExtensions: StateFlow<List<NovelExtension.Available>> = _availableExtensions.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadInstalledExtensions()
        refreshAvailablePlugins()
    }

    fun loadInstalledExtensions() {
        val installed = mutableListOf<NovelExtension.Installed>()
        // TODO: read metadata from a local JSON or SharedPreferences to populate the plugin fully.
        // For now, if the JS file exists, we just list it minimally.
        pluginsDir.listFiles { file -> file.extension == "js" }?.forEach { file ->
            val id = file.nameWithoutExtension
            val plugin = NovelPlugin(id, name = id, url = "", lang = "")
            installed.add(
                NovelExtension.Installed(
                    plugin = plugin,
                    localPath = file.absolutePath,
                    sources = listOf(NovelSourceWrapper(plugin))
                )
            )
        }
        _installedExtensions.value = installed
    }

    fun refreshAvailablePlugins() {
        scope.launch {
            _isRefreshing.value = true
            try {
                val stores = extensionStoreRepository.getAll().filter {
                    it.signingKey == "NOVEL_REPO" || it.badgeLabel.equals("Novel", ignoreCase = true) || it.indexUrl.contains("plugins", ignoreCase = true)
                }
                val allPlugins = mutableListOf<NovelPlugin>()
                for (store in stores) {
                    try {
                        val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                        val content = response.body.string()
                        val plugins = json.decodeFromString<List<NovelPlugin>>(content)
                        allPlugins.addAll(plugins)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to load novel plugins from ${store.indexUrl}" }
                    }
                }
                
                val installedIds = _installedExtensions.value.map { it.plugin.id }.toSet()
                _availableExtensions.value = allPlugins
                    .filterNot { installedIds.contains(it.id) }
                    .map { NovelExtension.Available(it) }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun installPlugin(plugin: NovelPlugin, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val response = network.client.newCall(GET(plugin.url)).awaitSuccess()
                val targetFile = File(pluginsDir, "${plugin.id}.js")
                targetFile.sink().buffer().use { sink ->
                    sink.writeAll(response.body.source())
                }
                // TODO: Save full metadata
                loadInstalledExtensions()
                refreshAvailablePlugins()
                onComplete(true)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to install novel plugin ${plugin.name}" }
                onComplete(false)
            }
        }
    }

    fun uninstallPlugin(pluginId: String) {
        val targetFile = File(pluginsDir, "$pluginId.js")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        // TODO: Remove full metadata
        loadInstalledExtensions()
        refreshAvailablePlugins()
    }
}
