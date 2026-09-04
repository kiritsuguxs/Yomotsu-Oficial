package eu.kanade.translation

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.at.ATMR
import uy.kohesive.injekt.injectLazy

internal class TranslationNotifier(private val context: Context) {

    private val securityPreferences: SecurityPreferences by injectLazy()

    private val progressBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_translate_24dp)
            setAutoCancel(false)
            setOnlyAlertOnce(true)
            setOngoing(true)
        }
    }

    private val errorBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_ERROR) {
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setAutoCancel(true)
        }
    }

    fun initialNotification() = context.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
        setContentTitle(context.stringResource(ATMR.strings.translation_notification_group))
        setContentText(context.stringResource(ATMR.strings.translation_notification_preparing))
        setSmallIcon(R.drawable.ic_translate_24dp)
        setOnlyAlertOnce(true)
        setOngoing(true)
        setProgress(0, 0, true)
    }.build()

    fun onPreparing(manga: Manga, chapterName: String, chapterNumber: Int, chapterTotal: Int) {
        showProgress(
            manga = manga,
            chapterName = chapterName,
            progressText = context.stringResource(ATMR.strings.translation_notification_preparing),
            chapterNumber = chapterNumber,
            chapterTotal = chapterTotal,
            progress = 0,
            max = 0,
            indeterminate = true,
        )
    }

    fun onPageProgress(
        manga: Manga,
        chapterName: String,
        page: Int,
        totalPages: Int,
        chapterNumber: Int,
        chapterTotal: Int,
    ) {
        showProgress(
            manga = manga,
            chapterName = chapterName,
            progressText = context.stringResource(
                ATMR.strings.translation_notification_page_progress,
                page,
                totalPages,
            ),
            chapterNumber = chapterNumber,
            chapterTotal = chapterTotal,
            progress = page,
            max = totalPages,
            indeterminate = false,
        )
    }

    fun onTextTranslation(
        manga: Manga,
        chapterName: String,
        totalPages: Int,
        chapterNumber: Int,
        chapterTotal: Int,
    ) {
        showProgress(
            manga = manga,
            chapterName = chapterName,
            progressText = context.stringResource(
                ATMR.strings.translation_notification_text_progress,
                totalPages,
            ),
            chapterNumber = chapterNumber,
            chapterTotal = chapterTotal,
            progress = 0,
            max = 0,
            indeterminate = true,
        )
    }

    fun onError(manga: Manga, chapterName: String, error: Throwable) {
        errorBuilder
            .setContentTitle("${manga.title} - $chapterName".chop(45))
            .setContentText(
                error.message?.takeIf(String::isNotBlank)
                    ?: context.stringResource(ATMR.strings.translation_notification_error),
            )
            .setContentIntent(NotificationReceiver.openEntryPendingActivity(context, manga.id))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    error.message?.takeIf(String::isNotBlank)
                        ?: context.stringResource(ATMR.strings.translation_notification_error),
                ),
            )
            .also { context.notify(Notifications.ID_TRANSLATION_ERROR, it.build()) }
    }

    fun dismissProgress() {
        context.cancelNotification(Notifications.ID_TRANSLATION_PROGRESS)
    }

    private fun showProgress(
        manga: Manga,
        chapterName: String,
        progressText: String,
        chapterNumber: Int,
        chapterTotal: Int,
        progress: Int,
        max: Int,
        indeterminate: Boolean,
    ) {
        val chapterProgress = context.stringResource(
            ATMR.strings.translation_notification_chapter_progress,
            chapterNumber.coerceAtLeast(1),
            chapterTotal.coerceAtLeast(1),
        )
        with(progressBuilder) {
            setContentIntent(NotificationReceiver.openEntryPendingActivity(context, manga.id))
            if (securityPreferences.hideNotificationContent.get()) {
                setContentTitle(progressText)
                setContentText(chapterProgress)
            } else {
                setContentTitle("${manga.title} - $chapterName".chop(45))
                setContentText("$progressText · $chapterProgress")
            }
            setProgress(max, progress, indeterminate)
            context.notify(Notifications.ID_TRANSLATION_PROGRESS, build())
        }
    }
}
