package eu.kanade.translation.detection

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext

/** UI feedback must not run on the detector's IO thread or interrupt fallback. */
object DbnetNotification {
    fun post(display: () -> Unit) {
        Dispatchers.Main.dispatch(EmptyCoroutineContext) { runCatching(display) }
    }
}
