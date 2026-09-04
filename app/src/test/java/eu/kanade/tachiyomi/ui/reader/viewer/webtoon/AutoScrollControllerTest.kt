package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoScrollControllerTest {

    @Test
    fun `initial state is disabled without requesting a frame`() = runTest {
        val fixture = Fixture(this)

        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(0, fixture.frameClock.requestCount)
    }

    @Test
    fun `start owns one loop and pause cancels it and clears fractional distance`() = runTest {
        val fixture = Fixture(this)

        fixture.controller.start()
        fixture.controller.start()
        runCurrent()
        assertTrue(fixture.controller.enabled.value)
        assertEquals(1, fixture.frameClock.requestCount)

        fixture.frameClock.sendFrame(0L)
        runCurrent()
        fixture.frameClock.sendFrame(10_000_000L)
        runCurrent()
        assertEquals(emptyList<Int>(), fixture.scrolls)

        fixture.controller.pause()
        runCurrent()
        assertFalse(fixture.controller.enabled.value)
        assertEquals(0, fixture.endReachedCount)

        fixture.controller.start()
        runCurrent()
        fixture.frameClock.sendFrame(20_000_000L)
        runCurrent()
        fixture.frameClock.sendFrame(30_000_000L)
        runCurrent()

        assertEquals(emptyList<Int>(), fixture.scrolls)
        fixture.controller.destroy()
    }

    @Test
    fun `fractional distance produces six pixels across ten ten millisecond intervals`() = runTest {
        val fixture = Fixture(this)
        fixture.speedPixelsPerSecond = 60f
        fixture.controller.start()
        runCurrent()

        (0L..100_000_000L step 10_000_000L).forEach { frameNanos ->
            fixture.frameClock.sendFrame(frameNanos)
            runCurrent()
        }

        assertEquals(6, fixture.scrolls.sum())
        fixture.controller.destroy()
    }

    @Test
    fun `frame gaps are clamped to one hundred milliseconds`() = runTest {
        val fixture = Fixture(this)
        fixture.speedPixelsPerSecond = 100f
        fixture.controller.start()
        runCurrent()

        fixture.frameClock.sendFrame(0L)
        runCurrent()
        fixture.frameClock.sendFrame(500_000_000L)
        runCurrent()

        assertEquals(listOf(10), fixture.scrolls)
        fixture.controller.destroy()
    }

    @Test
    fun `speed changes affect the next frame without restarting`() = runTest {
        val fixture = Fixture(this)
        fixture.speedPixelsPerSecond = 50f
        fixture.controller.start()
        runCurrent()

        fixture.frameClock.sendFrame(0L)
        runCurrent()
        fixture.frameClock.sendFrame(10_000_000L)
        runCurrent()
        fixture.speedPixelsPerSecond = 150f
        fixture.frameClock.sendFrame(20_000_000L)
        runCurrent()

        assertEquals(listOf(2), fixture.scrolls)
        assertTrue(fixture.controller.enabled.value)
        fixture.controller.destroy()
    }

    @Test
    fun `end of content pauses once without scrolling`() = runTest {
        val fixture = Fixture(this)
        fixture.canScrollForward = false

        fixture.controller.start()
        runCurrent()
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(emptyList<Int>(), fixture.scrolls)
        assertEquals(1, fixture.endReachedCount)
        assertEquals(false, fixture.enabledWhenEndReached)
    }

    @Test
    fun `end callback can start a new loop without old completion disabling it`() = runTest {
        val fixture = Fixture(this)
        fixture.canScrollForward = false
        fixture.onEndReachedAction = {
            fixture.canScrollForward = true
            fixture.controller.start()
        }

        fixture.controller.start()
        runCurrent()

        assertEquals(1, fixture.endReachedCount)
        assertTrue(fixture.controller.enabled.value)
        assertEquals(1, fixture.frameClock.requestCount)

        fixture.frameClock.sendFrame(0L)
        runCurrent()
        assertEquals(2, fixture.frameClock.requestCount)
        fixture.controller.destroy()
    }

    @Test
    fun `end callback can toggle a new loop without old completion disabling it`() = runTest {
        val fixture = Fixture(this)
        fixture.canScrollForward = false
        fixture.onEndReachedAction = {
            fixture.canScrollForward = true
            fixture.controller.toggle()
        }

        fixture.controller.start()
        runCurrent()

        assertEquals(1, fixture.endReachedCount)
        assertTrue(fixture.controller.enabled.value)
        assertEquals(1, fixture.frameClock.requestCount)
        fixture.controller.destroy()
    }

    @Test
    fun `pause from final scrollability callback prevents the pending scroll`() = runTest {
        val fixture = Fixture(this)
        var pauseBeforeScroll = false
        fixture.speedPixelsPerSecondAction = {
            pauseBeforeScroll = true
            100f
        }
        fixture.canScrollForwardAction = {
            if (pauseBeforeScroll) {
                pauseBeforeScroll = false
                fixture.controller.pause()
            }
            true
        }
        fixture.controller.start()
        runCurrent()

        fixture.frameClock.sendFrame(0L)
        runCurrent()
        fixture.frameClock.sendFrame(10_000_000L)
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(emptyList<Int>(), fixture.scrolls)
        assertEquals(0, fixture.endReachedCount)
    }

    @Test
    fun `destroy is terminal and later starts are ignored`() = runTest {
        val fixture = Fixture(this)
        fixture.controller.start()
        runCurrent()
        assertEquals(1, fixture.frameClock.requestCount)

        fixture.controller.destroy()
        runCurrent()
        fixture.controller.start()
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(1, fixture.frameClock.requestCount)
    }

    @Test
    fun `toggle starts and pauses the same loop`() = runTest {
        val fixture = Fixture(this)

        fixture.controller.toggle()
        runCurrent()
        assertTrue(fixture.controller.enabled.value)
        assertEquals(1, fixture.frameClock.requestCount)

        fixture.controller.toggle()
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(0, fixture.endReachedCount)
    }

    @Test
    fun `unexpected callback failure pauses without escaping or reporting content end`() = runTest {
        val fixture = Fixture(this)
        fixture.controller.start()
        runCurrent()
        fixture.frameClock.sendFrame(0L)
        runCurrent()
        fixture.speedPixelsPerSecondAction = { error("broken speed supplier") }

        fixture.frameClock.sendFrame(10_000_000L)
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(emptyList<Int>(), fixture.scrolls)
        assertEquals(0, fixture.endReachedCount)
    }

    @Test
    fun `timestamp subtraction cannot overflow past the frame clamp`() = runTest {
        val fixture = Fixture(this)
        fixture.speedPixelsPerSecond = 100f
        fixture.controller.start()
        runCurrent()

        fixture.frameClock.sendFrame(Long.MIN_VALUE)
        runCurrent()
        fixture.frameClock.sendFrame(Long.MAX_VALUE)
        runCurrent()

        assertEquals(listOf(10), fixture.scrolls)
        fixture.controller.destroy()
    }

    @Test
    fun `invalid speeds act as zero without poisoning later frames`() = runTest {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -60f).forEach { invalidSpeed ->
            val fixture = Fixture(this)
            fixture.speedPixelsPerSecond = invalidSpeed
            fixture.controller.start()
            runCurrent()

            fixture.frameClock.sendFrame(0L)
            runCurrent()
            fixture.frameClock.sendFrame(10_000_000L)
            runCurrent()
            assertEquals(emptyList<Int>(), fixture.scrolls)

            fixture.speedPixelsPerSecond = 100f
            fixture.frameClock.sendFrame(20_000_000L)
            runCurrent()

            assertEquals(listOf(1), fixture.scrolls)
            fixture.controller.destroy()
            runCurrent()
        }
    }

    @Test
    fun `frame clock failure pauses without escaping`() = runTest {
        val fixture = Fixture(this)
        fixture.controller.start()
        runCurrent()

        fixture.frameClock.failFrame(IllegalStateException("broken clock"))
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(emptyList<Int>(), fixture.scrolls)
        assertEquals(0, fixture.endReachedCount)
    }

    @Test
    fun `scrollability failure pauses without escaping`() = runTest {
        val fixture = Fixture(this)
        fixture.canScrollForwardAction = { error("broken scrollability") }

        fixture.controller.start()
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(0, fixture.frameClock.requestCount)
        assertEquals(0, fixture.endReachedCount)
    }

    @Test
    fun `scroll callback failure pauses without escaping`() = runTest {
        val fixture = Fixture(this)
        fixture.speedPixelsPerSecond = 100f
        fixture.controller.start()
        runCurrent()
        fixture.frameClock.sendFrame(0L)
        runCurrent()
        fixture.scrollFailure = IllegalStateException("broken scroll callback")

        fixture.frameClock.sendFrame(10_000_000L)
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(emptyList<Int>(), fixture.scrolls)
        assertEquals(0, fixture.endReachedCount)
    }

    @Test
    fun `end callback failure remains paused without escaping`() = runTest {
        val fixture = Fixture(this)
        fixture.canScrollForward = false
        fixture.endReachedFailure = IllegalStateException("broken end callback")

        fixture.controller.start()
        runCurrent()

        assertFalse(fixture.controller.enabled.value)
        assertEquals(1, fixture.endReachedCount)
        assertEquals(emptyList<Int>(), fixture.scrolls)
    }
}

private class Fixture(scope: CoroutineScope) {
    val frameClock = ChannelFrameClock()
    var speedPixelsPerSecond = 60f
    var speedPixelsPerSecondAction: (() -> Float)? = null
    var canScrollForward = true
    var canScrollForwardAction: (() -> Boolean)? = null
    var scrollFailure: RuntimeException? = null
    val scrolls = mutableListOf<Int>()
    var endReachedCount = 0
    var enabledWhenEndReached: Boolean? = null
    var onEndReachedAction: (() -> Unit)? = null
    var endReachedFailure: RuntimeException? = null

    lateinit var controller: AutoScrollController
        private set

    init {
        controller = AutoScrollController(
            scope = scope,
            frameClock = frameClock,
            speedPixelsPerSecond = {
                speedPixelsPerSecondAction?.invoke() ?: speedPixelsPerSecond
            },
            canScrollForward = { canScrollForwardAction?.invoke() ?: canScrollForward },
            scrollBy = { pixels ->
                scrollFailure?.let { throw it }
                scrolls += pixels
            },
            onEndReached = {
                endReachedCount++
                enabledWhenEndReached = controller.enabled.value
                onEndReachedAction?.invoke()
                endReachedFailure?.let { throw it }
            },
        )
    }
}

private class ChannelFrameClock : AutoScrollFrameClock {
    private val frames = Channel<Result<Long>>(capacity = Channel.UNLIMITED)

    var requestCount = 0
        private set

    override suspend fun awaitFrameNanos(): Long {
        requestCount++
        return frames.receive().getOrThrow()
    }

    fun sendFrame(frameNanos: Long) {
        frames.trySend(Result.success(frameNanos)).getOrThrow()
    }

    fun failFrame(error: Throwable) {
        frames.trySend(Result.failure(error)).getOrThrow()
    }
}
