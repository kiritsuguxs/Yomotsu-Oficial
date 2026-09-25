import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt", "r") as f:
    content = f.read()

# Add AnimeDownloadManager to injected dependencies
if "private val animeDownloadManager" not in content:
    content = content.replace(
        "private val downloadManager: DownloadManager by injectLazy()",
        "private val downloadManager: DownloadManager by injectLazy()\n    private val animeDownloadManager: eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager by injectLazy()"
    )

# Add Anime actions
anime_actions = '''
            // Resume the download service
            ACTION_RESUME_ANIME_DOWNLOADS -> animeDownloadManager.startDownloads()
            // Pause the download service
            ACTION_PAUSE_ANIME_DOWNLOADS -> animeDownloadManager.pauseDownloads()
            // Clear the download queue
            ACTION_CLEAR_ANIME_DOWNLOADS -> animeDownloadManager.clearQueue()
'''
if "ACTION_RESUME_ANIME_DOWNLOADS" not in content:
    content = content.replace(
        "ACTION_CLEAR_DOWNLOADS -> downloadManager.clearQueue()",
        "ACTION_CLEAR_DOWNLOADS -> downloadManager.clearQueue()" + anime_actions
    )

# Add companion object methods
companion_methods = '''
        internal fun resumeAnimeDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_RESUME_ANIME_DOWNLOADS
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        internal fun pauseAnimeDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_PAUSE_ANIME_DOWNLOADS
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        internal fun clearAnimeDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CLEAR_ANIME_DOWNLOADS
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        
        private const val ACTION_RESUME_ANIME_DOWNLOADS = "$ID.ACTION_RESUME_ANIME_DOWNLOADS"
        private const val ACTION_PAUSE_ANIME_DOWNLOADS = "$ID.ACTION_PAUSE_ANIME_DOWNLOADS"
        private const val ACTION_CLEAR_ANIME_DOWNLOADS = "$ID.ACTION_CLEAR_ANIME_DOWNLOADS"
'''
if "resumeAnimeDownloadsPendingBroadcast" not in content:
    content = content.replace(
        "private const val ACTION_CLEAR_DOWNLOADS = \"$ID.ACTION_CLEAR_DOWNLOADS\"",
        "private const val ACTION_CLEAR_DOWNLOADS = \"$ID.ACTION_CLEAR_DOWNLOADS\"\n" + companion_methods
    )

with open("app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt", "w") as f:
    f.write(content)
print("Patched NotificationReceiver.kt")
