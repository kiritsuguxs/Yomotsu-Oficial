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
        YomotsuTitle(1, "Mortal Comum", Color(0xFF9E9E9E), Color(0xFFBDBDBD)), // Cinza suave
        YomotsuTitle(10, "Desperto", Color(0xFF8D6E63), Color(0xFFA1887F)), // Marrom/Terra minimalista
        YomotsuTitle(20, "Caçador Novato", Color(0xFF78909C), Color(0xFF90A4AE)), // Azul metálico suave
        YomotsuTitle(30, "Discípulo Externo", Color(0xFF5D4037), Color(0xFF795548)), // Madeira/Bronze escuro
        YomotsuTitle(40, "Explorador do Abismo", Color(0xFF263238), Color(0xFF37474F)), // Abismo (Quase preto para chumbo)
        YomotsuTitle(50, "Caçador Rank C", Color(0xFF455A64), Color(0xFF546E7A)), // Aço escuro
        YomotsuTitle(60, "Especialista Marcial", Color(0xFF827717), Color(0xFF9E9D24)), // Musgo/Verde envelhecido
        YomotsuTitle(70, "Caçador Rank B", Color(0xFF006064), Color(0xFF00838F)), // Petróleo escuro
        YomotsuTitle(80, "Mestre das Sombras", Color(0xFF121212), Color(0xFF424242)), // Sombras (Preto absoluto para grafite)
        YomotsuTitle(90, "Caçador Rank A", Color(0xFF880E4F), Color(0xFFAD1457)), // Vinho/Bordô elegante
        YomotsuTitle(100, "Formação do Núcleo", Color(0xFFB71C1C), Color(0xFFC62828)), // Vermelho sangue puro e fechado (Zero neon)
        YomotsuTitle(110, "Senhor de Domínio", Color(0xFFF57F17), Color(0xFFF9A825)), // Ouro envelhecido
        YomotsuTitle(120, "Caçador Rank S", Color(0xFF1A237E), Color(0xFF283593)), // Azul Marinho Profundo
        YomotsuTitle(130, "Caçador Nacional", Color(0xFF004D40), Color(0xFF00695C)), // Verde Esmeralda escuro
        YomotsuTitle(140, "Alma Nascente", Color(0xFFE0E0E0), Color(0xFFFFFFFF)), // Branco etéreo limpo
        YomotsuTitle(150, "Soberano", Color(0xFFFFD54F), Color(0xFFFFE082)), // Ouro pálido e nobre
        YomotsuTitle(160, "Monarca", Color(0xFFCFD8DC), Color(0xFFECEFF1)), // Prata polida clara
        YomotsuTitle(170, "Shinigami", Color(0xFF212121), Color(0xFF616161)), // Escuridão e névoa
        YomotsuTitle(180, "Imortal", Color(0xFF311B92), Color(0xFF4527A0)), // Roxo imperial escuro
        YomotsuTitle(190, "Deus Marcial", Color(0xFFBF360C), Color(0xFFD84315)), // Fogo profundo (laranja escuro)
        YomotsuTitle(200, "Izanagi do Submundo", Color(0xFF000000), Color(0xFF1A1A1A)) // O Vazio Absoluto (Preto)
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
