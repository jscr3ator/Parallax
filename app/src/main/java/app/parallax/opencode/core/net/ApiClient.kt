package app.parallax.opencode.core.net

import app.parallax.opencode.core.model.BackendKind
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.repo.RepoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    isLenient = true
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/** Shared OkHttp base. SSE needs no read timeout; REST calls get one per-call via [ApiClient]. */
val BaseHttp: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

/**
 * Thin JSON client for one server. Every opencode v2 route lives under `/api/` on both backends
 * (OpenChamber proxies them), so callers pass paths like `/api/session`.
 *
 * Responses are returned as raw [JsonElement]; most opencode endpoints wrap the payload in `{ "data": ... }`,
 * use [data] to unwrap. Errors become [RepoException] with the server's message when it sends one.
 */
class ApiClient(val server: ServerConfig, private val http: OkHttpClient = BaseHttp) {

    private val rest = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    fun authHeader(): String? = authHeaderFor(server)

    fun url(path: String, query: Map<String, String?> = emptyMap()): String {
        val b = (server.baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private fun request(path: String, query: Map<String, String?>): Request.Builder =
        Request.Builder().url(url(path, query)).header("Accept", "application/json").apply {
            authHeader()?.let { header("Authorization", it) }
        }

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): JsonElement =
        execute(request(path, query).get().build())

    suspend fun post(path: String, body: JsonElement? = null, query: Map<String, String?> = emptyMap()): JsonElement =
        execute(request(path, query).post((body ?: JsonObject(emptyMap())).toString().toRequestBody(JSON_MEDIA)).build())

    suspend fun patch(path: String, body: JsonElement): JsonElement =
        execute(request(path, emptyMap()).patch(body.toString().toRequestBody(JSON_MEDIA)).build())

    suspend fun put(path: String, body: JsonElement): JsonElement =
        execute(request(path, emptyMap()).put(body.toString().toRequestBody(JSON_MEDIA)).build())

    suspend fun delete(path: String, query: Map<String, String?> = emptyMap()): JsonElement =
        execute(request(path, query).delete().build())

    /**
     * Opens an SSE stream (e.g. `/api/event`). The caller owns reconnection.
     * Returns the [EventSource]; call `cancel()` to close it.
     */
    fun sse(path: String, listener: EventSourceListener, query: Map<String, String?> = emptyMap()): EventSource {
        val req = request(path, query).header("Accept", "text/event-stream").get().build()
        return EventSources.createFactory(http).newEventSource(req, listener)
    }

    private suspend fun execute(req: Request): JsonElement = withContext(Dispatchers.IO) {
        val response = rest.newCall(req).await()
        response.use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw RepoException(errorMessage(r, text), r.code)
            if (text.isBlank()) JsonNull else runCatching { ApiJson.parseToJsonElement(text) }.getOrElse { JsonNull }
        }
    }

    private fun errorMessage(r: Response, text: String): String {
        val parsed = runCatching { ApiJson.parseToJsonElement(text).jsonObject }.getOrNull()
        val msg = parsed?.get("message")?.jsonPrimitive?.contentOrNull
            ?: parsed?.get("error")?.let { e -> (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: e.jsonPrimitive.contentOrNull }
        return when {
            r.code == 401 -> "Not authorized. Check the password or pair this device again."
            msg != null -> msg
            else -> "Server error ${r.code}"
        }
    }

    companion object {
        fun authHeaderFor(server: ServerConfig): String? = when {
            server.secret.isEmpty() -> null
            server.kind == BackendKind.OPENCODE -> Credentials.basic(server.username.ifEmpty { "opencode" }, server.secret)
            else -> "Bearer ${server.secret}"
        }
    }
}

/** Unwraps opencode's `{ "data": X }` envelope; returns the element itself when there is no envelope. */
val JsonElement.data: JsonElement
    get() = (this as? JsonObject)?.get("data") ?: this

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(RepoException(friendlyNetworkError(e), cause = e))
        }
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

fun friendlyNetworkError(e: Throwable): String = when (e) {
    is java.net.UnknownHostException -> "Can't find that host. Check the address."
    is java.net.ConnectException -> "Can't reach the server. Is it running and reachable from this phone?"
    is java.net.SocketTimeoutException -> "The server took too long to answer."
    is javax.net.ssl.SSLException -> "Secure connection failed (${e.message})."
    else -> e.message ?: "Network error"
}
