package eu.kanade.tachiyomi.extension.pt.projectnox

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Extension for Project Nox manga website.
 *
 * The site is built with SvelteKit and uses server-side rendering with
 * embedded data. We consume the SvelteKit `__data.json` endpoints which
 * return structured data in a compact indexed format that we decode.
 */
class ProjectNox : HttpSource() {

    override val name = "Project Nox"
    override val baseUrl = "https://manga.project-nox-awerkori.workers.dev"
    override val lang = "pt-BR"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ======================== Media URL ========================

    private fun mediaUrl(mediaId: String): String = "$baseUrl/media/$mediaId"

    // ======================== SvelteKit Data Decoder ========================

    /**
     * SvelteKit __data.json returns a compact indexed format where objects reference
     * values by index in a flat array. This function decodes that format into
     * regular JsonElement trees.
     */
    private fun decodeSvelteData(dataArray: JsonArray, startIndex: Int = 0): JsonElement {
        fun resolve(element: JsonElement): JsonElement {
            return when (element) {
                is JsonObject -> {
                    val resolved = mutableMapOf<String, JsonElement>()
                    for ((key, value) in element) {
                        if (value is JsonPrimitive && value.intOrNull != null) {
                            val idx = value.int
                            if (idx < dataArray.size) {
                                resolved[key] = resolve(dataArray[idx])
                            } else {
                                resolved[key] = JsonNull
                            }
                        } else {
                            resolved[key] = resolve(value)
                        }
                    }
                    JsonObject(resolved)
                }
                is JsonArray -> {
                    val resolved = element.map { item ->
                        if (item is JsonPrimitive && item.intOrNull != null) {
                            val idx = item.int
                            if (idx < dataArray.size) {
                                resolve(dataArray[idx])
                            } else {
                                JsonNull
                            }
                        } else {
                            resolve(item)
                        }
                    }
                    JsonArray(resolved)
                }
                else -> element
            }
        }

        if (startIndex < dataArray.size) {
            return resolve(dataArray[startIndex])
        }
        return JsonNull
    }

    /**
     * Parse the __data.json response and extract the page-specific data node.
     * The response has a `nodes` array where index 0 is layout data and index 1 is page data.
     */
    private fun parseSvelteResponse(response: Response): JsonObject {
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val nodes = root["nodes"]!!.jsonArray

        // The page data is in nodes[1]
        val pageNode = nodes[1].jsonObject
        val dataArray = pageNode["data"]!!.jsonArray

        // The first element is the schema object
        val schema = dataArray[0].jsonObject

        // Decode the schema resolving all index references
        val decoded = decodeSvelteData(dataArray)
        return decoded.jsonObject
    }

    // ======================== Popular Manga ========================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/catalogo/__data.json?ordem=populares&pagina=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return parseCatalogResponse(response)
    }

    // ======================== Latest Manga ========================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/catalogo/__data.json?ordem=recentes&pagina=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return parseCatalogResponse(response)
    }

    // ======================== Search ========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Handle deep link search by slug
        if (query.startsWith(SEARCH_PREFIX)) {
            val slug = query.removePrefix(SEARCH_PREFIX)
            return GET("$baseUrl/obra/$slug/__data.json", headers)
        }

        val url = buildString {
            append("$baseUrl/catalogo/__data.json?pagina=$page")
            if (query.isNotBlank()) {
                append("&q=$query")
            }

            filters.forEach { filter ->
                when (filter) {
                    is KindFilter -> {
                        if (filter.state > 0) {
                            append("&tipo=${filter.toUriPart()}")
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            append("&status=${filter.toUriPart()}")
                        }
                    }
                    is SortFilter -> {
                        append("&ordem=${filter.toUriPart()}")
                    }
                    is TagFilter -> {
                        val selected = filter.state.filter { it.state }
                        if (selected.isNotEmpty()) {
                            selected.forEach { tag ->
                                append("&tag=${tag.slug}")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        // Check if this is a slug-based search (single manga details page)
        if (response.request.url.toString().contains("/obra/")) {
            return parseSlugSearchResponse(response)
        }
        return parseCatalogResponse(response)
    }

    /**
     * Parse a single manga from the obra details endpoint (used for slug search / deep links).
     */
    private fun parseSlugSearchResponse(response: Response): MangasPage {
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val nodes = root["nodes"]!!.jsonArray
        val pageNode = nodes[1].jsonObject
        val dataArray = pageNode["data"]!!.jsonArray
        val decoded = decodeSvelteData(dataArray).jsonObject

        val work = decoded["work"]!!.jsonObject
        val manga = SManga.create().apply {
            url = "/obra/${work["slug"]!!.jsonPrimitive.content}"
            title = work["title"]!!.jsonPrimitive.content
            thumbnail_url = decoded["coverUrl"]?.jsonPrimitive?.contentOrNull
                ?: work["cover_id"]?.jsonPrimitive?.contentOrNull?.let { mediaUrl(it) }
            author = work["author"]?.jsonPrimitive?.contentOrNull
            artist = work["artist"]?.jsonPrimitive?.contentOrNull
            description = work["synopsis"]?.jsonPrimitive?.contentOrNull
            status = when (work["status"]?.jsonPrimitive?.contentOrNull) {
                "ONGOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "HIATUS" -> SManga.ON_HIATUS
                "CANCELLED" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
        return MangasPage(listOf(manga), false)
    }

    // ======================== Catalog Parser ========================

    private fun parseCatalogResponse(response: Response): MangasPage {
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val nodes = root["nodes"]!!.jsonArray
        val pageNode = nodes[1].jsonObject
        val dataArray = pageNode["data"]!!.jsonArray

        // Decode the root schema object
        val decoded = decodeSvelteData(dataArray).jsonObject

        val works = decoded["works"]?.jsonArray ?: return MangasPage(emptyList(), false)
        val count = decoded["count"]?.jsonPrimitive?.intOrNull ?: 0
        val currentPage = decoded["page"]?.jsonPrimitive?.intOrNull ?: 1

        val mangaList = works.mapNotNull { workElement ->
            try {
                val work = workElement.jsonObject
                SManga.create().apply {
                    url = "/obra/${work["slug"]!!.jsonPrimitive.content}"
                    title = work["title"]!!.jsonPrimitive.content
                    thumbnail_url = work["cover_id"]?.jsonPrimitive?.contentOrNull?.let { mediaUrl(it) }
                    author = work["author"]?.jsonPrimitive?.contentOrNull
                    artist = work["artist"]?.jsonPrimitive?.contentOrNull
                    description = work["synopsis"]?.jsonPrimitive?.contentOrNull
                    genre = buildString {
                        work["kind"]?.jsonPrimitive?.contentOrNull?.let { append(it) }
                    }
                    status = when (work["status"]?.jsonPrimitive?.contentOrNull) {
                        "ONGOING" -> SManga.ONGOING
                        "COMPLETED" -> SManga.COMPLETED
                        "HIATUS" -> SManga.ON_HIATUS
                        "CANCELLED" -> SManga.CANCELLED
                        else -> SManga.UNKNOWN
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        val hasNextPage = mangaList.size < count
        return MangasPage(mangaList, hasNextPage)
    }

    // ======================== Manga Details ========================

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}/__data.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val nodes = root["nodes"]!!.jsonArray
        val pageNode = nodes[1].jsonObject
        val dataArray = pageNode["data"]!!.jsonArray

        val decoded = decodeSvelteData(dataArray).jsonObject

        val work = decoded["work"]!!.jsonObject
        val tags = decoded["tags"]?.jsonArray

        return SManga.create().apply {
            url = "/obra/${work["slug"]!!.jsonPrimitive.content}"
            title = work["title"]!!.jsonPrimitive.content
            thumbnail_url = decoded["coverUrl"]?.jsonPrimitive?.contentOrNull
                ?: work["cover_id"]?.jsonPrimitive?.contentOrNull?.let { mediaUrl(it) }
            author = work["author"]?.jsonPrimitive?.contentOrNull
            artist = work["artist"]?.jsonPrimitive?.contentOrNull
            description = buildString {
                work["synopsis"]?.jsonPrimitive?.contentOrNull?.let { append(it) }
                work["aliases"]?.jsonArray?.let { aliases ->
                    if (aliases.isNotEmpty()) {
                        append("\n\nTítulos alternativos: ")
                        append(aliases.joinToString(", ") { it.jsonPrimitive.content })
                    }
                }
                work["year"]?.jsonPrimitive?.intOrNull?.let {
                    append("\nAno: $it")
                }
            }
            genre = buildList {
                work["kind"]?.jsonPrimitive?.contentOrNull?.let { add(it) }
                tags?.forEach { tag ->
                    try {
                        tag.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let { add(it) }
                    } catch (_: Exception) {}
                }
            }.joinToString(", ")
            status = when (work["status"]?.jsonPrimitive?.contentOrNull) {
                "ONGOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "HIATUS" -> SManga.ON_HIATUS
                "CANCELLED" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ======================== Chapters ========================

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}/__data.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val nodes = root["nodes"]!!.jsonArray
        val pageNode = nodes[1].jsonObject
        val dataArray = pageNode["data"]!!.jsonArray

        val decoded = decodeSvelteData(dataArray).jsonObject

        val chapters = decoded["chapters"]?.jsonArray ?: return emptyList()

        return chapters.mapNotNull { chapterElement ->
            try {
                val chapter = chapterElement.jsonObject
                SChapter.create().apply {
                    url = "/ler/${chapter["id"]!!.jsonPrimitive.content}"
                    name = buildString {
                        append("Capítulo ${chapter["number"]!!.jsonPrimitive.content}")
                        chapter["title"]?.jsonPrimitive?.contentOrNull?.let {
                            if (it.isNotBlank()) append(" - $it")
                        }
                    }
                    chapter_number = chapter["number"]?.jsonPrimitive?.floatOrNull ?: -1f
                    date_upload = chapter["published_at"]?.jsonPrimitive?.contentOrNull
                        ?.let { parseDate(it) } ?: 0L
                }
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ======================== Pages ========================

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}/__data.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val nodes = root["nodes"]!!.jsonArray
        val pageNode = nodes[1].jsonObject
        val dataArray = pageNode["data"]!!.jsonArray

        val decoded = decodeSvelteData(dataArray).jsonObject

        val pages = decoded["pages"]?.jsonArray ?: return emptyList()

        return pages.mapNotNull { pageElement ->
            try {
                val page = pageElement.jsonObject
                val position = page["position"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val mediaId = page["media_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Page(position - 1, "", mediaUrl(mediaId))
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.index }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ======================== Filters ========================

    override fun getFilterList(): FilterList {
        return FilterList(
            KindFilter(),
            StatusFilter(),
            SortFilter(),
            TagFilter(getTagList()),
        )
    }

    // ======================== URL Handler ========================

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    // ======================== Helpers ========================

    private fun parseDate(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            // Remove timezone info for simpler parsing
            val cleanDate = dateString.substringBefore("+").substringBefore("Z")
            format.parse(cleanDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    companion object {
        const val SEARCH_PREFIX = "slug:"
    }
}
