package app.parallax.opencode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun ParallaxTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val c = if (dark) DarkColors else LightColors
    // Material components still read MaterialTheme; map our tokens onto it so they match.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.textMuted, onSecondary = c.bg,
            background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.raised, onSurfaceVariant = c.textMuted, surfaceContainer = c.surface,
            surfaceContainerHigh = c.raised, surfaceContainerHighest = c.raised, surfaceContainerLow = c.surface,
            outline = c.hairlineStrong, outlineVariant = c.hairline, error = c.failed, scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.textMuted, onSecondary = c.bg,
            background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.raised, onSurfaceVariant = c.textMuted, surfaceContainer = c.surface,
            surfaceContainerHigh = c.raised, surfaceContainerHighest = c.raised, surfaceContainerLow = c.surface,
            outline = c.hairlineStrong, outlineVariant = c.hairline, error = c.failed, scrim = c.scrim,
        )
    }
    CompositionLocalProvider(LocalParallaxColors provides c, LocalParallaxType provides DefaultType) {
        MaterialTheme(colorScheme = scheme, typography = materialTypography(DefaultType), content = content)
    }
}

/** Accessors: `ParallaxTheme.colors.surface`, `ParallaxTheme.type.title`. */
object ParallaxTheme {
    val colors: ParallaxColors
        @Composable @ReadOnlyComposable get() = LocalParallaxColors.current
    val type: ParallaxType
        @Composable @ReadOnlyComposable get() = LocalParallaxType.current
}
