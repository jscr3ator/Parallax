package app.parallax.opencode.ui.screens.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parallax.opencode.core.data.ServerStore
import app.parallax.opencode.core.model.ConnectionState
import app.parallax.opencode.core.model.InboxItem
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.UsageSummary
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.AiryToggleRow
import app.parallax.opencode.ui.components.IconTile
import app.parallax.opencode.ui.components.ListRow
import app.parallax.opencode.ui.components.SectionLabel
import app.parallax.opencode.ui.components.SegmentedControl
import app.parallax.opencode.ui.components.SoftCard
import app.parallax.opencode.ui.components.UsageMeter
import app.parallax.opencode.ui.components.dotColor
import app.parallax.opencode.ui.components.prettyModelId
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Space
import app.parallax.opencode.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    repository: AgentRepository,
    store: ServerStore,
    onDismiss: () -> Unit,
    onAddServer: () -> Unit,
) {
    val c = ParallaxTheme.colors
    val scope = rememberCoroutineScope()
    val servers by repository.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by repository.activeServer.collectAsStateWithLifecycle(initialValue = null)
    val connection by repository.connection.collectAsStateWithLifecycle(initialValue = ConnectionState.NoServer)
    val theme by store.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val notifyKinds by store.notifyKinds.collectAsStateWithLifecycle(initialValue = emptySet())
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var usage by remember { mutableStateOf<List<UsageSummary>>(emptyList()) }
    var modelsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(active?.id) {
        models = runCatching { repository.models() }.getOrDefault(emptyList())
        usage = runCatching { repository.usage() }.getOrDefault(emptyList())
    }

    val statusText = when (val s = connection) {
        is ConnectionState.Connected -> "Connected${s.serverVersion?.let { " · v$it" } ?: ""}"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Offline -> "Offline"
        is ConnectionState.AuthFailed -> "Auth failed"
        ConnectionState.NoServer -> "No server"
    }
    val statusDot = when (connection) {
        is ConnectionState.Connected -> c.done
        is ConnectionState.Connecting -> c.textFaint
        is ConnectionState.NoServer -> c.textFaint
        else -> c.failed
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(Modifier.padding(horizontal = Space.gutter).padding(bottom = 48.dp)) {
            // Current server
            SoftCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Dns, size = 44.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(active?.name ?: "No server", style = ParallaxTheme.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            active?.baseUrl ?: "Connect a server to begin",
                            style = ParallaxTheme.type.caption,
                            color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(statusDot))
                    Spacer(Modifier.width(Space.sm))
                    Text(statusText, style = ParallaxTheme.type.bodySmall, color = c.textMuted)
                }
            }

            // Other servers
            val others = servers.filter { it.id != active?.id }
            if (others.isNotEmpty()) {
                others.forEach { s ->
                    ListRow(
                        title = s.name,
                        body = s.baseUrl,
                        leading = { IconTile(Icons.Rounded.Dns, size = 36.dp) },
                        onClick = {
                            scope.launch { runCatching { repository.setActiveServer(s.id) } }
                        },
                    )
                }
            }
            ListRow(
                title = "Add server",
                body = null,
                leading = { IconTile(Icons.Rounded.Add, size = 36.dp) },
                onClick = { onDismiss(); onAddServer() },
            )

            SectionLabel("Settings")

            SoftCard(Modifier.fillMaxWidth()) {
                Text("Appearance", style = ParallaxTheme.type.label, color = c.text)
                Spacer(Modifier.height(Space.sm))
                SegmentedControl(
                    options = listOf("System", "Light", "Dark"),
                    selected = when (theme) {
                        ThemeMode.SYSTEM -> 0
                        ThemeMode.LIGHT -> 1
                        ThemeMode.DARK -> 2
                    },
                    onSelect = {
                        scope.launch {
                            store.setTheme(
                                when (it) {
                                    1 -> ThemeMode.LIGHT
                                    2 -> ThemeMode.DARK
                                    else -> ThemeMode.SYSTEM
                                },
                            )
                        }
                    },
                )
                Spacer(Modifier.height(Space.lg))
                Text("Notifications", style = ParallaxTheme.type.label, color = c.text)
                Spacer(Modifier.height(Space.xs))
                listOf(
                    InboxItem.Kind.PERMISSION to "Permissions",
                    InboxItem.Kind.FORM to "Questions",
                    InboxItem.Kind.DONE to "Finished runs",
                    InboxItem.Kind.FAILED to "Failed runs",
                ).forEach { (kind, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = ParallaxTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                        AiryToggleRow(
                            on = notifyKinds.contains(kind.name),
                            busy = false,
                            onToggle = { on ->
                                scope.launch {
                                    val next = if (on) notifyKinds + kind.name else notifyKinds - kind.name
                                    store.setNotifyKinds(next.ifEmpty { setOf("NONE") })
                                }
                            },
                        )
                    }
                }
            }

            // Models
            ListRow(
                title = "Models",
                body = if (models.isEmpty()) null else "${models.size} available",
                leading = { IconTile(Icons.Rounded.Check, size = 36.dp) },
                trailing = {
                    Text(
                        if (modelsOpen) "Hide" else "Show",
                        style = ParallaxTheme.type.bodySmall,
                        color = c.accentText,
                    )
                },
                onClick = { modelsOpen = !modelsOpen },
            )
            if (modelsOpen) {
                val go = models.filter { it.isGo }
                val rest = models.filterNot { it.isGo }
                (go + rest).forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = ParallaxTheme.type.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (m.isGo) "Included with Go" else prettyModelId(m.providerName),
                                style = ParallaxTheme.type.caption,
                                color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (m.isGo) {
                            Box(
                                Modifier.clip(CircleShape).background(c.accentWash).padding(horizontal = Space.md, vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("Go", style = ParallaxTheme.type.caption, color = c.accentText) }
                        }
                    }
                }
            }

            // Usage
            if (usage.isNotEmpty()) {
                SectionLabel("Usage")
                SoftCard(Modifier.fillMaxWidth()) {
                    usage.forEachIndexed { idx, u ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.label, style = ParallaxTheme.type.bodySmall, color = c.textMuted, modifier = Modifier.weight(1f))
                            if (u.detail != null) {
                                Text(u.detail, style = ParallaxTheme.type.caption, color = c.textMuted)
                            }
                        }
                        Spacer(Modifier.height(Space.xs))
                        UsageMeter(u.usedFraction)
                        if (idx < usage.lastIndex) Spacer(Modifier.height(Space.md))
                    }
                }
            }

            // About
            ListRow(
                title = "About",
                body = "Parallax · opencode remote",
                leading = { IconTile(Icons.Rounded.Info, size = 36.dp) },
                onClick = {},
            )
            Spacer(Modifier.height(Space.md))
        }
    }
}
