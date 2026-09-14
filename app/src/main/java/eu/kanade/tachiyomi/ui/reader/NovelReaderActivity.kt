package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.tachiyomi.extension.novel.NovelSourceWrapper
import eu.kanade.tachiyomi.extension.novel.download.NovelDownloadManager
import eu.kanade.tachiyomi.extension.novel.translation.NovelTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelReaderActivity : ComponentActivity() {

    companion object {
        fun newIntent(context: Context, mangaId: Long, chapterId: Long): Intent {
            return Intent(context, NovelReaderActivity::class.java).apply {
                putExtra("manga_id", mangaId)
                putExtra("chapter_id", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    class ChapterItemState(
        val chapter: Chapter,
        originalText: String? = null,
        translatedText: String? = null,
        isLoading: Boolean = true,
        isTranslating: Boolean = false,
        error: String? = null,
    ) {
        var originalText by mutableStateOf(originalText)
        var translatedText by mutableStateOf(translatedText)
        var isLoading by mutableStateOf(isLoading)
        var isTranslating by mutableStateOf(isTranslating)
        var error by mutableStateOf(error)
    }

    enum class ReaderTheme(val title: String, val bg: Color, val text: Color) {
        DEFAULT("Sistema", Color.Unspecified, Color.Unspecified),
        LIGHT("Claro", Color(0xFFFFFFFF), Color(0xFF1C1B1F)),
        SEPIA("Sépia", Color(0xFFFBF0D9), Color(0xFF4A3525)),
        AMOLED("Preto Puro", Color(0xFF000000), Color(0xFFDCDCDC)),
    }

    private fun formatNovelText(raw: String): String {
        if (raw.isBlank()) return ""
        val withoutBreaks = raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</div>"), "\n\n")
            .replace(Regex("(?i)<p[^>]*>"), "")
            .replace(Regex("(?i)<div[^>]*>"), "")
            .replace(Regex("<[^>]+>"), "")

        val unescaped = try {
            HtmlCompat.fromHtml(
                withoutBreaks,
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            ).toString()
        } catch (_: Exception) {
            withoutBreaks
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }

        return unescaped
            .lines()
            .map { it.trim() }
            .filterIndexed { index, line -> line.isNotEmpty() || index > 0 }
            .joinToString("\n\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mangaId = intent.getLongExtra("manga_id", -1L)
        val initialChapterId = intent.getLongExtra("chapter_id", -1L)

        val getManga: GetManga = Injekt.get()
        val getChapter: GetChapter = Injekt.get()
        val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
        val updateChapter: UpdateChapter = Injekt.get()
        val sourceManager: SourceManager = Injekt.get()
        val translationPreferences: TranslationPreferences = Injekt.get()

        val prefs = getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)

        setContent {
            val scope = rememberCoroutineScope()
            var manga by remember { mutableStateOf<Manga?>(null) }
            var allChapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
            val loadedChapters = remember { mutableStateListOf<ChapterItemState>() }
            var isTranslated by remember { mutableStateOf(translationPreferences.autoTranslateNovels().get()) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var menuVisible by remember { mutableStateOf(false) }

            var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 17f)) }
            val savedThemeName = prefs.getString("theme_name", null)
            var readerTheme by remember {
                mutableStateOf(
                    savedThemeName?.let { name ->
                        if (name == "DARK") ReaderTheme.AMOLED
                        else runCatching { ReaderTheme.valueOf(name) }.getOrNull()
                    } ?: run {
                        val oldIdx = prefs.getInt("theme_index", 0)
                        if (oldIdx >= 3) ReaderTheme.AMOLED
                        else ReaderTheme.entries.getOrElse(oldIdx) { ReaderTheme.DEFAULT }
                    },
                )
            }

            val lazyListState = rememberLazyListState()

            // Immersive system bars handling
            val insetsController = remember(this) {
                WindowCompat.getInsetsController(window, window.decorView)
            }

            // Function to load a specific chapter's text
            fun loadChapterContent(state: ChapterItemState) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val localText = NovelDownloadManager.getDownloadedChapterText(mangaId, state.chapter.id)
                        val rawText = if (localText != null) {
                            localText
                        } else {
                            val m = manga ?: getManga.await(mangaId) ?: throw Exception("Obra não encontrada")
                            val source = sourceManager.get(m.source) as? NovelSourceWrapper
                                ?: throw Exception("Fonte não é um plugin de novel")
                            source.getChapterText(m.url, state.chapter.url)
                        }

                        val cleanText = formatNovelText(rawText)
                        state.originalText = cleanText
                        state.isLoading = false

                        // Check cached translation or auto-translate
                        val cached = NovelTranslator.getCached(state.chapter.id)
                        if (cached != null) {
                            state.translatedText = formatNovelText(cached)
                        } else if (isTranslated) {
                            state.isTranslating = true
                            val translated = NovelTranslator.translate(state.chapter.id, cleanText)
                            state.translatedText = formatNovelText(translated)
                            state.isTranslating = false
                        }
                    } catch (e: Exception) {
                        state.error = e.message ?: "Erro ao carregar texto"
                        state.isLoading = false
                    }
                }
            }

            fun navigateToChapter(targetChapter: Chapter) {
                val existingIndex = loadedChapters.indexOfFirst { it.chapter.id == targetChapter.id }
                if (existingIndex != -1) {
                    scope.launch { lazyListState.animateScrollToItem(existingIndex) }
                } else {
                    loadedChapters.clear()
                    val newItem = ChapterItemState(chapter = targetChapter)
                    loadedChapters.add(newItem)
                    loadChapterContent(newItem)
                    scope.launch { lazyListState.scrollToItem(0) }
                }
            }

            // Initial load of chapters and target chapter
            LaunchedEffect(mangaId, initialChapterId) {
                withContext(Dispatchers.IO) {
                    manga = getManga.await(mangaId)
                    val chapters = getChaptersByMangaId.await(mangaId).sortedBy { it.sourceOrder }
                    allChapters = chapters

                    val initial = chapters.firstOrNull { it.id == initialChapterId }
                        ?: getChapter.await(initialChapterId)

                    if (initial != null) {
                        val firstItem = ChapterItemState(chapter = initial)
                        loadedChapters.add(firstItem)
                        loadChapterContent(firstItem)
                    }
                }
            }

            // Infinite Scroll: observe scroll position and load next chapter when nearing the end
            val shouldLoadNextChapter by remember {
                derivedStateOf {
                    val total = lazyListState.layoutInfo.totalItemsCount
                    val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 0 && lastVisible >= total - 1
                }
            }

            LaunchedEffect(shouldLoadNextChapter) {
                if (shouldLoadNextChapter && loadedChapters.isNotEmpty() && allChapters.isNotEmpty()) {
                    val lastLoaded = loadedChapters.last().chapter
                    val currentIndex = allChapters.indexOfFirst { it.id == lastLoaded.id }
                    if (currentIndex != -1 && currentIndex + 1 < allChapters.size) {
                        val nextChapter = allChapters[currentIndex + 1]
                        if (loadedChapters.none { it.chapter.id == nextChapter.id }) {
                            val newItem = ChapterItemState(chapter = nextChapter)
                            loadedChapters.add(newItem)
                            loadChapterContent(newItem)
                        }
                    }
                }
            }

            // Automatic mark as read when user scrolls through a chapter
            LaunchedEffect(lazyListState) {
                snapshotFlow { lazyListState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { index ->
                        if (index in loadedChapters.indices) {
                            val item = loadedChapters[index]
                            if (!item.chapter.read) {
                                withContext(Dispatchers.IO) {
                                    updateChapter.await(ChapterUpdate(id = item.chapter.id, read = true))
                                }
                            }
                        }
                    }
            }

            // Color scheme resolution based on ReaderTheme
            val isDark = isSystemInDarkTheme()
            val isUiDark = when (readerTheme) {
                ReaderTheme.DEFAULT -> isDark
                ReaderTheme.LIGHT -> false
                ReaderTheme.SEPIA -> false
                ReaderTheme.AMOLED -> true
            }

            val resolvedBg = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
                ReaderTheme.LIGHT -> Color(0xFFFFFFFF)
                ReaderTheme.SEPIA -> Color(0xFFFBF0D9)
                ReaderTheme.AMOLED -> Color(0xFF000000)
            }
            val resolvedTextColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF1C1B1F)
                ReaderTheme.LIGHT -> Color(0xFF1C1B1F)
                ReaderTheme.SEPIA -> Color(0xFF4A3525)
                ReaderTheme.AMOLED -> Color(0xFFDCDCDC)
            }

            val uiBgColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xF5181818) else Color(0xF8FFFFFF)
                ReaderTheme.LIGHT -> Color(0xF8FFFFFF)
                ReaderTheme.SEPIA -> Color(0xF8FBF0D9)
                ReaderTheme.AMOLED -> Color(0xF8000000)
            }
            val uiSurfaceColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFF222222) else Color(0xFFFFFFFF)
                ReaderTheme.LIGHT -> Color(0xFFFFFFFF)
                ReaderTheme.SEPIA -> Color(0xFFF7EEDB)
                ReaderTheme.AMOLED -> Color(0xFF161616)
            }
            val uiTextColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFFEDEDED) else Color(0xFF1C1B1F)
                ReaderTheme.LIGHT -> Color(0xFF1C1B1F)
                ReaderTheme.SEPIA -> Color(0xFF4A3525)
                ReaderTheme.AMOLED -> Color(0xFFEDEDED)
            }
            val uiSubtextColor = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color(0xFFA0A0A0) else Color(0xFF666666)
                ReaderTheme.LIGHT -> Color(0xFF666666)
                ReaderTheme.SEPIA -> Color(0xFF7A604D)
                ReaderTheme.AMOLED -> Color(0xFFA0A0A0)
            }
            val uiIconTint = when (readerTheme) {
                ReaderTheme.DEFAULT -> if (isDark) Color.White else Color(0xFF1C1B1F)
                ReaderTheme.LIGHT -> Color(0xFF1C1B1F)
                ReaderTheme.SEPIA -> Color(0xFF4A3525)
                ReaderTheme.AMOLED -> Color.White
            }

            LaunchedEffect(menuVisible, isUiDark) {
                if (menuVisible) {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                    insetsController.isAppearanceLightStatusBars = !isUiDark
                    insetsController.isAppearanceLightNavigationBars = !isUiDark
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(resolvedBg),
            ) {
                // Reading content (Edge to Edge)
                if (loadedChapters.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                menuVisible = !menuVisible
                            },
                        contentPadding = PaddingValues(
                            top = 48.dp,
                            bottom = 96.dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = loadedChapters,
                            key = { _, item -> item.chapter.id },
                        ) { index, item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        menuVisible = !menuVisible
                                    }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                            ) {
                                // Chapter Header Divider
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 32.dp),
                                        color = resolvedTextColor.copy(alpha = 0.2f),
                                    )
                                }

                                Text(
                                    text = item.chapter.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = if (readerTheme == ReaderTheme.DEFAULT) MaterialTheme.colorScheme.primary else resolvedTextColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 20.dp),
                                    textAlign = TextAlign.Center,
                                )

                                when {
                                    item.isLoading || item.isTranslating -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 40.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = if (item.isTranslating) "Traduzindo para Português..." else "Carregando capítulo...",
                                                color = resolvedTextColor,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                    item.error != null -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = "Erro: ${item.error}",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = {
                                                item.isLoading = true
                                                item.error = null
                                                loadChapterContent(item)
                                            }) {
                                                Text("Tentar Novamente")
                                            }
                                        }
                                    }
                                    else -> {
                                        val displayText = if (isTranslated && item.translatedText != null) {
                                            item.translatedText!!
                                        } else {
                                            item.originalText ?: ""
                                        }

                                        Text(
                                            text = displayText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = fontSize.sp,
                                                lineHeight = (fontSize * 1.6f).sp,
                                            ),
                                            color = resolvedTextColor,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Manhwa-style Top App Bar (Slides in/out on tap)
                AnimatedVisibility(
                    visible = menuVisible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    TopAppBar(
                        modifier = Modifier.statusBarsPadding(),
                        title = {
                            Column {
                                val currentVisibleChapter = loadedChapters.getOrNull(lazyListState.firstVisibleItemIndex)?.chapter
                                Text(
                                    text = currentVisibleChapter?.name ?: manga?.title ?: "Novel",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = uiTextColor,
                                )
                                manga?.title?.let {
                                    Text(
                                        text = it,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = uiSubtextColor,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = uiIconTint,
                                )
                            }
                        },
                        actions = {
                            // Font / Theme Settings Button
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatSize,
                                    contentDescription = "Personalizar leitura",
                                    tint = uiIconTint,
                                )
                            }

                            // Translation Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                IconButton(onClick = {
                                    isTranslated = !isTranslated
                                    if (isTranslated) {
                                        loadedChapters.forEach { item ->
                                            if (item.translatedText == null && item.originalText != null) {
                                                scope.launch(Dispatchers.IO) {
                                                    item.isTranslating = true
                                                    item.translatedText = formatNovelText(NovelTranslator.translate(item.chapter.id, item.originalText!!))
                                                    item.isTranslating = false
                                                }
                                            }
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Translate,
                                        contentDescription = "Traduzir",
                                        tint = if (isTranslated) MaterialTheme.colorScheme.primary else uiSubtextColor,
                                    )
                                }
                                Text(
                                    text = if (isTranslated) "PT" else "ORIG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTranslated) MaterialTheme.colorScheme.primary else uiSubtextColor,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = uiBgColor,
                            titleContentColor = uiTextColor,
                            navigationIconContentColor = uiIconTint,
                            actionIconContentColor = uiIconTint,
                        ),
                    )
                }

                // Manhwa-style Bottom Bar with Chapter Navigator (Slides in/out on tap)
                AnimatedVisibility(
                    visible = menuVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(
                        color = uiBgColor,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            val currentChapter = loadedChapters.getOrNull(lazyListState.firstVisibleItemIndex)?.chapter
                            val currentIdx = allChapters.indexOfFirst { it.id == currentChapter?.id }

                            // Previous / Next chapter buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (currentIdx > 0) {
                                            navigateToChapter(allChapters[currentIdx - 1])
                                        }
                                    },
                                    enabled = currentIdx > 0,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SkipPrevious,
                                        contentDescription = "Capítulo anterior",
                                        tint = if (currentIdx > 0) uiIconTint else uiSubtextColor.copy(alpha = 0.4f),
                                    )
                                }

                                Text(
                                    text = if (allChapters.isNotEmpty() && currentIdx != -1) {
                                        "${currentIdx + 1} / ${allChapters.size}"
                                    } else {
                                        currentChapter?.name ?: ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = uiTextColor,
                                )

                                IconButton(
                                    onClick = {
                                        if (currentIdx != -1 && currentIdx + 1 < allChapters.size) {
                                            navigateToChapter(allChapters[currentIdx + 1])
                                        }
                                    },
                                    enabled = currentIdx != -1 && currentIdx + 1 < allChapters.size,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SkipNext,
                                        contentDescription = "Próximo capítulo",
                                        tint = if (currentIdx != -1 && currentIdx + 1 < allChapters.size) uiIconTint else uiSubtextColor.copy(alpha = 0.4f),
                                    )
                                }
                            }

                            // Chapter Scrubbing Slider
                            if (allChapters.size > 1) {
                                val safeIdx = currentIdx.coerceAtLeast(0)
                                var sliderValue by remember(safeIdx) { mutableFloatStateOf(safeIdx.toFloat()) }

                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    onValueChangeFinished = {
                                        val targetIdx = sliderValue.toInt().coerceIn(0, allChapters.size - 1)
                                        navigateToChapter(allChapters[targetIdx])
                                    },
                                    valueRange = 0f..(allChapters.size - 1).toFloat(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = uiSubtextColor.copy(alpha = 0.3f),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // Reader Customization Dialog (Kindle / E-Book Style)
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    containerColor = uiSurfaceColor,
                    titleContentColor = uiTextColor,
                    textContentColor = uiTextColor,
                    title = {
                        Text(
                            text = "Configurações do Leitor",
                            color = uiTextColor,
                        )
                    },
                    text = {
                        Column {
                            // Font Size Adjustment
                            Text(
                                text = "Tamanho da Fonte: ${fontSize.toInt()} sp",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Slider(
                                value = fontSize,
                                onValueChange = {
                                    fontSize = it
                                    prefs.edit().putFloat("font_size", it).apply()
                                },
                                valueRange = 13f..28f,
                                steps = 15,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = uiSubtextColor.copy(alpha = 0.3f),
                                ),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Theme Selection
                            Text(
                                text = "Cor de Fundo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = uiTextColor,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ReaderTheme.entries.forEach { theme ->
                                    val isSelected = readerTheme == theme
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                readerTheme = theme
                                                prefs.edit()
                                                    .putString("theme_name", theme.name)
                                                    .putInt("theme_index", theme.ordinal)
                                                    .apply()
                                            }
                                            .padding(6.dp),
                                    ) {
                                        val circleBg = when (theme) {
                                            ReaderTheme.DEFAULT -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                                            ReaderTheme.LIGHT -> Color(0xFFFFFFFF)
                                            ReaderTheme.SEPIA -> Color(0xFFFBF0D9)
                                            ReaderTheme.AMOLED -> Color(0xFF000000)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(circleBg)
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else uiSubtextColor.copy(alpha = 0.4f),
                                                    shape = CircleShape,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = theme.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else uiSubtextColor,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettingsDialog = false }) {
                            Text(
                                text = "Fechar",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}
