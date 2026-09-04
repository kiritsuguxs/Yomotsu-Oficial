package eu.kanade.translation.memory

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.model.normalizeTranslationText
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private data class CachedTranslation(
    val target: String,
    val intelligenceRevision: Long,
)

private data class TranslationCacheScope(
    val mangaKey: String,
    val sourceLanguage: String,
    val targetLanguage: String,
)

/**
 * Persistent cache for complete translated speech blocks.
 *
 * Cache records carry the revision of the manga's [TranslationMemory]. Saving,
 * changing or deleting a glossary item or learned correction increments that
 * revision, so stale machine output is ignored without risking another manga,
 * language pair or the approved translation flow.
 */
object TranslationCache {
    private const val PREF_PREFIX = "translation_cache_y7_"
    private const val SCOPE_INDEX_KEY = "translation_cache_scopes_y12"
    private const val RECORD_VERSION = "y12"
    private const val SEPARATOR = "\u001F"
    private const val MAX_ENTRIES_PER_SCOPE = 4000

    private val cache = ConcurrentHashMap<String, LinkedHashMap<String, CachedTranslation>>()
    private val scopeIndexLock = Any()

    fun get(
        context: ComicTranslationContext,
        fromLang: TextRecognizerLanguage,
        toLang: TextTranslatorLanguage,
        source: String,
        preferenceStore: PreferenceStore = Injekt.get(),
    ): String? {
        val scope = scope(context, fromLang.code, toLang.code) ?: return null
        val sourceKey = normalizeSource(source)
        if (sourceKey.isEmpty()) return null

        TranslationMemory.registerContext(context, preferenceStore)
        val memory = memoryFor(scope.first, preferenceStore)
        if (memory.isNotEmpty()) registerScope(scope.second, preferenceStore)
        val entry = synchronized(memory) { memory[sourceKey] } ?: return null
        val currentRevision = TranslationMemory.revision(context, preferenceStore)
        return normalizeTranslationText(entry.target).takeIf {
            it.isNotBlank() && entry.intelligenceRevision == currentRevision
        }
    }

    fun put(
        context: ComicTranslationContext,
        fromLang: TextRecognizerLanguage,
        toLang: TextTranslatorLanguage,
        source: String,
        target: String,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        val scope = scope(context, fromLang.code, toLang.code) ?: return
        val sourceKey = normalizeSource(source)
        val targetText = normalizeTranslationText(target)
        if (sourceKey.isEmpty() || targetText.isEmpty()) return
        if (sourceKey.equals(targetText, ignoreCase = true)) return

        TranslationMemory.registerContext(context, preferenceStore)
        registerScope(scope.second, preferenceStore)
        val memory = memoryFor(scope.first, preferenceStore)
        val cachedTranslation = CachedTranslation(
            target = targetText,
            intelligenceRevision = TranslationMemory.revision(context, preferenceStore),
        )
        synchronized(memory) {
            memory.remove(sourceKey)
            memory[sourceKey] = cachedTranslation
            while (memory.size > MAX_ENTRIES_PER_SCOPE) {
                val oldest = memory.keys.firstOrNull() ?: break
                memory.remove(oldest)
            }
            persist(scope.first, memory, preferenceStore)
        }
    }

    fun remove(
        context: ComicTranslationContext,
        fromLang: TextRecognizerLanguage,
        toLang: TextTranslatorLanguage,
        source: String,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        val scope = scope(context, fromLang.code, toLang.code) ?: return
        val sourceKey = normalizeSource(source)
        if (sourceKey.isEmpty()) return

        val memory = memoryFor(scope.first, preferenceStore)
        synchronized(memory) {
            if (memory.remove(sourceKey) != null) {
                persist(scope.first, memory, preferenceStore)
            }
        }
    }

    internal fun snapshotEntries(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore = Injekt.get(),
    ): List<TranslationCacheSnapshotEntry> {
        val mangaKey = TranslationMemory.normalizeMangaKey(context.mangaTitle)
        if (mangaKey.isEmpty()) return emptyList()
        val currentRevision = TranslationMemory.revision(context, preferenceStore)

        return registeredScopes(preferenceStore)
            .asSequence()
            .filter { it.mangaKey == mangaKey }
            .flatMap { scope ->
                val memory = memoryFor(scopeKey(scope), preferenceStore)
                synchronized(memory) {
                    memory.entries
                        .filter { it.value.intelligenceRevision == currentRevision }
                        .map { (source, cached) ->
                            TranslationCacheSnapshotEntry(
                                sourceLanguage = scope.sourceLanguage,
                                targetLanguage = scope.targetLanguage,
                                source = source,
                                target = cached.target,
                            )
                        }
                }.asSequence()
            }
            .sortedWith(
                compareBy<TranslationCacheSnapshotEntry> { it.sourceLanguage }
                    .thenBy { it.targetLanguage }
                    .thenBy { it.source },
            )
            .toList()
    }

    internal fun replaceEntries(
        context: ComicTranslationContext,
        entries: Collection<TranslationCacheSnapshotEntry>,
        merge: Boolean,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        val mangaKey = TranslationMemory.normalizeMangaKey(context.mangaTitle)
        if (mangaKey.isEmpty()) return

        TranslationMemory.registerContext(context, preferenceStore)
        if (!merge) clearManga(mangaKey, preferenceStore)
        val currentRevision = TranslationMemory.revision(context, preferenceStore)

        entries.asSequence()
            .map {
                it.copy(
                    sourceLanguage = it.sourceLanguage.trim().lowercase(),
                    targetLanguage = it.targetLanguage.trim().lowercase(),
                    source = normalizeSource(it.source),
                    target = normalizeTranslationText(it.target),
                )
            }
            .filter {
                it.sourceLanguage.isNotEmpty() && it.targetLanguage.isNotEmpty() &&
                    it.source.isNotEmpty() && it.target.isNotEmpty()
            }
            .groupBy { TranslationCacheScope(mangaKey, it.sourceLanguage, it.targetLanguage) }
            .forEach { (scope, restoredEntries) ->
                registerScope(scope, preferenceStore)
                val key = scopeKey(scope)
                val memory = memoryFor(key, preferenceStore)
                synchronized(memory) {
                    restoredEntries.forEach { entry ->
                        memory[entry.source] = CachedTranslation(entry.target, currentRevision)
                    }
                    trimToLimit(memory)
                    persist(key, memory, preferenceStore)
                }
            }
    }

    private fun clearManga(mangaKey: String, preferenceStore: PreferenceStore) {
        registeredScopes(preferenceStore)
            .filter { it.mangaKey == mangaKey }
            .forEach { scope ->
                val key = scopeKey(scope)
                cache.remove(key)
                preferenceStore.getStringSet(preferenceKey(key)).set(emptySet())
            }
    }

    private fun memoryFor(
        scope: String,
        preferenceStore: PreferenceStore,
    ): LinkedHashMap<String, CachedTranslation> = cache.getOrPut(scope) {
        val stored = preferenceStore.getStringSet(preferenceKey(scope)).get()
        LinkedHashMap(
            stored.mapNotNull(::decodeEntry).associate { it.first to it.second },
        )
    }

    private fun persist(
        scope: String,
        memory: Map<String, CachedTranslation>,
        preferenceStore: PreferenceStore,
    ) {
        val encoded = memory.entries.asSequence()
            .filter { it.key.isNotBlank() && it.value.target.isNotBlank() }
            .mapTo(mutableSetOf()) { (source, cached) -> encodeEntry(source, cached) }
        preferenceStore.getStringSet(preferenceKey(scope)).set(encoded)
    }

    private fun encodeEntry(source: String, cached: CachedTranslation): String = listOf(
        RECORD_VERSION,
        cached.intelligenceRevision.toString(),
        source,
        cached.target,
    ).joinToString(SEPARATOR)

    private fun decodeEntry(encoded: String): Pair<String, CachedTranslation>? {
        val parts = encoded.split(SEPARATOR)
        if (parts.firstOrNull() == RECORD_VERSION && parts.size >= 4) {
            val revision = parts[1].toLongOrNull() ?: return null
            val source = normalizeSource(parts[2])
            val target = normalizeTranslationText(parts.drop(3).joinToString(SEPARATOR))
            if (source.isEmpty() || target.isEmpty()) return null
            return source to CachedTranslation(target, revision)
        }

        val separatorIndex = encoded.indexOf(SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex >= encoded.lastIndex) return null
        val source = normalizeSource(encoded.substring(0, separatorIndex))
        val target = normalizeTranslationText(encoded.substring(separatorIndex + SEPARATOR.length))
        if (source.isEmpty() || target.isEmpty()) return null
        return source to CachedTranslation(target, intelligenceRevision = 0L)
    }

    private fun scope(
        context: ComicTranslationContext,
        sourceLanguage: String,
        targetLanguage: String,
    ): Pair<String, TranslationCacheScope>? {
        val mangaKey = TranslationMemory.normalizeMangaKey(context.mangaTitle)
        if (mangaKey.isEmpty()) return null
        val value = TranslationCacheScope(
            mangaKey = mangaKey,
            sourceLanguage = sourceLanguage.lowercase(),
            targetLanguage = targetLanguage.lowercase(),
        )
        return scopeKey(value) to value
    }

    private fun scopeKey(scope: TranslationCacheScope): String =
        "${scope.mangaKey}|${scope.sourceLanguage}|${scope.targetLanguage}"

    private fun registerScope(scope: TranslationCacheScope, preferenceStore: PreferenceStore) {
        synchronized(scopeIndexLock) {
            val preference = preferenceStore.getStringSet(SCOPE_INDEX_KEY)
            val scopes = preference.get().mapNotNull(::decodeScope).toMutableSet()
            if (!scopes.add(scope)) return
            preference.set(scopes.mapTo(mutableSetOf(), ::encodeScope))
        }
    }

    private fun registeredScopes(preferenceStore: PreferenceStore): Set<TranslationCacheScope> =
        preferenceStore.getStringSet(SCOPE_INDEX_KEY).get().mapNotNullTo(mutableSetOf(), ::decodeScope)

    private fun encodeScope(scope: TranslationCacheScope): String = listOf(
        scope.mangaKey,
        scope.sourceLanguage,
        scope.targetLanguage,
    ).joinToString(SEPARATOR)

    private fun decodeScope(encoded: String): TranslationCacheScope? {
        val parts = encoded.split(SEPARATOR)
        if (parts.size != 3 || parts.any(String::isBlank)) return null
        return TranslationCacheScope(parts[0], parts[1], parts[2])
    }

    private fun trimToLimit(memory: LinkedHashMap<String, CachedTranslation>) {
        while (memory.size > MAX_ENTRIES_PER_SCOPE) {
            val oldest = memory.keys.firstOrNull() ?: break
            memory.remove(oldest)
        }
    }

    private fun normalizeSource(source: String): String =
        normalizeTranslationText(source).replace('\n', ' ').replace(Regex(" {2,}"), " ")

    private fun preferenceKey(scope: String): String = PREF_PREFIX + sha256(scope)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
