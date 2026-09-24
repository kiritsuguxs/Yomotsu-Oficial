package eu.kanade.translation.detection

/**
 * Configuration for text removal / inpainting in manga/comic pages.
 *
 * Direct port of Yakuyomi-engine inpainter configuration:
 * - [method]: "boxfill" (fast flat color fill, zero inference overhead) or "aot" (NCNN AOT-GAN neural inpainting).
 * - [tileSize]: Resolution for whole-page AOT-GAN reconstruction (768 is the sweet spot for visual quality,
 *   memory consumption, and inference latency).
 * - [maskDilate]: Dilation diameter for text mask (radius = maskDilate / 2, default 24px -> 12px radius)
 *   to eliminate text outline strokes and font anti-aliasing halo artifacts.
 * - [bboxPad]: Padding applied to bounding boxes when building allowed region masks.
 */
data class InpainterConfig(
    val method: String = METHOD_AOT,
    val tileSize: Int = 768,
    val maskDilate: Float = 24f,
    val bboxPad: Int = 16,
) {
    companion object {
        const val METHOD_BOXFILL = "boxfill"
        const val METHOD_AOT = "aot"
    }
}
