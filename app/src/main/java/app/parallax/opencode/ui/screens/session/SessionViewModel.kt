package app.parallax.opencode.ui.screens.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parallax.opencode.core.model.AgentSession
import app.parallax.opencode.core.model.FormRequest
import app.parallax.opencode.core.model.ModelRef
import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.model.PermissionRequest
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(val sessionId: String, private val repository: AgentRepository) : ViewModel() {
    val session: StateFlow<AgentSession?> = repository.sessions
        .map { list -> list.find { it.id == sessionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val children: StateFlow<List<AgentSession>> = repository.sessions
        .map { list -> list.filter { it.parentId == sessionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transcript: StateFlow<List<TranscriptItem>> = repository.transcript(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingPermissions: StateFlow<List<PermissionRequest>> = transcript
        .map { items -> items.filterIsInstance<TranscriptItem.Permission>().map { it.request } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingForms: StateFlow<List<FormRequest>> = transcript
        .map { items -> items.filterIsInstance<TranscriptItem.Form>().map { it.form } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _composerBusy = MutableStateFlow(false)
    val composerBusy: StateFlow<Boolean> = _composerBusy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            try { repository.markViewed(sessionId) } catch (_: Throwable) { }
        }
    }

    fun clearError() { _error.value = null }

    private fun fail(e: Throwable) {
        _error.value = (e as? RepoException)?.message ?: e.message ?: "Something went wrong"
    }

    fun send(text: String, queued: Boolean, onSent: () -> Unit) = viewModelScope.launch {
        if (text.isBlank() || _composerBusy.value) return@launch
        _composerBusy.value = true
        try { repository.sendPrompt(sessionId, text, queued); onSent() } catch (e: Throwable) { fail(e) }
        _composerBusy.value = false
    }

    fun cancelQueued(id: String) = viewModelScope.launch {
        try { repository.cancelQueuedPrompt(sessionId, id) } catch (e: Throwable) { fail(e) }
    }

    fun stop() = viewModelScope.launch {
        try { repository.interrupt(sessionId) } catch (e: Throwable) { fail(e) }
    }

    fun replyPermission(requestId: String, decision: PermissionDecision) = viewModelScope.launch {
        try { repository.replyPermission(sessionId, requestId, decision) } catch (e: Throwable) { fail(e) }
    }

    fun replyForm(formId: String, answers: Map<String, Any>) = viewModelScope.launch {
        try { repository.replyForm(sessionId, formId, answers) } catch (e: Throwable) { fail(e) }
    }

    fun cancelForm(formId: String) = viewModelScope.launch {
        try { repository.cancelForm(sessionId, formId) } catch (e: Throwable) { fail(e) }
    }

    fun rename(title: String) = viewModelScope.launch {
        try { repository.renameSession(sessionId, title) } catch (e: Throwable) { fail(e) }
    }

    fun changeModel(model: ModelRef) = viewModelScope.launch {
        try { repository.setSessionModel(sessionId, model) } catch (e: Throwable) { fail(e) }
    }

    fun delete() = viewModelScope.launch {
        try { repository.deleteSession(sessionId) } catch (e: Throwable) { fail(e) }
    }
}
