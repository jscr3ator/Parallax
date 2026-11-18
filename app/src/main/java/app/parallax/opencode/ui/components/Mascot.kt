package app.parallax.opencode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.parallax.opencode.ui.theme.ParallaxTheme
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small ink characters with an oval eye and a curved, expressive eyelid.
 * Every session/project gets a stable character from its seed so lists scan at a glance.
 */

enum class MascotShape { CIRCLE, BLOB, SQUIRCLE, HEXAGON, PEBBLE }

object MascotPalette {
    val Ink = Color(0xFF111111)
    val Red = Color(0xFFE87068)
    val Green = Color(0xFF99C69B)
    val Brown = Color(0xFFC39879)
    val Blue = Color(0xFF687DCA)
    val Purple = Color(0xFFAF97DC)
    val Orange = Color(0xFFF4A964)
    val Teal = Color(0xFF80C8BE)
    val all = listOf(Red, Green, Brown, Blue, Purple, Teal, Orange)
}

/** FNV-1a 64-bit, folded to a positive Int: spreads similar ids (ses_…) across shapes and colors. */
private fun seedHash(seed: String): Int {
    var h = -0x340d631b7bdddcdbL
    for (ch in seed.lowercase()) { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
    return ((h ushr 33) xor h).toInt() and 0x7fffffff
}

@Composable
fun Mascot(
    size: Dp,
    seed: String,
    mood: OrbMood = OrbMood.IDLE,
    modifier: Modifier = Modifier,
    shape: MascotShape? = null,
    color: Color? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    val h = seedHash(seed)
    val s = shape ?: listOf(MascotShape.BLOB, MascotShape.SQUIRCLE, MascotShape.HEXAGON, MascotShape.PEBBLE, MascotShape.CIRCLE)[h % 5]
    val base = color ?: MascotPalette.all[(h / 7) % MascotPalette.all.size]
    val body by animateColorAsState(
        when (mood) {
            OrbMood.NEEDS_YOU -> MascotPalette.Orange
            OrbMood.FAILED -> MascotPalette.Red
            else -> base
        },
        tween(400), label = "body",
    )

    val t = rememberInfiniteTransition(label = "mascot")
    // Blink: eyes shut for a beat every ~5 s (offset per seed so a list doesn't blink in unison).
    val blink by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 5200 + (h % 1400)
            1f at 0
            1f at 4600
            0.12f at 4680
            1f at 4800
        }),
        label = "blink",
    )
    val working = mood == OrbMood.WORKING
    val look by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "look")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")

    Box(modifier.size(size)) {
        Canvas(Modifier.size(size).offset(y = if (working) (size.value * 0.02f * bob).dp else 0.dp)) {
            drawBody(s, body)
            drawFace(
                ink = if (body.luminance() > 0.33f) MascotPalette.Ink else Color(0xFFFFF8E9),
                mood = mood,
                lookX = if (working) look * 0.06f else 0f,
                blink = if (working) 1f else blink,
            )
        }
        val b = badge ?: when (mood) {
            OrbMood.NEEDS_YOU -> ({ MascotBadge(MascotPalette.Orange) { Icon(Icons.Rounded.PriorityHigh, null, tint = Color.White, modifier = Modifier.padding(2.dp)) } })
            else -> null
        }
        if (b != null) {
            Box(Modifier.align(Alignment.BottomEnd).size(size * 0.32f).offset(x = size * 0.04f, y = size * 0.02f)) { b() }
        }
    }
}

/** Badge disc with a white ring (online dot, star, "!" ...). Fills the slot Mascot gives it. */
@Composable
fun MascotBadge(color: Color, content: @Composable () -> Unit = {}) {
    val ring = ParallaxTheme.colors.surface
    Box(Modifier.clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { content() }
}

private fun DrawScope.drawBody(shape: MascotShape, color: Color) {
    val w = size.width
    val c = Offset(w / 2, w / 2)
    when (shape) {
        MascotShape.CIRCLE -> drawCircle(color, radius = w * 0.48f, center = c)
        MascotShape.SQUIRCLE -> rotate(-15f, c) {
            val side = w * 0.78f
            drawRoundRect(color, topLeft = Offset((w - side) / 2, (w - side) / 2), size = Size(side, side), cornerRadius = CornerRadius(side * 0.3f))
        }
        MascotShape.PEBBLE -> rotate(10f, c) {
            drawOval(color, topLeft = Offset(w * 0.04f, w * 0.12f), size = Size(w * 0.92f, w * 0.76f))
        }
        MascotShape.HEXAGON -> drawPath(roundedPolygon(c, w * 0.48f, 6, w * 0.10f, rotationDeg = 0f), color)
        MascotShape.BLOB -> drawPath(blob(w), color)
    }
}

private fun DrawScope.drawFace(ink: Color, mood: OrbMood, lookX: Float, blink: Float) {
    val w = size.width
    val eye = Offset(w * (0.40f + lookX), w * 0.46f)
    scale(1f, blink, eye) {
        drawOval(ink, eye - Offset(w * 0.05f, w * 0.083f), Size(w * 0.10f, w * 0.166f))
    }
    // One brushstroke lifts into a smile, levels in concentration, or dips in concern.
    val bend = when (mood) {
        OrbMood.WORKING -> -0.025f
        OrbMood.NEEDS_YOU, OrbMood.FAILED -> 0.11f
        else -> -0.14f
    }
    val x = w * (0.53f + lookX)
    val y = eye.y + w * 0.025f
    val lid = Path().apply {
        moveTo(x, y)
        quadraticTo(x + w * 0.075f, y + w * bend, x + w * 0.15f, y - w * 0.025f)
    }
    drawPath(lid, ink, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))
}

private fun roundedPolygon(c: Offset, r: Float, sides: Int, corner: Float, rotationDeg: Float): Path {
    // Polygon with rounded corners via quadratic curves between edge midpoints.
    val pts = (0 until sides).map { i ->
        val a = Math.toRadians((rotationDeg + i * 360.0 / sides) - 90)
        Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
    }
    val p = Path()
    fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    val k = (corner / r).coerceIn(0.05f, 0.45f)
    pts.indices.forEach { i ->
        val prev = pts[(i - 1 + sides) % sides]; val cur = pts[i]; val next = pts[(i + 1) % sides]
        val a = lerp(cur, prev, k); val b = lerp(cur, next, k)
        if (i == 0) p.moveTo(a.x, a.y) else p.lineTo(a.x, a.y)
        p.quadraticTo(cur.x, cur.y, b.x, b.y)
    }
    p.close()
    return p
}

/** Soft heart-ish blob, slightly tilted. */
private fun blob(w: Float): Path = Path().apply {
    moveTo(w * 0.50f, w * 0.90f)
    cubicTo(w * 0.22f, w * 0.80f, w * 0.02f, w * 0.62f, w * 0.06f, w * 0.38f)
    cubicTo(w * 0.10f, w * 0.14f, w * 0.34f, w * 0.06f, w * 0.50f, w * 0.20f)
    cubicTo(w * 0.66f, w * 0.06f, w * 0.92f, w * 0.12f, w * 0.95f, w * 0.38f)
    cubicTo(w * 0.98f, w * 0.64f, w * 0.78f, w * 0.82f, w * 0.50f, w * 0.90f)
    close()
}

