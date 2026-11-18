package app.parallax.opencode.core.data.live

import app.parallax.opencode.core.model.ConnectionState
import app.parallax.opencode.core.net.ApiClient
import app.parallax.opencode.core.net.ApiJson
import app.parallax.opencode.core.net.friendlyNetworkError
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

/**
 * One SSE connection to `/api/event` for one server.
 *
 * Events are funnelled through an unlimited channel so the OkHttp callback thread never blocks and
 * the consumer applies them strictly in order. The stream is lossy across disconnects, so every
 * successful (re)connect awaits [onResync] before the connection is reported as Connected.
 */
internal class EventStream(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    private val onResync: suspend () -> String?,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Parsed `data:` frames, in order. Events that arrive before a successful resync are dropped. */
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun run() {
        var backoff = START_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            _state.value = ConnectionState.Connecting
            val signals = Channel<Signal>(Channel.UNLIMITED)
            var failure: String? = null
            var authFailure = false
            var opened = false

            val source = try {
                newSource(signals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = ConnectionState.Offline(friendlyNetworkError(e), backoff)
                delay(backoff)
                backoff = nextBackoff(backoff)
                continue
            }

            try {
                for (signal in signals) {
                    when (signal) {
                        Signal.Open -> {
                            if (opened) continue
                            opened = true
                            try {
                                val version = onResync()
                                _state.value = ConnectionState.Connected(version)
                                backoff = START_BACKOFF_MS
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: RepoException) {
                                failure = e.message
                                authFailure = e.code == 401
                                break
                            } catch (e: Throwable) {
                                failure = friendlyNetworkError(e)
                                break
                            }
                        }

                        is Signal.Event -> {
                            if (!opened) continue
                            _events.emit(signal.event)
                        }

                        is Signal.Closed -> {
                            failure = signal.reason ?: failure
                            authFailure = signal.code == 401 || authFailure
                            break
                        }
                    }
                }
            } finally {
                source.cancel()
                signals.close()
            }

            if (!currentCoroutineContext().isActive) break

            _state.value = if (authFailure) {
                ConnectionState.AuthFailed(failure ?: "Not authorized. Check the password or pair this device again.")
            } else {
                ConnectionState.Offline(failure ?: "Disconnected from the server.", backoff)
            }
            delay(backoff)
            backoff = nextBackoff(backoff)
        }
    }

    private fun newSource(signals: Channel<Signal>): EventSource =
        api.sse("/api/event", object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                signals.trySend(Signal.Open)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val json = runCatching { ApiJson.parseToJsonElement(data) as? JsonObject }.getOrNull()
                    ?: return
                val event = parseAgentEvent(json) ?: return
                signals.trySend(Signal.Event(event))
            }

            override fun onClosed(eventSource: EventSource) {
                signals.trySend(Signal.Closed(reason = null, code = null))
                signals.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                val reason = when {
                    code == 401 -> "Not authorized. Check the password or pair this device again."
                    t != null -> friendlyNetworkError(t)
                    response != null -> "The server rejected the event stream (${response.code})."
                    else -> "The event stream closed."
                }
                signals.trySend(Signal.Closed(reason, code))
                signals.close()
            }
        })

    private fun nextBackoff(current: Long): Long =
        (current * 2).coerceAtMost(MAX_BACKOFF_MS)

    private sealed interface Signal {
        data object Open : Signal
        data class Event(val event: AgentEvent) : Signal
        data class Closed(val reason: String?, val code: Int?) : Signal
    }

    private companion object {
        const val START_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
