package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import eu.kanade.tachiyomi.source.INovelSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.security.MessageDigest

class NovelSourceWrapper(
    val plugin: NovelPlugin,
) : INovelSource {

    override val id: Long = generateId(plugin.id, plugin.lang)
    override val name: String = plugin.name
    override val lang: String = plugin.lang
    override val supportsLatest: Boolean = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        // TODO: Call NovelJsRuntime
        return MangasPage(emptyList(), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        return MangasPage(emptyList(), false)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList): MangasPage {
        return MangasPage(emptyList(), false)
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        return manga
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        return emptyList()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        return emptyList()
    }

    private fun generateId(pluginId: String, lang: String): Long {
        val key = "novel:\$pluginId:\$lang"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xffL shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
}
