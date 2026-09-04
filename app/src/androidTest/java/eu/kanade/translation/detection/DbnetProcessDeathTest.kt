package eu.kanade.translation.detection

import android.content.Intent
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DbnetProcessDeathTest {
    @Test fun workerDeathReturnsFailureAndKeepsCallerAlive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callerPid = Process.myPid()
        val input = File.createTempFile("dbnet-death", ".tmp", context.cacheDir).apply { writeText("test") }
        val intent = Intent().setClassName(context, "eu.kanade.translation.detection.DbnetCrashTestService")
        val client = DbnetClient(context, intent)
        try {
            val result = client.detect(input)
            assertTrue(result is DetectionResult.Failure)
            assertTrue((result as DetectionResult.Failure).reason, result.reason.contains("Processo DBNet encerrado"))
            assertEquals(callerPid, Process.myPid())
            val fallback = ExperimentalDetectionRoute.execute(true, true, {
                check(result is DetectionResult.Success)
                "dbnet"
            }, { "existing OCR" }, {})
            assertEquals("existing OCR", fallback)
        } finally {
            client.close()
            input.delete()
        }
    }
}
