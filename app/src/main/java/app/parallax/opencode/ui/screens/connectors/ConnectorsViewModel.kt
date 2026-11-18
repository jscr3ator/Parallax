package app.parallax.opencode.ui.screens.connectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parallax.opencode.core.model.McpServerInfo
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectorsViewModel(private val repository: AgentRepository) : ViewModel() {
    private val _mcp = MutableStateFlow<List<McpServerInfo>>(emptyList())
    val mcp: StateFlow<List<McpServerInfo>> = _mcp.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _toggling = MutableStateFlow<Set<String>>(emptySet())
    val toggling: StateFlow<Set<String>> = _toggling.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _mcp.value = repository.mcpServers()
            } catch (e: Throwable) {
                _error.value = (e as? RepoException)?.message ?: e.message
            }
            _loading.value = false
        }
    }

    fun clearError() { _error.value = null }

    fun setConnected(name: String, connected: Boolean) = viewModelScope.launch {
        _toggling.value = _toggling.value + name
        try {
            repository.setMcpConnected(name, connected)
            _mcp.value = repository.mcpServers()
        } catch (e: Throwable) {
            _error.value = (e as? RepoException)?.message ?: e.message ?: "Couldn't toggle connector"
        }
        _toggling.value = _toggling.value - name
    }
}
