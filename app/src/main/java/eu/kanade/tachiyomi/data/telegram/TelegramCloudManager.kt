package eu.kanade.tachiyomi.data.telegram

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import logcat.LogPriority

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class TelegramCloudManager(
    private val context: Context,
    private val network: NetworkHelper = Injekt.get(),
    private val preferences: tachiyomi.domain.telegram.TelegramPreferences = Injekt.get()
) {

    private val botToken get() = preferences.botToken.get()
    private val chatId get() = preferences.chatId.get()
    
    // URL padrão oficial. Limite: 50MB
    private val apiUrl get() = "https://api.telegram.org/bot$botToken"

    companion object {
        // Mutex global para garantir que os uploads ocorram em fila (um por vez)
        // Isso evita tomar block/ban por flood (Rate Limit: máx 20 mensagens/minuto)
        private val uploadMutex = Mutex()
    }

    suspend fun uploadChapter(manga: Manga, chapter: Chapter, cbzFile: UniFile) {
        withContext(Dispatchers.IO) {
            if (!preferences.enableTelegramCloud.get()) {
                return@withContext
            }
            if (botToken.isBlank() || chatId.isBlank()) {
                logcat(LogPriority.INFO) { "TelegramCloudManager: Bot Token ou Chat ID vazios. Pulando upload." }
                return@withContext
            }

            uploadMutex.withLock {
                try {
                    var tempFile: File? = null
                    val fileToUpload: File = if (cbzFile.uri.scheme == "file") {
                        File(cbzFile.uri.path!!)
                    } else {
                        tempFile = File(context.cacheDir, cbzFile.name ?: "capitulo.cbz")
                        cbzFile.openInputStream().use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile
                    }

                    // Verifica limite de 50MB da Bot API HTTP Padrão
                    val fileSizeMb = fileToUpload.length() / (1024 * 1024)
                    if (fileSizeMb > 49L) {
                        logcat(LogPriority.WARN) { "TelegramCloudManager: Arquivo ${fileToUpload.name} tem $fileSizeMb MB. Excede o limite de 50MB da API HTTP do Telegram! Upload cancelado." }
                        tempFile?.delete()
                        return@withLock
                    }

                    val caption = "#Yomotsu\n\nObra: ${manga.title}\nCapítulo: ${chapter.name}"
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", chatId)
                        .addFormDataPart("caption", caption)
                    
                    val fileBody = fileToUpload.asRequestBody("application/zip".toMediaType())
                    requestBody.addFormDataPart("document", fileToUpload.name, fileBody)

                    val request = Request.Builder()
                        .url("$apiUrl/sendDocument")
                        .post(requestBody.build())
                        .build()

                    logcat(LogPriority.INFO) { "Iniciando upload para Telegram: ${fileToUpload.name} ($fileSizeMb MB)" }

                    var success = false
                    var retryCount = 0
                    
                    while (!success && retryCount < 3) {
                        network.client.newCall(request).execute().use { response ->
                            if (response.code == 429) {
                                // Flood Wait (Too Many Requests)
                                val responseBody = response.body?.string() ?: ""
                                val retryAfter = try {
                                    JSONObject(responseBody).getJSONObject("parameters").getInt("retry_after")
                                } catch (e: Exception) {
                                    10
                                }
                                logcat(LogPriority.WARN) { "Telegram Rate Limit (429)! Aguardando $retryAfter segundos..." }
                                delay(retryAfter * 1000L)
                                retryCount++
                            } else if (!response.isSuccessful) {
                                logcat(LogPriority.ERROR) { "Erro no upload para Telegram: ${response.body?.string()}" }
                                break // Erro crítico, não tenta de novo
                            } else {
                                logcat(LogPriority.INFO) { "Upload para Telegram Cloud concluído: ${manga.title} - ${chapter.name}" }
                                success = true
                                
                                // Deletar local se a opção estiver ativada
                                if (preferences.deleteLocalAfterUpload.get()) {
                                    cbzFile.delete()
                                    logcat(LogPriority.INFO) { "Arquivo local apagado após upload com sucesso." }
                                }
                            }
                        }
                    }
                    
                    tempFile?.delete()
                    
                    // Delay fixo de 3.5 segundos entre cada upload para respeitar o limite de 20 msgs/minuto
                    delay(3500)

                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Falha no envio para o Telegram" }
                }
            }
        }
    }
}
