package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import tachiyomi.domain.entries.anime.model.Anime

class AniChartApi {
    suspend fun loadAiringTime(
        anime: Anime,
        trackItems: List<AnimeTrackItem>,
        manualFetch: Boolean,
    ): Pair<Int, Long> {
        return Pair(anime.nextEpisodeToAir, anime.nextEpisodeAiringAt)
    }
}
