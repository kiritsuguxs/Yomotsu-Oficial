package eu.kanade.tachiyomi.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.profile.UserProfileScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.screens.LoadingScreen

class YomotsuProfileScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<UserProfileViewModel>()
        val state by viewModel.state.collectAsState()

        when (val currentState = state) {
            is UserProfileState.Loading -> {
                LoadingScreen()
            }
            is UserProfileState.Success -> {
                UserProfileScreen(
                    navigateUp = { navigator.pop() },
                    totalXp = currentState.totalXp,
                    totalChaptersRead = currentState.totalChaptersRead,
                    totalMangas = currentState.totalMangas
                )
            }
        }
    }
}
