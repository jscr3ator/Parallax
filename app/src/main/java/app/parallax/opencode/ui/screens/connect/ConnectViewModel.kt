package app.parallax.opencode.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parallax.opencode.core.model.BackendKind
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.net.ServerAuth
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectViewModel(private val repository: AgentRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set when a server was added; the host navigates away. */
    private val _connected = MutableStateFlow<ServerConfig?>(null)
    val connected: StateFlow<ServerConfig?> = _connected.asStateFlow()

    fun clearError() { _error.value = null }

    private fun fail(e: Throwable) {
        _error.value = (e as? RepoException)?.message ?: e.message ?: "Couldn't connect"
    }

    private suspend fun added(config: ServerConfig) {
        try {
            repository.addServer(config)
            repository.setActiveServer(config.id)
            _connected.value = config
        } catch (e: Throwable) { fail(e) }
    }

    fun pairOpenCode(link: String) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        _error.value = null
        try { added(ServerAuth.pairOpenCode(link)) } catch (e: Exception) { fail(e) }
        finally { _busy.value = false }
    }

    fun redeem(link: String) = viewModelScope.launch {
        _busy.value = true
        try {
            added(ServerAuth.connectAnyLink(link))
        } catch (e: Throwable) { fail(e) }
        _busy.value = false
    }

    fun loginOpenChamber(url: String, password: String) = viewModelScope.launch {
        _busy.value = true
        try {
            val base = normalizeUrl(url)
            added(ServerAuth.loginOpenChamber(base, password, ServerAuth.hostLabel(base)))
        } catch (e: Throwable) { fail(e) }
        _busy.value = false
    }

    fun verifyOpencode(url: String, password: String) = viewModelScope.launch {
        _busy.value = true
        try {
            val base = normalizeUrl(url)
            val config = ServerConfig(
                id = ServerAuth.newId(),
                name = ServerAuth.hostLabel(base),
                kind = BackendKind.OPENCODE,
                baseUrl = base,
                username = "opencode",
                secret = password,
                addedAt = System.currentTimeMillis(),
            )
            try {
                ServerAuth.verifyOpencode(config)
            } catch (e: app.parallax.opencode.core.repo.RepoException) {
                // Turn a bare network failure into actionable help (firewall / Wi-Fi).
                if (e.code == null) throw app.parallax.opencode.core.repo.RepoException(ServerAuth.unreachableHelp(listOf(base)), cause = e) else throw e
            }
            added(config)
        } catch (e: Throwable) { fail(e) }
        _busy.value = false
    }
}

/** Accepts "192.168.1.5:4096", "host:3000/" or a full URL; returns an origin with a scheme and no trailing slash. */
internal fun normalizeUrl(raw: String): String {
    val t = raw.trim().trimEnd('/')
    return if ("://" in t) t else "https://$t"
}
