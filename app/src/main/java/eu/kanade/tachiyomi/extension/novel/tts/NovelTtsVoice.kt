package eu.kanade.tachiyomi.extension.novel.tts

enum class NovelTtsVoice(
    val id: String,
    val title: String,
    val subtitle: String,
    val isOnline: Boolean,
) {
    EDGE_FRANCISCA(
        id = EdgeTtsClient.VOICE_FRANCISCA,
        title = "Francisca Neural (Estúdio)",
        subtitle = "Voz feminina brasileira ultra-realista",
        isOnline = true,
    ),
    EDGE_THALITA(
        id = EdgeTtsClient.VOICE_THALITA,
        title = "Thalita Neural (Estúdio)",
        subtitle = "Voz feminina jovem, suave e natural",
        isOnline = true,
    ),
    EDGE_ANTONIO(
        id = EdgeTtsClient.VOICE_ANTONIO,
        title = "Antonio Neural (Estúdio)",
        subtitle = "Voz masculina natural de estúdio",
        isOnline = true,
    ),
    ANDROID_NATIVE(
        id = "android_native",
        title = "Android Nativo (Offline)",
        subtitle = "Voz feminina do aparelho sem internet",
        isOnline = false,
    );

    companion object {
        fun fromId(id: String?): NovelTtsVoice {
            return entries.firstOrNull { it.id == id } ?: EDGE_FRANCISCA
        }
    }
}
