package tachiyomi.domain.items.season.interactor

import tachiyomi.domain.entries.anime.model.Anime

class SetAnimeDefaultSeasonFlags {
    suspend fun await(anime: Anime) {}
    suspend fun awaitAll() {}
}
