package eu.kanade.translation.detection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DbnetNotificationTest {
    @Test fun `feedback is queued on Main and never runs inline on the OCR caller`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var shown = false
            DbnetNotification.post { shown = true }
            assertFalse(shown)
            runCurrent()
            assertTrue(shown)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a broken notification cannot abort OCR or crash the main dispatcher`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            DbnetNotification.post { error("UI unavailable") }
            runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
