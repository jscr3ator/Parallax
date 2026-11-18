package app.parallax.opencode.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * UI-facing domain model. The UI only ever sees these types; the data layer maps opencode's
 * wire JSON (see docs/api) into them. Keep these small, immutable and free of Android types.
 */

/** Which kind of backend a server entry points at. Both speak the same opencode v2 `/api/` routes. */
@Serializable
enum class BackendKind {
    /** A bare `opencode serve`. Auth: HTTP Basic, user "opencode", password = server password. */
    OPENCODE,
    /** An OpenChamber server (proxies opencode at `/api/` and adds its own routes). Auth: Bearer client token. */
    OPENCHAMBER,
}

@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val kind: BackendKind,
    /** Origin without a trailing slash, e.g. `http://192.168.1.20:3000`. */
    val baseUrl: String,
    /** OPENCODE: basic-auth username (default "opencode"). Unused for OPENCHAMBER. */
    val username: String = "opencode",
    /** OPENCODE: the server password. OPENCHAMBER: the paired device's client token. Empty = no auth. */
    val secret: String = "",
    /** Default directory new sessions run in, when the user has picked one. */
    val defaultDirectory: String? = null,
    val addedAt: Long = 0,
)

sealed interface ConnectionState {
    data object NoServer : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val serverVersion: String?) : ConnectionState
    data class Offline(val reason: String, val retryInMs: Long?) : ConnectionState
    data class AuthFailed(val message: String) : ConnectionState
}

@Serializable
data class ModelRef(val providerId: String, val modelId: String, val variant: String? = null) {
    val key: String get() = "$providerId/$modelId"
}

/** What a session is doing right now. Exactly one status color maps to each. */
enum class RunState { IDLE, RUNNING, NEEDS_YOU, FAILED, DONE }

data class AgentSession(
    val id: String,
    val serverId: String,
    val title: String,
    val directory: String?,
    val parentId: String?,
    val model: ModelRef?,
    val agent: String?,
    val state: RunState,
    /** One short human line for "what is it doing now", e.g. "Running `npm test`" or "Editing src/App.kt". */
    val activity: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** When the current run started (for an elapsed timer), null when not running. */
    val runStartedAt: Long?,
    val cost: Double,
    val tokensIn: Long,
    val tokensOut: Long,
    val pendingRequests: Int,
    val childCount: Int,
    val unread: Boolean,
    /** Error text of the last failed run. */
    val error: String? = null,
    /** Started from Parallax (tagged `metadata.parallax = true` on the server). Others are desktop/TUI history. */
    val fromParallax: Boolean = false,
)

enum class ToolStatus { PENDING, RUNNING, SUCCESS, FAILED }

/** One row in a session transcript, in display order. */
sealed interface TranscriptItem {
    val id: String

    data class User(override val id: String, val text: String, val time: Long, val queued: Boolean = false) : TranscriptItem

    data class AssistantText(override val id: String, val text: String, val streaming: Boolean) : TranscriptItem

    data class Reasoning(override val id: String, val text: String, val streaming: Boolean, val durationMs: Long?) : TranscriptItem

    data class Tool(
        override val id: String,
        /** Raw tool name, e.g. "shell", "read", "edit", "task", "mcp__github__search". */
        val name: String,
        /** Short verb phrase for the row, e.g. "Ran", "Read", "Edited", "Searched". */
        val verb: String,
        /** The subject, e.g. a command, a path or a query. Shown in mono. */
        val subject: String,
        val status: ToolStatus,
        val input: JsonObject?,
        /** Text output (may be long; UI truncates/collapses). */
        val output: String?,
        val startedAt: Long?,
        val endedAt: Long?,
        /** For subagent ("task") tools: the child session id, so the UI can open it. */
        val childSessionId: String? = null,
    ) : TranscriptItem

    data class Permission(override val id: String, val request: PermissionRequest) : TranscriptItem

    data class Form(override val id: String, val form: FormRequest) : TranscriptItem

    /** End-of-run marker. */
    data class RunEnd(override val id: String, val outcome: RunState, val time: Long, val error: String?) : TranscriptItem

    data class Notice(override val id: String, val text: String, val tone: Tone = Tone.INFO) : TranscriptItem {
        enum class Tone { INFO, WARN, ERROR }
    }
}

data class PermissionRequest(
    val id: String,
    val sessionId: String,
    /** What the agent wants to do, e.g. "bash", "edit", "webfetch". */
    val action: String,
    /** Resources it applies to, e.g. a command or file paths. */
    val resources: List<String>,
    val message: String?,
    val createdAt: Long,
)

enum class PermissionDecision(val wire: String) { ONCE("once"), ALWAYS("always"), REJECT("reject") }

data class FormRequest(
    val id: String,
    val sessionId: String,
    val title: String?,
    val fields: List<FormField>,
    val createdAt: Long,
)

data class FormField(
    val key: String,
    val label: String,
    val type: FieldType,
    val placeholder: String? = null,
    val options: List<String> = emptyList(),
    val default: String? = null,
    val required: Boolean = false,
) {
    enum class FieldType { TEXT, NUMBER, BOOLEAN, SINGLE_CHOICE, MULTI_CHOICE }
}

/** Something waiting on the user, or a run that just finished. Drives the Inbox and notifications. */
data class InboxItem(
    val key: String,
    val serverId: String,
    val sessionId: String,
    val sessionTitle: String,
    val kind: Kind,
    val title: String,
    val body: String?,
    val time: Long,
    /** Permission or form id when kind is PERMISSION/FORM. */
    val requestId: String?,
) {
    enum class Kind { PERMISSION, FORM, DONE, FAILED }
}

data class ModelOption(
    val ref: ModelRef,
    val name: String,
    val providerName: String,
    /** True for models served by the OpenCode Go subscription (provider "opencode-go"). */
    val isGo: Boolean,
    val contextWindow: Long? = null,
)

data class AgentOption(val id: String, val name: String, val description: String?, val isPrimary: Boolean)

data class McpServerInfo(
    val name: String,
    val status: McpStatus,
    val error: String?,
    val toolCount: Int?,
    val type: String?,
) {
    enum class McpStatus { CONNECTED, CONNECTING, DISABLED, FAILED, NEEDS_AUTH, UNKNOWN }
}

data class ProjectInfo(val id: String, val name: String, val directory: String, val lastUsed: Long?)

/** One run in a parallel launch. */
data class RunSpec(val model: ModelRef?, val agent: String?, val label: String? = null)

/** Launch the same task as N independent sessions (e.g. one per model) and compare. */
data class ParallelLaunch(
    val prompt: String,
    val directory: String?,
    val runs: List<RunSpec>,
    /** Optional shared title prefix; each run gets " · <label or model>" appended. */
    val title: String? = null,
)

data class UsageSummary(val label: String, val usedFraction: Float?, val detail: String?)
