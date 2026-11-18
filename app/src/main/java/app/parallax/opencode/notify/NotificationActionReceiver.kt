package app.parallax.opencode.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.parallax.opencode.ParallaxApp
import app.parallax.opencode.core.model.PermissionDecision
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the "Allow once" / "Always" / "Deny" buttons on permission notifications.
 * Replies through the repository, then clears the notification (or replaces it with an error).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifier.ACTION_PERMISSION_DECISION) return

        val serverId = intent.getStringExtra(Notifier.EXTRA_SERVER_ID) ?: return
        val sessionId = intent.getStringExtra(Notifier.EXTRA_SESSION_ID) ?: return
        val requestId = intent.getStringExtra(Notifier.EXTRA_REQUEST_ID) ?: return
        val itemKey = intent.getStringExtra(Notifier.EXTRA_ITEM_KEY) ?: return
        val sessionTitle = intent.getStringExtra(Notifier.EXTRA_SESSION_TITLE).orEmpty()
        val decisionWire = intent.getStringExtra(Notifier.EXTRA_DECISION) ?: return
        val decision = PermissionDecision.entries.firstOrNull { it.wire == decisionWire } ?: return

        val appContext = context.applicationContext
        val pending = goAsync()
        ParallaxApp.container.appScope.launch {
            val sent = try {
                withTimeoutOrNull(REPLY_TIMEOUT_MS) {
                    ParallaxApp.container.repository.replyPermission(sessionId, requestId, decision)
                    true
                } ?: false
            } catch (_: Throwable) {
                false
            }

            if (sent) {
                Notifier.cancel(appContext, itemKey)
            } else {
                Notifier.postFailure(appContext, itemKey, sessionTitle, serverId, sessionId)
            }
            pending.finish()
        }
    }

    private companion object {
        const val REPLY_TIMEOUT_MS = 20_000L
    }
}
