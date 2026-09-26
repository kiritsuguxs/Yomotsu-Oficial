package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.library.LibraryViewMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The anime library is no longer a bottom bar tab. It lives inside the
 * consolidated Library tab (see LibraryTab) and is reached by switching the
 * view mode dropdown to Anime. This object only remains so the search entry
 * points used by AnimeScreen keep working.
 */
data object AnimeLibraryTab {

    private val queryEvent = Channel<String>()

    fun queryFlow() = queryEvent.receiveAsFlow()

    suspend fun search(query: String) = queryEvent.send(query)
}

@Composable
fun animeLibraryTabContent() {
    AnimeLibraryPanel(
        screenModel = AnimeLibraryScreenModel(),
        settingsScreenModel = AnimeLibrarySettingsScreenModel(),
        libraryMode = LibraryViewMode.Anime,
        onModeSelected = {},
    )
}
