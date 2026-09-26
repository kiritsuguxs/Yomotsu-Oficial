package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.LibraryViewMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

fun labelFor(mode: LibraryViewMode) = when (mode) {
    LibraryViewMode.Manga -> MR.strings.manga
    LibraryViewMode.Anime -> AYMR.strings.label_anime
    LibraryViewMode.Novels -> MR.strings.label_novels
}

/**
 * Title with a dropdown to switch between the consolidated library modes
 * (manga / anime / novels). Mirrors the behaviour of the Chimahon library.
 */
@Composable
fun LibraryViewModeDropdown(
    current: LibraryViewMode,
    onSelected: (LibraryViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.clickable { expanded = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelFor(current)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.width(4.dp))
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(labelFor(mode)),
                            fontWeight = if (mode == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                )
            }
        }
    }
}
