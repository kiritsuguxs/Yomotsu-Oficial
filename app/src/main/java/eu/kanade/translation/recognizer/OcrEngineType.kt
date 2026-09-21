package eu.kanade.translation.recognizer

import tachiyomi.core.common.preference.Preference

enum class OcrEngineType(val preferenceValue: Int) {
    ML_KIT(0),
    PADDLE_OCR(1),
    ;

    fun supports(language: TextRecognizerLanguage): Boolean =
        this == ML_KIT || language == TextRecognizerLanguage.ENGLISH

    companion object {
        fun fromPreferenceValue(value: Int): OcrEngineType =
            entries.firstOrNull { it.preferenceValue == value } ?: ML_KIT

        fun fromPref(preference: Preference<Int>): OcrEngineType {
            val storedValue = preference.get()
            val type = fromPreferenceValue(storedValue)
            if (type.preferenceValue != storedValue) {
                preference.set(ML_KIT.preferenceValue)
            }
            return type
        }
    }
}
