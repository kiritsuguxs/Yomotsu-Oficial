package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryViewMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * Novels have no library yet. This keeps the consolidated library switchable to
 * Novels without crashing, and shows an explanation instead of an empty list.
 */
@Composable
fun NovelsLibraryPanel(
    libraryMode: LibraryViewMode? = null,
    showModeDropdown: Boolean = false,
    onToggleDropdown: () -> Unit = {},
    onDismissDropdown: () -> Unit = {},
    onModeSelected: (LibraryViewMode) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyScreen(
            stringRes = MR.strings.information_no_manga_category,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
