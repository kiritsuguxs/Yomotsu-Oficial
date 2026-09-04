package eu.kanade.translation.detection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** One terminal outcome; Binder death, normal replies and timeout race safely. */
class DbnetReply {
    private val result = CompletableDeferred<DetectionResult>()
    fun complete(value: DetectionResult): Boolean = result.complete(value)
    suspend fun await(timeoutMillis: Long): DetectionResult {
        val received = withTimeoutOrNull(timeoutMillis) { result.await() }
        if (received != null) return received
        val failure = DetectionResult.Failure("Tempo limite do processo DBNet")
        result.complete(failure)
        return failure
    }
}
