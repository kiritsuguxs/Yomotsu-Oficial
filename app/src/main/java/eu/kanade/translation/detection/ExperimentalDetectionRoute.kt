package eu.kanade.translation.detection

import kotlinx.coroutines.CancellationException

/** A valid blank/unreadable page uses original OCR without disabling later detection. */
class DbnetEmptyPageException : Exception()

/** Keeps the old route lazy and unchanged unless the experimental switch is enabled. */
object ExperimentalDetectionRoute {
    suspend fun <T> execute(
        enabled: Boolean,
        supported: Boolean,
        experimental: suspend () -> T,
        existing: suspend () -> T,
        onFallback: (String) -> Unit,
    ): T {
        if (!enabled) return existing()
        if (!supported) {
            notifyFallback(onFallback, "DBNet exige Android ARM64 e texto em inglês; usando OCR selecionado.")
            return existing()
        }
        try {
            return experimental()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: DbnetEmptyPageException) {
            // Empty is a valid page result, not a broken model/runtime/session.
        } catch (error: Exception) {
            val detail = error.message?.take(160) ?: error.javaClass.simpleName
            notifyFallback(
                onFallback,
                "DBNet indisponível ($detail); usando OCR selecionado.",
            )
        } catch (error: LinkageError) {
            notifyFallback(onFallback, "Biblioteca DBNet indisponível; usando OCR selecionado.")
        } catch (error: OutOfMemoryError) {
            notifyFallback(onFallback, "Memória insuficiente para DBNet; usando OCR selecionado.")
        }
        // Outside the try: a failure of the original engine remains its own failure, never retried.
        return existing()
    }

    private fun notifyFallback(callback: (String) -> Unit, reason: String) {
        try {
            callback(reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Experimental cleanup/notification must never prevent the original OCR call.
        } catch (_: LinkageError) {
        } catch (_: OutOfMemoryError) {
        }
    }
}
