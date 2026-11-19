package app.parallax.opencode.ui.screens.agents

import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.SessionFolder
import app.parallax.opencode.core.model.Workspace

/** OpenChamber-style sidebar sections: projects (in OC's order), Chats, then anything else. */
data class SidebarSection(
    val key: String,
    val title: String,
    val kind: Kind,
    /** Folder scope (project root / chats root / directory) used for folder changes. */
    val scopeKey: String,
    val path: String?,
    val color: String?,
    val folders: List<FolderGroup>,
    val loose: List<AgentSession>,
) {
    enum class Kind { PROJECT, CHATS, OTHER }
    val total: Int get() = loose.size + folders.sumOf { it.sessions.size }
    val latest: Long get() = (loose.map { it.updatedAt } + folders.flatMap { f -> f.sessions.map { it.updatedAt } }).maxOrNull() ?: 0L
}

data class FolderGroup(val folder: SessionFolder, val sessions: List<AgentSession>)

private val CHAT_LEAF = Regex("^session-[0-9a-fA-F-]{8,}$")

fun isOpenChamberChat(directory: String?): Boolean =
    directory?.trimEnd('/')?.substringAfterLast('/')?.let { CHAT_LEAF.matches(it) } == true

fun buildSections(sessions: List<AgentSession>, ws: Workspace): List<SidebarSection> {
    val projects = ws.projects.sortedByDescending { it.path.length } // longest prefix wins (worktrees, nested repos)
    val byProject = linkedMapOf<String, MutableList<AgentSession>>()
    val chats = mutableListOf<AgentSession>()
    val other = linkedMapOf<String, MutableList<AgentSession>>()
    sessions.forEach { s ->
        val dir = s.directory?.trimEnd('/')
        val project = dir?.let { d -> projects.firstOrNull { p -> d == p.path || d.startsWith(p.path + "/") } }
            ?: s.projectId?.let { id -> ws.projects.firstOrNull { it.id == id } }
        when {
            isOpenChamberChat(dir) -> chats += s
            project != null -> byProject.getOrPut(project.id) { mutableListOf() } += s
            else -> other.getOrPut(dir ?: "unknown") { mutableListOf() } += s
        }
    }
    fun withFolders(scope: String, list: List<AgentSession>): Pair<List<FolderGroup>, List<AgentSession>> {
        val folders = ws.folders.filter { it.scopeKey.trimEnd('/') == scope.trimEnd('/') }
        val filed = mutableSetOf<String>()
        val groups = folders.map { f ->
            val inFolder = list.filter { it.id in f.sessionIds }.sortedByDescending { it.updatedAt }
            filed += inFolder.map { it.id }
            FolderGroup(f, inFolder)
        }.filter { it.sessions.isNotEmpty() || it.folder.parentId == null }
        return groups to list.filterNot { it.id in filed }.sortedByDescending { it.updatedAt }
    }
    val out = mutableListOf<SidebarSection>()
    // Projects in OpenChamber's own order.
    ws.projects.forEach { p ->
        val list = byProject[p.id] ?: return@forEach
        val (folders, loose) = withFolders(p.path, list)
        out += SidebarSection("p-${p.id}", p.name, SidebarSection.Kind.PROJECT, p.path, p.path, p.color, folders, loose)
    }
    if (chats.isNotEmpty()) {
        val root = ws.chatsRoot ?: chats.first().directory!!.trimEnd('/').substringBeforeLast('/').substringBeforeLast('/')
        val (folders, loose) = withFolders(root, chats)
        out += SidebarSection("chats", "Chats", SidebarSection.Kind.CHATS, root, null, null, folders, loose)
    }
    other.entries.sortedByDescending { (_, l) -> l.maxOf { it.updatedAt } }.forEach { (dir, list) ->
        val (folders, loose) = withFolders(dir, list)
        val name = dir.substringAfterLast('/').ifBlank { dir }
        out += SidebarSection("o-$dir", name, SidebarSection.Kind.OTHER, dir, dir, null, folders, loose)
    }
    return out
}

/** The folder scope a chat belongs to (what OpenChamber keys its folders by). */
fun scopeFor(session: AgentSession, ws: Workspace): String {
    val dir = session.directory?.trimEnd('/') ?: return ""
    if (isOpenChamberChat(dir)) return ws.chatsRoot ?: dir.substringBeforeLast('/').substringBeforeLast('/')
    return ws.projects.sortedByDescending { it.path.length }.firstOrNull { dir == it.path || dir.startsWith(it.path + "/") }?.path ?: dir
}
