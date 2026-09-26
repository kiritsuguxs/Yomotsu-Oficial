package eu.kanade.tachiyomi.ui.history

import eu.kanade.presentation.history.anime.AnimeHistoryScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.TabText



data object HistoryTab : Tab {

    private val snackbarHostState = SnackbarHostState()

    private val resumeLastChapterReadEvent = Channel<Unit>()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_recent_manga),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastChapterReadEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val viewModel = viewModel<HistoryViewModel>()
        val state by viewModel.state.collectAsState()

        val animeScreenModel = rememberScreenModel { AnimeHistoryScreenModel() }
        val animeState by animeScreenModel.state.collectAsState()

        val pagerState = rememberPagerState { 2 }
        val selectedTab = pagerState.currentPage
        val layoutDirection = LocalLayoutDirection.current

        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.zIndex(1f),
            ) {
                Tab(
                    selected = selectedTab == TAB_MANGA,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_MANGA) } },
                    text = { TabText(text = stringResource(MR.strings.manga)) },
                )
                Tab(
                    selected = selectedTab == TAB_ANIME,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_ANIME) } },
                    text = { TabText(text = stringResource(AYMR.strings.label_anime)) },
                )
            }

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
            ) { page ->
                val pagePadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                when (page) {
                    TAB_ANIME -> AnimeHistoryScreen(
                        state = animeState,
                        contentPadding = pagePadding,
                        onClickCover = { navigator.push(AnimeScreen(it)) },
                        onClickResume = animeScreenModel::getNextEpisodeForAnime,
                        onDialogChange = animeScreenModel::setDialog,
                        onClickFavorite = animeScreenModel::addFavorite,
                        snackbarHostState = remember { SnackbarHostState() },
                        pagePadding = pagePadding,
                    )
                    else -> MangaHistoryPage(
                        state = state,
                        contentPadding = pagePadding,
                        navigator = navigator,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    @Composable
    private fun MangaHistoryPage(
        state: HistoryViewModel.State,
        contentPadding: PaddingValues,
        navigator: Navigator,
        viewModel: HistoryViewModel,
    ) {
        val context = LocalContext.current

        HistoryScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onClickCover = { navigator.push(MangaScreen(it)) },
            onClickResume = viewModel::getNextChapterForManga,
            onDialogChange = viewModel::setDialog,
            onClickFavorite = viewModel::addFavorite,
        )

        val onDismissRequest = { viewModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is HistoryViewModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { all ->
                        if (all) {
                            viewModel.removeAllFromHistory(dialog.history.mangaId)
                        } else {
                            viewModel.removeFromHistory(dialog.history)
                        }
                    },
                )
            }
            is HistoryViewModel.Dialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = viewModel::removeAllHistory,
                )
            }
            is HistoryViewModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { viewModel.showMigrateDialog(dialog.manga, it) },
                )
            }
            is HistoryViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is HistoryViewModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            null -> {}
        }

        LaunchedEffect(state.list) {
            if (state.list != null) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { e ->
                when (e) {
                    HistoryViewModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    HistoryViewModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    is HistoryViewModel.Event.OpenChapter -> openChapter(context, e.chapter)
                }
            }
        }

        LaunchedEffect(Unit) {
            resumeLastChapterReadEvent.receiveAsFlow().collectLatest {
                openChapter(context, viewModel.getNextChapter())
            }
        }
    }

    private suspend fun openChapter(context: Context, chapter: Chapter?) {
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
            context.startActivity(intent)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
}


private const val TAB_MANGA = 0
private const val TAB_ANIME = 1
