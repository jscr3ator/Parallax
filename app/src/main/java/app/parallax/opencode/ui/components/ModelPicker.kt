package app.parallax.opencode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.ModelRef
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space

/**
 * The one model picker. A tall sheet with search, provider filters and a lazily-scrolled list with sticky
 * section headers (Go first). Single mode picks on tap; multi mode toggles and confirms with a button
 * (used for parallel runs, with an optional runs-per-model stepper).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelPicker(
    models: List<ModelOption>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<ModelRef>) -> Unit,
    multi: Boolean = false,
    runsPerModel: Int = 1,
    onRunsChange: ((Int) -> Unit)? = null,
) {
    val c = ParallaxTheme.colors
    val haptic = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Go") }
    var picked by remember { mutableStateOf(selected) }
    val listState = rememberLazyListState()

    val providers = remember(models) { models.filterNot { it.isGo }.map { it.providerName }.distinct().sorted() }
    val filters = remember(providers) { listOf("Go", "All") + providers }
    val visible = remember(models, query, filter) {
        val q = query.trim().lowercase()
        models.filter { m ->
            val byFilter = when (filter) { "Go" -> m.isGo; "All" -> true; else -> !m.isGo && m.providerName == filter }
            val byQuery = q.isEmpty() || m.name.lowercase().contains(q) || m.ref.modelId.lowercase().contains(q) || m.providerName.lowercase().contains(q)
            byFilter && byQuery
        }
    }
    val groups = remember(visible) {
        visible.groupBy { if (it.isGo) "Included with Go" else it.providerName }
            .toList().sortedBy { (k, _) -> if (k == "Included with Go") "" else k }
    }
    // Jump back to the top whenever the filter or search changes, so results never open mid-list.
    LaunchedEffect(filter, query) { listState.scrollToItem(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(c.hairlineStrong)) },
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (multi) "Run in parallel" else "Choose a model", style = ParallaxTheme.type.headline, color = c.text, modifier = Modifier.weight(1f))
                    if (multi) Text("${picked.size} selected", style = ParallaxTheme.type.bodySmall, color = c.textMuted)
                }
                Spacer(Modifier.height(Space.md))
                SearchField(query, { query = it }, "Search models")
            }
            Spacer(Modifier.height(Space.md))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(filters, key = { it }) { f ->
                    val sel = f == filter
                    val bg by animateColorAsState(if (sel) c.text else c.raised, label = "f")
                    Box(
                        Modifier.height(34.dp).clip(CircleShape).background(bg).clickable { filter = f }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(f, style = ParallaxTheme.type.bodySmall, color = if (sel) c.bg else c.text, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(Space.sm))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Space.lg),
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text("No models match.", style = ParallaxTheme.type.bodySmall, color = c.textMuted, modifier = Modifier.padding(Space.gutter))
                    }
                }
                groups.forEach { (title, list) ->
                    stickyHeader(key = "h-$title") {
                        Text(
                            title, style = ParallaxTheme.type.caption, color = c.textMuted,
                            modifier = Modifier.fillMaxWidth().background(c.surface).padding(horizontal = Space.gutter, vertical = 8.dp),
                        )
                    }
                    items(list, key = { it.ref.key }) { m ->
                        val sel = m.ref.key in picked
                        ModelRow(m, sel, Modifier.animateItem()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (multi) {
                                picked = if (sel) (if (picked.size > 1) picked - m.ref.key else picked) else picked + m.ref.key
                            } else {
                                onConfirm(listOf(m.ref))
                            }
                        }
                    }
                }
            }
            if (multi) {
                Column(Modifier.padding(horizontal = Space.gutter).navigationBarsPadding().padding(bottom = Space.md)) {
                    if (onRunsChange != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.sm)) {
                            Text("Runs per model", style = ParallaxTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                            Stepper(runsPerModel, onRunsChange)
                        }
                    }
                    val total = picked.size * runsPerModel
                    PrimaryButton(
                        if (total <= 1) "Use this model" else "Run $total agents in parallel",
                        onClick = { onConfirm(models.filter { it.ref.key in picked }.map { it.ref }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun ModelRow(m: ModelOption, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = ParallaxTheme.colors
    val bg by animateColorAsState(if (selected) c.accentWash else Color.Transparent, label = "row")
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.sm)
            .clip(RoundedCornerShape(Radius.md))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderTile(if (m.isGo) "Go" else m.providerName, m.isGo)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(m.name, style = ParallaxTheme.type.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(if (m.isGo) "OpenCode Go" else m.providerName)
                m.contextWindow?.takeIf { it > 0 }?.let { append(" · ${it / 1000}k context") }
            }
            Text(sub, style = ParallaxTheme.type.caption, color = c.textMuted, maxLines = 1)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.accentText, modifier = Modifier.size(20.dp))
    }
}

/** Small rounded tile with the provider's initial; Go gets the brand gradient. */
@Composable
private fun ProviderTile(provider: String, go: Boolean) {
    val c = ParallaxTheme.colors
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
            .background(if (go) c.accentBrush else androidx.compose.ui.graphics.SolidColor(c.raised)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (go) "Go" else provider.trim().take(1).uppercase(),
            style = ParallaxTheme.type.caption.copy(fontSize = 12.sp),
            color = if (go) c.onAccent else c.textMuted,
        )
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    val c = ParallaxTheme.colors
    Row(Modifier.clip(CircleShape).background(c.raised), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).clickable(enabled = value > 1) { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, "Fewer", tint = if (value > 1) c.text else c.textFaint, modifier = Modifier.size(18.dp))
        }
        Text("$value", style = ParallaxTheme.type.label, color = c.text, modifier = Modifier.padding(horizontal = 6.dp))
        Box(Modifier.size(38.dp).clip(CircleShape).clickable(enabled = value < 4) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, "More", tint = if (value < 4) c.text else c.textFaint, modifier = Modifier.size(18.dp))
        }
    }
}
