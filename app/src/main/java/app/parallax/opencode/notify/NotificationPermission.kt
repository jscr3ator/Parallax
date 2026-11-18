package app.parallax.opencode.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Small helper the UI can call to decide whether to show a "turn on notifications" prompt. */
object NotificationPermission {
    /** True when the runtime POST_NOTIFICATIONS permission still has to be requested (Android 13+ only). */
    fun needsRequest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    }

    /** True when we are allowed to actually show notifications (permission + app-level switch). */
    fun granted(context: Context): Boolean {
        if (needsRequest(context)) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
