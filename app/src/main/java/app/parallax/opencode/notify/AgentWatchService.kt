package app.parallax.opencode.notify

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Entry point used by the UI (`MainActivity`). Starting a foreground service from the background is
 * not allowed on Android 12+, so failures are ignored: the next foreground start brings the watch back.
 */
object AgentWatchService {
    fun ensureRunning(context: Context) {
        val intent = Intent(context, WatchService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) or a restricted-OS refusal.
        }
    }
}
