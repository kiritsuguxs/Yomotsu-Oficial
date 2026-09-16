package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.data.telegram.TelegramCloudManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import tachiyomi.domain.telegram.TelegramPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.zip.ZipFile

class TelegramSource(
    private val context: Context,
    private val telegramCloudManager: TelegramCloudManager = Injekt.get(),
    private val preferences: TelegramPreferences = Injekt.get()
) : HttpSource(), UnmeteredSource {

    override val id: Long = ID
    override val name: String = "Biblioteca Telegram"
    override val lang: String = "other" // Coloca na aba "Outras" igual a Fonte Local
    override val supportsLatest: Boolean = false
    override val baseUrl: String = "http://telegram-cache"

    override val client: OkHttpClient = Injekt.get<NetworkHelper>().client.newBuilder()
        .addInterceptor { chain ->
            val url = chain.request().url.toString()
            if (url.startsWith("http://telegram-cache/")) {
                val path = url.substringAfter("http://telegram-cache/")
                val file = File(context.cacheDir, "telegram_pages/$path")
                val body = if (file.exists()) {
                    file.readBytes()
                } else {
                    ByteArray(0)
                }
                
                Response.Builder()
                    .code(200)
                    .message("OK")
                    .protocol(Protocol.HTTP_1_1)
                    .request(chain.request())
                    .body(body.toResponseBody("image/jpeg".toMediaTypeOrNull()))
                    .build()
            } else {
                chain.proceed(chain.request())
            }
        }.build()

    override suspend fun getPopularManga(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val mangas = index.map { cloudManga ->
            SManga.create().apply {
                title = cloudManga.title
                url = cloudManga.title
                description = cloudManga.description
                thumbnail_url = cloudManga.coverUrl
                status = SManga.UNKNOWN
            }
        }
        MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val mangas = index.filter {
            it.title.contains(query, ignoreCase = true)
        }.map { cloudManga ->
            SManga.create().apply {
                title = cloudManga.title
                url = cloudManga.title
                description = cloudManga.description
                thumbnail_url = cloudManga.coverUrl
                status = SManga.UNKNOWN
            }
        }
        MangasPage(mangas, false)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = supervisorScope {
        val asyncManga = if (fetchDetails) async { internalGetMangaDetails(manga) } else null
        val asyncChapters = if (fetchChapters) async { internalGetChapterList(manga) } else null
        SMangaUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
    }

    private suspend fun internalGetMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val cloudManga = index.find { it.title == manga.url }
        if (cloudManga != null) {
            manga.apply {
                title = cloudManga.title
                description = cloudManga.description
                thumbnail_url = cloudManga.coverUrl
            }
        }
        manga
    }

    private suspend fun internalGetChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val cloudManga = index.find { it.title == manga.url } ?: return@withContext emptyList()

        cloudManga.chapters.map { cloudChapter ->
            SChapter.create().apply {
                url = "${manga.url}||${cloudChapter.name}"
                name = cloudChapter.name
                date_upload = cloudChapter.date
                chapter_number = cloudChapter.name.replace(Regex("""[^0-9.]"""), "").toFloatOrNull() ?: -1f
            }
        }.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val parts = chapter.url.split("||")
        if (parts.size != 2) return@withContext emptyList()

        val mangaTitle = parts[0]
        val chapterName = parts[1]

        val index = telegramCloudManager.getCloudIndex()
        val cloudManga = index.find { it.title == mangaTitle } ?: return@withContext emptyList()
        val cloudChapter = cloudManga.chapters.find { it.name == chapterName } ?: return@withContext emptyList()

        val chatId = preferences.chatId.get().toLongOrNull() ?: return@withContext emptyList()

        telegramCloudManager.showNotification("Biblioteca Telegram", "Baixando o capítulo da nuvem...", ongoing = true)

        val chapterHash = chapter.url.hashCode().toString()
        val rootDir = File(context.cacheDir, "telegram_pages")
        if (rootDir.exists()) {
            val dirs = rootDir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }
            if (dirs != null && dirs.size > 2) {
                dirs.drop(2).forEach { it.deleteRecursively() }
            }
        }
        val extractDir = File(rootDir, chapterHash)
        if (extractDir.exists()) {
            extractDir.deleteRecursively()
        }
        extractDir.mkdirs()

        val downloadedPath = telegramCloudManager.downloadChapterFile(mangaTitle, cloudChapter, chatId)
        if (downloadedPath.isNullOrBlank()) {
            telegramCloudManager.showNotification("Biblioteca Telegram", "Falha ao puxar da nuvem.", autoDismiss = true)
            return@withContext emptyList()
        }

        val cbzFile = File(downloadedPath)
        val pages = mutableListOf<Page>()

        try {
            ZipFile(cbzFile).use { zip ->
                val entries = zip.entries().toList()
                    .filter { !it.isDirectory && !it.name.contains("__MACOSX") }
                    .sortedBy { it.name }

                entries.forEachIndexed { i, entry ->
                    val extractedFile = File(extractDir, entry.name.substringAfterLast('/'))
                    zip.getInputStream(entry).use { input ->
                        extractedFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val pageUri = "http://telegram-cache/$chapterHash/${extractedFile.name}"
                    pages.add(Page(i, "", pageUri))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cbzFile.delete()
            telegramCloudManager.showNotification("Biblioteca Telegram", "Capítulo carregado!", autoDismiss = true)
        }

        pages
    }

    override fun imageRequest(page: Page): Request {
        return Request.Builder().url(page.imageUrl!!).build()
    }

    override fun getFilterList(): FilterList = FilterList()

    companion object {
        const val ID = 9876543210L
    }
}
