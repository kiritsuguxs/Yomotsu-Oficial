package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.TranslationPreferences

class DbnetPreferencesTest {
    @Test fun `new detector preference is independent and off by default`() {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())
        assertFalse(preferences.dbnetExperimental().get())
        assertEquals("dbnet_experimental", preferences.dbnetExperimental().key())
        assertEquals(0, preferences.ocrEngine().get())
        assertEquals("ocr_engine", preferences.ocrEngine().key())
    }
}
