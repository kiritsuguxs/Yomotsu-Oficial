package eu.kanade.tachiyomi.data.telegram

import android.content.Context
import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import logcat.LogPriority

data class PendingUpload(
    val file: UniFile,
    val manga: Manga? = null,
    val chapter: Chapter? = null,
)

class TelegramCloudManager(
    private val context: Context,
    private val preferences: tachiyomi.domain.telegram.TelegramPreferences = Injekt.get()
) : Client.ResultHandler {

    private var tdClient: Client? = null
    private val isAuthReady = MutableStateFlow(false)
    
    // Usando as chaves públicas do Telegram Desktop (Open Source) para evitar API_ID_INVALID
    private val apiId = 2040 
    private val apiHash = "b18441a1ff607e10a989891a5462e627"

    companion object {
        // Mutex rigoroso: Garante apenas 1 upload por vez na fila do aplicativo.
        // A própria TDLib já lida com o FloodWait (Erro 429) automaticamente em C++,
        // mas o Mutex impede que o app sobrecarregue a RAM do celular enfileirando 50 arquivos de 200MB de uma vez.
        private val uploadMutex = Mutex()
    }

    init {
        if (preferences.enableTelegramCloud.get()) {
            initializeTdlib()
        }
    }

    private fun initializeTdlib() {
        if (tdClient != null) return
        tdClient = Client.create(this, null, null)
    }

    private val pendingUploads = java.util.concurrent.ConcurrentHashMap<Long, PendingUpload>()

    private fun showNotification(title: String, text: String, progress: Int = 0, max: Int = 0, ongoing: Boolean = false, autoDismiss: Boolean = false) {
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        // Reutilizando CHANNEL_DOWNLOADER_PROGRESS do Yomotsu para o canal
        val builder = androidx.core.app.NotificationCompat.Builder(context, "downloader_progress_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)

        if (max > 0) {
            builder.setProgress(max, progress, progress == 0)
        } else {
            builder.setProgress(0, 0, false)
        }

        if (autoDismiss) {
            builder.setTimeoutAfter(3000)
        }

        try {
            notificationManager.notify(889911, builder.build())
        } catch (e: SecurityException) {
            logcat(LogPriority.ERROR) { "Sem permissão de notificação para a Nuvem Telegram" }
        }
    }

    override fun onResult(update: TdApi.Object?) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                when (update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        val parameters = TdApi.SetTdlibParameters().apply {
                            databaseDirectory = File(context.filesDir, "tdlib_v2").absolutePath
                            useMessageDatabase = false
                            useSecretChats = false
                            apiId = this@TelegramCloudManager.apiId
                            apiHash = this@TelegramCloudManager.apiHash
                            systemLanguageCode = "pt"
                            deviceModel = "Android"
                            applicationVersion = "Yomotsu-Cloud-1.0"
                        }
                        tdClient?.send(parameters) { result ->
                            if (result is TdApi.Error) {
                                logcat(LogPriority.ERROR) { "Erro SetTdlibParameters: ${result.message}" }
                            }
                        }
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        val botToken = preferences.botToken.get()
                        if (botToken.isNotBlank()) {
                            tdClient?.send(TdApi.CheckAuthenticationBotToken(botToken)) { authResult ->
                                if (authResult is TdApi.Error) {
                                    logcat(LogPriority.ERROR) { "Erro na autenticação do Bot: ${authResult.message}" }
                                    showNotification("Nuvem Telegram", "Erro de Token: ${authResult.message}", ongoing = false)
                                }
                            }
                        } else {
                            logcat(LogPriority.ERROR) { "Bot Token não configurado!" }
                        }
                    }
                    is TdApi.AuthorizationStateReady -> {
                        logcat(LogPriority.INFO) { "TDLib Ready! Autenticado com sucesso." }
                        isAuthReady.value = true
                    }
                }
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                val messageId = update.oldMessageId
                val file = pendingUploads.remove(messageId)
                logcat(LogPriority.INFO) { "Upload finalizado pelo Telegram com SUCESSO. Arquivos restantes na fila local: ${pendingUploads.size}" }
                
                showNotification("Nuvem Telegram", "Upload concluído!", autoDismiss = true)
                
                if (file != null && preferences.deleteLocalAfterUpload.get()) {
                    val deleted = file.file.delete()
                    logcat(LogPriority.INFO) { "Arquivo local apagado após upload: $deleted" }
                    if (file.manga != null && file.chapter != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val downloadCache = Injekt.get<eu.kanade.tachiyomi.data.download.DownloadCache>()
                                downloadCache.removeChapter(file.chapter, file.manga)
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Erro ao atualizar cache de download" }
                            }
                        }
                    }
                }
            }
            is TdApi.UpdateMessageSendFailed -> {
                val messageId = update.oldMessageId
                pendingUploads.remove(messageId)
                logcat(LogPriority.ERROR) { "Falha confirmada pelo Telegram no envio da mensagem." }
                showNotification("Nuvem Telegram", "Erro no upload", ongoing = false)
            }
        }
    }

    suspend fun uploadChapter(manga: Manga, chapter: Chapter, cbzFile: UniFile) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get()) {
                return@withContext
            }

            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) {
                logcat(LogPriority.ERROR) { "TDLib não inicializou a tempo para o upload." }
                showNotification("Nuvem Telegram", "Erro: Cliente não inicializado", ongoing = false)
                return@withContext
            }

            val chatIdString = preferences.chatId.get()
            if (chatIdString.isBlank()) {
                logcat(LogPriority.ERROR) { "Chat ID vazio." }
                return@withContext
            }

            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext

            uploadMutex.withLock {
                try {
                    val fileToUpload = if (cbzFile.uri.scheme == "file") {
                        File(cbzFile.uri.path!!)
                    } else {
                        val temp = File(context.cacheDir, cbzFile.name ?: "capitulo.cbz")
                        cbzFile.openInputStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                        temp
                    }

                    val fileSizeMb = fileToUpload.length() / (1024 * 1024)
                    if (fileSizeMb > 1999L) {
                        logcat(LogPriority.WARN) { "Arquivo excede 2 GB!" }
                        return@withLock
                    }

                    showNotification("Nuvem Telegram", "Enviando: ${manga.title} - ${chapter.name}...", progress = 0, max = 100, ongoing = true)

                    val description = manga.description?.let {
                        if (it.length > 500) it.take(497) + "..." else it
                    } ?: "Sem sinopse disponível."

                    val textCaption = "#Yomotsu\n\n📖 Obra: ${manga.title}\n📄 Capítulo: ${chapter.name}\n\n📝 Sinopse: $description"
                    val caption = TdApi.FormattedText(textCaption, emptyArray())

                    val coverCache = Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
                    val coverFile = coverCache.getCoverFile(manga.thumbnailUrl)
                    val thumbnail = if (coverFile != null && coverFile.exists()) {
                        TdApi.InputThumbnail(TdApi.InputFileLocal(coverFile.absolutePath), 0, 0)
                    } else {
                        null
                    }

                    val inputFile = TdApi.InputFileLocal(fileToUpload.absolutePath)
                    val document = TdApi.InputMessageDocument(inputFile, thumbnail, false, caption)

                    val sendMessageRequest = TdApi.SendMessage(
                        targetChatId,
                        null,
                        null,
                        null,
                        null,
                        document
                    )

                    tdClient?.send(sendMessageRequest) { result ->
                        if (result is TdApi.Message) {
                            // Salva a referência real do arquivo para deletar no callback global Succeeded!
                            pendingUploads[result.id] = PendingUpload(cbzFile, manga, chapter)
                            logcat(LogPriority.INFO) { "Mensagem despachada pro TDLib. ID = ${result.id}" }
                        } else if (result is TdApi.Error) {
                            logcat(LogPriority.ERROR) { "Erro ao empurrar pra TDLib: ${result.message}" }
                            showNotification("Nuvem Telegram", "Erro: ${result.message}", ongoing = false)
                        }
                    }

                    delay(5000)

                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Erro no fluxo de preparo da TDLib" }
                    showNotification("Nuvem Telegram", "Falha interna no upload", ongoing = false)
                }
            }
        }
    }


    suspend fun restoreChapterFromTelegram(mangaTitle: String, chapterName: String, chapterDirname: String = "", localSourceMangaDir: UniFile): Boolean {
        if (!preferences.enableTelegramCloud.get()) return false
        
        return withContext(Dispatchers.IO) {
            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) {
                logcat(LogPriority.ERROR) { "TDLib não inicializou a tempo para o restore." }
                return@withContext false
            }

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext false

            var fromMessageId = 0L
            var foundMessage: TdApi.Message? = null
            var iterations = 0
            
            while (iterations < 50) {
                val messages = kotlin.coroutines.suspendCoroutine<Array<TdApi.Message>> { cont ->
                    tdClient?.send(TdApi.GetChatHistory(targetChatId, fromMessageId, 0, 100, false)) { result ->
                        if (result is TdApi.Messages) {
                            cont.resumeWith(Result.success(result.messages))
                        } else {
                            cont.resumeWith(Result.success(emptyArray()))
                        }
                    }
                }
                
                if (messages.isEmpty()) break
                
                foundMessage = messages.firstOrNull { msg ->
                    val content = msg.content
                    if (content is TdApi.MessageDocument) {
                        val caption = content.caption.text
                        val fileName = content.document.fileName ?: ""
                        val combined = "$caption $fileName"
                        combined.contains(mangaTitle, ignoreCase = true) && combined.contains(chapterName, ignoreCase = true)
                    } else {
                        false
                    }
                }
                
                if (foundMessage != null) break
                
                fromMessageId = messages.last().id
                iterations++
            }
            
            kotlin.coroutines.suspendCoroutine { continuation ->
                if (foundMessage == null) {
                    logcat(LogPriority.INFO) { "Capítulo não encontrado no histórico do Telegram." }
                    continuation.resumeWith(Result.success(false))
                    return@suspendCoroutine
                }
                
                val content = foundMessage!!.content
                if (content is TdApi.MessageDocument) {
                    val fileId = content.document.document.id
                    logcat(LogPriority.INFO) { "Capítulo encontrado no Telegram! Iniciando download da Nuvem..." }
                    showNotification("Nuvem Telegram", "Restaurando: $mangaTitle - $chapterName", ongoing = true)

                    tdClient?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) { downloadResult ->
                        if (downloadResult is TdApi.File) {
                            CoroutineScope(Dispatchers.IO).launch {
                                var currentFile: TdApi.File = downloadResult
                                while (!currentFile.local.isDownloadingCompleted) {
                                    delay(500)
                                    currentFile = kotlin.coroutines.suspendCoroutine<TdApi.File> { fileCont ->
                                        tdClient?.send(TdApi.GetFile(fileId)) { res ->
                                            if (res is TdApi.File) fileCont.resumeWith(Result.success(res))
                                            else fileCont.resumeWith(Result.success(currentFile))
                                        }
                                    }
                                }

                                val downloadedPath: String = currentFile.local.path
                                if (downloadedPath.isNotBlank()) {
                                    val sourceFile = java.io.File(downloadedPath)
                                    if (sourceFile.exists()) {
                                        val targetFileName = if (chapterDirname.isNotBlank()) "$chapterDirname.cbz" else "$chapterName.cbz"
                                        val targetFile = localSourceMangaDir.createFile(targetFileName)
                                        if (targetFile != null) {
                                            sourceFile.inputStream().use { input ->
                                                targetFile.openOutputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            logcat(LogPriority.INFO) { "Restaurado com sucesso!" }
                                            showNotification("Nuvem Telegram", "Capítulo restaurado", autoDismiss = true)
                                            continuation.resumeWith(Result.success(true))
                                            return@launch
                                        }
                                    }
                                }
                                continuation.resumeWith(Result.success(false))
                            }
                        } else {
                            continuation.resumeWith(Result.success(false))
                        }
                    }
                } else {
                    continuation.resumeWith(Result.success(false))
                }
            }
        }
    }
    suspend fun uploadNovelChapter(manga: Manga, chapter: Chapter, txtFile: File) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get() || txtFile.length() == 0L) return@withContext
            if (tdClient == null) initializeTdlib()
            
            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) {
                logcat(LogPriority.ERROR) { "TDLib não inicializou a tempo para o upload da Novel." }
                showNotification("Nuvem Telegram", "Erro: Cliente não inicializado", ongoing = false)
                return@withContext
            }

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext

            uploadMutex.withLock {
                try {
                    val description = manga.description?.let {
                        if (it.length > 500) it.take(497) + "..." else it
                    } ?: "Sem sinopse disponível."

                    val textCaption = "#YomotsuNovel\n\n📖 Obra: ${manga.title}\n📄 Capítulo: ${chapter.name}\n\n📝 Sinopse: $description"
                    val caption = TdApi.FormattedText(textCaption, emptyArray())

                    val coverCache = Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
                    val coverFile = coverCache.getCoverFile(manga.thumbnailUrl)
                    val thumbnail = if (coverFile != null && coverFile.exists()) {
                        TdApi.InputThumbnail(TdApi.InputFileLocal(coverFile.absolutePath), 0, 0)
                    } else {
                        null
                    }

                    val inputFile = TdApi.InputFileLocal(txtFile.absolutePath)
                    val document = TdApi.InputMessageDocument(inputFile, thumbnail, false, caption)
                    val sendMessageRequest = TdApi.SendMessage(targetChatId, null, null, null, null, document)

                    showNotification("Nuvem Telegram", "Enviando Novel: ${manga.title}...", progress = 0, max = 100, ongoing = true)

                    tdClient?.send(sendMessageRequest) { result ->
                        if (result is TdApi.Message) {
                            val uFile = UniFile.fromFile(txtFile)
                            if (uFile != null) {
                                pendingUploads[result.id] = PendingUpload(uFile, manga, chapter)
                            }
                        } else if (result is TdApi.Error) {
                            logcat(LogPriority.ERROR) { "Erro ao enviar Novel pra TDLib: ${result.message}" }
                            showNotification("Nuvem Telegram", "Erro: ${result.message}", ongoing = false)
                        }
                    }
                    delay(3000)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Erro ao enviar novel" }
                    showNotification("Nuvem Telegram", "Falha interna no upload", ongoing = false)
                }
            }
        }
    }

    suspend fun restoreNovelChapter(mangaTitle: String, chapterName: String, localTargetFile: java.io.File): Boolean {
        if (!preferences.enableTelegramCloud.get()) return false
        
        return withContext(Dispatchers.IO) {
            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) {
                logcat(LogPriority.ERROR) { "TDLib não inicializou a tempo para o restore da Novel." }
                return@withContext false
            }

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext false
            var fromMessageId = 0L
            var foundMessage: TdApi.Message? = null
            var iterations = 0
            
            while (iterations < 50) {
                val messages = kotlin.coroutines.suspendCoroutine<Array<TdApi.Message>> { cont ->
                    tdClient?.send(TdApi.GetChatHistory(targetChatId, fromMessageId, 0, 100, false)) { result ->
                        if (result is TdApi.Messages) {
                            cont.resumeWith(Result.success(result.messages))
                        } else {
                            cont.resumeWith(Result.success(emptyArray()))
                        }
                    }
                }
                
                if (messages.isEmpty()) break
                
                foundMessage = messages.firstOrNull { msg ->
                    val content = msg.content
                    if (content is TdApi.MessageDocument) {
                        val caption = content.caption.text
                        val fileName = content.document.fileName ?: ""
                        val combined = "$caption $fileName"
                        combined.contains(mangaTitle, ignoreCase = true) && combined.contains(chapterName, ignoreCase = true)
                    } else {
                        false
                    }
                }
                
                if (foundMessage != null) break
                
                fromMessageId = messages.last().id
                iterations++
            }
            
            kotlin.coroutines.suspendCoroutine { continuation ->
                if (foundMessage == null) {
                    logcat(LogPriority.INFO) { "Capítulo não encontrado no histórico do Telegram." }
                    continuation.resumeWith(Result.success(false))
                    return@suspendCoroutine
                }
                
                val content = foundMessage!!.content
                if (content is TdApi.MessageDocument) {
                    val fileId = content.document.document.id
                    tdClient?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) { downloadResult ->
                        if (downloadResult is TdApi.File) {
                            val downloadedPath = downloadResult.local.path
                            if (downloadedPath.isNotBlank()) {
                                val sourceFile = java.io.File(downloadedPath)
                                if (sourceFile.exists()) {
                                    sourceFile.copyTo(localTargetFile, overwrite = true)
                                    continuation.resumeWith(Result.success(true))
                                    return@send
                                }
                            }
                        }
                        continuation.resumeWith(Result.success(false))
                    }
                } else {
                    continuation.resumeWith(Result.success(false))
                }
            }
        }
    }
}
