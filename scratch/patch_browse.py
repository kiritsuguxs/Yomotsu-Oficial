import re

with open("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/browse/BrowseAnimeSourceScreenModel.kt", "r") as f:
    content = f.read()

old_code = """            Pager(PagingConfig(pageSize = 25)) {
                getRemoteAnime.subscribe(sourceId, listing.query ?: "", listing.filters)
            }.flow.map { pagingData ->
                pagingData.map {
                    networkToLocalAnime.await(it.toDomainAnime(sourceId))
                        .let { localAnime -> getAnime.subscribe(localAnime.url, localAnime.source) }
                        .filterNotNull()
                        .stateIn(ioCoroutineScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }"""

new_code = """            Pager(PagingConfig(pageSize = 25)) {
                getRemoteAnime.subscribe(sourceId, listing.query ?: "", listing.filters)
            }.flow.map { pagingData ->
                pagingData.map { sAnime ->
                    val domainAnime = sAnime.toDomainAnime(sourceId)
                    getAnime.subscribe(domainAnime.url, domainAnime.source)
                        .map { it ?: domainAnime }
                        .stateIn(ioCoroutineScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }"""

content = content.replace(old_code, new_code)

with open("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/browse/BrowseAnimeSourceScreenModel.kt", "w") as f:
    f.write(content)
print("Patched BrowseAnimeSourceScreenModel.kt")
