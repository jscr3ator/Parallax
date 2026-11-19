package app.parallax.opencode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Radius
import app.parallax.opencode.ui.theme.Space
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableSessionRow(
    session: AgentSession,
    modelName: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = ParallaxTheme.colors
    // Swipe left archives (synced with OpenChamber, restorable there) — no confirmation needed.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(c.raised)
                    .padding(end = Space.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Archive, contentDescription = null, tint = c.textMuted)
            }
        },
        content = {
            SessionListRow(
                session = session, modelName = modelName, onClick = onOpen, onLongClick = onRename,
                modifier = Modifier.background(c.bg).semantics {
                    customActions = listOf(CustomAccessibilityAction("Archive chat") { onDelete(); true })
                },
            )
        },
    )
}
