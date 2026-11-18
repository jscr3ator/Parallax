package app.parallax.opencode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
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
    var confirmDelete by remember(session.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) {
                confirmDelete = true
                false
            } else false
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
                    .background(c.failedWash)
                    .padding(end = Space.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = c.failed)
            }
        },
        content = {
            SessionListRow(
                session = session, modelName = modelName, onClick = onOpen, onLongClick = onRename,
                modifier = Modifier.background(c.bg).semantics {
                    customActions = listOf(CustomAccessibilityAction("Delete chat") { confirmDelete = true; true })
                },
            )
        },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete session?", style = ParallaxTheme.type.title, color = c.text) },
            text = { Text("“${session.title.ifBlank { "Untitled chat" }}” will be removed from the server.", style = ParallaxTheme.type.bodySmall, color = c.textMuted) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", style = ParallaxTheme.type.label, color = c.failed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", style = ParallaxTheme.type.label, color = c.textMuted)
                }
            },
        )
    }
}
