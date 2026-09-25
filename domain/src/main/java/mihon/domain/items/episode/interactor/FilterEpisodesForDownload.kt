package mihon.domain.items.episode.interactor

import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category.Companion.DEFAULT_CATEGORY_ID
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode

class FilterEpisodesForDownload(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val downloadPreferences: DownloadPreferences,
    private val getCategories: GetAnimeCategories,
) {
    suspend fun await(anime: Anime, newEpisodes: List<Episode>): List<Episode> {
        if (newEpisodes.isEmpty()) return emptyList()
        return newEpisodes
    }
}
