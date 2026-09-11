package eu.kanade.tachiyomi.extension.pt.nhentaibr

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class NhentaiBr : ParsedHttpSource() {

    override val name = "Nhentai BR"

    override val baseUrl = "https://nhentai.net.br"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrl/popular/", headers)
        } else {
            GET("$baseUrl/popular/page/$page/", headers)
        }
    }

    override fun popularMangaSelector() = "div.lista ul li div.thumb-conteudo:not(:has(span.seloPersonalizado)):has(a[href*=\"nhentai.net.br\"])"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleLink = element.selectFirst("a[title]:has(span.thumb-titulo)")
            ?: element.selectFirst("a[href*=\"nhentai.net.br\"]:not(.thumbParodiaNome)")
            ?: element.selectFirst("a")!!

        manga.setUrlWithoutDomain(titleLink.attr("href"))
        val titleText = element.selectFirst("span.thumb-titulo")?.text()?.trim()
            ?: titleLink.attr("title").ifEmpty { titleLink.text().trim() }
        manga.title = titleText
        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.attr("abs:src")?.ifEmpty { img.attr("src") }

        return manga
    }

    override fun popularMangaNextPageSelector() = "ul.paginacao li.active + li a"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrl/ultimos/", headers)
        } else {
            GET("$baseUrl/ultimos/page/$page/", headers)
        }
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (page == 1) {
            GET("$baseUrl/?s=$query", headers)
        } else {
            GET("$baseUrl/page/$page/?s=$query", headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val titleElem = document.selectFirst("h1.post-titulo")
        val fullTitle = titleElem?.text()?.trim() ?: document.title()
        manga.title = fullTitle

        val img = document.selectFirst("div.post-capa img")
            ?: document.selectFirst("ul.post-fotos li img")
        manga.thumbnail_url = img?.attr("abs:src")?.ifEmpty { img.attr("src") }

        val items = document.select("ul.post-itens li")
        val genres = mutableListOf<String>()
        var author: String? = null

        for (li in items) {
            val strong = li.selectFirst("strong")?.text()?.trim() ?: ""
            when {
                strong.contains("Categorias", ignoreCase = true) || strong.contains("Tags", ignoreCase = true) -> {
                    genres.addAll(li.select("a").map { it.text().trim() }.filter { it.isNotEmpty() })
                }
                strong.contains("Paródia", ignoreCase = true) -> {
                    val parody = li.select("a").joinToString { it.text().trim() }
                    if (parody.isNotEmpty()) {
                        genres.add("Paródia: $parody")
                    }
                }
                strong.contains("Artista", ignoreCase = true) || strong.contains("Autor", ignoreCase = true) -> {
                    val names = li.select("a").map { it.text().trim() }.filter { !it.contains("Login", true) && !it.contains("Registre", true) }
                    if (names.isNotEmpty()) {
                        author = names.joinToString()
                    }
                }
            }
        }

        manga.genre = genres.distinct().joinToString()
        manga.author = author
        manga.artist = author
        manga.status = SManga.COMPLETED

        val descElem = document.selectFirst("div.post-conteudo")
        manga.description = descElem?.text()?.trim()

        return manga
    }

    // Chapters
    override fun chapterListSelector() = "html"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val datePublished = document.selectFirst("meta[property=\"article:published_time\"]")?.attr("content")
            ?: document.selectFirst("meta[name=\"pubdate\"]")?.attr("content")

        val dateUpload = datePublished?.let {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).parse(it.substring(0, 19))?.time
            } catch (e: Exception) {
                null
            }
        } ?: 0L

        val chapter = SChapter.create().apply {
            name = "Capítulo Completo"
            setUrlWithoutDomain(response.request.url.encodedPath)
            date_upload = dateUpload
        }

        return listOf(chapter)
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val imgElements = document.select("ul.post-fotos li img")

        for ((i, img) in imgElements.withIndex()) {
            val url = img.attr("data-src").ifEmpty {
                img.attr("data-lazy-src").ifEmpty {
                    img.attr("abs:src").ifEmpty {
                        img.attr("src")
                    }
                }
            }
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }

        return pages
    }
}
