package eu.kanade.tachiyomi.ui.profile

import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.profile.YomotsuLevelManager
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
        val totalMangas: Int
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

            // O Cálculo Retroativo: Lidos + Baixados
            val totalXp = (readChapterCount * YomotsuLevelManager.XP_PER_CHAPTER_READ.toLong()) +
                          (downloadCount * YomotsuLevelManager.XP_PER_CHAPTER_DOWNLOAD.toLong())

            mutableState.update {
                UserProfileState.Success(
                    totalXp = totalXp,
                    totalChaptersRead = readChapterCount,
                    totalMangas = totalMangas
                )
            }
        }
    }
}
