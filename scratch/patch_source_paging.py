import re

with open("data/src/main/java/tachiyomi/data/source/anime/AnimeSourcePagingSource.kt", "r") as f:
    content = f.read()

old_load = """    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SAnime> {
        val page = params.key ?: 1

        val animesPage = try {
            withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.animes.isNotEmpty() }
                    ?: throw NoEpisodesException()
            }
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }

        return LoadResult.Page(
            data = animesPage.animes,
            prevKey = null,
            nextKey = if (animesPage.hasNextPage) page + 1 else null,
        )
    }"""

new_load = """    private val networkToLocalAnime: tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime = uy.kohesive.injekt.Injekt.get()
    private val seenAnime = hashSetOf<String>()

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SAnime> {
        val page = params.key ?: 1

        return try {
            val animesPage = withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.animes.isNotEmpty() }
                    ?: throw NoEpisodesException()
            }

            val animes = animesPage.animes
                .map { mihon.domain.anime.model.toDomainAnime(it, source.id) }
                .filter { seenAnime.add(it.url) }
                .let { networkToLocalAnime(it) }

            LoadResult.Page(
                data = animesPage.animes,
                prevKey = null,
                nextKey = if (animesPage.hasNextPage) page + 1 else null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }"""

content = content.replace(old_load, new_load)

with open("data/src/main/java/tachiyomi/data/source/anime/AnimeSourcePagingSource.kt", "w") as f:
    f.write(content)
print("Patched AnimeSourcePagingSource.kt")
