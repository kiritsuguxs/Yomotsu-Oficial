package eu.kanade.translation.recognizer

import com.paddle.ocr.engine.RecognitionBatchPlanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecognitionBatchPlannerTest {

    @Test
    fun `groups similar crop widths while retaining each source index`() {
        data class Region(val index: Int, val aspectRatio: Float)
        val regions = listOf(
            Region(0, 1f),
            Region(1, 12f),
            Region(2, 2f),
            Region(3, 11f),
        )

        val batches = RecognitionBatchPlanner.partitionByAspectRatio(regions, batchSize = 2) { it.aspectRatio }

        assertEquals(listOf(listOf(0, 2), listOf(3, 1)), batches.map { batch -> batch.map { it.index } })
        assertEquals(regions.map { it.index }.sorted(), batches.flatten().map { it.index }.sorted())
    }

    @Test
    fun `partitions recognition work into small ordered batches`() {
        val regions = (1..9).toList()

        val batches = RecognitionBatchPlanner.partition(regions, batchSize = 4)

        assertEquals(listOf(4, 4, 1), batches.map { it.size })
        assertEquals(regions, batches.flatten())
    }

    @Test
    fun `invalid batch size falls back to one item per inference`() {
        val regions = listOf(1, 2, 3)

        val batches = RecognitionBatchPlanner.partition(regions, batchSize = 0)

        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), batches)
    }
}
