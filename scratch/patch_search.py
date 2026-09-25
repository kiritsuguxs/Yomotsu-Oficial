import re

with open("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/globalsearch/AnimeSearchScreenModel.kt", "r") as f:
    content = f.read()

old_code = """                        val titles = page.animes.map {
                            networkToLocalAnime.await(it.toDomainAnime(source.id))
                        }"""

new_code = """                        val titles = page.animes.map {
                            it.toDomainAnime(source.id)
                        }
                            .let { networkToLocalAnime(it) }"""

content = content.replace(old_code, new_code)

with open("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/globalsearch/AnimeSearchScreenModel.kt", "w") as f:
    f.write(content)
print("Patched AnimeSearchScreenModel.kt")
