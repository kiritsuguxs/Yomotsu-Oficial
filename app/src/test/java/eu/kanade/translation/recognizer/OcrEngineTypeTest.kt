package eu.kanade.translation.recognizer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.translation.TranslationPreferences

class OcrEngineTypeTest {

    @Test
    fun `ml kit is the default for unknown preference values`() {
        assertEquals(OcrEngineType.ML_KIT, OcrEngineType.fromPreferenceValue(-1))
        assertEquals(OcrEngineType.ML_KIT, OcrEngineType.fromPreferenceValue(99))
    }

    @Test
    fun `invalid stored preference is reset to ml kit`() {
        val preference = TestPreference(99)

        assertEquals(OcrEngineType.ML_KIT, OcrEngineType.fromPref(preference))
        assertEquals(OcrEngineType.ML_KIT.preferenceValue, preference.get())
    }

    @Test
    fun `paddle is explicit and supports only english`() {
        assertEquals(OcrEngineType.PADDLE_OCR, OcrEngineType.fromPreferenceValue(1))
        assertTrue(OcrEngineType.PADDLE_OCR.supports(TextRecognizerLanguage.ENGLISH))
        assertFalse(OcrEngineType.PADDLE_OCR.supports(TextRecognizerLanguage.CHINESE))
        assertFalse(OcrEngineType.PADDLE_OCR.supports(TextRecognizerLanguage.JAPANESE))
        assertFalse(OcrEngineType.PADDLE_OCR.supports(TextRecognizerLanguage.KOREAN))
    }

    @Test
    fun `ml kit supports every existing source language`() {
        assertTrue(TextRecognizerLanguage.entries.all(OcrEngineType.ML_KIT::supports))
    }

    @Test
    fun `translation preferences keep ml kit as the backward compatible default`() {
        val preferences = TranslationPreferences(InMemoryPreferenceStore())

        assertEquals("ocr_engine", preferences.ocrEngine().key())
        assertEquals(OcrEngineType.ML_KIT.preferenceValue, preferences.ocrEngine().get())
    }
}

private class TestPreference(initialValue: Int) : Preference<Int> {
    private val state = MutableStateFlow(initialValue)

    override fun key(): String = "ocr_engine"

    override fun get(): Int = state.value

    override fun set(value: Int) {
        state.value = value
    }

    override fun isSet(): Boolean = true

    override fun delete() = Unit

    override fun defaultValue(): Int = OcrEngineType.ML_KIT.preferenceValue

    override fun changes(): Flow<Int> = state

    override fun stateIn(scope: CoroutineScope): StateFlow<Int> = state
}
