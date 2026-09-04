// RGB CHW normalization adapted from Houri-engine ImageOps.kt @85351aa.
// SPDX-License-Identifier: GPL-3.0-only
package eu.kanade.translation.detection

object DbnetPixels {
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        require(width > 0 && height > 0 && maxSide > 0)
        var sample = 1
        while (maxOf(width, height).toLong() > maxSide.toLong() * sample) sample *= 2
        return sample
    }

    fun toChw(pixels: IntArray): FloatArray {
        require(pixels.size in 1..1_048_576)
        val area = pixels.size
        return FloatArray(3 * area).also { output ->
            for (i in pixels.indices) {
                val pixel = pixels[i]
                output[i] = ((pixel shr 16) and 255) / 127.5f - 1f
                output[area + i] = ((pixel shr 8) and 255) / 127.5f - 1f
                output[2 * area + i] = (pixel and 255) / 127.5f - 1f
            }
        }
    }
}
