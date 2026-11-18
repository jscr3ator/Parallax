package app.parallax.opencode.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.parallax.opencode.core.model.InboxItem
import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.ApprovalCard
import app.parallax.opencode.ui.components.CircleIconButton
import app.parallax.opencode.ui.components.ErrorPill
import app.parallax.opencode.ui.components.FormBlock
import app.parallax.opencode.ui.components.IconTile
import app.parallax.opencode.ui.components.ListRow
import app.parallax.opencode.ui.components.Orb
import app.parallax.opencode.ui.components.OrbMood
import app.parallax.opencode.ui.components.ScreenTitle
import app.parallax.opencode.ui.components.SectionLabel
import app.parallax.opencode.ui.components.SoftCard
import app.parallax.opencode.ui.components.PrimaryButton
import app.parallax.opencode.ui.components.displayModelName
import app.parallax.opencode.ui.components.rememberModelNames
import app.parallax.opencode.ui.components.timeAgo
import app.parallax.opencode.ui.preview.FakeAgentRepository
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space
import app.parallax.opencode.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    repository: AgentRepository,
    onMenu: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: InboxViewModel = viewModel { InboxViewModel(repository) },
) {
    val c = ParallaxTheme.colors
    val items by vm.inbox.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val modelNames = rememberModelNames(repository)
    val now = System.currentTimeMillis()

    val actionable = items.filter { it.kind == InboxItem.Kind.PERMISSION || it.kind == InboxItem.Kind.FORM }
    val finished = items.filter { it.kind == InboxItem.Kind.DONE || it.kind == InboxItem.Kind.FAILED }

    Box(modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = Space.sm, bottom = 140.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CircleIconButton(Icons.Rounded.Menu, "Menu", onClick = onMenu)
                Spacer(Modifier.height(Space.sm))
                ScreenTitle("Inbox")
                Spacer(Modifier.height(Space.md))
            }

            if (items.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Space.xxxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Orb(96.dp, OrbMood.DONE)
                        Spacer(Modifier.height(Space.lg))
                        Text("All clear", style = ParallaxTheme.type.headline, color = c.text)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "Approvals and finished runs will land here.",
                            style = ParallaxTheme.type.bodySmall,
                            color = c.textMuted,
                        )
                    }
                }
            }

            if (actionable.isNotEmpty()) {
                item { SectionLabel("Waiting on you") }
                items(actionable, key = { it.key }) { entry ->
                    when (entry.kind) {
                        InboxItem.Kind.PERMISSION -> ApprovalCard(
                            icon = Icons.Rounded.Shield,
                            title = "Allow ${actionLabel(entry)}?",
                            message = entry.body,
                            details = if (entry.title != entry.sessionTitle) {
                                { Text(entry.sessionTitle, style = ParallaxTheme.type.monoSmall, color = c.textMuted) }
                            } else null,
                            onDeny = { vm.replyPermission(entry, PermissionDecision.REJECT) },
                            onAllow = { vm.replyPermission(entry, PermissionDecision.ONCE) },
                            secondaryLabel = "Always allow",
                            onSecondary = { vm.replyPermission(entry, PermissionDecision.ALWAYS) },
                            modifier = Modifier.animateItem(),
                        )
                        InboxItem.Kind.FORM -> SoftCard(Modifier.fillMaxWidth().animateItem()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Rounded.Forum, size = 38.dp)
                                Spacer(Modifier.width(Space.md))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = ParallaxTheme.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!entry.body.isNullOrBlank()) {
                                        Text(entry.body, style = ParallaxTheme.type.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Spacer(Modifier.height(Space.md))
                            PrimaryButton("Answer", onClick = { onOpenSession(entry.sessionId) })
                        }
                        else -> Unit
                    }
                }
            }

            if (finished.isNotEmpty()) {
                item { SectionLabel("Finished") }
                items(finished, key = { it.key }) { entry ->
                    val session = sessions.find { s -> s.id == entry.sessionId }
                    FinishedRow(
                        item = entry,
                        modelName = displayModelName(session?.model, modelNames),
                        now = now,
                        onOpen = { onOpenSession(entry.sessionId) },
                        onDismiss = { vm.dismiss(entry) },
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

private fun actionLabel(item: InboxItem): String {
    val t = item.title
    return when {
        t.startsWith("Permission", ignoreCase = true) -> t.removePrefix("Permission").trim().trimStart(':', '-', ' ').ifBlank { "this action" }
        else -> t.ifBlank { "this action" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishedRow(
    item: InboxItem,
    modelName: String,
    now: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ParallaxTheme.colors
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                false
            } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(c.raised)
                    .padding(end = Space.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.Delete,
                    contentDescription = null, tint = c.textFaint,
                )
            }
        },
        content = {
            val done = item.kind == InboxItem.Kind.DONE
            // Opaque row: the swipe background must only show while swiping.
            Box(Modifier.background(c.bg)) { ListRow(
                title = item.sessionTitle,
                body = "${if (done) "Finished" else "Failed"} · $modelName",
                leading = {
                    IconTile(
                        if (done) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        tint = if (done) c.done else c.failed,
                        background = if (done) c.raised else c.failedWash,
                        size = 36.dp,
                    )
                },
                trailing = {
                    Text(timeAgo(now, item.time), style = ParallaxTheme.type.caption, color = c.textFaint)
                },
                onClick = onOpen,
            ) }
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Inbox light")
@Composable
private fun InboxPreviewLight() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            InboxScreen(repository = FakeAgentRepository(), onMenu = {}, onOpenSession = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Inbox dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InboxPreviewDark() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.DARK) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            InboxScreen(repository = FakeAgentRepository(), onMenu = {}, onOpenSession = {})
        }
    }
}
