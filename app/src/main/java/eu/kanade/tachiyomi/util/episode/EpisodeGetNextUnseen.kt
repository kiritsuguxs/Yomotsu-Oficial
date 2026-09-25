package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

fun List<Episode>.getNextUnseen(anime: Anime, downloadManager: AnimeDownloadManager): Episode? {
    val unseen = filter { !it.seen }
    return if (anime.sortDescending()) {
        unseen.lastOrNull()
    } else {
        unseen.firstOrNull()
    }
}

fun List<EpisodeList.Item>.getNextUnseen(anime: Anime): Episode? {
    val unseen = filter { !it.episode.seen }
    return if (anime.sortDescending()) {
        unseen.lastOrNull()?.episode
    } else {
        unseen.firstOrNull()?.episode
    }
}
