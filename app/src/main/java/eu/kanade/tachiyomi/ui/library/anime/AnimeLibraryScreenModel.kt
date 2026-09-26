package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastPartition
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime

import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

class AnimeLibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val getTracksPerAnime: GetTracksPerAnime = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val downloadCache: AnimeDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<AnimeLibraryScreenModel.State>(State()) {

    init {
        mutableState.update { state ->
            state.copy(activeCategoryIndex = libraryPreferences.lastUsedAnimeCategory.get())
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                getCategories.subscribe(),
                getFavoritesFlow(),
                combine(getTracksPerAnime.subscribe(), getTrackingFiltersFlow(), ::Pair),
                getLibraryItemPreferencesFlow(),
            ) { searchQuery, categories, favorites, (tracksMap, trackingFilters), itemPreferences ->
                val showSystemCategory = favorites.any { it.libraryAnime.category == 0L }
                val filteredFavorites = favorites
                    .applyFilters(tracksMap, trackingFilters, itemPreferences)
                    .let { libraryItems ->
                        if (searchQuery.isNullOrEmpty()) {
                            libraryItems
                        } else {
                            val lowercaseQuery = searchQuery.lowercase()
                            libraryItems.filter { it.matches(lowercaseQuery) }
                        }
                    }

                LibraryData(
                    isInitialized = true,
                    showSystemCategory = showSystemCategory,
                    categories = categories,
                    favorites = filteredFavorites,
                    tracksMap = tracksMap,
                    loggedInTrackerIds = trackingFilters.keys,
                )
            }
                .distinctUntilChanged()
                .onEach { libraryData ->
                    mutableState.update { state ->
                        state.copy(libraryData = libraryData)
                    }
                }
                .launchIn(screenModelScope)
        }

        screenModelScope.launchIO {
            state
                .map { it.libraryData }
                .distinctUntilChanged()
                .onEach { data ->
                    val grouped = data.favorites
                        .applyGrouping(data.categories, data.showSystemCategory)
                        .applySort(data.favoritesById, data.tracksMap, data.loggedInTrackerIds)

                    mutableState.update { state ->
                        state.copy(
                            categories = grouped,
                            total = data.favorites.size,
                        )
                    }
                }
                .launchIn(screenModelScope)
        }

        screenModelScope.launchIO {
            getTrackingFiltersFlow()
                .map { it.isNotEmpty() }
                .onEach { hasFilters ->
                    mutableState.update { it.copy(hasActiveFilters = hasFilters) }
                }
                .launchIn(screenModelScope)
        }

        screenModelScope.launchNonCancellable {
            combine(
                getLibraryItemPreferencesFlow(),
                downloadCache.changes,
            ) { _, _ -> }
                .onEach {
                    mutableState.update { state ->
                        state.copy(hasFilters = state.selection.isNotEmpty())
                    }
                }
                .launchIn(screenModelScope)
        }
    }

    // region Filtering

    private fun applyFilter(filter: TriState, predicate: () -> Boolean): Boolean = when (filter) {
        TriState.DISABLED -> true
        TriState.ENABLED_IS -> predicate()
        TriState.ENABLED_NOT -> !predicate()
    }

    private fun List<AnimeLibraryItem>.applyFilters(
        trackMap: Map<Long, List<AnimeTrack>>,
        trackingFilter: Map<Long, TriState>,
        preferences: ItemPreferences,
    ): List<AnimeLibraryItem> {
        val downloadedOnly = preferences.globalFilterDownloaded
        val filterUnseen = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterUnseen
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterCompleted = preferences.filterCompleted

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isNotEmpty().not()

        val filterFnUnseen: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterUnseen) { it.libraryAnime.unseenCount > 0 }
        }

        val filterFnStarted: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryAnime.hasStarted }
        }

        val filterFnBookmarked: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryAnime.hasBookmarks }
        }

        val filterFnCompleted: (AnimeLibraryItem) -> Boolean = {
            applyFilter(filterCompleted) {
                it.libraryAnime.anime.status.toInt() == SAnime.COMPLETED
            }
        }

        val filterFnTracking: (AnimeLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val tracks = trackMap[item.id].orEmpty().map { it.trackerId }

            val isExcluded = excludedTracks.isNotEmpty() && tracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || tracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        return fastFilter {
            filterFnUnseen(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnTracking(it)
        }
    }

    // endregion

    // region Sorting

    private fun List<AnimeLibraryItem>.applyGrouping(
        categories: List<Category>,
        showSystemCategory: Boolean,
    ): Map<Category, List</* AnimeLibraryItem */ Long>> {
        val groupCache = mutableMapOf</* AnimeLibraryItem */ Long, MutableList</* AnimeLibraryItem */ Long>>()
        forEach { item ->
            groupCache.getOrPut(item.libraryAnime.category) { mutableListOf() }.add(item.id)
        }
        return categories.filter { showSystemCategory || !it.isSystemCategory }
            .associateWith { groupCache[it.id]?.toList().orEmpty() }
    }

    private fun Map<Category, List</* AnimeLibraryItem */ Long>>.applySort(
        favoritesById: Map<Long, AnimeLibraryItem>,
        trackMap: Map<Long, List<AnimeTrack>>,
        loggedInTrackerIds: Set<Long>,
    ): Map<Category, List</* AnimeLibraryItem */ Long>> {
        val sortAlphabetically: (AnimeLibraryItem, AnimeLibraryItem) -> Int = { anime1, anime2 ->
            val title1 = anime1.libraryAnime.anime.title.lowercase()
            val title2 = anime2.libraryAnime.anime.title.lowercase()
            title1.compareToWithCollator(title2)
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else -> entry.value.map { it.score }.average()
                }
            }
        }

        fun AnimeLibrarySort.comparator(): Comparator<AnimeLibraryItem> = Comparator { anime1, anime2 ->
            when (this.type) {
                AnimeLibrarySort.Type.Alphabetical -> sortAlphabetically(anime1, anime2)
                AnimeLibrarySort.Type.LastSeen -> {
                    anime1.libraryAnime.lastSeen.compareTo(anime2.libraryAnime.lastSeen)
                }
                AnimeLibrarySort.Type.LastUpdate -> {
                    anime1.libraryAnime.anime.lastUpdate.compareTo(anime2.libraryAnime.anime.lastUpdate)
                }
                AnimeLibrarySort.Type.UnseenCount -> when {
                    anime1.libraryAnime.unseenCount == anime2.libraryAnime.unseenCount -> 0
                    anime1.libraryAnime.unseenCount == 0L -> if (isAscending) 1 else -1
                    anime2.libraryAnime.unseenCount == 0L -> if (isAscending) -1 else 1
                    else -> anime1.libraryAnime.unseenCount.compareTo(anime2.libraryAnime.unseenCount)
                }
                AnimeLibrarySort.Type.TotalEpisodes -> {
                    anime1.libraryAnime.totalCount.compareTo(anime2.libraryAnime.totalCount)
                }
                AnimeLibrarySort.Type.LatestEpisode -> {
                    anime1.libraryAnime.latestUpload.compareTo(anime2.libraryAnime.latestUpload)
                }
                AnimeLibrarySort.Type.EpisodeFetchDate -> {
                    anime1.libraryAnime.episodeFetchedAt.compareTo(anime2.libraryAnime.episodeFetchedAt)
                }
                AnimeLibrarySort.Type.DateAdded -> {
                    anime1.libraryAnime.anime.dateAdded.compareTo(anime2.libraryAnime.anime.dateAdded)
                }
                AnimeLibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[anime1.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[anime2.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                AnimeLibrarySort.Type.AiringTime -> {
                    anime1.libraryAnime.anime.expectedNextUpdate
                        ?.compareTo(anime2.libraryAnime.anime.expectedNextUpdate ?: run { null })
                        ?: 0
                }
                AnimeLibrarySort.Type.Random -> error("Should not be reached, handled by caller")
            }
        }

        return mapValues { (key, value) ->
            val sort = AnimeLibrarySort(
                type = AnimeLibrarySort.Type.valueOf(key.flags),
                direction = AnimeLibrarySort.Direction.valueOf(key.flags),
            )

            if (sort.type == AnimeLibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomAnimeSortSeed.get()))
            }

            val anime = value.mapNotNull { favoritesById[it] }

            val comparator = sort.comparator()
                .let { if (sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            anime.sortedWith(comparator).map { it.id }
        }
    }

    // endregion

    private fun getTrackingFiltersFlow(): Flow<Map<Long, TriState>> {
        val allTriStates = TriState.entries.toList()
        return combine(
            trackerManager.loggedInTrackersFlow(),
            getTracksPerAnime.subscribe(),
        ) { trackers, tracksMap ->
            trackers.map { tracker ->
                val hasScoredAnime = tracksMap.values.any { tracks ->
                    tracks.any { it.trackerId == tracker.id && it.score != 0.0 }
                }
                tracker.id.toLong() to allTriStates.first { state ->
                    when (state) {
                        TriState.ENABLED_IS -> hasScoredAnime
                        else -> state == TriState.ENABLED_NOT
                    }
                }
            }.toMap()
        }
            .distinctUntilChanged()
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge.changes(),
            libraryPreferences.unseenBadge.changes(),
            libraryPreferences.localBadge.changes(),
            libraryPreferences.languageBadge.changes(),
            preferences.downloadedOnly.changes(),
            libraryPreferences.animeFilterUnseen.changes(),
            libraryPreferences.animeFilterStarted.changes(),
            libraryPreferences.animeFilterBookmarked.changes(),
            libraryPreferences.animeFilterCompleted.changes(),
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unseenBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                globalFilterDownloaded = it[4] as Boolean,
                filterUnseen = it[5] as TriState,
                filterStarted = it[6] as TriState,
                filterBookmarked = it[7] as TriState,
                filterCompleted = it[8] as TriState,
            )
        }
    }

    private fun getFavoritesFlow(): Flow<List<AnimeLibraryItem>> {
        return combine(
            getLibraryAnime.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryAnime, preferences, _ ->
            libraryAnime.map { anime ->
                AnimeLibraryItem(
                    libraryAnime = anime,
                    downloadCount = downloadManager.getDownloadCount(anime.anime).toLong(),
                    unseenCount = anime.unseenCount,
                    isLocal = anime.anime.isLocal(),
                    sourceLanguage = if (preferences.languageBadge) {
                        sourceManager.getOrStub(anime.anime.source).lang
                    } else {
                        ""
                    },
                )
            }
        }
    }

    // region Selection

    fun toggleSelection(anime: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.fastPartition { it.id != anime.id }
                .let { (notInSelection, inSelection) ->
                    if (inSelection.isEmpty()) {
                        notInSelection + anime
                    } else {
                        notInSelection
                    }
                }
            state.copy(selection = newSelection, hasFilters = newSelection.isNotEmpty())
        }
    }

    fun selectAll() {
        val items = getLibraryForPage(currentPage)
        mutableState.update { state ->
            state.copy(selection = items.map { it.libraryAnime }, hasFilters = true)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptyList(), hasFilters = false) }
    }

    fun invertSelection() {
        val items = getLibraryForPage(currentPage).map { it.libraryAnime }
        mutableState.update { state ->
            val inverted = items.fastFilterNot { item -> state.selection.any { it.id == item.id } }
            state.copy(selection = inverted, hasFilters = inverted.isNotEmpty())
        }
    }

    fun toggleRangeSelection(anchor: LibraryAnime, other: LibraryAnime) {
        val items = getLibraryForPage(currentPage).map { it.libraryAnime }
        val range = items.subList(minOf(items.indexOf(anchor), items.indexOf(other)), maxOf(items.indexOf(anchor), items.indexOf(other)) + 1)
        mutableState.update { state ->
            val newSelection = (state.selection + range).distinctBy { it.id }
            state.copy(selection = newSelection, hasFilters = newSelection.isNotEmpty())
        }
    }

    // endregion

    // region Categories

    fun setCategory(category: Category?) {
        val categories = state.libraryData.categories
        val newIndex = category?.let { categories.findIndex { c -> c.id == it.id } } ?: -1
        mutableState.update { it.copy(activeCategoryIndex = newIndex) }
        libraryPreferences.lastUsedAnimeCategory.set(newIndex)
    }

    fun updateLibrary() {
        val ids = state.selection.map { it.id }
        if (ids.isEmpty()) return
        screenModelScope.launchIO {
            val defaultCategoryId = libraryPreferences.defaultAnimeCategory.get().toLong()
            if (defaultCategoryId == -1L) {
                clearSelection()
                return@launchIO
            }
            ids.forEach { id ->
                setAnimeCategories.await(id, listOf(defaultCategoryId))
            }
            clearSelection()
        }
    }

    fun removeFromLibrary(deleteFromLibrary: Boolean, deleteEpisodes: Boolean) {
        screenModelScope.launchNonCancellable {
            val animes = state.selection.map { it.anime }
            if (deleteFromLibrary) {
                val toDelete = animes.map {
                    it.removeCovers(coverCache)
                    AnimeUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateAnime.awaitAll(toDelete)
            }

            if (deleteEpisodes) {
                animes.forEach { anime ->
                    val source = sourceManager.get(anime.source)
                    if (source != null) {
                        downloadManager.deleteAnime(anime, source)
                    }
                }
            }
        }
    }

    fun downloadSelected() {
        val ids = state.selection.map { it.id }
        screenModelScope.launchIO {
            getLibraryAnime.await()
                .filter { it.id in ids }
                .forEach { libraryAnime ->
                    val anime = libraryAnime.anime
                    val episodes = getEpisodesByAnimeId.await(anime.id)
                    episodes.getNextUnseen(anime, downloadManager)?.let { next ->
                        downloadManager.downloadEpisodes(
                            anime = anime,
                            episodes = listOf(next),
                        )
                    }
                }
            clearSelection()
        }
    }

    // endregion

    fun getLibraryForPage(page: Int): List<AnimeLibraryItem> {
        val category = state.activeCategory ?: return emptyList()
        val entries = state.categories[category]?.let { state.libraryData.favoritesById[it] } ?: emptyList()
        val pageLimit = (page + 1) * 50
        return entries.take(pageLimit).drop(page * 50)
    }

    fun getLibraryForCategory(category: Category?): ImmutableList<AnimeLibraryItem> {
        val entries = state.categories[category]?.let { state.libraryData.favoritesById[it] } ?: return persistentEmptyList()
        return entries.map { state.libraryData.favoritesById[it]!! }.toImmutableList()
    }

    fun getNumberOfItemsForCategory(category: Category): Int? {
        if (!libraryPreferences.categoryNumberOfItems.get()) return null
        return state.categories[category]?.size
    }

    fun getAnimeCount(category: Category?): Int? {
        return if (category == null) state.total else getLibraryForCategory(category).size
    }

    fun getDisplayMode(page: Int): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.animeDisplayMode.asState(screenModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.animeLandscapeColumns else libraryPreferences.animePortraitColumns)
            .asState(screenModelScope)
    }

    fun openRandomAnime() {
        screenModelScope.launchIO {
            val activeCategory = state.activeCategory
            val items = getLibraryForCategory(activeCategory)
            if (items.isEmpty()) return@launchIO
            items[Random.nextInt(0, items.size)].let { onRandomAnimeClick?.invoke(it.id) }
        }
    }

    var onRandomAnimeClick: ((Long) -> Unit)? = null

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query.orEmpty()) }
    }

    private val currentPage: Int
        get() = state.activeCategoryIndex

    val activeCategory: Category?
        get() = state.libraryData.categories.getOrNull(state.activeCategoryIndex)

    fun getToolbarTitle(
        defaultTitle: String,
        defaultCategoryTitle: String,
        page: Int,
    ): LibraryToolbarTitle {
        val category = state.libraryData.categories.getOrNull(state.activeCategoryIndex)
        return LibraryToolbarTitle(
            title = category?.title ?: defaultCategoryTitle,
            subtitle = defaultTitle.takeIf { category?.isSystemCategory == false },
            page = page,
        )
    }

    fun clearSelectionAndFilters() {
        mutableState.update { it.copy(selection = emptyList(), hasFilters = false) }
    }

    // region State

    data class State(
        val isInitialized: Boolean = false,
        val activeCategoryIndex: Int = 0,
        val searchQuery: String = "",
        val selection: List<LibraryAnime> = emptyList(),
        val hasActiveFilters: Boolean = false,
        val hasFilters: Boolean = false,
        val total: Int = 0,
        val categories: Map<Category, List<Long>> = emptyMap(),
        val libraryData: LibraryData = LibraryData(),
    ) {
        val activeCategory: Category?
            get() = libraryData.categories.getOrNull(activeCategoryIndex)
    }

    data class LibraryData(
        val isInitialized: Boolean = false,
        val showSystemCategory: Boolean = false,
        val categories: List<Category> = emptyList(),
        val favorites: List<AnimeLibraryItem> = emptyList(),
        val tracksMap: Map<Long, List<AnimeTrack>> = emptyMap(),
        val loggedInTrackerIds: Set<Long> = emptySet(),
    ) {
        val favoritesById: Map<Long, AnimeLibraryItem> = favorites.associateBy { it.id }
    }

    data class ItemPreferences(
        val downloadBadge: Boolean = true,
        val unseenBadge: Boolean = true,
        val localBadge: Boolean = true,
        val languageBadge: Boolean = true,
        val globalFilterDownloaded: Boolean = false,
        val filterUnseen: TriState = TriState.DISABLED,
        val filterStarted: TriState = TriState.DISABLED,
        val filterBookmarked: TriState = TriState.DISABLED,
        val filterCompleted: TriState = TriState.DISABLED,
    )

    // endregion
}

private fun persistentEmptyList(): ImmutableList<AnimeLibraryItem> {
    return kotlinx.collections.immutable.persistentListOf()
}

private const val SEARCH_DEBOUNCE_MILLIS = 250L
