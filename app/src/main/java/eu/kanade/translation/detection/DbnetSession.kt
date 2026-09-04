package eu.kanade.translation.detection

interface DbnetBackend {
    fun create(param: String, bin: String): Long
    fun infer(
        handle: Long,
        input: FloatArray,
        width: Int,
        height: Int,
        db: FloatArray,
        mask: FloatArray,
        dimensions: IntArray,
    ): Int
    fun release(handle: Long)
}
data class DbnetTensors(val db: FloatArray, val mask: FloatArray, val dimensions: IntArray)
class DbnetSession(private val backend: DbnetBackend, param: String, bin: String) : AutoCloseable {
    private var handle: Long = backend.create(param, bin).also { check(it != 0L) { "Falha ao carregar modelo NCNN" } }

    @Synchronized
    fun infer(input: FloatArray, width: Int, height: Int): DbnetTensors {
        check(handle != 0L) { "Sessão DBNet encerrada" }
        require(width in 256..1024 && height in 256..1024 && width % 256 == 0 && height % 256 == 0)
        val area = width * height
        require(input.size == 3 * area)
        val result = DbnetTensors(FloatArray(2 * area), FloatArray(area), IntArray(6))
        val status = backend.infer(handle, input, width, height, result.db, result.mask, result.dimensions)
        check(status == 0) { "NCNN falhou (código $status)" }
        return result
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            val owned = handle
            handle = 0L
            backend.release(owned)
        }
    }
}
