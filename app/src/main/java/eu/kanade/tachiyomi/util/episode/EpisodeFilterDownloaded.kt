package eu.kanade.tachiyomi.util.episode

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

fun List<Episode>.filterDownloadedEpisodes(anime: Anime): List<Episode> {
    return this
}
