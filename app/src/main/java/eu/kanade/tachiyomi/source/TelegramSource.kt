package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.data.telegram.TelegramCloudManager
import eu.kanade.tachiyomi.data.telegram.CloudChapter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.zip.ZipFile
import tachiyomi.domain.telegram.TelegramPreferences

class TelegramSource(
    private val context: Context,
    private val telegramCloudManager: TelegramCloudManager = Injekt.get(),
    private val preferences: TelegramPreferences = Injekt.get()
) : CatalogueSource, UnmeteredSource {

    override val id: Long = ID
    override val name: String = "Biblioteca Telegram"
    override val lang: String = "pt-BR"
    override val supportsLatest: Boolean = false

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

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
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

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
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

        // Notifica inicio
        telegramCloudManager.showNotification("Biblioteca Telegram", "Baixando o capítulo da nuvem...", ongoing = true)

        // Limpa a pasta temporária do Telegram de capítulos anteriores
        val extractDir = File(context.cacheDir, "telegram_pages")
        if (extractDir.exists()) {
            extractDir.deleteRecursively()
        }
        extractDir.mkdirs()

        // Faz o download do CBZ
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
                    val pageUri = "file://${extractedFile.absolutePath}"
                    pages.add(Page(i, "", pageUri))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Apaga o zip baixado para nao gastar armazenamento
            cbzFile.delete()
            telegramCloudManager.showNotification("Biblioteca Telegram", "Capítulo carregado!", autoDismiss = true)
        }
        
        pages
    }

    override fun getFilterList(): FilterList = FilterList()

    companion object {
        const val ID = 9876543210L 
    }
}
