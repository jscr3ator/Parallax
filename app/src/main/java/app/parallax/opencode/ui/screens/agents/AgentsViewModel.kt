package app.parallax.opencode.ui.screens.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.ConnectionState
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentsViewModel(private val repository: AgentRepository) : ViewModel() {
    /** Show desktop/TUI history too (default: only sessions started from the phone + anything live). */
    private val _showAll = MutableStateFlow(rememberedShowAll)
    val showAll: StateFlow<Boolean> = _showAll.asStateFlow()
    fun setShowAll(value: Boolean) { _showAll.value = value; rememberedShowAll = value }

    val sessions: StateFlow<List<AgentSession>> = kotlinx.coroutines.flow.combine(repository.sessions, _showAll) { list, all ->
        if (all) list else {
            val mine = list.filter { it.fromParallax }.map { it.id }.toSet()
            list.filter { s ->
                s.fromParallax || s.parentId in mine ||
                    s.state == RunState.RUNNING || s.state == RunState.NEEDS_YOU
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Total sessions on the server, to tell the user history is hidden. */
    val hiddenCount: StateFlow<Int> = kotlinx.coroutines.flow.combine(repository.sessions, sessions) { all, shown ->
        (all.count { it.parentId == null } - shown.count { it.parentId == null }).coerceAtLeast(0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val inbox = repository.inbox
    val connection: StateFlow<ConnectionState> = repository.connection
    val servers: StateFlow<List<ServerConfig>> = repository.servers
    val activeServer: StateFlow<ServerConfig?> = repository.activeServer

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val needsYouCount: StateFlow<Int> = repository.sessions
        .map { list -> list.count { it.state == RunState.NEEDS_YOU } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun clearError() { _error.value = null }

    private fun fail(e: Throwable) {
        _error.value = (e as? RepoException)?.message ?: e.message ?: "Something went wrong"
    }

    /** opencode projects, for naming groups the way OpenChamber does. */
    private val _projects = MutableStateFlow<List<app.parallax.opencode.core.model.ProjectInfo>>(emptyList())
    val projects: StateFlow<List<app.parallax.opencode.core.model.ProjectInfo>> = _projects.asStateFlow()
    init { viewModelScope.launch { _projects.value = runCatching { repository.projects() }.getOrDefault(emptyList()) } }

    /** OpenChamber projects + folders; refreshed on pull, after edits and every 20 s (other OC clients may change them). */
    private val _workspace = MutableStateFlow(app.parallax.opencode.core.model.Workspace(emptyList(), emptyList()))
    val workspace: StateFlow<app.parallax.opencode.core.model.Workspace> = _workspace.asStateFlow()
    fun reloadWorkspace() = viewModelScope.launch {
        runCatching { repository.workspace() }.onSuccess { _workspace.value = it }
    }
    init {
        viewModelScope.launch {
            while (true) { runCatching { repository.workspace() }.onSuccess { _workspace.value = it }; kotlinx.coroutines.delay(20_000) }
        }
    }

    fun moveToFolder(session: AgentSession, folderId: String?) = viewModelScope.launch {
        val ws = _workspace.value
        val scope = scopeFor(session, ws)
        val from = ws.folders.firstOrNull { it.scopeKey.trimEnd('/') == scope.trimEnd('/') && session.id in it.sessionIds }?.id
        try { repository.moveToFolder(session.id, scope, folderId, from); _workspace.value = repository.workspace() } catch (e: Throwable) { fail(e) }
    }

    fun newFolderAndMove(session: AgentSession, name: String) = viewModelScope.launch {
        val ws = _workspace.value
        val scope = scopeFor(session, ws)
        val from = ws.folders.firstOrNull { it.scopeKey.trimEnd('/') == scope.trimEnd('/') && session.id in it.sessionIds }?.id
        try {
            val id = repository.createFolder(scope, name.trim())
            repository.moveToFolder(session.id, scope, id, from)
            _workspace.value = repository.workspace()
        } catch (e: Throwable) { fail(e) }
    }

    fun archive(id: String) = viewModelScope.launch {
        try { repository.archiveSession(id) } catch (e: Throwable) { fail(e) }
    }

    fun delete(id: String) = viewModelScope.launch {
        try { repository.deleteSession(id) } catch (e: Throwable) { fail(e) }
    }

    fun rename(id: String, title: String) = viewModelScope.launch {
        try { repository.renameSession(id, title) } catch (e: Throwable) { fail(e) }
    }

    fun refresh() = viewModelScope.launch {
        _refreshing.value = true
        try {
            repository.refresh()
            runCatching { repository.workspace() }.onSuccess { _workspace.value = it }
        } catch (e: Throwable) { fail(e) }
        _refreshing.value = false
    }

    fun retryConnection() = refresh()

    fun switchServer(id: String) = viewModelScope.launch {
        try { repository.setActiveServer(id) } catch (e: Throwable) { fail(e) }
    }
}

/** Survives tab switches within a process. */
private var rememberedShowAll = true
