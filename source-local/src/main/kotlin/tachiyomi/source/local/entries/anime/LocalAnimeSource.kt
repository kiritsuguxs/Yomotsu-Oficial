package tachiyomi.source.local.entries.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.domain.entries.anime.model.Anime

class LocalAnimeSource(
    private val context: Context,
    p1: Any? = null,
    p2: Any? = null,
    p3: Any? = null,
    p4: Any? = null,
    p5: Any? = null,
) : AnimeSource, UnmeteredSource {

    override val id: Long = ID
    override val name: String = "Local source"
    override val lang: String = "other"
    override val supportsLatest: Boolean = false

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
    override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
    override suspend fun getPopularAnime(page: Int): AnimesPage = AnimesPage(emptyList(), false)
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage = AnimesPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int): AnimesPage = AnimesPage(emptyList(), false)

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = SAnimeEpisodeUpdate(anime, episodes)

    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = SAnimeSeasonUpdate(anime, seasons)

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://aniyomi.org/help/guides/local-anime/"
    }
}

fun Anime.isLocal(): Boolean = source == LocalAnimeSource.ID

fun AnimeSource.isLocal(): Boolean = id == LocalAnimeSource.ID
