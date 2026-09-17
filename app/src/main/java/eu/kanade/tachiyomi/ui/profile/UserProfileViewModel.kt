package eu.kanade.tachiyomi.ui.profile

import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.profile.*
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.Injekt

sealed interface UserProfileState {
    data object Loading : UserProfileState
    data class Success(
        val username: String,
        val totalXp: Long,
        val totalChaptersRead: Int,
        val totalMangas: Int,
        val unlockedAchievements: List<YomotsuAchievement>,
        val lockedAchievements: List<YomotsuAchievement>,
        val equippedTitle: YomotsuTitle,
        val unlockedTitles: List<YomotsuTitle>,
        val avatarUri: String?,
        val bannerUri: String?
    ) : UserProfileState
}

class UserProfileViewModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val profilePreferences: ProfilePreferences = ProfilePreferences()
) : StateViewModel<UserProfileState>(UserProfileState.Loading) {

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launchIO {
            val libraryManga = getLibraryManga.await()
            val distinctLibraryManga = libraryManga.fastDistinctBy { it.id }

            val totalMangas = distinctLibraryManga.size
            val readChapterCount = distinctLibraryManga.sumOf { it.readCount }.toInt()
            val downloadCount = downloadManager.getDownloadCount()

            val totalXp = (readChapterCount * YomotsuLevelManager.XP_PER_CHAPTER_READ.toLong()) +
                          (downloadCount * YomotsuLevelManager.XP_PER_CHAPTER_DOWNLOAD.toLong())

            val currentLevel = YomotsuLevelManager.calculateLevelFromXp(totalXp)
            val unlockedTitles = YomotsuLevelManager.getUnlockedTitles(currentLevel)

            val savedTitleId = profilePreferences.getEquippedTitleId()
            val equippedTitle = unlockedTitles.find { it.name == savedTitleId } ?: unlockedTitles.firstOrNull() ?: YomotsuLevelManager.ALL_TITLES.first()

            val unlocked = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filter {
                it.isUnlocked(readChapterCount, totalMangas, downloadCount)
            }
            val locked = YomotsuAchievementManager.ALL_ACHIEVEMENTS.filterNot {
                it.isUnlocked(readChapterCount, totalMangas, downloadCount)
            }

            mutableState.update {
                UserProfileState.Success(
                    username = profilePreferences.getUsername(),
                    totalXp = totalXp,
                    totalChaptersRead = readChapterCount,
                    totalMangas = totalMangas,
                    unlockedAchievements = unlocked,
                    lockedAchievements = locked,
                    equippedTitle = equippedTitle,
                    unlockedTitles = unlockedTitles,
                    avatarUri = profilePreferences.getAvatarUri(),
                    bannerUri = profilePreferences.getBannerUri()
                )
            }
        }
    }

    fun setEquippedTitle(title: YomotsuTitle) {
        profilePreferences.setEquippedTitleId(title.name)
        loadProfile()
    }

    fun setAvatarUri(uri: String?) {
        profilePreferences.setAvatarUri(uri)
        loadProfile()
    }

    fun setUsername(name: String) {
        profilePreferences.setUsername(name)
        loadProfile()
    }

    fun setBannerUri(uri: String?) {
        profilePreferences.setBannerUri(uri)
        loadProfile()
    }
}
