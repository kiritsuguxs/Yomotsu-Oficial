package eu.kanade.translation.translator

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

class TranslationProviderFailureTest {

    @Test
    fun `OpenRouter 401 is an invalid key without leaking response body`() {
        val error = TranslationProviderFailureMapper.fromHttp(
            provider = TranslationProviderId.OPENROUTER,
            statusCode = 401,
            responseBody = """{"error":{"message":"bad secret sk-test"}}""",
        )

        assertEquals(TranslationFailureCategory.INVALID_KEY, error.category)
        assertTrue(error.message.orEmpty().contains("OpenRouter"))
        assertFalse(error.message.orEmpty().contains("sk-test"))
    }

    @Test
    fun `OpenRouter 402 reports insufficient credits`() {
        val error = TranslationProviderFailureMapper.fromHttp(
            TranslationProviderId.OPENROUTER,
            402,
            "insufficient credits",
        )

        assertEquals(TranslationFailureCategory.INSUFFICIENT_CREDITS, error.category)
        assertFalse(error.retryable)
    }

    @Test
    fun `HTTP 429 reports rate limit without immediate retry`() {
        val error = TranslationProviderFailureMapper.fromHttp(
            TranslationProviderId.DEEPL,
            429,
            "too many requests",
        )

        assertEquals(TranslationFailureCategory.RATE_LIMIT, error.category)
        assertFalse(error.retryable)
        assertTrue(error.message.orEmpty().contains("429"))
    }

    @Test
    fun `DeepL 456 reports exhausted quota`() {
        val error = TranslationProviderFailureMapper.fromHttp(
            TranslationProviderId.DEEPL,
            456,
            "quota exceeded",
        )

        assertEquals(TranslationFailureCategory.INSUFFICIENT_CREDITS, error.category)
    }

    @Test
    fun `OpenRouter 404 reports missing model`() {
        val error = TranslationProviderFailureMapper.fromHttp(
            TranslationProviderId.OPENROUTER,
            404,
            "No endpoints found for model",
        )

        assertEquals(TranslationFailureCategory.MODEL_NOT_FOUND, error.category)
    }

    @Test
    fun `network IO reports retryable connection failure`() {
        val error = TranslationProviderFailureMapper.fromThrowable(
            TranslationProviderId.GEMINI,
            UnknownHostException("offline"),
        )

        assertEquals(TranslationFailureCategory.CONNECTION, error.category)
        assertTrue(error.retryable)
    }

    @Test
    fun `Gemini invalid key message is classified without leaking cause text`() {
        val error = TranslationProviderFailureMapper.fromThrowable(
            TranslationProviderId.GEMINI,
            IllegalStateException("API_KEY_INVALID secret-value"),
        )

        assertEquals(TranslationFailureCategory.INVALID_KEY, error.category)
        assertFalse(error.message.orEmpty().contains("secret-value"))
    }

    @Test
    fun `Gemini exhausted resource is classified as quota failure`() {
        val error = TranslationProviderFailureMapper.fromThrowable(
            TranslationProviderId.GEMINI,
            IllegalStateException("RESOURCE_EXHAUSTED: quota exceeded"),
        )

        assertEquals(TranslationFailureCategory.INSUFFICIENT_CREDITS, error.category)
        assertFalse(error.retryable)
    }

    @Test
    fun `Gemini missing model message is classified`() {
        val error = TranslationProviderFailureMapper.fromThrowable(
            TranslationProviderId.GEMINI,
            IllegalStateException("model gemini-old was not found"),
        )

        assertEquals(TranslationFailureCategory.MODEL_NOT_FOUND, error.category)
    }

    @Test
    fun `server response is retryable`() {
        val error = TranslationProviderFailureMapper.fromHttp(
            TranslationProviderId.OPENROUTER,
            503,
            "temporary upstream failure",
        )

        assertEquals(TranslationFailureCategory.SERVER, error.category)
        assertTrue(error.retryable)
    }

    @Test
    fun `JSON parsing failure is a non retryable malformed response`() {
        val error = TranslationProviderFailureMapper.fromThrowable(
            TranslationProviderId.DEEPL,
            JSONException("missing translations"),
        )

        assertEquals(TranslationFailureCategory.MALFORMED_RESPONSE, error.category)
        assertFalse(error.retryable)
    }

    @Test
    fun `cancellation is propagated unchanged`() {
        val cancellation = CancellationException("paused")

        val thrown = assertThrows(CancellationException::class.java) {
            TranslationProviderFailureMapper.fromThrowable(
                TranslationProviderId.GEMINI,
                cancellation,
            )
        }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun `missing key and model use typed configuration failures`() {
        assertEquals(
            TranslationFailureCategory.INVALID_KEY,
            TranslationProviderFailureMapper.missingKey(TranslationProviderId.GEMINI).category,
        )
        assertEquals(
            TranslationFailureCategory.MODEL_NOT_FOUND,
            TranslationProviderFailureMapper.missingModel(TranslationProviderId.OPENROUTER).category,
        )
    }

    @Test
    fun `DeepL rejects blank key through typed boundary`() = runTest {
        val translator = DeepLTranslator(
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
            apiKey = "",
        )

        val error = captureFailure { translator.translate(mutableMapOf()) }

        assertEquals(TranslationProviderId.DEEPL, error.provider)
        assertEquals(TranslationFailureCategory.INVALID_KEY, error.category)
    }

    @Test
    fun `OpenRouter rejects blank key through typed boundary`() = runTest {
        val translator = OpenRouterTranslator(
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
            apiKey = "",
            modelName = "openai/gpt-4o-mini",
            maxOutputToken = 128,
            temp = 0.3f,
        )

        val error = captureFailure { translator.translate(mutableMapOf()) }

        assertEquals(TranslationProviderId.OPENROUTER, error.provider)
        assertEquals(TranslationFailureCategory.INVALID_KEY, error.category)
    }

    @Test
    fun `OpenRouter rejects blank model through typed boundary`() = runTest {
        val translator = OpenRouterTranslator(
            TextRecognizerLanguage.ENGLISH,
            TextTranslatorLanguage.PORTUGUESE,
            apiKey = "configured",
            modelName = " ",
            maxOutputToken = 128,
            temp = 0.3f,
        )

        val error = captureFailure { translator.translate(mutableMapOf()) }

        assertEquals(TranslationFailureCategory.MODEL_NOT_FOUND, error.category)
    }

    @Test
    fun `Gemini rejects blank key before SDK use`() = runTest {
        val error = captureFailure {
            GeminiTranslator(
                TextRecognizerLanguage.ENGLISH,
                TextTranslatorLanguage.PORTUGUESE,
                apiKey = "",
                modelName = "gemini-1.5-pro",
                maxOutputToken = 128,
                temp = 0.3f,
            ).translate(mutableMapOf())
        }

        assertEquals(TranslationProviderId.GEMINI, error.provider)
        assertEquals(TranslationFailureCategory.INVALID_KEY, error.category)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): TranslationProviderException {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(error is TranslationProviderException)
        return error as TranslationProviderException
    }
}
