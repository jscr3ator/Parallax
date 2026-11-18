package app.parallax.opencode.core.data.live

import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.FormRequest
import app.parallax.opencode.core.model.PermissionRequest
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ToolStatus
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.net.ApiJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.max

/**
 * All mutable run/transcript state for one session. The reducer functions below are pure:
 * they take a state plus one parsed SSE event and return the next state.
 */
internal data class TranscriptState(
    val items: List<TranscriptItem> = emptyList(),
    val queueRevision: Long = 0,
    val runActive: Boolean = false,
    val runStartedAt: Long? = null,
    val lastOutcome: RunState = RunState.IDLE,
    val error: String? = null,
    val pendingPermissions: Map<String, PermissionRequest> = emptyMap(),
    val pendingForms: Map<String, FormRequest> = emptyMap(),
) {
    val pendingCount: Int get() = pendingPermissions.size + pendingForms.size

    val runningTool: TranscriptItem.Tool?
        get() = items.lastOrNull { it is TranscriptItem.Tool && it.status == ToolStatus.RUNNING } as? TranscriptItem.Tool

    /** The "what is it doing now" line: latest running tool, else thinking/waiting. */
    val activity: String?
        get() {
            val tool = runningTool
            return when {
                tool != null -> "${tool.verb} ${tool.subject}".trim().take(120)
                pendingCount > 0 -> "Waiting for you"
                runActive -> "Thinking…"
                else -> null
            }
        }

    val state: RunState
        get() = when {
            pendingCount > 0 -> RunState.NEEDS_YOU
            runActive -> RunState.RUNNING
            else -> lastOutcome
        }
}

internal object EventReducer {

    private val transcriptTypes = setOf(
        "session.execution.started",
        "session.execution.succeeded",
        "session.execution.failed",
        "session.execution.interrupted",
        "session.idle",
        "SessionStatusUpdated",
        "session.step.started",
        "session.step.ended",
        "session.step.failed",
        "session.retry.scheduled",
        "session.text.started",
        "session.text.delta",
        "session.text.ended",
        "session.reasoning.started",
        "session.reasoning.delta",
        "session.reasoning.ended",
        "session.tool.input.started",
        "session.tool.input.ended",
        "session.tool.called",
        "session.tool.progress",
        "session.tool.success",
        "session.tool.failed",
        "session.inbox.enqueued",
        "session.inbox.delivered",
        "session.inbox.cancelled",
        "session.synthetic",
        "permission.asked",
        "permission.replied",
        "form.created",
        "form.replied",
        "form.cancelled",
    )

    fun isTranscriptEvent(type: String): Boolean = type in transcriptTypes

    fun apply(state: TranscriptState, event: AgentEvent): TranscriptState = when (event.type) {
        "session.execution.started" -> state.copy(
            runActive = true,
            runStartedAt = event.created,
            error = null,
        )

        "session.execution.succeeded" -> withRunEnd(
            state.copy(runActive = false, runStartedAt = null, error = null),
            outcome = RunState.DONE,
            time = event.created,
            error = null,
            eventId = event.id,
        )

        "session.execution.failed" -> {
            val message = event.data.objectAt("error")?.string("message") ?: "Run failed"
            withRunEnd(
                state.copy(runActive = false, runStartedAt = null, error = message),
                outcome = RunState.FAILED,
                time = event.created,
                error = message,
                eventId = event.id,
            )
        }

        "session.execution.interrupted" -> state.copy(
            runActive = false,
            runStartedAt = null,
            lastOutcome = RunState.IDLE,
        )

        "session.idle", "SessionStatusUpdated" -> {
            val status = event.data.objectAt("status")?.string("type")
            when {
                event.type == "session.idle" || status == "idle" -> {
                    if (state.runActive) {
                        state.copy(runActive = false, runStartedAt = null, lastOutcome = RunState.IDLE)
                    } else {
                        state
                    }
                }
                status == "busy" || status == "retry" -> state.copy(
                    runActive = true,
                    runStartedAt = state.runStartedAt ?: event.created,
                )
                else -> state
            }
        }

        "session.step.started" -> state.copy(
            runActive = true,
            runStartedAt = state.runStartedAt ?: event.data.long("started") ?: event.created,
        )

        "session.step.failed" -> state.copy(
            error = event.data.objectAt("error")?.string("message") ?: state.error,
        )

        "session.step.ended" -> state

        "session.text.started" -> upsertText(state, event, delta = "", streaming = true)

        "session.text.delta" -> upsertText(
            state,
            event,
            delta = event.data.string("delta") ?: "",
            streaming = true,
        )

        "session.text.ended" -> upsertText(
            state,
            event,
            delta = event.data.string("text") ?: "",
            streaming = false,
            authoritative = true,
        )

        "session.reasoning.started" -> upsertReasoning(state, event, delta = "", streaming = true)

        "session.reasoning.delta" -> upsertReasoning(
            state,
            event,
            delta = event.data.string("delta") ?: "",
            streaming = true,
        )

        "session.reasoning.ended" -> upsertReasoning(
            state,
            event,
            delta = event.data.string("text") ?: "",
            streaming = false,
            authoritative = true,
        )

        "session.tool.input.started" -> {
            val id = event.data.string("id") ?: return state
            upsertTool(
                state = state,
                id = id,
                name = event.data.string("name") ?: "tool",
                input = null,
                metadata = null,
                status = ToolStatus.PENDING,
                output = null,
                startedAt = event.created,
                endedAt = null,
            )
        }

        "session.tool.input.ended" -> {
            val id = event.data.string("id") ?: return state
            val input = parseInput(event.data.string("text"))
            val existing = state.items.firstOrNull { it.id == id } as? TranscriptItem.Tool
            upsertTool(
                state = state,
                id = id,
                name = existing?.name ?: "tool",
                input = input,
                metadata = null,
                status = existing?.status ?: ToolStatus.PENDING,
                output = null,
                startedAt = event.created,
                endedAt = null,
            )
        }

        "session.tool.called" -> {
            val id = event.data.string("id") ?: return state
            upsertTool(
                state = state,
                id = id,
                name = event.data.string("name") ?: toolName(state, id) ?: "tool",
                input = event.data.objectAt("input"),
                metadata = null,
                status = ToolStatus.RUNNING,
                output = null,
                startedAt = event.created,
                endedAt = null,
            )
        }

        "session.tool.progress" -> {
            val id = event.data.string("id") ?: return state
            val existing = state.items.firstOrNull { it.id == id } as? TranscriptItem.Tool ?: return state
            upsertTool(
                state = state,
                id = id,
                name = existing.name,
                input = existing.input,
                metadata = event.data.objectAt("metadata"),
                status = existing.status,
                output = null,
                startedAt = event.created,
                endedAt = null,
            )
        }

        "session.tool.success" -> {
            val id = event.data.string("id") ?: return state
            upsertTool(
                state = state,
                id = id,
                name = toolName(state, id) ?: "tool",
                input = event.data.objectAt("input"),
                metadata = event.data.objectAt("metadata"),
                status = ToolStatus.SUCCESS,
                output = contentText(event.data.arrayAt("content")),
                startedAt = event.created,
                endedAt = event.created,
            )
        }

        "session.tool.failed" -> {
            val id = event.data.string("id") ?: return state
            val message = event.data.objectAt("error")?.string("message")
            upsertTool(
                state = state,
                id = id,
                name = toolName(state, id) ?: "tool",
                input = event.data.objectAt("input"),
                metadata = event.data.objectAt("metadata"),
                status = ToolStatus.FAILED,
                output = message ?: contentText(event.data.arrayAt("content")),
                startedAt = event.created,
                endedAt = event.created,
            )
        }

        "session.inbox.enqueued" -> {
            val inboxId = event.data.string("inboxID") ?: return state
            val item = event.data.objectAt("item")
            if (item?.string("type") != "user") return state
            if (state.items.any { it.id == inboxId }) return state
            val text = item.objectAt("payload")?.string("text") ?: ""
            state.copy(items = state.items + TranscriptItem.User(inboxId, text, event.created, queued = true))
        }

        "session.inbox.delivered" -> {
            val inboxId = event.data.string("inboxID") ?: return state
            updateUser(state, inboxId) { it.copy(queued = false) }
        }

        "session.inbox.cancelled" -> {
            val inboxId = event.data.string("inboxID") ?: return state
            state.copy(items = state.items.filterNot { it is TranscriptItem.User && it.id == inboxId })
        }

        "session.synthetic" -> {
            val text = event.data.string("text") ?: return state
            val id = "notice:${event.id.ifBlank { event.created.toString() }}"
            state.copy(items = replaceOrAppend(state.items, TranscriptItem.Notice(id, text)))
        }

        "permission.asked" -> {
            val sessionId = event.data.string("sessionID") ?: ""
            val request = mapPermission(event.data, sessionId, event.created) ?: return state
            val id = "perm:${request.id}"
            state.copy(
                items = replaceOrAppend(state.items, TranscriptItem.Permission(id, request)),
                pendingPermissions = state.pendingPermissions + (request.id to request),
            )
        }

        "permission.replied" -> {
            val requestId = event.data.string("requestID") ?: return state
            state.copy(
                items = state.items.filterNot {
                    it is TranscriptItem.Permission && it.request.id == requestId
                },
                pendingPermissions = state.pendingPermissions - requestId,
            )
        }

        "form.created" -> {
            val json = event.data.objectAt("form") ?: return state
            val sessionId = event.data.string("sessionID") ?: json.string("sessionID") ?: ""
            val form = mapForm(json, sessionId, event.created) ?: return state
            val id = "form:${form.id}"
            state.copy(
                items = replaceOrAppend(state.items, TranscriptItem.Form(id, form)),
                pendingForms = state.pendingForms + (form.id to form),
            )
        }

        "form.replied", "form.cancelled" -> {
            val formId = event.data.string("id") ?: return state
            state.copy(
                items = state.items.filterNot { it is TranscriptItem.Form && it.form.id == formId },
                pendingForms = state.pendingForms - formId,
            )
        }

        else -> state
    }

    /**
     * Merge a freshly fetched history snapshot into live state. Live rows that arrived after the
     * snapshot win for streaming text; history wins for completed rows. RunEnd rows are deduplicated
     * because the live run-end and the persisted idle message describe the same event.
     */
    fun mergeHistory(state: TranscriptState, history: List<TranscriptItem>): TranscriptState {
        if (history.isEmpty()) return state
        val existing = state.items.associateBy { it.id }
        val result = ArrayList<TranscriptItem>(history.size + state.items.size)
        history.forEach { item ->
            val live = existing[item.id]
            result += if (live == null) item else preferMoreComplete(live, item)
        }
        val historyIds = history.mapTo(HashSet()) { it.id }
        state.items.forEach { item ->
            if (item.id in historyIds) return@forEach
            if (item is TranscriptItem.RunEnd && result.any {
                    it is TranscriptItem.RunEnd && it.outcome == item.outcome && near(it.time, item.time)
                }
            ) {
                return@forEach
            }
            result += item
        }
        val lastEnd = result.lastOrNull { it is TranscriptItem.RunEnd } as? TranscriptItem.RunEnd
        return state.copy(
            items = result,
            lastOutcome = lastEnd?.outcome ?: state.lastOutcome,
            error = if (lastEnd == null) state.error else lastEnd.error,
        )
    }

    private fun withRunEnd(
        state: TranscriptState,
        outcome: RunState,
        time: Long,
        error: String?,
        eventId: String,
    ): TranscriptState {
        val id = "runend:${eventId.ifBlank { time.toString() }}"
        val already = state.items.any {
            it is TranscriptItem.RunEnd && it.outcome == outcome && near(it.time, time)
        }
        val items = if (already) state.items else state.items + TranscriptItem.RunEnd(id, outcome, time, error)
        return state.copy(items = items, lastOutcome = outcome, error = error, runActive = false, runStartedAt = null)
    }

    private fun upsertText(
        state: TranscriptState,
        event: AgentEvent,
        delta: String,
        streaming: Boolean,
        authoritative: Boolean = false,
    ): TranscriptState {
        val messageId = event.data.string("assistantMessageID") ?: return state
        val ordinal = event.data.long("ordinal") ?: 0L
        val id = "text:$messageId:$ordinal"
        val previous = state.items.firstOrNull { it.id == id } as? TranscriptItem.AssistantText
        val text = when {
            authoritative -> delta
            streaming -> (previous?.text ?: "") + delta
            else -> delta
        }
        val item = TranscriptItem.AssistantText(id, text, streaming)
        return state.copy(items = replaceOrAppend(state.items, item))
    }

    private fun upsertReasoning(
        state: TranscriptState,
        event: AgentEvent,
        delta: String,
        streaming: Boolean,
        authoritative: Boolean = false,
    ): TranscriptState {
        val messageId = event.data.string("assistantMessageID") ?: return state
        val ordinal = event.data.long("ordinal") ?: 0L
        val id = "reason:$messageId:$ordinal"
        val previous = state.items.firstOrNull { it.id == id } as? TranscriptItem.Reasoning
        val text = when {
            authoritative -> delta
            streaming -> (previous?.text ?: "") + delta
            else -> delta
        }
        val item = TranscriptItem.Reasoning(id, text, streaming, previous?.durationMs)
        return state.copy(items = replaceOrAppend(state.items, item))
    }

    private fun upsertTool(
        state: TranscriptState,
        id: String,
        name: String,
        input: JsonObject?,
        metadata: JsonObject?,
        status: ToolStatus,
        output: String?,
        startedAt: Long?,
        endedAt: Long?,
    ): TranscriptState {
        val previous = state.items.firstOrNull { it.id == id } as? TranscriptItem.Tool
        val toolName = name.ifBlank { previous?.name ?: "tool" }
        val display = toolDisplay(toolName, input ?: previous?.input, metadata)
        val mergedStatus = mergeStatus(previous?.status, status)
        val item = TranscriptItem.Tool(
            id = id,
            name = toolName,
            verb = display.verb,
            subject = display.subject.ifBlank { previous?.subject ?: "" },
            status = mergedStatus,
            input = input ?: previous?.input,
            output = output ?: previous?.output,
            startedAt = previous?.startedAt ?: startedAt,
            endedAt = if (mergedStatus == ToolStatus.SUCCESS || mergedStatus == ToolStatus.FAILED) {
                endedAt ?: previous?.endedAt ?: startedAt
            } else {
                previous?.endedAt
            },
            childSessionId = display.childSessionId ?: previous?.childSessionId,
        )
        return state.copy(items = replaceOrAppend(state.items, item))
    }

    private fun mergeStatus(previous: ToolStatus?, next: ToolStatus): ToolStatus {
        if (previous == null) return next
        val previousDone = previous == ToolStatus.SUCCESS || previous == ToolStatus.FAILED
        val nextDone = next == ToolStatus.SUCCESS || next == ToolStatus.FAILED
        return when {
            previousDone && !nextDone -> previous
            !previousDone && nextDone -> next
            statusRank(next) >= statusRank(previous) -> next
            else -> previous
        }
    }

    private fun statusRank(status: ToolStatus): Int = when (status) {
        ToolStatus.PENDING -> 0
        ToolStatus.RUNNING -> 1
        ToolStatus.SUCCESS, ToolStatus.FAILED -> 2
    }

    private fun preferMoreComplete(live: TranscriptItem, history: TranscriptItem): TranscriptItem = when {
        live is TranscriptItem.AssistantText && history is TranscriptItem.AssistantText -> {
            when {
                !live.streaming -> live
                !history.streaming -> history
                history.text.length >= live.text.length -> history
                else -> live
            }
        }

        live is TranscriptItem.Reasoning && history is TranscriptItem.Reasoning -> {
            when {
                !live.streaming -> live
                !history.streaming -> history
                history.text.length >= live.text.length -> history
                else -> live
            }
        }

        live is TranscriptItem.Tool && history is TranscriptItem.Tool -> {
            val liveDone = live.status == ToolStatus.SUCCESS || live.status == ToolStatus.FAILED
            val historyDone = history.status == ToolStatus.SUCCESS || history.status == ToolStatus.FAILED
            when {
                liveDone && !historyDone -> live
                !liveDone && historyDone -> history
                liveDone && historyDone -> if ((live.output?.length ?: 0) >= (history.output?.length ?: 0)) live else history
                else -> live
            }
        }

        live is TranscriptItem.User && history is TranscriptItem.User -> history
        live is TranscriptItem.RunEnd && history is TranscriptItem.RunEnd -> history
        else -> live
    }

    private fun toolName(state: TranscriptState, id: String): String? =
        (state.items.firstOrNull { it.id == id } as? TranscriptItem.Tool)?.name

    private fun updateUser(
        state: TranscriptState,
        id: String,
        transform: (TranscriptItem.User) -> TranscriptItem.User,
    ): TranscriptState {
        val index = state.items.indexOfFirst { it is TranscriptItem.User && it.id == id }
        if (index < 0) return state
        val items = state.items.toMutableList()
        items[index] = transform(items[index] as TranscriptItem.User)
        return state.copy(items = items)
    }

    private fun parseInput(text: String?): JsonObject? {
        if (text.isNullOrBlank()) return null
        return runCatching { ApiJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

    private fun contentText(content: JsonArray?): String? {
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

    private fun replaceOrAppend(
        items: List<TranscriptItem>,
        item: TranscriptItem,
    ): List<TranscriptItem> {
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) return items + item
        val copy = items.toMutableList()
        copy[index] = item
        return copy
    }
}

// ---------------------------------------------------------------------------
// session-list reducer
// ---------------------------------------------------------------------------

/**
 * Applies one event to the session list row. Returns null when the event does not change it.
 * Run status/activity are overridden from [TranscriptState] by the repository, but updatedAt,
 * unread and lifecycle metadata live here.
 */
internal fun applySessionEvent(session: AgentSession, event: AgentEvent): AgentSession? = when (event.type) {
    "session.renamed" -> event.data.string("title")?.let {
        session.copy(title = it, updatedAt = max(session.updatedAt, event.created))
    }

    "session.model.selected" -> mapModelRef(event.data.objectAt("model"))?.let {
        session.copy(model = it, updatedAt = max(session.updatedAt, event.created))
    }

    "session.agent.selected" -> event.data.string("agent")?.let {
        session.copy(agent = it, updatedAt = max(session.updatedAt, event.created))
    }

    "session.moved" -> {
        val directory = event.data.objectAt("location")?.string("directory")
        if (directory == null) null else session.copy(directory = directory)
    }

    "session.usage.updated" -> {
        val tokens = event.data.objectAt("tokens")
        session.copy(
            cost = event.data.double("cost") ?: session.cost,
            tokensIn = tokens?.long("input") ?: session.tokensIn,
            tokensOut = tokens?.long("output") ?: session.tokensOut,
        )
    }

    "session.viewed" -> session.copy(unread = false)

    "session.execution.started" -> session.copy(
        state = RunState.RUNNING,
        runStartedAt = event.created,
        activity = "Thinking…",
        error = null,
        unread = false,
        updatedAt = max(session.updatedAt, event.created),
    )

    "session.execution.succeeded" -> session.copy(
        state = RunState.DONE,
        runStartedAt = null,
        activity = null,
        error = null,
        unread = true,
        updatedAt = max(session.updatedAt, event.created),
    )

    "session.execution.failed" -> session.copy(
        state = RunState.FAILED,
        runStartedAt = null,
        activity = null,
        error = event.data.objectAt("error")?.string("message") ?: "Run failed",
        unread = true,
        updatedAt = max(session.updatedAt, event.created),
    )

    "session.execution.interrupted" -> session.copy(
        state = RunState.IDLE,
        runStartedAt = null,
        activity = null,
        updatedAt = max(session.updatedAt, event.created),
    )

    "session.idle" -> if (session.state == RunState.RUNNING) {
        session.copy(state = RunState.IDLE, runStartedAt = null, activity = null)
    } else {
        null
    }

    "SessionStatusUpdated" -> {
        when (event.data.objectAt("status")?.string("type")) {
            "busy" -> session.copy(
                state = RunState.RUNNING,
                runStartedAt = session.runStartedAt ?: event.created,
                activity = session.activity ?: "Thinking…",
                unread = false,
                updatedAt = max(session.updatedAt, event.created),
            )
            "idle" -> if (session.state == RunState.RUNNING) {
                session.copy(state = RunState.IDLE, runStartedAt = null, activity = null)
            } else {
                null
            }
            else -> null
        }
    }

    else -> if (isActivityEvent(event.type)) {
        session.copy(updatedAt = max(session.updatedAt, event.created))
    } else {
        null
    }
}

private fun isActivityEvent(type: String): Boolean =
    type.startsWith("session.text.") ||
        type.startsWith("session.reasoning.") ||
        type.startsWith("session.tool.") ||
        type.startsWith("session.step.") ||
        type == "session.inbox.enqueued" ||
        type == "session.inbox.delivered" ||
        type == "permission.asked" ||
        type == "permission.replied" ||
        type == "form.created" ||
        type == "form.replied" ||
        type == "form.cancelled"

internal fun eventSessionId(event: AgentEvent): String? = when (event.type) {
    "form.created" -> event.data.objectAt("form")?.string("sessionID")
        ?: event.data.string("sessionID")
    else -> event.data.string("sessionID") ?: event.data.string("sessionId")
}
