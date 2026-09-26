package tachiyomi.domain.library.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.manga.model.Manga

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val displayMode: Preference<LibraryDisplayMode> = preferenceStore.getObjectFromString(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val animeDisplayMode: Preference<LibraryDisplayMode> = preferenceStore.getObjectFromString(
        "pref_display_mode_animelib",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val animeCategoryTabs: Preference<Boolean> = preferenceStore.getBoolean(
        "display_animelib_category_tabs",
        true,
    )

    val unseenBadge: Preference<Boolean> = preferenceStore.getBoolean("display_unseen_badge", true)

    val showContinueWatchingButton: Preference<Boolean> = preferenceStore.getBoolean(
        "display_continue_watching_button",
        true,
    )

    val sortingMode: Preference<LibrarySort> = preferenceStore.getObjectFromString(
        "library_sorting_mode",
        LibrarySort.default,
        LibrarySort.Serializer::serialize,
        LibrarySort.Serializer::deserialize,
    )

    val animeSortingMode: Preference<AnimeLibrarySort> = preferenceStore.getObjectFromString(
        "animelib_sorting_mode",
        AnimeLibrarySort.default,
        AnimeLibrarySort.Serializer::serialize,
        AnimeLibrarySort.Serializer::deserialize,
    )

    val randomSortSeed: Preference<Int> = preferenceStore.getInt("library_random_sort_seed", 0)

    val randomAnimeSortSeed: Preference<Int> = preferenceStore.getInt("library_random_anime_sort_seed", 0)

    val portraitColumns: Preference<Int> = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    val landscapeColumns: Preference<Int> = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    val animePortraitColumns: Preference<Int> = preferenceStore.getInt("pref_anime_library_columns_portrait_key", 0)

    val animeLandscapeColumns: Preference<Int> = preferenceStore.getInt("pref_anime_library_columns_landscape_key", 0)

    val lastUpdatedTimestamp: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("library_update_last_timestamp"),
        0L,
    )
    val autoUpdateInterval: Preference<Int> = preferenceStore.getInt("pref_library_update_interval_key", 0)

    val autoUpdateDeviceRestrictions: Preference<Set<String>> = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )
    val autoUpdateMangaRestrictions: Preference<Set<String>> = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            MANGA_HAS_UNREAD,
            MANGA_NON_COMPLETED,
            MANGA_NON_READ,
            MANGA_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    val autoUpdateMetadata: Preference<Boolean> = preferenceStore.getBoolean("auto_update_metadata", false)

    val showContinueReadingButton: Preference<Boolean> = preferenceStore.getBoolean(
        "display_continue_reading_button",
        false,
    )

    val markDuplicateReadChapterAsRead: Preference<Set<String>> = preferenceStore.getStringSet(
        "mark_duplicate_read_chapter_read",
        emptySet(),
    )

    val markDuplicateSeenEpisodeAsSeen: Preference<Set<String>> = preferenceStore.getStringSet(
        "mark_duplicate_seen_episode_seen",
        emptySet(),
    )


    // region Filter

    val filterDownloaded: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_downloaded_v2",
        TriState.DISABLED,
    )

    val filterUnread: Preference<TriState> = preferenceStore.getEnum("pref_filter_library_unread_v2", TriState.DISABLED)

    val filterStarted: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_started_v2",
        TriState.DISABLED,
    )

    val filterBookmarked: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_bookmarked_v2",
        TriState.DISABLED,
    )

    val filterCompleted: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_completed_v2",
        TriState.DISABLED,
    )

    val filterIntervalCustom: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    fun filterTracking(id: Int): Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_tracked_${id}_v2",
        TriState.DISABLED,
    )

    val animeFilterUnseen: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_animelib_unseen_v2",
        TriState.DISABLED,
    )

    val animeFilterStarted: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_animelib_started_v2",
        TriState.DISABLED,
    )

    val animeFilterBookmarked: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_animelib_bookmarked_v2",
        TriState.DISABLED,
    )

    val animeFilterCompleted: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_animelib_completed_v2",
        TriState.DISABLED,
    )

    fun filterTrackedAnime(id: Int): Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_animelib_tracked_${id}_v2",
        TriState.DISABLED,
    )

    // endregion

    // region Badges

    val downloadBadge: Preference<Boolean> = preferenceStore.getBoolean("display_download_badge", false)

    val unreadBadge: Preference<Boolean> = preferenceStore.getBoolean("display_unread_badge", true)

    val localBadge: Preference<Boolean> = preferenceStore.getBoolean("display_local_badge", true)

    val languageBadge: Preference<Boolean> = preferenceStore.getBoolean("display_language_badge", true)

    val newShowUpdatesCount: Preference<Boolean> = preferenceStore.getBoolean("library_show_updates_count", true)
    val newUpdatesCount: Preference<Int> = preferenceStore.getInt(
        Preference.appStateKey("library_unseen_updates_count"),
        0,
    )

    // endregion

    // region Category

    val defaultCategory: Preference<Int> = preferenceStore.getInt(DEFAULT_CATEGORY_PREF_KEY, -1)

    val defaultAnimeCategory: Preference<Int> = preferenceStore.getInt("default_anime_category", -1)

    val lastUsedCategory: Preference<Int> = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)

    val lastUsedAnimeCategory: Preference<Int> = preferenceStore.getInt(Preference.appStateKey("last_used_anime_category"), 0)

    val categoryTabs: Preference<Boolean> = preferenceStore.getBoolean("display_category_tabs", true)

    val categoryNumberOfItems: Preference<Boolean> = preferenceStore.getBoolean("display_number_of_items", false)

    val categorizedDisplaySettings: Preference<Boolean> = preferenceStore.getBoolean("categorized_display", false)

    val updateCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val updateCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )

    val animeUpdateCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        "animelib_update_categories",
        emptySet(),
    )

    val animeUpdateCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        "animelib_update_categories_exclude",
        emptySet(),
    )

    // endregion

    // region Chapter

    val filterChapterByRead: Preference<Long> = preferenceStore.getLong(
        "default_chapter_filter_by_read",
        Manga.SHOW_ALL,
    )

    val filterChapterByDownloaded: Preference<Long> = preferenceStore.getLong(
        "default_chapter_filter_by_downloaded",
        Manga.SHOW_ALL,
    )

    val filterChapterByBookmarked: Preference<Long> = preferenceStore.getLong(
        "default_chapter_filter_by_bookmarked",
        Manga.SHOW_ALL,
    )

    // and upload date
    val sortChapterBySourceOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_chapter_sort_by_source_or_number",
        Manga.CHAPTER_SORTING_SOURCE,
    )

    val displayChapterByNameOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Manga.CHAPTER_DISPLAY_NAME,
    )

    val sortChapterByAscendingOrDescending: Preference<Long> = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Manga.CHAPTER_SORT_DESC,
    )

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead.set(manga.unreadFilterRaw)
        filterChapterByDownloaded.set(manga.downloadedFilterRaw)
        filterChapterByBookmarked.set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber.set(manga.sorting)
        displayChapterByNameOrNumber.set(manga.displayMode)
        sortChapterByAscendingOrDescending.set(
            if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    val autoClearChapterCache: Preference<Boolean> = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    val hideMissingChapters: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_hide_missing_chapter_indicators",
        false,
    )
    // endregion

    // region Episode

    val filterEpisodeBySeen: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_seen",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByDownloaded: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_downloaded",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByBookmarked: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_bookmarked",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByFillermarked: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_fillermarked",
        Anime.SHOW_ALL,
    )

    val sortEpisodeBySourceOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_episode_sort_by_source_or_number",
        Anime.EPISODE_SORTING_SOURCE,
    )

    val displayEpisodeByNameOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_episode_display_by_name_or_number",
        Anime.EPISODE_DISPLAY_NAME,
    )

    val sortEpisodeByAscendingOrDescending: Preference<Long> = preferenceStore.getLong(
        "default_episode_sort_by_ascending_or_descending",
        Anime.EPISODE_SORT_DESC,
    )

    val showEpisodeThumbnailPreviews: Preference<Boolean> = preferenceStore.getBoolean(
        "default_episode_show_thumbnail_previews",
        false,
    )

    val showEpisodeSummaries: Preference<Boolean> = preferenceStore.getBoolean(
        "default_episode_show_summaries",
        false,
    )

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen.set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded.set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked.set(anime.bookmarkedFilterRaw)
        filterEpisodeByFillermarked.set(anime.fillermarkedFilterRaw)
        sortEpisodeBySourceOrNumber.set(anime.sorting)
        displayEpisodeByNameOrNumber.set(anime.displayMode)
        sortEpisodeByAscendingOrDescending.set(
            if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC,
        )
    }

    fun setSeasonSettingsDefault(anime: Anime) {
    }

    // endregion

    // region Swipe Actions

    val swipeToStartAction: Preference<ChapterSwipeAction> = preferenceStore.getEnum(
        "pref_chapter_swipe_end_action",
        ChapterSwipeAction.ToggleBookmark,
    )

    val swipeToEndAction: Preference<ChapterSwipeAction> = preferenceStore.getEnum(
        "pref_chapter_swipe_start_action",
        ChapterSwipeAction.ToggleRead,
    )

    val episodeSwipeStartAction: Preference<EpisodeSwipeAction> = preferenceStore.getEnum(
        "pref_episode_swipe_start_action",
        EpisodeSwipeAction.ToggleBookmark,
    )

    val episodeSwipeEndAction: Preference<EpisodeSwipeAction> = preferenceStore.getEnum(
        "pref_episode_swipe_end_action",
        EpisodeSwipeAction.ToggleSeen,
    )

    fun swipeEpisodeStartAction() = episodeSwipeStartAction
    fun swipeEpisodeEndAction() = episodeSwipeEndAction

    val updateSeasonOnRefresh: Preference<Boolean> = preferenceStore.getBoolean("pref_update_season_on_refresh", false)
    fun updateSeasonOnRefresh() = updateSeasonOnRefresh

    val updateSeasonOnLibraryUpdate: Preference<Boolean> = preferenceStore.getBoolean("pref_update_season_on_library_update", false)
    fun updateSeasonOnLibraryUpdate() = updateSeasonOnLibraryUpdate

    val autoUpdateItemRestrictions: Preference<Set<String>> get() = autoUpdateMangaRestrictions
    fun autoUpdateItemRestrictions() = autoUpdateItemRestrictions

    val updateMangaTitles: Preference<Boolean> = preferenceStore.getBoolean("pref_update_library_manga_titles", false)

    val disallowNonAsciiFilenames: Preference<Boolean> = preferenceStore.getBoolean(
        "disallow_non_ascii_filenames",
        false,
    )

    // endregion

    enum class ChapterSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    enum class EpisodeSwipeAction {
        ToggleSeen,
        ToggleBookmark,
        ToggleFillermark,
        Download,
        Disabled,
    }

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        const val MANGA_NON_COMPLETED = "manga_ongoing"
        const val MANGA_HAS_UNREAD = "manga_fully_read"
        const val MANGA_NON_READ = "manga_started"
        const val MANGA_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"

        const val ENTRY_NON_COMPLETED = MANGA_NON_COMPLETED
        const val ENTRY_HAS_UNREAD = MANGA_HAS_UNREAD
        const val ENTRY_NON_READ = MANGA_NON_READ
        const val ENTRY_OUTSIDE_RELEASE_PERIOD = MANGA_OUTSIDE_RELEASE_PERIOD

        const val MARK_DUPLICATE_CHAPTER_READ_NEW = "new"
        const val MARK_DUPLICATE_CHAPTER_READ_EXISTING = "existing"

        const val MARK_DUPLICATE_EPISODE_SEEN_NEW = "new"
        const val MARK_DUPLICATE_EPISODE_SEEN_EXISTING = "existing"


        const val DEFAULT_CATEGORY_PREF_KEY = "default_category"
        private const val LIBRARY_UPDATE_CATEGORIES_PREF_KEY = "library_update_categories"
        private const val LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY = "library_update_categories_exclude"
        val categoryPreferenceKeys = setOf(
            DEFAULT_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
