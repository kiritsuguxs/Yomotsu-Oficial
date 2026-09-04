package eu.kanade.translation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.translation.model.Translation
import kotlinx.coroutines.delay
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val translationManager: TranslationManager = Injekt.get()
    private val notifier = TranslationNotifier(applicationContext)

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        Notifications.ID_TRANSLATION_PROGRESS,
        notifier.initialNotification(),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        },
    )

    override suspend fun doWork(): Result {
        setForegroundSafely()

        var idleChecks = 0
        var initialStartRequested = true
        try {
            while (!isStopped) {
                val translations = translationManager.queueState.value
                val hasPendingChapters = translations.any {
                    it.status == Translation.State.QUEUE || it.status == Translation.State.TRANSLATING
                }
                if (!translationManager.isRunning && (initialStartRequested || hasPendingChapters)) {
                    translationManager.translatorStart()
                    initialStartRequested = false
                }

                if (hasPendingChapters || translationManager.isRunning) {
                    idleChecks = 0
                } else {
                    idleChecks++
                    if (idleChecks >= IDLE_CHECKS_BEFORE_FINISH) break
                }
                delay(STATUS_POLL_INTERVAL_MS)
            }
        } finally {
            if (isStopped) {
                translationManager.translatorStop("Translation worker stopped")
            }
            translationManager.onTranslationWorkerFinished()
            notifier.dismissProgress()
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ChapterTranslator"
        private const val STATUS_POLL_INTERVAL_MS = 250L
        private const val IDLE_CHECKS_BEFORE_FINISH = 4

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranslationJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}
