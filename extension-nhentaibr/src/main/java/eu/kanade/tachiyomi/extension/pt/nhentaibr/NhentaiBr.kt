package eu.kanade.tachiyomi.extension.pt.nhentaibr

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class NhentaiBr : ParsedHttpSource() {

    override val name = "Nhentai BR"

    override val baseUrl = "https://nhentai.net.br"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comics?page=$page&sort=views", headers)
    }

    override fun popularMangaSelector() = "div.comics-grid > div.comic-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleElement = element.selectFirst("h3.comic-title a")!!
        val imgElement = element.selectFirst("img.comic-cover")!!

        manga.setUrlWithoutDomain(titleElement.attr("href"))
        manga.title = titleElement.text().trim()
        manga.thumbnail_url = imgElement.attr("abs:src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "ul.pagination li.page-item:not(.disabled) a[rel=next]"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comics?page=$page&sort=newest", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            return GET("$baseUrl/comics/$id", headers)
        }
        return GET("$baseUrl/search?q=${query}&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    val manga = mangaDetailsParse(response)
                    manga.url = "/comics/${query.removePrefix(PREFIX_ID_SEARCH)}"
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.selectFirst("div.comic-info") ?: return manga

        manga.title = infoElement.selectFirst("h1.title")?.text()?.trim() ?: ""
        manga.thumbnail_url = document.selectFirst("div.comic-cover img")?.attr("abs:src")
        manga.author = infoElement.select("div.meta-item:contains(Autor) a").joinToString { it.text() }
        manga.artist = infoElement.select("div.meta-item:contains(Artista) a").joinToString { it.text() }
        manga.genre = infoElement.select("div.tags a.tag").joinToString { it.text() }
        manga.description = infoElement.selectFirst("div.description")?.text()?.trim()
        manga.status = parseStatus(infoElement.selectFirst("div.meta-item:contains(Status) span.value")?.text())

        return manga
    }

    private fun parseStatus(status: String?): Int {
        return when (status?.lowercase()) {
            "completo" -> SManga.COMPLETED
            "em andamento", "ativo" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // Chapters
    override fun chapterListSelector() = "ul.chapter-list li.chapter-item"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val linkElement = element.selectFirst("a.chapter-link")!!
        
        chapter.setUrlWithoutDomain(linkElement.attr("href"))
        chapter.name = linkElement.selectFirst("span.chapter-title")?.text()?.trim() ?: linkElement.text().trim()
        chapter.date_upload = parseDate(element.selectFirst("span.chapter-date")?.text())
        
        return chapter
    }

    private fun parseDate(dateStr: String?): Long {
        return try {
            // Implement date parsing logic if available on site. Otherwise return 0L
            0L 
        } catch (e: Exception) {
            0L
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val imgElements = document.select("div.reader-images img.reader-img")
        
        for ((i, img) in imgElements.withIndex()) {
            val url = img.attr("data-src").ifEmpty { img.attr("src") }
            pages.add(Page(i, "", url))
        }
        
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
