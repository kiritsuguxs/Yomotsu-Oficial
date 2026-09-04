package eu.kanade.translation.translator

import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.io.IOException

enum class TranslationProviderId(val label: String) {
    GEMINI("Gemini"),
    DEEPL("DeepL"),
    OPENROUTER("OpenRouter"),
}

enum class TranslationFailureCategory {
    INVALID_KEY,
    RATE_LIMIT,
    MODEL_NOT_FOUND,
    INSUFFICIENT_CREDITS,
    CONNECTION,
    SERVER,
    MALFORMED_RESPONSE,
    UNKNOWN,
}

class TranslationProviderException(
    val provider: TranslationProviderId,
    val category: TranslationFailureCategory,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object TranslationProviderFailureMapper {

    fun fromHttp(
        provider: TranslationProviderId,
        statusCode: Int,
        responseBody: String,
    ): TranslationProviderException {
        val body = responseBody.lowercase()
        val category = when {
            statusCode == 429 -> TranslationFailureCategory.RATE_LIMIT
            statusCode == 402 || provider == TranslationProviderId.DEEPL && statusCode == 456 ->
                TranslationFailureCategory.INSUFFICIENT_CREDITS
            statusCode == 401 || statusCode == 403 -> TranslationFailureCategory.INVALID_KEY
            statusCode == 404 && provider != TranslationProviderId.DEEPL ->
                TranslationFailureCategory.MODEL_NOT_FOUND
            statusCode >= 500 -> TranslationFailureCategory.SERVER
            body.indicatesInvalidKey() -> TranslationFailureCategory.INVALID_KEY
            body.indicatesRateLimit() -> TranslationFailureCategory.RATE_LIMIT
            body.indicatesCreditsOrQuota() -> TranslationFailureCategory.INSUFFICIENT_CREDITS
            body.indicatesMissingModel() -> TranslationFailureCategory.MODEL_NOT_FOUND
            else -> TranslationFailureCategory.SERVER
        }
        return failure(
            provider = provider,
            category = category,
            retryable = category == TranslationFailureCategory.SERVER && statusCode >= 500,
            cause = null,
        )
    }

    fun fromThrowable(
        provider: TranslationProviderId,
        throwable: Throwable,
    ): TranslationProviderException {
        if (throwable is CancellationException) throw throwable
        if (throwable is TranslationProviderException) return throwable

        val causes = generateSequence(throwable) { it.cause }.toList()
        val message = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
        val category = when {
            causes.any { it is JSONException } -> TranslationFailureCategory.MALFORMED_RESPONSE
            causes.any { it is IOException } -> TranslationFailureCategory.CONNECTION
            message.indicatesInvalidKey() -> TranslationFailureCategory.INVALID_KEY
            message.indicatesRateLimit() -> TranslationFailureCategory.RATE_LIMIT
            message.indicatesCreditsOrQuota() -> TranslationFailureCategory.INSUFFICIENT_CREDITS
            message.indicatesMissingModel() -> TranslationFailureCategory.MODEL_NOT_FOUND
            message.contains("timeout") || message.contains("timed out") -> TranslationFailureCategory.CONNECTION
            message.contains("internal server") || message.contains("service unavailable") ->
                TranslationFailureCategory.SERVER
            else -> TranslationFailureCategory.UNKNOWN
        }
        return failure(
            provider = provider,
            category = category,
            retryable = category == TranslationFailureCategory.CONNECTION ||
                category == TranslationFailureCategory.SERVER,
            cause = throwable,
        )
    }

    fun missingKey(provider: TranslationProviderId): TranslationProviderException = failure(
        provider = provider,
        category = TranslationFailureCategory.INVALID_KEY,
        retryable = false,
        cause = null,
    )

    fun missingModel(provider: TranslationProviderId): TranslationProviderException = failure(
        provider = provider,
        category = TranslationFailureCategory.MODEL_NOT_FOUND,
        retryable = false,
        cause = null,
    )

    fun malformedResponse(
        provider: TranslationProviderId,
        cause: Throwable,
    ): TranslationProviderException = failure(
        provider = provider,
        category = TranslationFailureCategory.MALFORMED_RESPONSE,
        retryable = false,
        cause = cause,
    )

    private fun failure(
        provider: TranslationProviderId,
        category: TranslationFailureCategory,
        retryable: Boolean,
        cause: Throwable?,
    ) = TranslationProviderException(
        provider = provider,
        category = category,
        retryable = retryable,
        message = userMessage(provider, category),
        cause = cause,
    )

    private fun userMessage(
        provider: TranslationProviderId,
        category: TranslationFailureCategory,
    ): String = when (category) {
        TranslationFailureCategory.INVALID_KEY ->
            "${provider.label}: chave da API ausente, inválida ou não autorizada."
        TranslationFailureCategory.RATE_LIMIT ->
            "${provider.label}: limite de requisições atingido (HTTP 429). Tente novamente mais tarde."
        TranslationFailureCategory.MODEL_NOT_FOUND ->
            "${provider.label}: modelo não encontrado ou indisponível. Verifique o modelo configurado."
        TranslationFailureCategory.INSUFFICIENT_CREDITS ->
            "${provider.label}: créditos ou cota insuficientes para concluir a tradução."
        TranslationFailureCategory.CONNECTION ->
            "${provider.label}: falha de conexão. Verifique sua internet e tente novamente."
        TranslationFailureCategory.SERVER ->
            "${provider.label}: o serviço recusou ou não conseguiu processar a tradução. Tente novamente mais tarde."
        TranslationFailureCategory.MALFORMED_RESPONSE ->
            "${provider.label}: resposta inválida do serviço de tradução."
        TranslationFailureCategory.UNKNOWN ->
            "${provider.label}: não foi possível concluir a tradução."
    }

    private fun String.indicatesInvalidKey(): Boolean =
        contains("api_key_invalid") || contains("invalid api key") || contains("invalid key") ||
            contains("unauthorized") || contains("authentication failed")

    private fun String.indicatesRateLimit(): Boolean =
        contains("rate limit") || contains("too many requests")

    private fun String.indicatesCreditsOrQuota(): Boolean =
        contains("resource_exhausted") || contains("insufficient credit") ||
            contains("quota exceeded") || contains("quota exhausted") ||
            contains("billing limit") || contains("payment required")

    private fun String.indicatesMissingModel(): Boolean =
        (contains("model") && (contains("not found") || contains("unavailable"))) ||
            contains("no endpoints found")
}
