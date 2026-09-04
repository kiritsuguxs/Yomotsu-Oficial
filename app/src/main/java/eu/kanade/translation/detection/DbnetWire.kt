package eu.kanade.translation.detection

/** Bounded DBNet IPC: geometry plus a bit-packed native text mask, never a Bitmap. */
object DbnetWire {
    const val DETECT = 1
    const val RESULT = 2
    const val STOP = 3
    const val MAX_REGIONS = 2048
    const val MAX_MASK_BYTES = (DbnetResizePlan.MAX_INPUT_AREA + 7) / 8

    // Native out1 is the already-sigmoided stroke mask, not the DB component map (0.5f).
    private const val SEGMENTATION_MASK_THRESHOLD = 0.12f

    fun encode(result: DetectionResult.Success): FloatArray {
        require(result.regions.size <= MAX_REGIONS)
        require(validMask(result.width, result.height, result.mask))
        return FloatArray(result.regions.size * 9).also { output ->
            result.regions.forEachIndexed { index, region ->
                region.points.forEachIndexed { pointIndex, point ->
                    output[index * 9 + pointIndex * 2] = point.x
                    output[index * 9 + pointIndex * 2 + 1] = point.y
                }
                output[index * 9 + 8] = region.confidence
            }
        }
    }

    fun encodeMask(mask: DbnetMask): ByteArray {
        require(mask.bytes.size <= MAX_MASK_BYTES)
        return mask.bytes.copyOf()
    }

    internal fun encodeWorkerTimings(timings: DbnetWorkerTimings): LongArray {
        val shape = timings.shape
        return longArrayOf(
            statusCode(timings.preparation),
            timings.preparation.durationMillis ?: 0,
            statusCode(timings.inference),
            timings.inference.durationMillis ?: 0,
            statusCode(timings.postprocess),
            timings.postprocess.durationMillis ?: 0,
            shape?.inputWidth?.toLong() ?: 0,
            shape?.inputHeight?.toLong() ?: 0,
            shape?.dbWidth?.toLong() ?: 0,
            shape?.dbHeight?.toLong() ?: 0,
            shape?.maskWidth?.toLong() ?: 0,
            shape?.maskHeight?.toLong() ?: 0,
        )
    }

    fun decode(
        width: Int,
        height: Int,
        packed: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        maskInputWidth: Int,
        maskInputHeight: Int,
        maskRatio: Float,
        maskBytes: ByteArray,
    ): DetectionResult = decodePayload(
        width,
        height,
        packed,
        maskWidth,
        maskHeight,
        maskInputWidth,
        maskInputHeight,
        maskRatio,
        maskBytes,
        workerTimings = null,
    )

    fun decode(
        width: Int,
        height: Int,
        packed: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        maskInputWidth: Int,
        maskInputHeight: Int,
        maskRatio: Float,
        maskBytes: ByteArray,
        workerTimingValues: LongArray,
    ): DetectionResult {
        val workerTimings = decodeWorkerTimings(workerTimingValues)
            ?: return DetectionResult.Failure("Métricas DBNet inválidas")
        if (listOf(workerTimings.preparation, workerTimings.inference, workerTimings.postprocess).any {
                it.status != DbnetStageStatus.COMPLETED
            }
        ) return DetectionResult.Failure("Métricas DBNet incompletas", workerTimings)
        val shape = workerTimings.shape
            ?: return DetectionResult.Failure("Formato DBNet ausente", workerTimings)
        if (shape.inputWidth != maskInputWidth || shape.inputHeight != maskInputHeight ||
            shape.maskWidth != maskWidth || shape.maskHeight != maskHeight
        ) return DetectionResult.Failure("Formato DBNet inconsistente", workerTimings)
        return decodePayload(
            width,
            height,
            packed,
            maskWidth,
            maskHeight,
            maskInputWidth,
            maskInputHeight,
            maskRatio,
            maskBytes,
            workerTimings,
        )
    }

    private fun decodePayload(
        width: Int,
        height: Int,
        packed: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        maskInputWidth: Int,
        maskInputHeight: Int,
        maskRatio: Float,
        maskBytes: ByteArray,
        workerTimings: DbnetWorkerTimings?,
    ): DetectionResult {
        if (width !in 1..100_000 || height !in 1..100_000 || width.toLong() * height > 100_000_000 ||
            packed.size % 9 != 0 || packed.size > MAX_REGIONS * 9
        ) {
            return DetectionResult.Failure("Resposta DBNet inválida")
        }
        if (!validMaskMetadata(
                width,
                height,
                maskWidth,
                maskHeight,
                maskInputWidth,
                maskInputHeight,
                maskRatio,
                maskBytes,
            )
        ) return DetectionResult.Failure("Máscara DBNet inválida")
        val mask = DbnetMask(maskWidth, maskHeight, maskInputWidth, maskInputHeight, maskRatio, maskBytes)
        val regions = ArrayList<TextRegion>(packed.size / 9)
        for (offset in packed.indices step 9) {
            val points = (0..3).map { DetectionPoint(packed[offset + it * 2], packed[offset + it * 2 + 1]) }
            val region = TextDetection.normalize(points, packed[offset + 8], width, height)
                ?: return DetectionResult.Failure("Geometria DBNet inválida")
            regions.add(region)
        }
        return DetectionResult.Success(width, height, regions.toList(), mask, workerTimings)
    }

    internal fun decodeWorkerTimings(values: LongArray): DbnetWorkerTimings? {
        if (values.size != WORKER_TIMING_VALUE_COUNT) return null
        val stages = ArrayList<DbnetStageTiming>(WORKER_STAGE_COUNT)
        for (offset in 0 until WORKER_TIMING_FIELD_COUNT step 2) {
            val code = values[offset]
            val duration = values[offset + 1]
            if (duration < 0) return null
            val timing = when (code) {
                STATUS_UNREACHED -> if (duration == 0L) DbnetStageTiming.UNREACHED else return null
                STATUS_COMPLETED -> DbnetStageTiming.completed(duration)
                STATUS_FAILED -> DbnetStageTiming.failed(duration)
                else -> return null
            }
            stages += timing
        }
        val shapeValues = values.copyOfRange(WORKER_TIMING_FIELD_COUNT, WORKER_TIMING_VALUE_COUNT)
        val shape = if (shapeValues.all { it == 0L }) {
            null
        } else {
            if (shapeValues.any { it !in 1..DbnetResizePlan.MAX_INPUT_SIDE.toLong() }) return null
            try {
                DbnetWorkerShape(
                    inputWidth = shapeValues[0].toInt(),
                    inputHeight = shapeValues[1].toInt(),
                    dbWidth = shapeValues[2].toInt(),
                    dbHeight = shapeValues[3].toInt(),
                    maskWidth = shapeValues[4].toInt(),
                    maskHeight = shapeValues[5].toInt(),
                )
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        return DbnetWorkerTimings(stages[0], stages[1], stages[2], shape)
    }

    private fun statusCode(timing: DbnetStageTiming): Long = when (timing.status) {
        DbnetStageStatus.UNREACHED -> STATUS_UNREACHED
        DbnetStageStatus.COMPLETED -> STATUS_COMPLETED
        DbnetStageStatus.FAILED -> STATUS_FAILED
    }

    /** Packs only the native output's actual grid; unused native buffer capacity is not sent. */
    fun packMask(values: FloatArray, width: Int, height: Int, plan: DbnetResizePlan): DbnetMask? {
        if (width !in 1..plan.inputWidth || height !in 1..plan.inputHeight) return null
        val area = width * height // each factor is bounded by the 1024-pixel input side.
        if (values.size !in area..DbnetResizePlan.MAX_INPUT_AREA) return null
        val bytes = ByteArray((area + 7) / 8)
        for (index in 0 until area) {
            val probability = values[index]
            if (!probability.isFinite() || probability !in 0f..1f) return null
            if (probability > SEGMENTATION_MASK_THRESHOLD) {
                val byteIndex = index / 8
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl (index % 8))).toByte()
            }
        }
        return DbnetMask(width, height, plan.inputWidth, plan.inputHeight, plan.ratio, bytes)
    }

    /** Validates already-constructed masks before encoding them for IPC. */
    private fun validMask(originalWidth: Int, originalHeight: Int, mask: DbnetMask): Boolean {
        return isValidMask(
            originalWidth,
            originalHeight,
            mask,
            mask.bytes,
        )
    }

    /** Lets in-process consumers validate a defensive byte snapshot without reading it twice. */
    internal fun isValidMask(
        originalWidth: Int,
        originalHeight: Int,
        mask: DbnetMask,
        maskBytes: ByteArray,
    ): Boolean = validMaskMetadata(
        originalWidth,
        originalHeight,
        mask.width,
        mask.height,
        mask.inputWidth,
        mask.inputHeight,
        mask.ratio,
        maskBytes,
    )

    /** Validates raw metadata and byte count before a consumer can decode or allocate from it. */
    private fun validMaskMetadata(
        originalWidth: Int,
        originalHeight: Int,
        maskWidth: Int,
        maskHeight: Int,
        maskInputWidth: Int,
        maskInputHeight: Int,
        maskRatio: Float,
        maskBytes: ByteArray,
    ): Boolean {
        if (originalWidth !in 1..DbnetResizePlan.MAX_ORIGINAL_SIDE ||
            originalHeight !in 1..DbnetResizePlan.MAX_ORIGINAL_SIDE ||
            originalWidth.toLong() * originalHeight > DbnetResizePlan.MAX_ORIGINAL_AREA ||
            !maskRatio.isFinite() || maskBytes.size > MAX_MASK_BYTES
        ) return false
        val plan = try {
            DbnetResizePlan.create(originalWidth, originalHeight)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (maskInputWidth != plan.inputWidth || maskInputHeight != plan.inputHeight || maskRatio != plan.ratio ||
            maskWidth !in 1..maskInputWidth || maskHeight !in 1..maskInputHeight
        ) return false
        val area = maskWidth * maskHeight // bounded before multiplication.
        if (maskBytes.size != (area + 7) / 8) return false
        val unusedBits = maskBytes.size * 8 - area
        return unusedBits == 0 || (maskBytes.last().toInt() and (0xFF shl (8 - unusedBits))) == 0
    }

    private const val WORKER_STAGE_COUNT = 3
    private const val WORKER_TIMING_FIELD_COUNT = WORKER_STAGE_COUNT * 2
    private const val WORKER_SHAPE_VALUE_COUNT = 6
    private const val WORKER_TIMING_VALUE_COUNT = WORKER_TIMING_FIELD_COUNT + WORKER_SHAPE_VALUE_COUNT
    private const val STATUS_UNREACHED = 0L
    private const val STATUS_COMPLETED = 1L
    private const val STATUS_FAILED = 2L
}
