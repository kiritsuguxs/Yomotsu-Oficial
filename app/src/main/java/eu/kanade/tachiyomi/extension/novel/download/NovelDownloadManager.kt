package eu.kanade.tachiyomi.extension.novel.download

import android.app.Application
import eu.kanade.tachiyomi.extension.novel.NovelSourceWrapper
import eu.kanade.tachiyomi.extension.novel.translation.NovelTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object NovelDownloadManager {

    private val app: Application by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }
    private val translationPreferences: TranslationPreferences by lazy { Injekt.get() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val baseDir by lazy { File(app.filesDir, "novel_downloads").apply { mkdirs() } }

    private val _downloadedChapters = MutableStateFlow<Set<Long>>(emptySet())
    val downloadedChapters: StateFlow<Set<Long>> = _downloadedChapters.asStateFlow()

    private val _downloadingChapters = MutableStateFlow<Set<Long>>(emptySet())
    val downloadingChapters: StateFlow<Set<Long>> = _downloadingChapters.asStateFlow()

    init {
        refreshDownloadedCache()
    }

    fun isChapterDownloading(chapterId: Long): Boolean = _downloadingChapters.value.contains(chapterId)

    private fun getMangaDir(mangaId: Long): File = File(baseDir, mangaId.toString()).apply { mkdirs() }
    private fun getChapterFile(mangaId: Long, chapterId: Long): File = File(getMangaDir(mangaId), "$chapterId.txt")

    fun refreshDownloadedCache() {
        scope.launch {
            val ids = mutableSetOf<Long>()
            baseDir.listFiles()?.forEach { mangaFolder ->
                if (mangaFolder.isDirectory) {
                    mangaFolder.listFiles()?.forEach { file ->
                        if (file.extension == "txt" && file.length() > 0) {
                            file.nameWithoutExtension.toLongOrNull()?.let { ids.add(it) }
                        }
                    }
                }
            }
            _downloadedChapters.value = ids
        }
    }

    fun isChapterDownloaded(mangaId: Long, chapterId: Long): Boolean {
        return getChapterFile(mangaId, chapterId).exists()
    }

    fun getDownloadedChapterText(mangaId: Long, chapterId: Long): String? {
        val file = getChapterFile(mangaId, chapterId)
        return if (file.exists()) file.readText() else null
    }

    suspend fun downloadChapter(manga: Manga, chapter: Chapter): Boolean = withContext(Dispatchers.IO) {
        _downloadingChapters.value = _downloadingChapters.value + chapter.id
        try {
            val source = sourceManager.get(manga.source) as? NovelSourceWrapper
                ?: throw Exception("Fonte não é um plugin de novel")

            val file = getChapterFile(manga.id, chapter.id)
            
            // Nuvem Telegram: Tenta puxar de volta primeiro
            val telegramCloudManager = Injekt.get<eu.kanade.tachiyomi.data.telegram.TelegramCloudManager>()
            val restored = telegramCloudManager.restoreNovelChapter(manga.title, chapter.name, file)
            
            if (!restored) {
                // Se não tá no Telegram, baixa da internet
                val raw = source.getChapterText(manga.url, chapter.url)
                if (raw.isBlank()) return@withContext false
                file.writeText(raw)
                
                // Salva na nuvem logo em seguida
                telegramCloudManager.uploadNovelChapter(manga, chapter, file)
            }

            val current = _downloadedChapters.value.toMutableSet()
            current.add(chapter.id)
            _downloadedChapters.value = current

            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to download novel chapter ${chapter.name}" }
            false
        } finally {
            _downloadingChapters.value = _downloadingChapters.value - chapter.id
        }
    }

    fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        scope.launch {
            chapters.forEach { chapter ->
                downloadChapter(manga, chapter)
            }
        }
    }

    fun deleteChapter(mangaId: Long, chapterId: Long) {
        val file = getChapterFile(mangaId, chapterId)
        if (file.exists()) {
            file.delete()
        }
        val current = _downloadedChapters.value.toMutableSet()
        current.remove(chapterId)
        _downloadedChapters.value = current
    }
}
