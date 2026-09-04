package eu.kanade.translation.detection

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbnetReplyTest {
    private fun mask() = DbnetMask(1, 1, 256, 256, 25.6f, byteArrayOf(1))

    @Test fun `worker death unblocks caller with recoverable failure`() = runTest {
        val reply = DbnetReply()
        val waiting = async { reply.await(1000) }
        reply.complete(DetectionResult.Failure("worker died"))
        assertEquals(DetectionResult.Failure("worker died"), waiting.await())
    }

    @Test fun `timeout and late reply do not replace completed outcome`() = runTest {
        val reply = DbnetReply()
        assertTrue(reply.await(100) is DetectionResult.Failure)
        assertFalse(reply.complete(DetectionResult.Success(10, 10, emptyList(), mask())))
    }

    @Test fun `duplicate reply is ignored`() = runTest {
        val reply = DbnetReply()
        val expected = DetectionResult.Success(10, 10, emptyList(), mask())
        assertTrue(reply.complete(expected))
        assertFalse(reply.complete(DetectionResult.Failure("late")))
        assertEquals(expected, reply.await(1000))
    }
}
