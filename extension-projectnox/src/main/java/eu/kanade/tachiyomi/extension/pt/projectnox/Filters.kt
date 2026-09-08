package eu.kanade.tachiyomi.extension.pt.projectnox

import eu.kanade.tachiyomi.source.model.Filter

/**
 * Filter by manga format type (Manhwa, Manga, Manhua).
 */
class KindFilter : Filter.Select<String>(
    "Formato",
    arrayOf("Todos", "Manhwa", "Manga", "Manhua"),
) {
    fun toUriPart(): String = when (state) {
        1 -> "MANHWA"
        2 -> "MANGA"
        3 -> "MANHUA"
        else -> ""
    }
}

/**
 * Filter by publication status.
 */
class StatusFilter : Filter.Select<String>(
    "Status",
    arrayOf("Todos", "Em andamento", "Completo", "Hiato", "Cancelado"),
) {
    fun toUriPart(): String = when (state) {
        1 -> "ONGOING"
        2 -> "COMPLETED"
        3 -> "HIATUS"
        4 -> "CANCELLED"
        else -> ""
    }
}

/**
 * Sort order filter.
 */
class SortFilter : Filter.Select<String>(
    "Ordenar por",
    arrayOf("Recentes", "Populares", "Avaliação", "A-Z", "Z-A"),
) {
    fun toUriPart(): String = when (state) {
        0 -> "recentes"
        1 -> "populares"
        2 -> "avaliacao"
        3 -> "az"
        4 -> "za"
        else -> "recentes"
    }
}

/**
 * Tag/genre filter with checkboxes.
 */
class TagFilter(tags: List<Tag>) : Filter.Group<Tag>("Gêneros e Tags", tags)

class Tag(val slug: String, name: String) : Filter.CheckBox(name)

/**
 * Returns the list of available tags/genres from the catalog.
 * These were extracted from the catalog's __data.json response.
 */
fun getTagList(): List<Tag> = listOf(
    Tag("acao", "Ação"),
    Tag("apocalipse", "Apocalipse"),
    Tag("artes-marciais", "Artes marciais"),
    Tag("aventura", "Aventura"),
    Tag("comedia", "Comédia"),
    Tag("drama", "Drama"),
    Tag("escolar", "Escolar"),
    Tag("esportes", "Esportes"),
    Tag("fantasia", "Fantasia"),
    Tag("historico", "Histórico"),
    Tag("isekai", "Isekai"),
    Tag("josei", "Josei"),
    Tag("misterio", "Mistério"),
    Tag("murim", "Murim"),
    Tag("psicologico", "Psicológico"),
    Tag("reencarnacao", "Reencarnação"),
    Tag("regressao", "Regressão"),
    Tag("romance", "Romance"),
    Tag("sci-fi", "Sci-Fi"),
    Tag("seinen", "Seinen"),
    Tag("shoujo", "Shoujo"),
    Tag("shounen", "Shounen"),
    Tag("sistema", "Sistema"),
    Tag("slice-of-life", "Slice of Life"),
    Tag("sobrenatural", "Sobrenatural"),
    Tag("terror", "Terror"),
)
