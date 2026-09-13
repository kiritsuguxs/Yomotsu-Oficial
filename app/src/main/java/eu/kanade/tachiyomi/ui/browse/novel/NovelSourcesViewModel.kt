package eu.kanade.tachiyomi.ui.browse.novel

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

class NovelSourcesViewModel(
    private val getEnabledNovelSources: GetEnabledNovelSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
) : StateViewModel<SourcesViewModel.State>(SourcesViewModel.State()) {

    private val _events = Channel<SourcesViewModel.Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            getEnabledNovelSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(SourcesViewModel.Event.FailedFetchingSources)
                }
                .collectLatest(::collectLatestSources)
        }
    }

    private fun collectLatestSources(sources: List<Source>) {
        mutableState.update { state ->
            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                when {
                    d1 == SourcesViewModel.LAST_USED_KEY && d2 != SourcesViewModel.LAST_USED_KEY -> -1
                    d2 == SourcesViewModel.LAST_USED_KEY && d1 != SourcesViewModel.LAST_USED_KEY -> 1
                    d1 == SourcesViewModel.PINNED_KEY && d2 != SourcesViewModel.PINNED_KEY -> -1
                    d2 == SourcesViewModel.PINNED_KEY && d1 != SourcesViewModel.PINNED_KEY -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) {
                when {
                    it.isUsedLast -> SourcesViewModel.LAST_USED_KEY
                    Pin.Actual in it.pin -> SourcesViewModel.PINNED_KEY
                    else -> it.lang
                }
            }

            state.copy(
                isLoading = false,
                items = byLang
                    .flatMap {
                        listOf(
                            SourceUiModel.Header(it.key),
                            *it.value.map { source ->
                                SourceUiModel.Item(source)
                            }.toTypedArray(),
                        )
                    },
            )
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = SourcesViewModel.Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }
}
