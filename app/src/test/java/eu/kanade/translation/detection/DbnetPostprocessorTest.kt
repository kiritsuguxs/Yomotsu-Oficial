package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbnetPostprocessorTest {
    private fun grid(w: Int = 32, h: Int = 32) = FloatArray(w * h * 2) { -100f }
    private fun fill(db: FloatArray, width: Int, x: IntRange, y: IntRange, value: Float = 10f) {
        for (yy in y) for (xx in x) db[yy * width + xx] = value
    }
    private fun run(
        db: FloatArray,
        w: Int = 32,
        h: Int = 32,
        channels: Int = 2,
        mw: Int = 512,
        mh: Int = 512,
        mc: Int = 1,
        ms: Int = 1024 * 1024,
        plan: DbnetResizePlan = DbnetResizePlan.create(32, 32),
    ) = DbnetPostprocessor.process(db, w, h, channels, mw, mh, mc, FloatArray(ms), plan)
    private fun runWithMask(
        db: FloatArray,
        w: Int,
        h: Int,
        channels: Int,
        mw: Int,
        mh: Int,
        mc: Int,
        mask: FloatArray,
        plan: DbnetResizePlan,
    ): DetectionResult {
        return DbnetPostprocessor.process(db, w, h, channels, mw, mh, mc, mask, plan)
    }
    private fun success(result: DetectionResult): DetectionResult.Success {
        assertTrue(result is DetectionResult.Success, result.toString())
        return result as DetectionResult.Success
    }

    @Test
    fun `normal component is scored expanded and returned in original coordinates`() {
        val db = grid()
        fill(db, 32, 10..14, 10..14)
        val result = success(run(db))
        assertEquals(32, result.width)
        assertEquals(32, result.height)
        assertEquals(1, result.regions.size)
        val region = result.regions.single()
        assertEquals(7.7f, region.topLeft.x, 0.001f)
        assertEquals(7.7f, region.topLeft.y, 0.001f)
        assertEquals(16.3f, region.bottomRight.x, 0.001f)
        assertEquals(0.9999546f, region.confidence, 0.00001f)
    }

    @Test
    fun `blank logits produce successful empty result`() {
        assertTrue(success(run(grid())).regions.isEmpty())
    }

    @Test
    fun `zero logits are strictly excluded even when surrounded by positive logits`() {
        val db = grid()
        fill(db, 32, 10..14, 10..14, 1f)
        fill(db, 32, 10..12, 10..14, 0f)
        assertTrue(success(run(db)).regions.isEmpty()) // remaining two-pixel strip has short side 1
    }

    @Test
    fun `score uses sigmoid once and rejects mean probability below threshold`() {
        val low = grid()
        fill(low, 32, 10..14, 10..14, 0.8f) // sigmoid = .689974
        assertTrue(success(run(low)).regions.isEmpty())
        val high = grid()
        fill(high, 32, 10..14, 10..14, 0.9f) // sigmoid = .710950
        assertEquals(0.7109495f, success(run(high)).regions.single().confidence, 0.00001f)
    }

    @Test
    fun `score is component mean rather than maximum`() {
        val db = grid()
        fill(db, 32, 10..14, 10..14, 0.1f)
        db[10 * 32 + 10] = 100f
        assertTrue(success(run(db)).regions.isEmpty())
    }

    @Test
    fun `diagonal pixels connect components but separated boxes keep row major order`() {
        val connected = grid()
        fill(connected, 32, 2..5, 2..5)
        fill(connected, 32, 6..9, 6..9)
        assertEquals(1, success(run(connected)).regions.size)
        val separated = grid()
        fill(separated, 32, 20..23, 2..5)
        fill(separated, 32, 2..5, 20..23)
        val first = success(run(separated)).regions
        assertEquals(2, first.size)
        assertTrue(first[0].topLeft.x > first[1].topLeft.x)
        assertTrue(first[0].topLeft.y < first[1].topLeft.y)
        assertEquals(first, success(run(separated)).regions)
    }

    @Test
    fun `minimum side is measured before unclip with inclusive three pixel threshold`() {
        val tooThin = grid()
        fill(tooThin, 32, 10..12, 10..20)
        assertTrue(success(run(tooThin)).regions.isEmpty())
        val valid = grid()
        fill(valid, 32, 10..13, 10..20)
        assertEquals(1, success(run(valid)).regions.size)
    }

    @Test
    fun `border component is clipped to original image`() {
        val db = grid()
        fill(db, 32, 0..4, 0..4)
        val region = success(run(db)).regions.single()
        assertEquals(DetectionPoint(0f, 0f), region.topLeft)
        assertEquals(6.3f, region.bottomRight.x, 0.001f)
        assertTrue(region.points.all { it.x in 0f..32f && it.y in 0f..32f })
    }

    @Test
    fun `differently sized DB grid scales x and y independently using padded input`() {
        val db = grid(32, 32)
        fill(db, 32, 10..14, 5..9)
        val plan = DbnetResizePlan.create(1000, 333) // 1024x341 padded to 1024x512
        // A square DB grid over this rectangular input requires x scale 31.25 and y scale 15.625.
        val region = success(run(db, mw = 512, mh = 256, ms = 1024 * 512, plan = plan)).regions.single()
        assertEquals(240.625f, region.topLeft.x, 0.001f)
        assertEquals(42.1875f, region.topLeft.y, 0.001f)
        assertEquals(509.375f, region.bottomRight.x, 0.001f)
        assertEquals(176.5625f, region.bottomRight.y, 0.001f)
    }

    @Test
    fun `boxes entirely in padded area are removed after clipping`() {
        val db = grid(32, 32)
        fill(db, 32, 10..14, 25..29)
        val result = run(db, mw = 512, mh = 128, ms = 1024 * 256, plan = DbnetResizePlan.create(1000, 100))
        assertTrue(success(result).regions.isEmpty())
    }

    @Test
    fun `half and full masks are both accepted and do not change geometry`() {
        val db = grid()
        fill(db, 32, 10..14, 10..14)
        val half = success(run(db, ms = 512 * 512))
        val full = success(run(db, mw = 1024, mh = 1024))
        assertEquals(1, half.regions.size)
        assertEquals(half.width, full.width)
        assertEquals(half.height, full.height)
        assertEquals(half.regions, full.regions)
    }

    @Test
    fun `native stroke mask uses strict point twelve threshold with exact grid and padded input mapping`() {
        val db = grid()
        fill(db, 32, 10..14, 10..14)
        val plan = DbnetResizePlan.create(1000, 333) // 1024x341, then padded to 1024x512
        val probabilities = floatArrayOf(.1199f, .12f, .1201f, .13f)
        val result = success(runWithMask(db, 32, 32, 2, 4, 1, 1, probabilities, plan))
        assertEquals(4, result.mask.width)
        assertEquals(1, result.mask.height)
        assertEquals(1024, result.mask.inputWidth)
        assertEquals(512, result.mask.inputHeight)
        assertEquals(1.024f, result.mask.ratio)
        assertEquals(byteArrayOf(0b0000_1100).toList(), result.mask.bytes.toList())
    }

    @Test
    fun `nonfinite or non probability native mask fails without a partial result`() {
        val db = grid()
        fill(db, 32, 10..14, 10..14)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f, 1.01f)) {
            assertTrue(runWithMask(db, 32, 32, 2, 2, 2, 1, floatArrayOf(0f, 0f, 0f, value), DbnetResizePlan.create(32, 32)) is DetectionResult.Failure)
        }
    }

    @Test
    fun `only channel zero contributes geometry`() {
        val db = grid()
        for (i in 1024 until db.size) db[i] = 100f
        assertTrue(success(run(db)).regions.isEmpty())
        fill(db, 32, 10..14, 10..14)
        assertEquals(1, success(run(db)).regions.size)
    }

    @Test
    fun `nonfinite channel zero logits fail without partial regions`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val db = grid()
            fill(db, 32, 1..4, 1..4)
            db[1023] = value
            assertTrue(run(db) is DetectionResult.Failure)
        }
    }

    @Test
    fun `invalid output metadata and truncated buffers fail explicitly`() {
        val db = grid()
        val bad = listOf(
            run(db, w = 0), run(db, h = -1), run(db, w = Int.MAX_VALUE, h = Int.MAX_VALUE),
            run(db, w = 1025, h = 1), run(db, channels = 1), run(db, channels = 3),
            run(FloatArray(2047)), run(FloatArray(2 * 1024 * 1024 + 1)),
            run(db, mw = 0), run(db, mh = -1), run(db, mw = 1025, mh = 1),
            run(db, mw = Int.MAX_VALUE, mh = Int.MAX_VALUE), run(db, mc = 2),
            run(db, ms = 512 * 512 - 1), run(db, ms = 1024 * 1024 + 1),
        )
        bad.forEach { assertTrue(it is DetectionResult.Failure, it.toString()) }
    }

    @Test
    fun `larger capacity DB buffer reads only actual channel zero grid`() {
        val db = FloatArray(2 * 1024 * 1024) { Float.NaN }
        for (i in 0 until 2048) db[i] = -100f
        fill(db, 32, 10..14, 10..14)
        assertEquals(1, success(run(db)).regions.size)
    }

    @Test
    fun `region limit accepts 2048 but returns failure at 2049 instead of truncation`() {
        val db = grid(512, 512)
        for (i in 0 until 2048) fill(db, 512, (i % 64 * 6)..(i % 64 * 6 + 3), (i / 64 * 6)..(i / 64 * 6 + 3))
        val plan = DbnetResizePlan.create(512, 512)
        assertEquals(2048, success(run(db, w = 512, h = 512, plan = plan)).regions.size)
        fill(db, 512, 0..3, 192..195)
        val overLimit = run(db, w = 512, h = 512, plan = plan)
        assertTrue(overLimit is DetectionResult.Failure)
        assertTrue((overLimit as DetectionResult.Failure).reason.contains("2048"))
    }
}
