package eu.kanade.tachiyomi.data.download.anime.model

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

data class AnimeDownload(
    val source: AnimeSource,
    val anime: Anime,
    val episode: Episode,
    var video: Video? = null,
) {
    var status: State = State.NOT_DOWNLOADED
    var progress: Int = 0
    val statusFlow = MutableStateFlow(status)

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        val NOT_DOWNLOADED = State.NOT_DOWNLOADED.value
        val QUEUE = State.QUEUE.value
        val DOWNLOADING = State.DOWNLOADING.value
        val DOWNLOADED = State.DOWNLOADED.value
        val ERROR = State.ERROR.value
    }
}
