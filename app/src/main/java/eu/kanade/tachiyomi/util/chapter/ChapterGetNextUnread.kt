package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(manga: Manga, downloadManager: DownloadManager): Chapter? {
    val sourceManager: SourceManager = Injekt.get()
    val isNovel = sourceManager.get(manga.source) is eu.kanade.tachiyomi.source.INovelSource
    if (isNovel) {
        return this.sortedBy { it.sourceOrder }.find { !it.read }
    }
    return applyFilters(manga, downloadManager).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.read }
        } else {
            chapters.find { !it.read }
        }
    }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
    val sourceManager: SourceManager = Injekt.get()
    val isNovel = sourceManager.get(manga.source) is eu.kanade.tachiyomi.source.INovelSource
    if (isNovel) {
        return this.sortedBy { it.chapter.sourceOrder }.find { !it.chapter.read }?.chapter
    }
    return applyFilters(manga).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.chapter.read }
        } else {
            chapters.find { !it.chapter.read }
        }
    }?.chapter
}
