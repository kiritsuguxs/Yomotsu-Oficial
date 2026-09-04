package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.Choreographer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ChoreographerAutoScrollFrameClock(
    private val choreographer: Choreographer = Choreographer.getInstance(),
) : AutoScrollFrameClock {

    override suspend fun awaitFrameNanos(): Long = suspendCancellableCoroutine { continuation ->
        val callback = Choreographer.FrameCallback { frameTimeNanos ->
            if (continuation.isActive) {
                continuation.resume(frameTimeNanos)
            }
        }

        choreographer.postFrameCallback(callback)
        continuation.invokeOnCancellation {
            choreographer.removeFrameCallback(callback)
        }
    }
}
