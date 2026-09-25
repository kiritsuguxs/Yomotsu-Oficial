package tachiyomi.domain.items.season.service

import aniyomi.domain.anime.SeasonAnime
import tachiyomi.domain.entries.anime.model.Anime

val seasonSortAlphabetically: Comparator<SeasonAnime> = Comparator { a, b ->
    a.anime.title.compareTo(b.anime.title, ignoreCase = true)
}

fun getSeasonSortComparator(anime: Anime): Comparator<SeasonAnime> = Comparator { a, b ->
    a.anime.title.compareTo(b.anime.title, ignoreCase = true)
}
