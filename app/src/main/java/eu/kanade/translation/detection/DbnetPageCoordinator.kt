package eu.kanade.translation.detection

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class DbnetPageCoordinator(
    private val emitDiagnostic: (String) -> Unit = {},
    private val clock: DbnetMonotonicClock = DbnetSystemMonotonicClock,
) {
    suspend fun <T> execute(
        enabled: Boolean,
        supported: Boolean,
        experimental: suspend (DbnetPageTimingRecorder) -> T,
        existing: suspend () -> T,
        onFallback: (String) -> Unit,
        releaseAfterFallback: suspend () -> Unit,
    ): T {
        if (!enabled) return existing()
        var fallbackTriggered = false
        var fallbackIncluded = false
        var status = "success"
        val timings = DbnetPageTimingRecorder(clock)
        try {
            return try {
                ExperimentalDetectionRoute.execute(
                    enabled = true,
                    supported = supported,
                    experimental = {
                        try {
                            experimental(timings)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            status = "cancelled"
                            throw cancelled
                        } catch (empty: DbnetEmptyPageException) {
                            status = "empty"
                            throw empty
                        } catch (error: Throwable) {
                            status = "failed"
                            throw error
                        }
                    },
                    existing = {
                        fallbackIncluded = true
                        try {
                            existing()
                        } catch (error: Throwable) {
                            status = "fallback_failed"
                            throw error
                        }
                    },
                    onFallback = { reason ->
                        fallbackTriggered = true
                        if (!supported) status = "unsupported"
                        onFallback(reason)
                    },
                )
            } finally {
                if (fallbackTriggered) {
                    withContext(NonCancellable) {
                        try {
                            releaseAfterFallback()
                        } catch (_: Exception) {
                        } catch (_: LinkageError) {
                        } catch (_: OutOfMemoryError) {
                        }
                    }
                }
            }
        } finally {
            try {
                emitDiagnostic(timings.diagnostic(status, fallbackIncluded))
            } catch (_: Exception) {
            } catch (_: LinkageError) {
            } catch (_: OutOfMemoryError) {
            }
        }
    }
}

internal interface DbnetFullPageSession<I, O> {
    suspend fun recognize(input: I): O
    suspend fun release()
}

internal class DbnetFullPageMlKitOwner<I, O>(
    private val selectedIsMlKit: Boolean,
    private val selected: DbnetFullPageSession<I, O>,
    private val createOwnedMlKit: () -> DbnetFullPageSession<I, O>,
) {
    private var ownedMlKit: DbnetFullPageSession<I, O>? = null

    fun beginPage() = DbnetFullPageMlKitAttempt(this)

    internal suspend fun recognizeForAssociation(input: I): O =
        if (selectedIsMlKit) selected.recognize(input) else owned().recognize(input)

    internal suspend fun recognizeSelected(input: I): O = selected.recognize(input)

    internal fun associationUsesSelected(): Boolean = selectedIsMlKit

    suspend fun releaseOwned() {
        val current = ownedMlKit
        ownedMlKit = null
        current?.release()
    }

    private fun owned(): DbnetFullPageSession<I, O> =
        ownedMlKit ?: createOwnedMlKit().also { ownedMlKit = it }
}

internal class DbnetFullPageMlKitAttempt<I, O>(
    private val owner: DbnetFullPageMlKitOwner<I, O>,
) {
    private var selectedSuccess: Cached<O>? = null

    suspend fun recognizeForAssociation(input: I): O = owner.recognizeForAssociation(input).also { result ->
        if (owner.associationUsesSelected()) selectedSuccess = Cached(result)
    }

    suspend fun fallback(input: I): O = selectedSuccess?.value ?: owner.recognizeSelected(input)

    private data class Cached<O>(val value: O)
}
