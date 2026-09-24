package eu.kanade.tachiyomi.extension.novel.tts

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object EdgeTtsClient {

    private val client: OkHttpClient by lazy { Injekt.get<NetworkHelper>().client }

    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_VERSION = "143.0.3650.75"
    private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_VERSION"
    private const val WIN_EPOCH = 11644473600L

    const val VOICE_FRANCISCA = "pt-BR-FranciscaNeural"
    const val VOICE_THALITA = "pt-BR-ThalitaMultilingualNeural"
    const val VOICE_ANTONIO = "pt-BR-AntonioNeural"

    fun generateSecMsGec(): String {
        val nowSec = System.currentTimeMillis() / 1000L
        var ticks = nowSec + WIN_EPOCH
        ticks -= (ticks % 300)
        val fileTimeTicks = ticks * 10_000_000L
        val strToHash = "$fileTimeTicks$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(strToHash.toByteArray(Charsets.US_ASCII))
        return hash.joinToString("") { "%02X".format(it) }
    }

    private fun getFormattedDate(): String {
        val format = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(System.currentTimeMillis())
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    suspend fun synthesize(
        text: String,
        voice: String = VOICE_FRANCISCA,
        rate: Float = 1.0f,
    ): ByteArray? {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return null

        return withTimeoutOrNull(25_000L) {
            val deferred = CompletableDeferred<ByteArray?>()
            val audioStream = ByteArrayOutputStream()
            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val secMsGec = generateSecMsGec()

            val wssUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

            val request = Request.Builder()
                .url(wssUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .build()

            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val timestamp = getFormattedDate()
                    val configMsg = "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                    webSocket.send(configMsg)

                    val ratePercent = ((rate - 1.0f) * 100).toInt()
                    val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
                    val escaped = escapeXml(cleanText)
                    val reqId = UUID.randomUUID().toString().replace("-", "")

                    val ssmlMsg = "X-RequestId:$reqId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp}Z\r\n" +
                        "Path:ssml\r\n\r\n" +
                        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='pt-BR'>" +
                        "<voice name='$voice'>" +
                        "<prosody pitch='+0Hz' rate='$rateStr' volume='+0%'>" +
                        escaped +
                        "</prosody>" +
                        "</voice>" +
                        "</speak>"

                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, "Done")
                        deferred.complete(audioStream.toByteArray())
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val data = bytes.toByteArray()
                    if (data.size > 2) {
                        val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                        val offset = 2 + headerLen
                        if (data.size > offset) {
                            audioStream.write(data, offset, data.size - offset)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    logcat(LogPriority.WARN, t) { "Edge TTS synthesis failed: ${t.message}" }
                    if (!deferred.isCompleted) {
                        deferred.complete(null)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!deferred.isCompleted) {
                        deferred.complete(if (audioStream.size() > 0) audioStream.toByteArray() else null)
                    }
                }
            })

            deferred.invokeOnCancellation {
                ws.cancel()
            }

            deferred.await()
        }
    }
}
