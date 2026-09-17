package eu.kanade.tachiyomi.ui.profile

import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.profile.YomotsuLevelManager
import eu.kanade.tachiyomi.data.profile.YomotsuAchievementManager
import eu.kanade.tachiyomi.data.profile.YomotsuAchievement
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed interface UserProfileState {
    data object Loading : UserProfileState
    data class Success(
        val totalXp: Long,
        val totalChaptersRead: Int,
        val totalMangas: Int,
        val unlockedAchievements: List<YomotsuAchievement>,
        val lockedAchievements: List<YomotsuAchievement>
    ) : UserProfileState
}

class UserProfileViewModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
) : StateViewModel<UserProfileState>(UserProfileState.Loading) {

    init {
        viewModelScope.launchIO {
            val libraryManga = getLibraryManga.await()
            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

            val totalMangas = distinctLibraryManga.size
            val readChapterCount = distinctLibraryManga.sumOf { it.readCount }.toInt()
            val downloadCount = downloadManager.getDownloadCount()

            val totalXp = (readChapterCount * YomotsuLevelManager.XP_PER_CHAPTER_READ.toLong()) +
                          (downloadCount * YomotsuLevelManager.XP_PER_CHAPTER_DOWNLOAD.toLong())

            val unlocked = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filter { 
                it.isUnlocked(readChapterCount, totalMangas, downloadCount) 
            }
            val locked = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filterNot { 
                it.isUnlocked(readChapterCount, totalMangas, downloadCount) 
            }

            mutableState.update {
                UserProfileState.Success(
                    totalXp = totalXp,
                    totalChaptersRead = readChapterCount,
                    totalMangas = totalMangas,
                    unlockedAchievements = unlocked,
                    lockedAchievements = locked
                )
            }
        }
    }
}
