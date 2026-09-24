package eu.kanade.presentation.more.settings.screen.player.custombutton

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.custombuttons.model.CustomButton

sealed interface CustomButtonFetchState {
    @Immutable
    data object Loading : CustomButtonFetchState

    @Immutable
    data class Success(val customButtons: ImmutableList<CustomButton>) : CustomButtonFetchState

    @Immutable
    data class Error(val errorMessage: String) : CustomButtonFetchState
}

fun CustomButtonFetchState.getButtons(): ImmutableList<CustomButton> {
    return when (this) {
        is CustomButtonFetchState.Success -> this.customButtons
        else -> emptyList<CustomButton>().toImmutableList()
    }
}
