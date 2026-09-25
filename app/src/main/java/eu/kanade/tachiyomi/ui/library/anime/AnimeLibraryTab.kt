package eu.kanade.tachiyomi.ui.library.anime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object AnimeLibraryTab {
    private val queryEvent = Channel<String>()

    fun queryFlow() = queryEvent.receiveAsFlow()

    suspend fun search(query: String) = queryEvent.send(query)
}
