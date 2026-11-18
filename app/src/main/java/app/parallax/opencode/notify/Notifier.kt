package app.parallax.opencode.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.parallax.opencode.MainActivity
import app.parallax.opencode.R
import app.parallax.opencode.core.model.InboxItem
import app.parallax.opencode.core.model.PermissionDecision

/**
 * Builds and posts the inbox notifications (permission, form, done, failed) plus the quiet
 * ongoing notification used by [WatchService].
 */
object Notifier {
    const val GROUP_SESSION = "parallax.session"

    /** Parallax lime. */
    private const val ACCENT = 0xFFD4FF3A.toInt()

    /** Decision asked for by [NotificationActionReceiver]. */
    const val ACTION_PERMISSION_DECISION = "app.parallax.opencode.notify.PERMISSION_DECISION"
    const val EXTRA_SERVER_ID = "serverId"
    const val EXTRA_SESSION_ID = "sessionId"
    const val EXTRA_REQUEST_ID = "requestId"
    const val EXTRA_DECISION = "decision"
    const val EXTRA_ITEM_KEY = "itemKey"
    const val EXTRA_SESSION_TITLE = "sessionTitle"

    fun idFor(key: String): Int = key.hashCode() and 0x7fffffff

    /** Stable group id so all notifications of one session stack together. */
    private fun groupFor(item: InboxItem): String =
        "$GROUP_SESSION.${item.serverId}.${item.sessionId}"

    /** Builds and posts the notification for one inbox item. Returns false when notifications are off. */
    fun post(context: Context, item: InboxItem): Boolean {
        if (!NotificationPermission.granted(context)) return false
        Channels.ensure(context)

        val body = item.body?.takeIf { it.isNotBlank() } ?: item.title
        val builder = baseBuilder(context, item)
        when (item.kind) {
            InboxItem.Kind.PERMISSION -> {
                builder
                    .setChannelId(Channels.NEEDS_YOU)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentTitle("${item.sessionTitle} needs approval")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                permissionActions(context, item).forEach(builder::addAction)
            }

            InboxItem.Kind.FORM -> {
                builder
                    .setChannelId(Channels.NEEDS_YOU)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentTitle("${item.sessionTitle} needs your answer")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }

            InboxItem.Kind.DONE -> {
                builder
                    .setChannelId(Channels.RUNS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentTitle("Finished · ${item.sessionTitle}")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }

            InboxItem.Kind.FAILED -> {
                builder
                    .setChannelId(Channels.RUNS)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentTitle("Failed · ${item.sessionTitle}")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
        }

        return notify(context, idFor(item.key), builder.build())
    }

    /** Replaces the permission notification with a short "couldn't send" one. */
    fun postFailure(context: Context, itemKey: String, title: String, serverId: String, sessionId: String) {
        if (!NotificationPermission.granted(context)) return
        Channels.ensure(context)

        val builder = NotificationCompat.Builder(context, Channels.NEEDS_YOU)
            .setSmallIcon(R.drawable.ic_stat_parallax)
            .setColor(ACCENT)
            .setContentTitle("Couldn't send — open Parallax")
            .setContentText(if (title.isBlank()) "Tap to open the session." else title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (title.isBlank()) "Tap to open the session." else title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent(context, serverId, sessionId, idFor(itemKey)))

        notify(context, idFor(itemKey), builder.build())
    }

    fun cancel(context: Context, itemKey: String) {
        NotificationManagerCompat.from(context).cancel(idFor(itemKey))
    }

    /**
     * The ongoing, minimum-importance notification for [WatchService].
     * [running] is the number of RUNNING sessions on [serverName].
     */
    fun ongoingNotification(context: Context, serverName: String?, running: Int): Notification {
        val title = when {
            running == 1 -> "Watching 1 agent"
            running > 1 -> "Watching $running agents"
            serverName != null -> "Connected to $serverName"
            else -> "Watching for agents"
        }
        val text = when {
            running > 0 && serverName != null -> "Connected to $serverName"
            running > 0 -> "Tap to open Parallax"
            else -> "Tap to open Parallax"
        }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, Channels.WATCH)
            .setSmallIcon(R.drawable.ic_stat_parallax)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    // --- internals ---

    private fun baseBuilder(context: Context, item: InboxItem): NotificationCompat.Builder =
        NotificationCompat.Builder(context, Channels.RUNS)
            .setSmallIcon(R.drawable.ic_stat_parallax)
            .setColor(ACCENT)
            .setAutoCancel(true)
            .setWhen(item.time)
            .setShowWhen(true)
            .setGroup(groupFor(item))
            .setSortKey(item.key)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(contentIntent(context, item.serverId, item.sessionId, idFor(item.key)))

    private fun permissionActions(context: Context, item: InboxItem): List<NotificationCompat.Action> {
        val requestId = item.requestId ?: return emptyList()
        val decisions = listOf(
            PermissionDecision.ONCE to "Allow once",
            PermissionDecision.ALWAYS to "Always",
            PermissionDecision.REJECT to "Deny",
        )
        return decisions.map { (decision, label) ->
            NotificationCompat.Action(
                0,
                label,
                actionIntent(context, item, requestId, decision),
            )
        }
    }

    private fun actionIntent(
        context: Context,
        item: InboxItem,
        requestId: String,
        decision: PermissionDecision,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_PERMISSION_DECISION
            putExtra(EXTRA_SERVER_ID, item.serverId)
            putExtra(EXTRA_SESSION_ID, item.sessionId)
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_DECISION, decision.wire)
            putExtra(EXTRA_ITEM_KEY, item.key)
            putExtra(EXTRA_SESSION_TITLE, item.sessionTitle)
        }
        val requestCode = idFor(item.key) * 31 + decision.ordinal
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Opens `parallax://session/<serverId>/<sessionId>`, which the nav host already understands. */
    fun contentIntent(
        context: Context,
        serverId: String,
        sessionId: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("parallax://session/$serverId/$sessionId")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification): Boolean = try {
        NotificationManagerCompat.from(context).notify(id, notification)
        true
    } catch (_: SecurityException) {
        // Permission was revoked between the check and the post; drop it silently.
        false
    }
}
