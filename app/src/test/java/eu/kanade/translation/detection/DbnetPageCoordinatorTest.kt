package eu.kanade.translation.detection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.IOException

class DbnetPageCoordinatorTest {
    @Test fun `successful selected ML Kit page is the single association pass with no fallback`() = runTest {
        val selectedPage = Any()
        val selected = FakeSession(selectedPage)
        val owner = DbnetFullPageMlKitOwner(
            selectedIsMlKit = true,
            selected = selected,
            createOwnedMlKit = { error("owned ML Kit must stay lazy") },
        )
        val attempt = owner.beginPage()

        val result = DbnetPageCoordinator().execute(
            enabled = true,
            supported = true,
            experimental = { attempt.recognizeForAssociation(Unit) },
            existing = { error("selected page must not run twice") },
            onFallback = { error("success must not clean") },
            releaseAfterFallback = { error("success must not release") },
        )

        assertSame(selectedPage, result)
        assertEquals(1, selected.recognitions)
    }

    @Test fun `association failure reuses completed selected ML Kit page and performs fallback cleanup`() = runTest {
        val selectedPage = Any()
        val selected = FakeSession(selectedPage)
        val owner = DbnetFullPageMlKitOwner(
            selectedIsMlKit = true,
            selected = selected,
            createOwnedMlKit = { error("owned ML Kit must stay lazy") },
        )
        val attempt = owner.beginPage()
        var cleanupCalls = 0

        val result = DbnetPageCoordinator().execute(
            enabled = true,
            supported = true,
            experimental = {
                assertSame(selectedPage, attempt.recognizeForAssociation(Unit))
                throw DbnetAssociationException("ambiguous")
            },
            existing = { attempt.fallback(Unit) },
            onFallback = {},
            releaseAfterFallback = { cleanupCalls++; owner.releaseOwned() },
        )

        assertSame(selectedPage, result)
        assertEquals(1, selected.recognitions)
        assertEquals(0, selected.releases)
        assertEquals(1, cleanupCalls)
    }

    @Test fun `Paddle fallback uses selected once and releases its one owned ML Kit session`() = runTest {
        val selected = FakeSession("selected Paddle page")
        val ownedMlKit = FakeSession("full-page ML Kit")
        val owner = DbnetFullPageMlKitOwner(
            selectedIsMlKit = false,
            selected = selected,
            createOwnedMlKit = { ownedMlKit },
        )
        val attempt = owner.beginPage()

        val result = DbnetPageCoordinator().execute(
            enabled = true,
            supported = true,
            experimental = {
                assertEquals("full-page ML Kit", attempt.recognizeForAssociation(Unit))
                throw DbnetAssociationException("ambiguous")
            },
            existing = { attempt.fallback(Unit) },
            onFallback = {},
            releaseAfterFallback = { owner.releaseOwned() },
        )

        assertEquals("selected Paddle page", result)
        assertEquals(1, selected.recognitions)
        assertEquals(1, ownedMlKit.recognitions)
        assertEquals(1, ownedMlKit.releases)
    }

    @Test fun `successful page records actual stages worker timings counts and overlapping total`() = runTest {
        val diagnostics = mutableListOf<String>()
        val clock = FakeClock()
        val coordinator = DbnetPageCoordinator(
            emitDiagnostic = diagnostics::add,
            clock = clock,
        )

        val result = coordinator.execute(
            enabled = true,
            supported = true,
            experimental = { timing ->
                val durations = mapOf(
                    DbnetPageStage.PAGE_PREPARATION to 2L,
                    DbnetPageStage.DBNET_REQUEST to 20L,
                    DbnetPageStage.GROUPING to 3L,
                    DbnetPageStage.ML_KIT to 4L,
                    DbnetPageStage.ASSOCIATION to 5L,
                    DbnetPageStage.MASK_PREPARATION to 6L,
                )
                for (stage in DbnetPageStage.entries) timing.measure(stage) { clock.advance(durations.getValue(stage)) }
                timing.worker(
                    DbnetWorkerTimings(
                        DbnetStageTiming.completed(3),
                        DbnetStageTiming.completed(5),
                        DbnetStageTiming.completed(7),
                        DbnetWorkerShape(1024, 768, 256, 192, 512, 384),
                    ),
                )
                timing.counts(regions = 4, groups = 2, mlKitBlocks = 3, associatedBlocks = 2)
                "experimental page"
            },
            existing = { error("must not fall back") },
            onFallback = { error("must not clean") },
            releaseAfterFallback = { error("must not release") },
        )

        assertEquals("experimental page", result)
        assertEquals(
            "status=success;fallbackIncluded=false;total=40ms;" +
                "pagePreparation=completed:2ms;dbnetRequest=completed:20ms;" +
                "workerPreparation=completed:3ms;workerInference=completed:5ms;workerPostprocess=completed:7ms;" +
                "input=1024x768;db=256x192;mask=512x384;" +
                "grouping=completed:3ms;mlKit=completed:4ms;association=completed:5ms;" +
                "maskPreparation=completed:6ms;regions=4;groups=2;mlKitBlocks=3;associatedBlocks=2",
            diagnostics.single(),
        )
        assertFalse(diagnostics.single().contains("total=55ms"), "Overlapping worker timings must not be summed")
    }

    @Test fun `each failed page stage is distinguished from later unreached stages and falls back once`() = runTest {
        val fields = linkedMapOf(
            DbnetPageStage.PAGE_PREPARATION to "pagePreparation",
            DbnetPageStage.DBNET_REQUEST to "dbnetRequest",
            DbnetPageStage.GROUPING to "grouping",
            DbnetPageStage.ML_KIT to "mlKit",
            DbnetPageStage.ASSOCIATION to "association",
            DbnetPageStage.MASK_PREPARATION to "maskPreparation",
        )
        for ((failedStage, failedField) in fields) {
            val diagnostics = mutableListOf<String>()
            var selectedCalls = 0
            val clock = FakeClock()
            val coordinator = DbnetPageCoordinator(
                emitDiagnostic = diagnostics::add,
                clock = clock,
            )

            assertEquals(
                "selected page",
                coordinator.execute(
                    enabled = true,
                    supported = true,
                    experimental = { timing ->
                        for (stage in DbnetPageStage.entries) {
                            timing.measure(stage) {
                                clock.advance(1)
                                if (stage == failedStage) throw IOException("private stage content")
                            }
                        }
                        error("failed stage must stop the page")
                    },
                    existing = { selectedCalls++; "selected page" },
                    onFallback = {},
                    releaseAfterFallback = {},
                ),
            )

            val diagnostic = diagnostics.single()
            assertEquals(1, selectedCalls)
            assertTrue(diagnostic.contains("$failedField=failed:1ms"), diagnostic)
            fields.keys.dropWhile { it != failedStage }.drop(1).forEach { later ->
                assertTrue(diagnostic.contains("${fields.getValue(later)}=unreached"), diagnostic)
            }
            assertTrue(diagnostic.contains("fallbackIncluded=true"), diagnostic)
            assertFalse(diagnostic.contains("private stage content"), diagnostic)
        }
    }

    @Test fun `technical stage failure returns only selected fallback cleans once and emits one safe record`() = runTest {
        val diagnostics = mutableListOf<String>()
        var selectedCalls = 0
        var cleanupCalls = 0
        val secret = "recognized-secret api-key-secret"

        val result = DbnetPageCoordinator(diagnostics::add).execute(
            enabled = true,
            supported = true,
            experimental = { throw IOException(secret) },
            existing = {
                selectedCalls++
                "complete selected page"
            },
            onFallback = {},
            releaseAfterFallback = { cleanupCalls++ },
        )

        assertEquals("complete selected page", result)
        assertEquals(1, selectedCalls)
        assertEquals(1, cleanupCalls)
        assertEquals(1, diagnostics.size)
        assertFalse(diagnostics.single().contains(secret))
        assertTrue(diagnostics.single().startsWith("status="))
    }

    @Test fun `fallback time is included in monotonic total and cleanup callback failures stay isolated`() = runTest {
        val diagnostics = mutableListOf<String>()
        val clock = FakeClock()

        val result = DbnetPageCoordinator(diagnostics::add, clock).execute(
            enabled = true,
            supported = true,
            experimental = { timing ->
                timing.measure(DbnetPageStage.PAGE_PREPARATION) {
                    clock.advance(2)
                    throw IOException("private")
                }
            },
            existing = {
                clock.advance(5)
                "selected"
            },
            onFallback = { throw IllegalStateException("notification") },
            releaseAfterFallback = { throw IllegalStateException("release") },
        )

        assertEquals("selected", result)
        assertTrue(diagnostics.single().contains("fallbackIncluded=true"))
        assertTrue(diagnostics.single().contains("total=7ms"))
        assertFalse(diagnostics.single().contains("private"))
        assertFalse(diagnostics.single().contains("notification"))
    }

    @Test fun `cancellation emits one record but never falls back or performs failure cleanup`() = runTest {
        val diagnostics = mutableListOf<String>()
        var selectedCalls = 0
        var cleanupCalls = 0

        try {
            DbnetPageCoordinator(diagnostics::add).execute(
                enabled = true,
                supported = true,
                experimental = { throw CancellationException("private cancellation detail") },
                existing = { selectedCalls++; error("selected must not run") },
                onFallback = {},
                releaseAfterFallback = { cleanupCalls++ },
            )
            fail<Unit>("cancellation expected")
        } catch (_: CancellationException) {
        }

        assertEquals(0, selectedCalls)
        assertEquals(0, cleanupCalls)
        assertEquals(1, diagnostics.size)
        assertFalse(diagnostics.single().contains("private cancellation detail"))
    }

    @Test fun `disabled route stays lazy and emits no experimental diagnostic`() = runTest {
        val diagnostics = mutableListOf<String>()
        var selectedCalls = 0

        val result = DbnetPageCoordinator(diagnostics::add).execute(
            enabled = false,
            supported = true,
            experimental = { error("must remain lazy") },
            existing = { selectedCalls++; "selected" },
            onFallback = { error("must not clean") },
            releaseAfterFallback = { error("must not release") },
        )

        assertEquals("selected", result)
        assertEquals(1, selectedCalls)
        assertTrue(diagnostics.isEmpty())
    }

    @Test fun `selected fallback failure is propagated once and still emits one record`() = runTest {
        val diagnostics = mutableListOf<String>()
        var selectedCalls = 0
        var cleanupCalls = 0

        try {
            DbnetPageCoordinator(diagnostics::add).execute(
                enabled = true,
                supported = true,
                experimental = { error("association") },
                existing = {
                    selectedCalls++
                    throw IOException("selected failed")
                },
                onFallback = {},
                releaseAfterFallback = { cleanupCalls++ },
            )
            fail<Unit>("selected failure expected")
        } catch (_: IOException) {
        }

        assertEquals(1, selectedCalls)
        assertEquals(1, cleanupCalls)
        assertEquals(1, diagnostics.size)
    }

    private class FakeClock : DbnetMonotonicClock {
        private var nanos = 0L
        override fun nowNanos(): Long = nanos
        fun advance(millis: Long) {
            nanos += millis * 1_000_000L
        }
    }

    private class FakeSession<T>(private val value: T) : DbnetFullPageSession<Unit, T> {
        var recognitions = 0
        var releases = 0
        override suspend fun recognize(input: Unit): T {
            recognitions++
            return value
        }
        override suspend fun release() {
            releases++
        }
    }
}
