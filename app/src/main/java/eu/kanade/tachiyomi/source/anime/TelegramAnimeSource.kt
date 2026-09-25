package eu.kanade.tachiyomi.source.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.telegram.TelegramCloudManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import tachiyomi.domain.telegram.TelegramPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class TelegramAnimeSource(
    private val context: Context,
    private val telegramCloudManager: TelegramCloudManager = Injekt.get(),
    private val preferences: TelegramPreferences = Injekt.get()
) : AnimeHttpSource(), UnmeteredSource {

    override val id: Long = ID
    override val name: String = "Biblioteca Telegram (Anime)"
    override val lang: String = "other"
    override val supportsLatest: Boolean = false
    override val baseUrl: String = "http://telegram-cache"

    companion object {
        const val ID = 9876543211L // Different from TelegramSource
    }

    override fun headersBuilder() = super.headersBuilder()

    override suspend fun getPopularAnime(page: Int): AnimesPage = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val animes = index.filter { it.type == "ANIME" }.map { cloudAnime ->
            SAnime.create().apply {
                title = cloudAnime.title
                url = cloudAnime.title
                description = cloudAnime.description
                thumbnail_url = cloudAnime.coverUrl
                status = SAnime.UNKNOWN
            }
        }
        AnimesPage(animes, false)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val animes = index.filter {
            it.type == "ANIME" && it.title.contains(query, ignoreCase = true)
        }.map { cloudAnime ->
            SAnime.create().apply {
                title = cloudAnime.title
                url = cloudAnime.title
                description = cloudAnime.description
                thumbnail_url = cloudAnime.coverUrl
                status = SAnime.UNKNOWN
            }
        }
        AnimesPage(animes, false)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val cloudAnime = index.find { it.type == "ANIME" && it.title == anime.url } ?: return@withContext anime
        anime.apply {
            title = cloudAnime.title
            description = cloudAnime.description
            thumbnail_url = cloudAnime.coverUrl
            status = SAnime.UNKNOWN
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = withContext(Dispatchers.IO) {
        val index = telegramCloudManager.getCloudIndex()
        val cloudAnime = index.find { it.type == "ANIME" && it.title == anime.url } ?: return@withContext emptyList()
        
        cloudAnime.chapters.sortedByDescending { it.date }.map { cloudEpisode ->
            SEpisode.create().apply {
                name = cloudEpisode.name
                url = "${anime.title}||${cloudEpisode.name}"
                date_upload = cloudEpisode.date * 1000L
                episode_number = -1f // Optional
            }
        }
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> = withContext(Dispatchers.IO) {
        val parts = episode.url.split("||")
        if (parts.size != 2) return@withContext emptyList()

        val animeTitle = parts[0]
        val episodeName = parts[1]

        val index = telegramCloudManager.getCloudIndex()
        val cloudAnime = index.find { it.type == "ANIME" && it.title == animeTitle } ?: return@withContext emptyList()
        val cloudEpisode = cloudAnime.chapters.find { it.name == episodeName } ?: return@withContext emptyList()

        val chatId = preferences.chatId.get().toLongOrNull() ?: return@withContext emptyList()

        telegramCloudManager.showNotification("Biblioteca Telegram", "Baixando o episódio da nuvem...", ongoing = true)

        val downloadedPath = telegramCloudManager.downloadChapterFile(animeTitle, cloudEpisode, chatId)
        if (downloadedPath.isNullOrBlank()) {
            telegramCloudManager.showNotification("Biblioteca Telegram", "Erro ao baixar o episódio", autoDismiss = true)
            throw Exception("Falha ao baixar arquivo do Telegram")
        }

        telegramCloudManager.showNotification("Biblioteca Telegram", "Download concluído, abrindo player...", autoDismiss = true)

        val localUri = File(downloadedPath).toURI().toString()
        listOf(Video(url = localUri, quality = "Telegram Cloud", videoUrl = localUri, headers = null))
    }

    // Unused methods for HttpSource
    override fun popularAnimeRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = throw UnsupportedOperationException()
    override fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException()
    override fun animeDetailsParse(response: Response): SAnime = throw UnsupportedOperationException()
    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()
    override fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()
}
