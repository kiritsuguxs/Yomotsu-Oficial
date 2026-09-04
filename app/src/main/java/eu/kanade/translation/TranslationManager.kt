package eu.kanade.translation

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.translation.data.TranslationProvider
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationMemory
import eu.kanade.translation.model.AddedManualTranslation
import eu.kanade.translation.model.CURRENT_TRANSLATION_GEOMETRY_VERSION
import eu.kanade.translation.model.ManualTranslationPosition
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.Translation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationPageEditor
import eu.kanade.translation.model.defaultCleanupRegion
import eu.kanade.translation.model.defaultLayoutRegion
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.ComicTranslationContext
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.ceil
import kotlin.math.max

class TranslationManager(
    private val context: Context,
    private val provider: TranslationProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
) {
    private val translator = ChapterTranslator(context, provider)
    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translationFileMutex = Mutex()

    val isRunning: Boolean
        get() = translator.isRunning

    val queueState
        get() = translator.queueState

    fun translatorStart() = translator.start()
    fun translatorStop(reason: String? = null) = translator.stop(reason)

    fun startTranslation() {
        if (queueState.value.isEmpty()) return
        TranslationJob.start(context)
    }

    fun pauseTranslation() {
        translator.pause()
        TranslationJob.stop(context)
    }

    fun clearQueue() {
        translator.clearQueue()
        TranslationJob.stop(context)
    }

    internal fun onTranslationWorkerFinished() {
        translator.onWorkerFinished()
    }

    fun getQueuedTranslationOrNull(chapterId: Long): Translation? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun translateChapter(
        manga: Manga,
        chapter: Chapter,
        origin: TranslationRequestOrigin,
    ) = translateChapters(manga, listOf(chapter), origin)

    fun translateChapters(
        manga: Manga,
        chapters: Iterable<Chapter>,
        origin: TranslationRequestOrigin,
    ) {
        val autoTranslateEnabled = translationPreferences.autoTranslateManga(manga.id).get()
        if (!TranslationLaunchPolicy.canStart(origin, autoTranslateEnabled)) return
        chapters.forEach { chapter -> translator.queueChapter(manga, chapter) }
        startTranslation()
    }

    fun getChapterTranslationStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        title: String,
        sourceId: Long,
    ): Translation.State {
        val translation = getQueuedTranslationOrNull(chapterId)
        if (translation != null) return translation.status
        if (isChapterTranslated(chapterName, scanlator, title, sourceId)) return Translation.State.TRANSLATED
        return Translation.State.NOT_TRANSLATED
    }

    fun isChapterTranslated(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean {
        val source = sourceManager.get(sourceId)
        if (source == null) return false
        val file = provider.findTranslationFile(chapterName, chapterScanlator, mangaTitle, source)
        return file?.exists() == true
    }
    fun getChapterTranslation(
        chapterName: String,
        scanlator: String?,
        title: String,
        source: Source,
    ): Map<String, PageTranslation> {
        try {
            val file = provider.findTranslationFile(
                chapterName,
                scanlator,
                title,
                source,
            ) ?: return emptyMap()
            return getChapterTranslation(file)
        } catch (_: Exception) {
        }
        return emptyMap()
    }

    fun getChapterTranslation(
        file: UniFile,
    ): Map<String, PageTranslation> {
        try {
            return readStoredPages(file)
        } catch (e: Exception) {
            file.delete()
        }
        return emptyMap()
    }

    fun createPageEditor(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageKey: String,
    ): TranslationPageEditor = TranslationPageEditor(
        saveTranslation = { blockIndex, translation, onResult ->
            saveBlockTranslation(
                chapterName = chapterName,
                scanlator = scanlator,
                mangaTitle = mangaTitle,
                source = source,
                pageKey = pageKey,
                blockIndex = blockIndex,
                translation = translation,
                onResult = onResult,
            )
        },
        retranslate = { blockIndex, onResult ->
            retranslateBlock(
                chapterName = chapterName,
                scanlator = scanlator,
                mangaTitle = mangaTitle,
                source = source,
                pageKey = pageKey,
                blockIndex = blockIndex,
                onResult = onResult,
            )
        },
        addManualTranslation = { position, sourceText, translation, onResult ->
            addManualTranslation(
                chapterName = chapterName,
                scanlator = scanlator,
                mangaTitle = mangaTitle,
                source = source,
                pageKey = pageKey,
                position = position,
                sourceText = sourceText,
                translation = translation,
                onResult = onResult,
            )
        },
    )

    private fun addManualTranslation(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageKey: String,
        position: ManualTranslationPosition,
        sourceText: String,
        translation: String,
        onResult: (Result<AddedManualTranslation>) -> Unit,
    ) {
        editorScope.launch {
            val cleanSourceText = sourceText.trim()
            val cleanTranslation = translation.trim()
            val result = runCatching {
                require(cleanTranslation.isNotEmpty()) { "A tradução não pode ficar vazia." }
                val added = translationFileMutex.withLock {
                    val file = provider.findTranslationFile(chapterName, scanlator, mangaTitle, source)
                        ?: error("O arquivo de tradução deste capítulo não foi encontrado.")
                    val pages = readStoredPages(file).toMutableMap()
                    val page = pages.getOrPut(pageKey) {
                        PageTranslation(
                            imgWidth = position.pageWidth,
                            imgHeight = position.pageHeight,
                        )
                    }
                    if (page.imgWidth <= 0f) page.imgWidth = position.pageWidth
                    if (page.imgHeight <= 0f) page.imgHeight = position.pageHeight

                    val pageWidth = page.imgWidth.coerceAtLeast(1f)
                    val pageHeight = page.imgHeight.coerceAtLeast(1f)
                    val mappedX = position.x * pageWidth / position.pageWidth.coerceAtLeast(1f)
                    val mappedY = position.y * pageHeight / position.pageHeight.coerceAtLeast(1f)
                    val block = createManualBlock(
                        sourceText = cleanSourceText,
                        translation = cleanTranslation,
                        x = mappedX,
                        y = mappedY,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                    )
                    page.blocks.add(block)
                    page.blocks.sortWith(compareBy<TranslationBlock> { it.y }.thenBy { it.x })
                    val blockIndex = page.blocks.indexOfFirst { it === block }
                    check(blockIndex >= 0) { "Não foi possível adicionar o balão manual." }
                    writeStoredPages(file, pages)
                    AddedManualTranslation(
                        blockIndex = blockIndex,
                        block = block,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                    )
                }
                if (cleanSourceText.isNotEmpty()) {
                    rememberEditedTranslation(
                        mangaTitle = mangaTitle,
                        chapterName = chapterName,
                        sourceText = cleanSourceText,
                        translation = cleanTranslation,
                    )
                }
                added
            }
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    private fun createManualBlock(
        sourceText: String,
        translation: String,
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float,
    ): TranslationBlock {
        val explicitLines = sourceText.lines().count(String::isNotBlank).coerceAtLeast(1)
        val estimatedLines = max(
            explicitLines,
            ceil(sourceText.filterNot(Char::isWhitespace).length / MANUAL_CHARACTERS_PER_LINE).toInt(),
        ).coerceIn(1, MAX_MANUAL_TEXT_LINES)
        val longestLineLength = sourceText.lines()
            .maxOfOrNull { it.trim().length }
            ?.takeIf { it > 0 }
        val widthRatio = longestLineLength
            ?.let { it * MANUAL_CHARACTER_WIDTH_RATIO }
            ?.coerceIn(MIN_MANUAL_WIDTH_RATIO, MAX_MANUAL_WIDTH_RATIO)
            ?: DEFAULT_MANUAL_WIDTH_RATIO
        val sourceWidth = (pageWidth * widthRatio).coerceAtLeast(1f)
        val sourceHeight = max(
            pageHeight * MANUAL_LINE_HEIGHT_RATIO * estimatedLines,
            pageWidth * MIN_MANUAL_HEIGHT_TO_WIDTH_RATIO,
        ).coerceIn(1f, pageHeight * MAX_MANUAL_HEIGHT_RATIO)
        val left = (x - sourceWidth / 2f).coerceIn(0f, (pageWidth - sourceWidth).coerceAtLeast(0f))
        val top = (y - sourceHeight / 2f).coerceIn(0f, (pageHeight - sourceHeight).coerceAtLeast(0f))
        val symbolHeight = (sourceHeight / estimatedLines * MANUAL_SYMBOL_HEIGHT_RATIO).coerceAtLeast(1f)

        val block = TranslationBlock(
            text = sourceText,
            translation = translation,
            width = sourceWidth,
            height = sourceHeight,
            x = left,
            y = top,
            symHeight = symbolHeight,
            symWidth = (symbolHeight * MANUAL_SYMBOL_WIDTH_RATIO).coerceAtLeast(1f),
            angle = 0f,
        )
        // Do not reopen/decode the page here. Archive readers may still own the
        // stream while the chapter is visible, and a second decode during save
        // can terminate the reader before the JSON is written. Manual blocks use
        // conservative geometry and can be refined later without risking data.
        block.cleanupRegion = block.defaultCleanupRegion(pageWidth, pageHeight)
        block.layoutRegion = block.defaultLayoutRegion(pageWidth, pageHeight)
        block.geometryVersion = CURRENT_TRANSLATION_GEOMETRY_VERSION
        return block
    }

    private fun saveBlockTranslation(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageKey: String,
        blockIndex: Int,
        translation: String,
        onResult: (Result<String>) -> Unit,
    ) {
        editorScope.launch {
            val result = runCatching {
                val cleanTranslation = translation.trim()
                require(cleanTranslation.isNotEmpty()) { "A tradução não pode ficar vazia." }
                val sourceText = translationFileMutex.withLock {
                    updateStoredBlock(
                        chapterName = chapterName,
                        scanlator = scanlator,
                        mangaTitle = mangaTitle,
                        source = source,
                        pageKey = pageKey,
                        blockIndex = blockIndex,
                    ) { block ->
                        block.translation = cleanTranslation
                        block.text
                    }
                }
                rememberEditedTranslation(
                    mangaTitle = mangaTitle,
                    chapterName = chapterName,
                    sourceText = sourceText,
                    translation = cleanTranslation,
                )
                cleanTranslation
            }
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    private fun retranslateBlock(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageKey: String,
        blockIndex: Int,
        onResult: (Result<String>) -> Unit,
    ) {
        editorScope.launch {
            val result = runCatching {
                val sourceBlock = translationFileMutex.withLock {
                    readStoredBlock(
                        chapterName = chapterName,
                        scanlator = scanlator,
                        mangaTitle = mangaTitle,
                        source = source,
                        pageKey = pageKey,
                        blockIndex = blockIndex,
                    ).copy(translation = "")
                }
                val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
                val toLang = TextTranslatorLanguage.fromPref(translationPreferences.translateToLanguage())
                val comicContext = ComicTranslationContext(mangaTitle, chapterName)
                // Retranslate means the user wants a fresh engine result. Remove
                // only the learned complete-line correction; explicit glossary
                // names/titles/techniques remain authoritative.
                TranslationMemory.forgetCorrection(comicContext, sourceBlock.text)
                TranslationCache.remove(comicContext, fromLang, toLang, sourceBlock.text)

                val singlePage = PageTranslation(blocks = mutableListOf(sourceBlock))
                val pages = mutableMapOf(pageKey to singlePage)
                val textTranslator = TextTranslators.fromPref(translationPreferences.translationEngine())
                    .build(translationPreferences, fromLang, toLang)
                try {
                    textTranslator.translate(pages, comicContext)
                } finally {
                    textTranslator.close()
                }

                val translatedText = singlePage.blocks.firstOrNull()?.translation?.trim().orEmpty()
                check(translatedText.isNotEmpty()) { "O tradutor não retornou texto para este balão." }
                translationFileMutex.withLock {
                    updateStoredBlock(
                        chapterName = chapterName,
                        scanlator = scanlator,
                        mangaTitle = mangaTitle,
                        source = source,
                        pageKey = pageKey,
                        blockIndex = blockIndex,
                    ) { block ->
                        block.translation = translatedText
                    }
                }
                translatedText
            }
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    private fun rememberEditedTranslation(
        mangaTitle: String,
        chapterName: String,
        sourceText: String,
        translation: String,
    ) {
        val fromLang = TextRecognizerLanguage.fromPref(translationPreferences.translateFromLanguage())
        val toLang = TextTranslatorLanguage.fromPref(translationPreferences.translateToLanguage())
        val comicContext = ComicTranslationContext(mangaTitle, chapterName)
        TranslationMemory.rememberCorrection(
            context = comicContext,
            source = sourceText,
            target = translation,
        )
        TranslationCache.put(
            context = comicContext,
            fromLang = fromLang,
            toLang = toLang,
            source = sourceText,
            target = translation,
        )
    }

    private fun readStoredBlock(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageKey: String,
        blockIndex: Int,
    ): TranslationBlock {
        val file = provider.findTranslationFile(chapterName, scanlator, mangaTitle, source)
            ?: error("O arquivo de tradução deste capítulo não foi encontrado.")
        val pages = readStoredPages(file)
        val page = pages[pageKey] ?: error("A página traduzida não foi encontrada.")
        return page.blocks.getOrNull(blockIndex) ?: error("O balão traduzido não foi encontrado.")
    }

    private fun <T> updateStoredBlock(
        chapterName: String,
        scanlator: String?,
        mangaTitle: String,
        source: Source,
        pageKey: String,
        blockIndex: Int,
        update: (TranslationBlock) -> T,
    ): T {
        val file = provider.findTranslationFile(chapterName, scanlator, mangaTitle, source)
            ?: error("O arquivo de tradução deste capítulo não foi encontrado.")
        val pages = readStoredPages(file).toMutableMap()
        val page = pages[pageKey] ?: error("A página traduzida não foi encontrada.")
        val block = page.blocks.getOrNull(blockIndex) ?: error("O balão traduzido não foi encontrado.")
        val result = update(block)
        writeStoredPages(file, pages)
        return result
    }

    /**
     * Some document providers do not truncate an existing file when opened with
     * their default write mode. A shorter edited JSON then leaves old closing
     * bytes behind, which only becomes visible on the next read/retranslation.
     *
     * Strict decoding remains the default. The prefix fallback repairs files
     * already affected by that bug by accepting one complete top-level JSON
     * value and ignoring only trailing bytes.
     */
    private fun readStoredPages(file: UniFile): Map<String, PageTranslation> {
        val raw = file.openInputStream()
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return runCatching {
            Json.decodeFromString<Map<String, PageTranslation>>(raw)
        }.recoverCatching { originalError ->
            val recovered = firstCompleteJsonValue(raw) ?: throw originalError
            Json.decodeFromString<Map<String, PageTranslation>>(recovered)
        }.getOrThrow()
    }

    /**
     * Serialize fully before touching the stored chapter, then request explicit
     * truncation so a shorter edit cannot inherit bytes from the previous JSON.
     */
    private fun writeStoredPages(file: UniFile, pages: Map<String, PageTranslation>) {
        val payload = Json.encodeToString(pages).encodeToByteArray()
        val output = context.contentResolver.openOutputStream(file.uri, "wt")
            ?: error("Não foi possível abrir o arquivo de tradução para gravação.")
        output.use {
            it.write(payload)
            it.flush()
        }
    }

    private fun firstCompleteJsonValue(raw: String): String? {
        val start = raw.indexOfFirst { !it.isWhitespace() }
        if (start < 0 || (raw[start] != '{' && raw[start] != '[')) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val character = raw[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }

            when (character) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                    if (depth < 0) return null
                }
            }
        }
        return null
    }

    fun deleteTranslation(chapter: Chapter, manga: Manga, source: Source) {
        launchIO {
            removeFromTranslationQueue(chapter)
            val file = provider.findTranslationFile(chapter.name, chapter.scanlator, manga.title, source)
            file?.delete()
        }
    }

    fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                translator.removeFromQueue(manga)
            }
            provider.findMangaDir(manga.title, source)?.delete()
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
            }
        }
    }

    fun cancelQueuedTranslation(translation: Translation) {
        removeFromTranslationQueue(translation.chapter)
    }

    private fun removeFromTranslationQueue(chapter: Chapter) {
        val wasRunning = translator.isRunning
        if (wasRunning) {
            translator.pause()
        }
        translator.removeFromQueue(chapter)
        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                translator.stop()
            } else if (queueState.value.isNotEmpty()) {
                translator.start()
            }
        }
    }

    fun statusFlow(): Flow<Translation> = queueState
        .flatMapLatest { translations ->
            translations
                .map { translation ->
                    translation.statusFlow.drop(1).map { translation }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { translation -> translation.status == Translation.State.TRANSLATING }.asFlow(),
            )
        }

    private companion object {
        const val MANUAL_CHARACTERS_PER_LINE = 24f
        const val MAX_MANUAL_TEXT_LINES = 4
        const val MANUAL_CHARACTER_WIDTH_RATIO = 0.013f
        const val MIN_MANUAL_WIDTH_RATIO = 0.22f
        const val DEFAULT_MANUAL_WIDTH_RATIO = 0.38f
        const val MAX_MANUAL_WIDTH_RATIO = 0.55f
        const val MANUAL_LINE_HEIGHT_RATIO = 0.021f
        const val MIN_MANUAL_HEIGHT_TO_WIDTH_RATIO = 0.055f
        const val MAX_MANUAL_HEIGHT_RATIO = 0.12f
        const val MANUAL_SYMBOL_HEIGHT_RATIO = 0.62f
        const val MANUAL_SYMBOL_WIDTH_RATIO = 0.55f
    }
}
