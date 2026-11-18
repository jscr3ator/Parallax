package app.parallax.opencode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.ConnectionState
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.ui.theme.Motion
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space
import java.util.Calendar
import kotlinx.coroutines.delay

/** Status color for a run state. Shown as small dots or icon tints only. */
@Composable
fun RunState.dotColor(): Color = when (this) {
    RunState.RUNNING -> ParallaxTheme.colors.running
    RunState.NEEDS_YOU -> ParallaxTheme.colors.needsYou
    RunState.FAILED -> ParallaxTheme.colors.failed
    RunState.DONE -> ParallaxTheme.colors.done
    RunState.IDLE -> ParallaxTheme.colors.textFaint
}

fun formatElapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 3600) {
        "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    } else {
        "${s / 3600}h${((s % 3600) / 60).toString().padStart(2, '0')}m"
    }
}

fun formatDurationMs(ms: Long?): String {
    if (ms == null) return ""
    val s = ms / 1000.0
    return if (s < 60) "${"%.1f".format(s)}s" else formatElapsed(ms)
}

fun formatCost(cost: Double): String = if (cost <= 0) "$0.00" else "$${"%.2f".format(cost)}"

fun dirLeaf(dir: String?): String {
    if (dir.isNullOrBlank()) return "Default folder"
    val trimmed = dir.trimEnd('/')
    return trimmed.substringAfterLast('/').ifBlank { trimmed }
}

fun timeAgo(now: Long, ts: Long): String {
    val d = (now - ts).coerceAtLeast(0)
    val m = d / 60_000
    if (m < 1) return "now"
    if (m < 60) return "${m}m"
    val h = m / 60
    if (h < 24) return "${h}h"
    val days = h / 24
    if (days < 7) return "${days}d"
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
}

fun dayLabel(now: Long, ts: Long): String {
    val dayStart = { t: Long ->
        Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val diff = (dayStart(now) - dayStart(ts)) / (24 * 60 * 60_000L)
    return when (diff) {
        0L -> "Today"
        1L -> "Yesterday"
        in 2..6 -> {
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            val names = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            "${names[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)}"
        }
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
        }
    }
}

/** Turn "deepseek-v4.1-flash" into "Deepseek V4.1 Flash" when no display name is known. */
fun prettyModelId(modelId: String?): String {
    if (modelId.isNullOrBlank()) return "No model"
    return modelId.split('-', '_', '/', ' ').joinToString(" ") { part ->
        if (part.any { it.isDigit() }) part.uppercase().replaceFirstChar { it.titlecase() }
        else part.replaceFirstChar { it.titlecase() }
    }
}

/** A group of sessions launched together: same title prefix, created within 10 s. */
fun parallelGroups(sessions: List<AgentSession>): List<List<AgentSession>> {
    val byPrefix = sessions
        .filter { " · " in it.title }
        .groupBy { it.title.substringBefore(" · ") }
    val groups = mutableListOf<List<AgentSession>>()
    byPrefix.values.forEach { list ->
        if (list.size < 2) return@forEach
        val sorted = list.sortedBy { it.createdAt }
        var chunk = mutableListOf(sorted.first())
        for (s in sorted.drop(1)) {
            if (s.createdAt - chunk.last().createdAt <= 10_000) {
                chunk.add(s)
            } else {
                if (chunk.size >= 2) groups.add(chunk.toList())
                chunk = mutableListOf(s)
            }
        }
        if (chunk.size >= 2) groups.add(chunk.toList())
    }
    return groups
}

/** Ticking `1:24`-style timer for running rows. */
@Composable
fun ElapsedText(since: Long, prefix: String = "", modifier: Modifier = Modifier) {
    var now by remember(since) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Text(
        text = prefix + formatElapsed(now - since),
        style = ParallaxTheme.type.caption,
        color = ParallaxTheme.colors.textMuted,
        modifier = modifier,
    )
}

/** Soft amber pill shown on rows and headers that need attention. */
@Composable
fun NeedsYouPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(ParallaxTheme.colors.needsYouWash)
            .padding(horizontal = Space.md, vertical = Space.xs),
    ) {
        Text("Needs you", style = ParallaxTheme.type.caption, color = ParallaxTheme.colors.needsYou)
    }
}

/** Floating soft error card; auto-dismisses after 4 s. */
@Composable
fun ErrorPill(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        delay(4000)
        onDismiss()
    }
    Row(
        modifier = modifier
            .softShadow(10.dp, RoundedCornerShape(Radius.lg))
            .clip(RoundedCornerShape(Radius.lg))
            .background(ParallaxTheme.colors.surface)
            .clickable { onDismiss() }
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(ParallaxTheme.colors.failed))
        Spacer(Modifier.width(Space.sm))
        Text(message, style = ParallaxTheme.type.label, color = ParallaxTheme.colors.text, maxLines = 2)
    }
}

/** Soft banner shown when the connection is down. Hidden when connected. */
@Composable
fun ConnectionBanner(state: ConnectionState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        is ConnectionState.Offline -> BannerRow(
            icon = Icons.Rounded.CloudOff,
            text = "Offline — ${state.reason}",
            action = "Retry",
            onRetry = onRetry,
            modifier = modifier,
        )
        is ConnectionState.AuthFailed -> BannerRow(
            icon = Icons.Rounded.ErrorOutline,
            text = state.message,
            action = "Retry",
            onRetry = onRetry,
            modifier = modifier,
            error = true,
        )
        is ConnectionState.Connecting -> BannerRow(
            icon = Icons.Rounded.Refresh,
            text = "Connecting…",
            action = null,
            onRetry = onRetry,
            modifier = modifier,
        )
        else -> Unit
    }
}

@Composable
private fun BannerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    action: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (error) ParallaxTheme.colors.failedWash else ParallaxTheme.colors.raised)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (error) ParallaxTheme.colors.failed else ParallaxTheme.colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text,
            style = ParallaxTheme.type.bodySmall,
            color = ParallaxTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        if (action != null) {
            TextButton(onClick = onRetry) {
                Text(action, style = ParallaxTheme.type.label, color = ParallaxTheme.colors.accentText)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = ParallaxTheme.type.caption, color = ParallaxTheme.colors.textFaint, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = ParallaxTheme.type.caption, color = ParallaxTheme.colors.textFaint)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(title, style = ParallaxTheme.type.headline, color = ParallaxTheme.colors.text)
        Text(body, style = ParallaxTheme.type.bodySmall, color = ParallaxTheme.colors.textMuted)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.sm))
            TextButton(onClick = onAction) {
                Text(actionLabel, style = ParallaxTheme.type.label, color = ParallaxTheme.colors.accentText)
            }
        }
    }
}

/** Shimmering placeholder rows in `raised`. */
@Composable
fun LoadingSkeleton(rows: Int = 4, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.slow, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        repeat(rows) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(ParallaxTheme.colors.raised.copy(alpha = t * 0.6f)),
            )
        }
    }
}

/** Shared pulsing halo used behind live dots. Returns 0..1 progress, or null when not running. */
@Composable
fun rememberPulse(): Float? {
    val running = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.pulse, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    return running.value
}

fun Modifier.pulseHalo(progress: Float, color: Color): Modifier = drawBehind {
    val base = size.minDimension / 2f
    val r = base * (1f + 1.2f * progress)
    drawCircle(color.copy(alpha = 0.5f * (1f - progress)), r)
}

@Composable
fun DismissIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            Icons.Rounded.Cancel,
            contentDescription = "Dismiss",
            tint = ParallaxTheme.colors.textMuted,
        )
    }
}
