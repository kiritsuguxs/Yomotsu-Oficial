package eu.kanade.translation.memory

import eu.kanade.translation.translator.ComicTranslationContext
import kotlinx.serialization.Serializable
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Versioned DTOs kept independent from any future file picker/export UI. */
@Serializable
data class TranslationCacheSnapshotEntry(
    val sourceLanguage: String,
    val targetLanguage: String,
    val source: String,
    val target: String,
)

@Serializable
data class MangaTranslationIntelligenceSnapshot(
    val mangaTitle: String,
    val memory: List<TranslationMemoryEntry> = emptyList(),
    val cache: List<TranslationCacheSnapshotEntry> = emptyList(),
)

@Serializable
data class TranslationIntelligenceSnapshot(
    val schemaVersion: Int = TranslationIntelligenceStore.SCHEMA_VERSION,
    val mangas: List<MangaTranslationIntelligenceSnapshot> = emptyList(),
)

enum class TranslationIntelligenceRestoreMode {
    MERGE,
    REPLACE_SNAPSHOT_SCOPES,
}

data class TranslationIntelligenceRestoreResult(
    val mangaCount: Int,
    val memoryEntryCount: Int,
    val cacheEntryCount: Int,
)

/**
 * Storage boundary for a future import/export and backup screen.
 *
 * It deliberately returns serializable data instead of reading or writing a
 * file. A later UI can choose JSON, a document provider or the app backup while
 * the translation intelligence keeps one tested schema and restore path.
 */
object TranslationIntelligenceStore {
    const val SCHEMA_VERSION = 1

    fun snapshot(
        preferenceStore: PreferenceStore = Injekt.get(),
    ): TranslationIntelligenceSnapshot {
        val mangas = TranslationMemory.registeredScopes(preferenceStore)
            .mapNotNull { scope ->
                val context = ComicTranslationContext(scope.mangaTitle, chapterName = "")
                val memory = TranslationMemory.entries(context, preferenceStore)
                val cache = TranslationCache.snapshotEntries(context, preferenceStore)
                if (memory.isEmpty() && cache.isEmpty()) return@mapNotNull null
                MangaTranslationIntelligenceSnapshot(
                    mangaTitle = scope.mangaTitle,
                    memory = memory,
                    cache = cache,
                )
            }
        return TranslationIntelligenceSnapshot(mangas = mangas)
    }

    fun restore(
        snapshot: TranslationIntelligenceSnapshot,
        mode: TranslationIntelligenceRestoreMode = TranslationIntelligenceRestoreMode.MERGE,
        preferenceStore: PreferenceStore = Injekt.get(),
    ): TranslationIntelligenceRestoreResult {
        require(snapshot.schemaVersion == SCHEMA_VERSION) {
            "Versão de backup da inteligência não suportada: ${snapshot.schemaVersion}."
        }

        val merge = mode == TranslationIntelligenceRestoreMode.MERGE
        var memoryEntryCount = 0
        var cacheEntryCount = 0
        var mangaCount = 0

        snapshot.mangas
            .filter { it.mangaTitle.isNotBlank() }
            .distinctBy { TranslationMemory.normalizeMangaKey(it.mangaTitle) }
            .forEach { manga ->
                val context = ComicTranslationContext(manga.mangaTitle.trim(), chapterName = "")
                TranslationMemory.replaceEntries(context, manga.memory, merge, preferenceStore)
                TranslationCache.replaceEntries(context, manga.cache, merge, preferenceStore)
                mangaCount++
                memoryEntryCount += manga.memory.size
                cacheEntryCount += manga.cache.size
            }

        return TranslationIntelligenceRestoreResult(
            mangaCount = mangaCount,
            memoryEntryCount = memoryEntryCount,
            cacheEntryCount = cacheEntryCount,
        )
    }
}
