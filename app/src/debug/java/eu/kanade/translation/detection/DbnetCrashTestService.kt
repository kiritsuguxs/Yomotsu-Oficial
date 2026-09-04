package eu.kanade.translation.detection

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.Process

/** Debug-only process-death fixture. Never packaged in the phone release APK. */
class DbnetCrashTestService : Service() {
    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) {
            check(DbnetProcess.isWorker(this))
            Process.killProcess(Process.myPid())
            true
        },
    )
    override fun onBind(intent: Intent): IBinder = messenger.binder
}
