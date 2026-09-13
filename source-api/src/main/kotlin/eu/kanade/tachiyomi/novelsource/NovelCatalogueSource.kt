package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

interface NovelCatalogueSource : NovelSource {

    override val lang: String

    val supportsLatest: Boolean

    @Suppress("DEPRECATION")
    suspend fun getPopularNovels(page: Int): NovelsPage {
        return fetchPopularNovels(page).awaitSingle()
    }

    suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        return getPopularNovels(page)
    }

    @Suppress("DEPRECATION")
    suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return fetchSearchNovels(page, query, filters).awaitSingle()
    }

    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): NovelsPage {
        return fetchLatestUpdates(page).awaitSingle()
    }

    @Deprecated("Use getPopularNovels")
    fun fetchPopularNovels(page: Int): Observable<NovelsPage> =
        throw IllegalStateException("Not used")

    @Deprecated("Use getSearchNovels")
    fun fetchSearchNovels(page: Int, query: String, filters: NovelFilterList): Observable<NovelsPage> =
        throw IllegalStateException("Not used")

    @Deprecated("Use getLatestUpdates")
    fun fetchLatestUpdates(page: Int): Observable<NovelsPage> =
        throw IllegalStateException("Not used")
}
