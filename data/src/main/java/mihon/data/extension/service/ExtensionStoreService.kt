package mihon.data.extension.service

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.model.NetworkExtensionStore
import mihon.data.extension.model.NetworkLegacyExtension
import mihon.data.extension.model.NetworkLegacyExtensionRepo
import mihon.data.extension.model.toAvailableExtensions
import mihon.domain.extension.model.ExtensionStore
import okio.BufferedSource
import okio.buffer
import okio.gzip
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class ExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    suspend fun fetch(indexUrl: String): Result<ExtensionStore> {
        val candidates = buildList {
            add(indexUrl)
            val trimmed = indexUrl.trim().trimEnd('/')
            if (!indexUrl.endsWith(".json", ignoreCase = true) && !indexUrl.endsWith(".pb", ignoreCase = true)) {
                add("$trimmed/plugins.min.json")
                add("$trimmed/index.min.json")
                add("$trimmed/repo.json")
            }
        }
        for ((i, candidate) in candidates.withIndex()) {
            val result = fetchSingle(candidate)
            if (result.isSuccess || i == candidates.lastIndex) {
                return result
            }
        }
        return Result.failure(Exception("Not found"))
    }

    private suspend fun fetchSingle(indexUrl: String): Result<ExtensionStore> {
        var updatedIndexUrl: String = indexUrl
        return try {
            val response = network.client.newCall(GET(updatedIndexUrl)).awaitSuccess()
            val store = response.body.source().decompressIfGzipped().use { source ->
                val networkStore = when (source.peek().readByte()) {
                    // "[..."
                    0x5B.toByte() -> run {
                        val peekedSource = source.peek()
                        val sample = peekedSource.readUtf8(minOf(peekedSource.buffer.size, 1024L))
                        if (updatedIndexUrl.contains("plugins", ignoreCase = true) || sample.contains("\"site\"") || sample.contains(".js\"")) {
                            return@run NetworkExtensionStore(
                                name = "LNReader Plugins",
                                badgeLabel = "Novel",
                                signingKey = "NOVEL_REPO",
                                contact = NetworkExtensionStore.Contact(website = "https://github.com/lnreader/lnreader-plugins", discord = null),
                                extensionList = null,
                                extensionListUrl = updatedIndexUrl,
                            )
                        }
                        if (!indexUrl.endsWith("/index.min.json")) {
                            throw IllegalArgumentException("Provided legacy store url is not valid")
                        }
                        updatedIndexUrl = indexUrl.replace("/index.min.json", "/repo.json")
                        network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().body.source().use {
                            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it)
                        }
                    }
                    // "{..."
                    0x7B.toByte() -> try {
                        json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                    } catch (_: IllegalArgumentException) {
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                    }
                    else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                }

                if (networkStore is NetworkLegacyExtensionRepo && networkStore.indexV2 != null) {
                    return fetchSingle(networkStore.indexV2)
                }

                networkStore.toExtensionStore(updatedIndexUrl)
            }
            Result.success(store)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to add extension store '$updatedIndexUrl'"
            }
            Result.failure(e)
        }
    }

    suspend fun getExtensions(store: ExtensionStore): Result<List<Extension.Available>> {
        if (store.signingKey == "NOVEL_REPO" || store.badgeLabel.equals("Novel", ignoreCase = true) || store.indexUrl.contains("plugins", ignoreCase = true)) {
            return Result.success(emptyList())
        }
        return try {
            val extensions = if (store.extensionListUrl != null) {
                val response = network.client.newCall(GET(store.extensionListUrl!!)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore.ExtensionList>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(
                            source.readByteArray(),
                        )
                    }
                        .toAvailableExtensions(store)
                }
            } else if (!store.isLegacy) {
                val response = network.client.newCall(GET(store.indexUrl)).awaitSuccess()
                response.body.source().decompressIfGzipped().use { source ->
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                    }
                        .extensionList!!
                        .toAvailableExtensions(store)
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                val response = network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess()
                response.body.source().use { source ->
                    json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .map { it.toAvailableExtension(store, storeBaseUrl) }
                }
            }
            Result.success(extensions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }

        return if (isGzip) gzip().buffer() else this
    }
}
