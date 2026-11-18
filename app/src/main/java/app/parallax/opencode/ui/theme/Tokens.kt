package app.parallax.opencode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Parallax tokens — "airy" direction (light-first, soft, rounded; inspired by Meta's Muse app).
 * No hard borders: separation comes from soft shadows, tinted fills and whitespace.
 * Every color in the UI comes from here. See docs/DESIGN.md.
 */
@Immutable
data class ParallaxColors(
    val isDark: Boolean,
    /** Page background (very light cool grey / deep ink). */
    val bg: Color,
    /** Cards, sheets, the tab bar, icon buttons. Sits on bg with a soft shadow. */
    val surface: Color,
    /** Filled controls on a surface: secondary buttons, chips, assistant bubbles, segmented track. */
    val raised: Color,
    /** Wells for code and tool output. */
    val sunken: Color,
    /** Use sparingly: only inside cards to separate rows. */
    val hairline: Color,
    val hairlineStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    /** Primary actions (Allow, Send, Start). Solid fallback of [accentBrush]. */
    val accent: Color,
    val onAccent: Color,
    /** Accent as text/icon color (links, "Connect", selected states). */
    val accentText: Color,
    /** Light accent fill: selected chips, user bubble. */
    val accentWash: Color,
    /** User message bubble + its text. */
    val bubbleUser: Color,
    val onBubbleUser: Color,
    /** Assistant message bubble. */
    val bubbleAgent: Color,
    /** Soft shadow tint for cards / floating controls. */
    val shadow: Color,
    // Status: shown as small dots / icon tints only, never big fills.
    val running: Color,
    val needsYou: Color,
    val needsYouWash: Color,
    val failed: Color,
    val failedWash: Color,
    val done: Color,
    val info: Color,
    val reasoning: Color,
    val diffAdd: Color,
    val diffRemove: Color,
    val scrim: Color,
) {
    /** The blue → indigo gradient used on the primary button and the orb. */
    val accentBrush: Brush get() = Brush.horizontalGradient(listOf(accentStart, accentEnd))
    val accentStart: Color get() = if (isDark) Color(0xFF3B82F6) else Color(0xFF0A66E4)
    val accentEnd: Color get() = if (isDark) Color(0xFF6366F1) else Color(0xFF4F55F1)
}

val LightColors = ParallaxColors(
    isDark = false,
    bg = Color(0xFFFAFAFB),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFEEF0F3),
    sunken = Color(0xFFF2F3F6),
    hairline = Color(0xFFE9EBEF),
    hairlineStrong = Color(0xFFDADDE3),
    text = Color(0xFF111318),
    textMuted = Color(0xFF5B606B),
    textFaint = Color(0xFF9A9FA9),
    accent = Color(0xFF1A62EA),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF1A62EA),
    accentWash = Color(0xFFE3EEFF),
    bubbleUser = Color(0xFF0F1115),
    onBubbleUser = Color(0xFFFFFFFF),
    bubbleAgent = Color(0xFFF0F1F3),
    shadow = Color(0x1A1B2540),
    running = Color(0xFF1A62EA),
    needsYou = Color(0xFFE08A00),
    needsYouWash = Color(0xFFFFF3DC),
    failed = Color(0xFFE5484D),
    failedWash = Color(0xFFFFE9E9),
    done = Color(0xFF16A34A),
    info = Color(0xFF1A62EA),
    reasoning = Color(0xFF7C5CDB),
    diffAdd = Color(0xFF16A34A),
    diffRemove = Color(0xFFE5484D),
    scrim = Color(0x52101420),
)

val DarkColors = ParallaxColors(
    isDark = true,
    bg = Color(0xFF0D0E11),
    surface = Color(0xFF17181C),
    raised = Color(0xFF212328),
    sunken = Color(0xFF111215),
    hairline = Color(0xFF26282E),
    hairlineStrong = Color(0xFF33363D),
    text = Color(0xFFF2F3F5),
    textMuted = Color(0xFFA1A5AE),
    textFaint = Color(0xFF6B6F78),
    accent = Color(0xFF3B82F6),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF6EA8FF),
    accentWash = Color(0xFF1A2A45),
    bubbleUser = Color(0xFFF2F3F5),
    onBubbleUser = Color(0xFF0F1115),
    bubbleAgent = Color(0xFF1E2025),
    shadow = Color(0x66000000),
    running = Color(0xFF6EA8FF),
    needsYou = Color(0xFFF5A524),
    needsYouWash = Color(0xFF3A2A10),
    failed = Color(0xFFFF6369),
    failedWash = Color(0xFF3A1719),
    done = Color(0xFF3DD68C),
    info = Color(0xFF6EA8FF),
    reasoning = Color(0xFFA78BFA),
    diffAdd = Color(0xFF3DD68C),
    diffRemove = Color(0xFFFF6369),
    scrim = Color(0x99000000),
)

/** Spacing scale (4dp grid). */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Side gutter for every screen. */
    val gutter = 16.dp
}

/** Corner radii. Everything is soft. */
object Radius {
    val sm = 10.dp
    val md = 14.dp
    /** Chat bubbles. */
    val bubble = 22.dp
    /** Cards. */
    val lg = 24.dp
    val xl = 28.dp
    val sheet = 32.dp
}

/** Elevations used with Modifier.softShadow (see ui/components/Soft.kt). */
object Elevation {
    val card = 14.dp
    val floating = 24.dp
}

object Motion {
    const val fast = 140
    const val base = 240
    const val slow = 420
    const val pulse = 1600
}

val LocalParallaxColors = staticCompositionLocalOf { LightColors }
