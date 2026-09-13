package eu.kanade.tachiyomi.extension.novel.repo

/**
 * Resolve as URLs de índice de repositórios de plugins de Novel (compatível com LNReader e Keiyoushi).
 */
internal fun resolveNovelPluginRepoIndexUrls(baseUrl: String): List<String> {
    val normalized = baseUrl.trim().trimEnd('/')
    if (normalized.isEmpty()) return emptyList()

    return if (
        normalized.endsWith(".json", ignoreCase = true) ||
        normalized.endsWith(".pb", ignoreCase = true)
    ) {
        listOf(normalized)
    } else {
        listOf(
            "$normalized/plugins.min.json",
            "$normalized/plugins.json",
            "$normalized/index.json",
            "$normalized/index.pb",
            "$normalized/index.min.json",
        )
    }
}

internal fun resolveNovelPluginRepoIndexUrl(baseUrl: String): String {
    return resolveNovelPluginRepoIndexUrls(baseUrl).firstOrNull().orEmpty()
}
