package eu.kanade.translation

enum class TranslationRequestOrigin {
    USER_ACTION,
    DOWNLOAD_COMPLETION,
}

object TranslationLaunchPolicy {
    fun canStart(
        origin: TranslationRequestOrigin,
        autoTranslateEnabled: Boolean,
    ): Boolean = when (origin) {
        TranslationRequestOrigin.USER_ACTION -> true
        TranslationRequestOrigin.DOWNLOAD_COMPLETION -> autoTranslateEnabled
    }
}
