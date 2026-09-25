import re

with open("data/src/main/java/tachiyomi/data/entries/anime/AnimeRepositoryImpl.kt", "r") as f:
    content = f.read()

new_method = """    override suspend fun insertNetworkAnime(anime: List<Anime>): List<Anime> {
        return handler.await(inTransaction = true) {
            anime.map {
                animesQueries.insertNetworkAnime(
                    source = it.source,
                    url = it.url,
                    artist = it.artist,
                    author = it.author,
                    description = it.description,
                    genre = it.genre?.let(tachiyomi.data.StringListColumnAdapter::encode),
                    title = it.title,
                    status = it.status,
                    thumbnailUrl = it.thumbnailUrl,
                    favorite = it.favorite,
                    lastUpdate = it.lastUpdate,
                    nextUpdate = it.nextUpdate,
                    calculateInterval = it.fetchInterval?.toLong(),
                    initialized = it.initialized,
                    viewerFlags = it.viewerFlags,
                    episodeFlags = it.episodeFlags,
                    coverLastModified = it.coverLastModified,
                    dateAdded = it.dateAdded,
                    updateStrategy = it.updateStrategy?.let(tachiyomi.data.entries.anime.AnimeUpdateStrategyColumnAdapter::encode),
                    version = it.version,
                    memo = it.memo?.let(tachiyomi.data.MemoColumnAdapter::encode),
                    updateTitle = it.title.isNotBlank(),
                    updateCover = !it.thumbnailUrl.isNullOrBlank(),
                    updateDetails = it.initialized,
                    mapper = AnimeMapper::mapAnime,
                )
                    .awaitAsOne()
            }
        }
    }"""

old_method = """    override suspend fun insertNetworkAnime(anime: List<Anime>): List<Anime> {
        return handler.awaitList(inTransaction = true) {
            anime.map {
                animesQueries.insertNetworkAnime(
                    source = it.source,
                    url = it.url,
                    artist = it.artist,
                    author = it.author,
                    description = it.description,
                    genre = it.genre?.let(StringListColumnAdapter::encode),
                    title = it.title,
                    status = it.status,
                    thumbnailUrl = it.thumbnailUrl,
                    favorite = it.favorite,
                    lastUpdate = it.lastUpdate,
                    nextUpdate = it.nextUpdate,
                    calculateInterval = it.fetchInterval?.toLong(),
                    initialized = it.initialized,
                    viewerFlags = it.viewerFlags,
                    episodeFlags = it.episodeFlags,
                    coverLastModified = it.coverLastModified,
                    dateAdded = it.dateAdded,
                    updateStrategy = it.updateStrategy?.let(AnimeUpdateStrategyColumnAdapter::encode),
                    version = it.version,
                    memo = it.memo?.let(MemoColumnAdapter::encode),
                    updateTitle = it.title.isNotBlank(),
                    updateCover = !it.thumbnailUrl.isNullOrBlank(),
                    updateDetails = it.initialized,
                    mapper = AnimeMapper::mapAnime,
                )
                    .awaitAsOne()
            }
        }
    }"""

content = content.replace(old_method, new_method)

with open("data/src/main/java/tachiyomi/data/entries/anime/AnimeRepositoryImpl.kt", "w") as f:
    f.write(content)
print("Patched AnimeRepositoryImpl.kt")
