package mihon.core.migration.migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TranslationFontMigrationTest {

    @Test
    fun `supported font values remain unchanged`() {
        listOf(0, 1, 3, 4, 5).forEach { value ->
            val preference = InMemoryPreferenceStore().getInt("translation_font", 0)
            preference.set(value)

            TranslationFontMigration().migrate(preference)

            assertEquals(value, preference.get())
            assertTrue(preference.isSet())
        }
    }

    @Test
    fun `legacy comic font value migrates to Anime Ace`() {
        val preference = InMemoryPreferenceStore().getInt("translation_font", 0)
        preference.set(2)

        TranslationFontMigration().migrate(preference)

        assertEquals(0, preference.get())
    }

    @Test
    fun `unknown font values migrate to Anime Ace`() {
        listOf(-1, 6, 99).forEach { value ->
            val preference = InMemoryPreferenceStore().getInt("translation_font", 0)
            preference.set(value)

            TranslationFontMigration().migrate(preference)

            assertEquals(0, preference.get())
        }
    }

    @Test
    fun `unset preference remains at Anime Ace default`() {
        val preference = InMemoryPreferenceStore().getInt("translation_font", 0)

        TranslationFontMigration().migrate(preference)

        assertEquals(0, preference.get())
        assertFalse(preference.isSet())
    }
}
