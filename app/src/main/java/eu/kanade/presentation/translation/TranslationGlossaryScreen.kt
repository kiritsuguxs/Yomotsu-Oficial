package eu.kanade.presentation.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.translation.memory.GlossarySaveResult
import eu.kanade.translation.memory.TranslationGlossaryManager
import eu.kanade.translation.memory.TranslationMemoryEntry
import eu.kanade.translation.memory.TranslationMemoryEntryType
import eu.kanade.translation.translator.ComicTranslationContext

class TranslationGlossaryScreen(
    private val mangaTitle: String,
) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val translationContext = remember(mangaTitle) {
            ComicTranslationContext(mangaTitle = mangaTitle, chapterName = "")
        }
        var entries by remember { mutableStateOf(TranslationGlossaryManager.list(translationContext)) }
        var source by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var message by remember { mutableStateOf<String?>(null) }
        var showClearDialog by remember { mutableStateOf(false) }
        var showBulkDialog by remember { mutableStateOf(false) }
        var entryType by remember { mutableStateOf(TranslationMemoryEntryType.TERM) }
        var isProtected by remember { mutableStateOf(false) }
        var learnedCorrectionCount by remember {
            mutableStateOf(TranslationGlossaryManager.learnedCorrectionCount(translationContext))
        }

        fun refresh() {
            entries = TranslationGlossaryManager.list(translationContext)
            learnedCorrectionCount = TranslationGlossaryManager.learnedCorrectionCount(translationContext)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Glossário de tradução") },
                    navigationIcon = {
                        TextButton(onClick = navigator::pop) {
                            Text("Voltar")
                        }
                    },
                    actions = {
                        if (entries.isNotEmpty()) {
                            TextButton(onClick = { showClearDialog = true }) {
                                Text("Limpar")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Column {
                        Text(
                            text = mangaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        Text(
                            text = "Salve nomes, títulos, habilidades e outros termos para manter " +
                                "a tradução consistente nesta obra.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        if (learnedCorrectionCount > 0) {
                            Text(
                                text = "$learnedCorrectionCount correções manuais aprendidas nesta obra.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }

                        GlossaryTypeSelector(
                            selected = entryType,
                            onSelected = {
                                entryType = it
                                if (it == TranslationMemoryEntryType.NAME) isProtected = true
                            },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = isProtected,
                                onCheckedChange = { isProtected = it },
                            )
                            Text(
                                text = "Proteger: o tradutor deve usar exatamente o texto escolhido",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text("Texto original") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            label = { Text("Tradução preferida") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        ) {
                            Button(
                                onClick = {
                                    message = when (
                                        TranslationGlossaryManager.save(
                                            context = translationContext,
                                            source = source,
                                            target = target,
                                            type = entryType,
                                            isProtected = isProtected,
                                        )
                                    ) {
                                        GlossarySaveResult.CREATED -> "Termo adicionado."
                                        GlossarySaveResult.UPDATED -> "Termo atualizado."
                                        GlossarySaveResult.INVALID_SOURCE -> "Digite o texto original."
                                        GlossarySaveResult.INVALID_TARGET -> "Digite a tradução preferida."
                                        GlossarySaveResult.SAME_TEXT -> "A tradução precisa ser diferente do original."
                                    }
                                    if (message == "Termo adicionado." || message == "Termo atualizado.") {
                                        source = ""
                                        target = ""
                                        refresh()
                                    }
                                },
                            ) {
                                Text("Salvar termo")
                            }
                            TextButton(onClick = { showBulkDialog = true }) {
                                Text("Adicionar vários")
                            }
                        }
                        message?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }

                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = "Nenhum termo salvo para esta obra.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                } else {
                    items(entries, key = TranslationMemoryEntry::source) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.source, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = entry.type.displayLabel(entry.isProtected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = entry.target,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    TranslationGlossaryManager.remove(translationContext, entry.source)
                                    refresh()
                                    message = "Termo removido."
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Remover termo",
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "",
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Limpar glossário?") },
                text = { Text("Todos os termos salvos para esta obra serão removidos.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            TranslationGlossaryManager.clear(translationContext)
                            refresh()
                            showClearDialog = false
                            message = "Glossário limpo."
                        },
                    ) {
                        Text("Limpar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }

        if (showBulkDialog) {
            BulkGlossaryDialog(
                type = entryType,
                isProtected = isProtected,
                onDismiss = { showBulkDialog = false },
                onSave = { bulkText ->
                    val result = TranslationGlossaryManager.saveMany(
                        context = translationContext,
                        text = bulkText,
                        type = entryType,
                        isProtected = isProtected,
                    )
                    refresh()
                    showBulkDialog = false
                    message = when {
                        result.saved == 0 -> "Nenhum termo válido foi encontrado."
                        result.skipped > 0 -> "${result.saved} termos salvos; ${result.skipped} linhas ignoradas."
                        else -> "${result.saved} termos salvos."
                    }
                },
            )
        }
    }
}

@Composable
private fun GlossaryTypeSelector(
    selected: TranslationMemoryEntryType,
    onSelected: (TranslationMemoryEntryType) -> Unit,
) {
    Text("Tipo", style = MaterialTheme.typography.labelLarge)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlossaryTypeChip(TranslationMemoryEntryType.TERM, "Termo", selected, onSelected)
            GlossaryTypeChip(TranslationMemoryEntryType.NAME, "Nome", selected, onSelected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlossaryTypeChip(TranslationMemoryEntryType.TITLE, "Título", selected, onSelected)
            GlossaryTypeChip(TranslationMemoryEntryType.TECHNIQUE, "Técnica", selected, onSelected)
        }
    }
}

@Composable
private fun GlossaryTypeChip(
    type: TranslationMemoryEntryType,
    label: String,
    selected: TranslationMemoryEntryType,
    onSelected: (TranslationMemoryEntryType) -> Unit,
) {
    FilterChip(
        selected = selected == type,
        onClick = { onSelected(type) },
        label = { Text(label) },
    )
}

@Composable
private fun BulkGlossaryDialog(
    type: TranslationMemoryEntryType,
    isProtected: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val protectedNameHint = if (type == TranslationMemoryEntryType.NAME && isProtected) {
        " Para nomes protegidos, também é possível informar apenas o nome."
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar vários termos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use uma linha por termo no formato original => tradução.$protectedNameHint")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Lista de termos") },
                    placeholder = { Text("Shadow Monarch => Monarca das Sombras") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(text) },
            ) {
                Text("Salvar lista")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun TranslationMemoryEntryType.displayLabel(isProtected: Boolean): String {
    val label = when (this) {
        TranslationMemoryEntryType.TERM -> "Termo"
        TranslationMemoryEntryType.NAME -> "Nome"
        TranslationMemoryEntryType.TITLE -> "Título"
        TranslationMemoryEntryType.TECHNIQUE -> "Técnica"
        TranslationMemoryEntryType.MANUAL_CORRECTION -> "Correção aprendida"
    }
    return if (isProtected) "$label protegido" else label
}
