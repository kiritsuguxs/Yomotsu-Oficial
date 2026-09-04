package eu.kanade.translation.detection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.IOException

class ExperimentalDetectionRouteTest {
    @Test fun `empty page uses existing OCR without disabling DBNet for the next page`() = runTest {
        var disabled = false
        val blank = ExperimentalDetectionRoute.execute(
            true,
            true,
            { throw DbnetEmptyPageException() },
            { "blank via original OCR" },
            { disabled = true },
        )
        assertEquals("blank via original OCR", blank)
        assertFalse(disabled)
        val next = ExperimentalDetectionRoute.execute(!disabled, true, { "DBNet next page" }, { "original" }, {})
        assertEquals("DBNet next page", next)
    }

    @Test fun `fallback remains usable when experimental cleanup or warning fails`() = runTest {
        for (supported in listOf(true, false)) {
            val result = ExperimentalDetectionRoute.execute(
                true,
                supported,
                { error("worker") },
                { "existing" },
                { throw IllegalStateException("cleanup") },
            )
            assertEquals("existing", result)
        }
    }

    @Test fun `default invokes only existing OCR without detector initialization`() = runTest {
        val value = ExperimentalDetectionRoute.execute(false, true, {
            error("detector must not initialize")
        }, { "existing" }, { error("no warning") })
        assertEquals("existing", value)
    }

    @Test fun `experimental success bypasses existing detector`() = runTest {
        assertEquals(
            "regions",
            ExperimentalDetectionRoute.execute(true, true, {
                "regions"
            }, { error("must not redetect") }, {}),
        )
    }

    @Test fun `unsupported configuration keeps existing OCR and explains fallback`() = runTest {
        val warnings = mutableListOf<String>()
        assertEquals(
            "existing",
            ExperimentalDetectionRoute.execute(true, false, {
                error("must not run")
            }, { "existing" }, warnings::add),
        )
        assertEquals(1, warnings.size)
    }

    @Test fun `model native and process failures each fall back exactly once`() = runTest {
        for (failure in listOf(
            IOException("model"),
            IllegalStateException("worker died"),
            UnsatisfiedLinkError("ncnn"),
            OutOfMemoryError("experimental bitmap"),
        )) {
            var count = 0
            val warnings = mutableListOf<String>()
            assertEquals(
                "existing",
                ExperimentalDetectionRoute.execute(true, true, { throw failure }, {
                    count++
                    "existing"
                }, warnings::add),
            )
            assertEquals(1, count)
            assertEquals(1, warnings.size)
        }
    }

    @Test fun `cancellation propagates without fallback`() = runTest {
        var fellBack = false
        try {
            ExperimentalDetectionRoute.execute(true, true, {
                throw CancellationException("cancel")
            }, { fellBack = true }, {})
            fail<Unit>("cancellation expected")
        } catch (_: CancellationException) {
            assertFalse(fellBack)
        }
    }

    @Test fun `existing engine failure is not retried`() = runTest {
        var count = 0
        try {
            ExperimentalDetectionRoute.execute(true, true, { error("worker") }, {
                count++
                throw IOException("existing")
            }, {})
            fail<Unit>("failure expected")
        } catch (e: IOException) {
            assertEquals("existing", e.message)
            assertEquals(1, count)
        }
    }
}
