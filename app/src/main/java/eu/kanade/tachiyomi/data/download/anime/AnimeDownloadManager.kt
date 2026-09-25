package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class AnimeDownloadManager(
    private val context: Context? = null,
) {
    val isRunning: Boolean = false

    private val _queueState = MutableStateFlow<List<AnimeDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    val isDownloaderRunning: Flow<Boolean> = flowOf(false)

    fun startDownloads() {}
    fun stopDownloads(reason: String? = null) {}
    fun pauseDownloads() {}
    fun clearQueue() {}

    fun isEpisodeDownloaded(
        episodeName: String,
        scanlator: String?,
        animeTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean = false

    fun getEpisodeDownloadOrNull(episode: Episode): AnimeDownload? = null

    fun statusFlow(): Flow<AnimeDownload> = emptyFlow()
    fun progressFlow(): Flow<AnimeDownload> = emptyFlow()

    fun downloadEpisodes(anime: Anime, episodes: List<Episode>) {}
    fun downloadEpisodes(
        anime: Anime,
        episodes: List<Episode>,
        customQuality: Boolean = false,
        useExternalDownloader: Boolean = false,
        video: Video? = null,
    ) {}
    fun deleteEpisodes(episodes: List<Episode>, anime: Anime, source: AnimeSource? = null) {}
    fun addDownloadsToStartOfQueue(downloads: List<AnimeDownload>) {}
    fun enqueueEpisodesToDelete(episodes: List<Episode>, anime: Anime) {}
    fun deletePendingEpisodes() {}
    fun cancelQueuedDownloads(downloads: List<AnimeDownload>) {}
    fun getDownloadCount(): Int = 0
    fun getDownloadCount(anime: Anime): Int = 0
    fun getQueuedDownloadOrNull(episodeId: Long): AnimeDownload? = null
    fun deleteAnime(anime: Anime, source: AnimeSource? = null) {}
    fun startDownloadNow(episode: Episode) {}
    fun updateDownloadState(download: AnimeDownload) {}
    fun renameSource(oldSource: AnimeSource, newSource: AnimeSource) {}


    fun buildVideo(source: AnimeSource, anime: Anime, episode: Episode): Video = error("Not supported")
}
