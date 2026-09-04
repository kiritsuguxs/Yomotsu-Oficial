package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun interface AutoScrollFrameClock {
    suspend fun awaitFrameNanos(): Long
}

class AutoScrollController(
    private val scope: CoroutineScope,
    private val frameClock: AutoScrollFrameClock,
    private val speedPixelsPerSecond: () -> Float,
    private val canScrollForward: () -> Boolean,
    private val scrollBy: (Int) -> Unit,
    private val onEndReached: () -> Unit,
) {
    private val lock = Any()
    private val mutableEnabled = MutableStateFlow(false)
    private var activeJob: Job? = null
    private var destroyed = false

    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun toggle() {
        var jobToStart: Job? = null
        var jobToCancel: Job? = null
        synchronized(lock) {
            if (mutableEnabled.value) {
                jobToCancel = detachJobLocked()
            } else {
                jobToStart = prepareJobLocked()
            }
        }
        jobToCancel?.cancel()
        jobToStart?.start()
    }

    fun start() {
        val job = synchronized(lock) { prepareJobLocked() }
        job?.start()
    }

    fun pause() {
        val job = synchronized(lock) { detachJobLocked() }
        job?.cancel()
    }

    fun destroy() {
        val job = synchronized(lock) {
            destroyed = true
            detachJobLocked()
        }
        job?.cancel()
    }

    private fun prepareJobLocked(): Job? {
        if (destroyed || activeJob != null) return null

        val job = scope.launch(start = CoroutineStart.LAZY) { runLoop() }
        activeJob = job
        mutableEnabled.value = true
        job.invokeOnCompletion { completeJob(job) }
        return job
    }

    private fun detachJobLocked(): Job? {
        mutableEnabled.value = false
        return activeJob.also { activeJob = null }
    }

    private fun completeJob(job: Job) {
        synchronized(lock) {
            if (activeJob === job) {
                activeJob = null
                mutableEnabled.value = false
            }
        }
    }

    private suspend fun runLoop() {
        try {
            var previousFrameNanos: Long? = null
            var remainder = 0.0

            while (true) {
                if (!canScrollForward()) {
                    notifyEndReached()
                    return
                }

                val frameNanos = frameClock.awaitFrameNanos()
                currentCoroutineContext().ensureActive()

                if (!canScrollForward()) {
                    notifyEndReached()
                    return
                }

                val previousFrame = previousFrameNanos
                previousFrameNanos = frameNanos
                if (previousFrame == null) continue

                val deltaNanos = clampedFrameDeltaNanos(previousFrame, frameNanos)
                val speed = speedPixelsPerSecond().takeIf { it.isFinite() && it > 0f } ?: 0f
                remainder += deltaNanos / NANOS_PER_SECOND * speed
                val pixels = (remainder + FRACTIONAL_DISTANCE_EPSILON).toInt()
                remainder = (remainder - pixels).coerceAtLeast(0.0)

                if (pixels > 0) {
                    currentCoroutineContext().ensureActive()
                    if (!canScrollForward()) {
                        notifyEndReached()
                        return
                    }
                    currentCoroutineContext().ensureActive()
                    scrollBy(pixels)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Completion disables the controller; UI callbacks must never crash the owning scope.
        }
    }

    private suspend fun notifyEndReached() {
        val runningJob = currentCoroutineContext()[Job]
        val shouldNotify = synchronized(lock) {
            if (activeJob === runningJob) {
                activeJob = null
                mutableEnabled.value = false
                true
            } else {
                false
            }
        }
        if (shouldNotify) onEndReached()
    }

    private fun clampedFrameDeltaNanos(previousFrameNanos: Long, frameNanos: Long): Long {
        if (frameNanos <= previousFrameNanos) return 0L
        if (previousFrameNanos > Long.MAX_VALUE - MAX_FRAME_DELTA_NANOS) {
            return frameNanos - previousFrameNanos
        }

        val clampAtNanos = previousFrameNanos + MAX_FRAME_DELTA_NANOS
        return if (frameNanos >= clampAtNanos) {
            MAX_FRAME_DELTA_NANOS
        } else {
            frameNanos - previousFrameNanos
        }
    }

    private companion object {
        const val MAX_FRAME_DELTA_NANOS = 100_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val FRACTIONAL_DISTANCE_EPSILON = 1e-9
    }
}
