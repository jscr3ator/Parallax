package app.parallax.opencode.ui.screens.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.components.AgentBubble
import app.parallax.opencode.ui.components.AgentThinking
import app.parallax.opencode.ui.components.ApprovalCard
import app.parallax.opencode.ui.components.ChatBubble
import app.parallax.opencode.ui.components.ChatComposer
import app.parallax.opencode.ui.components.ChatHeader
import app.parallax.opencode.ui.components.ChoicePill
import app.parallax.opencode.ui.components.CircleIconButton
import app.parallax.opencode.ui.components.DayStamp
import app.parallax.opencode.ui.components.ErrorPill
import app.parallax.opencode.ui.components.FormBlock
import app.parallax.opencode.ui.components.IconPillGroup
import app.parallax.opencode.ui.components.IconTile
import app.parallax.opencode.ui.components.ListRow
import app.parallax.opencode.ui.components.MarkdownText
import app.parallax.opencode.ui.components.OrbHeader
import app.parallax.opencode.ui.components.ReasoningLine
import app.parallax.opencode.ui.components.SectionLabel
import app.parallax.opencode.ui.components.SoftComposer
import app.parallax.opencode.ui.components.StepsLine
import app.parallax.opencode.ui.components.TextBubble
import app.parallax.opencode.ui.components.ToolActivity
import app.parallax.opencode.ui.components.ToolGroupActivity
import app.parallax.opencode.ui.components.TypingDots
import app.parallax.opencode.ui.components.UserBubble
import app.parallax.opencode.ui.components.chatStamp
import app.parallax.opencode.ui.components.displayModelName
import app.parallax.opencode.ui.components.prettyModelId
import app.parallax.opencode.ui.components.rememberModelNames
import app.parallax.opencode.ui.components.toMood
import app.parallax.opencode.ui.preview.FakeAgentRepository
import app.parallax.opencode.ui.screens.agents.RenameDialog
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Space
import app.parallax.opencode.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/*
 * Session = a clean conversation (Grok / Muse style). The agent talks in bubbles; tool use is reduced to
 * one quiet "Working · …" / "Took 4 steps" line; approvals appear as a Muse-style Deny / Allow card.
 */

/** One rendered row of the conversation, after collapsing tool calls into step groups. */
private sealed interface Row2 {
    val key: String
    data class Stamp(override val key: String, val text: String) : Row2
    data class User(override val key: String, val text: String, val queued: Boolean) : Row2
    data class Agent(override val key: String, val text: String) : Row2
    data class Steps(override val key: String, val tools: List<TranscriptItem.Tool>, val live: Boolean) : Row2
    data class Approval(override val key: String, val item: TranscriptItem.Permission) : Row2
    data class Question(override val key: String, val item: TranscriptItem.Form) : Row2
    data class Problem(override val key: String, val text: String) : Row2
}

private fun buildRows(items: List<TranscriptItem>, running: Boolean): List<Row2> {
    val out = mutableListOf<Row2>()
    var pending = mutableListOf<TranscriptItem.Tool>()
    var lastStampAt = 0L
    fun flush(live: Boolean) {
        if (pending.isNotEmpty()) {
            out += Row2.Steps("steps-" + pending.first().id, pending.toList(), live)
            pending = mutableListOf()
        }
    }
    items.forEachIndexed { i, item ->
        when (item) {
            is TranscriptItem.Tool -> pending += item
            is TranscriptItem.Reasoning -> Unit // thinking stays invisible; the live line covers it
            is TranscriptItem.User -> {
                flush(false)
                if (item.time - lastStampAt > 30 * 60_000L) {
                    out += Row2.Stamp("stamp-" + item.id, chatStamp(item.time)); lastStampAt = item.time
                }
                out += Row2.User(item.id, item.text, item.queued)
            }
            is TranscriptItem.AssistantText -> {
                flush(false)
                if (item.text.isNotBlank()) out += Row2.Agent(item.id, item.text)
            }
            is TranscriptItem.Permission -> { flush(false); out += Row2.Approval(item.id, item) }
            is TranscriptItem.Form -> { flush(false); out += Row2.Question(item.id, item) }
            is TranscriptItem.RunEnd -> {
                flush(false)
                if (item.outcome == RunState.FAILED) out += Row2.Problem(item.id, item.error ?: "Something went wrong on this run.")
            }
            is TranscriptItem.Notice -> if (item.tone != TranscriptItem.Notice.Tone.INFO) { flush(false); out += Row2.Problem(item.id, item.text) }
        }
    }
    // Trailing steps are "live" while the session runs or any step is still going.
    flush(running || pending.any { it.status == app.parallax.opencode.core.model.ToolStatus.RUNNING })
    return out
}

private val testCommand = Regex("""\b(?:tests?|pytest|jest|vitest|testDebugUnitTest)\b""", RegexOption.IGNORE_CASE)

/** Link each recap line to its source row in the latest turn. */
private fun recapEntries(rows: List<Row2>): List<Pair<String, String>> {
    val start = rows.indexOfLast { it is Row2.User }
    if (start < 0 || start == rows.lastIndex) return emptyList()
    val turn = rows.subList(start, rows.size)
    val entries = linkedMapOf<String, String>()
    val prompt = turn.first() as Row2.User
    entries[prompt.key] = "Request · ${prompt.text}"
    val steps = turn.filterIsInstance<Row2.Steps>()
    val tools = steps.flatMap { row -> row.tools.map { row.key to it } }
    val edits = tools.filter { (_, tool) -> tool.name in setOf("write", "edit", "apply_patch", "patch") && tool.status == app.parallax.opencode.core.model.ToolStatus.SUCCESS }
    edits.lastOrNull()?.let { (key, _) ->
        entries[key] = "Changes · " + edits.map { it.second.subject }.distinct().joinToString(", ")
    }
    val checks = tools.filter { (_, tool) ->
        tool.name in setOf("bash", "shell") && testCommand.containsMatchIn(tool.subject)
    }
    val failed = tools.lastOrNull { it.second.status == app.parallax.opencode.core.model.ToolStatus.FAILED }
    val highlights = listOfNotNull(checks.lastOrNull(), failed, tools.lastOrNull()).distinctBy { it.second.id }
    for ((key, tool) in highlights) {
        if (edits.any { it.second.id == tool.id }) continue
        val detail = "${tool.status.name.lowercase().replaceFirstChar { it.uppercase() }} · ${tool.verb} ${tool.subject}"
        entries[key] = entries[key]?.let { "$it\n$detail" } ?: detail
    }
    turn.filterIsInstance<Row2.Agent>().lastOrNull()?.let { entries[it.key] = it.text.trim().lineSequence().firstOrNull { line -> line.isNotBlank() }.orEmpty() }
    turn.filterIsInstance<Row2.Problem>().lastOrNull()?.let { entries[it.key] = it.text }
    turn.filterIsInstance<Row2.Approval>().lastOrNull()?.let { entries[it.key] = "Needs approval · ${it.item.request.action}" }
    turn.filterIsInstance<Row2.Question>().lastOrNull()?.let { entries[it.key] = it.item.form.title ?: "Needs your answer" }
    return entries.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    sessionId: String,
    repository: AgentRepository,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    onVoice: (String) -> Unit = {},
    vm: SessionViewModel = viewModel(key = "session-$sessionId") { SessionViewModel(sessionId, repository) },
) {
    val c = ParallaxTheme.colors
    val session by vm.session.collectAsStateWithLifecycle()
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var composerText by androidx.compose.runtime.saveable.rememberSaveable(sessionId) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val state = session?.state ?: RunState.IDLE
    val running = state == RunState.RUNNING
    val queueFollowUp = running || state == RunState.NEEDS_YOU
    val sending by vm.composerBusy.collectAsStateWithLifecycle()
    val queued = transcript.filterIsInstance<TranscriptItem.User>().filter { it.queued }
    var queueOpen by remember(sessionId) { mutableStateOf(false) }
    var recapOpen by remember(sessionId) { mutableStateOf(false) }
    val rows = remember(transcript, running) {
        buildRows(transcript.filterNot { it is TranscriptItem.User && it.queued }, running)
    }
    val recap = remember(rows) { recapEntries(rows) }
    val streamingText = transcript.lastOrNull() is TranscriptItem.AssistantText &&
        (transcript.last() as TranscriptItem.AssistantText).streaming
    val lastIsSteps = rows.lastOrNull() is Row2.Steps
    val showThinking = running && !streamingText && !lastIsSteps

    // Stick to the bottom while the user is there (follows streaming); scrolling up releases it.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index >= info.totalItemsCount - 2
        }
    }
    // Only the user's own drags may release (or re-engage) sticking; programmatic scrolls and layout changes never do.
    var stick by remember(sessionId) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { i ->
            when (i) {
                is androidx.compose.foundation.interaction.DragInteraction.Start -> stick = false
                is androidx.compose.foundation.interaction.DragInteraction.Stop,
                is androidx.compose.foundation.interaction.DragInteraction.Cancel -> {
                    kotlinx.coroutines.delay(350) // let the fling settle before deciding
                    stick = atBottom
                }
            }
        }
    }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // Any change (incl. a history reload that re-keys rows) must re-pin to the bottom while sticking.
    val contentKey = rows.hashCode() to showThinking
    LaunchedEffect(contentKey, stick, imeBottom) {
        if (!stick) return@LaunchedEffect
        // Index from our own data, not layoutInfo (which is still 0 before the first layout pass).
        val last = rows.size - 1 + (if (showThinking) 1 else 0)
        if (last >= 0) listState.scrollToItem(last, scrollOffset = 100_000)
    }
    // Bubbles (markdown) grow after their first frame; while sticking, keep pinned whenever there's more below.
    val lastIndex by androidx.compose.runtime.rememberUpdatedState(rows.size - 1 + (if (showThinking) 1 else 0))
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.canScrollForward }.collect { more ->
            if (more && stick && lastIndex >= 0) listState.scrollToItem(lastIndex, scrollOffset = 100_000)
        }
    }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) {
                ChatHeader(
                    title = session?.title?.ifBlank { null } ?: "New chat",
                    mood = state.toMood(),
                    live = running,
                    onBack = onBack,
                    onTitleClick = { renameOpen = true },
                    rightIcon = Icons.Rounded.MoreHoriz,
                    rightLabel = "Options",
                    onRight = { menuOpen = true },
                    seed = sessionId,
                )
                Box(Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = Space.gutter)) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename", style = ParallaxTheme.type.body) }, onClick = { menuOpen = false; renameOpen = true })
                        DropdownMenuItem(text = { Text("Change model", style = ParallaxTheme.type.body) }, onClick = { menuOpen = false; modelOpen = true })
                        DropdownMenuItem(text = { Text("Delete", style = ParallaxTheme.type.body, color = c.failed) }, onClick = { menuOpen = false; deleteConfirm = true })
                    }
                }
            }

            if (recap.isNotEmpty() || queued.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                    if (recap.isNotEmpty()) TextButton(onClick = { recapOpen = true }) {
                        Text("Session recap", style = ParallaxTheme.type.label)
                    }
                    Spacer(Modifier.weight(1f))
                    if (queued.isNotEmpty()) TextButton(onClick = { queueOpen = true }) {
                        Text("Queued (${queued.size})", style = ParallaxTheme.type.label)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.lg, bottom = Space.xl),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rows.forEach { row ->
                        item(key = row.key) {
                            val m = Modifier.animateItem()
                            when (row) {
                                is Row2.Stamp -> DayStamp(row.text, m)
                                is Row2.User -> UserBubble(row.text, row.queued, m)
                                is Row2.Agent -> AgentBubble(row.text, m)
                                is Row2.Steps -> StepsLine(row.tools, row.live, onOpenSession, m)
                                is Row2.Approval -> {
                                    val r = row.item.request
                                    ApprovalCard(
                                        icon = Icons.Rounded.Shield,
                                        title = friendlyAsk(r.action),
                                        message = r.message ?: "Your agent wants your OK before continuing.",
                                        details = if (r.resources.isEmpty()) null else ({
                                            r.resources.take(4).forEach { Text(it, style = ParallaxTheme.type.monoSmall, color = c.textMuted, maxLines = 2) }
                                        }),
                                        onDeny = { vm.replyPermission(r.id, PermissionDecision.REJECT) },
                                        onAllow = { vm.replyPermission(r.id, PermissionDecision.ONCE) },
                                        secondaryLabel = "Always allow",
                                        onSecondary = { vm.replyPermission(r.id, PermissionDecision.ALWAYS) },
                                        modifier = m,
                                    )
                                }
                                is Row2.Question -> FormBlock(
                                    form = row.item.form, busy = false,
                                    onCancel = { vm.cancelForm(row.item.form.id) },
                                    onSubmit = { vm.replyForm(row.item.form.id, it) },
                                    modifier = m,
                                )
                                is Row2.Problem -> Text(
                                    row.text, style = ParallaxTheme.type.bodySmall, color = c.failed,
                                    modifier = m.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    if (showThinking) item(key = "__thinking") { AgentThinking() }
                }
                // Soft fade under the header.
                Box(Modifier.fillMaxWidth().height(16.dp).background(Brush.verticalGradient(listOf(c.bg, Color.Transparent))))

                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && rows.isNotEmpty(),
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.md),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c.surface)
                            .clickable { stick = true; scope.launch { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Jump to latest", tint = c.text, modifier = Modifier.size(20.dp)) }
                }
            }

            ChatComposer(
                value = composerText,
                onValueChange = { composerText = it },
                hint = if (queueFollowUp) "Queue a follow-up…" else "Message",
                enabled = !sending,
                queueFollowUp = queueFollowUp,
                running = running,
                onSend = {
                    val draft = composerText
                    vm.send(draft, queueFollowUp) {
                        if (composerText == draft) composerText = ""
                        stick = true
                    }
                },
                onStop = { vm.stop() },
                onPlus = { modelOpen = true },
                onVoice = { onVoice(sessionId) },
                modifier = Modifier.padding(horizontal = Space.gutter).navigationBarsPadding().imePadding().padding(top = Space.xs, bottom = Space.md),
            )
        }

        if (error != null) {
            ErrorPill(
                message = error.orEmpty(),
                onDismiss = { vm.clearError() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Space.xl, start = Space.gutter, end = Space.gutter),
            )
        }
    }

    if (queueOpen || recapOpen) {
        ModalBottomSheet(onDismissRequest = { queueOpen = false; recapOpen = false }, containerColor = c.surface) {
            LazyColumn(contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
                modifier = Modifier.navigationBarsPadding()) {
                item { Text(if (queueOpen) "Queued follow-ups" else "Latest turn", style = ParallaxTheme.type.headline, color = c.text) }
                if (queueOpen) {
                    if (queued.isEmpty()) item { Text("Queue is empty", modifier = Modifier.padding(vertical = Space.md), color = c.textMuted) }
                    queued.forEach { prompt ->
                        item(key = prompt.id) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(prompt.text, Modifier.weight(1f).padding(vertical = Space.md), color = c.text)
                                TextButton(onClick = { vm.cancelQueued(prompt.id) }) { Text("Cancel") }
                            }
                        }
                    }
                } else {
                    recap.forEach { (key, label) ->
                        item(key = key) {
                            Text(label, color = c.text, style = ParallaxTheme.type.body, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    recapOpen = false
                                    val index = rows.indexOfFirst { it.key == key }
                                    if (index >= 0) { stick = false; scope.launch { listState.scrollToItem(index) } }
                                }.padding(vertical = Space.md))
                        }
                    }
                }
            }
        }
    }
    if (renameOpen) {
        RenameDialog(current = session?.title.orEmpty(), onDismiss = { renameOpen = false }, onConfirm = { vm.rename(it); renameOpen = false })
    }
    if (modelOpen) {
        ModelPickerSheet(
            repository = repository,
            current = session?.model?.key,
            onDismiss = { modelOpen = false },
            onPick = { vm.changeModel(it); modelOpen = false },
        )
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete chat?", style = ParallaxTheme.type.title, color = c.text) },
            text = { Text("This removes it from the server.", style = ParallaxTheme.type.bodySmall, color = c.textMuted) },
            confirmButton = {
                TextButton(onClick = { vm.delete(); deleteConfirm = false; if (!deleted) { deleted = true; onBack() } }) {
                    Text("Delete", style = ParallaxTheme.type.label, color = c.failed)
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel", style = ParallaxTheme.type.label, color = c.textMuted) } },
        )
    }
}

/** "bash" → "Allow running a command?" — plain language for approval cards. */
private fun friendlyAsk(action: String): String = when (action.lowercase()) {
    "bash", "shell" -> "Allow running a command?"
    "edit", "write", "patch" -> "Allow changing files?"
    "webfetch", "websearch" -> "Allow going online?"
    "external_directory" -> "Allow working outside the project?"
    else -> "Allow ${action.replace('_', ' ')}?"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    repository: AgentRepository,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (app.parallax.opencode.core.model.ModelRef) -> Unit,
) {
    val c = ParallaxTheme.colors
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    LaunchedEffect(Unit) {
        models = runCatching { repository.models() }.getOrDefault(emptyList())
    }
    app.parallax.opencode.ui.components.ModelPicker(
        models = models,
        selected = setOfNotNull(current),
        onDismiss = onDismiss,
        onConfirm = { picked -> picked.firstOrNull()?.let(onPick) },
    )
}

@Composable
private fun ModelSheetRow(name: String, isGo: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = ParallaxTheme.colors
    ListRow(
        title = name,
        body = null,
        leading = {
            IconTile(
                androidx.compose.material.icons.Icons.Rounded.Check, size = 36.dp,
                tint = if (selected) c.accentText else c.textMuted,
                background = if (selected) c.accentWash else c.raised,
            )
        },
        trailing = if (selected) {
            { Icon(Icons.Rounded.Check, null, tint = c.accentText, modifier = Modifier.size(20.dp)) }
        } else if (isGo) {
            {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .clip(CircleShape)
                        .background(c.accentWash)
                        .padding(horizontal = Space.md, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Go", style = ParallaxTheme.type.caption, color = c.accentText) }
            }
        } else null,
        onClick = onClick,
    )
}

@androidx.compose.ui.tooling.preview.Preview(name = "Session light")
@Composable
private fun SessionPreviewLight() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.LIGHT) {
        SessionScreen(sessionId = "ses-dark", repository = FakeAgentRepository(), onBack = {}, onOpenSession = {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Session dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SessionPreviewDark() {
    app.parallax.opencode.ui.theme.ParallaxTheme(ThemeMode.DARK) {
        SessionScreen(sessionId = "ses-dark", repository = FakeAgentRepository(), onBack = {}, onOpenSession = {})
    }
}
