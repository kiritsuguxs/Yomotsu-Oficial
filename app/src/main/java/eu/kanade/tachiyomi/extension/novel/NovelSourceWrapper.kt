package eu.kanade.tachiyomi.extension.novel

import eu.kanade.tachiyomi.extension.novel.model.NovelPlugin
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntime
import eu.kanade.tachiyomi.extension.novel.runtime.NovelNativeApi
import eu.kanade.tachiyomi.source.INovelSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.security.MessageDigest

class NovelSourceWrapper(
    val plugin: NovelPlugin,
    val localPath: String,
) : HttpSource(), INovelSource {

    override val id: Long = generateId(plugin.id, plugin.lang)
    override val name: String = plugin.name
    override val lang: String = plugin.lang
    override val supportsLatest: Boolean = true
    override val baseUrl: String get() = plugin.site.ifBlank { "https://google.com" }

    override fun getHomeUrl(): String = plugin.site.ifBlank { baseUrl }

    override fun getMangaUrl(manga: SManga): String {
        if (manga.url.startsWith("http://") || manga.url.startsWith("https://")) {
            return manga.url
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = manga.url.trimStart('/')
        return "$cleanBase/$cleanPath"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith("http://") || chapter.url.startsWith("https://")) {
            return chapter.url
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = chapter.url.trimStart('/')
        return "$cleanBase/$cleanPath"
    }

    private fun evaluateAsyncMethod(runtime: NovelJsRuntime, methodCall: String): String {
        val script = """
            var __result = null;
            var __error = null;
            var __done = false;
            try {
                var promise = $methodCall;
                if (promise && typeof promise.then === 'function') {
                    promise.then(function(res) {
                        __result = res;
                        __done = true;
                    }).catch(function(err) {
                        __error = err && err.message ? err.message : String(err);
                        __done = true;
                    });
                    // Spin event loop manually
                    while(!__done) {
                        __drainJobs(100);
                    }
                } else {
                    __result = promise;
                }
            } catch (e) {
                __error = e && e.message ? e.message : String(e);
            }
            if (__error) throw new Error(__error);
            JSON.stringify(__result);
        """
        return runtime.evaluate(script, "novel-evaluate.js") as? String ?: "null"
    }

    private fun <T> runInJs(block: (NovelJsRuntime) -> T): T {
        val runtime = NovelJsRuntime(plugin.id, NovelNativeApi())
        try {
            val jsCode = File(localPath).readText()
            runtime.evaluate("var module = { exports: {} };\nvar exports = module.exports;\n" + jsCode, localPath)
            return block(runtime)
        } finally {
            runtime.close()
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = runInJs { runtime ->
        val jsonStr = evaluateAsyncMethod(runtime, "module.exports.default.popularNovels($page, { showLatestNovels: false })")
        parseNovelsJson(jsonStr)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = runInJs { runtime ->
        val jsonStr = evaluateAsyncMethod(runtime, "module.exports.default.popularNovels($page, { showLatestNovels: true })")
        parseNovelsJson(jsonStr)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList): MangasPage = runInJs { runtime ->
        val jsonStr = evaluateAsyncMethod(runtime, "module.exports.default.searchNovels('$query', $page)")
        parseNovelsJson(jsonStr)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var updatedManga = manga
        var updatedChapters = chapters

        if (fetchDetails || fetchChapters) {
            runInJs { runtime ->
                val jsonStr = evaluateAsyncMethod(runtime, "module.exports.default.parseNovel('${manga.url}')")
                val json = Injekt.get<Json>().parseToJsonElement(jsonStr).jsonObject

                if (fetchDetails) {
                    updatedManga = manga.apply {
                        title = json["name"]?.jsonPrimitive?.content ?: title
                        thumbnail_url = json["cover"]?.jsonPrimitive?.content ?: thumbnail_url
                        author = json["author"]?.jsonPrimitive?.content ?: author
                        artist = json["artist"]?.jsonPrimitive?.content ?: artist
                        description = json["summary"]?.jsonPrimitive?.content ?: description
                        genre = json["genres"]?.jsonPrimitive?.content
                        status = when (json["status"]?.jsonPrimitive?.content) {
                            "Ongoing", "مستمرة" -> SManga.ONGOING
                            "Completed", "منتهية" -> SManga.COMPLETED
                            else -> SManga.UNKNOWN
                        }
                    }
                }

                if (fetchChapters) {
                    val chapList = json["chapters"]?.jsonArray ?: JsonArray(emptyList())
                    updatedChapters = chapList.map { chapterObj ->
                        val obj = chapterObj.jsonObject
                        SChapter.create().apply {
                            name = obj["name"]?.jsonPrimitive?.content ?: "Chapter"
                            url = obj["path"]?.jsonPrimitive?.content ?: ""
                            date_upload = obj["releaseTime"]?.jsonPrimitive?.longOrNull ?: 0L
                        }
                    }
                }
            }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        return emptyList()
    }

    suspend fun getChapterText(mangaUrl: String, chapterUrl: String): String = runInJs { runtime ->
        val jsonStr = evaluateAsyncMethod(runtime, "module.exports.default.parseChapter('$mangaUrl', '$chapterUrl')")
        val json = Injekt.get<Json>().parseToJsonElement(jsonStr)
        json.jsonPrimitive.content
    }

    private fun parseNovelsJson(jsonStr: String): MangasPage {
        if (jsonStr == "null" || jsonStr.isEmpty()) return MangasPage(emptyList(), false)
        val json = Injekt.get<Json>().parseToJsonElement(jsonStr).jsonArray
        val mangas = json.map {
            val obj = it.jsonObject
            SManga.create().apply {
                title = obj["name"]?.jsonPrimitive?.content ?: ""
                url = obj["path"]?.jsonPrimitive?.content ?: ""
                thumbnail_url = obj["cover"]?.jsonPrimitive?.content ?: ""
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun generateId(pluginId: String, lang: String): Long {
        val key = "novel:$pluginId:$lang"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xffL shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
}
