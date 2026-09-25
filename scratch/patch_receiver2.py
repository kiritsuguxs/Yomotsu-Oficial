import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt", "r") as f:
    content = f.read()

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

        private const val ACTION_RESUME_ANIME_DOWNLOADS = "$ID.$NAME.ACTION_RESUME_ANIME_DOWNLOADS"
        private const val ACTION_PAUSE_ANIME_DOWNLOADS = "$ID.$NAME.ACTION_PAUSE_ANIME_DOWNLOADS"
        private const val ACTION_CLEAR_ANIME_DOWNLOADS = "$ID.$NAME.ACTION_CLEAR_ANIME_DOWNLOADS"
'''

content = content.replace(
    'private const val ACTION_CLEAR_DOWNLOADS = "$ID.$NAME.ACTION_CLEAR_DOWNLOADS"',
    'private const val ACTION_CLEAR_DOWNLOADS = "$ID.$NAME.ACTION_CLEAR_DOWNLOADS"\n' + companion_methods
)

with open("app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt", "w") as f:
    f.write(content)
print("Patched NotificationReceiver.kt")
