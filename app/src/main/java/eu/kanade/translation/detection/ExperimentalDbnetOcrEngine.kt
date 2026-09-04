package eu.kanade.translation.detection

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import eu.kanade.translation.recognizer.OcrEngine
import eu.kanade.translation.recognizer.OcrEngineType
import eu.kanade.translation.recognizer.OcrImage
import eu.kanade.translation.recognizer.OcrPage
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/** Removable opt-in wrapper; original OCR instance and all defaults remain unchanged. */
class ExperimentalDbnetOcrEngine internal constructor(
    context: Context,
    private val existing: OcrEngine,
    private val enabled: () -> Boolean,
    private val deviceSupported: () -> Boolean,
    private val createClient: (Context) -> DbnetDetectionClient,
    createOwnedMlKit: () -> OcrEngine,
    private val notify: (String) -> Unit,
    emitDiagnostic: (String) -> Unit,
    private val validateExifOrientation: (OcrImage) -> Unit = DbnetExifOrientationGate::requireCompatible,
    private val experimentalPage: DbnetExperimentalPageRecognizer? = null,
) : OcrEngine {
    private val context = context.applicationContext
    override val type get() = existing.type
    override val language get() = existing.language
    private var client: DbnetDetectionClient? = null
    private val fullPageMlKit = DbnetFullPageMlKitOwner(
        selectedIsMlKit = existing.type == OcrEngineType.ML_KIT,
        selected = OcrEngineSession(existing),
        createOwnedMlKit = {
            OcrEngineSession(createOwnedMlKit())
        },
    )
    private var modelsReady = false
    private var failed = false
    private val coordinator = DbnetPageCoordinator(emitDiagnostic)

    override suspend fun recognize(image: OcrImage): OcrPage = withContext(Dispatchers.IO) {
        val attempt = fullPageMlKit.beginPage()
        coordinator.execute(
            enabled = enabled() && !failed,
            supported = language == TextRecognizerLanguage.ENGLISH && deviceSupported(),
            experimental = { timings ->
                recognizeExperimental(image, attempt, timings)
            },
            existing = { attempt.fallback(image) },
            onFallback = { reason ->
                failed = true
                val oldClient = client
                client = null
                oldClient?.close()
                notify(reason)
            },
            releaseAfterFallback = { fullPageMlKit.releaseOwned() },
        )
    }

    private suspend fun recognizeExperimental(
        image: OcrImage,
        attempt: DbnetFullPageMlKitAttempt<OcrImage, OcrPage>,
        timings: DbnetPageTimingRecorder,
    ): OcrPage = experimentalPage?.let { pageRecognizer ->
        preparePage(image, timings) { Unit }
        pageRecognizer.recognize(image, attempt, timings) {
            client ?: createClient(context).also { client = it }
        }
    } ?: recognizeRegions(image, attempt, timings)

    private suspend fun recognizeRegions(
        image: OcrImage,
        attempt: DbnetFullPageMlKitAttempt<OcrImage, OcrPage>,
        timings: DbnetPageTimingRecorder,
    ): OcrPage {
        val currentContext = coroutineContext
        val prepared = preparePage(image, timings) {
            var requestFile: File? = null
            try {
                if (!modelsReady) {
                    DbnetModelStore(File(context.filesDir, "models/dbnet-v3")) { url ->
                        val connection = URL(url).openConnection() as HttpsURLConnection
                        connection.connectTimeout = 30_000
                        connection.readTimeout = 30_000
                        try {
                            object : FilterInputStream(connection.inputStream) {
                                override fun close() {
                                    try {
                                        super.close()
                                    } finally {
                                        connection.disconnect()
                                    }
                                }
                            }
                        } catch (error: Throwable) {
                            connection.disconnect()
                            throw error
                        }
                    }.ensureAvailable(
                        checkCancelled = { currentContext.ensureActive() },
                        onDownloadRequired = {
                            notify("DBNet: verificando modelo; no primeiro uso serão baixados 153 MB.")
                        },
                    )
                    modelsReady = true
                }
                val requestDir = File(context.cacheDir, "dbnet-requests").apply { mkdirs() }
                val file = File.createTempFile("page-", ".image", requestDir).also { requestFile = it }
                image.openInputStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= 64L * 1024 * 1024) { "Imagem grande demais para DBNet experimental" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                PreparedPage(file, client ?: createClient(context).also { client = it })
            } catch (error: Throwable) {
                requestFile?.delete()
                throw error
            }
        }
        try {
            val detection = timings.measure(DbnetPageStage.DBNET_REQUEST) {
                prepared.client.detect(prepared.file).also { result ->
                    timings.worker(result.workerTimings)
                    check(result is DetectionResult.Success) { (result as DetectionResult.Failure).reason }
                }
            } as DetectionResult.Success
            timings.counts(regions = detection.regions.size)
            if (detection.regions.isEmpty()) throw DbnetEmptyPageException()
            val groups = timings.measure(DbnetPageStage.GROUPING) {
                DbnetLineGrouping.group(detection.regions)
            }
            timings.counts(groups = groups.size)
            val mlKitPage = timings.measure(DbnetPageStage.ML_KIT) { attempt.recognizeForAssociation(image) }
            timings.counts(mlKitBlocks = mlKitPage.blocks.size)
            val associated = timings.measure(DbnetPageStage.ASSOCIATION) {
                DbnetMlKitAssociation.associate(detection, groups, mlKitPage)
            }
            timings.counts(associatedBlocks = associated.blocks.size)
            return timings.measure(DbnetPageStage.MASK_PREPARATION) { DbnetCleanupMask.prepare(associated) }
        } finally {
            prepared.file.delete()
        }
    }

    private suspend fun <T> preparePage(
        image: OcrImage,
        timings: DbnetPageTimingRecorder,
        prepare: suspend () -> T,
    ): T = timings.measure(DbnetPageStage.PAGE_PREPARATION) {
        validateExifOrientation(image)
        prepare()
    }

    override suspend fun release() {
        try {
            client?.close()
            client = null
        } finally {
            try {
                fullPageMlKit.releaseOwned()
            } finally {
                existing.release()
            }
        }
    }

    private class OcrEngineSession(
        private val engine: OcrEngine,
    ) : DbnetFullPageSession<OcrImage, OcrPage> {
        override suspend fun recognize(input: OcrImage): OcrPage = engine.recognize(input)
        override suspend fun release() = engine.release()
    }

    private data class PreparedPage(
        val file: File,
        val client: DbnetDetectionClient,
    )
}

internal interface DbnetDetectionClient : AutoCloseable {
    suspend fun detect(file: File): DetectionResult
}

internal fun interface DbnetExperimentalPageRecognizer {
    suspend fun recognize(
        image: OcrImage,
        attempt: DbnetFullPageMlKitAttempt<OcrImage, OcrPage>,
        timings: DbnetPageTimingRecorder,
        acquireClient: () -> DbnetDetectionClient,
    ): OcrPage
}

/** DBNet decodes raw pixels while ML Kit applies EXIF transforms, so only a shared normal space is safe. */
internal object DbnetExifOrientationGate {
    fun requireCompatible(image: OcrImage) {
        val orientation = image.openInputStream().use { input ->
            val boundedInput = DbnetBoundedInputStream(input)
            ExifInterface(boundedInput).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            ).also {
                boundedInput.throwIfFailed()
            }
        }
        check(orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL) {
            "DBNet experimental requires a normal EXIF orientation"
        }
    }
}

internal class DbnetBoundedInputStream(
    input: InputStream,
    private val maxBytes: Long = DBNET_MAX_IMAGE_BYTES,
) : FilterInputStream(input) {
    private var consumed = 0L
    private var firstFailure: Throwable? = null

    init {
        require(maxBytes >= 0) { "Negative image size limit" }
    }

    fun throwIfFailed() {
        firstFailure?.let { throw it }
    }

    override fun read(): Int {
        if (consumed == maxBytes) {
            val byte = readByteFromDelegate()
            if (byte < 0) return -1
            throw tooLarge()
        }
        return readByteFromDelegate().also { if (it >= 0) consumed++ }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (consumed == maxBytes) {
            val byte = readByteFromDelegate()
            if (byte < 0) return -1
            throw tooLarge()
        }
        val allowed = minOf(length.toLong(), maxBytes - consumed).toInt()
        return readFromDelegate(buffer, offset, allowed).also { if (it > 0) consumed += it }
    }

    override fun skip(length: Long): Long {
        require(length >= 0) { "Negative skip length" }
        val allowed = minOf(length, maxBytes - consumed)
        return skipFromDelegate(allowed).also { consumed += it }
    }

    private fun readByteFromDelegate(): Int = try {
        super.read()
    } catch (error: IOException) {
        remember(error)
        throw error
    } catch (error: UnsupportedOperationException) {
        remember(error)
        throw error
    }

    private fun readFromDelegate(buffer: ByteArray, offset: Int, length: Int): Int = try {
        super.read(buffer, offset, length)
    } catch (error: IOException) {
        remember(error)
        throw error
    } catch (error: UnsupportedOperationException) {
        remember(error)
        throw error
    }

    private fun skipFromDelegate(length: Long): Long = try {
        super.skip(length)
    } catch (error: IOException) {
        remember(error)
        throw error
    } catch (error: UnsupportedOperationException) {
        remember(error)
        throw error
    }

    private fun tooLarge(): IOException = IOException("Imagem grande demais para DBNet experimental").also(::remember)

    private fun remember(error: Throwable) {
        if (firstFailure == null) firstFailure = error
    }
}

private const val DBNET_MAX_IMAGE_BYTES = 64L * 1024 * 1024
