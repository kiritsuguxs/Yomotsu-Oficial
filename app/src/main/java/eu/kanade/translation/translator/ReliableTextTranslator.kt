package eu.kanade.translation.translator

import eu.kanade.translation.memory.TranslationCache
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.normalizeTranslationText
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException

class TranslationFallbackException(
    val primaryFailure: Throwable,
    val fallbackFailure: Throwable,
) : Exception(
    buildString {
        append(
            (primaryFailure as? TranslationProviderException)?.message
                ?: "O tradutor principal falhou.",
        )
        append(" O tradutor de reserva também falhou.")
    },
    fallbackFailure,
) {
    init {
        if (primaryFailure !== fallbackFailure) addSuppressed(primaryFailure)
    }
}

/**
 * Preserves valid block results while retrying or falling back only for the
 * unresolved subset of a translation request.
 */
class ReliableTextTranslator(
    private val delegate: TextTranslator,
    private val retryFactory: () -> TextTranslator,
    private val fallbackFactory: (() -> TextTranslator)? = null,
) : TextTranslator {

    override val fromLang: TextRecognizerLanguage = delegate.fromLang
    override val toLang: TextTranslatorLanguage = delegate.toLang

    override suspend fun translate(
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ) {
        var primaryFailure: Throwable? = try {
            delegate.translate(pages, context)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error
        }
        normalizeTranslations(pages)

        var failed = collectFailedBlocks(pages)
        if (failed.isEmpty()) return

        val shouldRetryPrimary = primaryFailure == null ||
            (primaryFailure as? TranslationProviderException)?.retryable == true
        if (shouldRetryPrimary) {
            clearFailedBlocks(failed, context)
            val retryPages = pagesFor(failed, pages)
            val retryFailure = translateWithFactory(retryFactory, retryPages, context)
            normalizeTranslations(retryPages)
            failed = collectFailedBlocks(pages)
            if (failed.isEmpty()) return
            if (primaryFailure == null && retryFailure != null) {
                primaryFailure = retryFailure
            }
        }

        if (failed.isNotEmpty() && fallbackFactory != null) {
            clearFailedBlocks(failed, context)
            val fallbackPages = pagesFor(failed, pages)
            val fallbackFailure = translateWithFactory(fallbackFactory, fallbackPages, context)
            normalizeTranslations(fallbackPages)
            failed = collectFailedBlocks(pages)
            if (failed.isEmpty()) return
            if (fallbackFailure != null) {
                throw TranslationFallbackException(
                    primaryFailure = primaryFailure ?: fallbackFailure,
                    fallbackFailure = fallbackFailure,
                )
            }
        }

        clearFailedBlocks(failed, context)
        primaryFailure?.let { throw it }
    }

    private suspend fun translateWithFactory(
        factory: () -> TextTranslator,
        pages: MutableMap<String, PageTranslation>,
        context: ComicTranslationContext,
    ): Throwable? {
        var translator: TextTranslator? = null
        return try {
            translator = factory()
            translator.translate(pages, context)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error
        } finally {
            translator?.close()
        }
    }

    private fun normalizeTranslations(pages: Map<String, PageTranslation>) {
        pages.values.forEach { page ->
            page.blocks.forEach { block -> block.translation = normalizeTranslationText(block.translation) }
        }
    }

    private fun clearFailedBlocks(
        failed: List<Pair<String, TranslationBlock>>,
        context: ComicTranslationContext,
    ) {
        failed.forEach { (_, block) ->
            if (context.mangaTitle.isNotBlank()) {
                TranslationCache.remove(context, fromLang, toLang, block.text)
            }
            block.translation = ""
        }
    }

    private fun pagesFor(
        failed: List<Pair<String, TranslationBlock>>,
        originalPages: Map<String, PageTranslation>,
    ): MutableMap<String, PageTranslation> = mutableMapOf<String, PageTranslation>().apply {
        failed.groupBy({ it.first }, { it.second }).forEach { (pageKey, blocks) ->
            val originalPage = originalPages.getValue(pageKey)
            put(
                pageKey,
                PageTranslation(
                    blocks = blocks.toMutableList(),
                    imgWidth = originalPage.imgWidth,
                    imgHeight = originalPage.imgHeight,
                ),
            )
        }
    }

    private fun collectFailedBlocks(
        pages: Map<String, PageTranslation>,
    ): List<Pair<String, TranslationBlock>> = buildList {
        pages.forEach { (pageKey, page) ->
            page.blocks.forEach { block ->
                if (TranslationQualityGuard.shouldRetry(block.text, block.translation, fromLang, toLang)) {
                    add(pageKey to block)
                }
            }
        }
    }

    override fun close() {
        delegate.close()
    }
}
