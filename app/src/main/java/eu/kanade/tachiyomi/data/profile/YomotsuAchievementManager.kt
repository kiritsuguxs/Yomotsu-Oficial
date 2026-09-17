package eu.kanade.tachiyomi.data.profile

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook

enum class AchievementCategory(val icon: ImageVector) {
    READING(Icons.Outlined.MenuBook),
    COLLECTION(Icons.Outlined.LibraryBooks),
    DOWNLOADS(Icons.Outlined.CloudDownload),
    HARDCORE(Icons.Outlined.LocalFireDepartment),
    SPECIAL(Icons.Outlined.EmojiEvents)
}

data class YomotsuAchievement(
    val id: String,
    val name: String,
    val description: String,
    val category: AchievementCategory,
    val isUnlocked: (chaptersRead: Int, mangasInLibrary: Int, downloads: Int) -> Boolean
)

object YomotsuAchievementManager {
    
    val ALL_ACHIEVEMENTS = listOf(
        // === LEITURA ===
        YomotsuAchievement("read_1", "Primeiros Passos", "Leia seu primeiro capítulo.", AchievementCategory.READING) { r, _, _ -> r >= 1 },
        YomotsuAchievement("read_100", "Devorador de Páginas", "Leia 100 capítulos.", AchievementCategory.READING) { r, _, _ -> r >= 100 },
        YomotsuAchievement("read_500", "Rato de Biblioteca", "Leia 500 capítulos.", AchievementCategory.READING) { r, _, _ -> r >= 500 },
        YomotsuAchievement("read_1k", "Milagreiro", "Leia 1.000 capítulos.", AchievementCategory.READING) { r, _, _ -> r >= 1000 },
        YomotsuAchievement("read_5k", "Mestre do Conhecimento", "Leia 5.000 capítulos.", AchievementCategory.READING) { r, _, _ -> r >= 5000 },
        YomotsuAchievement("read_10k", "Lenda Viva", "Leia 10.000 capítulos.", AchievementCategory.READING) { r, _, _ -> r >= 10000 },
        YomotsuAchievement("read_15k", "Vício Incurável", "Leia 15.000 capítulos.", AchievementCategory.READING) { r, _, _ -> r >= 15000 },
        YomotsuAchievement("read_20k", "Deus da Leitura", "Leia 20.000 capítulos.", AchievementCategory.READING) { r, _, _ -> r >= 20000 },

        // === BIBLIOTECA ===
        YomotsuAchievement("lib_10", "Início da Coleção", "Tenha 10 obras na biblioteca.", AchievementCategory.COLLECTION) { _, l, _ -> l >= 10 },
        YomotsuAchievement("lib_50", "Colecionador", "Tenha 50 obras na biblioteca.", AchievementCategory.COLLECTION) { _, l, _ -> l >= 50 },
        YomotsuAchievement("lib_100", "Acumulador Compulsivo", "Tenha 100 obras na biblioteca.", AchievementCategory.COLLECTION) { _, l, _ -> l >= 100 },
        YomotsuAchievement("lib_250", "Arquivista", "Tenha 250 obras na biblioteca.", AchievementCategory.COLLECTION) { _, l, _ -> l >= 250 },
        YomotsuAchievement("lib_500", "Buraco Negro", "Tenha 500 obras na biblioteca.", AchievementCategory.COLLECTION) { _, l, _ -> l >= 500 },

        // === DOWNLOADS ===
        YomotsuAchievement("dl_1", "Precavido", "Baixe seu primeiro capítulo.", AchievementCategory.DOWNLOADS) { _, _, d -> d >= 1 },
        YomotsuAchievement("dl_100", "Viagem Longa", "Baixe 100 capítulos.", AchievementCategory.DOWNLOADS) { _, _, d -> d >= 100 },
        YomotsuAchievement("dl_500", "Peso Pesado", "Baixe 500 capítulos.", AchievementCategory.DOWNLOADS) { _, _, d -> d >= 500 },
        YomotsuAchievement("dl_1k", "Servidor Local", "Baixe 1.000 capítulos.", AchievementCategory.DOWNLOADS) { _, _, d -> d >= 1000 },
        YomotsuAchievement("dl_5k", "A Nuvem Sou Eu", "Baixe 5.000 capítulos.", AchievementCategory.DOWNLOADS) { _, _, d -> d >= 5000 },

        // === HARDCORE ===
        YomotsuAchievement("hc_veterano", "O Verdadeiro Veterano", "Tenha 15.000 lidos e 200 obras na biblioteca.", AchievementCategory.HARDCORE) { r, l, _ -> r >= 15000 && l >= 200 }
        
        // VOCÊ PODE COPIAR E COLAR AQUI PARA CRIAR AS OUTRAS 280 CONQUISTAS DEPOIS!
    )
}
