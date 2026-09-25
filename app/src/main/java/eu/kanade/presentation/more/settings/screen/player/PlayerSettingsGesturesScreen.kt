package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.player.controls.components.dialogs.IntegerPickerDialog
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

object PlayerSettingsGesturesScreen {
    @Composable
    fun SkipIntroLengthDialog(
        initialSkipIntroLength: Int,
        onDismissRequest: () -> Unit,
        onValueChanged: (Int) -> Unit,
    ) {
        IntegerPickerDialog(
            defaultValue = initialSkipIntroLength,
            minValue = 0,
            maxValue = 255,
            step = 1,
            nameFormat = "%d s",
            title = stringResource(AYMR.strings.pref_intro_length),
            onChange = onValueChanged,
            onDismissRequest = onDismissRequest,
        )
    }

    @Composable
    fun SkipIntroLengthDialog(
        initialSkipIntroLength: Long,
        onDismissRequest: () -> Unit,
        onValueChanged: (Int) -> Unit,
    ) {
        SkipIntroLengthDialog(
            initialSkipIntroLength = initialSkipIntroLength.toInt(),
            onDismissRequest = onDismissRequest,
            onValueChanged = onValueChanged,
        )
    }
}
