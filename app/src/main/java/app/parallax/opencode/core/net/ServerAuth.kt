package app.parallax.opencode.core.net

import android.os.Build
import android.util.Base64
import app.parallax.opencode.core.model.BackendKind
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.repo.RepoException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/**
 * How a phone gets credentials for each backend. Verified against OpenChamber 2.7.2's server source
 * (`lib/opencode/core-routes.js`, `lib/ui-auth/ui-auth.js`, `bin/lib/commands-connect-url.js`)
 * and opencode 2.2.6's server.
 */
object ServerAuth {

    /** Stable per-install id so re-pairing replaces this phone's entry on OpenChamber instead of adding a new one. */
    var dedupeKey: String = UUID.randomUUID().toString()

    private fun deviceFields() = buildJsonObject {
        put("clientKind", "mobile")
        put("clientLabel", "Parallax on ${Build.MODEL}")
        put("deviceName", Build.MODEL)
        put("devicePlatform", "android")
        put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("appVersion", "0.1.0")
        put("dedupeKey", dedupeKey)
    }

    /**
     * Bare opencode server: verify the password by calling `GET /api/info`.
     * Returns the server version string.
     */
    suspend fun verifyOpencode(server: ServerConfig): String? {
        val info = ApiClient(server).get("/api/info").jsonObject
        return info["version"]?.jsonPrimitive?.contentOrNull
    }

    /**
     * OpenChamber with a UI password: `POST /auth/session` with `issueClientToken: true` returns a durable
     * per-device bearer token (`clientToken`). The password itself is never stored.
     */
    suspend fun loginOpenChamber(baseUrl: String, password: String, name: String?): ServerConfig {
        val probe = ServerConfig(id = "probe", name = "", kind = BackendKind.OPENCHAMBER, baseUrl = baseUrl.trimEnd('/'))
        val body = JsonObject(deviceFields() + mapOf(
            "password" to kotlinx.serialization.json.JsonPrimitive(password),
            "trustDevice" to kotlinx.serialization.json.JsonPrimitive(true),
            "issueClientToken" to kotlinx.serialization.json.JsonPrimitive(true),
        ))
        val res = ApiClient(probe).post("/auth/session", body).jsonObject
        val token = res["clientToken"]?.jsonPrimitive?.contentOrNull
        if (token.isNullOrEmpty()) {
            // A server with no UI password accepts requests without credentials.
            val open = res["authenticated"]?.jsonPrimitive?.contentOrNull == "true" || res["disabled"] != null
            if (!open) throw RepoException("OpenChamber did not issue a device token. Update OpenChamber and try again.")
        }
        return probe.copy(id = newId(), name = name ?: hostLabel(baseUrl), secret = token.orEmpty(), addedAt = System.currentTimeMillis())
    }

    /**
     * Pairing v2: the QR / link `openchamber://connect?v=2&p=<base64url(JSON)>` created in OpenChamber under
     * Settings → Remote Instances → Add a device (or `openchamber connect-url --qr`).
     * We try each direct candidate URL in priority order and redeem the one-time secret at
     * `POST {url}/api/client-auth/pairing/redeem`, which returns `clientToken`.
     * Relay-only links (no direct candidate) are not supported: they need OpenChamber's own relay app.
     */
    suspend fun redeemPairingLink(link: String): ServerConfig {
        val payload = parseConnectLink(link) ?: throw RepoException("That isn't an OpenChamber connect link.")
        val direct = payload.candidates.filter { it.url != null && it.type != "relay" }.sortedByDescending { it.priority ?: 0 }
        if (direct.isEmpty()) {
            throw RepoException("This link only works through OpenChamber's relay. Create a link for your local network or a tunnel instead (scope: Home network, or start OpenChamber with --lan).")
        }
        var lastError: Throwable? = null
        for (candidate in direct) {
            val baseUrl = candidate.url!!.trimEnd('/')
            try {
                val probe = ServerConfig(id = "probe", name = "", kind = BackendKind.OPENCHAMBER, baseUrl = baseUrl)
                val body = JsonObject(deviceFields() + mapOf(
                    "pairingId" to kotlinx.serialization.json.JsonPrimitive(payload.pairingId),
                    "secret" to kotlinx.serialization.json.JsonPrimitive(payload.secret),
                ))
                val res = ApiClient(probe).post("/api/client-auth/pairing/redeem", body).jsonObject
                val token = res["clientToken"]?.jsonPrimitive?.contentOrNull
                    ?: throw RepoException("Pairing failed: no token returned.")
                val serverLabel = (res["server"] as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull
                return probe.copy(
                    id = newId(),
                    name = payload.label ?: serverLabel ?: hostLabel(baseUrl),
                    secret = token,
                    addedAt = System.currentTimeMillis(),
                )
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw RepoException(lastError?.message ?: "Couldn't reach the server in that link.", cause = lastError)
    }

    /**
     * Parallax's own QR / link for a plain opencode server, printed by `scripts/phone-server.sh`:
     * `parallax://connect?name=<host>&password=<pw>&url=<http://lan:4096>&url=<http://tailscale:4096>`.
     * Tries each URL in order and keeps the first that answers with a valid password.
     */
    suspend fun connectParallaxLink(link: String): ServerConfig {
        val uri = android.net.Uri.parse(link.trim())
        val urls = uri.getQueryParameters("url").map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
        val password = uri.getQueryParameter("password").orEmpty()
        val name = uri.getQueryParameter("name")
        if (urls.isEmpty()) throw RepoException("That QR has no server address. Re-run scripts/phone-server.sh --qr.")
        var lastError: Throwable? = null
        for (url in urls) {
            val config = ServerConfig(
                id = newId(), name = name ?: hostLabel(url), kind = BackendKind.OPENCODE,
                baseUrl = url, username = "opencode", secret = password, addedAt = System.currentTimeMillis(),
            )
            try {
                verifyOpencode(config)
                return config
            } catch (e: RepoException) {
                if (e.code == 401) throw RepoException("Wrong password in that QR. Re-run scripts/phone-server.sh --qr and scan again.", 401, e)
                lastError = e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw RepoException(unreachableHelp(urls), cause = lastError)
    }

    /** OpenCode returns a reusable Basic-auth password when the pairing request accepts JSON. */
    suspend fun pairOpenCode(link: String): ServerConfig {
        val candidates = openCodePairingUrls(link)
        for ((index, url) in candidates.withIndex()) {
            try {
                return redeemOpenCodeUrl(url)
            } catch (e: RepoException) {
                if (e.code != null || index == candidates.lastIndex) throw e
            }
        }
        throw RepoException("Pairing code has no reachable server address.")
    }

    @Serializable
    private data class OpenCodePairing(val code: String, val urls: List<String>)

    internal fun openCodePairingUrls(input: String): List<okhttp3.HttpUrl> {
        val raw = input.trim()
        val path = Regex("/auth/connect/[A-Za-z0-9_-]+")
        fun invalid(): Nothing = throw RepoException("This QR is not an OpenCode pairing code. Run opencode pair --remote for a fresh code.")
        if (raw.startsWith("{")) {
            val pairing = try {
                ApiJson.decodeFromString(OpenCodePairing.serializer(), raw)
            } catch (_: kotlinx.serialization.SerializationException) { invalid() }
            if (!path.matches("/auth/connect/${pairing.code}")) invalid()
            return pairing.urls.mapNotNull { origin ->
                origin.toHttpUrlOrNull()?.takeIf {
                    it.username.isEmpty() && it.password.isEmpty() && it.query == null &&
                        it.fragment == null && it.encodedPath == "/"
                }?.newBuilder()?.addPathSegments("auth/connect")?.addPathSegment(pairing.code)?.build()
            }.distinct().ifEmpty { invalid() }
        }
        val url = raw.toHttpUrlOrNull() ?: invalid()
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null ||
            url.fragment != null || !path.matches(url.encodedPath)) invalid()
        return listOf(url)
    }

    private suspend fun redeemOpenCodeUrl(url: okhttp3.HttpUrl): ServerConfig {
        val origin = url.newBuilder().encodedPath("/").build().toString().trimEnd('/')
        val config = ServerConfig(id = newId(), name = url.host, kind = BackendKind.OPENCODE,
            baseUrl = origin, addedAt = System.currentTimeMillis())
        val http = BaseHttp.newBuilder().followRedirects(false).retryOnConnectionFailure(false).build()
        val response = try {
            ApiClient(config, http).get(url.encodedPath)
        } catch (e: RepoException) {
            if (e.code in listOf(400, 401, 404, 410)) throw RepoException("Pairing code expired or already used. Run opencode pair --remote again.")
            throw e
        }
        val token = (response as? JsonObject)?.get("token")?.jsonPrimitive?.contentOrNull
        if (token.isNullOrBlank()) throw RepoException("OpenCode did not return a session token.")
        return config.copy(secret = token)
    }

    /** Any supported link: Parallax (plain opencode) or OpenChamber pairing. */
    suspend fun connectAnyLink(link: String): ServerConfig = when {
        link.trim().startsWith("parallax://connect") -> connectParallaxLink(link)
        link.trim().startsWith("openchamber://") -> redeemPairingLink(link)
        link.trim().startsWith("http") -> throw RepoException("That's a web address, not a Parallax QR. Use “Enter address” instead.")
        else -> throw RepoException("That QR isn't a Parallax or OpenChamber connect code.")
    }

    /** Explains the usual reason a phone can't reach a computer: the firewall, or a different network. */
    fun unreachableHelp(urls: List<String>): String {
        val hosts = urls.mapNotNull { runCatching { android.net.Uri.parse(it) }.getOrNull()?.let { u -> "${u.host}:${u.port}" } }
        return "Your phone can't reach ${hosts.joinToString(" or ")}. " +
            "Make sure the phone is on the same Wi-Fi (or Tailscale), and that your computer's firewall allows the port " +
            "— on Linux with ufw run: sudo ufw allow from 10.0.0.0/24 to any port ${hosts.firstOrNull()?.substringAfter(':') ?: "4096"} proto tcp"
    }

    @Serializable
    data class ConnectPayload(
        val v: Int = 2,
        val pairingId: String,
        val secret: String,
        val label: String? = null,
        val fingerprint: String? = null,
        val expiresAt: String? = null,
        val candidates: List<Candidate> = emptyList(),
    )

    @Serializable
    data class Candidate(val type: String? = null, val url: String? = null, val priority: Int? = null)

    fun parseConnectLink(link: String): ConnectPayload? = runCatching {
        val uri = android.net.Uri.parse(link.trim())
        val p = uri.getQueryParameter("p") ?: return null
        val bytes = Base64.decode(p, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        ApiJson.decodeFromString(ConnectPayload.serializer(), String(bytes, Charsets.UTF_8))
    }.getOrNull()

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun hostLabel(url: String): String = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: url
}
