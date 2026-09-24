package tachiyomi.domain.items.episode.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences

class SetAnimeDefaultEpisodeFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val getFavorites: GetAnimeFavorites,
) {

    suspend fun await(anime: Anime) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setAnimeEpisodeFlags.awaitSetAllFlags(
                    animeId = anime.id,
                    unseenFilter = filterEpisodeBySeen.get(),
                    downloadedFilter = filterEpisodeByDownloaded.get(),
                    bookmarkedFilter = filterEpisodeByBookmarked.get(),
                    fillermarkedFilter = filterEpisodeByFillermarked.get(),
                    sortingMode = sortEpisodeBySourceOrNumber.get(),
                    sortingDirection = sortEpisodeByAscendingOrDescending.get(),
                    displayMode = displayEpisodeByNameOrNumber.get(),
                    showPreviews = if (showEpisodeThumbnailPreviews.get()) Anime.EPISODE_SHOW_PREVIEWS else Anime.EPISODE_SHOW_NOT_PREVIEWS,
                    showSummaries = if (showEpisodeSummaries.get()) Anime.EPISODE_SHOW_SUMMARIES else Anime.EPISODE_SHOW_NOT_SUMMARIES,
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
