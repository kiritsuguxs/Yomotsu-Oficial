package eu.kanade.translation.translator

enum class TranslationFallbackEngine(val preferenceValue: Int) {
    NONE(0),
    ML_KIT(1),
    GOOGLE(2),
    ;

    fun resolve(
        primary: TextTranslators,
        mlKitTargetSupported: Boolean,
    ): TextTranslators? = when (this) {
        NONE -> null
        ML_KIT -> TextTranslators.MLKIT.takeIf {
            primary != TextTranslators.MLKIT && mlKitTargetSupported
        }
        GOOGLE -> TextTranslators.GOOGLE.takeIf { primary != TextTranslators.GOOGLE }
    }

    companion object {
        fun fromValue(value: Int): TranslationFallbackEngine =
            entries.firstOrNull { it.preferenceValue == value } ?: NONE
    }
}
