package eu.kanade.tachiyomi.animesource.model

/**
 * Define what type of content an anime entry should fetch.
 * The fetch type for an anime will not update after it's been initialized
 * to either Seasons or Episodes.
 */
@Suppress("UNUSED")
enum class FetchType {
    /**
     * The anime will only fetch its season list.
     */
    Seasons,

    /**
     * The anime will only fetch its episode list.
     */
    Episodes,
}
