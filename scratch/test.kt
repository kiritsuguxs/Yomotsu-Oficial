import kotlinx.coroutines.*
import kotlin.coroutines.*

fun main() = runBlocking {
    try {
        suspendCancellableCoroutine<String> { cont ->
            throw NoSuchMethodError("BOOM")
        }
    } catch (e: Throwable) {
        println("Caught: ${e.message}")
    }
}
