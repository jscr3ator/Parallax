package app.parallax.opencode.core.repo

import app.parallax.opencode.core.model.AgentOption
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.ConnectionState
import app.parallax.opencode.core.model.InboxItem
import app.parallax.opencode.core.model.McpServerInfo
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.ModelRef
import app.parallax.opencode.core.model.ParallelLaunch
import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.model.ProjectInfo
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.model.UsageSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single contract between UI and data. The UI depends only on this interface.
 *
 * Implementations:
 *  - `core.data.live.LiveAgentRepository` talks to the real server (REST + SSE).
 *  - `ui.preview.FakeAgentRepository` is in-memory demo data for previews and offline UI work.
 *
 * All suspend functions throw [RepoException] with a human-readable message on failure.
 * All state is held on the server (sessions, transcripts, MCP config); the app keeps only the server list.
 */
interface AgentRepository {
    val servers: StateFlow<List<ServerConfig>>
    val activeServer: StateFlow<ServerConfig?>
    val connection: StateFlow<ConnectionState>

    /** All sessions of the active server, newest activity first. Child (subagent) sessions included; filter by parentId. */
    val sessions: StateFlow<List<AgentSession>>

    /** Items waiting on the user plus recently finished runs, newest first. */
    val inbox: StateFlow<List<InboxItem>>

    /** Live transcript of one session: loads history, then follows events. Cold flow; collect while visible. */
    fun transcript(sessionId: String): Flow<List<TranscriptItem>>

    // --- servers ---
    suspend fun addServer(config: ServerConfig)
    suspend fun removeServer(serverId: String)
    suspend fun setActiveServer(serverId: String)

    /** Re-fetch everything for the active server; reconnects the event stream if it is offline. */
    suspend fun refresh()

    // --- sessions ---
    /** Creates a session and sends the first prompt. Returns the new session id. */
    suspend fun startSession(prompt: String, directory: String?, model: ModelRef?, agent: String?, title: String? = null): String

    /** Starts every run of [launch] concurrently. Returns the session ids in run order. */
    suspend fun launchParallel(launch: ParallelLaunch): List<String>

    suspend fun sendPrompt(sessionId: String, text: String, queued: Boolean = false)
    suspend fun cancelQueuedPrompt(sessionId: String, id: String)
    suspend fun interrupt(sessionId: String)
    suspend fun renameSession(sessionId: String, title: String)
    suspend fun deleteSession(sessionId: String)
    /** Archive (or restore) a chat on the server — the same archive OpenChamber shows. */
    suspend fun archiveSession(sessionId: String, archived: Boolean = true)

    /** Projects and folders as OpenChamber organizes them (falls back to opencode projects). */
    suspend fun workspace(): app.parallax.opencode.core.model.Workspace
    /** File a chat into an OpenChamber folder ([folderId] null = take it out of its folder). Synced to every OC client. */
    suspend fun moveToFolder(sessionId: String, scopeKey: String, folderId: String?, fromFolderId: String?)
    /** Create a folder in a project/chats scope; returns its id. */
    suspend fun createFolder(scopeKey: String, name: String): String
    suspend fun setSessionModel(sessionId: String, model: ModelRef)
    suspend fun markViewed(sessionId: String)

    // --- requests ---
    suspend fun replyPermission(sessionId: String, requestId: String, decision: PermissionDecision, message: String? = null, serverId: String? = null)
    suspend fun replyForm(sessionId: String, formId: String, answers: Map<String, Any>)
    suspend fun cancelForm(sessionId: String, formId: String)

    // --- catalog ---
    suspend fun models(): List<ModelOption>
    suspend fun defaultModel(): ModelRef?
    suspend fun agents(): List<AgentOption>
    suspend fun projects(): List<ProjectInfo>
    suspend fun mcpServers(): List<McpServerInfo>
    suspend fun setMcpConnected(name: String, connected: Boolean)
    suspend fun usage(): List<UsageSummary>
}

class RepoException(message: String, val code: Int? = null, cause: Throwable? = null) : Exception(message, cause)
