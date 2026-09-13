package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * Interface base para fontes de Novel (compatível com os plugins do LNReader).
 */
interface NovelSource {

    val id: Long

    val name: String

    val lang: String
        get() = ""

    val isKotlinExtension: Boolean
        get() = false

    @Suppress("DEPRECATION")
    suspend fun getNovelDetails(novel: SNovel): SNovel {
        return fetchNovelDetails(novel).awaitSingle()
    }

    @Suppress("DEPRECATION")
    suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return fetchChapterList(novel).awaitSingle()
    }

    @Suppress("DEPRECATION")
    suspend fun getChapterText(chapter: SNovelChapter): String {
        return fetchChapterText(chapter).awaitSingle()
    }

    @Deprecated("Use getNovelDetails")
    fun fetchNovelDetails(novel: SNovel): Observable<SNovel> =
        throw IllegalStateException("Not used")

    @Deprecated("Use getChapterList")
    fun fetchChapterList(novel: SNovel): Observable<List<SNovelChapter>> =
        throw IllegalStateException("Not used")

    @Deprecated("Use getChapterText")
    fun fetchChapterText(chapter: SNovelChapter): Observable<String> =
        throw IllegalStateException("Not used")
}
