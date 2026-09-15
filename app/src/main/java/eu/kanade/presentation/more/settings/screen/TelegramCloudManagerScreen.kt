package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.telegram.CloudManga
import eu.kanade.tachiyomi.data.telegram.TelegramCloudManager
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TelegramCloudManagerScreen : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val cloudManager = remember { Injekt.get<TelegramCloudManager>() }
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var mangas by remember { mutableStateOf(cloudManager.getCloudIndex()) }
        var isSyncing by remember { mutableStateOf(false) }
        var downloadingManga by remember { mutableStateOf<String?>(null) }
        var downloadProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
        var downloadingChapterKey by remember { mutableStateOf<String?>(null) }
        var mangaToDelete by remember { mutableStateOf<CloudManga?>(null) }

        LaunchedEffect(Unit) {
            cloudManager.initializeTdlib()
            mangas = cloudManager.getCloudIndex()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Gerenciador da Nuvem") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    try {
                                        snackbarHostState.showSnackbar("Sincronizando com a Nuvem Telegram...")
                                        mangas = cloudManager.syncFromTelegram()
                                        snackbarHostState.showSnackbar("Sincronização concluída! ${mangas.size} obras sincronizadas.")
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Erro ao sincronizar: ${e.message}")
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.CloudSync, contentDescription = "Sincronizar com o Telegram")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (mangas.isEmpty() && !isSyncing) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhuma obra na lista local",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Clique abaixo para varrer seu grupo do Telegram e buscar os capítulos que já foram enviados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    try {
                                        mangas = cloudManager.syncFromTelegram()
                                        snackbarHostState.showSnackbar("Sincronização concluída!")
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizar do Telegram")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(mangas, key = { it.title }) { manga ->
                            val isExpanded = expandedMap[manga.title] == true
                            val isDownloadingThis = downloadingManga == manga.title

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = manga.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${manga.chapters.size} capítulos no Telegram",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { mangaToDelete = manga },
                                            enabled = downloadingManga == null && downloadingChapterKey == null
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Apagar obra da Nuvem",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        IconButton(onClick = { expandedMap[manga.title] = !isExpanded }) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                contentDescription = "Expandir"
                                            )
                                        }
                                    }

                                    if (isDownloadingThis && downloadProgress != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val (curr, total) = downloadProgress!!
                                        val frac = if (total > 0) curr.toFloat() / total.toFloat() else 0f
                                        LinearProgressIndicator(
                                            progress = { frac },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Baixando capítulo $curr de $total para a Fonte Local...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                downloadingManga = manga.title
                                                try {
                                                    val success = cloudManager.downloadMangaToLocalSource(manga.title) { curr, tot ->
                                                        downloadProgress = curr to tot
                                                    }
                                                    if (success) {
                                                        snackbarHostState.showSnackbar("${manga.title} salvo na Fonte Local!")
                                                    } else {
                                                        val err = cloudManager.lastDownloadError
                                                        val msg = if (!err.isNullOrBlank()) "Erro ao baixar: $err" else "Nenhum capítulo novo foi baixado para ${manga.title}"
                                                        snackbarHostState.showSnackbar(msg)
                                                    }
                                                } finally {
                                                    downloadingManga = null
                                                    downloadProgress = null
                                                    mangas = cloudManager.getCloudIndex()
                                                }
                                            }
                                        },
                                        enabled = downloadingManga == null && downloadingChapterKey == null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Outlined.FolderSpecial, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Baixar todos para Fonte Local (${manga.chapters.size})")
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            manga.chapters.forEach { chapter ->
                                                val chapterKey = "${manga.title}_${chapter.name}"
                                                val isThisChapterDownloading = downloadingChapterKey == chapterKey

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = chapter.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable(enabled = downloadingManga == null && downloadingChapterKey == null) {
                                                                scope.launch {
                                                                    downloadingChapterKey = chapterKey
                                                                    try {
                                                                        snackbarHostState.showSnackbar("Baixando ${chapter.name}...")
                                                                        val ok = cloudManager.downloadSingleChapterToLocalSource(manga.title, chapter)
                                                                        if (ok) {
                                                                            snackbarHostState.showSnackbar("${chapter.name} salvo na Fonte Local!")
                                                                        } else {
                                                                            val err = cloudManager.lastDownloadError
                                                                            val msg = if (!err.isNullOrBlank()) "Erro ao baixar ${chapter.name}: $err" else "Erro ao baixar ${chapter.name}"
                                                                            snackbarHostState.showSnackbar(msg)
                                                                        }
                                                                    } finally {
                                                                        downloadingChapterKey = null
                                                                        mangas = cloudManager.getCloudIndex()
                                                                    }
                                                                }
                                                            }
                                                    )
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    snackbarHostState.showSnackbar("Apagando ${chapter.name}...")
                                                                    cloudManager.deleteChapter(manga.title, chapter)
                                                                    mangas = cloudManager.getCloudIndex()
                                                                    snackbarHostState.showSnackbar("${chapter.name} apagado!")
                                                                }
                                                            },
                                                            enabled = downloadingManga == null && downloadingChapterKey == null,
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Delete,
                                                                contentDescription = "Apagar capítulo",
                                                                modifier = Modifier.size(18.dp),
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        if (isThisChapterDownloading) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(20.dp),
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            IconButton(
                                                                onClick = {
                                                                    scope.launch {
                                                                        downloadingChapterKey = chapterKey
                                                                        try {
                                                                            snackbarHostState.showSnackbar("Baixando ${chapter.name}...")
                                                                            val ok = cloudManager.downloadSingleChapterToLocalSource(manga.title, chapter)
                                                                            if (ok) {
                                                                                snackbarHostState.showSnackbar("${chapter.name} salvo na Fonte Local!")
                                                                            } else {
                                                                                val err = cloudManager.lastDownloadError
                                                                                val msg = if (!err.isNullOrBlank()) "Erro ao baixar ${chapter.name}: $err" else "Erro ao baixar ${chapter.name}"
                                                                                snackbarHostState.showSnackbar(msg)
                                                                            }
                                                                        } finally {
                                                                            downloadingChapterKey = null
                                                                            mangas = cloudManager.getCloudIndex()
                                                                        }
                                                                    }
                                                                },
                                                                enabled = downloadingManga == null && downloadingChapterKey == null,
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Download,
                                                                    contentDescription = "Baixar capítulo",
                                                                    modifier = Modifier.size(20.dp),
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (mangaToDelete != null) {
            val target = mangaToDelete!!
            AlertDialog(
                onDismissRequest = { mangaToDelete = null },
                title = { Text("Apagar Obra da Nuvem?") },
                text = { Text("Deseja apagar \"${target.title}\" e todos os seus ${target.chapters.size} capítulos da Nuvem do Telegram?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val title = target.title
                            mangaToDelete = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Apagando $title da Nuvem...")
                                val ok = cloudManager.deleteManga(title)
                                mangas = cloudManager.getCloudIndex()
                                if (ok) {
                                    snackbarHostState.showSnackbar("$title apagado da Nuvem!")
                                }
                            }
                        }
                    ) {
                        Text("Apagar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mangaToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
