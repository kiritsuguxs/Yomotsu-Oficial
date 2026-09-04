package eu.kanade.translation.detection

import eu.kanade.translation.recognizer.OcrPage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.math.ceil
import kotlin.math.floor

class DbnetCleanupMaskException(message: String) : Exception(message)

/**
 * Sparse original-page mask. Runs are immutable (y, x, length) triples in strict row order.
 * The checksum detects accidental stored-value damage; semantic validation independently bounds
 * dimensions, ordering, coordinates, run count, erased pixels, and page-wide rendering work.
 */
@Serializable(with = DbnetCleanupMask.Serializer::class)
class DbnetCleanupMask private constructor(
    val pageWidth: Int,
    val pageHeight: Int,
    private val runs: IntArray,
    private val checksum: Int,
) {
    val isEmpty: Boolean get() = runs.isEmpty()

    fun forEachRun(
        expectedWidth: Int,
        expectedHeight: Int,
        action: (y: Int, x: Int, length: Int) -> Unit,
    ): Boolean {
        if (!isValidFor(expectedWidth, expectedHeight)) return false
        var index = 0
        while (index < runs.size) {
            action(runs[index], runs[index + 1], runs[index + 2])
            index += RUN_FIELDS
        }
        return true
    }

    private fun isValidFor(expectedWidth: Int, expectedHeight: Int): Boolean {
        if (pageWidth != expectedWidth || pageHeight != expectedHeight ||
            pageWidth !in 1..DbnetResizePlan.MAX_ORIGINAL_SIDE ||
            pageHeight !in 1..DbnetResizePlan.MAX_ORIGINAL_SIDE ||
            pageWidth.toLong() * pageHeight > DbnetResizePlan.MAX_ORIGINAL_AREA ||
            runs.size % RUN_FIELDS != 0 || runs.size > MAX_RUNS * RUN_FIELDS ||
            checksum != checksum(pageWidth, pageHeight, runs)
        ) return false

        var previousY = -1
        var previousEnd = -1
        var pixels = 0L
        var index = 0
        while (index < runs.size) {
            val y = runs[index]
            val x = runs[index + 1]
            val length = runs[index + 2]
            if (y !in 0 until pageHeight || x !in 0 until pageWidth || length <= 0 ||
                x.toLong() + length > pageWidth || y < previousY ||
                (y == previousY && x <= previousEnd)
            ) return false
            pixels += length
            if (pixels > MAX_ERASED_PIXELS) return false
            if (y != previousY) previousEnd = -1
            previousY = y
            previousEnd = x + length - 1
            index += RUN_FIELDS
        }
        return true
    }

    private fun runCount(): Int = runs.size / RUN_FIELDS

    private fun erasedPixels(): Long {
        var total = 0L
        var index = 2
        while (index < runs.size) {
            total += runs[index]
            index += RUN_FIELDS
        }
        return total
    }

    internal object Serializer : KSerializer<DbnetCleanupMask> {
        override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

        override fun serialize(encoder: Encoder, value: DbnetCleanupMask) {
            val jsonEncoder = encoder as? JsonEncoder
                ?: error("DbnetCleanupMask supports JSON storage only")
            jsonEncoder.encodeJsonElement(
                JsonObject(
                    mapOf(
                        "pageWidth" to JsonPrimitive(value.pageWidth),
                        "pageHeight" to JsonPrimitive(value.pageHeight),
                        "runs" to JsonArray(value.runs.map(::JsonPrimitive)),
                        "checksum" to JsonPrimitive(value.checksum),
                    ),
                ),
            )
        }

        override fun deserialize(decoder: Decoder): DbnetCleanupMask {
            val jsonDecoder = decoder as? JsonDecoder
                ?: error("DbnetCleanupMask supports JSON storage only")
            val objectValue = jsonDecoder.decodeJsonElement() as? JsonObject ?: return invalid()
            val width = objectValue.intValue("pageWidth") ?: return invalid()
            val height = objectValue.intValue("pageHeight") ?: return invalid()
            val checksum = objectValue.intValue("checksum") ?: return invalid()
            val values = objectValue["runs"] as? JsonArray ?: return invalid()
            if (values.size > MAX_RUNS * RUN_FIELDS) return invalid()
            val runs = IntArray(values.size)
            for (index in values.indices) {
                runs[index] = (values[index] as? JsonPrimitive)?.intOrNull ?: return invalid()
            }
            return DbnetCleanupMask(width, height, runs, checksum)
        }

        private fun JsonObject.intValue(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
    }

    companion object {
        const val MAX_RUNS = 131_072
        const val MAX_MASKS_PER_PAGE = DbnetWire.MAX_REGIONS
        const val MAX_PERMISSION_ROW_VISITS = 262_144
        const val MAX_WORK_UNITS = 8_000_000L
        const val MAX_ERASED_PIXELS = 8_000_000L
        private const val RUN_FIELDS = 3
        private const val DILATION_RADIUS = 2
        private const val MIN_DENSE_PERMISSION_PIXELS = 64L
        private const val MAX_PERMISSION_FILL_PERCENT = 70L

        internal fun fromRuns(pageWidth: Int, pageHeight: Int, runs: IntArray): DbnetCleanupMask {
            val snapshot = runs.copyOf()
            return DbnetCleanupMask(pageWidth, pageHeight, snapshot, checksum(pageWidth, pageHeight, snapshot))
        }

        fun areValidForPage(masks: Iterable<DbnetCleanupMask>, pageWidth: Int, pageHeight: Int): Boolean =
            areValidForPage(masks.asSequence(), pageWidth, pageHeight)

        fun areValidForPage(masks: Sequence<DbnetCleanupMask>, pageWidth: Int, pageHeight: Int): Boolean {
            var count = 0
            var runs = 0L
            var pixels = 0L
            for (mask in masks) {
                count++
                if (count > MAX_MASKS_PER_PAGE || !mask.isValidFor(pageWidth, pageHeight)) return false
                runs += mask.runCount()
                pixels += mask.erasedPixels()
                if (runs > MAX_RUNS || pixels > MAX_ERASED_PIXELS) return false
            }
            return true
        }

        fun areValidForPage(masks: Iterable<DbnetCleanupMask>, pageWidth: Float, pageHeight: Float): Boolean =
            areValidForPage(masks.asSequence(), pageWidth, pageHeight)

        fun areValidForPage(masks: Sequence<DbnetCleanupMask>, pageWidth: Float, pageHeight: Float): Boolean {
            val width = exactDimension(pageWidth) ?: return false
            val height = exactDimension(pageHeight) ?: return false
            return areValidForPage(masks, width, height)
        }

        /** Validates and prepares every associated group atomically before OCR leaves the wrapper. */
        fun prepare(page: OcrPage): OcrPage {
            val metadata = page.dbnetAssociation ?: return page
            val plan = try {
                DbnetResizePlan.create(page.width, page.height)
            } catch (_: IllegalArgumentException) {
                fail("Invalid original page dimensions")
            }
            val bytes = metadata.mask.bytes
            if (!DbnetWire.isValidMask(page.width, page.height, metadata.mask, bytes)) {
                fail("Invalid DBNet mask metadata")
            }
            if (metadata.mask.inputWidth != plan.inputWidth || metadata.mask.inputHeight != plan.inputHeight ||
                metadata.mask.ratio != plan.ratio || metadata.groups.size != page.blocks.size ||
                metadata.groups.size > MAX_MASKS_PER_PAGE
            ) fail("Invalid DBNet cleanup ownership")

            val owners = arrayOfNulls<DbnetCleanupMask>(page.blocks.size)
            val budget = Budget()
            for (associated in metadata.groups) {
                if (associated.blockIndex !in page.blocks.indices || owners[associated.blockIndex] != null) {
                    fail("Invalid DBNet cleanup block owner")
                }
                owners[associated.blockIndex] = buildMask(
                    page.width,
                    page.height,
                    plan,
                    metadata.mask,
                    bytes,
                    associated.group.memberLines,
                    budget,
                )
            }
            if (owners.any { it == null }) fail("Missing DBNet cleanup block owner")
            return page.copy(
                blocks = page.blocks.mapIndexed { index, block -> block.copy(dbnetCleanupMask = owners[index]) },
            )
        }

        private fun buildMask(
            pageWidth: Int,
            pageHeight: Int,
            plan: DbnetResizePlan,
            nativeMask: DbnetMask,
            bytes: ByteArray,
            permissions: List<TextRegion>,
            budget: Budget,
        ): DbnetCleanupMask {
            if (permissions.isEmpty()) fail("DBNet cleanup owner has no member lines")
            val permitted = sortedMapOf<Int, MutableList<Interval>>()
            permissions.forEach { permission ->
                val points = permission.points
                validatePermission(permission, points, pageWidth, pageHeight)
                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }
                val startX = ceil(minX - 0.5f).toInt().coerceIn(0, pageWidth)
                val endX = floor(maxX - 0.5f).toInt().coerceIn(-1, pageWidth - 1)
                val startY = ceil(minY - 0.5f).toInt().coerceIn(0, pageHeight)
                val endY = floor(maxY - 0.5f).toInt().coerceIn(-1, pageHeight - 1)
                if (startX > endX || startY > endY) fail("DBNet cleanup permission has no pixels")
                for (y in startY..endY) {
                    budget.permissionRows++
                    if (budget.permissionRows > MAX_PERMISSION_ROW_VISITS) fail("DBNet cleanup row limit exceeded")
                    var runStart = -1
                    for (x in startX..endX) {
                        budget.workUnits++
                        if (budget.workUnits > MAX_WORK_UNITS) fail("DBNet cleanup work limit exceeded")
                        val inside = contains(points, x + 0.5f, y + 0.5f)
                        if (inside && runStart < 0) runStart = x
                        if (!inside && runStart >= 0) {
                            permitted.getOrPut(y) { mutableListOf() } += Interval(runStart, x - 1)
                            runStart = -1
                        }
                    }
                    if (runStart >= 0) permitted.getOrPut(y) { mutableListOf() } += Interval(runStart, endX)
                }
            }
            permitted.entries.forEach { entry -> entry.setValue(merge(entry.value)) }
            val permissionPixels = permitted.values.sumOf { intervals ->
                intervals.sumOf { interval -> interval.length.toLong() }
            }

            // First intersection: only native-positive pixels already inside permissions seed dilation.
            val base = sortedMapOf<Int, List<Interval>>()
            permitted.forEach { (y, intervals) ->
                val row = mutableListOf<Interval>()
                intervals.forEach { interval ->
                    var start = -1
                    for (x in interval.start..interval.end) {
                        budget.workUnits++
                        if (budget.workUnits > MAX_WORK_UNITS) fail("DBNet cleanup work limit exceeded")
                        val set = nativePixelIsSet(x, y, plan, nativeMask, bytes)
                        if (set && start < 0) start = x
                        if (!set && start >= 0) {
                            budget.baseRuns++
                            if (budget.baseRuns > MAX_RUNS) fail("DBNet cleanup intermediate run limit exceeded")
                            row += Interval(start, x - 1)
                            start = -1
                        }
                    }
                    if (start >= 0) {
                        budget.baseRuns++
                        if (budget.baseRuns > MAX_RUNS) fail("DBNet cleanup intermediate run limit exceeded")
                        row += Interval(start, interval.end)
                    }
                }
                if (row.isNotEmpty()) base[y] = row
            }

            val output = ArrayList<Int>()
            var ownerErasedPixels = 0L
            permitted.forEach { (y, permissionRuns) ->
                val expanded = mutableListOf<Interval>()
                for (sourceY in y - DILATION_RADIUS..y + DILATION_RADIUS) {
                    base[sourceY]?.forEach { run ->
                        expanded += Interval(
                            (run.start - DILATION_RADIUS).coerceAtLeast(0),
                            (run.end + DILATION_RADIUS).coerceAtMost(pageWidth - 1),
                        )
                    }
                }
                // Second intersection: dilation cannot escape the exact translated member quads.
                val clipped = intersect(merge(expanded), permissionRuns)
                clipped.forEach { run ->
                    budget.outputRuns++
                    budget.erasedPixels += run.length
                    ownerErasedPixels += run.length
                    if (budget.outputRuns > MAX_RUNS || budget.erasedPixels > MAX_ERASED_PIXELS) {
                        fail("DBNet cleanup output limit exceeded")
                    }
                    output += y
                    output += run.start
                    output += run.length
                }
            }
            if (output.isEmpty()) fail("DBNet cleanup owner has no usable mask pixels")
            if (permissionPixels >= MIN_DENSE_PERMISSION_PIXELS &&
                ownerErasedPixels * 100L > permissionPixels * MAX_PERMISSION_FILL_PERCENT
            ) {
                fail("DBNet cleanup mask is excessively dense")
            }
            return fromRuns(pageWidth, pageHeight, output.toIntArray())
        }

        private fun validatePermission(
            region: TextRegion,
            points: List<DetectionPoint>,
            width: Int,
            height: Int,
        ) {
            if (!region.confidence.isFinite() || region.confidence !in 0f..1f || points.any { point ->
                    !point.x.isFinite() || !point.y.isFinite() || point.x < 0f || point.y < 0f ||
                        point.x > width || point.y > height
                } || polygonArea(points) <= 1e-3f
            ) fail("Invalid DBNet cleanup permission")
        }

        private fun nativePixelIsSet(
            x: Int,
            y: Int,
            plan: DbnetResizePlan,
            mask: DbnetMask,
            bytes: ByteArray,
        ): Boolean {
            val detectorX = (x + 0.5f) * plan.ratio
            val detectorY = (y + 0.5f) * plan.ratio
            if (detectorX < 0f || detectorY < 0f ||
                detectorX >= plan.resizedWidth || detectorY >= plan.resizedHeight
            ) return false
            val maskX = floor(detectorX * mask.width / mask.inputWidth).toInt()
            val maskY = floor(detectorY * mask.height / mask.inputHeight).toInt()
            if (maskX !in 0 until mask.width || maskY !in 0 until mask.height) return false
            val index = maskY * mask.width + maskX
            return bytes[index / 8].toInt() and (1 shl (index % 8)) != 0
        }

        private fun merge(intervals: List<Interval>): MutableList<Interval> {
            if (intervals.isEmpty()) return mutableListOf()
            val sorted = intervals.sortedBy { it.start }
            val result = mutableListOf(sorted.first())
            for (index in 1 until sorted.size) {
                val candidate = sorted[index]
                val previous = result.last()
                if (candidate.start <= previous.end + 1) {
                    result[result.lastIndex] = Interval(previous.start, maxOf(previous.end, candidate.end))
                } else {
                    result += candidate
                }
            }
            return result
        }

        private fun intersect(first: List<Interval>, second: List<Interval>): List<Interval> {
            val result = mutableListOf<Interval>()
            var a = 0
            var b = 0
            while (a < first.size && b < second.size) {
                val start = maxOf(first[a].start, second[b].start)
                val end = minOf(first[a].end, second[b].end)
                if (start <= end) result += Interval(start, end)
                if (first[a].end < second[b].end) a++ else b++
            }
            return result
        }

        private fun contains(points: List<DetectionPoint>, x: Float, y: Float): Boolean {
            var inside = false
            var previous = points.lastIndex
            for (current in points.indices) {
                val first = points[current]
                val second = points[previous]
                if ((first.y > y) != (second.y > y) &&
                    x < (second.x - first.x) * (y - first.y) / (second.y - first.y) + first.x
                ) inside = !inside
                previous = current
            }
            return inside
        }

        private fun polygonArea(points: List<DetectionPoint>): Float {
            var twice = 0.0
            for (index in points.indices) {
                val next = points[(index + 1) % points.size]
                twice += points[index].x.toDouble() * next.y - next.x.toDouble() * points[index].y
            }
            return (kotlin.math.abs(twice) / 2.0).toFloat()
        }

        private fun checksum(width: Int, height: Int, values: IntArray): Int {
            var hash = -0x7ee3623b
            fun add(value: Int) {
                hash = (hash xor value) * 0x01000193
            }
            add(width)
            add(height)
            values.forEach(::add)
            return hash
        }

        internal fun exactDimension(value: Float): Int? {
            if (!value.isFinite() || value < 1f || value > DbnetResizePlan.MAX_ORIGINAL_SIDE) return null
            val integer = value.toInt()
            return integer.takeIf { it.toFloat() == value }
        }

        private fun invalid() = DbnetCleanupMask(0, 0, IntArray(0), 0)
        private fun fail(message: String): Nothing = throw DbnetCleanupMaskException(message)

        private data class Interval(val start: Int, val end: Int) {
            val length: Int get() = end - start + 1
        }

        private class Budget {
            var permissionRows = 0
            var workUnits = 0L
            var baseRuns = 0
            var outputRuns = 0
            var erasedPixels = 0L
        }
    }
}
