package app.parallax.opencode.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.parallax.opencode.core.model.ServerConfig
import app.parallax.opencode.core.net.ApiJson
import app.parallax.opencode.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.store by preferencesDataStore("parallax")

/**
 * The only state the phone keeps: which servers it knows, which one is active, and UI prefs.
 * Everything else (sessions, transcripts, MCP config, agents) lives on the server.
 *
 * Note: secrets are stored in app-private DataStore. Android's per-app sandbox protects them from
 * other apps; a Keystore-wrapped store is a planned hardening step.
 */
class ServerStore(private val context: Context) {
    private val kServers = stringPreferencesKey("servers")
    private val kActive = stringPreferencesKey("active_server")
    private val kTheme = stringPreferencesKey("theme")
    private val kDevice = stringPreferencesKey("device_key")
    private val kNotify = stringPreferencesKey("notify_prefs")

    private val listSer = ListSerializer(ServerConfig.serializer())

    val servers: Flow<List<ServerConfig>> = context.store.data.map { p ->
        p[kServers]?.let { runCatching { ApiJson.decodeFromString(listSer, it) }.getOrNull() } ?: emptyList()
    }
    val activeServerId: Flow<String?> = context.store.data.map { it[kActive] }
    val theme: Flow<ThemeMode> = context.store.data.map { p ->
        p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT
    }

    /** Notification toggles, a comma-separated set of InboxItem.Kind names. Default: all. */
    val notifyKinds: Flow<Set<String>> = context.store.data.map { p ->
        p[kNotify]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: setOf("PERMISSION", "FORM", "DONE", "FAILED")
    }

    suspend fun upsert(server: ServerConfig) = context.store.edit { p ->
        val list = p[kServers]?.let { runCatching { ApiJson.decodeFromString(listSer, it) }.getOrNull() } ?: emptyList()
        val next = list.filterNot { it.id == server.id } + server
        p[kServers] = ApiJson.encodeToString(listSer, next)
        if (p[kActive] == null) p[kActive] = server.id
    }

    suspend fun remove(serverId: String) = context.store.edit { p ->
        val list = p[kServers]?.let { runCatching { ApiJson.decodeFromString(listSer, it) }.getOrNull() } ?: emptyList()
        val next = list.filterNot { it.id == serverId }
        p[kServers] = ApiJson.encodeToString(listSer, next)
        if (p[kActive] == serverId) {
            if (next.isEmpty()) p.remove(kActive) else p[kActive] = next.first().id
        }
    }

    suspend fun setActive(serverId: String) = context.store.edit { it[kActive] = serverId }
    suspend fun setTheme(mode: ThemeMode) = context.store.edit { it[kTheme] = mode.name }
    suspend fun setNotifyKinds(kinds: Set<String>) = context.store.edit { it[kNotify] = kinds.joinToString(",") }

    /** Stable install id used as the pairing dedupe key. */
    suspend fun deviceKey(): String {
        context.store.data.first()[kDevice]?.let { return it }
        val key = java.util.UUID.randomUUID().toString()
        context.store.edit { it[kDevice] = key }
        return key
    }
}
