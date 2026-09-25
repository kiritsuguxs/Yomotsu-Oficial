package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.viewmodel.StateViewModel
import mihon.domain.extension.anime.interactor.AddAnimeExtensionStore
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.anime.interactor.RemoveAnimeExtensionStore
import mihon.domain.extension.anime.interactor.UpdateAnimeExtensionStores
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionStoresViewModel(
    private val getExtensionStores: GetExtensionStores = Injekt.get(),
    private val addExtensionStore: AddExtensionStore = Injekt.get(),
    private val removeExtensionStore: RemoveExtensionStore = Injekt.get(),
    private val updateExtensionStores: UpdateExtensionStores = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),

    private val getAnimeExtensionStores: GetAnimeExtensionStores = Injekt.get(),
    private val addAnimeExtensionStore: AddAnimeExtensionStore = Injekt.get(),
    private val removeAnimeExtensionStore: RemoveAnimeExtensionStore = Injekt.get(),
    private val updateAnimeExtensionStores: UpdateAnimeExtensionStores = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
) : StateViewModel<ExtensionStoreScreenState>(ExtensionStoreScreenState.Loading) {

    private inline fun updateSuccessState(
        func: (ExtensionStoreScreenState.Success) -> ExtensionStoreScreenState.Success,
    ) {
        mutableState.update {
            when (it) {
                ExtensionStoreScreenState.Loading -> it
                is ExtensionStoreScreenState.Success -> func(it)
            }
        }
    }

    init {
        viewModelScope.launchIO {
            combine(
                getExtensionStores.subscribe(),
                getAnimeExtensionStores.subscribe(),
            ) { mangaStores, animeStores ->
                val mappedAnimeStores = animeStores.map {
                    ExtensionStore(
                        indexUrl = it.indexUrl,
                        name = it.name,
                        badgeLabel = if (it.badgeLabel.isBlank() || it.badgeLabel.equals(it.name, ignoreCase = true)) "Anime" else it.badgeLabel,
                        signingKey = it.signingKey,
                        contact = ExtensionStore.Contact(it.contact.website, it.contact.discord),
                        isLegacy = it.isLegacy,
                        extensionListUrl = it.extensionListUrl,
                    )
                }
                (mangaStores + mappedAnimeStores).distinctBy { it.indexUrl }
            }
                .collectLatest { stores ->
                    mutableState.update {
                        when (it) {
                            ExtensionStoreScreenState.Loading -> ExtensionStoreScreenState.Success(stores = stores)
                            is ExtensionStoreScreenState.Success -> it.copy(stores = stores)
                        }
                    }
                }
        }
    }

    /**
     * Creates and adds a new repo to the database (manga, novel or anime).
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        viewModelScope.launch {
            updateSuccessState {
                it.copy(
                    dialog = when (it.dialog) {
                        is ExtensionStoreDialog.Create -> it.dialog.copy(processing = true)
                        is ExtensionStoreDialog.Confirm -> it.dialog.copy(processing = true)
                        else -> it.dialog
                    },
                )
            }

            val isAnimeHint = baseUrl.contains("anime", ignoreCase = true) ||
                baseUrl.contains("aniyomi", ignoreCase = true) ||
                baseUrl.contains("anikku", ignoreCase = true)

            var added = false
            var lastError: Throwable? = null

            if (isAnimeHint) {
                val animeResult = addAnimeExtensionStore(baseUrl)
                if (animeResult.isSuccess) {
                    added = true
                    animeExtensionManager.findAvailableExtensions()
                } else {
                    lastError = animeResult.exceptionOrNull()
                    val mangaResult = addExtensionStore(baseUrl)
                    if (mangaResult.isSuccess) {
                        added = true
                        extensionManager.findAvailableExtensions()
                    }
                }
            } else {
                val mangaResult = addExtensionStore(baseUrl)
                if (mangaResult.isSuccess) {
                    added = true
                    extensionManager.findAvailableExtensions()
                } else {
                    lastError = mangaResult.exceptionOrNull()
                    val animeResult = addAnimeExtensionStore(baseUrl)
                    if (animeResult.isSuccess) {
                        added = true
                        animeExtensionManager.findAvailableExtensions()
                    }
                }
            }

            if (added) {
                dismissDialog()
            } else {
                updateSuccessState {
                    it.copy(
                        dialog = when (it.dialog) {
                            is ExtensionStoreDialog.Create -> it.dialog.copy(
                                processing = false,
                                errorMessage = lastError?.message ?: "unknown error",
                            )
                            is ExtensionStoreDialog.Confirm -> it.dialog.copy(
                                processing = false,
                                errorMessage = lastError?.message ?: "unknown error",
                            )
                            else -> it.dialog
                        },
                    )
                }
            }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is ExtensionStoreScreenState.Success) {
            viewModelScope.launchIO {
                updateExtensionStores()
                updateAnimeExtensionStores()
                extensionManager.findAvailableExtensions()
                animeExtensionManager.findAvailableExtensions()
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        viewModelScope.launchIO {
            removeExtensionStore(baseUrl)
            removeAnimeExtensionStore(baseUrl)
            extensionManager.findAvailableExtensions()
            animeExtensionManager.findAvailableExtensions()
        }
    }

    fun addFromDeeplink(storeIndexUrl: String) {
        updateSuccessState { state ->
            state.copy(
                dialog = ExtensionStoreDialog.Confirm(
                    url = storeIndexUrl,
                    alreadyExists = state.stores.any { it.indexUrl == storeIndexUrl },
                ),
            )
        }
    }

    fun showDialog(dialog: ExtensionStoreDialog) {
        updateSuccessState { state ->
            state.copy(dialog = dialog)
        }
    }

    fun dismissDialog() {
        updateSuccessState {
            it.copy(dialog = null)
        }
    }
}

sealed class ExtensionStoreDialog {
    data class Create(val processing: Boolean = false, val errorMessage: String? = null) : ExtensionStoreDialog()
    data class Delete(val store: ExtensionStore) : ExtensionStoreDialog()
    data class Confirm(
        val url: String,
        val alreadyExists: Boolean = false,
        val processing: Boolean = false,
        val errorMessage: String? = null,
    ) : ExtensionStoreDialog()
}

sealed class ExtensionStoreScreenState {

    @Immutable
    data object Loading : ExtensionStoreScreenState()

    @Immutable
    data class Success(
        val stores: List<ExtensionStore>,
        val dialog: ExtensionStoreDialog? = null,
    ) : ExtensionStoreScreenState() {

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
