package app.parallax.opencode

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.parallax.opencode.notify.NotificationPermission
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parallax.opencode.notify.AgentWatchService
import app.parallax.opencode.ui.AppNavHost
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    /** Deep link target from a notification or a pairing link, consumed by the nav host. */
    private val pendingLink = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) AgentWatchService.ensureRunning(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        pendingLink.value = linkFrom(intent)
        val container = ParallaxApp.container
        AgentWatchService.ensureRunning(this)
        // Once a server exists: make sure the watch service runs (it stops itself while there are none)
        // and ask for notification permission (Android 13+), which is the whole point of the service.
        lifecycleScope.launch {
            container.store.servers.distinctUntilChangedBy { it.isNotEmpty() }.collect { servers ->
                if (servers.isEmpty()) return@collect
                AgentWatchService.ensureRunning(this@MainActivity)
                if (NotificationPermission.needsRequest(this@MainActivity)) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        setContent {
            val theme by container.store.theme.collectAsStateWithLifecycle(ThemeMode.LIGHT)
            ParallaxTheme(theme) {
                AppNavHost(
                    repository = container.repository,
                    store = container.store,
                    deepLink = pendingLink.value,
                    onDeepLinkHandled = { pendingLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { pendingLink.value = it }
    }

    /**
     * Links the nav host understands:
     *  - `parallax://session/<serverId>/<sessionId>` (from notifications)
     *  - `openchamber://connect?v=2&p=...` (pairing)
     */
    private fun linkFrom(intent: Intent?): String? = intent?.data?.toString()
}
