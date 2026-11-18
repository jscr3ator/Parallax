package app.parallax.opencode.ui.screens.connectors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.parallax.opencode.core.model.McpServerInfo
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.AiryToggleRow
import app.parallax.opencode.ui.components.CircleIconButton
import app.parallax.opencode.ui.components.ErrorPill
import app.parallax.opencode.ui.components.IconTile
import app.parallax.opencode.ui.components.ListRow
import app.parallax.opencode.ui.components.LoadingSkeleton
import app.parallax.opencode.ui.components.ScreenTitle
import app.parallax.opencode.ui.components.SearchField
import app.parallax.opencode.ui.components.SegmentedControl
import app.parallax.opencode.ui.preview.FakeAgentRepository
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Space
import app.parallax.opencode.ui.theme.ThemeMode

@Composable
fun ConnectorsScreen(
    repository: AgentRepository,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ConnectorsViewModel = viewModel { ConnectorsViewModel(repository) },
) {
    val c = ParallaxTheme.colors
    val servers by vm.mcp.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val toggling by vm.toggling.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var segment by remember { mutableStateOf(0) } // 0 = All, 1 = Connected

    val filtered = servers.filter { info ->
        (segment == 0 || info.status == McpServerInfo.McpStatus.CONNECTED) &&
            (query.isBlank() || info.name.contains(query, ignoreCase = true))
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = Space.sm, bottom = 140.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                CircleIconButton(Icons.Rounded.Menu, "Menu", onClick = onMenu)
                Spacer(Modifier.height(Space.sm))
                ScreenTitle("Connectors")
                Spacer(Modifier.height(Space.md))
                SearchField(value = query, onValueChange = { query = it }, hint = "Search connectors")
                Spacer(Modifier.height(Space.md))
                SegmentedControl(listOf("All", "Connected"), segment, onSelect = { segment = it })
                Spacer(Modifier.height(Space.sm))
            }

            if (loading) {
                item { LoadingSkeleton(4) }
            } else if (filtered.isEmpty() && servers.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Space.xxxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconTile(Icons.Rounded.Widgets, size = 56.dp)
                        Spacer(Modifier.height(Space.lg))
                        Text("No connectors yet", style = ParallaxTheme.type.headline, color = c.text)
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "Add MCP servers in opencode's config or in OpenChamber → Settings → MCP. They appear here with live status.",
                            style = ParallaxTheme.type.bodySmall,
                            color = c.textMuted,
                            modifier = Modifier.padding(horizontal = Space.lg),
                        )
                    }
                }
            } else {
                items(filtered, key = { it.name }) { info ->
                    ConnectorRow(
                        info = info,
                        busy = toggling.contains(info.name),
                        onToggle = { vm.setConnected(info.name, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item { Spacer(Modifier.height(Space.xxl)) }
        }

        if (error != null) {
            ErrorPill(
                message = error.orEmpty(),
                onDismiss = { vm.clearError() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Space.lg, start = Space.gutter, end = Space.gutter),
            )
        }
    }
}

@Composable
private fun ConnectorRow(
    info: McpServerInfo,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ParallaxTheme.colors
    val (tint, bg) = when (info.status) {
        McpServerInfo.McpStatus.CONNECTED -> c.accentText to c.accentWash
        McpServerInfo.McpStatus.FAILED -> c.failed to c.failedWash
        McpServerInfo.McpStatus.NEEDS_AUTH -> c.needsYou to c.needsYouWash
        else -> c.textMuted to c.raised
    }
    val statusText = when (info.status) {
        McpServerInfo.McpStatus.CONNECTED -> buildString {
            append("Connected")
            if ((info.toolCount ?: 0) > 0) append(" · ${info.toolCount} tools")
        }
        McpServerInfo.McpStatus.NEEDS_AUTH -> "Needs sign-in"
        McpServerInfo.McpStatus.FAILED -> if (info.error.isNullOrBlank()) "Failed" else "Failed: ${info.error}"
        McpServerInfo.McpStatus.DISABLED -> "Off"
        else -> info.status.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
    }
    val connected = info.status == McpServerInfo.McpStatus.CONNECTED
    ListRow(
        title = info.name,
        body = statusText,
        modifier = modifier,
        leading = { IconTile(Icons.Rounded.Extension, tint = tint, background = bg, size = 40.dp) },
        trailing = {
            AiryToggleRow(on = connected, busy = busy, onToggle = onToggle)
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Connectors light")
@Composable
private fun ConnectorsPreviewLight() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            ConnectorsScreen(repository = FakeAgentRepository(), onMenu = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Connectors dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectorsPreviewDark() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.DARK) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            ConnectorsScreen(repository = FakeAgentRepository(), onMenu = {})
        }
    }
}
