package eu.kanade.translation.detection

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class DbnetModelAsset(val name: String, val url: String, val size: Long, val sha256: String)

/** Detector-only distribution pins from Houri-engine 85351aa/models.json (GPL-3.0 weights).
 * See docs/yakuyomi-dbnet-upstream.md. No weights are packaged with the app.
 */
class DbnetModelStore(
    private val directory: File,
    private val assets: List<DbnetModelAsset> = ASSETS,
    private val openStream: (String) -> InputStream,
) {
    fun ensureAvailable(
        checkCancelled: () -> Unit = {},
        onDownloadRequired: () -> Unit = {},
    ): File = synchronized(downloadLock) {
        require(assets.isNotEmpty())
        assets.forEach {
            require(it.name.matches(Regex("[a-zA-Z0-9_.-]+")) && it.name != "." && it.name != "..")
            require(it.size > 0 && it.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Não foi possível criar a pasta DBNet")
        var downloadReported = false
        for (asset in assets) {
            checkCancelled()
            val target = File(directory, asset.name)
            if (target.isFile && target.length() == asset.size &&
                target.inputStream().use { digest(it, asset.size, checkCancelled) } == asset.sha256
            ) {
                continue
            }
            val partial = File.createTempFile(asset.name, ".part", directory)
            try {
                if (!downloadReported) {
                    onDownloadRequired()
                    downloadReported = true
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                openStream(asset.url).use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            checkCancelled()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            count += read
                            if (count > asset.size) throw IOException("Modelo DBNet maior que o tamanho esperado")
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                        output.fd.sync()
                    }
                }
                checkCancelled()
                if (count != asset.size || digest.digest().hex() != asset.sha256) {
                    throw IOException("Falha na verificação SHA-256/tamanho do modelo DBNet")
                }
                Files.move(
                    partial.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                partial.delete()
            }
        }
        directory
    }

    private fun digest(input: InputStream, expectedSize: Long, checkCancelled: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var count = 0L
        while (true) {
            checkCancelled()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            count += read
            if (count > expectedSize) return ""
            digest.update(buffer, 0, read)
        }
        return if (count == expectedSize) digest.digest().hex() else ""
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private val downloadLock = Any()
        val ASSETS = listOf(
            DbnetModelAsset(
                "dbnet_detect.ncnn.param",
                "https://github.com/joyeli/yakuyomi-engine/releases/download/models-v3/dbnet_detect.ncnn.param",
                13_392L,
                "9e6db2f8c6b0662ab00eb2100b3373d3c984a235eaac0e61c0b2a484ee1ff7b5",
            ),
            DbnetModelAsset(
                "dbnet_detect.ncnn.bin",
                "https://github.com/joyeli/yakuyomi-engine/releases/download/models-v3/dbnet_detect.ncnn.bin",
                153_010_556L,
                "f57bdbede7764a534c56e88be0269602259a7fcd47e54e8b7d954fd0fcc55c3d",
            ),
        )
    }
}
