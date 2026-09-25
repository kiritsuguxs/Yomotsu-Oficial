import re

with open("domain/src/main/java/tachiyomi/domain/entries/anime/interactor/NetworkToLocalAnime.kt", "r") as f:
    content = f.read()

new_method = """    suspend operator fun invoke(anime: Anime): Anime {
        return invoke(listOf(anime)).single()
    }

    suspend operator fun invoke(anime: List<Anime>): List<Anime> {
        return animeRepository.insertNetworkAnime(anime)
    }

    suspend fun await(anime: Anime): Anime {"""

content = content.replace("    suspend fun await(anime: Anime): Anime {", new_method)

with open("domain/src/main/java/tachiyomi/domain/entries/anime/interactor/NetworkToLocalAnime.kt", "w") as f:
    f.write(content)
print("Patched NetworkToLocalAnime.kt")
