package app.parallax.opencode.core.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
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

private val legacyServers = stringPreferencesKey("servers")
private val encryptedServers = stringPreferencesKey("servers_encrypted")
private val Context.store by preferencesDataStore("parallax", produceMigrations = {
    listOf(object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences) = currentData[legacyServers] != null
        override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
            this[encryptedServers] = ServerCipher.encrypt(requireNotNull(currentData[legacyServers]))
            remove(legacyServers)
        }
        override suspend fun cleanUp() = Unit
    })
})

/**
 * The only state the phone keeps: which servers it knows, which one is active, and UI prefs.
 * Everything else (sessions, transcripts, MCP config, agents) lives on the server.
 *
 * The saved server list is encrypted with a per-install Android Keystore key.
 */
class ServerStore(private val context: Context) {
    private val kActive = stringPreferencesKey("active_server")
    private val kTheme = stringPreferencesKey("theme")
    private val kDevice = stringPreferencesKey("device_key")
    private val kNotify = stringPreferencesKey("notify_prefs")

    private val listSer = ListSerializer(ServerConfig.serializer())

    private fun decodeServers(p: Preferences): List<ServerConfig> =
        p[encryptedServers]?.let { ApiJson.decodeFromString(listSer, ServerCipher.decrypt(it)) } ?: emptyList()

    val servers: Flow<List<ServerConfig>> = context.store.data.map { p ->
        decodeServers(p)
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
        val list = decodeServers(p)
        val next = list.filterNot { it.id == server.id } + server
        p[encryptedServers] = ServerCipher.encrypt(ApiJson.encodeToString(listSer, next))
        if (p[kActive] == null) p[kActive] = server.id
    }

    suspend fun remove(serverId: String) = context.store.edit { p ->
        val list = decodeServers(p)
        val next = list.filterNot { it.id == serverId }
        p[encryptedServers] = ServerCipher.encrypt(ApiJson.encodeToString(listSer, next))
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
