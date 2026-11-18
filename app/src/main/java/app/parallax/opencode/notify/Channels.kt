package app.parallax.opencode.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Notification channels. Creating them is idempotent; [ensure] is safe to call before every post. */
object Channels {
    /** Approvals and questions — high importance, vibrates. */
    const val NEEDS_YOU = "needs_you"

    /** Finished / failed runs — default importance, no vibration. */
    const val RUNS = "runs"

    /** The quiet ongoing notification that keeps the watch service alive. */
    const val WATCH = "watch"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val needsYou = NotificationChannel(
            NEEDS_YOU,
            "Needs you",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Permission requests and questions waiting on you"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val runs = NotificationChannel(
            RUNS,
            "Finished runs",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Runs that finished or failed"
            enableVibration(false)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val watch = NotificationChannel(
            WATCH,
            "Live connection",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Keeps the live connection to your servers open"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }

        nm.createNotificationChannels(listOf(needsYou, runs, watch))
    }
}
