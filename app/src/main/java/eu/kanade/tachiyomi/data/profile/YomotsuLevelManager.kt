package eu.kanade.tachiyomi.data.profile

import androidx.compose.ui.graphics.Color

data class YomotsuTitle(
    val unlockLevel: Int,
    val name: String,
    val colorStart: Color,
    val colorEnd: Color
)

object YomotsuLevelManager {
    
    const val MAX_LEVEL = 200
    const val XP_PER_CHAPTER_READ = 90
    const val XP_PER_CHAPTER_DOWNLOAD = 90
    
    // Lista de Títulos baseada na evolução do Nível 1 ao 200
    val ALL_TITLES = listOf(
        YomotsuTitle(1, "Mortal Comum", Color(0xFF9E9E9E), Color(0xFFFFFFFF)), // Cinza a Branco
        YomotsuTitle(10, "Desperto", Color(0xFFFFFFFF), Color(0xFFAEEA00)), // Branco a Verde Limão
        YomotsuTitle(20, "Caçador Novato", Color(0xFFAEEA00), Color(0xFF00E676)), // Verde Limão a Verde Esmeralda
        YomotsuTitle(30, "Discípulo Externo", Color(0xFF00E676), Color(0xFF00E5FF)), // Esmeralda a Ciano
        YomotsuTitle(40, "Explorador do Abismo", Color(0xFF00E5FF), Color(0xFF29B6F6)), // Ciano a Azul Claro
        YomotsuTitle(50, "Caçador Rank C", Color(0xFF29B6F6), Color(0xFF00B0FF)), // Azul Claro a Celeste
        YomotsuTitle(60, "Especialista Marcial", Color(0xFF00B0FF), Color(0xFF2962FF)), // Celeste a Azul Royal
        YomotsuTitle(70, "Caçador Rank B", Color(0xFF2962FF), Color(0xFF3D5AFE)), // Royal a Índigo
        YomotsuTitle(80, "Mestre das Sombras", Color(0xFF3D5AFE), Color(0xFF651FFF)), // Índigo a Roxo Metálico
        YomotsuTitle(90, "Caçador Rank A", Color(0xFF651FFF), Color(0xFFD500F9)), // Roxo a Violeta Intenso
        YomotsuTitle(100, "Formação do Núcleo", Color(0xFFD500F9), Color(0xFFFF1744)), // Violeta a Rosa Choque/Fúcsia
        YomotsuTitle(110, "Senhor de Domínio", Color(0xFFFF1744), Color(0xFFFF3D00)), // Fúcsia a Laranja
        YomotsuTitle(120, "Caçador Rank S", Color(0xFFFF3D00), Color(0xFFD50000)), // Laranja a Vermelho Fogo
        YomotsuTitle(130, "Caçador Nacional", Color(0xFFD50000), Color(0xFF8E0000)), // Vermelho Fogo a Vermelho Sangue
        YomotsuTitle(140, "Alma Nascente", Color(0xFF8E0000), Color(0xFFFFC400)), // Sangue a Dourado Escuro
        YomotsuTitle(150, "Soberano", Color(0xFFFFC400), Color(0xFFFFEA00)), // Dourado a Ouro Brilhante
        YomotsuTitle(160, "Monarca", Color(0xFFFFEA00), Color(0xFFF5F5F5)), // Ouro a Prata
        YomotsuTitle(170, "Shinigami", Color(0xFF212121), Color(0xFFB71C1C)), // Preto a Vermelho Carmesim
        YomotsuTitle(180, "Imortal", Color(0xFF212121), Color(0xFF4A148C)), // Preto a Roxo Profundo
        YomotsuTitle(190, "Deus Marcial", Color(0xFF4A148C), Color(0xFFFFD600)), // Roxo Profundo a Ouro
        YomotsuTitle(200, "Izanagi do Submundo", Color(0xFF000000), Color(0xFF00E5FF)) // Cósmico: Preto ao Ciano
    )

    /**
     * Fórmula Curva Exponencial (Quadrática)
     * XP(Level) = 125 * (Level - 1)^2
     * Isso resulta em exatos 5.000.000 XP para o Nível 200.
     * Early game (Lv 10) = 10.125 XP
     * Mid game (Lv 100) = 1.225.125 XP
     * Late game (Lv 150) = 2.775.125 XP
     * Endgame (Lv 200) = 4.950.125 XP
     */
    fun calculateLevelFromXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL) {
            val nextLevelXp = 125L * (level) * (level)
            if (xp < nextLevelXp) {
                break
            }
            level++
        }
        return level
    }

    fun getXpRequiredForLevel(level: Int): Long {
        if (level <= 1) return 0L
        if (level > MAX_LEVEL) return 125L * (MAX_LEVEL - 1) * (MAX_LEVEL - 1)
        return 125L * (level - 1) * (level - 1)
    }

    fun getUnlockedTitles(currentLevel: Int): List<YomotsuTitle> {
        return ALL_TITLES.filter { it.unlockLevel <= currentLevel }.reversed() // Mostra os mais picas primeiro
    }

    fun getCurrentTitleByLevel(level: Int): YomotsuTitle {
        return ALL_TITLES.lastOrNull { it.unlockLevel <= level } ?: ALL_TITLES.first()
    }
}
