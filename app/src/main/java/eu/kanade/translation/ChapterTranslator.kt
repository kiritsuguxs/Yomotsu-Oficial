package eu.kanade.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.model.CURRENT_TRANSLATION_GEOMETRY_VERSION
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.Translation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationBlockGrouper
import eu.kanade.translation.model.TranslationRegion
import eu.kanade.translation.model.normalizeTranslationText
import eu.kanade.translation.model.withReliableSourceMetrics
import eu.kanade.translation.recognizer.OcrEngineFactory
import eu.kanade.translation.recognizer.OcrEngineManager
import eu.kanade.translation.recognizer.OcrEngineType
import eu.kanade.translation.recognizer.OcrImage
import eu.kanade.translation.recognizer.OcrTextBlock
import eu.kanade.translation.recognizer.SpeechBubbleAnalyzer
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.translator.TextTranslator
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import logcat.LogPriority
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.at.ATMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Closeable
import java.io.InputStream

internal data class TextTranslatorConfiguration(
    val primaryEngine: Int,
    val fallbackEngine: Int,
    val fromLang: TextRecognizerLanguage,
    val toLang: TextTranslatorLanguage,
    val model: String,
    val apiKey: String,
    val temperature: String,
    val maxOutputTokens: String,
    val deepLApiKey: String,
) {
    companion object {
        fun from(
            preferences: TranslationPreferences,
            fromLang: TextRecognizerLanguage,
            toLang: TextTranslatorLanguage,
        ) = TextTranslatorConfiguration(
            primaryEngine = preferences.translationEngine().get(),
            fallbackEngine = preferences.translationFallbackEngine().get(),
            fromLang = fromLang,
            toLang = toLang,
            model = preferences.translationEngineModel().get(),
            apiKey = preferences.translationEngineApiKey().get(),
            temperature = preferences.translationEngineTemperature().get(),
            maxOutputTokens = preferences.translationEngineMaxOutputTokens().get(),
            deepLApiKey = preferences.deepLApiKey().get(),
        )
    }
}

class ChapterTranslator(
    private val context: Context,
    private val provider: TranslationProvider,
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
) {

    private val _queueState = MutableStateFlow<List<Translation>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var translationJob: Job? = null

    private val notifier = TranslationNotifier(context)
    private val sessionLock = Any()
    private var completedChaptersInSession = 0
    private var totalChaptersInSession = 0

    val isRunning: Boolean
        get() = translationJob?.isActive == true

    @Volatile
    var isPaused: Boolean = false

    private val ocrEngineManager = OcrEngineManager { type, language ->
        eu.kanade.translation.detection.createExperimentalDbnetOcrEngine(
            context, OcrEngineFactory.create(context, type, language),
            enabled = { translationPreferences.dbnetExperimental().get() },
        )
    }
    private var textTranslator: TextTranslator? = null
    private var textTranslatorConfiguration: TextTranslatorConfiguration? = null

    private suspend fun ensureTextTranslator(
        fromLang: TextRecognizerLanguage,
        toLang: TextTranslatorLanguage,
    ): TextTranslator {
        val configuration = TextTranslatorConfiguration.from(
            preferences = translationPreferences,
            fromLang = fromLang,
            toLang = toLang,
        )
        if (textTranslator == null || textTranslatorConfiguration != configuration) {
            withContext(Dispatchers.IO) { textTranslator?.close() }
            textTranslator = TextTranslators.fromPref(translationPreferences.translationEngine())
                .build(translationPreferences, fromLang, toLang)
            textTranslatorConfiguration = configuration
        }
        return requireNotNull(textTranslator)
    }

    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) return false
        val pending = queueState.value.filter { it.status != Translation.State.TRANSLATED }
        pending.forEach { if (it.status != Translation.State.QUEUE) it.status = Translation.State.QUEUE }
        isPaused = false
        launchTranslatorJob()
        return pending.isNotEmpty()
    }

    fun stop(reason: String? = null) {
        cancelTranslatorJob()
        queueState.value.filter { it.status == Translation.State.TRANSLATING }.forEach { it.status = Translation.State.ERROR }
        if (reason == null || !isPaused) releaseOcrEngineAsync()
        if (reason != null) return
        isPaused = false
    }

    fun pause() {
        cancelTranslatorJob()
        queueState.value.filter { it.status == Translation.State.TRANSLATING }.forEach { it.status = Translation.State.QUEUE }
        isPaused = true
    }

    fun clearQueue() {
        cancelTranslatorJob()
        releaseOcrEngineAsync()
        internalClearQueue()
    }

    private fun launchTranslatorJob() {
        if (isRunning) return
        translationJob = scope.launch {
            val activeTranslationFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeTranslations = queue.asSequence().filter { it.status.value <= Translation.State.TRANSLATING.value }
                        .groupBy { it.source }.toList().take(5).map { (_, translations) -> translations.first() }
                    emit(activeTranslations)
                    if (activeTranslations.isEmpty()) break
                    combine(activeTranslations.map(Translation::statusFlow)) { states -> states.contains(Translation.State.ERROR) }
                        .filter { it }.first()
                }
            }.distinctUntilChanged()
            supervisorScope {
                val translationJobs = mutableMapOf<Translation, Job>()
                activeTranslationFlow.collectLatest { activeTranslations ->
                    translationJobs.filter { it.key !in activeTranslations }.forEach { (download, job) ->
                        job.cancel(); translationJobs.remove(download)
                    }
                    activeTranslations.filter { it !in translationJobs }.forEach { translation ->
                        translationJobs[translation] = launchTranslationJob(translation)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchTranslationJob(translation: Translation) = launchIO {
        try {
            translateChapter(translation)
            if (translation.status == Translation.State.TRANSLATED) {
                synchronized(sessionLock) { completedChaptersInSession++ }
                removeFromQueue(translation)
            }
            if (areAllTranslationsFinished()) stop()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e); stop()
        }
    }

    private fun cancelTranslatorJob() { translationJob?.cancel(); translationJob = null }

    fun queueChapter(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        if (provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source) != null) return
        if (queueState.value.any { it.chapter.id == chapter.id }) return
        val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
        val toLang = TextTranslatorLanguage.fromPref(translationPreferences.translateToLanguage())
        val ocrType = OcrEngineType.fromPref(translationPreferences.ocrEngine())
        if (!ocrType.supports(fromLang)) { context.toast(ATMR.strings.error_paddle_ocr_english_only); return }
        val engine = TextTranslators.fromPref(translationPreferences.translationEngine())
        if (engine == TextTranslators.MLKIT && !TextTranslatorLanguage.mlkitSupportedLanguages().contains(toLang)) {
            context.toast(ATMR.strings.error_mlkit_language_unsupported); return
        }
        addToQueue(Translation(source, manga, chapter, fromLang, toLang))
    }

    private suspend fun translateChapter(translation: Translation) {
        val pipelineStart = System.nanoTime()
        val selectedOcrType = OcrEngineType.fromPref(translationPreferences.ocrEngine())
        var processedPages = 0
        var accumulatedOcrTimeMs = 0L
        try {
            val chapterNumber = synchronized(sessionLock) { completedChaptersInSession + 1 }
            notifier.onPreparing(translation.manga, translation.chapter.name, chapterNumber, currentSessionTotal())
            val translator = ensureTextTranslator(translation.fromLang, translation.toLang)
            val strictOcrGrouping = selectedOcrType == OcrEngineType.PADDLE_OCR
            val translationMangaDir = provider.getMangaDir(translation.manga.title, translation.source)
            val saveFile = provider.getTranslationFileName(translation.chapter.name, translation.chapter.scanlator)
            val chapterPath = downloadProvider.findChapterDir(
                translation.chapter.name, translation.chapter.scanlator, translation.chapter.url,
                translation.manga.title, translation.source,
            ) ?: error("O capítulo original não foi encontrado para tradução.")

            val pages = mutableMapOf<String, PageTranslation>()
            val tmpFile = translationMangaDir.createFile("tmp")!!
            val chapterPages = getChapterPages(chapterPath)
            val totalPageCount = chapterPages.pages.size
            try {
                chapterPages.use {
                    withContext(Dispatchers.IO) {
                        chapterPages.pages.forEachIndexed { index, (fileName, streamFn) ->
                            coroutineContext.ensureActive()
                            streamFn().use { tmpFile.openOutputStream().use { out -> it.copyTo(out) } }
                            val result = ocrEngineManager.withEngine(selectedOcrType, translation.fromLang) { recognizer ->
                                recognizer.recognize(OcrImage(tmpFile.uri) { checkNotNull(tmpFile.openInputStream()) })
                            }
                            processedPages++
                            accumulatedOcrTimeMs += result.metrics?.ocrTimeMs ?: 0L
                            val blocks = result.blocks.map { block ->
                                block.copy(text = normalizeTranslationText(block.text))
                            }.filter { it.text.length > 1 }
                            val analysisBitmap = decodeAnalysisBitmap(tmpFile, result.width, result.height)
                            val pageTranslation = try {
                                convertToPageTranslation(
                                    blocks, result.width, result.height,
                                    translationPreferences.translateSoundEffects().get(),
                                    analysisBitmap?.let {
                                        SpeechBubbleAnalyzer(it, result.width, result.height, blocks.map { block ->
                                            TranslationRegion(block.x, block.y, block.width, block.height)
                                        })
                                    },
                                    strictOcrGrouping,
                                )
                            } finally { analysisBitmap?.recycle() }
                            if (pageTranslation.blocks.isNotEmpty()) pages[fileName] = pageTranslation
                            notifier.onPageProgress(
                                translation.manga, translation.chapter.name, index + 1, totalPageCount,
                                chapterNumber, currentSessionTotal(),
                            )
                        }
                    }
                }
            } finally { tmpFile.delete() }

            val automaticPages = pages.mapValuesTo(mutableMapOf()) { (_, page) ->
                PageTranslation(page.blocks.filter(::isTranslatableBlock).toMutableList(), page.imgWidth, page.imgHeight)
            }
            withContext(Dispatchers.IO) {
                notifier.onTextTranslation(
                    translation.manga, translation.chapter.name, totalPageCount, chapterNumber, currentSessionTotal(),
                )
                translator.translate(
                    automaticPages,
                    ComicTranslationContext(translation.manga.title, translation.chapter.name),
                )
                automaticPages.values.forEach { page ->
                    page.blocks = TranslationBlockGrouper.group(page.blocks, strict = strictOcrGrouping)
                }
                pages.forEach { (pageKey, page) ->
                    val manualCandidates = page.blocks.filterNot(::isTranslatableBlock)
                    val translatedBlocks = automaticPages[pageKey]?.blocks.orEmpty()
                    page.blocks = (translatedBlocks + manualCandidates)
                        .sortedWith(compareBy<TranslationBlock> { it.y }.thenBy { it.x }).toMutableList()
                }
            }
            translationMangaDir.createFile(saveFile)!!.openOutputStream().use { output -> Json.encodeToStream(pages, output) }
            translation.status = Translation.State.TRANSLATED
            logcat(LogPriority.INFO) {
                "OCR chapter engine=$selectedOcrType pages=$processedPages ocrMs=$accumulatedOcrTimeMs " +
                    "totalMs=${elapsedMs(pipelineStart)} failures=0"
            }
        } catch (error: Throwable) {
            translation.status = Translation.State.ERROR
            notifier.onError(translation.manga, translation.chapter.name, error)
            logcat(LogPriority.ERROR, error) {
                "OCR chapter failure engine=$selectedOcrType pages=$processedPages ocrMs=$accumulatedOcrTimeMs " +
                    "totalMs=${elapsedMs(pipelineStart)} failures=1 error=${error::class.simpleName}"
            }
        }
    }

    private fun convertToPageTranslation(
        blocks: List<OcrTextBlock>, width: Int, height: Int, includeSoundEffects: Boolean,
        bubbleAnalyzer: SpeechBubbleAnalyzer?, strictOcrGrouping: Boolean,
    ): PageTranslation {
        val translation = PageTranslation(imgWidth = width.toFloat(), imgHeight = height.toFloat())
        for (block in blocks) {
            translation.blocks.add(
                TranslationBlock(
                    text = block.text, width = block.width, height = block.height,
                    symWidth = block.symbolWidth, symHeight = block.symbolHeight, angle = block.angle,
                    x = block.x, y = block.y,
                    dbnetCleanupMask = block.dbnetCleanupMask,
                ).withReliableSourceMetrics(),
            )
        }
        if (bubbleAnalyzer == null) {
            if (!includeSoundEffects) translation.blocks.removeAll { block -> isLikelySoundEffect(block, width, height) }
            return translation
        }
        analyzeTranslationBlocks(translation.blocks, bubbleAnalyzer)
        translation.blocks = TranslationBlockGrouper.group(translation.blocks, strict = strictOcrGrouping)
        analyzeTranslationBlocks(translation.blocks, bubbleAnalyzer)
        if (!includeSoundEffects) translation.blocks.removeAll { block -> isLikelySoundEffect(block, width, height) }
        return translation
    }

    private fun analyzeTranslationBlocks(blocks: List<TranslationBlock>, analyzer: SpeechBubbleAnalyzer) {
        blocks.forEach { block ->
            val analysis = analyzer.analyze(block)
            block.cleanupRegion = analysis.cleanupRegion
            block.layoutRegion = analysis.layoutRegion
            block.backgroundColor = analysis.backgroundColor
            block.foregroundColor = analysis.foregroundColor
            block.balloonDetected = analysis.balloonDetected
            block.geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION
        }
    }

    private fun isTranslatableBlock(block: TranslationBlock): Boolean {
        val layout = block.layoutRegion ?: return false
        val widthToHeight = layout.width / layout.height.coerceAtLeast(1f)
        if (widthToHeight < MIN_TRANSLATABLE_WIDTH_TO_HEIGHT) return false
        return block.balloonDetected || block.backgroundColor != null
    }

    private fun decodeAnalysisBitmap(file: UniFile, width: Int, height: Int): Bitmap? = runCatching {
        var sampleSize = 1
        while (true) {
            val sampledPixels = (width / sampleSize).toLong() * (height / sampleSize).toLong()
            val nextSampleSize = sampleSize * 2
            if (sampledPixels <= MAX_ANALYSIS_PIXELS || width / nextSampleSize < MIN_ANALYSIS_WIDTH) break
            sampleSize = nextSampleSize
        }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565; inSampleSize = sampleSize }
        file.openInputStream().use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun isLikelySoundEffect(block: TranslationBlock, pageWidth: Int, pageHeight: Int): Boolean {
        val compactText = block.text.filterNot(Char::isWhitespace)
        if (compactText.length !in 1..12) return false
        val glyphWidthRatio = block.symWidth / pageWidth.coerceAtLeast(1)
        val glyphHeightRatio = block.symHeight / pageHeight.coerceAtLeast(1)
        return glyphWidthRatio >= 0.045f || glyphHeightRatio >= 0.045f
    }

    private fun getChapterPages(chapterPath: UniFile): ChapterPages {
        if (chapterPath.isFile) {
            val reader = chapterPath.archiveReader(context)
            try {
                val pages = reader.useEntries { entries ->
                    entries.filter { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .map { entry -> Pair(entry.name) { reader.getInputStream(entry.name)!! } }.toList()
                }
                return ChapterPages(pages, reader)
            } catch (error: Throwable) { reader.close(); throw error }
        } else {
            val pages = chapterPath.listFiles()!!.filter { ImageUtil.isImage(it.name) }
                .map { entry -> Pair(entry.name!!) { entry.openInputStream() } }.toList()
            return ChapterPages(pages)
        }
    }

    private class ChapterPages(
        val pages: List<Pair<String, () -> InputStream>>,
        private val closeable: Closeable? = null,
    ) : Closeable {
        override fun close() { closeable?.close() }
    }

    private fun areAllTranslationsFinished(): Boolean = queueState.value.none { it.status.value <= Translation.State.TRANSLATING.value }

    private fun addToQueue(translation: Translation) {
        synchronized(sessionLock) {
            if (_queueState.value.isEmpty() && !isRunning) { completedChaptersInSession = 0; totalChaptersInSession = 0 }
            totalChaptersInSession++
        }
        translation.status = Translation.State.QUEUE
        _queueState.update { it + translation }
    }

    private fun removeFromQueue(translation: Translation) {
        _queueState.update {
            if (translation.status == Translation.State.TRANSLATING || translation.status == Translation.State.QUEUE) {
                translation.status = Translation.State.NOT_TRANSLATED
            }
            it - translation
        }
    }

    private inline fun removeFromQueueIf(predicate: (Translation) -> Boolean) {
        _queueState.update { queue ->
            val translations = queue.filter { predicate(it) }
            translations.forEach { translation ->
                if (translation.status == Translation.State.TRANSLATING || translation.status == Translation.State.QUEUE) {
                    translation.status = Translation.State.NOT_TRANSLATED
                }
            }
            queue - translations
        }
    }

    fun removeFromQueue(chapter: Chapter) { removeFromQueueIf { it.chapter.id == chapter.id } }
    fun removeFromQueue(manga: Manga) { removeFromQueueIf { it.manga.id == manga.id } }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { translation ->
                if (translation.status == Translation.State.TRANSLATING || translation.status == Translation.State.QUEUE) {
                    translation.status = Translation.State.NOT_TRANSLATED
                }
            }
            emptyList()
        }
        synchronized(sessionLock) { completedChaptersInSession = 0; totalChaptersInSession = 0 }
        notifier.dismissProgress()
    }

    fun onWorkerFinished() {
        synchronized(sessionLock) {
            completedChaptersInSession = 0
            totalChaptersInSession = _queueState.value.count { it.status == Translation.State.ERROR }
        }
        if (!isPaused) releaseOcrEngineAsync()
        notifier.dismissProgress()
    }

    private fun releaseOcrEngineAsync() {
        scope.launch {
            try { ocrEngineManager.release() } catch (error: Throwable) { logcat(LogPriority.ERROR, error) }
        }
    }

    private fun currentSessionTotal(): Int = synchronized(sessionLock) {
        totalChaptersInSession.coerceAtLeast(completedChaptersInSession + 1)
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_ANALYSIS_PIXELS = 6_000_000L
        const val MIN_ANALYSIS_WIDTH = 128
        const val MIN_TRANSLATABLE_WIDTH_TO_HEIGHT = 0.38f
    }
}
