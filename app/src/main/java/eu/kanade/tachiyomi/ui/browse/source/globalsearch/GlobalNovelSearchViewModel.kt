package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalNovelSearchViewModel(
    initialQuery: String,
    private val sourceManager: SourceManager = Injekt.get(),
) : SearchViewModel(State(searchQuery = initialQuery)) {

    companion object {
        val INITIAL_QUERY_KEY = CreationExtras.Key<String>()

        val Factory = viewModelFactory {
            initializer {
                GlobalNovelSearchViewModel(
                    initialQuery = get(INITIAL_QUERY_KEY) ?: "",
                )
            }
        }
    }

    init {
        if (initialQuery.isNotBlank()) {
            search()
        }
    }

    override fun getEnabledSources(): List<Source> {
        return sourceManager.getAll()
            .filter { it is eu.kanade.tachiyomi.source.INovelSource }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
