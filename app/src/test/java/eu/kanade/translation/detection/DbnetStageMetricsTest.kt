package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class DbnetStageMetricsTest {
    @Test fun `worker reply encoding exports the complete validated timing shape`() {
        assertArrayEquals(
            longArrayOf(1, 7, 2, 11, 0, 0, 0, 0, 0, 0, 0, 0),
            DbnetWire.encodeWorkerTimings(
                DbnetWorkerTimings(
                    DbnetStageTiming.completed(7),
                    DbnetStageTiming.failed(11),
                    DbnetStageTiming.UNREACHED,
                ),
            ),
        )
    }

    @Test fun `worker recorder marks a thrown stage failed and leaves later work unreached`() {
        val ticks = ArrayDeque(listOf(0L, 7_000_000L, 10_000_000L, 21_000_000L))
        val recorder = DbnetWorkerTimingRecorder { ticks.removeFirst() }

        assertEquals("prepared", recorder.measure(DbnetWorkerStage.PREPARATION) { "prepared" })
        assertThrows(IOException::class.java) {
            recorder.measure(DbnetWorkerStage.INFERENCE) { throw IOException("native") }
        }
        val shape = DbnetWorkerShape(1024, 768, 256, 192, 512, 384)
        recorder.shape(shape)

        assertEquals(
            DbnetWorkerTimings(
                preparation = DbnetStageTiming.completed(7),
                inference = DbnetStageTiming.failed(11),
                postprocess = DbnetStageTiming.UNREACHED,
                shape = shape,
            ),
            recorder.snapshot(),
        )
    }

    @Test fun `worker recorder marks a returned postprocessor failure failed`() {
        val ticks = ArrayDeque(
            listOf(
                0L,
                3_000_000L,
                3_000_000L,
                8_000_000L,
                8_000_000L,
                15_000_000L,
            ),
        )
        val recorder = DbnetWorkerTimingRecorder { ticks.removeFirst() }

        recorder.measure(DbnetWorkerStage.PREPARATION) { Unit }
        recorder.measure(DbnetWorkerStage.INFERENCE) { Unit }
        val result = recorder.measure(DbnetWorkerStage.POSTPROCESS) {
            DbnetPostprocessor.process(
                db = FloatArray(2),
                dbWidth = 1,
                dbHeight = 1,
                dbChannels = 1,
                maskWidth = 1,
                maskHeight = 1,
                maskChannels = 1,
                mask = FloatArray(1),
                plan = DbnetResizePlan.create(1, 1),
            )
        }

        assertTrue(result is DetectionResult.Failure)
        assertEquals("Invalid DBNet output channels", (result as DetectionResult.Failure).reason)
        assertEquals(
            DbnetWorkerTimings(
                preparation = DbnetStageTiming.completed(3),
                inference = DbnetStageTiming.completed(5),
                postprocess = DbnetStageTiming.failed(7),
            ),
            recorder.snapshot(),
        )
    }

    @Test fun `worker timing transport preserves stage outcome and rejects malformed payloads`() {
        val valid = DbnetWire.decode(
            100,
            200,
            floatArrayOf(),
            1,
            1,
            512,
            1024,
            5.12f,
            byteArrayOf(0),
            longArrayOf(1, 7, 1, 11, 1, 13, 512, 1024, 256, 512, 1, 1),
        ) as DetectionResult.Success
        assertEquals(DbnetStageTiming.completed(7), valid.workerTimings?.preparation)
        assertEquals(DbnetStageTiming.completed(11), valid.workerTimings?.inference)
        assertEquals(DbnetStageTiming.completed(13), valid.workerTimings?.postprocess)
        assertEquals(DbnetWorkerShape(512, 1024, 256, 512, 1, 1), valid.workerTimings?.shape)

        val partial = DbnetWire.decode(
            100,
            200,
            floatArrayOf(),
            1,
            1,
            512,
            1024,
            5.12f,
            byteArrayOf(0),
            longArrayOf(1, 7, 2, 11, 0, 0, 0, 0, 0, 0, 0, 0),
        ) as DetectionResult.Failure
        assertEquals(DbnetStageTiming.completed(7), partial.workerTimings?.preparation)
        assertEquals(DbnetStageTiming.failed(11), partial.workerTimings?.inference)
        assertEquals(DbnetStageTiming.UNREACHED, partial.workerTimings?.postprocess)

        for (payload in listOf(
            longArrayOf(1, 7),
            longArrayOf(1, -1, 1, 0, 1, 0),
            longArrayOf(1, 7, 2, 11, 0, 0),
            longArrayOf(1, 7, 1, 11, 1, 13),
            longArrayOf(1, 7, 1, 11, 1, 13, 512, 1024, 256, 512, 2, 1),
        )) {
            val result = DbnetWire.decode(
                100,
                200,
                floatArrayOf(),
                1,
                1,
                512,
                1024,
                5.12f,
                byteArrayOf(0),
                payload,
            )
            assertTrue(result is DetectionResult.Failure, "Malformed or incomplete success timings must fail the reply")
        }
    }
}
