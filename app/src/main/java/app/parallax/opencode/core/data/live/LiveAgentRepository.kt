package app.parallax.opencode.core.data.live

import android.content.Context
import app.parallax.opencode.core.data.ServerStore
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
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.model.UsageSummary
import app.parallax.opencode.core.net.ApiClient
import app.parallax.opencode.core.net.data
import app.parallax.opencode.core.net.friendlyNetworkError
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * The real repository: one active server, one SSE connection, REST for everything else.
 *
 * State layout:
 *  - [_baseSessions] is what the server's session list said, plus lifecycle/usage/unread edits.
 *  - [liveStates] is per-session run + transcript state fed by the reducer.
 *  - [sessions]/[inbox] combine the two, so a run update from SSE immediately changes the list.
 *
 * A server switch bumps [generation]; stale resync results and stale streams are ignored.
 */
class LiveAgentRepository(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val store: ServerStore,
    private val scope: CoroutineScope,
) : AgentRepository {

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.NoServer)
    private val _baseSessions = MutableStateFlow<List<AgentSession>>(emptyList())
    private val liveStates = ConcurrentHashMap<String, MutableStateFlow<TranscriptState>>()
    private val watching = ConcurrentHashMap<String, WatchRef>()
    private val revision = MutableStateFlow(0L)

    private var api: ApiClient? = null
    private var stream: EventStream? = null
    private var streamCollector: Job? = null
    private var streamEvents: Job? = null
    private var generation = 0

    private class WatchRef(var count: Int = 0, var loading: Job? = null)

    override val servers: StateFlow<List<ServerConfig>> =
        store.servers.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val activeServer: StateFlow<ServerConfig?> =
        combine(store.servers, store.activeServerId) { servers, activeId ->
            servers.firstOrNull { it.id == activeId } ?: servers.firstOrNull()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    override val sessions: StateFlow<List<AgentSession>> =
        combine(_baseSessions, revision) { base, _ -> overlaySessions(base) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val inbox: StateFlow<List<InboxItem>> =
        combine(_baseSessions, revision) { base, _ -> buildInbox(base) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            activeServer.collectLatest { server -> switchServer(server) }
        }
    }

    // -----------------------------------------------------------------------
    // servers
    // -----------------------------------------------------------------------

    override suspend fun addServer(config: ServerConfig) = io {
        store.upsert(config)
        Unit
    }

    override suspend fun removeServer(serverId: String) = io {
        store.remove(serverId)
        Unit
    }

    override suspend fun refresh() {
        val server = activeServer.value ?: return
        val client = api
        if (client == null || _connection.value !is ConnectionState.Connected) {
            scope.launch { switchServer(server) }.join()
        } else {
            io { resync(server, client) }
        }
    }

    override suspend fun setActiveServer(serverId: String) = io {
        store.setActive(serverId)
        Unit
    }

    // -----------------------------------------------------------------------
    // transcripts
    // -----------------------------------------------------------------------

    override fun transcript(sessionId: String): Flow<List<TranscriptItem>> = flow {
        val state = liveState(sessionId)
        var shouldLoad = false
        watching.compute(sessionId) { _, existing ->
            if (existing == null) {
                shouldLoad = true
                WatchRef(count = 1)
            } else {
                existing.count++
                existing
            }
        }
        if (shouldLoad) {
            val ref = watching[sessionId]
            ref?.loading = scope.launch {
                try {
                    val server = activeServer.filterNotNull().first()
                    loadHistory(sessionId, requireClient(), server.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RepoException) {
                    addNotice(sessionId, e.message ?: "Couldn't load the transcript.")
                } catch (e: Throwable) {
                    addNotice(sessionId, friendlyNetworkError(e))
                }
            }
            // The collector may have been cancelled between compute() and this assignment.
            if (watching[sessionId] !== ref) ref?.loading?.cancel()
        }
        try {
            emitAll(state.map { it.items }.distinctUntilChanged())
        } finally {
            watching.computeIfPresent(sessionId) { _, ref ->
                ref.count--
                if (ref.count <= 0) {
                    ref.loading?.cancel()
                    null
                } else {
                    ref
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // sessions
    // -----------------------------------------------------------------------

    override suspend fun startSession(
        prompt: String,
        directory: String?,
        model: ModelRef?,
        agent: String?,
        title: String?,
    ): String = io {
        val client = requireClient()
        val body = buildJsonObject {
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
            agent?.trim()?.takeIf { it.isNotEmpty() }?.let { put("agent", it) }
            model?.let { put("model", modelJson(it)) }
            // Tag sessions started here so the app can hide desktop/TUI history (stored server-side).
            putJsonObject("metadata") { put("parallax", true) }
            val dir = directory ?: activeServer.value?.defaultDirectory
            dir?.takeIf { it.isNotBlank() }?.let {
                putJsonObject("location") { put("directory", it) }
            }
        }
        val created = client.post("/api/session", body).data.asObjectOrNull()
            ?: throw RepoException("The server didn't return the new session.")
        val session = mapSession(created, activeServer.value?.id ?: "")
            ?: throw RepoException("The server didn't return the new session.")
        _baseSessions.update { list ->
            (list.filterNot { it.id == session.id } + session).sortedByDescending { it.updatedAt }
        }
        revision.value++
        postPrompt(client, session.id, prompt)
        session.id
    }

    override suspend fun launchParallel(launch: ParallelLaunch): List<String> = io {
        val base = launch.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: shortTitle(launch.prompt).ifBlank { "Parallel run" }
        coroutineScope {
            launch.runs.map { run ->
                async {
                    val suffix = run.label?.trim()?.takeIf { it.isNotEmpty() }
                        ?: run.model?.modelId
                        ?: run.agent
                        ?: "run"
                    startSession(
                        prompt = launch.prompt,
                        directory = launch.directory,
                        model = run.model,
                        agent = run.agent,
                        title = "$base · $suffix",
                    )
                }
            }.awaitAll()
        }
    }

    override suspend fun sendPrompt(sessionId: String, text: String, queued: Boolean) = io {
        postPrompt(requireClient(), sessionId, text, queued)
    }

    override suspend fun cancelQueuedPrompt(sessionId: String, id: String) = io {
        val client = requireClient()
        client.delete("/api/session/$sessionId/inbox/$id")
        if (client.server.id == activeServer.value?.id) liveState(sessionId).update { state ->
            state.copy(items = state.items.filterNot { it is TranscriptItem.User && it.queued && it.id == id },
                queueRevision = state.queueRevision + 1)
        }
    }

    override suspend fun interrupt(sessionId: String) = io {
        requireClient().post("/api/session/$sessionId/interrupt")
        Unit
    }

    override suspend fun renameSession(sessionId: String, title: String) = io {
        val clean = title.trim()
        requireClient().patch("/api/session/$sessionId", buildJsonObject { put("title", clean) })
        _baseSessions.update { list ->
            list.map { if (it.id == sessionId) it.copy(title = clean) else it }
        }
        revision.value++
        Unit
    }

    override suspend fun deleteSession(sessionId: String) = io {
        requireClient().delete("/api/session/$sessionId")
        _baseSessions.update { list -> list.filterNot { it.id == sessionId } }
        liveStates.remove(sessionId)
        watching.remove(sessionId)?.loading?.cancel()
        revision.value++
        Unit
    }

    override suspend fun setSessionModel(sessionId: String, model: ModelRef) = io {
        requireClient().post(
            "/api/session/$sessionId/model",
            buildJsonObject { put("model", modelJson(model)) },
        )
        _baseSessions.update { list ->
            list.map { if (it.id == sessionId) it.copy(model = model) else it }
        }
        revision.value++
        Unit
    }

    override suspend fun markViewed(sessionId: String) = io {
        requireClient().post(
            "/api/session/$sessionId/view",
            buildJsonObject { put("idle", System.currentTimeMillis()) },
        )
        _baseSessions.update { list ->
            list.map { if (it.id == sessionId) it.copy(unread = false) else it }
        }
        revision.value++
        Unit
    }

    // -----------------------------------------------------------------------
    // requests
    // -----------------------------------------------------------------------

    override suspend fun replyPermission(
        sessionId: String,
        requestId: String,
        decision: PermissionDecision,
        message: String?,
        serverId: String?,
    ) = io {
        val client = if (serverId == null) requireClient() else {
            val origin = store.servers.first().firstOrNull { it.id == serverId }
                ?: throw RepoException("This server is no longer saved.")
            ApiClient(origin)
        }
        client.post(
            "/api/session/$sessionId/permission/$requestId/reply",
            buildJsonObject {
                put("decision", decision.wire)
                message?.let { put("message", it) }
            },
        )
        if (activeServer.value?.id != client.server.id) return@io
        liveState(sessionId).update { state ->
            state.copy(
                items = state.items.filterNot {
                    it is TranscriptItem.Permission && it.request.id == requestId
                },
                pendingPermissions = state.pendingPermissions - requestId,
            )
        }
        revision.value++
        Unit
    }

    override suspend fun replyForm(
        sessionId: String,
        formId: String,
        answers: Map<String, Any>,
    ) = io {
        requireClient().post(
            "/api/session/$sessionId/form/$formId/reply",
            buildJsonObject {
                putJsonObject("answer") {
                    answers.forEach { (key, value) -> put(key, anyToJson(value)) }
                }
            },
        )
        clearForm(sessionId, formId)
    }

    override suspend fun cancelForm(sessionId: String, formId: String) = io {
        requireClient().delete("/api/session/$sessionId/form/$formId")
        clearForm(sessionId, formId)
    }

    // -----------------------------------------------------------------------
    // catalog
    // -----------------------------------------------------------------------

    override suspend fun models(): List<ModelOption> = io {
        val client = requireClient()
        val modelsJson = client.get("/api/model")
        val providers = try {
            client.get("/api/provider")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        mapModels(modelsJson.data, mapProviderNames(providers?.data))
    }

    override suspend fun defaultModel(): ModelRef? = io {
        mapModelRef(requireClient().get("/api/model/default").data.asObjectOrNull())
    }

    override suspend fun agents(): List<AgentOption> = io {
        mapAgents(requireClient().get("/api/agent").data)
    }

    override suspend fun projects(): List<ProjectInfo> = io {
        mapProjects(requireClient().get("/api/project").data)
    }

    override suspend fun mcpServers(): List<McpServerInfo> = io {
        mapMcpServers(requireClient().get("/api/mcp").data)
    }

    override suspend fun setMcpConnected(name: String, connected: Boolean) = io {
        val action = if (connected) "connect" else "disconnect"
        requireClient().post("/api/experimental/mcp/$name/$action")
        Unit
    }

    override suspend fun usage(): List<UsageSummary> = io {
        mapUsage(requireClient().get("/api/experimental/usage/limits").data)
    }

    // -----------------------------------------------------------------------
    // server lifecycle + resync
    // -----------------------------------------------------------------------

    private fun switchServer(server: ServerConfig?) {
        generation++
        streamCollector?.cancel()
        streamCollector = null
        streamEvents?.cancel()
        streamEvents = null
        stream?.stop()
        stream = null
        _baseSessions.value = emptyList()
        liveStates.clear()
        watching.values.forEach { it.loading?.cancel() }
        watching.clear()
        revision.value++

        if (server == null) {
            api = null
            _connection.value = ConnectionState.NoServer
            return
        }

        val gen = generation
        val client = ApiClient(server)
        api = client
        _connection.value = ConnectionState.Connecting

        val eventStream = EventStream(
            scope = scope,
            api = client,
            onResync = {
                if (gen == generation) resync(server, client) else null
            },
        )
        stream = eventStream
        streamCollector = scope.launch {
            eventStream.state.collect { state ->
                if (gen == generation) _connection.value = state
            }
        }
        streamEvents = scope.launch {
            eventStream.events.collect { event ->
                if (gen == generation) handleEvent(server.id, client, event)
            }
        }
        eventStream.start()
    }

    private suspend fun resync(server: ServerConfig, client: ApiClient): String? {
        val version = try {
            (client.get("/api/info") as? JsonObject)?.string("version")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        refreshSessions(client, server.id)
        try {
            refreshPending(client, server.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Pending-request routes are optional on older servers; keep the stream alive.
        }
        watching.keys.toList().forEach { sessionId ->
            try {
                loadHistory(sessionId, client, server.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                addNotice(sessionId, "Couldn't refresh this transcript after reconnecting.")
            }
        }
        return version
    }

    private suspend fun refreshSessions(client: ApiClient, serverId: String) {
        if (activeServer.value?.id != serverId) return
        val array = client.get("/api/session", mapOf("limit" to "200")).data.asArrayOrNull()
            ?: return
        val list = array.mapNotNull { item ->
            (item as? JsonObject)?.let { mapSession(it, serverId) }
        }
        _baseSessions.value = list.sortedByDescending { it.updatedAt }
        revision.value++
    }

    private suspend fun refreshPending(client: ApiClient, serverId: String) {
        if (activeServer.value?.id != serverId) return
        val now = System.currentTimeMillis()
        val directories = pendingDirectories()
        val (sawPermissions, permissionJson) = collectPending(client, "/api/permission/request", directories)
        val (sawForms, formJson) = collectPending(client, "/api/form", directories)
        if (!sawPermissions && !sawForms) return

        val permissions = permissionJson.mapNotNull { item ->
            (item as? JsonObject)?.let { json ->
                mapPermission(json, json.string("sessionID") ?: "", now)
            }
        }
        val forms = formJson.mapNotNull { item ->
            (item as? JsonObject)?.let { json ->
                mapForm(json, json.string("sessionID") ?: "", now)
            }
        }

        val bySessionPermissions = permissions.groupBy { it.sessionId }
        val bySessionForms = forms.groupBy { it.sessionId }
        val sessions = liveStates.keys + bySessionPermissions.keys + bySessionForms.keys
        sessions.forEach { sessionId ->
            val state = liveState(sessionId)
            state.update { current ->
                val oldPermissions = current.pendingPermissions
                val oldForms = current.pendingForms
                val newPermissions = bySessionPermissions[sessionId].orEmpty().associate { request ->
                    request.id to (oldPermissions[request.id]?.let { request.copy(createdAt = it.createdAt) } ?: request)
                }
                val newForms = bySessionForms[sessionId].orEmpty().associate { form ->
                    form.id to (oldForms[form.id]?.let { form.copy(createdAt = it.createdAt) } ?: form)
                }
                val items = current.items.filterNot {
                    it is TranscriptItem.Permission || it is TranscriptItem.Form
                }
                current.copy(
                    items = items +
                        newPermissions.values.map { TranscriptItem.Permission("perm:${it.id}", it) } +
                        newForms.values.map { TranscriptItem.Form("form:${it.id}", it) },
                    pendingPermissions = newPermissions,
                    pendingForms = newForms,
                )
            }
        }
        revision.value++
    }

    /**
     * Pending-request routes are scoped to one location unless a directory is supplied. Query the
     * default location plus the directories of sessions that are running or currently watched.
     */
    private fun pendingDirectories(): List<String> {
        val sessions = _baseSessions.value
        val running = sessions.filter {
            it.state == RunState.RUNNING || it.state == RunState.NEEDS_YOU
        }
        val watched = watching.keys.mapNotNull { id -> sessions.firstOrNull { it.id == id } }
        return (running + watched)
            .mapNotNull { it.directory?.takeIf(String::isNotBlank) }
            .distinct()
            .take(24)
    }

    private suspend fun collectPending(
        client: ApiClient,
        path: String,
        directories: List<String>,
    ): Pair<Boolean, List<JsonElement>> {
        val items = ArrayList<JsonElement>()
        var sawSuccess = false

        suspend fun fetch(query: Map<String, String?>) {
            try {
                client.get(path, query).data.asArrayOrNull()?.let { items.addAll(it) }
                sawSuccess = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Try the remaining locations; this route is optional on older servers.
            }
        }

        fetch(emptyMap())
        directories.forEach { directory ->
            fetch(mapOf("location[directory]" to directory))
        }
        return sawSuccess to items
    }

    private suspend fun loadHistory(sessionId: String, client: ApiClient, serverId: String) {
        val array = client.get(
            "/api/session/$sessionId/message",
            mapOf("limit" to "200"),
        ).data.asArrayOrNull() ?: return
        val messages = array.mapNotNull { it as? JsonObject }.reversed()
        val items = mapMessages(messages)
        if (activeServer.value?.id != serverId) return
        val flow = liveState(sessionId)
        flow.update { EventReducer.mergeHistory(it, items) }
        revision.value++
        // A delivery or cancellation during this GET supersedes its snapshot.
        while (activeServer.value?.id == serverId) {
            val version = flow.value.queueRevision
            val inbox = client.get("/api/session/$sessionId/inbox").data.asArrayOrNull() ?: return
            val pending = inbox.mapNotNull { raw ->
                val item = raw.asObjectOrNull() ?: return@mapNotNull null
                if (item.string("type") != "user") return@mapNotNull null
                TranscriptItem.User(item.string("id") ?: return@mapNotNull null,
                    item.objectAt("payload")?.string("text").orEmpty(), item.objectAt("time")?.long("created") ?: 0, true)
            }
            flow.update { state ->
                if (state.queueRevision != version) state else {
                    val ids = pending.map { it.id }.toSet()
                    state.copy(items = state.items.filterNot { it is TranscriptItem.User && (it.queued || it.id in ids) } + pending)
                }
            }
            if (flow.value.queueRevision == version) break
        }
    }

    private suspend fun handleEvent(serverId: String, client: ApiClient, event: AgentEvent) {
        if (activeServer.value?.id != serverId) return
        if (event.type == "server.connected") return

        val sessionId = eventSessionId(event)
        if (sessionId != null) {
            applySessionEvent(sessionId, event)
            if (EventReducer.isTranscriptEvent(event.type)) {
                liveState(sessionId).update {
                    val next = EventReducer.apply(it, event)
                    if (event.type.startsWith("session.inbox.")) next.copy(queueRevision = it.queueRevision + 1) else next
                }
            }
            revision.value++
        }

        when (event.type) {
            "session.created" -> addCreatedSession(event, serverId)
            "session.deleted" -> {
                if (sessionId != null) {
                    _baseSessions.update { list -> list.filterNot { it.id == sessionId } }
                    liveStates.remove(sessionId)
                    watching.remove(sessionId)?.loading?.cancel()
                    revision.value++
                }
            }
            "session.renamed" -> {
                if (event.data.string("title") == null) {
                    refreshSessions(client, serverId)
                }
            }
        }

        if (sessionId != null &&
            event.type in setOf("session.execution.started", "session.inbox.enqueued") &&
            _baseSessions.value.none { it.id == sessionId }
        ) {
            refreshSessions(client, serverId)
        }
    }

    private fun applySessionEvent(sessionId: String, event: AgentEvent) {
        _baseSessions.update { list ->
            var changed = false
            val next = list.map { session ->
                if (session.id != sessionId) {
                    session
                } else {
                    val updated = applySessionEvent(session, event)
                    if (updated == null) {
                        session
                    } else {
                        changed = true
                        updated
                    }
                }
            }
            if (changed) next else list
        }
    }

    private fun addCreatedSession(event: AgentEvent, serverId: String) {
        val session = mapSession(event.data, serverId, event.created) ?: return
        _baseSessions.update { list ->
            (list.filterNot { it.id == session.id } + session).sortedByDescending { it.updatedAt }
        }
        revision.value++
    }

    private fun liveState(sessionId: String): MutableStateFlow<TranscriptState> =
        liveStates.computeIfAbsent(sessionId) { MutableStateFlow(TranscriptState()) }

    private fun clearForm(sessionId: String, formId: String) {
        liveState(sessionId).update { state ->
            state.copy(
                items = state.items.filterNot { it is TranscriptItem.Form && it.form.id == formId },
                pendingForms = state.pendingForms - formId,
            )
        }
        revision.value++
    }

    private fun addNotice(sessionId: String, text: String) {
        val id = "notice:history:$sessionId"
        liveState(sessionId).update { state ->
            state.copy(
                items = state.items.filterNot { it.id == id } +
                    TranscriptItem.Notice(id, text, TranscriptItem.Notice.Tone.ERROR),
            )
        }
        revision.value++
    }

    // -----------------------------------------------------------------------
    // derived state
    // -----------------------------------------------------------------------

    private fun overlaySessions(base: List<AgentSession>): List<AgentSession> {
        val childCounts = base.groupingBy { it.parentId }.eachCount()
        return base.map { session ->
            val live = liveStates[session.id]?.value
            val merged = if (live == null) {
                session
            } else {
                val liveRunState = live.state
                val state = when {
                    liveRunState != RunState.IDLE -> liveRunState
                    live.runActive -> RunState.RUNNING
                    else -> session.state
                }
                val error = when {
                    state == RunState.DONE -> null
                    live.error != null -> live.error
                    else -> session.error
                }
                session.copy(
                    state = state,
                    activity = live.activity ?: if (state == RunState.RUNNING) session.activity else null,
                    runStartedAt = if (state == RunState.RUNNING) {
                        live.runStartedAt ?: session.runStartedAt
                    } else {
                        null
                    },
                    error = error,
                    pendingRequests = live.pendingCount,
                )
            }
            merged.copy(childCount = childCounts[session.id] ?: 0)
        }.sortedByDescending { it.updatedAt }
    }

    private fun buildInbox(base: List<AgentSession>): List<InboxItem> {
        val now = System.currentTimeMillis()
        val titles = base.associate { it.id to it.title }
        val serverIds = base.associate { it.id to it.serverId }
        val fallbackServerId = activeServer.value?.id ?: base.firstOrNull()?.serverId ?: ""
        val items = ArrayList<InboxItem>()

        liveStates.forEach { (sessionId, stateFlow) ->
            val state = stateFlow.value
            val sessionTitle = titles[sessionId] ?: "Session"
            val serverId = serverIds[sessionId] ?: fallbackServerId
            state.pendingPermissions.values.forEach { request ->
                items += InboxItem(
                    key = "perm:${request.id}",
                    serverId = serverId,
                    sessionId = sessionId,
                    sessionTitle = sessionTitle,
                    kind = InboxItem.Kind.PERMISSION,
                    title = "Permission: ${request.action}",
                    body = request.message ?: request.resources.joinToString(", ").ifBlank { null },
                    time = request.createdAt,
                    requestId = request.id,
                )
            }
            state.pendingForms.values.forEach { form ->
                items += InboxItem(
                    key = "form:${form.id}",
                    serverId = serverId,
                    sessionId = sessionId,
                    sessionTitle = sessionTitle,
                    kind = InboxItem.Kind.FORM,
                    title = form.title ?: "Needs your input",
                    body = form.fields.joinToString(", ") { it.label }.ifBlank { null },
                    time = form.createdAt,
                    requestId = form.id,
                )
            }
        }

        overlaySessions(base).forEach { session ->
            val finished = session.state == RunState.DONE || session.state == RunState.FAILED
            // Finished-run items (and their notifications) only for sessions started from the phone.
            if (finished && session.fromParallax && session.unread && session.updatedAt >= now - DAY_MS) {
                val failed = session.state == RunState.FAILED
                items += InboxItem(
                    key = "${session.state}:${session.id}:${session.updatedAt}",
                    serverId = session.serverId,
                    sessionId = session.id,
                    sessionTitle = session.title,
                    kind = if (failed) InboxItem.Kind.FAILED else InboxItem.Kind.DONE,
                    title = session.title,
                    body = if (failed) session.error ?: "Run failed" else "Run finished",
                    time = session.updatedAt,
                    requestId = null,
                )
            }
        }
        return items.sortedByDescending { it.time }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private fun requireClient(): ApiClient {
        val server = activeServer.value
            ?: throw RepoException("No server selected. Add a server first.")
        return api?.takeIf { it.server.id == server.id } ?: ApiClient(server).also { api = it }
    }

    /** Sends the prompt and optimistically adds the user row until the event stream confirms it. */
    private suspend fun postPrompt(client: ApiClient, sessionId: String, text: String, requestQueue: Boolean = false) {
        val response = client.post(
            "/api/session/$sessionId/prompt",
            buildJsonObject { put("text", text); if (requestQueue) put("delivery", "queue") },
        )
        val data = response.data.asObjectOrNull() ?: return
        val id = data.string("id") ?: return
        val message = data.objectAt("payload")?.string("text") ?: text
        if (client.server.id != activeServer.value?.id) return
        val queued = data.string("delivery") == "queue"
        liveState(sessionId).update { state ->
            if (state.items.any { it.id == id }) {
                state
            } else {
                state.copy(
                    queueRevision = state.queueRevision + 1,
                    items = state.items + TranscriptItem.User(
                        id = id,
                        text = message,
                        time = data.objectAt("time")?.long("created") ?: System.currentTimeMillis(),
                        queued = queued,
                    ),
                )
            }
        }
        revision.value++
    }

    private fun modelJson(ref: ModelRef): JsonObject = buildJsonObject {
        put("providerID", ref.providerId)
        put("id", ref.modelId)
        ref.variant?.let { put("variant", it) }
    }

    private fun anyToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (key, item) ->
                if (key != null) put(key.toString(), anyToJson(item))
            }
        }
        is Iterable<*> -> buildJsonArray { value.forEach { add(anyToJson(it)) } }
        is Array<*> -> buildJsonArray { value.forEach { add(anyToJson(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    private suspend fun <T> io(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RepoException) {
        throw e
    } catch (e: Throwable) {
        throw RepoException(friendlyNetworkError(e), cause = e)
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}

/** First ~48 chars of a prompt, cut at a word boundary, with an ellipsis when shortened. Never contains " · ". */
internal fun shortTitle(prompt: String, max: Int = 48): String {
    val one = prompt.trim().replace(Regex("\\s+"), " ").replace(" · ", " - ")
    if (one.length <= max) return one
    val cut = one.take(max).substringBeforeLast(' ').ifBlank { one.take(max) }
    return cut.trimEnd(',', '.', ';', ':') + "…"
}
