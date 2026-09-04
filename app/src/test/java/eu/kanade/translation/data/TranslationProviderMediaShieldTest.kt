package eu.kanade.translation.data

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.storage.DiskUtil
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.domain.storage.service.StorageManager

class TranslationProviderMediaShieldTest {

    @AfterEach
    fun tearDown() {
        unmockkObject(DiskUtil)
    }

    @Test
    fun `translation directory is hidden from gallery before OCR files are created`() {
        val context = mockk<Context>()
        val storageManager = mockk<StorageManager>()
        val root = mockk<UniFile>()
        val sourceDirectory = mockk<UniFile>()
        val mangaDirectory = mockk<UniFile>()
        val source = mockk<Source>()

        every { storageManager.getTranslationsDirectory() } returns root
        every { source.toString() } returns "Test Source"
        every { root.createDirectory("Test Source") } returns sourceDirectory
        every { sourceDirectory.createDirectory("Test Manga") } returns mangaDirectory
        mockkObject(DiskUtil)
        every { DiskUtil.createNoMediaFile(root, context) } just Runs

        val result = TranslationProvider(context, storageManager)
            .getMangaDir("Test Manga", source)

        assertSame(mangaDirectory, result)
        verify(exactly = 1) { DiskUtil.createNoMediaFile(root, context) }
    }
}
