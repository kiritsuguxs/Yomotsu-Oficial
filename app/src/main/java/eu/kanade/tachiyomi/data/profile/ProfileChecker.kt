package eu.kanade.tachiyomi.data.profile

import android.content.Context
import androidx.compose.ui.util.fastDistinctBy
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object ProfileChecker {

    suspend fun checkAchievements(
        context: Context,
        getLibraryManga: GetLibraryManga = Injekt.get(),
        downloadManager: DownloadManager = Injekt.get()
    ) {
        val prefs = context.getSharedPreferences("yomotsu_profile_prefs", Context.MODE_PRIVATE)
        val notifiedSet = prefs.getStringSet("notified_achievements", emptySet()) ?: emptySet()

        val libraryManga = getLibraryManga.await()
        val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

        val totalMangas = distinctLibraryManga.size
        val readChapterCount = distinctLibraryManga.sumOf { it.readCount }.toInt()
        val downloadCount = downloadManager.getDownloadCount()

        val unlockedNow = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filter {
            it.isUnlocked(readChapterCount, totalMangas, downloadCount)
        }

        val newlyUnlocked = unlockedNow.filter { it.id !in notifiedSet }

        if (newlyUnlocked.isNotEmpty()) {
            val notifier = ProfileNotifier(context)
            val updatedSet = notifiedSet.toMutableSet()
            
            for (achievement in newlyUnlocked) {
                notifier.showAchievementUnlocked(achievement)
                updatedSet.add(achievement.id)
            }
            
            prefs.edit().putStringSet("notified_achievements", updatedSet).apply()
        }
    }
}
