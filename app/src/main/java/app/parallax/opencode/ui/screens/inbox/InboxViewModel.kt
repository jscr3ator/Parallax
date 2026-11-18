package app.parallax.opencode.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parallax.opencode.core.model.InboxItem
import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxViewModel(private val repository: AgentRepository) : ViewModel() {
    val inbox: StateFlow<List<InboxItem>> = repository.inbox
    val sessions = repository.sessions

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    private fun fail(e: Throwable) {
        _error.value = (e as? RepoException)?.message ?: e.message ?: "Something went wrong"
    }

    fun replyPermission(item: InboxItem, decision: PermissionDecision) = viewModelScope.launch {
        val req = item.requestId ?: return@launch
        _busy.value = _busy.value + req
        try {
            repository.replyPermission(item.sessionId, req, decision)
        } catch (e: Throwable) { fail(e) }
        _busy.value = _busy.value - req
    }

    fun dismiss(item: InboxItem) = viewModelScope.launch {
        try {
            when (item.kind) {
                InboxItem.Kind.DONE, InboxItem.Kind.FAILED -> repository.markViewed(item.sessionId)
                else -> Unit
            }
        } catch (e: Throwable) { fail(e) }
    }
}
