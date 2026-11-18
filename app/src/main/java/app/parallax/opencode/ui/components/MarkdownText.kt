package app.parallax.opencode.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.parallax.opencode.ui.theme.ParallaxTheme
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/** Assistant prose rendered as markdown, styled with Parallax tokens (code on `sunken`, inline code on `raised`). */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val c = ParallaxTheme.colors
    val t = ParallaxTheme.type
    Markdown(
        content = text,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = c.text,
            codeBackground = c.sunken,
            inlineCodeBackground = c.raised,
            dividerColor = c.hairline,
            tableBackground = c.surface,
        ),
        typography = markdownTypography(
            h1 = t.headline,
            h2 = t.headline.copy(fontSize = t.title.fontSize * 1.15f),
            h3 = t.title.copy(fontWeight = FontWeight.SemiBold),
            h4 = t.title,
            h5 = t.label,
            h6 = t.label,
            text = t.body,
            code = t.mono,
            inlineCode = t.mono.copy(color = c.text),
            quote = t.body.copy(color = c.textMuted),
            paragraph = t.body,
            ordered = t.body,
            bullet = t.body,
            list = t.body,
        ),
    )
}
