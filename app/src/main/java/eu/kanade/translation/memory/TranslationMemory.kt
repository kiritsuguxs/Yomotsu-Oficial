package eu.kanade.translation.memory

import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.model.normalizeTranslationText
import kotlinx.serialization.Serializable
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** The role of one piece of translation intelligence. */
@Serializable
enum class TranslationMemoryEntryType {
    TERM,
    NAME,
    TITLE,
    TECHNIQUE,
    MANUAL_CORRECTION,
}

/** A preferred translation or a complete manual correction for one manga. */
@Serializable
data class TranslationMemoryEntry(
    val source: String,
    val target: String,
    val type: TranslationMemoryEntryType = TranslationMemoryEntryType.TERM,
    val isProtected: Boolean = false,
) {
    val isManualCorrection: Boolean
        get() = type == TranslationMemoryEntryType.MANUAL_CORRECTION
}

internal data class TranslationMemoryScope(
    val mangaKey: String,
    val mangaTitle: String,
)

/**
 * Persistent, versioned translation intelligence scoped by manga title.
 *
 * Y6/Y11 entries used `source<US>target`. Y12 reads that format in place and
 * writes a versioned record containing the entry type and protection flag. A
 * separate record is kept for a learned complete-line correction, so an
 * explicit glossary term and a correction may safely share the same source.
 */
object TranslationMemory {
    private const val PREF_PREFIX = "translation_memory_"
    private const val REVISION_PREFIX = "translation_intelligence_revision_"
    private const val SCOPE_INDEX_KEY = "translation_intelligence_scopes_y12"
    private const val RECORD_VERSION = "y12"
    private const val SEPARATOR = "\u001F"

    private val cache = ConcurrentHashMap<String, LinkedHashMap<String, TranslationMemoryEntry>>()
    private val scopeIndexLock = Any()

    fun entries(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore = Injekt.get(),
    ): List<TranslationMemoryEntry> {
        val mangaKey = normalizeMangaKey(context.mangaTitle)
        if (mangaKey.isEmpty()) return emptyList()

        registerContext(context, preferenceStore)
        val memory = memoryFor(mangaKey, preferenceStore)
        return synchronized(memory) {
            memory.values
                .toList()
                .sortedWith(
                    compareByDescending<TranslationMemoryEntry> { it.isManualCorrection }
                        .thenByDescending { it.source.length },
                )
        }
    }

    fun glossaryEntries(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore = Injekt.get(),
    ): List<TranslationMemoryEntry> = entries(context, preferenceStore)
        .filterNot(TranslationMemoryEntry::isManualCorrection)

    fun correctionEntries(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore = Injekt.get(),
    ): List<TranslationMemoryEntry> = entries(context, preferenceStore)
        .filter(TranslationMemoryEntry::isManualCorrection)

    fun remember(
        context: ComicTranslationContext,
        source: String,
        target: String,
        type: TranslationMemoryEntryType = TranslationMemoryEntryType.TERM,
        isProtected: Boolean = false,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        if (type == TranslationMemoryEntryType.MANUAL_CORRECTION) {
            rememberCorrection(context, source, target, preferenceStore)
            return
        }
        upsert(
            context = context,
            entry = TranslationMemoryEntry(
                source = source.trim(),
                target = target.trim(),
                type = type,
                isProtected = isProtected,
            ),
            preferenceStore = preferenceStore,
        )
    }

    fun rememberCorrection(
        context: ComicTranslationContext,
        source: String,
        target: String,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        val sourceText = normalizeTranslationText(source)
        val targetText = normalizeTranslationText(target)
        if (sourceText.equals(targetText, ignoreCase = true)) return

        upsert(
            context = context,
            entry = TranslationMemoryEntry(
                source = sourceText,
                target = targetText,
                type = TranslationMemoryEntryType.MANUAL_CORRECTION,
                isProtected = true,
            ),
            preferenceStore = preferenceStore,
        )
    }

    fun forget(
        context: ComicTranslationContext,
        source: String,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        removeMatching(context, source, preferenceStore) { !it.isManualCorrection }
    }

    fun forgetCorrection(
        context: ComicTranslationContext,
        source: String,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        removeMatching(context, source, preferenceStore, TranslationMemoryEntry::isManualCorrection)
    }

    /** Clears the user-managed glossary without discarding learned corrections. */
    fun clear(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        mutate(context, preferenceStore) { memory ->
            val keys = memory.filterValues { !it.isManualCorrection }.keys
            keys.forEach(memory::remove)
            keys.isNotEmpty()
        }
    }

    fun clearAll(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        mutate(context, preferenceStore) { memory ->
            if (memory.isEmpty()) return@mutate false
            memory.clear()
            true
        }
    }

    fun revision(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore = Injekt.get(),
    ): Long {
        val mangaKey = normalizeMangaKey(context.mangaTitle)
        if (mangaKey.isEmpty()) return 0L
        memoryFor(mangaKey, preferenceStore)
        return preferenceStore.getLong(revisionKey(mangaKey)).get()
    }

    internal fun registeredScopes(
        preferenceStore: PreferenceStore = Injekt.get(),
    ): List<TranslationMemoryScope> = preferenceStore.getStringSet(SCOPE_INDEX_KEY).get()
        .mapNotNull(::decodeScope)
        .distinctBy(TranslationMemoryScope::mangaKey)
        .sortedBy(TranslationMemoryScope::mangaTitle)

    internal fun replaceEntries(
        context: ComicTranslationContext,
        entries: Collection<TranslationMemoryEntry>,
        merge: Boolean,
        preferenceStore: PreferenceStore = Injekt.get(),
    ) {
        mutate(context, preferenceStore) { memory ->
            val before = memory.toMap()
            if (!merge) memory.clear()
            entries.asSequence()
                .map(::sanitizeEntry)
                .filter { it.source.isNotEmpty() && it.target.isNotEmpty() }
                .forEach { memory[entryKey(it)] = it }
            memory != before
        }
    }

    private fun upsert(
        context: ComicTranslationContext,
        entry: TranslationMemoryEntry,
        preferenceStore: PreferenceStore,
    ) {
        val cleanEntry = sanitizeEntry(entry)
        if (cleanEntry.source.isEmpty() || cleanEntry.target.isEmpty()) return

        mutate(context, preferenceStore) { memory ->
            val key = entryKey(cleanEntry)
            if (memory[key] == cleanEntry) return@mutate false
            memory[key] = cleanEntry
            true
        }
    }

    private fun removeMatching(
        context: ComicTranslationContext,
        source: String,
        preferenceStore: PreferenceStore,
        predicate: (TranslationMemoryEntry) -> Boolean,
    ) {
        val sourceKey = normalizeSourceKey(source)
        if (sourceKey.isEmpty()) return
        mutate(context, preferenceStore) { memory ->
            val keys = memory.filterValues {
                predicate(it) && normalizeSourceKey(it.source) == sourceKey
            }.keys
            keys.forEach(memory::remove)
            keys.isNotEmpty()
        }
    }

    private fun mutate(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore,
        mutation: (LinkedHashMap<String, TranslationMemoryEntry>) -> Boolean,
    ) {
        val mangaKey = normalizeMangaKey(context.mangaTitle)
        if (mangaKey.isEmpty()) return

        registerContext(context, preferenceStore)
        val memory = memoryFor(mangaKey, preferenceStore)
        synchronized(memory) {
            if (!mutation(memory)) return
            persist(mangaKey, memory.values, preferenceStore)
            val revisionPreference = preferenceStore.getLong(revisionKey(mangaKey))
            revisionPreference.set(revisionPreference.get() + 1L)
        }
    }

    private fun memoryFor(
        mangaKey: String,
        preferenceStore: PreferenceStore,
    ): LinkedHashMap<String, TranslationMemoryEntry> = cache.getOrPut(mangaKey) {
        val stored = preferenceStore.getStringSet(preferenceKey(mangaKey)).get()
        val loadedEntries = stored.mapNotNull(::decodeEntry)
        val memory = LinkedHashMap<String, TranslationMemoryEntry>()
        loadedEntries.forEach { entry -> memory[entryKey(entry)] = entry }

        // A non-empty Y6/Y11 memory predates cache revisions. Starting it at 1
        // makes pre-Y12 machine-cache entries miss once instead of returning a
        // translation produced before the glossary was fully enforced.
        if (memory.isNotEmpty()) {
            val revisionPreference = preferenceStore.getLong(revisionKey(mangaKey))
            if (revisionPreference.get() == 0L) revisionPreference.set(1L)
        }
        memory
    }

    private fun persist(
        mangaKey: String,
        memory: Collection<TranslationMemoryEntry>,
        preferenceStore: PreferenceStore,
    ) {
        val encoded = memory.asSequence()
            .map(::sanitizeEntry)
            .filter { it.source.isNotEmpty() && it.target.isNotEmpty() }
            .mapTo(mutableSetOf(), ::encodeEntry)
        preferenceStore.getStringSet(preferenceKey(mangaKey)).set(encoded)
    }

    internal fun registerContext(
        context: ComicTranslationContext,
        preferenceStore: PreferenceStore,
    ) {
        val mangaKey = normalizeMangaKey(context.mangaTitle)
        val title = context.mangaTitle.trim()
        if (mangaKey.isEmpty() || title.isEmpty()) return

        synchronized(scopeIndexLock) {
            val preference = preferenceStore.getStringSet(SCOPE_INDEX_KEY)
            val scopes = preference.get().mapNotNull(::decodeScope).toMutableList()
            val previous = scopes.firstOrNull { it.mangaKey == mangaKey }
            if (previous?.mangaTitle == title) return
            scopes.removeAll { it.mangaKey == mangaKey }
            scopes += TranslationMemoryScope(mangaKey, title)
            preference.set(scopes.mapTo(mutableSetOf(), ::encodeScope))
        }
    }

    private fun sanitizeEntry(entry: TranslationMemoryEntry): TranslationMemoryEntry = entry.copy(
        source = normalizeTranslationText(entry.source),
        target = normalizeTranslationText(entry.target),
        isProtected = entry.isProtected || entry.isManualCorrection,
    )

    private fun entryKey(entry: TranslationMemoryEntry): String {
        val namespace = if (entry.isManualCorrection) "correction" else "glossary"
        return "$namespace:${normalizeSourceKey(entry.source)}"
    }

    private fun encodeEntry(entry: TranslationMemoryEntry): String = listOf(
        RECORD_VERSION,
        entry.type.name,
        if (entry.isProtected) "1" else "0",
        entry.source,
        entry.target,
    ).joinToString(SEPARATOR)

    private fun decodeEntry(encoded: String): TranslationMemoryEntry? {
        val parts = encoded.split(SEPARATOR)
        if (parts.firstOrNull() == RECORD_VERSION && parts.size >= 5) {
            val type = runCatching { TranslationMemoryEntryType.valueOf(parts[1]) }.getOrNull()
                ?: return null
            return sanitizeEntry(
                TranslationMemoryEntry(
                    source = parts[3],
                    target = parts.drop(4).joinToString(SEPARATOR),
                    type = type,
                    isProtected = parts[2] == "1",
                ),
            ).takeIf { it.source.isNotEmpty() && it.target.isNotEmpty() }
        }

        val separatorIndex = encoded.indexOf(SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex >= encoded.lastIndex) return null
        return sanitizeEntry(
            TranslationMemoryEntry(
                source = encoded.substring(0, separatorIndex),
                target = encoded.substring(separatorIndex + SEPARATOR.length),
            ),
        ).takeIf { it.source.isNotEmpty() && it.target.isNotEmpty() }
    }

    private fun encodeScope(scope: TranslationMemoryScope): String =
        scope.mangaKey + SEPARATOR + scope.mangaTitle

    private fun decodeScope(encoded: String): TranslationMemoryScope? {
        val separatorIndex = encoded.indexOf(SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex >= encoded.lastIndex) return null
        return TranslationMemoryScope(
            mangaKey = encoded.substring(0, separatorIndex),
            mangaTitle = encoded.substring(separatorIndex + SEPARATOR.length),
        )
    }

    private fun preferenceKey(mangaKey: String): String = PREF_PREFIX + sha256(mangaKey)

    private fun revisionKey(mangaKey: String): String = REVISION_PREFIX + sha256(mangaKey)

    internal fun normalizeMangaKey(title: String): String = title.trim().lowercase()

    private fun normalizeSourceKey(source: String): String = normalizeTranslationText(source)
        .replace('\n', ' ')
        .lowercase()
        .replace(Regex(" {2,}"), " ")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
