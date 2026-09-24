package eu.kanade.tachiyomi.extension.novel.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class NovelAudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var playbackJob: Job? = null

    var isPlaying: Boolean = false
        private set

    var onStateChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yomotsu:NovelAudioPlayerWakeLock")
        initNativeTts()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours max
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed to acquire wake lock" }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {}
    }

    private fun initNativeTts(onReady: (() -> Unit)? = null) {
        if (textToSpeech != null && isTtsInitialized) {
            onReady?.invoke()
            return
        }
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.endsWith("_last") == true) {
                            scope.launch(Dispatchers.Main) {
                                stop()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        scope.launch(Dispatchers.Main) {
                            stop()
                        }
                    }
                })
                textToSpeech?.let { configureNativeVoice(it, Locale("pt", "BR")) }
                onReady?.invoke()
            } else {
                isTtsInitialized = false
            }
        }
    }

    private fun configureNativeVoice(tts: TextToSpeech, targetLocale: Locale, speed: Float = 1.02f) {
        try {
            val availableVoices = tts.voices
            if (!availableVoices.isNullOrEmpty()) {
                val matchingVoices = availableVoices.filter { voice ->
                    voice.locale.language.equals(targetLocale.language, ignoreCase = true)
                }

                if (matchingVoices.isNotEmpty()) {
                    fun isFemale(voice: Voice): Boolean {
                        val nameLower = voice.name.lowercase(Locale.ROOT)
                        val hasFemaleFeature = voice.features?.any { feature ->
                            val fLower = feature.lowercase(Locale.ROOT)
                            fLower == "female" || fLower.contains("female") ||
                                fLower.contains("mulher") || fLower.contains("feminino")
                        } ?: false

                        return hasFemaleFeature ||
                            nameLower.contains("female") ||
                            nameLower.contains("#female") ||
                            nameLower.contains("-f-") ||
                            nameLower.contains("_f_") ||
                            nameLower.contains("feminino") ||
                            nameLower.contains("mulher") ||
                            nameLower.contains("-afs") ||
                            nameLower.contains("-ptd") ||
                            nameLower.contains("-pte")
                    }

                    val offlineVoices = matchingVoices.filter { !it.isNetworkConnectionRequired }
                    val pool = if (offlineVoices.isNotEmpty()) offlineVoices else matchingVoices

                    val bestVoice = pool.maxWithOrNull(
                        compareBy<Voice> { isFemale(it) }
                            .thenBy { it.locale.country.equals(targetLocale.country, ignoreCase = true) }
                            .thenBy { it.quality }
                    )

                    if (bestVoice != null) {
                        tts.voice = bestVoice
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            tts.setPitch(1.08f)
            tts.setSpeechRate(speed)
        } catch (_: Throwable) {}
    }

    fun play(
        text: String,
        voice: NovelTtsVoice,
        speed: Float,
        isPortuguese: Boolean,
    ) {
        stop()
        val sanitized = NovelSpeechSanitizer.sanitize(text)
        if (sanitized.isBlank()) {
            onError?.invoke("Texto vazio para narração.")
            return
        }

        isPlaying = true
        onStateChanged?.invoke(true)
        acquireWakeLock()

        if (voice.isOnline) {
            playWithEdgeTts(sanitized, voice, speed, isPortuguese)
        } else {
            playWithNativeTts(sanitized, speed, isPortuguese)
        }
    }

    private fun playWithNativeTts(text: String, speed: Float, isPortuguese: Boolean) {
        initNativeTts {
            val tts = textToSpeech ?: return@initNativeTts
            val targetLocale = if (isPortuguese) Locale("pt", "BR") else Locale.getDefault()
            var effectiveLocale = targetLocale
            var langResult = tts.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                langResult = tts.setLanguage(Locale("pt"))
                effectiveLocale = Locale("pt")
            }
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
                effectiveLocale = Locale.getDefault()
            }
            configureNativeVoice(tts, effectiveLocale, speed)
            tts.stop()

            val chunks = splitIntoChunks(text, 1000)
            chunks.forEachIndexed { index, chunk ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val isLast = index == chunks.lastIndex
                val utteranceId = if (isLast) "novel_p_${index}_last" else "novel_p_$index"
                tts.speak(chunk, queueMode, null, utteranceId)
            }
        }
    }

    private fun playWithEdgeTts(
        text: String,
        voice: NovelTtsVoice,
        speed: Float,
        isPortuguese: Boolean,
    ) {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            val chunks = splitIntoChunks(text, 1200)
            var currentIdx = 0

            suspend fun playChunk(chunkIdx: Int) {
                if (!isPlaying || chunkIdx >= chunks.size) {
                    withContext(Dispatchers.Main) { stop() }
                    return
                }

                val chunkText = chunks[chunkIdx]
                val audioBytes = EdgeTtsClient.synthesize(chunkText, voice.id, speed)

                if (audioBytes == null || audioBytes.isEmpty()) {
                    // Fallback seamlessly to native offline TTS if offline or Edge network drops
                    logcat(LogPriority.INFO) { "Edge TTS unavailable, falling back to Native TTS for chunk $chunkIdx" }
                    val remainingText = chunks.subList(chunkIdx, chunks.size).joinToString("\n\n")
                    withContext(Dispatchers.Main) {
                        playWithNativeTts(remainingText, speed, isPortuguese)
                    }
                    return
                }

                val tempFile = File(context.cacheDir, "edge_tts_chunk_${chunkIdx % 2}.mp3")
                try {
                    FileOutputStream(tempFile).use { it.write(audioBytes) }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to write TTS audio cache" }
                    withContext(Dispatchers.Main) {
                        playWithNativeTts(chunkText, speed, isPortuguese)
                    }
                    return
                }

                withContext(Dispatchers.Main) {
                    if (!isPlaying) return@withContext
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setOnCompletionListener {
                            scope.launch(Dispatchers.IO) {
                                playChunk(chunkIdx + 1)
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            scope.launch(Dispatchers.IO) {
                                playChunk(chunkIdx + 1)
                            }
                            true
                        }
                        prepare()
                        start()
                    }
                }
            }

            playChunk(currentIdx)
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Throwable) {}
        mediaPlayer = null

        try {
            textToSpeech?.stop()
        } catch (_: Throwable) {}

        releaseWakeLock()

        if (isPlaying) {
            isPlaying = false
            onStateChanged?.invoke(false)
        }
    }

    fun release() {
        stop()
        try {
            textToSpeech?.shutdown()
        } catch (_: Throwable) {}
        textToSpeech = null
    }

    private fun splitIntoChunks(text: String, maxChunkSize: Int = 1000): List<String> {
        val lines = text.split(Regex("\\r?\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (line in lines) {
            if (current.length + line.length + 1 > maxChunkSize) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.clear()
                }
                if (line.length > maxChunkSize) {
                    var remaining = line
                    while (remaining.length > maxChunkSize) {
                        val splitIdx = remaining.take(maxChunkSize).lastIndexOfAny(charArrayOf('.', '!', '?', ';', ',', ' '))
                            .takeIf { it > 100 } ?: maxChunkSize
                        chunks.add(remaining.substring(0, splitIdx).trim())
                        remaining = remaining.substring(splitIdx).trim()
                    }
                    if (remaining.isNotEmpty()) current.append(remaining)
                } else {
                    current.append(line)
                }
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(line)
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }
        return chunks
    }
}
