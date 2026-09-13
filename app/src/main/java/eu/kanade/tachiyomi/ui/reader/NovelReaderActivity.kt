package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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

    data class ChapterItemState(
        val chapter: Chapter,
        var originalText: String? = null,
        var translatedText: String? = null,
        var isLoading: Boolean = true,
        var isTranslating: Boolean = false,
        var error: String? = null,
    )

    enum class ReaderTheme(val title: String, val bg: Color, val text: Color) {
        DEFAULT("Sistema", Color.Unspecified, Color.Unspecified),
        LIGHT("Claro", Color(0xFFFFFFFF), Color(0xFF1C1B1F)),
        SEPIA("Sépia", Color(0xFFFBF0D9), Color(0xFF4A3525)),
        DARK("Escuro", Color(0xFF121212), Color(0xFFE0E0E0)),
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 17f)) }
            var currentThemeIndex by remember { mutableIntStateOf(prefs.getInt("theme_index", 0)) }
            val readerTheme = ReaderTheme.entries[currentThemeIndex.coerceIn(0, ReaderTheme.entries.size - 1)]

            val lazyListState = rememberLazyListState()

            // Function to load a specific chapter's text
            fun loadChapterContent(state: ChapterItemState) {
                scope.launch(Dispatchers.IO) {
                    try {
                        // 1. Check local download
                        val localText = NovelDownloadManager.getDownloadedChapterText(mangaId, state.chapter.id)
                        val text = if (localText != null) {
                            localText
                        } else {
                            val m = manga ?: getManga.await(mangaId) ?: throw Exception("Obra não encontrada")
                            val source = sourceManager.get(m.source) as? NovelSourceWrapper
                                ?: throw Exception("Fonte não é um plugin de novel")
                            source.getChapterText(m.url, state.chapter.url)
                        }

                        state.originalText = text
                        state.isLoading = false

                        // Check cached translation or auto-translate
                        val cached = NovelTranslator.getCached(state.chapter.id)
                        if (cached != null) {
                            state.translatedText = cached
                        } else if (isTranslated) {
                            state.isTranslating = true
                            state.translatedText = NovelTranslator.translate(state.chapter.id, text)
                            state.isTranslating = false
                        }
                    } catch (e: Exception) {
                        state.error = e.message ?: "Erro ao carregar texto"
                        state.isLoading = false
                    }
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
            val resolvedBg = if (readerTheme.bg != Color.Unspecified) readerTheme.bg else MaterialTheme.colorScheme.background
            val resolvedTextColor = if (readerTheme.text != Color.Unspecified) readerTheme.text else MaterialTheme.colorScheme.onBackground

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                val currentVisibleChapter = loadedChapters.getOrNull(lazyListState.firstVisibleItemIndex)?.chapter
                                Text(
                                    text = currentVisibleChapter?.name ?: manga?.title ?: "Novel",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                manga?.title?.let {
                                    Text(
                                        text = it,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Voltar",
                                )
                            }
                        },
                        actions = {
                            // Font / Theme Settings Button
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatSize,
                                    contentDescription = "Personalizar leitura",
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
                                                    item.translatedText = NovelTranslator.translate(item.chapter.id, item.originalText!!)
                                                    item.isTranslating = false
                                                }
                                            }
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Translate,
                                        contentDescription = "Traduzir",
                                        tint = if (isTranslated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = if (isTranslated) "PT" else "ORIG",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTranslated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
            ) { innerPadding ->
                Surface(
                    color = resolvedBg,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (loadedChapters.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(
                                items = loadedChapters,
                                key = { _, item -> item.chapter.id },
                            ) { index, item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
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
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reader Customization Dialog (Kindle / E-Book Style)
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = { Text("Configurações do Leitor") },
                    text = {
                        Column {
                            // Font Size Adjustment
                            Text(
                                text = "Tamanho da Fonte: ${fontSize.toInt()} sp",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Slider(
                                value = fontSize,
                                onValueChange = {
                                    fontSize = it
                                    prefs.edit().putFloat("font_size", it).apply()
                                },
                                valueRange = 13f..28f,
                                steps = 15,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Theme Selection
                            Text(
                                text = "Cor de Fundo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ReaderTheme.entries.forEachIndexed { idx, theme ->
                                    val isSelected = currentThemeIndex == idx
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                currentThemeIndex = idx
                                                prefs.edit().putInt("theme_index", idx).apply()
                                            }
                                            .padding(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (theme.bg != Color.Unspecified) theme.bg else MaterialTheme.colorScheme.background,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = theme.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettingsDialog = false }) {
                            Text("Fechar")
                        }
                    },
                )
            }
        }
    }
}
