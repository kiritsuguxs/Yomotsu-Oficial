import re

with open("data/src/main/java/tachiyomi/data/source/anime/AnimeSourcePagingSource.kt", "r") as f:
    content = f.read()

content = content.replace("import tachiyomi.domain.source.anime.repository.AnimeSourcePagingSourceType", "import tachiyomi.domain.source.anime.repository.AnimeSourcePagingSourceType\nimport eu.kanade.domain.entries.anime.model.toDomainAnime")
content = content.replace("it.eu.kanade.domain.entries.anime.model.toDomainAnime(source.id)", "it.toDomainAnime(source.id)")

with open("data/src/main/java/tachiyomi/data/source/anime/AnimeSourcePagingSource.kt", "w") as f:
    f.write(content)
