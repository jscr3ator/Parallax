package app.parallax.opencode.ui

import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.SessionFolder
import app.parallax.opencode.core.model.Workspace
import app.parallax.opencode.core.model.WorkspaceProject
import app.parallax.opencode.ui.screens.agents.SidebarSection
import app.parallax.opencode.ui.screens.agents.buildSections
import app.parallax.opencode.ui.screens.agents.scopeFor
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupingTest {
    private fun s(id: String, dir: String, t: Long) = AgentSession(
        id = id, serverId = "x", title = id, directory = dir, parentId = null, model = null, agent = null,
        state = RunState.DONE, activity = null, createdAt = t, updatedAt = t, runStartedAt = null, cost = 0.0,
        tokensIn = 0, tokensOut = 0, pendingRequests = 0, childCount = 0, unread = false,
    )

    private val ws = Workspace(
        projects = listOf(
            WorkspaceProject("p1", "Fretlark", "/home/pi/fretlark"),
            WorkspaceProject("p2", "Site", "/home/pi/site"),
        ),
        folders = listOf(SessionFolder("f1", "Bugs", "/home/pi/fretlark", null, listOf("a2"))),
        chatsRoot = "/home/pi/.config/openchamber/chats",
        supportsFolders = true,
    )

    private val sessions = listOf(
        s("a1", "/home/pi/fretlark", 5),
        s("a2", "/home/pi/fretlark/.worktrees/fix-login", 9), // worktree under the project, filed in "Bugs"
        s("b1", "/home/pi/site", 3),
        s("c1", "/home/pi/.config/openchamber/chats/2026-11-17/session-fd9ed0b6-01cf-4884-b070-22da1b000000", 7),
        s("o1", "/tmp/scratch", 1),
    )

    @Test fun groupsLikeOpenChamberSidebar() {
        val sections = buildSections(sessions, ws)
        assertEquals(listOf("Fretlark", "Site", "Chats", "scratch"), sections.map { it.title })
        val fret = sections[0]
        assertEquals(listOf("Bugs"), fret.folders.map { it.folder.name })
        assertEquals(listOf("a2"), fret.folders[0].sessions.map { it.id })
        assertEquals(listOf("a1"), fret.loose.map { it.id })
        assertEquals(SidebarSection.Kind.CHATS, sections[2].kind)
        assertEquals(listOf("c1"), sections[2].loose.map { it.id })
    }

    @Test fun scopeIsProjectRootOrChatsRoot() {
        assertEquals("/home/pi/fretlark", scopeFor(sessions[1], ws))
        assertEquals("/home/pi/.config/openchamber/chats", scopeFor(sessions[3], ws))
        assertEquals("/tmp/scratch", scopeFor(sessions[4], ws))
    }
}
