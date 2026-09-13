package eu.kanade.tachiyomi.source

/**
 * Marker interface for Novel sources.
 * Any source implementing this will be treated as a Novel source by the UI
 * (e.g., hidden from Manga source lists, opens NovelReader instead of MangaReader).
 */
interface INovelSource : Source {
    // Additional novel-specific methods can be added here if needed,
    // like getChapterText(chapter) without using Page list.
}
