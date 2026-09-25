package eu.kanade.tachiyomi.data.download.anime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class AnimeDownloadCache {
    val changes: Flow<Unit> = emptyFlow()

    fun isEpisodeDownloaded(
        episodeName: String,
        scanlator: String?,
        animeTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean = false
}
