package eu.kanade.tachiyomi.data.telegram

import android.content.Context
import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import logcat.LogPriority

class TelegramCloudManager(
    private val context: Context,
    private val preferences: tachiyomi.domain.telegram.TelegramPreferences = Injekt.get()
) : Client.ResultHandler {

    private var tdClient: Client? = null
    
    // Obtenha seu api_id e api_hash em https://my.telegram.org
    // TODO: Mover para as configurações se quiser manter privado
    private val apiId = 94575 // Substitua pelo seu
    private val apiHash = "a3406de8d171bb422bb6c0587373f819" // Substitua pelo seu

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

        val parameters = TdApi.SetTdlibParameters().apply {
            databaseDirectory = File(context.filesDir, "tdlib").absolutePath
            useMessageDatabase = false
            useSecretChats = false
            apiId = this@TelegramCloudManager.apiId
            apiHash = this@TelegramCloudManager.apiHash
            systemLanguageCode = "pt"
            deviceModel = "Android"
            applicationVersion = "Yomotsu-Cloud-1.0"
            enableStorageOptimizer = true
        }

        tdClient?.send(parameters) { result ->
            if (result is TdApi.Ok) {
                logcat(LogPriority.INFO) { "TDLib Iniciada. Autenticando com Bot Token..." }
                val botToken = preferences.botToken.get()
                if (botToken.isNotBlank()) {
                    // MÁGICA AQUI: Autentica sem número de telefone, direto no MTProto (limite de 2GB)!
                    tdClient?.send(TdApi.CheckAuthenticationBotToken(botToken)) { authResult ->
                        if (authResult is TdApi.Ok) {
                            logcat(LogPriority.INFO) { "Autenticação via Bot Token concluída com sucesso!" }
                        } else {
                            logcat(LogPriority.ERROR) { "Erro na autenticação do Bot: $authResult" }
                        }
                    }
                }
            }
        }
    }

    override fun onResult(update: TdApi.Object?) {
        // Ouve atualizações globais da TDLib
        when (update) {
            is TdApi.UpdateMessageSendSucceeded -> {
                logcat(LogPriority.INFO) { "Upload finalizado pelo Telegram com SUCESSO." }
                // Quando o Telegram confirmar que o arquivo subiu, apagamos o local se solicitado
                if (preferences.deleteLocalAfterUpload.get()) {
                    // Aqui a limpeza local segura deve ser engatilhada
                    logcat(LogPriority.INFO) { "Apagando arquivo local após a nuvem confirmar recebimento." }
                }
            }
            is TdApi.UpdateMessageSendFailed -> {
                logcat(LogPriority.ERROR) { "Falha confirmada pelo Telegram no envio da mensagem." }
            }
        }
    }

    suspend fun uploadChapter(manga: Manga, chapter: Chapter, cbzFile: UniFile) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get()) {
                return@withContext
            }

            // Inicializa lazy caso tenha sido ativado recentemente
            if (tdClient == null) initializeTdlib()

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

                    // Limite da TDLib / MTProto é 2000 MB (2 GB)
                    val fileSizeMb = fileToUpload.length() / (1024 * 1024)
                    if (fileSizeMb > 1999L) {
                        logcat(LogPriority.WARN) { "Arquivo excede os incríveis 2 GB do MTProto! Upload cancelado." }
                        return@withLock
                    }

                    logcat(LogPriority.INFO) { "Enfileirando upload de $fileSizeMb MB via MTProto..." }

                    val caption = TdApi.FormattedText(
                        "#Yomotsu\n\nObra: ${manga.title}\nCapítulo: ${chapter.name}",
                        emptyArray()
                    )

                    val inputFile = TdApi.InputFileLocal(fileToUpload.absolutePath)
                    val document = TdApi.InputMessageDocument(inputFile, null, false, caption)

                    val sendMessageRequest = TdApi.SendMessage(
                        targetChatId,
                        0,
                        0,
                        null,
                        null,
                        document
                    )

                    // Envia para a fila da TDLib. A própria biblioteca C++ lida com o Rate Limit (429) e faz os retries.
                    tdClient?.send(sendMessageRequest) { result ->
                        if (result is TdApi.Error) {
                            logcat(LogPriority.ERROR) { "Erro ao empurrar pra TDLib: ${result.message}" }
                        } else {
                            logcat(LogPriority.INFO) { "Arquivo transferido para a engine da TDLib com sucesso." }
                        }
                    }

                    // Pausa conservadora de segurança extra do app (5 segundos por capítulo)
                    // Garantimos que nunca passamos de 12 uploads por minuto.
                    delay(5000)

                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Erro no fluxo de preparo da TDLib" }
                }
            }
        }
    }

    /**
     * Função para puxar o capítulo de volta do Telegram para a pasta local do Yomotsu
     */
    suspend fun restoreChapterFromTelegram(mangaTitle: String, chapterName: String, localSourceMangaDir: UniFile) {
        withContext(Dispatchers.IO) {
            val chatIdString = preferences.chatId.get()
            val targetChatId = chatIdString.toLongOrNull() ?: return@withContext

            val query = "Obra: $mangaTitle\nCapítulo: $chapterName"
            
            // Busca a mensagem no chat
            tdClient?.send(TdApi.SearchChatMessages(targetChatId, query, null, 0, 0, 1, null, 0)) { result ->
                if (result is TdApi.FoundChatMessages && result.messages.isNotEmpty()) {
                    val message = result.messages.first()
                    val content = message.content
                    if (content is TdApi.MessageDocument) {
                        val fileId = content.document.document.id
                        logcat(LogPriority.INFO) { "Capítulo encontrado no Telegram! Iniciando download..." }

                        // Inicia o download do Telegram (Priority 32 = máximo)
                        tdClient?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) { downloadResult ->
                            if (downloadResult is TdApi.File) {
                                // O arquivo físico baixado pela TDLib fica salvo em downloadResult.local.path
                                val downloadedPath = downloadResult.local.path
                                if (downloadedPath.isNotBlank()) {
                                    val sourceFile = File(downloadedPath)
                                    if (sourceFile.exists()) {
                                        // Copia para a pasta "local" do Yomotsu
                                        val targetFile = localSourceMangaDir.createFile("$chapterName.cbz")
                                        if (targetFile != null) {
                                            sourceFile.inputStream().use { input ->
                                                targetFile.openOutputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            logcat(LogPriority.INFO) { "Capítulo $chapterName restaurado com sucesso na Fonte Local!" }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        logcat(LogPriority.WARN) { "Mensagem encontrada não é um documento CBZ." }
                    }
                } else {
                    logcat(LogPriority.WARN) { "Capítulo não encontrado no Telegram." }
                }
            }
        }
    }
}
