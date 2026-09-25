package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.entries.anime.model.Anime
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Class used to create anime background cache.
 */
class AnimeBackgroundCache(private val context: Context) {

    companion object {
        private const val BACKGROUNDS_DIR = "animebackgrounds"
        private const val CUSTOM_BACKGROUNDS_DIR = "animebackgrounds/custom"
    }

    private val cacheDir = getCacheDir(BACKGROUNDS_DIR)
    private val customBackgroundCacheDir = getCacheDir(CUSTOM_BACKGROUNDS_DIR)

    fun getBackgroundFile(animeThumbnailUrl: String?): File? {
        return animeThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    fun getCustomBackgroundFile(animeId: Long?): File {
        return File(customBackgroundCacheDir, DiskUtil.hashKeyForDisk(animeId.toString()))
    }

    @Throws(IOException::class)
    fun setCustomBackgroundToCache(anime: Anime, inputStream: InputStream) {
        setCustomBackgroundToCache(anime.id, inputStream)
    }

    @Throws(IOException::class)
    fun setCustomBackgroundToCache(animeId: Long, inputStream: InputStream) {
        getCustomBackgroundFile(animeId).outputStream().use {
            inputStream.copyTo(it)
        }
    }

    fun deleteFromCache(anime: Anime, deleteCustomBackground: Boolean = false): Int {
        var deleted = 0
        getBackgroundFile(anime.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }
        if (deleteCustomBackground) {
            if (deleteCustomBackground(anime.id)) ++deleted
        }
        return deleted
    }

    fun deleteCustomBackground(animeId: Long?): Boolean {
        return getCustomBackgroundFile(animeId).let {
            it.exists() && it.delete()
        }
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
