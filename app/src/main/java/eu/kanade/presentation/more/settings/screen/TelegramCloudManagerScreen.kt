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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.MenuBook
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
                                        mangas = cloudManager.syncFromTelegram()
                                        snackbarHostState.showSnackbar("Sincronização concluída! ${mangas.size} obras encontradas.")
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
                                Icon(Icons.Outlined.CloudSync, contentDescription = "Sincronizar do Telegram")
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
                                                        snackbarHostState.showSnackbar("Nenhum capítulo novo foi baixado para ${manga.title}")
                                                    }
                                                } finally {
                                                    downloadingManga = null
                                                    downloadProgress = null
                                                }
                                            }
                                        },
                                        enabled = downloadingManga == null,
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
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar("Baixando ${chapter.name}...")
                                                                val ok = cloudManager.downloadSingleChapterToLocalSource(manga.title, chapter)
                                                                if (ok) {
                                                                    snackbarHostState.showSnackbar("${chapter.name} salvo na Fonte Local!")
                                                                } else {
                                                                    snackbarHostState.showSnackbar("Erro ao baixar ${chapter.name}")
                                                                }
                                                            }
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = chapter.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Outlined.Download,
                                                        contentDescription = "Disponível na nuvem",
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
