package app.parallax.opencode.core.data.live

import app.parallax.opencode.core.model.AgentOption
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.FormField
import app.parallax.opencode.core.model.FormRequest
import app.parallax.opencode.core.model.McpServerInfo
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.ModelRef
import app.parallax.opencode.core.model.PermissionRequest
import app.parallax.opencode.core.model.ProjectInfo
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ToolStatus
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.model.UsageSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lenient mapping from opencode's wire JSON into the small domain model.
 * Nothing here touches Android or the network, so it is directly unit-testable.
 */

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayAt(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement?.stringList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> contentOrNull?.let { listOf(it) } ?: emptyList()
    else -> emptyList()
}

/** One parsed `/api/event` frame. [created] falls back to "now" only for frames that omit it (server.connected). */
internal data class AgentEvent(
    val id: String,
    val type: String,
    val created: Long,
    val directory: String?,
    val data: JsonObject,
)

internal fun parseAgentEvent(json: JsonObject): AgentEvent? {
    val type = json.string("type") ?: return null
    return AgentEvent(
        id = json.string("id") ?: "",
        type = type,
        created = json.long("created") ?: System.currentTimeMillis(),
        directory = json.objectAt("location")?.string("directory"),
        data = json.objectAt("data") ?: JsonObject(emptyMap()),
    )
}

// ---------------------------------------------------------------------------
// sessions
// ---------------------------------------------------------------------------

/**
 * Maps `Session.Info` (or a `session.created` payload, passing [eventTime]) into [AgentSession].
 * Wire sessions do not carry an explicit status, so it is derived from busy/idle/outcome/viewed.
 */
internal fun mapSession(json: JsonObject, serverId: String, eventTime: Long? = null): AgentSession? {
    val id = json.string("id") ?: json.string("sessionID") ?: return null
    val time = json.objectAt("time") ?: JsonObject(emptyMap())
    val created = time.long("created") ?: eventTime ?: 0L
    val updated = time.long("updated") ?: eventTime ?: created
    val busy = time.long("busy")
    val idle = time.long("idle")
    val viewed = time.long("viewed")
    val outcome = json.string("outcome")
    val running = busy != null && (idle == null || idle < busy)
    val state = when {
        running -> RunState.RUNNING
        outcome == "failed" -> RunState.FAILED
        outcome == "succeeded" -> RunState.DONE
        else -> RunState.IDLE
    }
    val tokens = json.objectAt("tokens")
    return AgentSession(
        id = id,
        serverId = serverId,
        title = json.string("title") ?: json.string("slug") ?: "Untitled session",
        directory = json.objectAt("location")?.string("directory") ?: json.string("directory"),
        parentId = json.string("parentID"),
        model = mapModelRef(json.objectAt("model")),
        agent = json.string("agent"),
        state = state,
        activity = if (running) "Thinking…" else null,
        createdAt = created,
        updatedAt = updated,
        runStartedAt = if (running) busy else null,
        cost = json.double("cost") ?: 0.0,
        tokensIn = tokens?.long("input") ?: 0L,
        tokensOut = tokens?.long("output") ?: 0L,
        pendingRequests = 0,
        childCount = 0,
        unread = outcome != null && updated > (viewed ?: 0L),
        error = null,
        fromParallax = json.objectAt("metadata")?.get("parallax")?.toString() == "true",
        projectId = json.string("projectID"),
    )
}

internal fun mapModelRef(json: JsonObject?): ModelRef? {
    if (json == null) return null
    val provider = json.string("providerID") ?: return null
    val id = json.string("modelID") ?: json.string("id") ?: return null
    return ModelRef(provider, id, json.string("variant"))
}

// ---------------------------------------------------------------------------
// messages -> transcript
// ---------------------------------------------------------------------------

/** Maps messages in display order (oldest first) into transcript rows. */
internal fun mapMessages(messages: List<JsonObject>): List<TranscriptItem> {
    val out = ArrayList<TranscriptItem>()
    messages.forEach { out += mapMessage(it) }
    return out
}

private fun mapMessage(message: JsonObject): List<TranscriptItem> {
    val id = message.string("id") ?: return emptyList()
    val created = message.objectAt("time")?.long("created") ?: 0L
    return when (message.string("type")) {
        "user" -> listOf(TranscriptItem.User(id, message.string("text") ?: "", created))

        "idle" -> {
            val outcome = when (message.string("outcome")) {
                "succeeded" -> RunState.DONE
                "failed" -> RunState.FAILED
                else -> RunState.IDLE
            }
            listOf(
                TranscriptItem.RunEnd(
                    id = id,
                    outcome = outcome,
                    time = created,
                    error = message.objectAt("error")?.string("message"),
                ),
            )
        }

        "synthetic", "system" -> {
            val text = message.string("text") ?: message.string("content") ?: return emptyList()
            listOf(TranscriptItem.Notice("notice:$id", text))
        }

        "assistant" -> mapAssistantMessage(id, message)

        else -> emptyList()
    }
}

private fun mapAssistantMessage(messageId: String, message: JsonObject): List<TranscriptItem> {
    val out = ArrayList<TranscriptItem>()
    var textOrdinal = 0
    var reasoningOrdinal = 0
    var toolOrdinal = 0
    message.arrayAt("content")?.forEach { element ->
        val part = element as? JsonObject ?: return@forEach
        when (part.string("type")) {
            "text" -> {
                val id = "text:$messageId:$textOrdinal"
                textOrdinal++
                out += TranscriptItem.AssistantText(id, part.string("text") ?: "", streaming = false)
            }

            "reasoning" -> {
                val id = "reason:$messageId:$reasoningOrdinal"
                reasoningOrdinal++
                val time = part.objectAt("time")
                val start = time?.long("created")
                val end = time?.long("completed")
                out += TranscriptItem.Reasoning(
                    id = id,
                    text = part.string("text") ?: "",
                    streaming = false,
                    durationMs = if (start != null && end != null && end >= start) end - start else null,
                )
            }

            "tool" -> {
                val toolId = part.string("id") ?: "tool:$messageId:${toolOrdinal++}"
                val state = part.objectAt("state")
                val input = state?.get("input") as? JsonObject
                val status = when (state?.string("status")) {
                    "running" -> ToolStatus.RUNNING
                    "completed" -> ToolStatus.SUCCESS
                    "error" -> ToolStatus.FAILED
                    else -> ToolStatus.PENDING
                }
                val metadata = state?.objectAt("metadata")
                val display = toolDisplay(part.string("name") ?: "tool", input, metadata)
                val time = part.objectAt("time")
                out += TranscriptItem.Tool(
                    id = toolId,
                    name = part.string("name") ?: "tool",
                    verb = display.verb,
                    subject = display.subject,
                    status = status,
                    input = input,
                    output = toolContentText(state?.arrayAt("content"))
                        ?: state?.objectAt("error")?.string("message"),
                    startedAt = time?.long("ran") ?: time?.long("created"),
                    endedAt = time?.long("completed"),
                    childSessionId = display.childSessionId,
                )
            }
        }
    }
    return out
}

private fun toolContentText(content: JsonArray?): String? {
    if (content == null) return null
    val parts = content.mapNotNull { element ->
        val part = element as? JsonObject ?: return@mapNotNull null
        when (part.string("type")) {
            "text" -> part.string("text")
            "file" -> part.string("uri")?.let { "[file] $it" }
            else -> null
        }
    }
    return parts.joinToString("\n").takeIf { it.isNotBlank() }
}

// ---------------------------------------------------------------------------
// tools
// ---------------------------------------------------------------------------

internal data class ToolDisplay(
    val verb: String,
    val subject: String,
    val childSessionId: String? = null,
)

/**
 * Turns an opencode tool name + input into the short row shown in the transcript.
 * MCP tools (`mcp__server__tool`, or any name containing "mcp") become "Used · server · tool".
 */
internal fun toolDisplay(name: String, input: JsonObject?, metadata: JsonObject? = null): ToolDisplay {
    val lower = name.lowercase(Locale.US)
    val child = childSessionId(metadata) ?: childSessionId(input)
    if (lower.contains("__") || lower.contains("mcp")) {
        return mcpDisplay(name).copy(childSessionId = child)
    }
    val display = when (lower) {
        "shell", "bash" -> ToolDisplay("Ran", subjectOf(input?.string("command"), input?.string("cmd")))
        "read" -> ToolDisplay("Read", subjectOf(input?.string("filePath"), input?.string("path"), input?.string("file")))
        "edit", "write", "patch" -> ToolDisplay(
            "Edited",
            subjectOf(input?.string("filePath"), input?.string("path"), input?.string("file")),
        )
        "grep", "glob" -> ToolDisplay(
            "Searched",
            subjectOf(input?.string("pattern"), input?.string("query"), input?.string("path")),
        )
        "list", "ls" -> ToolDisplay("Searched", subjectOf(input?.string("path"), input?.string("dir")))
        "webfetch", "fetch" -> ToolDisplay("Fetched", subjectOf(input?.string("url")))
        "websearch", "web_search" -> ToolDisplay("Searched web", subjectOf(input?.string("query")))
        "task" -> ToolDisplay(
            "Delegated",
            subjectOf(
                input?.string("description"),
                input?.string("prompt"),
                input?.string("task"),
            ),
            child,
        )
        else -> ToolDisplay(name, subjectOf(input?.string("description"), input?.string("path")))
    }
    return display.copy(childSessionId = display.childSessionId ?: child)
}

private fun mcpDisplay(name: String): ToolDisplay {
    val parts = name.split("__").filter { it.isNotBlank() }
    val cleaned = if (parts.size > 1 && parts.first().equals("mcp", ignoreCase = true)) parts.drop(1) else parts
    val server: String
    val tool: String
    if (cleaned.size >= 2) {
        server = cleaned.first()
        tool = cleaned.drop(1).joinToString("__")
    } else {
        val rest = name.removePrefix("mcp__").removePrefix("mcp_").removePrefix("mcp.")
        val bits = rest.split('_', limit = 2)
        server = bits.firstOrNull()?.takeIf { it.isNotBlank() } ?: "mcp"
        tool = bits.getOrElse(1) { rest }
    }
    return ToolDisplay("Used", cleanSubject("$server · $tool"))
}

private fun childSessionId(json: JsonObject?): String? {
    if (json == null) return null
    return listOf(
        "sessionID", "sessionId", "childSessionID", "childSessionId", "child_session_id",
    ).firstNotNullOfOrNull { json.string(it) }
}

private fun subjectOf(vararg values: String?): String {
    val value = values.firstOrNull { !it.isNullOrBlank() } ?: return ""
    return cleanSubject(value)
}

private fun cleanSubject(value: String): String {
    val oneLine = value.trim()
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s{2,}"), " ")
    return if (oneLine.length > 240) oneLine.take(239) + "…" else oneLine
}

// ---------------------------------------------------------------------------
// permissions and forms
// ---------------------------------------------------------------------------

internal fun mapPermission(
    json: JsonObject,
    fallbackSessionId: String,
    createdAt: Long,
): PermissionRequest? {
    val id = json.string("id") ?: return null
    return PermissionRequest(
        id = id,
        sessionId = json.string("sessionID") ?: fallbackSessionId,
        action = json.string("action") ?: "request",
        resources = json.arrayAt("resources").stringList(),
        message = json.string("message"),
        createdAt = createdAt,
    )
}

internal fun mapForm(json: JsonObject, fallbackSessionId: String, createdAt: Long): FormRequest? {
    val id = json.string("id") ?: return null
    val fields = json.arrayAt("fields")?.mapNotNull { (it as? JsonObject)?.let(::mapFormField) } ?: emptyList()
    return FormRequest(
        id = id,
        sessionId = json.string("sessionID") ?: fallbackSessionId,
        title = json.string("title"),
        fields = fields,
        createdAt = createdAt,
    )
}

private fun mapFormField(json: JsonObject): FormField? {
    val key = json.string("key") ?: return null
    val options = json.arrayAt("options")?.mapNotNull { option ->
        when (option) {
            is JsonObject -> option.string("label") ?: option.string("value")
            is JsonPrimitive -> option.contentOrNull
            else -> null
        }
    } ?: emptyList()
    val type = when (json.string("type")) {
        "number", "integer" -> FormField.FieldType.NUMBER
        "boolean" -> FormField.FieldType.BOOLEAN
        "multiselect" -> FormField.FieldType.MULTI_CHOICE
        "select", "single_select", "single-choice", "choice" -> FormField.FieldType.SINGLE_CHOICE
        "string" -> if (options.isEmpty()) FormField.FieldType.TEXT else FormField.FieldType.SINGLE_CHOICE
        else -> FormField.FieldType.TEXT
    }
    val default = when (val value = json["default"]) {
        is JsonPrimitive -> value.contentOrNull
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
        else -> null
    }
    return FormField(
        key = key,
        label = json.string("title") ?: json.string("label") ?: key,
        type = type,
        placeholder = json.string("placeholder"),
        options = options,
        default = default,
        required = json.bool("required") == true,
    )
}

// ---------------------------------------------------------------------------
// catalog
// ---------------------------------------------------------------------------

internal fun mapProviderNames(element: JsonElement?): Map<String, String> {
    val array = element.asArrayOrNull() ?: return emptyMap()
    return array.mapNotNull { item ->
        val json = item as? JsonObject ?: return@mapNotNull null
        val id = json.string("id") ?: return@mapNotNull null
        id to (json.string("name") ?: id)
    }.toMap()
}

internal fun mapModels(element: JsonElement?, providerNames: Map<String, String>): List<ModelOption> {
    val array = element.asArrayOrNull() ?: return emptyList()
    return array.mapNotNull { item ->
        val json = item as? JsonObject ?: return@mapNotNull null
        val providerId = json.string("providerID") ?: return@mapNotNull null
        val modelId = json.string("modelID") ?: json.string("id") ?: return@mapNotNull null
        ModelOption(
            ref = ModelRef(providerId, modelId),
            name = json.string("name") ?: modelId,
            providerName = providerNames[providerId] ?: providerId,
            isGo = providerId == "opencode-go",
            contextWindow = json.objectAt("limit")?.long("context"),
        )
    }.sortedWith(compareByDescending<ModelOption> { it.isGo }.thenBy { it.providerName }.thenBy { it.name })
}

internal fun mapAgents(element: JsonElement?): List<AgentOption> {
    val array = element.asArrayOrNull() ?: return emptyList()
    return array.mapNotNull { item ->
        val json = item as? JsonObject ?: return@mapNotNull null
        val id = json.string("id") ?: return@mapNotNull null
        // Hidden agents (compaction, title, summary) are opencode internals, not user choices.
        if (json["hidden"]?.toString() == "true") return@mapNotNull null
        AgentOption(
            id = id,
            name = json.string("name") ?: id,
            description = json.string("description"),
            isPrimary = json.string("mode") != "subagent" && json["hidden"]?.toString() != "true",
        )
    }
}

internal fun mapProjects(element: JsonElement?): List<ProjectInfo> {
    val array = element.asArrayOrNull() ?: return emptyList()
    return array.mapNotNull { item ->
        val json = item as? JsonObject ?: return@mapNotNull null
        val id = json.string("id") ?: return@mapNotNull null
        val directory = json.string("canonical") ?: json.string("directory") ?: return@mapNotNull null
        ProjectInfo(
            id = id,
            name = json.string("name") ?: directory.substringAfterLast('/').ifBlank { directory },
            directory = directory,
            lastUsed = json.objectAt("time")?.let { it.long("active") ?: it.long("updated") },
        )
    }.sortedByDescending { it.lastUsed ?: 0L }
}

internal fun mapMcpServers(element: JsonElement?): List<McpServerInfo> {
    val array = element.asArrayOrNull() ?: return emptyList()
    return array.mapNotNull { item ->
        val json = item as? JsonObject ?: return@mapNotNull null
        val name = json.string("name") ?: return@mapNotNull null
        val statusJson = json.objectAt("status")
        val statusText = statusJson?.string("status") ?: json.string("status") ?: ""
        McpServerInfo(
            name = name,
            status = when (statusText) {
                "connected" -> McpServerInfo.McpStatus.CONNECTED
                "pending" -> McpServerInfo.McpStatus.CONNECTING
                "disabled" -> McpServerInfo.McpStatus.DISABLED
                "failed" -> McpServerInfo.McpStatus.FAILED
                "needs_auth" -> McpServerInfo.McpStatus.NEEDS_AUTH
                else -> McpServerInfo.McpStatus.UNKNOWN
            },
            error = statusJson?.string("error") ?: json.string("error"),
            toolCount = json.long("toolCount")?.toInt() ?: json.arrayAt("tools")?.size,
            type = json.string("type") ?: json.string("transport"),
        )
    }
}

internal fun mapUsage(element: JsonElement?): List<UsageSummary> {
    val root = element.asObjectOrNull() ?: return emptyList()
    val data = root.objectAt("data") ?: root
    val out = ArrayList<UsageSummary>()
    val go = data.objectAt("go")
    fun meter(label: String, key: String) {
        val meter = go?.objectAt(key) ?: return
        val percent = meter.double("percent") ?: return
        val limited = meter.bool("limited") == true
        val resetsAt = meter.long("resetsAt")
        out += UsageSummary(
            label = label,
            usedFraction = (percent / 100.0).toFloat().coerceIn(0f, 1f),
            detail = when {
                limited && resetsAt != null -> "Limit reached · resets ${formatTime(resetsAt)}"
                limited -> "Limit reached"
                resetsAt != null -> "${percent.roundToInt()}% · resets ${formatTime(resetsAt)}"
                else -> "${percent.roundToInt()}% used"
            },
        )
    }
    meter("Rolling usage", "rolling")
    meter("Weekly usage", "weekly")
    meter("Monthly usage", "monthly")

    data.arrayAt("budgets")?.forEach { item ->
        val budget = item as? JsonObject ?: return@forEach
        val spent = budget.double("spent")
        val limit = budget.double("limit")
        out += UsageSummary(
            label = budget.string("id") ?: "Budget",
            usedFraction = if (spent != null && limit != null && limit > 0) {
                (spent / limit).toFloat().coerceIn(0f, 1f)
            } else {
                null
            },
            detail = when {
                spent != null && limit != null -> "$${money(spent)} of $${money(limit)}"
                limit != null -> "$${money(limit)} limit"
                else -> null
            },
        )
    }
    return out
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

private fun formatTime(epochMillis: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun money(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)

internal fun near(a: Long, b: Long, window: Long = 5_000L): Boolean = abs(a - b) <= window

