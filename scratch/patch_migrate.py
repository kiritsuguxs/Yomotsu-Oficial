import re

with open("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/anime/season/MigrateSeasonSelectScreenModel.kt", "r") as f:
    content = f.read()

old_code = """                pagingData.map {
                    networkToLocalAnime.await(it.toDomainAnime(anime.source))
                        .let { localAnime -> getAnime.subscribe(localAnime.url, localAnime.source) }
                        .filterNotNull()
                        .stateIn(ioCoroutineScope)
                }"""

new_code = """                pagingData.map { sAnime ->
                    val domainAnime = sAnime.toDomainAnime(anime.source)
                    getAnime.subscribe(domainAnime.url, domainAnime.source)
                        .map { it ?: domainAnime }
                        .stateIn(ioCoroutineScope)
                }"""

content = content.replace(old_code, new_code)

with open("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/anime/season/MigrateSeasonSelectScreenModel.kt", "w") as f:
    f.write(content)
print("Patched MigrateSeasonSelectScreenModel.kt")
