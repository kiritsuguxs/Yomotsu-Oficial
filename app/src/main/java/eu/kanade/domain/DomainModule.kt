package eu.kanade.domain

import eu.kanade.domain.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.download.interactor.DeleteDownload
import eu.kanade.domain.extension.interactor.GetExtensionLanguages
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.interactor.TrackChapter
import mihon.data.extension.repository.ExtensionStoreRepositoryImpl
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.data.source.SourceRepositoryImpl
import tachiyomi.data.source.StubSourceRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.data.updates.UpdatesRepositoryImpl
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.GetTotalReadDuration
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.GetSourcesWithNonLibraryManga
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.repository.UpdatesRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<CategoryRepository> { CategoryRepositoryImpl(get()) }
        addFactory { GetCategories(get()) }
        addFactory { ResetCategoryFlags(get(), get()) }
        addFactory { SetDisplayMode(get()) }
        addFactory { SetSortModeForCategory(get(), get()) }
        addFactory { CreateCategoryWithName(get(), get()) }
        addFactory { RenameCategory(get()) }
        addFactory { ReorderCategory(get()) }
        addFactory { DeleteCategory(get(), get(), get()) }

        addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryManga(get()) }
        addFactory { GetFavorites(get()) }
        addFactory { GetLibraryManga(get()) }
        addFactory { GetMangaWithChapters(get(), get()) }
        addFactory { GetMangaByUrlAndSourceId(get()) }
        addFactory { GetManga(get()) }
        addFactory { GetNextChapters(get(), get(), get()) }
        addFactory { GetUpcomingManga(get()) }
        addFactory { ResetViewerFlags(get()) }
        addFactory { SetMangaChapterFlags(get()) }
        addFactory { FetchInterval(get()) }
        addFactory { SetMangaDefaultChapterFlags(get(), get(), get()) }
        addFactory { SetMangaViewerFlags(get()) }
        addFactory { NetworkToLocalManga(get()) }
        addFactory { UpdateManga(get(), get()) }
        addFactory { UpdateMangaNotes(get()) }
        addFactory { SetMangaCategories(get()) }
        addFactory { GetExcludedScanlators(get()) }
        addFactory { SetExcludedScanlators(get()) }
        addFactory {
            MigrateMangaUseCase(
                get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            )
        }

        addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
        addFactory { GetApplicationRelease(get()) }

        addSingletonFactory<TrackRepository> { TrackRepositoryImpl(get()) }
        addFactory { TrackChapter(get(), get(), get(), get()) }
        addFactory { AddTracks(get(), get(), get(), get()) }
        addFactory { RefreshTracks(get(), get(), get(), get()) }
        addFactory { DeleteTrack(get()) }
        addFactory { GetTracksPerManga(get()) }
        addFactory { GetTracks(get()) }
        addFactory { InsertTrack(get()) }
        addFactory { SyncChapterProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
        addFactory { GetChapter(get()) }
        addFactory { GetChaptersByMangaId(get()) }
        addFactory { GetBookmarkedChaptersByMangaId(get()) }
        addFactory { GetChapterByUrlAndMangaId(get()) }
        addFactory { UpdateChapter(get()) }
        addFactory { SetReadStatus(get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbChapter() }
        addFactory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { GetAvailableScanlators(get()) }
        addFactory { FilterChaptersForDownload(get(), get(), get()) }

        addSingletonFactory<HistoryRepository> { HistoryRepositoryImpl(get()) }
        addFactory { GetHistory(get()) }
        addFactory { UpsertHistory(get()) }
        addFactory { RemoveHistory(get()) }
        addFactory { GetTotalReadDuration(get()) }

        addFactory { DeleteDownload(get(), get()) }

        addFactory { GetExtensionsByType(get(), get()) }
        addFactory { GetExtensionSources(get()) }
        addFactory { GetExtensionLanguages(get(), get()) }

        addSingletonFactory<UpdatesRepository> { UpdatesRepositoryImpl(get()) }
        addFactory { GetUpdates(get()) }

        addSingletonFactory<SourceRepository> { SourceRepositoryImpl(get(), get()) }
        addSingletonFactory<StubSourceRepository> { StubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledSources(get(), get()) }
        addFactory { GetEnabledNovelSources(get(), get()) }
        addFactory { GetLanguagesWithSources(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetSourcesWithNonLibraryManga(get()) }
        addFactory { SetMigrateSorting(get()) }
        addFactory { ToggleLanguage(get()) }
        addFactory { ToggleSource(get()) }
        addFactory { ToggleSourcePin(get()) }
        addFactory { TrustExtension(get(), get()) }

        addSingletonFactory { ExtensionStoreService(get(), get(), get()) }
        addSingletonFactory<ExtensionStoreRepository> { ExtensionStoreRepositoryImpl(get(), get()) }
        addFactory { AddExtensionStore(get()) }
        addFactory { GetExtensionStoreCountAsFlow(get()) }
        addFactory { GetExtensionStores(get()) }
        addFactory { RemoveExtensionStore(get()) }
        addFactory { UpdateExtensionStores(get()) }

        addFactory { ToggleIncognito(get()) }
        addFactory { GetIncognitoState(get(), get(), get()) }

        addFactory { UpdateMangaFromRemote(get(), get(), get(), get(), get(), get(), get()) }

        // Anime Repositories & Interactors
        addSingletonFactory<tachiyomi.domain.entries.anime.repository.AnimeRepository> {
            tachiyomi.data.entries.anime.AnimeRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnime(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetLibraryAnime(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags(get(), get(), get()) }
        addFactory { eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags(get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime(get(), get()) }
        addFactory { eu.kanade.domain.entries.anime.interactor.UpdateAnime(get(), get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons(get(), get()) }
        addFactory { tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags(get()) }
        addFactory { tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags() }
        addFactory { mihon.domain.items.episode.interactor.FilterEpisodesForDownload(get(), get(), get()) }
        addFactory { mihon.domain.source.interactor.UpdateAnimeFromRemote(get()) }
        addFactory { eu.kanade.domain.items.episode.interactor.SetSeenStatus(get()) }

        addSingletonFactory<tachiyomi.domain.entries.anime.repository.AnimeRelationRepository> {
            tachiyomi.data.entries.anime.AnimeRelationRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.entries.anime.interactor.GetRelatedAnime(get()) }
        addFactory { eu.kanade.domain.entries.anime.interactor.SyncRelatedAnimeWithSource(get(), get(), get()) }

        addSingletonFactory<tachiyomi.domain.category.anime.repository.AnimeCategoryRepository> {
            tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.category.anime.interactor.GetAnimeCategories(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.SetAnimeCategories(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName(get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory(get(), get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.HideAnimeCategory(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.RenameAnimeCategory(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags(get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.SetAnimeDisplayMode(get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.SetSortModeForAnimeCategory(get(), get()) }
        addFactory { tachiyomi.domain.category.anime.interactor.UpdateAnimeCategory(get()) }

        addSingletonFactory<tachiyomi.domain.items.episode.repository.EpisodeRepository> {
            tachiyomi.data.items.episode.EpisodeRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.items.episode.interactor.GetEpisode(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId(get()) }
        addFactory { tachiyomi.domain.items.episode.interactor.UpdateEpisode(get()) }

        addSingletonFactory<tachiyomi.domain.history.anime.repository.AnimeHistoryRepository> {
            tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.history.anime.interactor.GetAnimeHistory(get()) }
        addFactory { tachiyomi.domain.history.anime.interactor.GetNextEpisodes(get(), get(), get()) }
        addFactory { tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory(get()) }
        addFactory { tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory(get()) }

        addSingletonFactory<tachiyomi.domain.track.anime.repository.AnimeTrackRepository> {
            tachiyomi.data.track.anime.AnimeTrackRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.track.anime.interactor.GetAnimeTracks(get()) }
        addFactory { tachiyomi.domain.track.anime.interactor.GetTracksPerAnime(get()) }
        addFactory { tachiyomi.domain.track.anime.interactor.InsertAnimeTrack(get()) }
        addFactory { tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack(get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack(get(), get(), get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks(get(), get(), get(), get()) }
        addFactory { eu.kanade.domain.track.anime.interactor.TrackEpisode(get(), get(), get(), get()) }

        addSingletonFactory<tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository> {
            tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates(get()) }

        addSingletonFactory<tachiyomi.domain.source.anime.repository.AnimeSourceRepository> {
            tachiyomi.data.source.anime.AnimeSourceRepositoryImpl(get(), get())
        }
        addSingletonFactory<tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository> {
            tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl(get())
        }
        addFactory { eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount(get(), get()) }
        addFactory { tachiyomi.domain.source.anime.interactor.GetAnimeSourcesWithNonLibraryAnime(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.ToggleAnimeSource(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState(get(), get(), get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito(get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources(get(), get()) }
        addFactory { eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources(get(), get()) }
        addFactory { tachiyomi.domain.source.anime.interactor.GetRemoteAnime(get()) }

        addSingletonFactory { mihon.data.extension.anime.service.AnimeExtensionStoreService(get(), get(), get()) }
        addSingletonFactory<mihon.domain.extension.anime.repository.AnimeExtensionStoreRepository> {
            mihon.data.extension.anime.repository.AnimeExtensionStoreRepositoryImpl(get(), get())
        }
        addFactory { mihon.domain.extension.anime.interactor.AddAnimeExtensionStore(get()) }
        addFactory { mihon.domain.extension.anime.interactor.GetAnimeExtensionStoreCountAsFlow(get()) }
        addFactory { mihon.domain.extension.anime.interactor.GetAnimeExtensionStores(get()) }
        addFactory { mihon.domain.extension.anime.interactor.RemoveAnimeExtensionStore(get()) }
        addFactory { mihon.domain.extension.anime.interactor.UpdateAnimeExtensionStores(get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType(get(), get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources(get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages(get(), get()) }
        addFactory { eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension(get(), get()) }
        addSingletonFactory<tachiyomi.domain.custombuttons.repository.CustomButtonRepository> {
            tachiyomi.data.custombutton.CustomButtonRepositoryImpl()
        }
        addFactory { tachiyomi.domain.custombuttons.interactor.GetCustomButtons(get()) }
    }
}
