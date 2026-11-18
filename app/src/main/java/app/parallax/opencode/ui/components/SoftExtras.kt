package app.parallax.opencode.ui.components

import androidx.compose.foundation.combinedClickable

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.FormField
import app.parallax.opencode.core.model.FormRequest
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ToolStatus
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space

// ---------------------------------------------------------------- model names

/** Map of ModelRef.key → human display name, loaded once from the catalog. */
@Composable
fun rememberModelNames(repository: AgentRepository): Map<String, String> {
    var names by remember(repository) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(repository) {
        names = runCatching { repository.models() }.getOrDefault(emptyList())
            .associate { it.ref.key to it.name }
    }
    return names
}

fun displayModelName(ref: app.parallax.opencode.core.model.ModelRef?, names: Map<String, String>): String {
    if (ref == null) return "No model"
    return names[ref.key] ?: prettyModelId(ref.modelId)
}

fun isGoModel(option: ModelOption?): Boolean = option?.isGo == true

fun RunState.toMood(): OrbMood = when (this) {
    RunState.RUNNING -> OrbMood.WORKING
    RunState.NEEDS_YOU -> OrbMood.NEEDS_YOU
    RunState.FAILED -> OrbMood.FAILED
    RunState.DONE -> OrbMood.DONE
    RunState.IDLE -> OrbMood.IDLE
}

// ---------------------------------------------------------------- controls

/** Switch-like pill ("On") or a "Connect" text action for the Connectors list. */
@Composable
fun AiryToggleRow(
    on: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ParallaxTheme.colors
    if (on) {
        Box(
            modifier
                .clip(CircleShape)
                .background(c.accentWash)
                .clickable(enabled = !busy) { onToggle(false) }
                .padding(horizontal = Space.lg, vertical = Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text("On", style = ParallaxTheme.type.label, color = c.accentText)
        }
    } else {
        Box(
            modifier
                .clip(CircleShape)
                .clickable(enabled = !busy) { onToggle(true) }
                .padding(horizontal = Space.md, vertical = Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text("Connect", style = ParallaxTheme.type.label, color = c.accentText)
        }
    }
}

/** Rounded filled text field on `raised` (no outlines). */
@Composable
fun SoftField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
) {
    val c = ParallaxTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 8,
        textStyle = ParallaxTheme.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(c.raised)
            .padding(horizontal = Space.lg, vertical = Space.md),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = ParallaxTheme.type.body, color = c.textFaint)
                inner()
            }
        },
    )
}

/** Segmented control already exists in Soft.kt; theme toggle uses it. Thin usage meter. */
@Composable
fun UsageMeter(usedFraction: Float?, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    val f = (usedFraction ?: 0f).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(c.raised),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(6.dp)
                .clip(CircleShape)
                .background(c.accentBrush),
        )
    }
}

// ---------------------------------------------------------------- transcript pieces

/** Quiet reasoning line: sparkle icon + "Thought for 6s", tap to expand. */
@Composable
fun ReasoningLine(item: TranscriptItem.Reasoning, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    var expanded by remember(item.id) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable { expanded = !expanded }
            .padding(vertical = Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AutoAwesome, tint = c.textFaint, background = c.raised, size = 30.dp)
            Spacer(Modifier.width(Space.sm))
            Text(
                "Thought${if (item.durationMs != null) " for ${formatDurationMs(item.durationMs)}" else ""}",
                style = ParallaxTheme.type.bodySmall,
                color = c.textFaint,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            Text(
                item.text,
                style = ParallaxTheme.type.bodySmall,
                color = c.textMuted,
                modifier = Modifier.padding(top = Space.sm, start = Space.xs, end = Space.xs),
            )
        }
    }
}

fun toolVisual(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n.startsWith("mcp__") || n.startsWith("mcp") -> Icons.Rounded.Extension
        "task" in n || "subagent" in n -> Icons.Rounded.AccountTree
        n.startsWith("read") || n.startsWith("cat") || n.startsWith("ls") || n.startsWith("list") || n.startsWith("file") -> Icons.Rounded.Description
        n.startsWith("edit") || n.startsWith("write") || n.startsWith("patch") || n.startsWith("apply") -> Icons.Rounded.Edit
        n.startsWith("search") || n.startsWith("grep") || n.startsWith("find") || n.startsWith("glob") || n.startsWith("websearch") -> Icons.Rounded.Search
        n.startsWith("webfetch") || n.startsWith("fetch") || n.startsWith("curl") || "web" in n -> Icons.Rounded.Language
        n.startsWith("shell") || n.startsWith("bash") || n.startsWith("run") || n.startsWith("exec") || n.startsWith("command") -> Icons.Rounded.Terminal
        else -> Icons.Rounded.Bolt
    }
}

/** Friendly title + subtitle for a tool call. */
fun toolTitle(name: String): String {
    val n = name.lowercase()
    return when {
        n.startsWith("mcp__") -> {
            val rest = n.removePrefix("mcp__").split("__").firstOrNull().orEmpty()
            if (rest.isBlank()) "MCP tool" else "${rest.replaceFirstChar { it.titlecase() }} tool"
        }
        n.startsWith("mcp") -> "MCP tool"
        "task" in n || "subagent" in n -> "Subagent"
        n.startsWith("shell") || n.startsWith("bash") || n.startsWith("run") || n.startsWith("exec") || n.startsWith("command") -> "Terminal"
        n.startsWith("read") -> "Read file"
        n.startsWith("edit") || n.startsWith("write") || n.startsWith("patch") || n.startsWith("apply") -> "Edited file"
        n.startsWith("glob") || n.startsWith("grep") || n.startsWith("search") || n.startsWith("find") || n.startsWith("list") -> "Search"
        n.startsWith("webfetch") || n.startsWith("fetch") || n.startsWith("curl") || "web" in n -> "Web"
        else -> name.replaceFirstChar { it.titlecase() }
    }
}

@Composable
private fun ToolMonoBody(output: String) {
    val c = ParallaxTheme.colors
    var showAll by remember(output) { mutableStateOf(false) }
    Column {
        Text(
            output,
            style = ParallaxTheme.type.monoSmall,
            color = c.textMuted,
            maxLines = if (showAll) Int.MAX_VALUE else 12,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(max = 320.dp),
        )
        if (output.lines().size > 12) {
            Text(
                if (showAll) "Show less" else "Show all",
                style = ParallaxTheme.type.label,
                color = c.accentText,
                modifier = Modifier
                    .padding(top = Space.sm)
                    .clickable { showAll = !showAll },
            )
        }
    }
}

/** One tool call rendered as an ActivityCard. */
@Composable
fun ToolActivity(
    tool: TranscriptItem.Tool,
    onOpenChild: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val live = tool.status == ToolStatus.RUNNING || tool.status == ToolStatus.PENDING
    val subtitle = when {
        live -> tool.subject.ifBlank { "Running…" }.let { if (tool.subject.isBlank()) "Running…" else "${tool.subject} · Running…" }
        else -> tool.subject.ifBlank { toolTitle(tool.name) }
    }
    ActivityCard(
        icon = toolVisual(tool.name),
        title = toolTitle(tool.name),
        subtitle = subtitle,
        modifier = modifier,
        expandable = tool.output != null,
        initiallyExpanded = false,
        actionLabel = if (tool.childSessionId != null && onOpenChild != null) "Open" else null,
        onAction = if (tool.childSessionId != null && onOpenChild != null) {
            { onOpenChild(tool.childSessionId) }
        } else null,
        body = if (tool.output != null) {
            { ToolMonoBody(tool.output) }
        } else null,
    )
}

/** Consecutive tool calls in one run collapse into ONE ActivityCard. */
@Composable
fun ToolGroupActivity(
    tools: List<TranscriptItem.Tool>,
    onOpenChild: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = ParallaxTheme.colors
    val live = tools.any { it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING }
    ActivityCard(
        icon = Icons.Rounded.Bolt,
        title = "Worked on ${tools.size} steps",
        subtitle = if (live) "Running…" else tools.lastOrNull()?.subject?.ifBlank { null },
        modifier = modifier,
        expandable = true,
        initiallyExpanded = live,
        body = {
            tools.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val dot = when (t.status) {
                        ToolStatus.RUNNING, ToolStatus.PENDING -> c.running
                        ToolStatus.SUCCESS -> c.done
                        ToolStatus.FAILED -> c.failed
                    }
                    Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(Space.sm))
                    androidx.compose.material3.Icon(
                        toolVisual(t.name),
                        contentDescription = null,
                        tint = c.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        t.subject.ifBlank { toolTitle(t.name) },
                        style = ParallaxTheme.type.bodySmall,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (t.startedAt != null && t.endedAt != null) {
                        Text(
                            formatElapsed(t.endedAt - t.startedAt),
                            style = ParallaxTheme.type.caption,
                            color = c.textFaint,
                        )
                    }
                }
                if (t.output != null) {
                    Text(
                        t.output,
                        style = ParallaxTheme.type.monoSmall,
                        color = c.textFaint,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Space.lg, bottom = Space.sm),
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------- forms (airy)

/** Airy form: SoftCard with ChoicePills for choices, SoftField for text, PrimaryButton Send. */
@Composable
fun FormBlock(
    form: FormRequest,
    busy: Boolean,
    onCancel: (() -> Unit)?,
    onSubmit: (Map<String, Any>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ParallaxTheme.colors
    val answers = remember(form.id) { mutableStateMapOf<String, Any>() }
    var singleChoice by remember(form.id) { mutableStateOf(mapOf<String, String>()) }
    var multiChoice by remember(form.id) { mutableStateOf(mapOf<String, Set<String>>()) }
    val valid = form.fields.all { f ->
        if (!f.required) true else when (f.type) {
            FormField.FieldType.BOOLEAN -> true
            FormField.FieldType.SINGLE_CHOICE -> !singleChoice[f.key].isNullOrEmpty()
            FormField.FieldType.MULTI_CHOICE -> !(multiChoice[f.key].isNullOrEmpty())
            else -> !(answers[f.key] as? String).isNullOrBlank()
        }
    }
    SoftCard(modifier.fillMaxWidth()) {
        Text(form.title ?: "Input needed", style = ParallaxTheme.type.title, color = c.text)
        Spacer(Modifier.height(Space.md))
        form.fields.forEach { field ->
            when (field.type) {
                FormField.FieldType.TEXT, FormField.FieldType.NUMBER -> {
                    var text by remember(form.id, field.key) {
                        mutableStateOf(answers[field.key] as? String ?: field.default.orEmpty())
                    }
                    Text(field.label, style = ParallaxTheme.type.label, color = c.text)
                    Spacer(Modifier.height(Space.xs))
                    SoftField(
                        value = text,
                        onValueChange = { text = it; answers[field.key] = it },
                        hint = field.placeholder ?: "Type here",
                        singleLine = true,
                        keyboardType = if (field.type == FormField.FieldType.NUMBER) KeyboardType.Number else KeyboardType.Text,
                    )
                    Spacer(Modifier.height(Space.md))
                }
                FormField.FieldType.BOOLEAN -> {
                    var checked by remember(form.id, field.key) {
                        mutableStateOf((answers[field.key] as? Boolean) ?: (field.default == "true"))
                    }
                    LaunchedEffect(checked) { answers[field.key] = checked }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(field.label, style = ParallaxTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                        AiryToggleRow(on = checked, busy = busy, onToggle = { checked = it })
                    }
                }
                FormField.FieldType.SINGLE_CHOICE -> {
                    Text(field.label, style = ParallaxTheme.type.label, color = c.text)
                    Spacer(Modifier.height(Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        field.options.forEach { opt ->
                            val sel = singleChoice[field.key] == opt
                            ChoicePill(
                                text = opt,
                                selected = sel,
                                onClick = { singleChoice = singleChoice + (field.key to opt); answers[field.key] = opt },
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                }
                FormField.FieldType.MULTI_CHOICE -> {
                    Text(field.label, style = ParallaxTheme.type.label, color = c.text)
                    Spacer(Modifier.height(Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        field.options.forEach { opt ->
                            val sel = multiChoice[field.key].orEmpty().contains(opt)
                            ChoicePill(
                                text = opt,
                                selected = sel,
                                onClick = {
                                    val cur = multiChoice[field.key].orEmpty()
                                    val next = if (sel) cur - opt else cur + opt
                                    multiChoice = multiChoice + (field.key to next)
                                    answers[field.key] = next.toList()
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (onCancel != null) {
                SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
            }
            PrimaryButton(
                "Send",
                onClick = {
                    val out = mutableMapOf<String, Any>()
                    form.fields.forEach { f ->
                        when (f.type) {
                            FormField.FieldType.SINGLE_CHOICE -> singleChoice[f.key]?.let { out[f.key] = it }
                            FormField.FieldType.MULTI_CHOICE -> multiChoice[f.key]?.let { out[f.key] = it.toList() }
                            else -> answers[f.key]?.let { out[f.key] = it }
                        }
                    }
                    onSubmit(out)
                },
                modifier = Modifier.weight(1f),
                enabled = !busy && valid,
            )
        }
    }
}

// ---------------------------------------------------------------- list rows

/** One session as a Muse-style Ideas row. */
@Composable
fun SessionListRow(
    session: AgentSession,
    modelName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    onLongClick: (() -> Unit)? = null,
) {
    val c = ParallaxTheme.colors
    val preview = when (session.state) {
        RunState.RUNNING -> session.activity ?: "Working…"
        RunState.NEEDS_YOU -> "Waiting for your OK"
        RunState.FAILED -> session.error ?: "Something went wrong"
        else -> "$modelName · ${dirLeaf(session.directory)}"
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 10.dp, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mascot(52.dp, seed = session.id, mood = session.state.toMood())
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.title.ifBlank { "Untitled chat" },
                    style = ParallaxTheme.type.title.copy(fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.sm))
                if (session.state == RunState.RUNNING && session.runStartedAt != null) {
                    ElapsedText(since = session.runStartedAt)
                } else {
                    Text(timeAgo(now, session.updatedAt), style = ParallaxTheme.type.bodySmall, color = c.textFaint)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Checklist, null, tint = c.textFaint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(preview, style = ParallaxTheme.type.body.copy(fontSize = 15.sp), color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (session.unread && session.state != RunState.RUNNING) {
                    Spacer(Modifier.width(Space.sm))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
                }
            }
        }
    }
}

/** Parallel launch as one SoftCard with a thin segmented progress and compact rows. */
@Composable
fun ParallelGroupCard(
    sessions: List<AgentSession>,
    modelNames: Map<String, String>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val c = ParallaxTheme.colors
    val done = sessions.count { it.state == RunState.DONE }
    val title = sessions.first().title.substringBefore(" · ")
    SoftCard(modifier, padding = Space.lg) {
        Text(title, style = ParallaxTheme.type.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            "Parallel · ${sessions.size} models · $done of ${sessions.size} done",
            style = ParallaxTheme.type.bodySmall,
            color = c.textMuted,
        )
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sessions.forEach { s ->
                val fill = when (s.state) {
                    RunState.DONE -> c.done
                    RunState.FAILED -> c.failed
                    RunState.RUNNING, RunState.NEEDS_YOU -> c.accent
                    RunState.IDLE -> c.raised
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (s.state == RunState.IDLE) c.raised else fill),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        sessions.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onOpen(s.id) }
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(s.state.dotColor()))
                Spacer(Modifier.width(Space.sm))
                Text(
                    displayModelName(s.model, modelNames),
                    style = ParallaxTheme.type.bodySmall,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
                Text(
                    s.activity ?: when (s.state) {
                        RunState.DONE -> "Finished"
                        RunState.FAILED -> s.error ?: "Failed"
                        RunState.NEEDS_YOU -> "Needs you"
                        else -> "Queued"
                    },
                    style = ParallaxTheme.type.bodySmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (s.state == RunState.RUNNING && s.runStartedAt != null) {
                    ElapsedText(since = s.runStartedAt)
                } else {
                    Text(timeAgo(now, s.updatedAt), style = ParallaxTheme.type.caption, color = c.textFaint)
                }
            }
        }
    }
}


/** A project's character: the same flat mascot everywhere that project appears. */
@Composable
fun ProjectTile(directory: String?, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Mascot(size, seed = directory ?: "default")
}
