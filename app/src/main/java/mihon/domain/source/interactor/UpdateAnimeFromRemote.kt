package mihon.domain.source.interactor

import eu.kanade.tachiyomi.animesource.AnimeSource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

data class RemoteEpisodesUpdate(
    val anime: Anime,
    val newEpisodes: List<Episode> = emptyList(),
)

data class RemoteSeasonsUpdate(
    val anime: Anime,
    val newSeasons: List<Anime> = emptyList(),
)

class UpdateAnimeFromRemote(
    private val sourceManager: AnimeSourceManager,
) {
    suspend fun awaitEpisodesUpdate(
        anime: Anime,
        source: AnimeSource? = null,
        fetchDetails: Boolean = false,
        fetchEpisodes: Boolean = false,
        manualFetch: Boolean = false,
    ): Result<RemoteEpisodesUpdate> {
        return Result.success(RemoteEpisodesUpdate(anime, emptyList()))
    }

    suspend fun awaitSeasonsUpdate(
        anime: Anime,
        source: AnimeSource? = null,
        fetchDetails: Boolean = false,
        fetchSeasons: Boolean = false,
        manualFetch: Boolean = false,
    ): Result<RemoteSeasonsUpdate> {
        return Result.success(RemoteSeasonsUpdate(anime, emptyList()))
    }
}
