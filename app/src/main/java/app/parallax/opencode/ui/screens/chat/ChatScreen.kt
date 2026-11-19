package app.parallax.opencode.ui.screens.chat

import app.parallax.opencode.ui.components.softShadow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.parallax.opencode.core.model.ModelRef
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.ChoicePill
import app.parallax.opencode.ui.components.CircleIconButton
import app.parallax.opencode.ui.components.ErrorPill
import app.parallax.opencode.ui.components.IconPillGroup
import app.parallax.opencode.ui.components.IconTile
import app.parallax.opencode.ui.components.ListRow
import app.parallax.opencode.ui.components.Orb
import app.parallax.opencode.ui.components.OrbMood
import app.parallax.opencode.ui.components.SectionLabel
import app.parallax.opencode.ui.components.SoftComposer
import app.parallax.opencode.ui.components.dirLeaf
import app.parallax.opencode.ui.preview.FakeAgentRepository
import app.parallax.opencode.ui.screens.newtask.NewTaskViewModel
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Space
import app.parallax.opencode.ui.theme.ThemeMode

private val Suggestions = listOf(
    "Fix the failing tests",
    "Review my last commit",
    "Explain this codebase",
    "Add a dark mode",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: AgentRepository,
    onMenu: () -> Unit,
    onLaunched: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSession: (String) -> Unit = {},
    onVoice: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    vm: NewTaskViewModel = viewModel { NewTaskViewModel(repository) },
) {
    val c = ParallaxTheme.colors
    val models by vm.models.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val defaultModel by vm.defaultModel.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val launched by vm.launched.collectAsStateWithLifecycle()
    val sessions by repository.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeServer by repository.activeServer.collectAsStateWithLifecycle(initialValue = null)

    var prompt by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf<String?>(null) }
    var selectedModel by remember { mutableStateOf<ModelRef?>(null) }
    var parallelModels by remember { mutableStateOf(setOf<ModelRef>()) }
    var runsPerModel by remember { mutableStateOf(1) }
    var modelOpen by remember { mutableStateOf(false) }
    var projectOpen by remember { mutableStateOf(false) }
    var parallelOpen by remember { mutableStateOf(false) }

    LaunchedEffect(defaultModel, models) {
        if (selectedModel == null && models.isNotEmpty()) {
            val def = models.find { it.ref.key == defaultModel?.key }
            val first = def?.takeIf { it.isGo }
                ?: models.firstOrNull { it.isGo && it.ref.modelId == "deepseek-v4.1-flash" }
                ?: models.firstOrNull { it.isGo }
                ?: def
                ?: models.firstOrNull()
            selectedModel = first?.ref
            if (first != null) parallelModels = setOf(first.ref)
        }
    }
    LaunchedEffect(launched) {
        launched?.let { onLaunched(it); vm.consumeLaunched() }
    }

    val runningCount = sessions.count { it.state == RunState.RUNNING }
    val needsYou = sessions.any { it.state == RunState.NEEDS_YOU }
    val mood = when {
        needsYou -> OrbMood.NEEDS_YOU
        runningCount > 0 -> OrbMood.WORKING
        else -> OrbMood.IDLE
    }
    val modelName = models.find { it.ref.key == selectedModel?.key }?.name
        ?: selectedModel?.let { app.parallax.opencode.ui.components.prettyModelId(it.modelId) }
        ?: "Model"
    val modelIsGo = models.find { it.ref.key == selectedModel?.key }?.isGo == true
    val parallelCount = parallelModels.size * runsPerModel

    fun send() {
        val text = prompt.trim()
        if (text.isEmpty()) return
        if (parallelCount > 1) {
            val runs = parallelModels.toList().flatMap { m ->
                (1..runsPerModel).map { n ->
                    val label = if (runsPerModel > 1) "${m.modelId} #$n" else m.modelId
                    app.parallax.opencode.core.model.RunSpec(model = m, agent = null, label = label)
                }
            }
            vm.launchParallelDirect(text, directory, runs)
        } else {
            vm.launch(prompt = text, title = null, directory = directory, agent = null, models = listOfNotNull(selectedModel), runsPerModel = 1)
        }
        prompt = ""
    }
    val modelNames = remember(models) { models.associate { it.ref.key to it.name } }
    // Phone chats, plus anything live from elsewhere (an agent you started in OpenChamber shows up here while it runs).
    val recent = sessions.filter {
        it.parentId == null && (it.fromParallax || it.state == RunState.RUNNING || it.state == RunState.NEEDS_YOU)
    }.sortedWith(compareByDescending<app.parallax.opencode.core.model.AgentSession> { it.state == RunState.RUNNING || it.state == RunState.NEEDS_YOU }.thenByDescending { it.updatedAt })
    val serverName = activeServer?.name ?: "Parallax"
    val status = when {
        needsYou -> "Something needs your OK"
        runningCount == 1 -> "1 agent working"
        runningCount > 1 -> "$runningCount agents working"
        else -> "Tap me to talk"
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                // Leave room for the floating tab bar unless the keyboard is up (it covers the bar).
                .padding(bottom = if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) Space.sm else app.parallax.opencode.ui.components.TabBarClearance),
        ) {
            // Top row: server initial (menu) and "+" (fresh chat)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).softShadow(app.parallax.opencode.ui.theme.Elevation.card, CircleShape)
                        .clip(CircleShape).background(c.raised).clickable(onClick = onMenu),
                    contentAlignment = Alignment.Center,
                ) { Text(serverName.trim().take(1).uppercase(), style = ParallaxTheme.type.headline, color = c.textMuted) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(52.dp).softShadow(app.parallax.opencode.ui.theme.Elevation.card, CircleShape)
                        .clip(CircleShape).background(c.surface).clickable { prompt = ""; directory = null },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, contentDescription = "New chat", tint = c.text, modifier = Modifier.size(28.dp)) }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.gutter, vertical = Space.md),
            ) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.clip(CircleShape).clickable(onClick = onVoice)) {
                            app.parallax.opencode.ui.components.Orb(104.dp, mood)
                        }
                        Spacer(Modifier.height(Space.md))
                        Text(serverName, style = ParallaxTheme.type.body, color = c.textMuted)
                        Text(status, style = ParallaxTheme.type.caption, color = c.textFaint, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (recent.isEmpty()) {
                    item(key = "suggest") {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Text("Try asking", style = ParallaxTheme.type.label, color = c.text, modifier = Modifier.padding(bottom = Space.xs))
                            Suggestions.forEach { sug ->
                                app.parallax.opencode.ui.components.ListRow(
                                    title = sug, body = null, bodyLines = 1,
                                    leading = { app.parallax.opencode.ui.components.Mascot(40.dp, seed = sug) },
                                    onClick = { prompt = sug },
                                )
                            }
                        }
                    }
                } else {
                    items(recent.take(6), key = { it.id }) { s ->
                        app.parallax.opencode.ui.components.SwipeableSessionRow(
                            session = s,
                            modelName = app.parallax.opencode.ui.components.displayModelName(s.model, modelNames),
                            onOpen = { onOpenSession(s.id) },
                            onDelete = { vm.delete(s.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (recent.size > 6) {
                        item(key = "seeall") {
                            Text(
                                "See all ${recent.size} chats", style = ParallaxTheme.type.label, color = c.accentText,
                                modifier = Modifier.clip(CircleShape).clickable(onClick = onSeeAll).padding(horizontal = Space.sm, vertical = Space.md),
                            )
                        }
                    }
                }
            }

            // Options in one subtle line, then the Grok-style bottom row.
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(bottom = Space.sm),
            ) {
                item { MiniPill(label = if (parallelModels.size > 1) "${parallelModels.size} models" else modelName, badge = modelIsGo && parallelModels.size <= 1, onClick = { modelOpen = true }) }
                item { MiniPill(label = dirLeaf(directory), icon = Icons.Rounded.Folder, onClick = { projectOpen = true }) }
                item { MiniPill(label = "×${parallelCount.coerceAtLeast(1)}", onClick = { parallelOpen = true }) }
            }
            app.parallax.opencode.ui.components.ChatComposer(
                value = prompt,
                onValueChange = { prompt = it },
                hint = "Ask anything",
                onSend = { send() },
                onPlus = { parallelOpen = true },
                plusIcon = Icons.Rounded.Tune,
                onVoice = onVoice,
                enabled = !busy && !loading,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
            )
        }

        if (busy) {
            Box(Modifier.align(Alignment.Center)) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(28.dp))
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

    if (modelOpen) {
        app.parallax.opencode.ui.components.ModelPicker(
            models = models,
            selected = setOfNotNull(selectedModel?.key),
            onDismiss = { modelOpen = false },
            onConfirm = { picked -> selectedModel = picked.firstOrNull() ?: selectedModel; parallelModels = emptySet(); runsPerModel = 1; modelOpen = false },
        )
    }

    if (projectOpen) {
        ModalBottomSheet(
            onDismissRequest = { projectOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
        ) {
            Column(Modifier.padding(horizontal = Space.gutter).padding(bottom = 48.dp)) {
                SectionLabel("Project")
                ListRow(
                    title = "Default",
                    body = "Server default directory",
                    leading = { IconTile(Icons.Rounded.Folder, size = 36.dp) },
                    trailing = if (directory == null) {
                        { Icon(Icons.Rounded.Check, null, tint = c.accentText, modifier = Modifier.size(20.dp)) }
                    } else null,
                    onClick = { directory = null; projectOpen = false },
                )
                projects.forEach { p ->
                    ListRow(
                        title = p.name,
                        body = p.directory,
                        leading = { IconTile(Icons.Rounded.Folder, size = 36.dp) },
                        trailing = if (directory == p.directory) {
                            { Icon(Icons.Rounded.Check, null, tint = c.accentText, modifier = Modifier.size(20.dp)) }
                        } else null,
                        onClick = { directory = p.directory; projectOpen = false },
                    )
                }
            }
        }
    }

    if (parallelOpen) {
        app.parallax.opencode.ui.components.ModelPicker(
            models = models,
            selected = (if (parallelModels.isEmpty()) setOfNotNull(selectedModel?.key) else parallelModels.map { it.key }.toSet()),
            multi = true,
            runsPerModel = runsPerModel,
            onRunsChange = { runsPerModel = it },
            onDismiss = { parallelOpen = false },
            onConfirm = { picked -> parallelModels = picked.toSet(); parallelOpen = false },
        )
    }
}

@Composable
private fun MiniPill(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    badge: Boolean = false,
) {
    val c = ParallaxTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(c.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.textMuted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label, style = ParallaxTheme.type.bodySmall, color = c.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (badge) {
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier.clip(CircleShape).background(c.accentWash).padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Go", style = ParallaxTheme.type.caption, color = c.accentText)
            }
        }
    }
}

@Composable
private fun ModelSheetRow(name: String, sub: String, go: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = ParallaxTheme.colors
    ListRow(
        title = name,
        body = sub,
        leading = {
            IconTile(
                Icons.Rounded.Folder, size = 36.dp,
                tint = if (selected) c.accentText else c.textMuted,
                background = if (selected) c.accentWash else c.raised,
            )
        },
        trailing = if (selected) {
            { Icon(Icons.Rounded.Check, null, tint = c.accentText, modifier = Modifier.size(20.dp)) }
        } else if (go) {
            {
                Box(
                    Modifier.clip(CircleShape).background(c.accentWash).padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Go", style = ParallaxTheme.type.caption, color = c.accentText) }
            }
        } else null,
        onClick = onClick,
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Chat light")
@Composable
private fun ChatPreviewLight() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.LIGHT) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            ChatScreen(repository = FakeAgentRepository(), onMenu = {}, onLaunched = {})
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Chat dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatPreviewDark() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.DARK) {
        Box(Modifier.background(ParallaxTheme.colors.bg)) {
            ChatScreen(repository = FakeAgentRepository(), onMenu = {}, onLaunched = {})
        }
    }
}
