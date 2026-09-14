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

class TelegramCloudManager(
    private val context: Context,
    private val network: NetworkHelper = Injekt.get()
) {

    // TODO: Adicionar nas configurações (Settings/DataStore)
    private val botToken = ""
    private val chatId = ""
    
    // URL padrão oficial. Limite: 50MB
    // Para usar limite de 2GB: Use uma URL de um servidor local da Bot API do Telegram (ex: http://192.168.x.x:8081)
    private val apiUrl = "https://api.telegram.org/bot$botToken"

    suspend fun uploadChapter(manga: Manga, chapter: Chapter, cbzFile: UniFile) {
        withContext(Dispatchers.IO) {
            if (botToken.isBlank() || chatId.isBlank()) {
                logcat(LogPriority.INFO) { "TelegramCloudManager: Bot Token ou Chat ID vazios. Pulando upload." }
                return@withContext
            }

            try {
                val caption = "#Yomotsu\n\nObra: ${manga.title}\nCapítulo: ${chapter.name}"
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("caption", caption)

                var tempFile: File? = null
                val fileToUpload: File = if (cbzFile.uri.scheme == "file") {
                    File(cbzFile.uri.path!!)
                } else {
                    // Copia do SAF para cache temporário
                    tempFile = File(context.cacheDir, cbzFile.name ?: "capitulo.cbz")
                    cbzFile.openInputStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                }

                val fileBody = fileToUpload.asRequestBody("application/zip".toMediaType())
                requestBody.addFormDataPart("document", fileToUpload.name, fileBody)

                val request = Request.Builder()
                    .url("$apiUrl/sendDocument")
                    .post(requestBody.build())
                    .build()

                logcat(LogPriority.INFO) { "Iniciando upload para Telegram: ${fileToUpload.name} (${fileToUpload.length() / 1024 / 1024} MB)" }

                network.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        logcat(LogPriority.ERROR) { "Erro no upload para Telegram: ${response.body?.string()}" }
                    } else {
                        logcat(LogPriority.INFO) { "Upload para Telegram Cloud concluído: ${manga.title} - ${chapter.name}" }
                        // TODO: Implementar exclusão do capítulo local dependendo da preferência
                    }
                }
                
                tempFile?.delete()

            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Falha no envio para o Telegram" }
            }
        }
    }
}
