package eu.kanade.tachiyomi.data.profile

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity

class ProfileNotifier(private val context: Context) {

    @android.annotation.SuppressLint("MissingPermission")
    fun showAchievementUnlocked(achievement: YomotsuAchievement) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Ação opcional para abrir o perfil direto
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_COMMON)
            .setSmallIcon(R.drawable.ic_mihon) // Pode trocar pelo ícone de troféu se tiver
            .setContentTitle("🏆 Conquista Desbloqueada!")
            .setContentText("${achievement.name}: ${achievement.description}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(achievement.id.hashCode(), builder.build())
        }
    }
}
