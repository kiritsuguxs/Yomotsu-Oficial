package eu.kanade.tachiyomi.util

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import kotlin.time.Clock

fun Anime.removeCovers(coverCache: AnimeCoverCache = Injekt.get()): Anime {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        copy(coverLastModified = Clock.System.now().toEpochMilliseconds())
    } else {
        this
    }
}

fun Anime.removeBackgrounds(backgroundCache: eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache = Injekt.get()): Anime {
    if (isLocal()) return this
    return if (backgroundCache.deleteFromCache(this, true) > 0) {
        copy(coverLastModified = Clock.System.now().toEpochMilliseconds())
    } else {
        this
    }
}

suspend fun Anime.editCover(
    coverManager: Any = Unit,
    stream: InputStream,
    updateAnime: UpdateAnime = Injekt.get(),
    coverCache: AnimeCoverCache = Injekt.get(),
) {
    if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateAnime.awaitUpdateCoverLastModified(id)
    }
}

suspend fun Anime.editBackground(
    backgroundManager: Any = Unit,
    stream: InputStream,
    updateAnime: UpdateAnime = Injekt.get(),
    backgroundCache: eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache = Injekt.get(),
) {
    if (favorite) {
        backgroundCache.setCustomBackgroundToCache(this, stream)
        updateAnime.awaitUpdateCoverLastModified(id)
    }
}

fun Episode.editThumbnail(
    anime: Anime,
    thumbnailManager: Any = Unit,
    stream: InputStream,
) {
}

fun eu.kanade.tachiyomi.data.database.models.anime.Episode.editThumbnail(
    anime: Anime,
    thumbnailManager: Any = Unit,
    stream: InputStream,
) {
}

