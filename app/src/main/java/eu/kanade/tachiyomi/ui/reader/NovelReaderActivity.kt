package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.extension.novel.NovelSourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mangaId = intent.getLongExtra("manga_id", -1L)
        val chapterId = intent.getLongExtra("chapter_id", -1L)

        val getManga: GetManga = Injekt.get()
        val getChapter: GetChapter = Injekt.get()
        val sourceManager: SourceManager = Injekt.get()

        setContent {
            var textContent by remember { mutableStateOf<String?>(null) }
            var errorContent by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(mangaId, chapterId) {
                withContext(Dispatchers.IO) {
                    try {
                        val manga = getManga.await(mangaId) ?: throw Exception("Manga not found")
                        val chapter = getChapter.await(chapterId) ?: throw Exception("Chapter not found")
                        val source = sourceManager.get(manga.source) as? NovelSourceWrapper 
                            ?: throw Exception("Source not found or not a novel plugin")

                        textContent = source.getChapterText(manga.url, chapter.url)
                    } catch (e: Exception) {
                        errorContent = e.message ?: "Unknown error"
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (textContent != null) {
                        Text(
                            text = textContent!!,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else if (errorContent != null) {
                        Text(
                            text = "Error: $errorContent",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}
