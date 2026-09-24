package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.domain.items.episode.model.toSEpisode
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

/**
 * Loader used to retrieve the hosters for a given episode.
 */
class EpisodeLoader {
    companion object {

        /**
         * Returns a list of hosters of an [episode] based on the type of [source] used.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         * @param source the source of the anime.
         */
        suspend fun getHosters(episode: Episode, anime: Anime, source: AnimeSource): List<Hoster> {
            return if (source is AnimeHttpSource) {
                getHostersOnHttp(episode, source)
            } else {
                emptyList()
            }
        }

        /**
         * Returns true if the given [episode] is downloaded.
         *
         * @param episode the episode being parsed.
         * @param anime the anime of the episode.
         */
        fun isDownload(episode: Episode, anime: Anime): Boolean = false

        private fun checkHasHosters(source: AnimeHttpSource): Boolean {
            var current: Class<in AnimeHttpSource> = source.javaClass
            while (true) {
                if (current == ParsedAnimeHttpSource::class.java ||
                    current == AnimeHttpSource::class.java ||
                    current == AnimeSource::class.java
                ) {
                    return false
                }
                if (current.declaredMethods.any {
                        it.name in
                            listOf("getHosterList", "hosterListRequest", "hosterListParse")
                    }
                ) {
                    return true
                }
                current = current.superclass ?: return false
            }
        }

        /**
         * Returns a list of hosters when the [episode] is online.
         *
         * @param episode the episode being parsed.
         * @param source the online source of the episode.
         */
        private suspend fun getHostersOnHttp(episode: Episode, source: AnimeHttpSource): List<Hoster> {
            // TODO(1.6): Remove else block when dropping support for ext lib <1.6
            return if (checkHasHosters(source)) {
                source.getHosterList(episode.toSEpisode())
                    .let { source.run { it.sortHosters() } }
            } else {
                source.getVideoList(episode.toSEpisode())
                    .let { source.run { it.sortVideos() } }
                    .toHosterList()
            }
        }

        /**
         * Returns a list of videos of a [hoster] based on the type of [source] used.
         * Note that for every type of episode except non-downloaded online, `videoList`
         * will be set to null.
         *
         * @param source the source of the anime.
         * @param hoster the hoster.
         */
        private suspend fun getVideos(source: AnimeSource, hoster: Hoster): List<Video> {
            val videos = when {
                hoster.videoList != null && source is AnimeHttpSource -> hoster.videoList!!.parseVideoUrls(source)
                hoster.videoList != null -> hoster.videoList!!
                source is AnimeHttpSource -> getVideosOnHttp(source, hoster)
                else -> error("source not supported")
            }

            return if (source is AnimeHttpSource) {
                source.run { videos.sortVideos() }
            } else {
                videos
            }
        }

        /**
         * Returns a list of hosters when the [episode] is online.
         *
         * @param source the online source of the episode.
         * @param hoster the hoster.
         */
        private suspend fun getVideosOnHttp(source: AnimeHttpSource, hoster: Hoster): List<Video> {
            return source.getVideoList(hoster)
                .parseVideoUrls(source)
        }

        // TODO(1.6): Remove after ext lib bump
        private suspend fun List<Video>.parseVideoUrls(source: AnimeHttpSource): List<Video> {
            return this.map { video ->
                if (video.videoUrl != "null") return@map video

                val newVideoUrl = source.getVideoUrl(video)
                video.copy(videoUrl = newVideoUrl)
            }
        }

        suspend fun loadHosterVideos(source: AnimeSource, hoster: Hoster, force: Boolean = false): HosterState {
            if (!force && hoster.lazy) {
                return HosterState.Idle(hoster.hosterName)
            }

            return try {
                val videos = getVideos(source, hoster)
                HosterState.Ready(hoster.hosterName, videos, List(videos.size) { Video.State.QUEUE })
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }

                HosterState.Error(hoster.hosterName)
            }
        }
    }
}
