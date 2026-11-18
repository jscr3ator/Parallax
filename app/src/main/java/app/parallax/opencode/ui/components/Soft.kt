package app.parallax.opencode.ui.components

import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.parallax.opencode.ui.theme.Elevation
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space

/*
 * The "airy" component kit. Screens are composed from these; keep visual decisions here.
 * Rules: no borders on cards (soft shadow instead), pill/circle shapes for controls,
 * one gradient primary action per view, status shown as small dots or icon tints.
 */

// ---------------------------------------------------------------- surfaces

/** Soft, diffuse shadow tinted with the theme's shadow color (no hard outlines). */
@Composable
fun Modifier.softShadow(elevation: Dp = Elevation.card, shape: Shape = RoundedCornerShape(Radius.lg)): Modifier {
    val c = ParallaxTheme.colors
    return if (c.isDark) this else this.shadow(elevation, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
}

/** White card with a soft shadow. The default container for anything grouped. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = Space.lg,
    shape: Shape = RoundedCornerShape(Radius.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ParallaxTheme.colors
    Column(
        modifier
            .softShadow(Elevation.card, shape)
            .clip(shape)
            .background(c.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Rounded square tile holding an icon (Muse-style leading visual for rows and cards). */
@Composable
fun IconTile(icon: ImageVector, tint: Color = ParallaxTheme.colors.accentText, background: Color = ParallaxTheme.colors.accentWash, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

// ---------------------------------------------------------------- buttons

/** 44dp round icon button on a white surface with a soft shadow (top-bar menu/back). */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = ParallaxTheme.colors.text) {
    val c = ParallaxTheme.colors
    Box(
        modifier
            .size(44.dp)
            .softShadow(Elevation.card, CircleShape)
            .clip(CircleShape)
            .background(c.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Several icon actions sharing one white pill (top-right of Muse's header). */
@Composable
fun IconPillGroup(vararg actions: Triple<ImageVector, String, () -> Unit>, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    Row(
        modifier
            .height(44.dp)
            .softShadow(Elevation.card, CircleShape)
            .clip(CircleShape)
            .background(c.surface)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (icon, label, onClick) ->
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = label, tint = c.text, modifier = Modifier.size(21.dp)) }
        }
    }
}

/** The one primary action: blue→indigo gradient pill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 48.dp) {
    val c = ParallaxTheme.colors
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (enabled) c.accentBrush else SolidColor(c.raised))
            .clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .padding(horizontal = Space.xl),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ParallaxTheme.type.label, color = if (enabled) c.onAccent else c.textFaint, maxLines = 1) }
}

/** Neutral pill (Deny, Open browser, Cancel). */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 48.dp) {
    val c = ParallaxTheme.colors
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(c.raised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.xl),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ParallaxTheme.type.label, color = if (enabled) c.text else c.textFaint, maxLines = 1) }
}

/** Small selectable pill (directory, agent, model choices). */
@Composable
fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val c = ParallaxTheme.colors
    val bg by animateColorAsState(if (selected) c.accentWash else c.surface, label = "pill")
    Row(
        modifier
            .height(36.dp)
            .then(if (selected) Modifier else Modifier.softShadow(6.dp, CircleShape))
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(text, style = ParallaxTheme.type.label.copy(fontSize = ParallaxTheme.type.bodySmall.fontSize), color = if (selected) c.accentText else c.text, maxLines = 1)
    }
}

// ---------------------------------------------------------------- the orb

enum class OrbMood { IDLE, WORKING, NEEDS_YOU, DONE, FAILED }

/**
 * Parallax's character: the round ink mascot.
 */
@Composable
fun Orb(size: Dp, mood: OrbMood = OrbMood.IDLE, modifier: Modifier = Modifier) {
    Mascot(size, seed = "parallax", mood = mood, modifier = modifier, shape = MascotShape.CIRCLE,
        color = if (ParallaxTheme.colors.isDark) Color(0xFFF2F3F5) else MascotPalette.Ink)
}

/** Small white pill with a title and an optional live subtitle; overlaps the orb's bottom edge. */
@Composable
fun StatusPill(title: String, subtitle: String? = null, modifier: Modifier = Modifier, dot: Color? = null) {
    val c = ParallaxTheme.colors
    Column(
        modifier
            .softShadow(10.dp, CircleShape)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(horizontal = Space.md, vertical = 5.dp)
            .widthIn(max = 240.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) { Box(Modifier.size(6.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(5.dp)) }
            Text(title, style = ParallaxTheme.type.label.copy(fontSize = ParallaxTheme.type.bodySmall.fontSize), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (subtitle != null) {
            Text(subtitle, style = ParallaxTheme.type.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Orb + status pill stacked like Muse's header avatar. */
@Composable
fun OrbHeader(title: String, subtitle: String?, mood: OrbMood, modifier: Modifier = Modifier, orbSize: Dp = 64.dp) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Orb(orbSize, mood)
        StatusPill(title, subtitle, Modifier.padding(top = orbSize * 0.78f))
    }
}

// ---------------------------------------------------------------- chat

/** iMessage-like bubble. Agent: grey, left. User: light blue, right. */
@Composable
fun ChatBubble(fromUser: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = ParallaxTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(Radius.bubble))
                .background(if (fromUser) c.bubbleUser else c.bubbleAgent)
                .padding(horizontal = Space.lg, vertical = 11.dp),
            content = content,
        )
    }
}

@Composable
fun TextBubble(text: String, fromUser: Boolean, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    ChatBubble(fromUser, modifier) {
        Text(text, style = ParallaxTheme.type.body, color = if (fromUser) c.onBubbleUser else c.text)
    }
}

/** Three pulsing dots in a small grey bubble: the agent is thinking. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    val t = rememberInfiniteTransition(label = "dots")
    Row(
        modifier.clip(RoundedCornerShape(Radius.bubble)).background(c.bubbleAgent).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { i ->
            val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520, delayMillis = i * 140, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.offset(y = (-3 * y).dp).size(7.dp).clip(CircleShape).background(c.textFaint.copy(alpha = 0.55f + 0.45f * y)))
        }
    }
}

/**
 * Muse's tool card: icon tile, title ("Terminal"), live subtitle ("Running npm test…"), optional
 * expandable body (output) and an optional bottom action pill ("Open session").
 */
@Composable
fun ActivityCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    tint: Color = ParallaxTheme.colors.accentText,
    tileBg: Color = ParallaxTheme.colors.accentWash,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    expandable: Boolean = false,
    initiallyExpanded: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = ParallaxTheme.colors
    var expanded by remember { mutableStateOf(initiallyExpanded || !expandable) }
    // Follow the caller when its default flips (e.g. a live group collapses once the run finishes).
    androidx.compose.runtime.LaunchedEffect(initiallyExpanded) { expanded = initiallyExpanded || !expandable }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.bubbleAgent)
            .padding(Space.md),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
                .then(if (expandable && body != null) Modifier.clickable { expanded = !expanded } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, tint = tint, background = c.surface, size = 38.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = ParallaxTheme.type.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = ParallaxTheme.type.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke(this)
            if (expandable && body != null) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textFaint, modifier = Modifier.size(20.dp))
            }
        }
        if (body != null) {
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(
                    Modifier.padding(top = Space.md).fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface).padding(Space.md),
                    content = body,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.md))
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(c.surface).clickable(onClick = onAction),
                contentAlignment = Alignment.Center,
            ) { Text(actionLabel, style = ParallaxTheme.type.label, color = c.text) }
        }
    }
}

/** Muse's approval card: white card, icon + title line, explanation, details slot, Deny / Allow. */
@Composable
fun ApprovalCard(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    denyLabel: String = "Deny",
    allowLabel: String = "Allow",
    onDeny: () -> Unit,
    onAllow: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = ParallaxTheme.colors
    SoftCard(modifier.fillMaxWidth(), padding = Space.lg) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Space.md))
            Text(title, style = ParallaxTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
        }
        if (message != null) {
            Spacer(Modifier.height(Space.sm))
            Text(message, style = ParallaxTheme.type.bodySmall, color = c.textMuted)
        }
        if (details != null) {
            Spacer(Modifier.height(Space.md))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.sunken).padding(Space.md), content = details)
        }
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SecondaryButton(denyLabel, onDeny, Modifier.weight(1f))
            PrimaryButton(allowLabel, onAllow, Modifier.weight(1f))
        }
        if (secondaryLabel != null && onSecondary != null) {
            Text(
                secondaryLabel,
                style = ParallaxTheme.type.label,
                color = c.accentText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Space.md).clickable(onClick = onSecondary),
            )
        }
    }
}

// ---------------------------------------------------------------- navigation & input

/** Bottom space a tab screen must leave free for the floating IconTabBar (60dp bar + 16dp margin + gap). */
val TabBarClearance = 92.dp

data class TabItem(val icon: ImageVector, val label: String, val badge: Int = 0)

/** Floating white pill with icon-only tabs; the selected tab sits in a soft grey circle. */
@Composable
fun IconTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    val tabW = 60f
    val gap = 2f
    val pad = 6f
    fun leftOf(i: Int) = pad + i * (tabW + gap)

    // Liquid selector: two edges on separate springs. The edge moving toward the target leads (stiffer),
    // the trailing edge follows (softer), so the pill stretches a little mid-travel and settles back.
    val left = remember { androidx.compose.animation.core.Animatable(leftOf(selected)) }
    val right = remember { androidx.compose.animation.core.Animatable(leftOf(selected) + tabW) }
    LaunchedEffect(selected) {
        val targetL = leftOf(selected)
        val targetR = targetL + tabW
        val movingRight = targetL > left.value
        val lead = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.78f, stiffness = 520f)
        val trail = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.86f, stiffness = 210f)
        launch { left.animateTo(targetL, if (movingRight) trail else lead) }
        launch { right.animateTo(targetR, if (movingRight) lead else trail) }
    }
    val stretch = ((right.value - left.value) - tabW).coerceAtLeast(0f)
    val squash = (stretch / 60f).coerceIn(0f, 1f) * 6f

    Box(
        modifier
            .height(60.dp)
            .softShadow(Elevation.floating, CircleShape)
            .clip(CircleShape)
            .background(c.surface),
    ) {
        Box(
            Modifier
                .padding(top = (6f + squash / 2f).dp)
                .offset(x = left.value.dp)
                .size(width = (right.value - left.value).dp, height = (48f - squash).dp)
                .clip(CircleShape)
                .background(c.raised),
        )
        Row(
            Modifier.height(60.dp).padding(horizontal = pad.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap.dp),
        ) {
            items.forEachIndexed { i, item ->
                val isSel = i == selected
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pop by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = when { pressed -> 0.9f; isSel -> 1.08f; else -> 1f },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f),
                    label = "pop",
                )
                val tint by animateColorAsState(if (isSel) c.text else c.textMuted, label = "tint")
                Box(
                    Modifier.size(width = tabW.dp, height = 48.dp).clip(CircleShape)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    BadgedBox(badge = { if (item.badge > 0) Badge(containerColor = c.needsYou, contentColor = Color.White) { Text("${item.badge}") } }) {
                        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(24.dp).scale(pop))
                    }
                }
            }
        }
    }
}

/**
 * Muse-style composer: white pill, "+" on the left, send/stop on the right.
 * [above] renders inside the same card above the field (model + parallel chips).
 */
@Composable
fun SoftComposer(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    onStop: (() -> Unit)? = null,
    onPlus: (() -> Unit)? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    above: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = ParallaxTheme.colors
    val haptic = LocalHapticFeedback.current
    val canSend = value.isNotBlank() && enabled
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(Elevation.floating, RoundedCornerShape(Radius.lg))
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.surface)
            .padding(horizontal = Space.sm, vertical = Space.sm),
    ) {
        if (above != null) {
            Column(Modifier.padding(start = Space.sm, end = Space.sm, top = Space.xs, bottom = Space.xs), content = above)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (onPlus != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "More", tint = c.textMuted, modifier = Modifier.size(24.dp))
                }
            } else Spacer(Modifier.width(Space.sm))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = ParallaxTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = minLines,
                maxLines = 8,
                modifier = Modifier.weight(1f).heightIn(min = 40.dp).padding(horizontal = Space.xs, vertical = 9.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(hint, style = ParallaxTheme.type.body, color = c.textFaint)
                        inner()
                    }
                },
            )
            val showStop = running && value.isBlank() && onStop != null
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            showStop -> SolidColor(c.text)
                            canSend -> c.accentBrush
                            else -> SolidColor(c.raised)
                        },
                    )
                    .clickable(enabled = showStop || canSend) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (showStop) onStop?.invoke() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (showStop) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                    contentDescription = if (showStop) "Stop" else "Send",
                    tint = when { showStop -> c.surface; canSend -> c.onAccent; else -> c.textFaint },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Two-or-more option segmented control on a grey track (Available / Connected). */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    Row(modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(c.raised).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(34.dp)
                    .then(if (sel) Modifier.softShadow(4.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (sel) c.surface else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = ParallaxTheme.type.bodySmall, color = if (sel) c.text else c.textMuted) }
        }
    }
}

/** Rounded search field on a white surface. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    Row(
        modifier.fillMaxWidth().height(44.dp).softShadow(6.dp, CircleShape).clip(CircleShape).background(c.surface).padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textFaint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = ParallaxTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = ParallaxTheme.type.body, color = c.textFaint); inner() } },
        )
    }
}

/** Muse "Ideas" row: leading visual, bold title, grey body (2 lines), optional trailing. */
@Composable
fun ListRow(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    bodyLines: Int = 2,
) {
    val c = ParallaxTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Space.md, horizontal = Space.xs),
        verticalAlignment = Alignment.Top,
    ) {
        if (leading != null) { Box(Modifier.padding(top = 2.dp)) { leading() }; Spacer(Modifier.width(Space.md)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = ParallaxTheme.type.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(body, style = ParallaxTheme.type.bodySmall, color = c.textMuted, maxLines = bodyLines, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) { Spacer(Modifier.width(Space.sm)); Box(Modifier.padding(top = 2.dp)) { trailing() } }
    }
}

/** Large section title like Muse's "Ideas" / "Connectors". */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ParallaxTheme.type.display, color = ParallaxTheme.colors.text, modifier = modifier)
}

/** Small grey section label ("Featured", "Productivity"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val c = ParallaxTheme.colors
    Row(modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = ParallaxTheme.type.label, color = c.text, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = ParallaxTheme.type.bodySmall, color = c.textMuted, modifier = if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier)
        }
    }
}
