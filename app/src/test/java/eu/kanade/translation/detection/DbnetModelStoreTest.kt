package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class DbnetModelStoreTest {
    @TempDir lateinit var directory: File
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }
    private fun asset() = DbnetModelAsset(
        "dbnet_detect.ncnn.bin",
        "https://example.invalid/model",
        bytes.size.toLong(),
        hash(bytes),
    )
    private fun hash(
        data: ByteArray,
    ) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    @Test fun `downloads exact verified bytes using bounded reads`() {
        var largestRead = 0
        val source = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                largestRead = maxOf(largestRead, len)
                return super.read(b, off, len)
            }
        }
        val result = DbnetModelStore(directory, listOf(asset())) { source }.ensureAvailable()
        assertArrayEquals(bytes, File(result, asset().name).readBytes())
        assertTrue(largestRead in 1..65536)
        assertFalse(directory.listFiles()!!.any { it.name.endsWith(".part") })
    }

    @Test fun `valid existing model causes no network access`() {
        File(directory, asset().name).writeBytes(bytes)
        var downloadNotices = 0
        DbnetModelStore(directory, listOf(asset())) { error("network must not run") }.ensureAvailable(
            onDownloadRequired = { downloadNotices++ },
        )
        assertEquals(0, downloadNotices, "A valid installed model must be checked silently")
    }

    @Test fun `corrupt existing model is replaced after verification`() {
        File(directory, asset().name).writeBytes(ByteArray(bytes.size))
        var opened = 0
        var downloadNotices = 0
        DbnetModelStore(directory, listOf(asset())) {
            opened++
            ByteArrayInputStream(bytes)
        }.ensureAvailable(onDownloadRequired = { downloadNotices++ })
        assertEquals(1, opened)
        assertEquals(1, downloadNotices)
        assertArrayEquals(bytes, File(directory, asset().name).readBytes())
    }

    @Test fun `first installation reports one download even when both assets are absent`() {
        val secondBytes = byteArrayOf(1, 3, 3, 7)
        val secondAsset = DbnetModelAsset(
            "dbnet_detect.ncnn.param",
            "https://example.invalid/param",
            secondBytes.size.toLong(),
            hash(secondBytes),
        )
        var downloadNotices = 0

        DbnetModelStore(directory, listOf(asset(), secondAsset)) { url ->
            ByteArrayInputStream(if (url == secondAsset.url) secondBytes else bytes)
        }.ensureAvailable(onDownloadRequired = { downloadNotices++ })

        assertEquals(1, downloadNotices)
        assertArrayEquals(bytes, File(directory, asset().name).readBytes())
        assertArrayEquals(secondBytes, File(directory, secondAsset.name).readBytes())
    }

    @Test fun `truncated oversized and wrong hash downloads never become model`() {
        for (bad in listOf(bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(1), ByteArray(bytes.size))) {
            val sub = File(directory, "case${bad.size}-${bad[0]}")
            assertThrows(IOException::class.java) {
                DbnetModelStore(sub, listOf(asset())) { ByteArrayInputStream(bad) }.ensureAvailable()
            }
            assertFalse(File(sub, asset().name).exists())
            assertFalse(sub.listFiles().orEmpty().any { it.name.endsWith(".part") })
        }
    }

    @Test fun `interrupted download removes partial and preserves cancellation`() {
        val interrupted = object : InputStream() {
            override fun read(): Int = throw CancellationException("cancelled")
        }
        assertThrows(CancellationException::class.java) {
            DbnetModelStore(directory, listOf(asset())) { interrupted }.ensureAvailable()
        }
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".part") })
        assertFalse(File(directory, asset().name).exists())
    }

    @Test fun `unsafe filenames are rejected before opening stream`() {
        assertThrows(IllegalArgumentException::class.java) {
            DbnetModelStore(directory, listOf(asset().copy(name = "../outside"))) {
                error("must not open")
            }.ensureAvailable()
        }
    }

    @Test fun `pinned detector pair matches reviewed manifest`() {
        assertEquals(153_023_948L, DbnetModelStore.ASSETS.sumOf { it.size })
        assertEquals(2, DbnetModelStore.ASSETS.size)
        assertEquals(
            "f57bdbede7764a534c56e88be0269602259a7fcd47e54e8b7d954fd0fcc55c3d",
            DbnetModelStore.ASSETS.last().sha256,
        )
    }
}
