package app.parallax.opencode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.parallax.opencode.core.model.ToolStatus
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.ui.theme.Elevation
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space

/*
 * Chat surfaces in the Grok / Muse style: the agent talks, the machinery stays out of sight.
 * No terminal cards, no command output — work collapses into one quiet line you can open.
 */

/** Header: round back button, a pill with the live avatar + name, and one round action on the right. */
@Composable
fun ChatHeader(
    title: String,
    mood: OrbMood,
    live: Boolean,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    rightIcon: ImageVector,
    rightLabel: String,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    seed: String = "parallax",
) {
    val c = ParallaxTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .height(44.dp)
                    .softShadow(Elevation.card, CircleShape)
                    .clip(CircleShape)
                    .background(c.surface)
                    .clickable(onClick = onTitleClick)
                    .padding(start = 6.dp, end = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mascot(
                    32.dp, seed = seed, mood = if (live) OrbMood.WORKING else mood,
                    badge = if (live) ({ MascotBadge(c.done) }) else null,
                )
                Spacer(Modifier.width(Space.sm))
                Text(title, style = ParallaxTheme.type.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Space.sm))
        CircleIconButton(rightIcon, rightLabel, onClick = onRight)
    }
}

/** Centered grey timestamp ("Today 10:54 AM"). */
@Composable
fun DayStamp(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = ParallaxTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        color = ParallaxTheme.colors.textFaint,
        modifier = modifier.fillMaxWidth().padding(vertical = Space.md),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Your message: solid ink bubble, right-aligned. */
@Composable
fun UserBubble(text: String, queued: Boolean = false, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .padding(start = 56.dp)
                .clip(RoundedCornerShape(Radius.bubble))
                .background(if (queued) c.bubbleUser.copy(alpha = 0.6f) else c.bubbleUser)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) { Text(text, style = ParallaxTheme.type.body.copy(fontSize = ParallaxTheme.type.body.fontSize * 1.03f), color = c.onBubbleUser) }
    }
}

/** The agent's words: soft grey bubble, left-aligned, markdown inside. */
@Composable
fun AgentBubble(text: String, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .padding(end = 40.dp)
                .clip(RoundedCornerShape(Radius.bubble))
                .background(c.bubbleAgent)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) { MarkdownText(text) }
    }
}

/** Grok-style "thinking" avatar: the orb, swirling, on its own at the left. */
@Composable
fun AgentThinking(modifier: Modifier = Modifier) {
    Box(modifier.padding(start = 2.dp, top = 2.dp)) { Orb(40.dp, OrbMood.WORKING) }
}

/** A friendly, non-technical description of one step. */
fun friendlyStep(tool: TranscriptItem.Tool): String {
    val subject = tool.subject.trim()
    val file = subject.substringAfterLast('/').take(48)
    val n = tool.name.lowercase()
    return when {
        n.contains("task") || n.contains("agent") -> "Asked a helper" + if (subject.isNotEmpty()) ": ${subject.take(40)}" else ""
        n == "read" || n.endsWith("read") -> if (file.isNotEmpty()) "Read $file" else "Read a file"
        n in setOf("edit", "write", "patch", "multiedit", "apply_patch") || n.endsWith("edit") || n.endsWith("write") -> if (file.isNotEmpty()) "Updated $file" else "Updated a file"
        n in setOf("grep", "glob", "list", "ls", "find", "search") -> "Looked through the code"
        n.contains("websearch") || n.contains("search_web") -> "Searched the web"
        n.contains("fetch") || n.contains("web") -> "Read a web page"
        n.contains("todo") -> "Updated the plan"
        n in setOf("shell", "bash", "run", "exec") || n.contains("shell") || n.contains("bash") -> "Ran a quick check"
        "__" in n || n.startsWith("mcp") -> "Used " + n.substringBefore("__").removePrefix("mcp").trim('_').replace('_', ' ').ifBlank { "a connector" }
        else -> tool.verb.ifBlank { "Worked on something" }
    }
}

/**
 * The only trace of tool use: one quiet line. While working: "Working · Read calc.py".
 * After: "Took 4 steps" — tap to see the steps as plain sentences (never commands or output).
 */
@Composable
fun StepsLine(
    tools: List<TranscriptItem.Tool>,
    live: Boolean,
    onOpenChild: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ParallaxTheme.colors
    var open by remember { mutableStateOf(false) }
    val latest = tools.lastOrNull()?.let { friendlyStep(it) }
    val failed = tools.count { it.status == ToolStatus.FAILED }
    val label = when {
        live -> "Working" + (latest?.let { " · $it" } ?: "…")
        tools.size == 1 -> latest ?: "1 step"
        else -> "Took ${tools.size} steps"
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.clip(CircleShape).clickable(enabled = tools.isNotEmpty()) { open = !open }.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                Orb(14.dp, OrbMood.WORKING)
            } else {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (failed > 0) c.needsYou else c.textFaint.copy(alpha = 0.6f)))
            }
            Spacer(Modifier.width(Space.sm))
            Text(label, style = ParallaxTheme.type.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (tools.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textFaint, modifier = Modifier.size(16.dp))
            }
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 26.dp, top = 2.dp, bottom = 4.dp)) {
                tools.forEach { t ->
                    val child = t.childSessionId
                    Row(
                        Modifier.clip(RoundedCornerShape(Radius.sm))
                            .then(if (child != null) Modifier.clickable { onOpenChild(child) } else Modifier)
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            friendlyStep(t),
                            style = ParallaxTheme.type.bodySmall,
                            color = if (t.status == ToolStatus.FAILED) c.needsYou else c.textMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (child != null) Icon(Icons.Rounded.ChevronRight, null, tint = c.textFaint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Grok-style composer: a separate round "+" button, then a white pill field with the action inside on the right.
 * The action is send (ink) when there's text, stop while the agent works, otherwise a muted send.
 */
@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onSend: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    onStop: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
    queueFollowUp: Boolean = false,
    plusIcon: ImageVector = Icons.Rounded.Add,
    enabled: Boolean = true,
) {
    val c = ParallaxTheme.colors
    val haptic = LocalHapticFeedback.current
    val canSend = value.isNotBlank() && enabled
    val showStop = running && !canSend && onStop != null
    val showMic = !canSend && !showStop && onVoice != null
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(
            Modifier.size(48.dp).softShadow(Elevation.card, CircleShape).clip(CircleShape).background(c.surface).clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) { Icon(plusIcon, contentDescription = "Options", tint = c.text, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(Space.sm))
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .softShadow(Elevation.card, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .padding(start = Space.lg, end = 5.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = ParallaxTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(hint, style = ParallaxTheme.type.body, color = c.textFaint, maxLines = 1)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(Space.sm))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (canSend || showStop || showMic) c.text else c.raised)
                    .clickable(enabled = canSend || showStop || showMic) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        when {
                            showStop -> onStop?.invoke()
                            showMic -> onVoice?.invoke()
                            else -> onSend()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when { showStop -> Icons.Rounded.Stop; showMic -> Icons.Rounded.Mic; queueFollowUp -> Icons.Rounded.PlaylistAdd; else -> Icons.Rounded.ArrowUpward },
                    contentDescription = when { showStop -> "Stop"; showMic -> "Talk"; queueFollowUp -> "Queue follow-up"; else -> "Send" },
                    tint = if (canSend || showStop || showMic) c.bg else c.textFaint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/** "Today 10:54 AM" / "Yesterday 9:02 PM" / "Mon 12 Nov, 3:15 PM". */
fun chatStamp(ts: Long, now: Long = System.currentTimeMillis()): String {
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ts))
    val day = dayLabel(now, ts)
    return "$day $time"
}

