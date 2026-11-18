package app.parallax.opencode.ui.screens.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.model.RunState
import app.parallax.opencode.core.model.ToolStatus
import app.parallax.opencode.core.model.TranscriptItem
import app.parallax.opencode.core.repo.AgentRepository
import app.parallax.opencode.core.repo.RepoException
import app.parallax.opencode.ui.components.friendlyStep
import app.parallax.opencode.ui.voice.VoiceEngine
import app.parallax.opencode.ui.voice.speakable
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface VoicePhase {
    data object Idle : VoicePhase
    data object Listening : VoicePhase
    data object Sending : VoicePhase
    data class Working(val caption: String) : VoicePhase
    data class Speaking(val text: String) : VoicePhase
    data class Approval(val requestId: String, val question: String) : VoicePhase
    data class Paused(val hint: String) : VoicePhase
    data class Error(val message: String) : VoicePhase
    data object Ended : VoicePhase
}

/**
 * Hands-free loop: listen → send to the real opencode agent → wait for the run → speak the reply → listen.
 * Approvals are asked out loud ("Say allow or deny"). Everything else is the normal session, visible in chat.
 */
class VoiceViewModel(app: Application, private val repository: AgentRepository, initialSessionId: String?) : AndroidViewModel(app) {
    val engine = VoiceEngine(app)

    private val _phase = MutableStateFlow<VoicePhase>(VoicePhase.Idle)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()

    private val _sessionId = MutableStateFlow(initialSessionId)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    /** Last thing you said (shown faintly above the agent's caption). */
    private val _lastHeard = MutableStateFlow("")
    val lastHeard: StateFlow<String> = _lastHeard.asStateFlow()

    private val firstModel = if (initialSessionId == null) viewModelScope.async { pickModel() } else null
    private var watchJob: Job? = null
    private var emptyTries = 0
    private var promptSentAt = 0L
    private val answeredRequests = mutableSetOf<String>()

    fun start() {
        if (_phase.value == VoicePhase.Idle || _phase.value is VoicePhase.Paused || _phase.value is VoicePhase.Error) listen()
    }

    fun onOrbTap() {
        when (_phase.value) {
            is VoicePhase.Speaking -> { watchJob?.cancel(); engine.stopSpeaking(); listen() }
            VoicePhase.Listening -> { engine.stopListening(); _phase.value = VoicePhase.Paused("Tap to talk") }
            is VoicePhase.Paused, is VoicePhase.Error, VoicePhase.Idle -> listen()
            else -> Unit
        }
    }

    fun toggleMute() {
        if (_phase.value == VoicePhase.Listening) { engine.stopListening(); _phase.value = VoicePhase.Paused("Microphone off") }
        else if (_phase.value is VoicePhase.Paused) listen()
    }

    fun micDenied() { _phase.value = VoicePhase.Error("Parallax needs the microphone to talk with your agent.") }

    private fun listen(approval: VoicePhase.Approval? = null) {
        _phase.value = approval ?: VoicePhase.Listening
        engine.listen(
            onResult = { text -> onHeard(text.trim(), approval) },
            onError = { msg -> _phase.value = VoicePhase.Error(msg) },
        )
    }

    private fun onHeard(text: String, approval: VoicePhase.Approval?) {
        if (text.isEmpty()) {
            emptyTries++
            if (emptyTries >= 3) { emptyTries = 0; _phase.value = VoicePhase.Paused("Tap to talk") } else listen(approval)
            return
        }
        emptyTries = 0
        _lastHeard.value = text
        val lower = text.lowercase()
        if (approval != null) { answerApproval(approval, lower); return }
        if (STOP_PHRASES.any { lower == it || lower.startsWith("$it ") }) {
            engine.speak("Okay.") { _phase.value = VoicePhase.Ended }
            return
        }
        send(text)
    }

    private fun answerApproval(a: VoicePhase.Approval, lower: String) {
        val decision = when {
            lower.contains("always") -> PermissionDecision.ALWAYS
            listOf("allow", "yes", "okay", "ok", "sure", "go ahead", "do it", "yep", "approve").any { lower.contains(it) } -> PermissionDecision.ONCE
            listOf("deny", "no", "don't", "do not", "stop", "cancel", "nope", "reject").any { lower.contains(it) } -> PermissionDecision.REJECT
            else -> null
        }
        if (decision == null) { engine.speak("Say allow, or deny.") { listen(a) }; return }
        val sid = _sessionId.value ?: return
        answeredRequests += a.requestId
        _phase.value = VoicePhase.Working(if (decision == PermissionDecision.REJECT) "Okay, skipping that" else "Okay, continuing")
        viewModelScope.launch {
            try { repository.replyPermission(sid, a.requestId, decision) } catch (e: Throwable) { fail(e) }
        }
    }

    private fun send(text: String) {
        _phase.value = VoicePhase.Sending
        promptSentAt = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val sid = _sessionId.value
                if (sid == null) {
                    _sessionId.value = repository.startSession(prompt = text, directory = null, model = firstModel?.await(), agent = null)
                } else {
                    repository.sendPrompt(sid, text)
                }
                _phase.value = VoicePhase.Working("Thinking…")
                watchRun()
            } catch (e: Throwable) { fail(e) }
        }
    }

    /** The server default when it's on Go; otherwise a fast Go model; otherwise whatever the server prefers. */
    private suspend fun pickModel() = runCatching {
        val models = repository.models()
        val def = repository.defaultModel()
        models.find { it.ref.key == def?.key && it.isGo }?.ref
            ?: models.firstOrNull { it.isGo && it.ref.modelId == "deepseek-v4.1-flash" }?.ref
            ?: models.firstOrNull { it.isGo }?.ref
            ?: def
    }.getOrNull()

    /**
     * Follow the run. Narrates the current step, asks approvals aloud, and speaks the reply AS IT STREAMS:
     * each finished sentence is queued immediately, so you hear the start within a second of the first token.
     * The turn ends on the run's own end marker; a 400 ms tick re-checks so nothing can stall.
     */
    private fun watchRun() {
        val sid = _sessionId.value ?: return
        watchJob?.cancel()
        var spokenUpTo = 0          // chars of this turn's reply already queued for speech
        var spokenChars = 0         // cap total speech; the rest stays in the chat
        var truncated = false
        var finishing = false
        val tick = kotlinx.coroutines.flow.flow { while (true) { emit(Unit); kotlinx.coroutines.delay(400) } }
        watchJob = viewModelScope.launch {
            combine(repository.transcript(sid), repository.sessions, tick) { items, sessions, _ -> items to sessions.find { it.id == sid } }
                .collect { (items, session) ->
                    if (finishing) return@collect
                    val phase = _phase.value
                    if (phase is VoicePhase.Ended || phase is VoicePhase.Approval) return@collect

                    val lastUser = items.indexOfLast { it is TranscriptItem.User && !it.queued }
                    val turn = if (lastUser >= 0) items.drop(lastUser + 1) else emptyList()

                    // Approvals interrupt everything.
                    val pending = turn.filterIsInstance<TranscriptItem.Permission>().lastOrNull { it.request.id !in answeredRequests }
                    if (pending != null) {
                        engine.stopSpeaking()
                        val q = "Your agent wants to ${friendlyAction(pending.request.action)}. Say allow, or deny."
                        val a = VoicePhase.Approval(pending.request.id, q)
                        _phase.value = a
                        engine.speak(q) { listen(a) }
                        return@collect
                    }

                    val reply = turn.filterIsInstance<TranscriptItem.AssistantText>().joinToString("\n\n") { it.text }
                    val ended = turn.any { it is TranscriptItem.RunEnd } ||
                        (session != null && session.state != RunState.RUNNING && session.state != RunState.NEEDS_YOU &&
                            System.currentTimeMillis() - promptSentAt > 4000 && turn.none { it is TranscriptItem.AssistantText && it.streaming })

                    // Queue every newly completed sentence (outside code fences).
                    if (!truncated && reply.length > spokenUpTo) {
                        val boundary = if (ended) reply.length else voiceChunkEnd(reply, spokenUpTo, firstPhrase = spokenChars == 0)
                        if (boundary > spokenUpTo) {
                            val chunk = speakable(reply.substring(spokenUpTo, boundary), max = Int.MAX_VALUE)
                            spokenUpTo = boundary
                            if (chunk.isNotBlank()) {
                                val remaining = MAX_SPOKEN - spokenChars
                                val overflow = chunk.length > remaining
                                val words = if (overflow) chunk.take(remaining).substringBeforeLast(' ', "") else chunk
                                if (words.isNotBlank()) { engine.enqueue(words); spokenChars += words.length }
                                if (overflow) { truncated = true; engine.enqueue("The rest is in the chat.") }
                                _phase.value = VoicePhase.Speaking("")
                            }
                        }
                    }
                    if (spokenChars == 0 && !ended) {
                        val step = turn.filterIsInstance<TranscriptItem.Tool>().lastOrNull { it.status == ToolStatus.RUNNING }
                        if (_phase.value !is VoicePhase.Speaking) _phase.value = VoicePhase.Working(step?.let { friendlyStep(it) } ?: "Thinking…")
                    }
                    if (ended) {
                        finishing = true
                        if (session?.state == RunState.FAILED && spokenChars == 0) engine.enqueue("That didn't work. ${session.error?.take(120) ?: ""}")
                        else if (spokenChars == 0 && !truncated) engine.enqueue("Done.")
                        engine.whenQueueDrained { if (_phase.value !is VoicePhase.Ended) listen() }
                        watchJob?.cancel()
                    }
                }
        }
    }

    private fun speakThenListen(text: String) {
        _phase.value = VoicePhase.Speaking(text)
        engine.speak(text) { if (_phase.value is VoicePhase.Speaking) listen() }
    }

    private fun fail(e: Throwable) {
        val msg = (e as? RepoException)?.message ?: e.message ?: "Something went wrong"
        _phase.value = VoicePhase.Error(msg)
    }

    override fun onCleared() {
        watchJob?.cancel()
        engine.release()
    }

    companion object {
        private const val MAX_SPOKEN = 900
        private val STOP_PHRASES = listOf("stop", "goodbye", "good bye", "end call", "that's all", "bye", "hang up")
        fun friendlyAction(action: String) = when (action.lowercase()) {
            "bash", "shell" -> "run a command"
            "edit", "write", "patch" -> "change some files"
            "webfetch", "websearch" -> "go online"
            "external_directory" -> "work outside the project"
            else -> action.replace('_', ' ')
        }
    }
}

/** Release prose at a clause or a word boundary while keeping Markdown spans together. */
internal fun voiceChunkEnd(text: String, from: Int, firstPhrase: Boolean = true): Int {
    var boundary = from
    var ticks = 0
    var linkLabel = false
    var linkDepth = 0
    var i = from
    while (i < text.length) {
        val c = text[i]
        if (c == '`') {
            val begin = i
            while (i < text.length && text[i] == '`') i++
            val count = i - begin
            if (ticks == 0) ticks = count else if (count == ticks) ticks = 0
            continue
        }
        if (ticks == 0) {
            when {
                linkDepth > 0 && c == ')' -> linkDepth--
                linkDepth > 0 && c == '(' -> linkDepth++
                c == '[' -> linkLabel = true
                c == ']' -> {
                    linkLabel = false
                    if (text.getOrNull(i + 1) == '(') { linkDepth = 1; i++ }
                }
            }
            if (!linkLabel && linkDepth == 0) {
                if (c.isWhitespace() && i - from >= (if (firstPhrase) 64 else 180)) {
                    return if (boundary > from) boundary else i + 1
                }
                val next = text.getOrNull(i + 1)
                if (c == '\n' || ((next == null || next.isWhitespace()) &&
                        (c in ".!?" || (c in ",;:" && i - from >= 22)))) boundary = i + 1
            }
        }
        i++
    }
    return boundary
}
