package tachiyomi.domain.entries.anime.model

import eu.kanade.tachiyomi.animesource.model.SAnime

fun SAnime.toDomainAnime(sourceId: Long): Anime {
    return Anime.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        backgroundUrl = background_url,
        updateStrategy = update_strategy,
        fetchType = fetch_type,
        seasonNumber = season_number,
        initialized = initialized,
        memo = memo,
        source = sourceId,
    )
}
