package app.parallax.opencode.ui.screens.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.Workspace
import app.parallax.opencode.ui.components.Mascot
import app.parallax.opencode.ui.components.PrimaryButton
import app.parallax.opencode.ui.theme.ParallaxTheme
import app.parallax.opencode.ui.theme.Space

/** Long-press actions for a chat: rename, move to an OpenChamber folder (synced), archive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionActionsSheet(
    session: AgentSession,
    workspace: Workspace,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onMove: (String?) -> Unit,
    onNewFolder: (String) -> Unit,
) {
    val c = ParallaxTheme.colors
    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val scope = scopeFor(session, workspace)
    val folders = workspace.folders.filter { it.scopeKey.trimEnd('/') == scope.trimEnd('/') }
    val current = folders.firstOrNull { session.id in it.sessionIds }?.id

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = Space.gutter).navigationBarsPadding().padding(bottom = Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Space.md)) {
                Mascot(40.dp, seed = session.id)
                Spacer(Modifier.width(Space.md))
                Text(session.title.ifBlank { "Untitled chat" }, style = ParallaxTheme.type.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when {
                naming -> {
                    Text("New folder", style = ParallaxTheme.type.label, color = c.text)
                    Spacer(Modifier.height(Space.sm))
                    BasicTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        textStyle = ParallaxTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.raised).padding(horizontal = Space.lg, vertical = Space.md),
                        decorationBox = { inner -> if (name.isEmpty()) Text("Folder name", style = ParallaxTheme.type.body, color = c.textFaint); inner() },
                    )
                    Spacer(Modifier.height(Space.md))
                    PrimaryButton("Create and move", onClick = { onNewFolder(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
                }
                picking -> {
                    Text("Move to folder", style = ParallaxTheme.type.label, color = c.textMuted, modifier = Modifier.padding(bottom = Space.xs))
                    ActionRow(Icons.Rounded.FolderOff, "No folder", selected = current == null) { onMove(null) }
                    folders.filter { it.parentId == null || folders.none { f -> f.id == it.parentId } }.forEach { f ->
                        ActionRow(Icons.Rounded.Folder, f.name, selected = f.id == current) { onMove(f.id) }
                    }
                    ActionRow(Icons.Rounded.CreateNewFolder, "New folder…") { naming = true }
                }
                else -> {
                    ActionRow(Icons.Rounded.Edit, "Rename") { onRename() }
                    if (workspace.supportsFolders) ActionRow(Icons.Rounded.DriveFileMove, "Move to folder") { picking = true }
                    ActionRow(Icons.Rounded.Archive, "Archive") { onArchive() }
                    if (workspace.supportsFolders) {
                        Text(
                            "Folders and archive sync with OpenChamber on every device.",
                            style = ParallaxTheme.type.caption, color = c.textFaint, modifier = Modifier.padding(top = Space.sm, start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = ParallaxTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.textMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.md))
        Text(label, style = ParallaxTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, "Current", tint = c.accentText, modifier = Modifier.size(20.dp))
    }
}
