package app.parallax.opencode.notify

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import app.parallax.opencode.ParallaxApp
import app.parallax.opencode.core.model.InboxItem
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the repository's live connection open and turns new inbox items into
 * system notifications. This is what makes notifications arrive while the app is in the background.
 *
 * Started from [AgentWatchService.ensureRunning] (normally by `MainActivity`). The manifest already
 * declares it with `foregroundServiceType="dataSync"`.
 */
class WatchService : LifecycleService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Keys already handled, so an item is never notified twice. Seeded with whatever existed at start. */
    private val seenKeys = HashSet<String>()

    /** Finished/failed runs older than this are history, not news (the inbox fills in after the first resync). */
    private var startedAt = 0L
    private var started = false
    private var stopping = false
    private var lastOngoing: String? = null

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Must happen within a few seconds of startForegroundService().
        goForeground()
        startWatching()
        return Service.START_STICKY
    }

    private fun goForeground() {
        val server = currentServerName()
        val notification = Notifier.ongoingNotification(this, server, runningCount())
        lastOngoing = notificationKey(server, runningCount())
        ServiceCompat.startForeground(
            this,
            WATCH_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun startWatching() {
        if (started) return
        started = true
        startedAt = System.currentTimeMillis() - START_GRACE_MS

        // Anything already in the inbox when we start is not "new": no notification, no spam.
        seenKeys.addAll(repository.inbox.value.map { it.key })

        scope.launch { watchInbox() }
        scope.launch { watchSessions() }
        scope.launch { watchServers() }
    }

    private suspend fun watchInbox() {
        combine(repository.inbox, store.notifyKinds) { inbox, kinds -> inbox to kinds }
            .collect { (inbox, kinds) ->
                for (item in inbox) {
                    if (!seenKeys.add(item.key)) continue
                    val isHistory = (item.kind == InboxItem.Kind.DONE || item.kind == InboxItem.Kind.FAILED) && item.time < startedAt
                    if (isHistory) continue
                    if (item.kind.name in kinds) Notifier.post(applicationContext, item)
                }
            }
    }

    private suspend fun watchSessions() {
        combine(repository.sessions, repository.activeServer) { sessions, active -> sessions to active }
            .collect { (sessions, active) ->
                val running = sessions.count { it.state == RunState.RUNNING }
                val serverName = active?.name ?: repository.servers.value.firstOrNull()?.name
                updateOngoing(serverName, running)
            }
    }

    private suspend fun watchServers() {
        var sawServer = false
        repository.servers.collect { servers: List<ServerConfig> ->
            if (servers.isNotEmpty()) {
                sawServer = true
                return@collect
            }
            if (sawServer) {
                stopWatch()
                return@collect
            }
            // At cold start DataStore may not have loaded the server list yet. Give it a moment.
            delay(CONFIG_GRACE_MS)
            if (repository.servers.value.isEmpty()) stopWatch()
        }
    }

    private fun updateOngoing(serverName: String?, running: Int) {
        val key = notificationKey(serverName, running)
        if (key == lastOngoing) return
        lastOngoing = key
        ServiceCompat.startForeground(
            this,
            WATCH_ID,
            Notifier.ongoingNotification(this, serverName, running),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun currentServerName(): String? {
        val repo = runCatching { repository }.getOrNull() ?: return null
        return repo.activeServer.value?.name ?: repo.servers.value.firstOrNull()?.name
    }

    private fun runningCount(): Int =
        runCatching { repository.sessions.value.count { it.state == RunState.RUNNING } }.getOrDefault(0)

    private fun notificationKey(serverName: String?, running: Int): String =
        "${serverName ?: ""}|$running"

    private fun stopWatch() {
        if (stopping) return
        stopping = true
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val repository get() = ParallaxApp.container.repository
    private val store get() = ParallaxApp.container.store

    private companion object {
        const val WATCH_ID = 1001
        const val CONFIG_GRACE_MS = 8_000L
        const val START_GRACE_MS = 5_000L
    }
}
