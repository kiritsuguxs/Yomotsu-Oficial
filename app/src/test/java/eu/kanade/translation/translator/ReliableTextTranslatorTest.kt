package eu.kanade.translation.translator

import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReliableTextTranslatorTest {

    @Test
    fun `no fallback propagates permanent failure without clearing valid blocks`() = runTest {
        val first = block("First source line")
        val second = block("Second source line")
        val primaryError = providerFailure(TranslationFailureCategory.INVALID_KEY, retryable = false)
        val primary = RecordingTranslator { pages ->
            pages.blocks()[0].translation = "Primeira linha."
            throw primaryError
        }
        var retryFactories = 0
        val reliable = ReliableTextTranslator(
            delegate = primary,
            retryFactory = {
                retryFactories++
                RecordingTranslator()
            },
            fallbackFactory = null,
        )

        val thrown = captureFailure { reliable.translate(pages(first, second)) }

        assertSame(primaryError, thrown)
        assertEquals("Primeira linha.", first.translation)
        assertEquals("", second.translation)
        assertEquals(0, retryFactories)
    }

    @Test
    fun `configured fallback receives only unresolved blocks after permanent failure`() = runTest {
        val first = block("First source line")
        val second = block("Second source line")
        val primary = RecordingTranslator { pages ->
            pages.blocks()[0].translation = "Primeira linha."
            throw providerFailure(TranslationFailureCategory.INVALID_KEY, retryable = false)
        }
        val fallback = RecordingTranslator { pages ->
            pages.blocks().single().translation = "Segunda linha."
        }
        val reliable = ReliableTextTranslator(
            delegate = primary,
            retryFactory = { error("Permanent failure must not retry") },
            fallbackFactory = { fallback },
        )

        reliable.translate(pages(first, second))

        assertEquals(listOf(listOf("Second source line")), fallback.receivedSources)
        assertEquals("Primeira linha.", first.translation)
        assertEquals("Segunda linha.", second.translation)
        assertTrue(fallback.closed)
    }

    @Test
    fun `transient failure retries once before fallback using unresolved blocks`() = runTest {
        val first = block("First source line")
        val second = block("Second source line")
        val events = mutableListOf<String>()
        val primary = RecordingTranslator { pages ->
            events += "primary"
            pages.blocks()[0].translation = "Primeira linha."
            throw providerFailure(TranslationFailureCategory.CONNECTION, retryable = true)
        }
        val retry = RecordingTranslator { _ ->
            events += "retry"
            throw providerFailure(TranslationFailureCategory.CONNECTION, retryable = true)
        }
        val fallback = RecordingTranslator { pages ->
            events += "fallback"
            pages.blocks().single().translation = "Segunda linha."
        }
        val reliable = ReliableTextTranslator(primary, { retry }, { fallback })

        reliable.translate(pages(first, second))

        assertEquals(listOf("primary", "retry", "fallback"), events)
        assertEquals(listOf(listOf("Second source line")), retry.receivedSources)
        assertEquals(listOf(listOf("Second source line")), fallback.receivedSources)
        assertEquals("Primeira linha.", first.translation)
        assertEquals("Segunda linha.", second.translation)
        assertTrue(retry.closed)
        assertTrue(fallback.closed)
    }

    @Test
    fun `successful transient retry prevents fallback`() = runTest {
        val source = block("Only source line")
        val primary = RecordingTranslator {
            throw providerFailure(TranslationFailureCategory.SERVER, retryable = true)
        }
        val retry = RecordingTranslator { pages ->
            pages.blocks().single().translation = "Única linha."
        }
        var fallbackFactories = 0
        val reliable = ReliableTextTranslator(
            delegate = primary,
            retryFactory = { retry },
            fallbackFactory = {
                fallbackFactories++
                RecordingTranslator()
            },
        )

        reliable.translate(pages(source))

        assertEquals("Única linha.", source.translation)
        assertEquals(0, fallbackFactories)
        assertTrue(retry.closed)
    }

    @Test
    fun `fallback failure preserves progress and never exposes raw fallback error`() = runTest {
        val first = block("First source line")
        val second = block("Second source line")
        val primary = RecordingTranslator { pages ->
            pages.blocks()[0].translation = "Primeira linha."
            throw providerFailure(TranslationFailureCategory.INVALID_KEY, retryable = false)
        }
        val fallback = RecordingTranslator {
            throw IllegalStateException("fallback raw secret sk-fallback")
        }
        val reliable = ReliableTextTranslator(primary, { RecordingTranslator() }, { fallback })

        val thrown = captureFailure { reliable.translate(pages(first, second)) }

        assertTrue(thrown is TranslationFallbackException)
        assertTrue(thrown.message.orEmpty().contains("reserva"))
        assertFalse(thrown.message.orEmpty().contains("sk-fallback"))
        assertEquals("Primeira linha.", first.translation)
        assertEquals("", second.translation)
    }

    @Test
    fun `cancellation never retries or starts fallback`() = runTest {
        val cancellation = CancellationException("paused")
        var retryFactories = 0
        var fallbackFactories = 0
        val reliable = ReliableTextTranslator(
            delegate = RecordingTranslator { throw cancellation },
            retryFactory = {
                retryFactories++
                RecordingTranslator()
            },
            fallbackFactory = {
                fallbackFactories++
                RecordingTranslator()
            },
        )

        val thrown = captureFailure { reliable.translate(pages(block("Source line"))) }

        assertSame(cancellation, thrown)
        assertEquals(0, retryFactories)
        assertEquals(0, fallbackFactories)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable =
        runCatching { block() }.exceptionOrNull() ?: error("Expected translation to fail")

    private fun providerFailure(
        category: TranslationFailureCategory,
        retryable: Boolean,
    ) = TranslationProviderException(
        provider = TranslationProviderId.OPENROUTER,
        category = category,
        retryable = retryable,
        message = "OpenRouter: falha segura.",
    )

    private fun pages(vararg blocks: TranslationBlock): MutableMap<String, PageTranslation> =
        mutableMapOf(
            "001.jpg" to PageTranslation(
                blocks = blocks.toMutableList(),
                imgWidth = 1200f,
                imgHeight = 1800f,
            ),
        )

    private fun MutableMap<String, PageTranslation>.blocks(): List<TranslationBlock> =
        values.flatMap(PageTranslation::blocks)

    private fun block(text: String) = TranslationBlock(
        text = text,
        width = 220f,
        height = 80f,
        x = 20f,
        y = 40f,
        symHeight = 20f,
        symWidth = 12f,
        angle = 0f,
    )

    private class RecordingTranslator(
        private val behavior: suspend (MutableMap<String, PageTranslation>) -> Unit = {},
    ) : TextTranslator {
        override val fromLang = TextRecognizerLanguage.ENGLISH
        override val toLang = TextTranslatorLanguage.PORTUGUESE
        val receivedSources = mutableListOf<List<String>>()
        var closed = false

        override suspend fun translate(
            pages: MutableMap<String, PageTranslation>,
            context: ComicTranslationContext,
        ) {
            receivedSources += pages.values.flatMap(PageTranslation::blocks).map(TranslationBlock::text)
            behavior(pages)
        }

        override fun close() {
            closed = true
        }
    }
}
