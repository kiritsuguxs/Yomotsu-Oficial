package eu.kanade.translation.detection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.model.TranslationRegion
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Text removal and inpainting engine ported and adapted from yakuyomi-engine.
 *
 * Supports two operational modes:
 * - **boxfill (Fast cleanup)**: Flat-fills text mask regions with the estimated background color.
 *   Instantaneous, zero ML overhead, cleanest for solid-color speech bubbles.
 * - **aot (Neural Inpainting)**: AOT-GAN neural background reconstruction using NCNN.
 *   Scales page to [InpainterConfig.tileSize] (default 768), infers inpaint field,
 *   and composites reconstructed artwork strictly inside the masked text region.
 */
class Inpainter(
    modelPath: String? = null,
    val cfg: InpainterConfig = InpainterConfig(),
    private val backend: DbnetBackend = DbnetNativeBackend,
) : AutoCloseable {

    private var handle: Long = 0L

    val isModelLoaded: Boolean get() = handle != 0L
    val executionProvider: String get() = if (handle != 0L) "NCNN-CPU" else "BOXFILL"

    init {
        if (!modelPath.isNullOrBlank() && cfg.method == InpainterConfig.METHOD_AOT) {
            check(modelPath.endsWith(".param")) { "AOT Inpainter requires an NCNN .param model: $modelPath" }
            val bin = modelPath.removeSuffix(".param") + ".bin"
            handle = backend.create(modelPath, bin)
            if (handle == 0L) {
                Log.w(TAG, "Failed to initialize native NCNN AOT model from $modelPath; falling back to boxfill")
            }
        }
    }

    /**
     * Inpaints / removes text from [page] using the provided [regions] and [textMask].
     * Returns a new ARGB_8888 bitmap with text removed and background reconstructed.
     */
    suspend fun inpaint(
        page: Bitmap,
        regions: List<TranslationRegion>,
        textMask: Bitmap,
    ): Bitmap = coroutineScope {
        val w = page.width
        val h = page.height
        val result = page.copy(Bitmap.Config.ARGB_8888, true)
        val maskPx = buildSegMask(regions, textMask, w, h)

        if (cfg.method == InpainterConfig.METHOD_BOXFILL || handle == 0L) {
            val px = IntArray(w * h)
            result.getPixels(px, 0, w, 0, 0, w, h)
            val tightPx = IntArray(w * h)
            textMask.getPixels(tightPx, 0, w, 0, 0, w, h)
            for (r in regions) {
                val s = bgStats(px, tightPx, r, w, h)
                flatFill(result, maskPx, r, s.color, cfg.bboxPad, w, h)
            }
            return@coroutineScope result
        }

        // AOT-GAN neural inpainting
        val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        maskBmp.setPixels(maskPx, 0, w, 0, 0, w, h)
        runWholeAot(page, maskBmp, w, h)?.let { winOut ->
            compositePixels(result, maskPx, winOut)
        }
        maskBmp.recycle()
        result
    }

    /**
     * Convenience method to inpaint all translatable [blocks] on a [page].
     * Derives [textMask] from [DbnetCleanupMask] when present, or bounding boxes.
     */
    suspend fun inpaintBlocks(
        page: Bitmap,
        blocks: List<TranslationBlock>,
    ): Bitmap {
        val w = page.width
        val h = page.height
        val regions = blocks.map { b ->
            b.cleanupRegion ?: TranslationRegion(b.x, b.y, b.width, b.height)
        }
        val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBmp)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

        var hasDbnetMask = false
        for (block in blocks) {
            val dbnetMask = block.dbnetCleanupMask
            if (dbnetMask != null && !dbnetMask.isEmpty) {
                hasDbnetMask = true
                dbnetMask.forEachRun(w, h) { y, x, length ->
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + length).toFloat(), (y + 1).toFloat(), paint)
                }
            }
        }
        if (!hasDbnetMask) {
            for (r in regions) {
                canvas.drawRect(r.x, r.y, r.x + r.width, r.y + r.height, paint)
            }
        }

        return try {
            inpaint(page, regions, maskBmp)
        } finally {
            maskBmp.recycle()
        }
    }

    /**
     * Warm-up pass on a small 64x64 canvas to initialize single-threaded runtime caches.
     */
    fun warmUp() {
        if (cfg.method == InpainterConfig.METHOD_BOXFILL || handle == 0L) return
        val w = 64
        val h = 64
        val page = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            runWholeAot(page, mask, w, h)
        } catch (t: Throwable) {
            Log.w(TAG, "Inpainter warm-up failed: ${t.message}")
        } finally {
            page.recycle()
            mask.recycle()
        }
    }

    private fun buildSegMask(regions: List<TranslationRegion>, textMask: Bitmap, w: Int, h: Int): IntArray {
        val pad = cfg.bboxPad
        val allow = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(allow).apply {
            drawColor(Color.BLACK)
            val p = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            for (r in regions) {
                drawRect(r.x - pad, r.y - pad, r.x + r.width + pad, r.y + r.height + pad, p)
            }
        }
        val mask = IntArray(w * h)
        allow.getPixels(mask, 0, w, 0, 0, w, h)
        allow.recycle()

        val seg = IntArray(w * h)
        textMask.getPixels(seg, 0, w, 0, 0, w, h)
        for (i in mask.indices) {
            mask[i] = if ((mask[i] and 0xFF) > 127 && (seg[i] and 0xFF) > 127) Color.WHITE else Color.BLACK
        }
        dilate(mask, w, h, (cfg.maskDilate / 2f).roundToInt().coerceAtLeast(1))
        return mask
    }

    private class BgStat(val meanLum: Float, val std: Float, val color: Int)

    private fun bgStats(px: IntArray, tightPx: IntArray, region: TranslationRegion, w: Int, h: Int): BgStat {
        val x0 = region.x.toInt().coerceIn(0, w - 1)
        val y0 = region.y.toInt().coerceIn(0, h - 1)
        val x1 = (region.x + region.width).toInt().coerceIn(x0 + 1, w)
        val y1 = (region.y + region.height).toInt().coerceIn(y0 + 1, h)
        val bw = x1 - x0
        val bh = y1 - y0
        var n = 0
        var sl = 0.0
        var sl2 = 0.0
        var sr = 0L
        var sg = 0L
        var sb = 0L
        for (y in y0 until y1) {
            val row = y * w
            for (x in x0 until x1) {
                val gi = row + x
                if ((tightPx[gi] and 0xFF) > 127) continue
                val p = px[gi]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                sl += lum
                sl2 += lum * lum
                sr += r
                sg += g
                sb += b
                n++
            }
        }
        if (n < 16) return BgStat(255f, 0f, Color.WHITE)
        val mean = sl / n
        val std = sqrt((sl2 / n - mean * mean).coerceAtLeast(0.0))
        return BgStat(
            mean.toFloat(),
            std.toFloat(),
            Color.rgb((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt()),
        )
    }

    private fun flatFill(result: Bitmap, maskPx: IntArray, region: TranslationRegion, color: Int, pad: Int, w: Int, h: Int) {
        val x0 = (region.x.toInt() - pad).coerceIn(0, w - 1)
        val y0 = (region.y.toInt() - pad).coerceIn(0, h - 1)
        val x1 = ((region.x + region.width).toInt() + pad).coerceIn(x0 + 1, w)
        val y1 = ((region.y + region.height).toInt() + pad).coerceIn(y0 + 1, h)
        val bw = x1 - x0
        val bh = y1 - y0
        val sub = IntArray(bw * bh)
        result.getPixels(sub, 0, bw, x0, y0, bw, bh)
        for (y in 0 until bh) {
            val mrow = (y0 + y) * w + x0
            val row = y * bw
            for (x in 0 until bw) {
                if ((maskPx[mrow + x] and 0xFF) > 127) {
                    sub[row + x] = color
                }
            }
        }
        result.setPixels(sub, 0, bw, x0, y0, bw, bh)
    }

    private class WinOut(val x0: Int, val y0: Int, val ww: Int, val wh: Int, val px: IntArray)

    private fun runWholeAot(page: Bitmap, maskBmp: Bitmap, w: Int, h: Int): WinOut? {
        if (handle == 0L) return null
        val t = cfg.tileSize
        val imgScaled = Bitmap.createScaledBitmap(page, t, t, true)
        val maskScaled = Bitmap.createScaledBitmap(maskBmp, t, t, false)
        return try {
            val px = IntArray(t * t)
            imgScaled.getPixels(px, 0, t, 0, 0, t, t)
            val mp = IntArray(t * t)
            maskScaled.getPixels(mp, 0, t, 0, 0, t, t)

            val imgChw = aotImageChwFromPixels(px, mp, t)
            val maskArr = maskArrFromPixels(mp, t)
            val out = FloatArray(3 * t * t)

            val rc = backend.inpaintAot(handle, imgChw, maskArr, t, out)
            check(rc == 0) { "NCNN AOT inference failed with status code $rc" }

            val resScaledPixels = aotArrToPixels(out, t)
            val resScaled = Bitmap.createBitmap(resScaledPixels, t, t, Bitmap.Config.ARGB_8888)
            val resWin = Bitmap.createScaledBitmap(resScaled, w, h, true)
            val outPx = IntArray(w * h)
            resWin.getPixels(outPx, 0, w, 0, 0, w, h)
            if (resWin !== resScaled) resScaled.recycle()
            resWin.recycle()
            WinOut(0, 0, w, h, outPx)
        } catch (t2: Throwable) {
            Log.w(TAG, "AOT neural inpainting failed: ${t2.message}")
            null
        } finally {
            if (imgScaled !== page) imgScaled.recycle()
            if (maskScaled !== maskBmp) maskScaled.recycle()
        }
    }

    private fun compositePixels(result: Bitmap, maskPx: IntArray, o: WinOut) {
        val w = result.width
        val cur = IntArray(o.ww * o.wh)
        result.getPixels(cur, 0, o.ww, o.x0, o.y0, o.ww, o.wh)
        for (y in 0 until o.wh) {
            val maskRow = (o.y0 + y) * w + o.x0
            val row = y * o.ww
            for (x in 0 until o.ww) {
                if ((maskPx[maskRow + x] and 0xFF) > 127) {
                    cur[row + x] = o.px[row + x]
                }
            }
        }
        result.setPixels(cur, 0, o.ww, o.x0, o.y0, o.ww, o.wh)
    }

    override fun close() {
        if (handle != 0L) {
            val owned = handle
            handle = 0L
            backend.release(owned)
        }
    }

    companion object {
        private const val TAG = "Inpainter"

        /**
         * Dilates binary mask [px] with radius [radius] using separable horizontal + vertical filter.
         */
        internal fun dilate(px: IntArray, w: Int, h: Int, radius: Int) {
            if (radius <= 0) return
            val tmp = IntArray(px.size)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var on = false
                    var k = -radius
                    while (k <= radius) {
                        val xx = x + k
                        if (xx in 0 until w && (px[row + xx] and 0xFF) > 127) {
                            on = true
                            break
                        }
                        k++
                    }
                    tmp[row + x] = if (on) Color.WHITE else Color.BLACK
                }
            }
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var on = false
                    var k = -radius
                    while (k <= radius) {
                        val yy = y + k
                        if (yy in 0 until h && (tmp[yy * w + x] and 0xFF) > 127) {
                            on = true
                            break
                        }
                        k++
                    }
                    px[y * w + x] = if (on) Color.WHITE else Color.BLACK
                }
            }
        }

        /**
         * Prepares NCHW float array [3 * n * n]: RGB mapped to [-1, 1], mask hole pixels zeroed out.
         */
        internal fun aotImageChwFromPixels(px: IntArray, mp: IntArray, n: Int): FloatArray {
            val area = n * n
            val chw = FloatArray(3 * area)
            for (i in 0 until area) {
                if ((mp[i] and 0xFF) > 127) continue
                val p = px[i]
                chw[i] = ((p shr 16) and 0xFF) / 127.5f - 1f
                chw[area + i] = ((p shr 8) and 0xFF) / 127.5f - 1f
                chw[2 * area + i] = (p and 0xFF) / 127.5f - 1f
            }
            return chw
        }

        /**
         * Prepares float mask array [n * n]: 1.0f for mask pixel, 0.0f otherwise.
         */
        internal fun maskArrFromPixels(mp: IntArray, n: Int): FloatArray {
            val area = n * n
            val m = FloatArray(area)
            for (i in 0 until area) {
                m[i] = if ((mp[i] and 0xFF) > 127) 1f else 0f
            }
            return m
        }

        /**
         * Reconstructs ARGB_8888 pixel array from AOT output float array [-1, 1].
         */
        internal fun aotArrToPixels(arr: FloatArray, n: Int): IntArray {
            val area = n * n
            val px = IntArray(area)
            for (i in 0 until area) {
                val r = ((arr[i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                val g = ((arr[area + i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                val b = ((arr[2 * area + i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return px
        }
    }
}
