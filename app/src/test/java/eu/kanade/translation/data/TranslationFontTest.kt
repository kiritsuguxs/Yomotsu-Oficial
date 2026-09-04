package eu.kanade.translation.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TranslationFontTest {

    @Test
    fun `stable values preserve existing choices and reserve comic font`() {
        assertEquals(0, TranslationFont.ANIME_ACE.preferenceValue)
        assertEquals(1, TranslationFont.MANGA_MASTER_BB.preferenceValue)
        assertEquals(3, TranslationFont.BUBBLE_SANS.preferenceValue)
        assertEquals(4, TranslationFont.COMIC_SPICE.preferenceValue)
        assertEquals(5, TranslationFont.BALSAMIQ_SANS.preferenceValue)

        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPreferenceValue(0))
        assertEquals(TranslationFont.MANGA_MASTER_BB, TranslationFont.fromPreferenceValue(1))
        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPreferenceValue(2))
        assertEquals(TranslationFont.BUBBLE_SANS, TranslationFont.fromPreferenceValue(3))
        assertEquals(TranslationFont.COMIC_SPICE, TranslationFont.fromPreferenceValue(4))
        assertEquals(TranslationFont.BALSAMIQ_SANS, TranslationFont.fromPreferenceValue(5))
        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPreferenceValue(99))
    }

    @Test
    fun `settings order contains no legacy comic entry`() {
        assertEquals(
            listOf(0, 1, 3, 4, 5),
            TranslationFont.selectableEntries.map(TranslationFont::preferenceValue),
        )
        assertFalse(TranslationFont.selectableEntries.any { it.label == "Comic Font" })
    }

    @Test
    fun `stored legacy comic value is normalized`() {
        val pref = InMemoryPreferenceStore().getInt("translation_font", 0)
        pref.set(2)

        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPref(pref))
        assertEquals(0, pref.get())
    }

    @Test
    fun `stored unknown value is normalized`() {
        val pref = InMemoryPreferenceStore().getInt("translation_font", 0)
        pref.set(99)

        assertEquals(TranslationFont.ANIME_ACE, TranslationFont.fromPref(pref))
        assertEquals(0, pref.get())
    }
}
