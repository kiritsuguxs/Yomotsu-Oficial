package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.extension.novel.NovelSourceWrapper
import eu.kanade.tachiyomi.extension.novel.translation.NovelTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mangaId = intent.getLongExtra("manga_id", -1L)
        val chapterId = intent.getLongExtra("chapter_id", -1L)

        val getManga: GetManga = Injekt.get()
        val getChapter: GetChapter = Injekt.get()
        val sourceManager: SourceManager = Injekt.get()
        val translationPreferences: TranslationPreferences = Injekt.get()

        setContent {
            val scope = rememberCoroutineScope()
            var manga by remember { mutableStateOf<Manga?>(null) }
            var chapter by remember { mutableStateOf<Chapter?>(null) }
            var originalText by remember { mutableStateOf<String?>(null) }
            var translatedText by remember { mutableStateOf<String?>(null) }
            var isTranslated by remember { mutableStateOf(translationPreferences.autoTranslateNovels().get()) }
            var isTranslating by remember { mutableStateOf(false) }
            var errorContent by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(true) }

            // Fetch chapter text from source
            LaunchedEffect(mangaId, chapterId) {
                withContext(Dispatchers.IO) {
                    try {
                        val m = getManga.await(mangaId) ?: throw Exception("Obra não encontrada")
                        val c = getChapter.await(chapterId) ?: throw Exception("Capítulo não encontrado")
                        manga = m
                        chapter = c

                        val source = sourceManager.get(manga!!.source) as? NovelSourceWrapper
                            ?: throw Exception("Fonte não é um plugin de novel")

                        val raw = source.getChapterText(m.url, c.url)
                        originalText = raw
                        isLoading = false

                        // Check cached translation
                        val cached = NovelTranslator.getCached(chapterId)
                        if (cached != null) {
                            translatedText = cached
                        } else if (isTranslated) {
                            isTranslating = true
                            val result = NovelTranslator.translate(chapterId, raw)
                            translatedText = result
                            isTranslating = false
                        }
                    } catch (e: Exception) {
                        errorContent = e.message ?: "Erro desconhecido ao carregar capítulo"
                        isLoading = false
                    }
                }
            }

            fun toggleTranslation() {
                if (originalText == null) return
                if (!isTranslated) {
                    isTranslated = true
                    if (translatedText == null) {
                        scope.launch {
                            isTranslating = true
                            translatedText = NovelTranslator.translate(chapterId, originalText!!)
                            isTranslating = false
                        }
                    }
                } else {
                    isTranslated = false
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = chapter?.name ?: "Capítulo",
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                if (isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                IconButton(onClick = { toggleTranslation() }) {
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
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isLoading -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Carregando capítulo...",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            errorContent != null -> {
                                Text(
                                    text = "Erro: $errorContent",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(16.dp),
                                )
                            }
                            else -> {
                                val displayText = if (isTranslated && translatedText != null) {
                                    translatedText!!
                                } else {
                                    originalText ?: ""
                                }

                                Text(
                                    text = displayText,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 28.sp,
                                        fontSize = 17.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
