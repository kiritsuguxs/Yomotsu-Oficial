data class PendingAnimeUpload(
    val file: UniFile,
    val anime: tachiyomi.domain.entries.anime.model.Anime,
    val episode: tachiyomi.domain.items.episode.model.Episode,
)

    // ==========================================
    // ANIME
    // ==========================================

    suspend fun uploadAnimeEpisode(anime: tachiyomi.domain.entries.anime.model.Anime, episode: tachiyomi.domain.items.episode.model.Episode, videoFile: com.hippo.unifile.UniFile) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get()) return@withContext
            if (tdClient == null) initializeTdlib()

            val ready = kotlinx.coroutines.withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) return@withContext

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext

            uploadMutex.withLock {
                try {
                    val description = anime.description?.let {
                        if (it.length > 500) it.take(497) + "..." else it
                    } ?: "Sem sinopse disponivel."

                    val textCaption = "#YomotsuAnime\n\n📖 Obra: ${anime.title}\n📄 Episódio: ${episode.name}\n\n📝 Sinopse: $description"
                    val caption = org.drinkless.tdlib.TdApi.FormattedText(textCaption, emptyArray())

                    val coverCache = uy.kohesive.injekt.Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
                    val coverFile = coverCache.getCoverFile(anime.thumbnailUrl)
                    val thumbnail = if (coverFile != null && coverFile.exists()) {
                        org.drinkless.tdlib.TdApi.InputThumbnail(org.drinkless.tdlib.TdApi.InputFileLocal(coverFile.absolutePath), 0, 0)
                    } else null

                    // TdLib requires local file path. Since videoFile is UniFile, it might not have an absolutePath if it's on SAF.
                    // But Anime downloader uses File internally? UniFile.filePath
                    val path = videoFile.filePath
                    if (path == null) {
                        showNotification("Nuvem Telegram", "Erro: Caminho do arquivo inválido", autoDismiss = true)
                        return@withContext
                    }

                    val inputFile = org.drinkless.tdlib.TdApi.InputFileLocal(path)
                    
                    // Use InputMessageVideo for videos!
                    val video = org.drinkless.tdlib.TdApi.InputMessageVideo(inputFile, thumbnail, emptyArray(), 0, 0, 0, false, caption)
                    val sendMessageRequest = org.drinkless.tdlib.TdApi.SendMessage(targetChatId, null, null, null, null, video)

                    showNotification("Nuvem Telegram", "Enviando Episódio: ${anime.title}...", progress = 0, max = 100, ongoing = true)

                    var success = false
                    tdClient?.send(sendMessageRequest) { result ->
                        if (result is org.drinkless.tdlib.TdApi.Message) {
                            success = true
                            // We don't have pendingAnimeUploads map, we can just log success
                            showNotification("Nuvem Telegram", "Envio Completo: ${anime.title} - ${episode.name}", autoDismiss = true)
                        } else if (result is org.drinkless.tdlib.TdApi.Error) {
                            tachiyomi.core.common.util.system.logcat(logcat.LogPriority.ERROR) { "Erro ao empurrar anime pra TDLib: ${result.message}" }
                            showNotification("Nuvem Telegram", "Erro: ${result.message}", autoDismiss = true)
                        }
                    }
                    kotlinx.coroutines.delay(3000)
                } catch (e: Exception) {
                    tachiyomi.core.common.util.system.logcat(logcat.LogPriority.ERROR, e) { "Erro ao enviar anime" }
                    showNotification("Nuvem Telegram", "Erro fatal ao enviar anime", autoDismiss = true)
                }
            }
        }
    }
