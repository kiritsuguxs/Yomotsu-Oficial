package eu.kanade.translation.detection

/** Accessed only by DbnetService in :dbnet; never initialize this in the reader process. */
internal object DbnetNativeBackend : DbnetBackend {
    init {
        System.loadLibrary("yomotsu_dbnet")
    }

    private external fun createNative(param: String, bin: String): Long
    private external fun releaseNative(handle: Long)
    private external fun inferNative(
        handle: Long,
        input: FloatArray,
        width: Int,
        height: Int,
        db: FloatArray,
        mask: FloatArray,
        dimensions: IntArray,
    ): Int

    override fun create(param: String, bin: String) = createNative(param, bin)
    override fun release(handle: Long) = releaseNative(handle)
    override fun infer(
        handle: Long,
        input: FloatArray,
        width: Int,
        height: Int,
        db: FloatArray,
        mask: FloatArray,
        dimensions: IntArray,
    ) =
        inferNative(handle, input, width, height, db, mask, dimensions)
}
