package eu.kanade.tachiyomi.data.telegram

import android.content.Context
import com.hippo.unifile.UniFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PendingUpload(
    val file: UniFile,
    val manga: Manga? = null,
    val chapter: Chapter? = null,
)

data class CloudChapter(
    val name: String,
    val messageId: Long,
    val fileId: Int,
    val remoteFileId: String,
    val date: Long = System.currentTimeMillis(),
)

data class CloudManga(
    val title: String,
    val description: String = "",
    val coverUrl: String = "",
    val chapters: MutableList<CloudChapter> = mutableListOf(),
)

class TelegramCloudManager(
    private val context: Context,
    private val preferences: tachiyomi.domain.telegram.TelegramPreferences = Injekt.get()
) : Client.ResultHandler {

    private var tdClient: Client? = null
    val isAuthReady = MutableStateFlow(false)

    // Usando as chaves publicas do Telegram Desktop (Open Source) para evitar API_ID_INVALID
    private val apiId = 2040
    private val apiHash = "b18441a1ff607e10a989891a5462e627"

    private val pendingUploads = ConcurrentHashMap<Long, PendingUpload>()
    private val pendingDownloads = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val indexMutex = Mutex()

    companion object {
        private val uploadMutex = Mutex()
    }

    init {
        if (preferences.enableTelegramCloud.get()) {
            initializeTdlib()
        }
    }

    fun initializeTdlib() {
        if (tdClient != null) return
        tdClient = Client.create(this, null, null)
    }

    fun showNotification(
        title: String,
        text: String,
        progress: Int = 0,
        max: Int = 0,
        ongoing: Boolean = false,
        autoDismiss: Boolean = false
    ) {
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
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
            logcat(LogPriority.ERROR) { "Sem permissao de notificacao para a Nuvem Telegram" }
        }
    }

    override fun onResult(update: TdApi.Object?) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                when (update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        val parameters = TdApi.SetTdlibParameters().apply {
                            databaseDirectory = File(context.filesDir, "tdlib_v2").absolutePath
                            useMessageDatabase = true
                            useChatInfoDatabase = true
                            useFileDatabase = true
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
                                    logcat(LogPriority.ERROR) { "Erro na autenticacao do Bot: ${authResult.message}" }
                                    showNotification("Nuvem Telegram", "Erro de Token: ${authResult.message}", ongoing = false)
                                }
                            }
                        } else {
                            logcat(LogPriority.ERROR) { "Bot Token nao configurado!" }
                        }
                    }
                    is TdApi.AuthorizationStateReady -> {
                        logcat(LogPriority.INFO) { "TDLib Ready! Autenticado com sucesso." }
                        isAuthReady.value = true

                        val chatIdString = preferences.chatId.get()
                        val targetChatId = chatIdString.toLongOrNull()
                        if (targetChatId != null) {
                            tdClient?.send(TdApi.OpenChat(targetChatId)) { openRes ->
                                logcat(LogPriority.INFO) { "Chat aberto no TDLib: $openRes" }
                            }
                        }
                    }
                }
            }
            is TdApi.UpdateFile -> {
                val file = update.file
                if (file.local.isDownloadingCompleted && file.local.path.isNotBlank()) {
                    val completer = pendingDownloads.remove(file.id)
                    completer?.complete(file.local.path)
                }
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                val messageId = update.oldMessageId
                val pending = pendingUploads.remove(messageId)
                logcat(LogPriority.INFO) { "Upload finalizado pelo Telegram com SUCESSO." }
                showNotification("Nuvem Telegram", "Upload concluido!", autoDismiss = true)

                if (pending != null && preferences.deleteLocalAfterUpload.get()) {
                    val deleted = pending.file.delete()
                    logcat(LogPriority.INFO) { "Arquivo local apagado apos upload: $deleted" }
                    if (pending.manga != null && pending.chapter != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val downloadCache = Injekt.get<eu.kanade.tachiyomi.data.download.DownloadCache>()
                                downloadCache.removeChapter(pending.chapter, pending.manga)
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

    // ==========================================
    // INDICE LOCAL DE OBRAS SALVAS NA NUVEM
    // ==========================================

    private fun getIndexFile(): File = File(context.filesDir, "telegram_cloud_index.json")

    fun getCloudIndex(): List<CloudManga> {
        val file = getIndexFile()
        if (!file.exists()) return emptyList()
        return try {
            val jsonString = file.readText()
            val array = JSONArray(jsonString)
            val list = mutableListOf<CloudManga>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val chaptersArray = obj.getJSONArray("chapters")
                val chapters = mutableListOf<CloudChapter>()
                for (j in 0 until chaptersArray.length()) {
                    val cObj = chaptersArray.getJSONObject(j)
                    chapters.add(
                        CloudChapter(
                            name = cObj.getString("name"),
                            messageId = cObj.optLong("messageId", 0L),
                            fileId = cObj.optInt("fileId", 0),
                            remoteFileId = cObj.optString("remoteFileId", ""),
                            date = cObj.optLong("date", 0L)
                        )
                    )
                }
                list.add(
                    CloudManga(
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        coverUrl = obj.optString("coverUrl", ""),
                        chapters = chapters
                    )
                )
            }
            list
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Erro ao ler telegram_cloud_index.json" }
            emptyList()
        }
    }

    suspend fun saveCloudIndex(list: List<CloudManga>) = indexMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val array = JSONArray()
                for (manga in list) {
                    val obj = JSONObject().apply {
                        put("title", manga.title)
                        put("description", manga.description)
                        put("coverUrl", manga.coverUrl)
                        val cArray = JSONArray()
                        for (chap in manga.chapters) {
                            cArray.put(
                                JSONObject().apply {
                                    put("name", chap.name)
                                    put("messageId", chap.messageId)
                                    put("fileId", chap.fileId)
                                    put("remoteFileId", chap.remoteFileId)
                                    put("date", chap.date)
                                }
                            )
                        }
                        put("chapters", cArray)
                    }
                    array.put(obj)
                }
                getIndexFile().writeText(array.toString(2))
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Erro ao salvar telegram_cloud_index.json" }
            }
        }
    }

    suspend fun registerInIndex(
        mangaTitle: String,
        description: String,
        coverUrl: String,
        chapterName: String,
        messageId: Long,
        fileId: Int,
        remoteFileId: String
    ) {
        val current = getCloudIndex().toMutableList()
        var manga = current.find { it.title.equals(mangaTitle, ignoreCase = true) }
        if (manga == null) {
            manga = CloudManga(title = mangaTitle, description = description, coverUrl = coverUrl)
            current.add(manga)
        }
        val existingIndex = manga.chapters.indexOfFirst { it.name.equals(chapterName, ignoreCase = true) }
        val chapterObj = CloudChapter(
            name = chapterName,
            messageId = messageId,
            fileId = fileId,
            remoteFileId = remoteFileId
        )
        if (existingIndex >= 0) {
            manga.chapters[existingIndex] = chapterObj
        } else {
            manga.chapters.add(chapterObj)
        }
        saveCloudIndex(current)
    }

    // ==========================================
    // UPLOAD DE CAPITULO (MANGA)
    // ==========================================

    suspend fun uploadChapter(manga: Manga, chapter: Chapter, cbzFile: UniFile) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get()) return@withContext
            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) {
                logcat(LogPriority.ERROR) { "TDLib nao inicializou a tempo para o upload." }
                showNotification("Nuvem Telegram", "Erro: Cliente nao inicializado", ongoing = false)
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
                    } ?: "Sem sinopse disponivel."

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
                            pendingUploads[result.id] = PendingUpload(cbzFile, manga, chapter)
                            val doc = (result.content as? TdApi.MessageDocument)?.document?.document
                            val fileId = doc?.id ?: 0
                            val remoteId = doc?.remote?.id ?: ""
                            CoroutineScope(Dispatchers.IO).launch {
                                registerInIndex(
                                    mangaTitle = manga.title,
                                    description = manga.description ?: "",
                                    coverUrl = manga.thumbnailUrl ?: "",
                                    chapterName = chapter.name,
                                    messageId = result.id,
                                    fileId = fileId,
                                    remoteFileId = remoteId
                                )
                            }
                            logcat(LogPriority.INFO) { "Mensagem despachada pro TDLib. ID = ${result.id}" }
                        } else if (result is TdApi.Error) {
                            logcat(LogPriority.ERROR) { "Erro ao empurrar pra TDLib: ${result.message}" }
                            showNotification("Nuvem Telegram", "Erro: ${result.message}", ongoing = false)
                        }
                    }

                    delay(3000)

                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Erro no fluxo de preparo da TDLib" }
                    showNotification("Nuvem Telegram", "Falha interna no upload", ongoing = false)
                }
            }
        }
    }

    // ==========================================
    // DOWNLOAD DE ARQUIVO DA TDLIB
    // ==========================================

    suspend fun downloadFileFromTelegram(fileId: Int, remoteFileId: String = ""): String? {
        if (tdClient == null) initializeTdlib()
        isAuthReady.first { it }

        var targetFileId = fileId

        if (targetFileId == 0 && remoteFileId.isNotBlank()) {
            val fileObj = kotlin.coroutines.suspendCoroutine<TdApi.File?> { cont ->
                tdClient?.send(TdApi.GetRemoteFile(remoteFileId, null)) { res ->
                    if (res is TdApi.File) cont.resumeWith(Result.success(res))
                    else cont.resumeWith(Result.success(null))
                }
            }
            if (fileObj != null) {
                targetFileId = fileObj.id
            }
        }

        if (targetFileId == 0) return null

        val completer = CompletableDeferred<String>()
        pendingDownloads[targetFileId] = completer

        tdClient?.send(TdApi.DownloadFile(targetFileId, 32, 0, 0, false)) { res ->
            if (res is TdApi.File && res.local.isDownloadingCompleted && res.local.path.isNotBlank()) {
                completer.complete(res.local.path)
            } else if (res is TdApi.Error) {
                logcat(LogPriority.ERROR) { "Erro DownloadFile: ${res.message}" }
                completer.completeExceptionally(Exception(res.message))
            }
        }

        return try {
            withTimeoutOrNull(90000) { completer.await() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Excecao aguardando download do Telegram" }
            null
        } finally {
            pendingDownloads.remove(targetFileId)
        }
    }

    // ==========================================
    // PUXAR PARA A FONTE LOCAL (POR OBRA)
    // ==========================================

    suspend fun downloadMangaToLocalSource(
        mangaTitle: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val storageManager = Injekt.get<StorageManager>()
        val localSourceDir = storageManager.getLocalSourceDirectory() ?: return@withContext false

        val sanitizedTitle = DiskUtil.buildValidFilename(mangaTitle)
        val mangaDir = localSourceDir.findFile(sanitizedTitle) ?: localSourceDir.createDirectory(sanitizedTitle) ?: return@withContext false

        val cloudManga = getCloudIndex().find { it.title.equals(mangaTitle, ignoreCase = true) } ?: return@withContext false
        val chapters = cloudManga.chapters
        if (chapters.isEmpty()) return@withContext false

        showNotification("Nuvem Telegram", "Baixando $mangaTitle para Fonte Local...", ongoing = true)

        var downloadedCount = 0
        for ((index, chapter) in chapters.withIndex()) {
            onProgress(index + 1, chapters.size)
            val chapterFilename = DiskUtil.buildValidFilename(chapter.name) + ".cbz"
            if (mangaDir.findFile(chapterFilename) != null) {
                downloadedCount++
                continue
            }

            val downloadedPath = downloadFileFromTelegram(chapter.fileId, chapter.remoteFileId) ?: continue
            val sourceFile = File(downloadedPath)
            if (sourceFile.exists()) {
                val targetFile = mangaDir.createFile(chapterFilename)
                if (targetFile != null) {
                    sourceFile.inputStream().use { input ->
                        targetFile.openOutputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    downloadedCount++
                }
            }
        }

        showNotification("Nuvem Telegram", "$mangaTitle salvo na Fonte Local! ($downloadedCount/${chapters.size})", autoDismiss = true)
        true
    }

    // ==========================================
    // RESTORE DE CAPITULO PARA DOWNLOADER
    // ==========================================

    suspend fun restoreChapterFromTelegram(
        mangaTitle: String,
        chapterName: String,
        chapterDirname: String = "",
        localSourceMangaDir: UniFile
    ): Boolean {
        if (!preferences.enableTelegramCloud.get()) return false

        return withContext(Dispatchers.IO) {
            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) return@withContext false

            // 1. Procura primeiro no indice local da nuvem
            val cloudManga = getCloudIndex().find { it.title.equals(mangaTitle, ignoreCase = true) }
            val cloudChapter = cloudManga?.chapters?.find { it.name.equals(chapterName, ignoreCase = true) }

            var downloadedPath: String? = null

            if (cloudChapter != null) {
                showNotification("Nuvem Telegram", "Restaurando da Nuvem: $mangaTitle - $chapterName", ongoing = true)
                downloadedPath = downloadFileFromTelegram(cloudChapter.fileId, cloudChapter.remoteFileId)
            }

            // 2. Se nao encontrou no indice local, varre o historico do Telegram
            if (downloadedPath == null) {
                val chatIdString = preferences.chatId.get()
                val targetChatId = chatIdString.toLongOrNull() ?: return@withContext false

                var fromMessageId = 0L
                var iterations = 0
                var foundMsg: TdApi.Message? = null

                while (iterations < 20) {
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

                    foundMsg = messages.firstOrNull { msg ->
                        val content = msg.content
                        if (content is TdApi.MessageDocument) {
                            val caption = content.caption.text
                            val fileName = content.document.fileName ?: ""
                            val combined = "$caption $fileName"
                            combined.contains(mangaTitle, ignoreCase = true) && combined.contains(chapterName, ignoreCase = true)
                        } else false
                    }
                    if (foundMsg != null) break
                    fromMessageId = messages.last().id
                    iterations++
                }

                if (foundMsg != null) {
                    val content = foundMsg.content as? TdApi.MessageDocument
                    val fileId = content?.document?.document?.id ?: 0
                    val remoteId = content?.document?.document?.remote?.id ?: ""
                    if (fileId != 0) {
                        showNotification("Nuvem Telegram", "Restaurando: $mangaTitle - $chapterName", ongoing = true)
                        downloadedPath = downloadFileFromTelegram(fileId, remoteId)
                    }
                }
            }

            if (downloadedPath.isNullOrBlank()) return@withContext false

            val sourceFile = File(downloadedPath)
            if (!sourceFile.exists()) return@withContext false

            val targetFileName = if (chapterDirname.isNotBlank()) "$chapterDirname.cbz" else "$chapterName.cbz"
            val targetFile = localSourceMangaDir.createFile(targetFileName) ?: return@withContext false

            sourceFile.inputStream().use { input ->
                targetFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }

            showNotification("Nuvem Telegram", "Capitulo restaurado!", autoDismiss = true)
            true
        }
    }

    // ==========================================
    // SINCRONIZAR TUDO DO CANAL TELEGRAM PRO INDICE
    // ==========================================

    suspend fun syncFromTelegram(): List<CloudManga> = withContext(Dispatchers.IO) {
        if (tdClient == null) initializeTdlib()
        isAuthReady.first { it }

        val chatIdString = preferences.chatId.get()
        val targetChatId = chatIdString.toLongOrNull() ?: return@withContext emptyList()

        var fromMessageId = 0L
        var iterations = 0
        val scannedList = getCloudIndex().toMutableList()

        while (iterations < 50) {
            val messages = kotlin.coroutines.suspendCoroutine<Array<TdApi.Message>> { cont ->
                tdClient?.send(TdApi.GetChatHistory(targetChatId, fromMessageId, 0, 100, false)) { result ->
                    if (result is TdApi.Messages) cont.resumeWith(Result.success(result.messages))
                    else cont.resumeWith(Result.success(emptyArray()))
                }
            }
            if (messages.isEmpty()) break

            for (msg in messages) {
                val content = msg.content
                if (content is TdApi.MessageDocument) {
                    val caption = content.caption.text
                    if (caption.contains("#Yomotsu")) {
                        // Extrai titulo e capitulo da legenda
                        val obraRegex = """📖 Obra:\s*(.+)""".toRegex()
                        val capRegex = """📄 Capítulo:\s*(.+)""".toRegex()

                        val titleMatch = obraRegex.find(caption)?.groupValues?.get(1)?.trim()
                        val capMatch = capRegex.find(caption)?.groupValues?.get(1)?.trim()

                        if (!titleMatch.isNullOrBlank() && !capMatch.isNullOrBlank()) {
                            var manga = scannedList.find { it.title.equals(titleMatch, ignoreCase = true) }
                            if (manga == null) {
                                manga = CloudManga(title = titleMatch)
                                scannedList.add(manga)
                            }
                            if (manga.chapters.none { it.name.equals(capMatch, ignoreCase = true) }) {
                                manga.chapters.add(
                                    CloudChapter(
                                        name = capMatch,
                                        messageId = msg.id,
                                        fileId = content.document.document.id,
                                        remoteFileId = content.document.document.remote.id
                                    )
                                )
                            }
                        }
                    }
                }
            }
            fromMessageId = messages.last().id
            iterations++
        }

        saveCloudIndex(scannedList)
        scannedList
    }

    // ==========================================
    // NOVELS
    // ==========================================

    suspend fun uploadNovelChapter(manga: Manga, chapter: Chapter, txtFile: File) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get() || txtFile.length() == 0L) return@withContext
            if (tdClient == null) initializeTdlib()

            val ready = withTimeoutOrNull(10000) {
                isAuthReady.first { it }
                true
            } ?: false

            if (!ready) return@withContext

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext

            uploadMutex.withLock {
                try {
                    val description = manga.description?.let {
                        if (it.length > 500) it.take(497) + "..." else it
                    } ?: "Sem sinopse disponivel."

                    val textCaption = "#YomotsuNovel\n\n📖 Obra: ${manga.title}\n📄 Capítulo: ${chapter.name}\n\n📝 Sinopse: $description"
                    val caption = TdApi.FormattedText(textCaption, emptyArray())

                    val coverCache = Injekt.get<eu.kanade.tachiyomi.data.cache.CoverCache>()
                    val coverFile = coverCache.getCoverFile(manga.thumbnailUrl)
                    val thumbnail = if (coverFile != null && coverFile.exists()) {
                        TdApi.InputThumbnail(TdApi.InputFileLocal(coverFile.absolutePath), 0, 0)
                    } else null

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
                        }
                    }
                    delay(3000)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Erro ao enviar novel" }
                }
            }
        }
    }

    suspend fun restoreNovelChapter(mangaTitle: String, chapterName: String, localTargetFile: File): Boolean {
        if (!preferences.enableTelegramCloud.get()) return false
        return withContext(Dispatchers.IO) {
            if (tdClient == null) initializeTdlib()
            val ready = withTimeoutOrNull(10000) { isAuthReady.first { it }; true } ?: false
            if (!ready) return@withContext false

            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext false

            var fromMessageId = 0L
            var iterations = 0
            var foundMsg: TdApi.Message? = null

            while (iterations < 30) {
                val messages = kotlin.coroutines.suspendCoroutine<Array<TdApi.Message>> { cont ->
                    tdClient?.send(TdApi.GetChatHistory(targetChatId, fromMessageId, 0, 100, false)) { result ->
                        if (result is TdApi.Messages) cont.resumeWith(Result.success(result.messages))
                        else cont.resumeWith(Result.success(emptyArray()))
                    }
                }
                if (messages.isEmpty()) break
                foundMsg = messages.firstOrNull { msg ->
                    val c = msg.content
                    if (c is TdApi.MessageDocument) {
                        val cap = c.caption.text
                        cap.contains(mangaTitle, ignoreCase = true) && cap.contains(chapterName, ignoreCase = true)
                    } else false
                }
                if (foundMsg != null) break
                fromMessageId = messages.last().id
                iterations++
            }

            if (foundMsg == null) return@withContext false
            val c = foundMsg.content as? TdApi.MessageDocument ?: return@withContext false
            val fileId = c.document.document.id
            val downloadedPath = downloadFileFromTelegram(fileId, c.document.document.remote.id) ?: return@withContext false
            val sFile = File(downloadedPath)
            if (sFile.exists()) {
                sFile.copyTo(localTargetFile, overwrite = true)
                return@withContext true
            }
            false
        }
    }
}
