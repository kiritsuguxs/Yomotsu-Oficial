package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.manga.DownloadAction
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DownloadDropdownMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    offset: DpOffset? = null,
) {
    if (offset != null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            content = {
                DownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                )
            },
        )
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = {
                DownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                )
            },
        )
    }
}

@Composable
fun EntryDownloadDropdownMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (eu.kanade.presentation.entries.DownloadAction) -> Unit,
    isManga: Boolean = false,
    offset: DpOffset? = null,
) {
    val items = listOf(
        eu.kanade.presentation.entries.DownloadAction.NEXT_1_ITEM to if (isManga) {
            pluralStringResource(MR.plurals.download_amount, 1, 1)
        } else {
            pluralStringResource(tachiyomi.i18n.aniyomi.AYMR.plurals.download_amount_anime, 1, 1)
        },
        eu.kanade.presentation.entries.DownloadAction.NEXT_5_ITEMS to if (isManga) {
            pluralStringResource(MR.plurals.download_amount, 5, 5)
        } else {
            pluralStringResource(tachiyomi.i18n.aniyomi.AYMR.plurals.download_amount_anime, 5, 5)
        },
        eu.kanade.presentation.entries.DownloadAction.NEXT_10_ITEMS to if (isManga) {
            pluralStringResource(MR.plurals.download_amount, 10, 10)
        } else {
            pluralStringResource(tachiyomi.i18n.aniyomi.AYMR.plurals.download_amount_anime, 10, 10)
        },
        eu.kanade.presentation.entries.DownloadAction.NEXT_25_ITEMS to if (isManga) {
            pluralStringResource(MR.plurals.download_amount, 25, 25)
        } else {
            pluralStringResource(tachiyomi.i18n.aniyomi.AYMR.plurals.download_amount_anime, 25, 25)
        },
        eu.kanade.presentation.entries.DownloadAction.UNVIEWED_ITEMS to if (isManga) {
            stringResource(MR.strings.download_unread)
        } else {
            stringResource(tachiyomi.i18n.aniyomi.AYMR.strings.download_unseen)
        },
    )

    val content: @Composable () -> Unit = {
        items.forEach { (downloadAction, string) ->
            DropdownMenuItem(
                text = { Text(text = string) },
                onClick = {
                    onDownloadClicked(downloadAction)
                    onDismissRequest()
                },
            )
        }
    }

    if (offset != null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            content = content,
        )
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    }
}


@Composable
private fun DownloadDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
) {
    val options = listOf(
        DownloadAction.NEXT_1_CHAPTER to pluralStringResource(MR.plurals.download_amount, 1, 1),
        DownloadAction.NEXT_5_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 5, 5),
        DownloadAction.NEXT_10_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 10, 10),
        DownloadAction.NEXT_25_CHAPTERS to pluralStringResource(MR.plurals.download_amount, 25, 25),
        DownloadAction.UNREAD_CHAPTERS to stringResource(MR.strings.download_unread),
        DownloadAction.BOOKMARKED_CHAPTERS to stringResource(MR.strings.download_bookmarked),
    )

    options.map { (downloadAction, string) ->
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onDownloadClicked(downloadAction)
                onDismissRequest()
            },
        )
    }
}
