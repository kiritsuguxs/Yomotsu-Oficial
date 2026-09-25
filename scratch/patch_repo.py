import re

with open("data/src/main/java/tachiyomi/data/entries/anime/AnimeRepositoryImpl.kt", "r") as f:
    content = f.read()

old_call = """                animesQueries.insertNetworkAnime(
                    source = it.source,
                    url = it.url,
                    artist = it.artist,
                    author = it.author,
                    description = it.description,
                    genre = it.genre,
                    title = it.title,
                    status = it.status,
                    thumbnailUrl = it.thumbnailUrl,
                    favorite = it.favorite,
                    lastUpdate = it.lastUpdate,
                    nextUpdate = it.nextUpdate,
                    calculateInterval = it.fetchInterval.toLong(),
                    initialized = it.initialized,
                    viewerFlags = it.viewerFlags,
                    episodeFlags = it.episodeFlags,
                    coverLastModified = it.coverLastModified,
                    dateAdded = it.dateAdded,
                    updateStrategy = it.updateStrategy,
                    version = it.version,
                    memo = it.memo,
                    updateTitle = it.title.isNotBlank(),
                    updateCover = !it.thumbnailUrl.isNullOrBlank(),
                    updateDetails = it.initialized,
                    mapper = AnimeMapper::mapAnime,
                )"""

new_call = """                animesQueries.insertNetworkAnime(
                    source = it.source,
                    url = it.url,
                    artist = it.artist,
                    author = it.author,
                    description = it.description,
                    genre = it.genre,
                    title = it.title,
                    status = it.status,
                    thumbnailUrl = it.thumbnailUrl,
                    favorite = it.favorite,
                    lastUpdate = it.lastUpdate,
                    nextUpdate = it.nextUpdate,
                    calculateInterval = it.fetchInterval.toLong(),
                    initialized = it.initialized,
                    viewerFlags = it.viewerFlags,
                    episodeFlags = it.episodeFlags,
                    coverLastModified = it.coverLastModified,
                    dateAdded = it.dateAdded,
                    updateStrategy = it.updateStrategy,
                    version = it.version,
                    fetchType = it.fetchType,
                    parentId = it.parentId,
                    seasonFlags = it.seasonFlags,
                    seasonNumber = it.seasonNumber,
                    seasonSourceOrder = it.seasonSourceOrder,
                    backgroundUrl = it.backgroundUrl,
                    backgroundLastModified = it.backgroundLastModified,
                    memo = it.memo,
                    updateTitle = it.title.isNotBlank(),
                    updateCover = !it.thumbnailUrl.isNullOrBlank(),
                    updateDetails = it.initialized,
                    mapper = AnimeMapper::mapAnime,
                )"""

content = content.replace(old_call, new_call)

with open("data/src/main/java/tachiyomi/data/entries/anime/AnimeRepositoryImpl.kt", "w") as f:
    f.write(content)
print("Patched AnimeRepositoryImpl.kt")
