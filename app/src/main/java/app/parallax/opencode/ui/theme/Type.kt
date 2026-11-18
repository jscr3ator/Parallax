package app.parallax.opencode.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.parallax.opencode.R

val Geist = FontFamily(
    Font(R.font.geist_400, FontWeight.Normal),
    Font(R.font.geist_500, FontWeight.Medium),
    Font(R.font.geist_600, FontWeight.SemiBold),
    Font(R.font.geist_700, FontWeight.Bold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_400, FontWeight.Normal),
    Font(R.font.geist_mono_500, FontWeight.Medium),
)

/**
 * Type scale. Friendly and readable: generous body size, medium-weight titles, tight display tracking.
 * Mono is ONLY for code blocks, commands inside tool output, and file paths inside expanded details.
 */
@Immutable
data class ParallaxType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle,
)

val DefaultType = ParallaxType(
    display = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.025).em),
    headline = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em),
    title = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.005).em),
    body = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp),
    mono = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    monoSmall = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
)

internal fun materialTypography(t: ParallaxType) = Typography(
    displayLarge = t.display, displayMedium = t.display, displaySmall = t.headline,
    headlineLarge = t.headline, headlineMedium = t.headline, headlineSmall = t.title,
    titleLarge = t.headline, titleMedium = t.title, titleSmall = t.label,
    bodyLarge = t.body, bodyMedium = t.bodySmall, bodySmall = t.caption,
    labelLarge = t.label, labelMedium = t.caption, labelSmall = t.caption,
)

val LocalParallaxType = staticCompositionLocalOf { DefaultType }
