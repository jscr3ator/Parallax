package app.parallax.opencode.ui.screens.agents

import app.parallax.opencode.ui.components.SegmentedControl
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.CircleIconButton
import app.parallax.opencode.ui.components.ErrorPill
import app.parallax.opencode.ui.components.LoadingSkeleton
import app.parallax.opencode.ui.components.ParallelGroupCard
import app.parallax.opencode.ui.components.ScreenTitle
import app.parallax.opencode.ui.components.SectionLabel
import app.parallax.opencode.ui.components.SwipeableSessionRow
import app.parallax.opencode.ui.components.dayLabel
import app.parallax.opencode.ui.components.parallelGroups
import app.parallax.opencode.ui.components.rememberModelNames
import app.parallax.opencode.ui.components.displayModelName
import app.parallax.opencode.ui.preview.FakeAgentRepository
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Space
import app.parallax.opencode.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AgentsScreen(
    repository: AgentRepository,
    onMenu: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: AgentsViewModel = viewModel { AgentsViewModel(repository) },
) {
    val c = ParallaxTheme.colors
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val showAll by vm.showAll.collectAsStateWithLifecycle()
    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val modelNames = rememberModelNames(repository)
    var renameTarget by remember { mutableStateOf<AgentSession?>(null) }

    val groups = remember(sessions) { parallelGroups(sessions) }
    val groupedIds = remember(groups) { groups.flatten().map { it.id }.toSet() }
    val now = System.currentTimeMillis()
    val needsYou = sessions.filter { it.state == RunState.NEEDS_YOU }
    val running = sessions.filter { it.state == RunState.RUNNING && it.id !in groupedIds }
    val recent = sessions.filter {
        it.state != RunState.NEEDS_YOU && it.state != RunState.RUNNING && it.parentId == null && it.id !in groupedIds
    }
    val groupedRunning = groups.filter { g -> g.any { it.state == RunState.RUNNING || it.state == RunState.NEEDS_YOU } }
    val recentEntries: List<Any> = (
        recent.map { it.updatedAt to (it as Any) } +
            groups.filter { it !in groupedRunning }.map { g -> g.maxOf { it.updatedAt } to (g as Any) }
        ).sortedByDescending { it.first }.map { it.second }

    Box(modifier.fillMaxSize().background(c.bg)) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScreenTitle("Agents", Modifier.weight(1f))
                        SegmentedControl(
                            options = listOf("Phone", "All"),
                            selected = if (showAll) 1 else 0,
                            onSelect = { vm.setShowAll(it == 1) },
                            modifier = Modifier.width(150.dp),
                        )
                    }
                    if (!showAll && hiddenCount > 0) {
                        Text(
                            "$hiddenCount desktop sessions hidden",
                            style = ParallaxTheme.type.caption,
                            color = ParallaxTheme.colors.textFaint,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                    Spacer(Modifier.height(Space.md))
                }

                if (sessions.isEmpty() && !refreshing) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = Space.xxxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No agents yet", style = ParallaxTheme.type.headline, color = c.text)
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                "Start a task from Chat and it will show up here.",
                                style = ParallaxTheme.type.bodySmall,
                                color = c.textMuted,
                            )
                        }
                    }
                }

                if (needsYou.isNotEmpty()) {
                    item { SectionLabel("Needs you") }
                    items(needsYou, key = { it.id }) { s ->
                        SwipeableSessionRow(
                            session = s,
                            modelName = displayModelName(s.model, modelNames),
                            onOpen = { onOpenSession(s.id) },
                            onDelete = { vm.delete(s.id) },
                            onRename = { renameTarget = s },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (groupedRunning.isNotEmpty() || running.isNotEmpty()) {
                    item { SectionLabel("Running") }
                    groupedRunning.forEach { g ->
                        item(key = "g-${g.first().id}") {
                            ParallelGroupCard(
                                sessions = g,
                                modelNames = modelNames,
                                onOpen = onOpenSession,
                                modifier = Modifier.animateItem(),
                                now = now,
                            )
                            Spacer(Modifier.height(Space.sm))
                        }
                    }
                    items(running, key = { it.id }) { s ->
                        SwipeableSessionRow(
                            session = s,
                            modelName = displayModelName(s.model, modelNames),
                            onOpen = { onOpenSession(s.id) },
                            onDelete = { vm.delete(s.id) },
                            onRename = { renameTarget = s },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (recentEntries.isNotEmpty()) {
                    var lastDay: String? = null
                    recentEntries.forEach { entry ->
                        val updated = if (entry is AgentSession) entry.updatedAt else (entry as List<*>).maxOf { (it as AgentSession).updatedAt }
                        val firstId = if (entry is AgentSession) entry.id else ((entry as List<*>).first() as AgentSession).id
                        val day = dayLabel(now, updated)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "day-$day-$firstId") { SectionLabel(day) }
                        }
                        if (entry !is AgentSession) {
                            @Suppress("UNCHECKED_CAST")
                            val g = entry as List<AgentSession>
                            item(key = "g-$firstId") {
                                ParallelGroupCard(
                                    sessions = g,
                                    modelNames = modelNames,
                                    onOpen = onOpenSession,
                                    modifier = Modifier.animateItem(),
                                    now = now,
                                )
                                Spacer(Modifier.height(Space.sm))
                            }
                            return@forEach
                        }
                        val s = entry
                        item(key = s.id) {
                            SwipeableSessionRow(
                                session = s,
                                modelName = displayModelName(s.model, modelNames),
                                onOpen = { onOpenSession(s.id) },
                                onDelete = { vm.delete(s.id) },
                                onRename = { renameTarget = s },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                } else if (needsYou.isEmpty() && groupedRunning.isEmpty() && running.isEmpty() && sessions.isNotEmpty()) {
                    item { LoadingSkeleton(3) }
                }

                item { Spacer(Modifier.height(Space.xxl)) }
            }
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

    renameTarget?.let { target ->
        RenameDialog(
            current = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { vm.rename(target.id, it); renameTarget = null },
        )
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = ParallaxTheme.colors
    var text by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session", style = ParallaxTheme.type.title, color = c.text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = ParallaxTheme.type.body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accentText, unfocusedBorderColor = c.hairline,
                    cursorColor = c.accentText,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("Save", style = ParallaxTheme.type.label, color = c.accentText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = ParallaxTheme.type.label, color = c.textMuted)
            }
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Agents light")
@Composable
private fun AgentsPreviewLight() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            AgentsScreen(repository = FakeAgentRepository(), onMenu = {}, onOpenSession = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Agents dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AgentsPreviewDark() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.DARK) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            AgentsScreen(repository = FakeAgentRepository(), onMenu = {}, onOpenSession = {})
        }
    }
}
