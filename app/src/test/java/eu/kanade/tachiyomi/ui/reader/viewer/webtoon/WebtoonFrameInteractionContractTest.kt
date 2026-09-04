package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class WebtoonFrameInteractionContractTest {

    @Test
    fun `action down pauses before gesture detectors without consuming the event`() {
        val source = webtoonFrameSource().readText()
        val dispatchTouchEvent = source.substring(
            startIndex = source.indexOf("override fun dispatchTouchEvent(ev: MotionEvent): Boolean {")
                .also { assertTrue(it >= 0, "dispatchTouchEvent must remain implemented by WebtoonFrame") },
            endIndex = source.indexOf("    /**", source.indexOf("override fun dispatchTouchEvent"))
                .also { assertTrue(it >= 0, "dispatchTouchEvent must retain a bounded method body") },
        )

        val actionDown = dispatchTouchEvent.indexOf("ev.actionMasked == MotionEvent.ACTION_DOWN")
        val interactionCallback = dispatchTouchEvent.indexOf("onUserInteraction?.invoke()")
        val scaleDetector = dispatchTouchEvent.indexOf("scaleDetector.onTouchEvent(ev)")
        val flingDetector = dispatchTouchEvent.indexOf("flingDetector.onTouchEvent(ev)")

        assertTrue(actionDown >= 0, "ACTION_DOWN must be handled explicitly")
        assertTrue(interactionCallback > actionDown, "ACTION_DOWN must invoke the interaction callback")
        assertTrue(interactionCallback < scaleDetector, "Manual pause must happen before scale detection")
        assertTrue(interactionCallback < flingDetector, "Manual pause must happen before fling detection")
        assertTrue(
            dispatchTouchEvent.contains("return super.dispatchTouchEvent(ev)"),
            "WebtoonFrame must keep forwarding the original event result",
        )
    }

    private fun webtoonFrameSource(): File {
        return sequenceOf(
            File("app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonFrame.kt"),
            File("src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonFrame.kt"),
        ).firstOrNull(File::isFile)
            ?: error("Unable to locate WebtoonFrame.kt from ${File(".").absolutePath}")
    }
}
