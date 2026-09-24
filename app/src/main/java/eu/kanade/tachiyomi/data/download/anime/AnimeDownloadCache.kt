package eu.kanade.tachiyomi.data.download.anime

class AnimeDownloadCache {
    fun isEpisodeDownloaded(
        episodeName: String,
        scanlator: String?,
        animeTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean = false
}
