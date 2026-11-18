package app.parallax.opencode.ui.screens.newtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.parallax.opencode.core.model.AgentOption
import app.parallax.opencode.core.model.ModelOption
import app.parallax.opencode.core.model.ModelRef
import app.parallax.opencode.core.model.ParallelLaunch
import app.parallax.opencode.core.model.ProjectInfo
import app.parallax.opencode.core.model.RunSpec
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NewTaskViewModel(private val repository: AgentRepository) : ViewModel() {
    private val _models = MutableStateFlow<List<ModelOption>>(emptyList())
    val models: StateFlow<List<ModelOption>> = _models.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentOption>>(emptyList())
    val agents: StateFlow<List<AgentOption>> = _agents.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()

    private val _defaultModel = MutableStateFlow<ModelRef?>(null)
    val defaultModel: StateFlow<ModelRef?> = _defaultModel.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Session ids of the launched run(s), in run order. Consumed once by the sheet. */
    private val _launched = MutableStateFlow<List<String>?>(null)
    val launched: StateFlow<List<String>?> = _launched.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // Default first: the sheet preselects as soon as models arrive.
                _defaultModel.value = runCatching { repository.defaultModel() }.getOrNull()
                _models.value = repository.models()
                _agents.value = repository.agents()
                _projects.value = repository.projects()
            } catch (e: Throwable) {
                _error.value = (e as? RepoException)?.message ?: e.message ?: "Couldn't load catalog"
            }
            _loading.value = false
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        try {
            repository.deleteSession(id)
        } catch (e: RepoException) {
            _error.value = e.message ?: "Couldn't delete chat"
        } catch (e: java.io.IOException) {
            _error.value = e.message ?: "Couldn't delete chat"
        }
    }

    fun clearError() { _error.value = null }

    fun consumeLaunched() { _launched.value = null }

    fun launch(
        prompt: String,
        title: String?,
        directory: String?,
        agent: String?,
        models: List<ModelRef>,
        runsPerModel: Int,
    ) = viewModelScope.launch {
        if (prompt.isBlank() || models.isEmpty()) return@launch
        _busy.value = true
        try {
            val ids = if (models.size == 1 && runsPerModel == 1) {
                listOf(
                    repository.startSession(
                        prompt = prompt,
                        directory = directory,
                        model = models.first(),
                        agent = agent,
                        title = title?.ifBlank { null },
                    ),
                )
            } else {
                val runs = models.flatMap { m ->
                    (1..runsPerModel).map { n ->
                        val label = if (runsPerModel > 1) "${m.modelId} #$n" else m.modelId
                        RunSpec(model = m, agent = agent, label = label)
                    }
                }
                launchRuns(prompt, directory, runs)
            }
            _launched.value = ids
        } catch (e: Throwable) {
            _error.value = (e as? RepoException)?.message ?: e.message ?: "Couldn't start session"
        }
        _busy.value = false
    }

    /** Multi-model/multi-run launch from the Chat tab's parallel pill. */
    fun launchParallelDirect(prompt: String, directory: String?, runs: List<RunSpec>) = viewModelScope.launch {
        if (prompt.isBlank() || runs.isEmpty()) return@launch
        _busy.value = true
        try {
            _launched.value = launchRuns(prompt, directory, runs)
        } catch (e: Throwable) {
            _error.value = (e as? RepoException)?.message ?: e.message ?: "Couldn't start session"
        }
        _busy.value = false
    }

    private suspend fun launchRuns(prompt: String, directory: String?, runs: List<RunSpec>): List<String> =
        repository.launchParallel(
            ParallelLaunch(
                prompt = prompt,
                directory = directory,
                runs = runs,
                title = null,
            ),
        )
}
