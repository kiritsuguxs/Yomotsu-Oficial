package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ReaderAutoScrollPreferencesTest {

    @Test
    fun `auto scroll speed uses the stable persisted key and default`() {
        val preferences = ReaderPreferences(InMemoryPreferenceStore())

        assertEquals("pref_webtoon_auto_scroll_speed", preferences.autoScrollSpeed.key())
        assertEquals(60, preferences.autoScrollSpeed.defaultValue())
        assertEquals(60, preferences.autoScrollSpeed.get())
    }

    @Test
    fun `auto scroll speed exposes the supported range and step`() {
        assertEquals(20, ReaderPreferences.AUTO_SCROLL_SPEED_MIN)
        assertEquals(180, ReaderPreferences.AUTO_SCROLL_SPEED_MAX)
        assertEquals(10, ReaderPreferences.AUTO_SCROLL_SPEED_STEP)
        assertEquals(60, ReaderPreferences.AUTO_SCROLL_SPEED_DEFAULT)
    }
}
