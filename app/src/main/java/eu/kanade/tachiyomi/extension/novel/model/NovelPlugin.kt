package eu.kanade.tachiyomi.extension.novel.model

import kotlinx.serialization.Serializable

@Serializable
data class NovelPlugin(
    val id: String,
    val name: String,
    val site: String = "",
    val lang: String = "",
    val version: String = "",
    val url: String,
    val iconUrl: String? = null,
)

sealed class NovelExtension {
    abstract val plugin: NovelPlugin

    data class Installed(
        override val plugin: NovelPlugin,
        val localPath: String,
        val hasUpdate: Boolean = false,
    ) : NovelExtension()

    data class Available(
        override val plugin: NovelPlugin,
    ) : NovelExtension()
}
