import re

with open("app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationHandler.kt", "r") as f:
    content = f.read()

anime_methods = '''
    internal fun openAnimeDownloadManagerPendingActivity(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Constants.SHORTCUT_ANIME_DOWNLOADS
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun openAnimeEntryPendingActivity(context: Context, animeId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Constants.SHORTCUT_ANIME
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.ANIME_EXTRA, animeId)
        }
        return PendingIntent.getActivity(
            context,
            animeId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
'''

if "openAnimeDownloadManagerPendingActivity" not in content:
    # insert before openImagePendingActivity
    content = content.replace(
        "    internal fun openImagePendingActivity",
        anime_methods + "\n    internal fun openImagePendingActivity"
    )

with open("app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationHandler.kt", "w") as f:
    f.write(content)
print("Patched NotificationHandler.kt")
