package app.parallax.opencode.ui.preview

import app.parallax.opencode.core.model.AgentOption
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.BackendKind
import app.parallax.opencode.core.model.ConnectionState
import app.parallax.opencode.core.model.FormField
import app.parallax.opencode.core.model.FormRequest
import app.parallax.opencode.core.model.InboxItem
import app.parallax.opencode.core.model.McpServerInfo
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.ModelRef
import app.parallax.opencode.core.model.ParallelLaunch
import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.model.PermissionRequest
import app.parallax.opencode.core.model.ProjectInfo
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.model.ToolStatus
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.model.UsageSummary
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * In-memory AgentRepository with realistic demo data for previews and offline UI work.
 * One server, sessions in every RunState (3 forming a parallel "Refactor auth" launch),
 * rich transcripts, and a coroutine that streams text deltas into the research session.
 */
class FakeAgentRepository : AgentRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val now = System.currentTimeMillis()
    private val min = 60_000L

    private val demoServer = ServerConfig(
        id = "srv-home",
        name = "homelab",
        kind = BackendKind.OPENCODE,
        baseUrl = "http://192.168.1.20:4096",
    )

    private val goFlash = ModelRef("opencode-go", "deepseek-v4.1-flash")
    private val goSpark = ModelRef("opencode-go", "muse-spark-1.3-contributor")
    private val claude = ModelRef("anthropic", "claude-opus-4-6")
    private val gpt = ModelRef("openai", "gpt-5-2")

    override val servers: StateFlow<List<ServerConfig>> =
        MutableStateFlow(listOf(demoServer))
    override val activeServer: StateFlow<ServerConfig?> =
        MutableStateFlow(demoServer)
    override val connection: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Connected("2.2.6"))

    private val _sessions = MutableStateFlow(
        listOf(
            AgentSession(
                id = "ses-ref-flash", serverId = "srv-home", title = "Refactor auth · deepseek",
                directory = "/home/you/parallax", parentId = null, model = goFlash, agent = "build",
                state = RunState.RUNNING, activity = "Running `npm test`",
                createdAt = now - 4 * min, updatedAt = now - 20_000, runStartedAt = now - 84_000,
                cost = 0.031, tokensIn = 12_400, tokensOut = 3_100, pendingRequests = 0,
                childCount = 0, unread = false,
            ),
            AgentSession(
                id = "ses-ref-spark", serverId = "srv-home", title = "Refactor auth · muse-spark",
                directory = "/home/you/parallax", parentId = null, model = goSpark, agent = "build",
                state = RunState.RUNNING, activity = "Editing src/auth/session.ts",
                createdAt = now - 4 * min + 3_000, updatedAt = now - 12_000, runStartedAt = now - 81_000,
                cost = 0.042, tokensIn = 15_200, tokensOut = 4_400, pendingRequests = 0,
                childCount = 0, unread = false,
            ),
            AgentSession(
                id = "ses-ref-gpt", serverId = "srv-home", title = "Refactor auth · gpt",
                directory = "/home/you/parallax", parentId = null, model = gpt, agent = "build",
                state = RunState.NEEDS_YOU, activity = null,
                createdAt = now - 4 * min + 6_000, updatedAt = now - 2 * min, runStartedAt = now - 78_000,
                cost = 0.11, tokensIn = 21_000, tokensOut = 5_900, pendingRequests = 1,
                childCount = 0, unread = true,
            ),
            AgentSession(
                id = "ses-dark", serverId = "srv-home", title = "Add dark mode",
                directory = "/home/you/parallax", parentId = null, model = claude, agent = "build",
                state = RunState.NEEDS_YOU, activity = null,
                createdAt = now - 42 * min, updatedAt = now - 6 * min, runStartedAt = now - 6 * min,
                cost = 0.08, tokensIn = 18_000, tokensOut = 4_100, pendingRequests = 1,
                childCount = 0, unread = true,
            ),
            AgentSession(
                id = "ses-research", serverId = "srv-home", title = "Wide research",
                directory = "/home/you/parallax", parentId = null, model = goSpark, agent = "explore",
                state = RunState.RUNNING, activity = "Searched 4 sources",
                createdAt = now - 26 * min, updatedAt = now - 8_000, runStartedAt = now - 133_000,
                cost = 0.02, tokensIn = 30_000, tokensOut = 2_200, pendingRequests = 0,
                childCount = 2, unread = false,
            ),
            AgentSession(
                id = "ses-tests", serverId = "srv-home", title = "Fix flaky tests",
                directory = "/home/you/parallax", parentId = null, model = goFlash, agent = "build",
                state = RunState.FAILED, activity = null,
                createdAt = now - 3 * 60 * min, updatedAt = now - 90 * min, runStartedAt = null,
                cost = 0.05, tokensIn = 9_000, tokensOut = 2_800, pendingRequests = 0,
                childCount = 0, unread = true, error = "Process exited with code 1: 3 tests failed in auth.test.ts",
            ),
            AgentSession(
                id = "ses-changelog", serverId = "srv-home", title = "Write changelog",
                directory = "/home/you/parallax", parentId = null, model = claude, agent = "plan",
                state = RunState.DONE, activity = null,
                createdAt = now - 5 * 60 * min, updatedAt = now - 4 * 60 * min, runStartedAt = null,
                cost = 0.012, tokensIn = 6_000, tokensOut = 1_100, pendingRequests = 0,
                childCount = 0, unread = true,
            ),
            AgentSession(
                id = "ses-migrate", serverId = "srv-home", title = "Migrate to Room",
                directory = "/home/you/parallax", parentId = null, model = gpt, agent = "plan",
                state = RunState.IDLE, activity = null,
                createdAt = now - 26 * 60 * min, updatedAt = now - 25 * 60 * min, runStartedAt = null,
                cost = 0.0, tokensIn = 0, tokensOut = 0, pendingRequests = 0,
                childCount = 0, unread = false,
            ),
            AgentSession(
                id = "ses-child-1", serverId = "srv-home", title = "Explore SSE mapper",
                directory = "/home/you/parallax", parentId = "ses-research", model = goFlash, agent = "explore",
                state = RunState.IDLE, activity = null,
                createdAt = now - 24 * min, updatedAt = now - 20 * min, runStartedAt = null,
                cost = 0.004, tokensIn = 4_000, tokensOut = 600, pendingRequests = 0,
                childCount = 0, unread = false,
            ),
        ),
    )
    override val sessions: StateFlow<List<AgentSession>> = _sessions.asStateFlow()

    private val _inbox = MutableStateFlow(
        listOf(
            InboxItem("inb-1", "srv-home", "ses-ref-gpt", "Refactor auth · gpt", InboxItem.Kind.PERMISSION, "Permission: run tests?", "npm test -- --coverage", now - 2 * min, "perm-gpt-1"),
            InboxItem("inb-2", "srv-home", "ses-dark", "Add dark mode", InboxItem.Kind.PERMISSION, "Permission: edit?", "src/theme/tokens.kt", now - 6 * min, "perm-dark-1"),
            InboxItem("inb-3", "srv-home", "ses-dark", "Add dark mode", InboxItem.Kind.FORM, "Choose a palette", "Pick the dark background style", now - 9 * min, "form-dark-1"),
            InboxItem("inb-4", "srv-home", "ses-tests", "Fix flaky tests", InboxItem.Kind.FAILED, "Run failed", "3 tests failed in auth.test.ts", now - 90 * min, null),
            InboxItem("inb-5", "srv-home", "ses-changelog", "Write changelog", InboxItem.Kind.DONE, "Finished", "Done in 2:13 · $0.01", now - 4 * 60 * min, null),
        ),
    )
    override val inbox: StateFlow<List<InboxItem>> = _inbox.asStateFlow()

    private val transcripts = mutableMapOf<String, MutableStateFlow<List<TranscriptItem>>>()

    private fun flowFor(id: String): MutableStateFlow<List<TranscriptItem>> =
        transcripts.getOrPut(id) { MutableStateFlow(buildTranscript(id)) }

    override fun transcript(sessionId: String): Flow<List<TranscriptItem>> = flowFor(sessionId)

    init {
        scope.launch {
            val id = "ses-research"
            val chunks = listOf(
                "Here's", " what I found", " across the codebase:\n\n",
                "- **SSE parsing** lives in `EventSource.kt`", " with a 1s→30s backoff.\n",
                "- Sessions re-fetch on reconnect", " to avoid missed events.\n\n",
                "Next I'll check the mapper for dropped deltas.",
            )
            // Wait for first collector, then stream.
            delay(1200)
            chunks.forEach { chunk ->
                delay(900)
                val f = transcripts[id] ?: return@forEach
                val cur = f.value.toMutableList()
                val last = cur.lastOrNull()
                if (last is TranscriptItem.AssistantText && last.streaming) {
                    cur[cur.lastIndex] = last.copy(text = last.text + chunk)
                } else {
                    cur.add(TranscriptItem.AssistantText("stream-${UUID.randomUUID()}", chunk, streaming = true))
                }
                f.value = cur
            }
        }
    }

    private fun perm(id: String, session: String, action: String, resources: List<String>, msg: String?, at: Long) =
        TranscriptItem.Permission("p-$id", PermissionRequest(id, session, action, resources, msg, at))

    private fun buildTranscript(id: String): List<TranscriptItem> {
        val t0 = now - 30 * min
        val answer = """
            ## Plan

            I'll refactor the session handling into three small steps:

            1. Extract a `SessionStore` from the activity
            2. Move token refresh into `ServerAuth`
            3. Add a regression test for reconnects

            ```kotlin
            fun refresh(token: String): String = client.refresh(token)
            ```

            Starting with the store now.
        """.trimIndent()
        return when (id) {
            "ses-ref-gpt" -> listOf(
                TranscriptItem.User("u1", "Refactor the auth flow, keep it small", t0),
                TranscriptItem.AssistantText("a1", answer, streaming = false),
                TranscriptItem.Tool(
                    "t1", "shell", "Ran", "npm test -- --coverage", ToolStatus.SUCCESS,
                    buildJsonObject { put("command", "npm test -- --coverage") },
                    "PASS  auth.test.ts (12 tests)\nCoverage: 91%", t0 + 60_000, t0 + 95_000,
                ),
                perm("perm-gpt-1", id, "bash", listOf("npm run migrate -- --all"), "Needs DB access to continue.", now - 2 * min),
            )
            "ses-dark" -> listOf(
                TranscriptItem.User("u1", "Add a dark mode toggle to settings", t0),
                TranscriptItem.Reasoning("r1", "The theme already supports dark tokens; I only need a toggle wired to ServerStore.setTheme and a check that contrast stays legible in both themes.", streaming = false, durationMs = 6_000),
                TranscriptItem.Tool(
                    "t1", "read", "Read", "src/theme/tokens.kt", ToolStatus.SUCCESS,
                    buildJsonObject { put("path", "src/theme/tokens.kt") },
                    "112 lines read", t0 + 30_000, t0 + 31_000,
                ),
                perm("perm-dark-1", id, "edit", listOf("src/theme/tokens.kt", "src/screens/Settings.kt"), null, now - 6 * min),
                TranscriptItem.Form(
                    "f-dark-1",
                    FormRequest(
                        "form-dark-1", id, "Choose a palette",
                        listOf(
                            FormField("bg", "Background style", FormField.FieldType.SINGLE_CHOICE, options = listOf("Near-black", "Charcoal", "Navy"), required = true),
                            FormField("contrast", "High contrast text", FormField.FieldType.BOOLEAN, default = "true"),
                        ),
                        now - 9 * min,
                    ),
                ),
            )
            "ses-research" -> listOf(
                TranscriptItem.User("u1", "Research how our event stream handles reconnects", t0),
                TranscriptItem.Reasoning("r1", "Wide question — fan out to two subagents and read the mapper myself.", streaming = false, durationMs = 11_000),
                TranscriptItem.Tool(
                    "t1", "task", "Explored", "Explore SSE mapper", ToolStatus.SUCCESS,
                    buildJsonObject { put("prompt", "Explore SSE mapper") },
                    "2 files read, summary returned", t0 + 120_000, t0 + 200_000, childSessionId = "ses-child-1",
                ),
                TranscriptItem.Tool(
                    "t2", "mcp__github__search", "Searched", "sse reconnect backoff", ToolStatus.SUCCESS,
                    buildJsonObject { put("query", "sse reconnect backoff") },
                    "4 results", t0 + 210_000, t0 + 214_000,
                ),
                TranscriptItem.AssistantText("a-live", "", streaming = true),
            )
            "ses-ref-flash" -> listOf(
                TranscriptItem.User("u1", "Refactor the auth flow, keep it small", t0),
                TranscriptItem.AssistantText("a1", "On it — extracting the store first.", streaming = false),
                TranscriptItem.Tool(
                    "t1", "read", "Read", "src/auth/session.ts", ToolStatus.SUCCESS,
                    buildJsonObject { put("path", "src/auth/session.ts") },
                    "84 lines", t0 + 10_000, t0 + 11_000,
                ),
                TranscriptItem.Tool(
                    "t2", "edit", "Edited", "src/auth/session.ts", ToolStatus.SUCCESS,
                    buildJsonObject { put("path", "src/auth/session.ts") },
                    "+32 −18", t0 + 40_000, t0 + 44_000,
                ),
                TranscriptItem.Tool(
                    "t3", "shell", "Ran", "npm test", ToolStatus.RUNNING,
                    buildJsonObject { put("command", "npm test") },
                    null, now - 84_000, null,
                ),
            )
            "ses-ref-spark" -> listOf(
                TranscriptItem.User("u1", "Refactor the auth flow, keep it small", t0),
                TranscriptItem.Tool(
                    "t1", "read", "Read", "src/auth/session.ts", ToolStatus.SUCCESS,
                    buildJsonObject { put("path", "src/auth/session.ts") }, "84 lines",
                    t0 + 10_000, t0 + 11_000,
                ),
                TranscriptItem.Tool(
                    "t2", "edit", "Edited", "src/auth/store.ts", ToolStatus.RUNNING,
                    buildJsonObject { put("path", "src/auth/store.ts") }, null,
                    now - 40_000, null,
                ),
            )
            "ses-tests" -> listOf(
                TranscriptItem.User("u1", "Fix the flaky tests", t0),
                TranscriptItem.Tool(
                    "t1", "shell", "Ran", "npm test -- --retry", ToolStatus.FAILED,
                    buildJsonObject { put("command", "npm test -- --retry") },
                    "FAIL  auth.test.ts > refreshes before expiry\nAssertionError: expected 401 to be 200",
                    t0 + 60_000, t0 + 140_000,
                ),
                TranscriptItem.RunEnd("e1", RunState.FAILED, now - 90 * min, "Process exited with code 1: 3 tests failed in auth.test.ts"),
            )
            "ses-changelog" -> listOf(
                TranscriptItem.User("u1", "Write the changelog for 0.4", t0),
                TranscriptItem.AssistantText("a1", "Done — drafted **CHANGELOG.md** with highlights, fixes, and upgrade notes.", streaming = false),
                TranscriptItem.RunEnd("e1", RunState.DONE, now - 4 * 60 * min, null),
            )
            else -> listOf(TranscriptItem.Notice("n1", "No messages yet. Send the first prompt below."))
        }
    }

    // --- servers ---
    override suspend fun addServer(config: ServerConfig) {
        (servers as MutableStateFlow).update { it + config }
    }

    override suspend fun removeServer(serverId: String) {
        (servers as MutableStateFlow).update { it.filterNot { s -> s.id == serverId } }
    }

    override suspend fun refresh() { kotlinx.coroutines.delay(600) }

    override suspend fun setActiveServer(serverId: String) {
        val found = (servers as MutableStateFlow).value.find { it.id == serverId }
            ?: throw RepoException("Unknown server")
        (activeServer as MutableStateFlow).value = found
    }

    // --- sessions ---
    override suspend fun startSession(prompt: String, directory: String?, model: ModelRef?, agent: String?, title: String?): String {
        val id = "ses-${UUID.randomUUID().toString().take(6)}"
        val s = AgentSession(
            id = id, serverId = "srv-home", title = title ?: prompt.lineSequence().first().take(48),
            directory = directory ?: "/home/you/parallax", parentId = null, model = model ?: goFlash,
            agent = agent, state = RunState.RUNNING, activity = "Starting…",
            createdAt = now(), updatedAt = now(), runStartedAt = now(),
            cost = 0.0, tokensIn = 0, tokensOut = 0, pendingRequests = 0, childCount = 0, unread = false,
        )
        _sessions.update { listOf(s) + it }
        transcripts[id] = MutableStateFlow(listOf(TranscriptItem.User("u0", prompt, now())))
        return id
    }

    override suspend fun launchParallel(launch: ParallelLaunch): List<String> {
        val ids = launch.runs.mapIndexed { i, run ->
            val suffix = run.label ?: run.model?.modelId ?: "run-${i + 1}"
            startSession(launch.prompt, launch.directory, run.model, run.agent, "${launch.title ?: "Parallel run"} · $suffix")
        }
        return ids
    }

    override suspend fun sendPrompt(sessionId: String, text: String, queued: Boolean) {
        val f = flowFor(sessionId)
        f.value = f.value + TranscriptItem.User("u-${UUID.randomUUID()}", text, now(), queued)
    }

    override suspend fun cancelQueuedPrompt(sessionId: String, id: String) {
        flowFor(sessionId).update { items -> items.filterNot { it is TranscriptItem.User && it.queued && it.id == id } }
    }

    override suspend fun interrupt(sessionId: String) {
        updateSession(sessionId) { it.copy(state = RunState.IDLE, runStartedAt = null, activity = null) }
        val f = flowFor(sessionId)
        f.value = f.value + TranscriptItem.RunEnd("e-${UUID.randomUUID()}", RunState.IDLE, now(), null)
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        updateSession(sessionId) { it.copy(title = title, updatedAt = now()) }
    }

    override suspend fun deleteSession(sessionId: String) {
        _sessions.update { it.filterNot { s -> s.id == sessionId } }
        transcripts.remove(sessionId)
        _inbox.update { it.filterNot { item -> item.sessionId == sessionId } }
    }

    override suspend fun setSessionModel(sessionId: String, model: ModelRef) {
        updateSession(sessionId) { it.copy(model = model) }
    }

    override suspend fun markViewed(sessionId: String) {
        updateSession(sessionId) { it.copy(unread = false, state = if (it.state == RunState.DONE || it.state == RunState.FAILED) RunState.IDLE else it.state) }
        _inbox.update { it.filterNot { item -> item.sessionId == sessionId && (item.kind == InboxItem.Kind.DONE || item.kind == InboxItem.Kind.FAILED) } }
    }

    // --- requests ---
    override suspend fun replyPermission(sessionId: String, requestId: String, decision: PermissionDecision, message: String?) {
        val f = flowFor(sessionId)
        f.value = f.value.filterNot { it is TranscriptItem.Permission && it.request.id == requestId } +
            TranscriptItem.Notice("n-$requestId", "Permission ${decision.name.lowercase()}: $requestId")
        _inbox.update { it.filterNot { item -> item.requestId == requestId } }
        if (decision == PermissionDecision.REJECT) {
            updateSession(sessionId) { it.copy(pendingRequests = maxOf(0, it.pendingRequests - 1)) }
        } else {
            updateSession(sessionId) { it.copy(pendingRequests = maxOf(0, it.pendingRequests - 1), state = RunState.RUNNING, runStartedAt = now(), activity = "Continuing…") }
        }
    }

    override suspend fun replyForm(sessionId: String, formId: String, answers: Map<String, Any>) {
        val f = flowFor(sessionId)
        f.value = f.value.filterNot { it is TranscriptItem.Form && it.form.id == formId } +
            TranscriptItem.Notice("n-$formId", "Form submitted")
        _inbox.update { it.filterNot { item -> item.requestId == formId } }
        updateSession(sessionId) { it.copy(pendingRequests = maxOf(0, it.pendingRequests - 1)) }
    }

    override suspend fun cancelForm(sessionId: String, formId: String) {
        val f = flowFor(sessionId)
        f.value = f.value.filterNot { it is TranscriptItem.Form && it.form.id == formId }
        _inbox.update { it.filterNot { item -> item.requestId == formId } }
    }

    // --- catalog ---
    private val demoModels = listOf(
        ModelOption(goFlash, "DeepSeek V4.1 Flash", "opencode-go", true, 128_000),
        ModelOption(goSpark, "Muse Spark 1.3", "opencode-go", true, 200_000),
        ModelOption(claude, "Claude Opus 4.6", "anthropic", false, 200_000),
        ModelOption(gpt, "GPT 5.2", "openai", false, 400_000),
    )

    override suspend fun models(): List<ModelOption> = demoModels
    override suspend fun defaultModel(): ModelRef = goFlash

    override suspend fun agents(): List<AgentOption> = listOf(
        AgentOption("build", "build", "Full implementation agent", true),
        AgentOption("plan", "plan", "Research and planning", true),
        AgentOption("explore", "explore", "Fast codebase exploration", true),
        AgentOption("general", "general", "General purpose", false),
    )

    override suspend fun projects(): List<ProjectInfo> = listOf(
        ProjectInfo("p1", "parallax", "/home/you/parallax", now - 2 * min),
        ProjectInfo("p2", "openchamber", "/home/you/openchamber", now - 5 * 60 * min),
        ProjectInfo("p3", "dotfiles", "/home/you/dotfiles", null),
    )

    override suspend fun mcpServers(): List<McpServerInfo> = listOf(
        McpServerInfo("github", McpServerInfo.McpStatus.CONNECTED, null, 12, "remote"),
        McpServerInfo("linear", McpServerInfo.McpStatus.FAILED, "auth expired", 0, "remote"),
        McpServerInfo("context7", McpServerInfo.McpStatus.NEEDS_AUTH, "OAuth required", null, "remote"),
        McpServerInfo("fetch", McpServerInfo.McpStatus.DISABLED, null, 3, "local"),
    )

    override suspend fun setMcpConnected(name: String, connected: Boolean) = Unit

    override suspend fun usage(): List<UsageSummary> = listOf(
        UsageSummary("This week", 0.62f, "31.2 / 50 hrs"),
        UsageSummary("Models", 0.35f, "7 / 20 providers"),
    )

    private fun now(): Long = System.currentTimeMillis()

    private fun updateSession(id: String, fn: (AgentSession) -> AgentSession) {
        _sessions.update { list -> list.map { if (it.id == id) fn(it) else it } }
    }
}
