package eu.kanade.translation.memory

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.translator.TextTranslatorLanguage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TranslationIntelligenceIntegrationTest {

    @Test
    fun `cache normalizes invisible Unicode characters in source and target`() {
        val preferences = MapPreferences()
        val context = ComicTranslationContext("Unicode cache normalization", "Chapter 1")

        TranslationCache.put(
            context,
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
            "Eu\u00A0\u200Bestou aqui",
            "Eu\u202F\u2060estou aqui !",
            preferences.store,
        )

        assertEquals(
            "Eu estou aqui!",
            TranslationCache.get(
                context,
                TextRecognizerLanguage.ENGLISH,
                TextTranslatorLanguage.PORTUGUESE,
                "Eu estou aqui",
                preferences.store,
            ),
        )
    }

    @Test
    fun `glossary revision invalidates stale cache and snapshot remains serializable`() {
        val preferences = MapPreferences()
        val context = ComicTranslationContext("Y12 cache integration test", "Chapter 1")

        TranslationCache.put(
            context,
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
            "Shadow Monarch",
            "Monarca das Trevas",
            preferences.store,
        )
        assertEquals(
            "Monarca das Trevas",
            TranslationCache.get(
                context,
                TextRecognizerLanguage.ENGLISH,
                TextTranslatorLanguage.PORTUGUESE,
                "Shadow Monarch",
                preferences.store,
            ),
        )

        TranslationMemory.remember(
            context = context,
            source = "Shadow Monarch",
            target = "Monarca das Sombras",
            type = TranslationMemoryEntryType.TITLE,
            isProtected = true,
            preferenceStore = preferences.store,
        )

        assertNull(
            TranslationCache.get(
                context,
                TextRecognizerLanguage.ENGLISH,
                TextTranslatorLanguage.PORTUGUESE,
                "Shadow Monarch",
                preferences.store,
            ),
        )

        TranslationCache.put(
            context,
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
            "Shadow Monarch",
            "Monarca das Sombras",
            preferences.store,
        )
        val snapshot = TranslationIntelligenceStore.snapshot(preferences.store)
        val decoded = Json.decodeFromString<TranslationIntelligenceSnapshot>(Json.encodeToString(snapshot))

        assertEquals(TranslationIntelligenceStore.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("Y12 cache integration test", decoded.mangas.single().mangaTitle)
        assertEquals(1, decoded.mangas.single().memory.size)
        assertEquals(1, decoded.mangas.single().cache.size)
    }
}

private class MapPreferences {
    private val values = mutableMapOf<String, Any>()

    val store: PreferenceStore = mockk {
        every { getStringSet(any(), any()) } answers {
            preference(firstArg(), secondArg())
        }
        every { getLong(any(), any()) } answers {
            preference(firstArg(), secondArg())
        }
    }

    private fun <T : Any> preference(key: String, defaultValue: T): Preference<T> =
        MapPreference(key, defaultValue)

    private inner class MapPreference<T : Any>(
        private val key: String,
        private val defaultValue: T,
    ) : Preference<T> {
        override fun key(): String = key

        @Suppress("UNCHECKED_CAST")
        override fun get(): T = values[key] as? T ?: defaultValue

        override fun set(value: T) {
            values[key] = value
        }

        override fun isSet(): Boolean = key in values

        override fun delete() {
            values.remove(key)
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flowOf(get())

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
    }
}
