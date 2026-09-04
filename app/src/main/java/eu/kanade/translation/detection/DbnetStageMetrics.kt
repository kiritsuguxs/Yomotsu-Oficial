package eu.kanade.translation.detection

internal fun interface DbnetMonotonicClock {
    fun nowNanos(): Long
}

internal object DbnetSystemMonotonicClock : DbnetMonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

internal enum class DbnetWorkerStage {
    PREPARATION,
    INFERENCE,
    POSTPROCESS,
}

internal enum class DbnetPageStage {
    PAGE_PREPARATION,
    DBNET_REQUEST,
    GROUPING,
    ML_KIT,
    ASSOCIATION,
    MASK_PREPARATION,
}

internal class DbnetPageTimingRecorder(
    private val clock: DbnetMonotonicClock,
) {
    private val attemptStart = clock.nowNanos()
    private val stages = DbnetPageStage.entries.associateWith { DbnetStageTiming.UNREACHED }.toMutableMap()
    private var workerTimings: DbnetWorkerTimings? = null
    private var regionCount: Int? = null
    private var groupCount: Int? = null
    private var mlKitBlockCount: Int? = null
    private var associatedBlockCount: Int? = null

    suspend fun <T> measure(stage: DbnetPageStage, block: suspend () -> T): T {
        val start = clock.nowNanos()
        try {
            return block().also { stages[stage] = DbnetStageTiming.completed(elapsedMillis(start)) }
        } catch (error: Throwable) {
            stages[stage] = DbnetStageTiming.failed(elapsedMillis(start))
            throw error
        }
    }

    fun worker(timings: DbnetWorkerTimings?) {
        workerTimings = timings
    }

    fun counts(
        regions: Int? = null,
        groups: Int? = null,
        mlKitBlocks: Int? = null,
        associatedBlocks: Int? = null,
    ) {
        listOfNotNull(regions, groups, mlKitBlocks, associatedBlocks).forEach { require(it >= 0) }
        if (regions != null) regionCount = regions
        if (groups != null) groupCount = groups
        if (mlKitBlocks != null) mlKitBlockCount = mlKitBlocks
        if (associatedBlocks != null) associatedBlockCount = associatedBlocks
    }

    fun diagnostic(status: String, fallbackIncluded: Boolean): String {
        val total = elapsedMillis(attemptStart)
        val worker = workerTimings
        return buildString {
            append("status=").append(status)
            append(";fallbackIncluded=").append(fallbackIncluded)
            append(";total=").append(total).append("ms")
            append(";pagePreparation=").append(format(stages.getValue(DbnetPageStage.PAGE_PREPARATION)))
            append(";dbnetRequest=").append(format(stages.getValue(DbnetPageStage.DBNET_REQUEST)))
            append(";workerPreparation=").append(format(worker?.preparation))
            append(";workerInference=").append(format(worker?.inference))
            append(";workerPostprocess=").append(format(worker?.postprocess))
            append(";input=").append(worker?.shape?.let { "${it.inputWidth}x${it.inputHeight}" } ?: "unavailable")
            append(";db=").append(worker?.shape?.let { "${it.dbWidth}x${it.dbHeight}" } ?: "unavailable")
            append(";mask=").append(worker?.shape?.let { "${it.maskWidth}x${it.maskHeight}" } ?: "unavailable")
            append(";grouping=").append(format(stages.getValue(DbnetPageStage.GROUPING)))
            append(";mlKit=").append(format(stages.getValue(DbnetPageStage.ML_KIT)))
            append(";association=").append(format(stages.getValue(DbnetPageStage.ASSOCIATION)))
            append(";maskPreparation=").append(format(stages.getValue(DbnetPageStage.MASK_PREPARATION)))
            append(";regions=").append(format(regionCount))
            append(";groups=").append(format(groupCount))
            append(";mlKitBlocks=").append(format(mlKitBlockCount))
            append(";associatedBlocks=").append(format(associatedBlockCount))
        }
    }

    private fun elapsedMillis(start: Long): Long {
        val end = clock.nowNanos()
        check(end >= start) { "Monotonic clock moved backwards" }
        return (end - start) / NANOS_PER_MILLISECOND
    }

    private fun format(timing: DbnetStageTiming?): String = when (timing?.status) {
        null -> "unavailable"
        DbnetStageStatus.UNREACHED -> "unreached"
        DbnetStageStatus.COMPLETED -> "completed:${timing.durationMillis}ms"
        DbnetStageStatus.FAILED -> "failed:${timing.durationMillis}ms"
    }

    private fun format(value: Int?): String = value?.toString() ?: "unavailable"

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

internal class DbnetWorkerTimingRecorder(
    private val clock: DbnetMonotonicClock = DbnetSystemMonotonicClock,
) {
    private var preparation = DbnetStageTiming.UNREACHED
    private var inference = DbnetStageTiming.UNREACHED
    private var postprocess = DbnetStageTiming.UNREACHED
    private var shape: DbnetWorkerShape? = null

    fun <T> measure(stage: DbnetWorkerStage, block: () -> T): T {
        val start = clock.nowNanos()
        try {
            return block().also { result ->
                val status = if (result is DetectionResult.Failure) {
                    DbnetStageStatus.FAILED
                } else {
                    DbnetStageStatus.COMPLETED
                }
                record(stage, status, elapsedMillis(start))
            }
        } catch (error: Throwable) {
            record(stage, DbnetStageStatus.FAILED, elapsedMillis(start))
            throw error
        }
    }

    fun shape(value: DbnetWorkerShape) {
        shape = value
    }

    fun snapshot() = DbnetWorkerTimings(preparation, inference, postprocess, shape)

    private fun elapsedMillis(start: Long): Long {
        val end = clock.nowNanos()
        check(end >= start) { "Monotonic clock moved backwards" }
        return (end - start) / NANOS_PER_MILLISECOND
    }

    private fun record(stage: DbnetWorkerStage, status: DbnetStageStatus, durationMillis: Long) {
        val timing = DbnetStageTiming(status, durationMillis)
        when (stage) {
            DbnetWorkerStage.PREPARATION -> preparation = timing
            DbnetWorkerStage.INFERENCE -> inference = timing
            DbnetWorkerStage.POSTPROCESS -> postprocess = timing
        }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
