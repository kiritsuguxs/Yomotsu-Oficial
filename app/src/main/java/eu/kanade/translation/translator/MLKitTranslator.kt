package eu.kanade.translation.translator

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.memory.TranslationGlossary
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage

class MLKitTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
) : TextTranslator {

    private var translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(fromLang.code)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(toLang.code) ?: TranslateLanguage.ENGLISH)
            .build(),
    )

    private var conditions = DownloadConditions.Builder().build()

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        val sequentialBlocks = pages.values.flatMap { it.blocks }
        if (sequentialBlocks.isEmpty()) return

        val normalizedTexts = sequentialBlocks.map { normalizeOcrText(it.text) }
        val preparedTexts = normalizedTexts.map { TranslationGlossary.prepare(it, context) }
        val cached = normalizedTexts.map { TranslationCache.get(context, fromLang, toLang, it) }

        // Avoid even loading/downloading the ML Kit model when the complete set
        // is already known. For partial misses we keep the original full batch
        // so neighboring dialogue continues to provide translation context.
        val translations = if (cached.all { it != null }) {
            cached.map { it.orEmpty() }
        } else {
            Tasks.await(translator.downloadModelIfNeeded(conditions))
            translateBlocksWithContext(
                texts = preparedTexts.map { it.textForTranslation },
                sourceTextsForPostEdit = normalizedTexts,
            )
        }

        sequentialBlocks.forEachIndexed { index, block ->
            val finalTranslation = cached[index] ?: TranslationGlossary.resolve(
                prepared = preparedTexts[index],
                translatedText = translations[index],
                context = context,
            )
            block.translation = TranslationGlossary.apply(
                sourceText = block.text,
                translatedText = finalTranslation,
                context = context,
            )
            if (cached[index] == null) {
                TranslationCache.put(
                    context = context,
                    fromLang = fromLang,
                    toLang = toLang,
                    source = normalizedTexts[index],
                    target = block.translation,
                )
            }
        }
    }

    private fun translateBlocksWithContext(
        texts: List<String>,
        sourceTextsForPostEdit: List<String> = texts,
    ): List<String> {
        val normalizedTexts = texts.map(::normalizeOcrText)
        val translations = MutableList(normalizedTexts.size) { "" }
        val chunks = buildMachineTranslationChunks(
            texts = normalizedTexts,
            maxCharacters = MLKIT_CONTEXT_MAX_CHARACTERS,
            maxItems = MLKIT_CONTEXT_MAX_ITEMS,
        )

        chunks.forEach { chunk ->
            val contextualResult = runCatching {
                Tasks.await(translator.translate(buildMarkedMachineTranslationText(chunk)))
            }.getOrNull()
            val parsedTranslations = contextualResult?.let {
                parseMarkedMachineTranslations(it, chunk)
            }

            if (parsedTranslations != null) {
                parsedTranslations.forEach { (index, value) -> translations[index] = value }
            } else {
                chunk.forEach { item ->
                    translations[item.index] = Tasks.await(translator.translate(item.value))
                }
            }
        }

        findSuspiciousDuplicateTranslationIndices(normalizedTexts, translations).forEach { index ->
            val individualTranslation = runCatching {
                Tasks.await(translator.translate(normalizedTexts[index]))
            }.getOrNull()
            if (!individualTranslation.isNullOrBlank()) {
                translations[index] = individualTranslation
            }
        }

        return translations.mapIndexed { index, translated ->
            val finalTranslation = translated.takeIf { it.isNotBlank() } ?: normalizedTexts[index]
            postEditMachineTranslation(
                sourceText = sourceTextsForPostEdit[index],
                translatedText = finalTranslation,
                targetLanguage = toLang,
            )
        }
    }

    override fun close() {
        translator.close()
    }

    private companion object {
        const val MLKIT_CONTEXT_MAX_CHARACTERS = 3_000
        const val MLKIT_CONTEXT_MAX_ITEMS = 16
    }
}
