package nl.planernl.unraider.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "instellingen")

data class ServerConfig(
    val address: String = "",
    val apiKey: String = "",
    /** SHA-256-vingerafdruk van het goedgekeurde servercertificaat (Use SSL/TLS: Yes). */
    val certPin: String? = null,
) {
    val isComplete: Boolean get() = address.isNotBlank() && apiKey.isNotBlank()

    /** Basis-URL zonder slash aan het eind, met http:// als er geen schema is opgegeven. */
    val baseUrl: String
        get() {
            val trimmed = address.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "http://$trimmed"
        }
}

class SettingsStore(private val context: Context) {
    private val keyAddress = stringPreferencesKey("adres")
    private val keyApiKey = stringPreferencesKey("api_key")
    private val keyCertPin = stringPreferencesKey("cert_pin")
    private val keyPush = booleanPreferencesKey("push_aan")
    private val keySeen = stringSetPreferencesKey("gemelde_ids")

    val config: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            address = prefs[keyAddress].orEmpty(),
            apiKey = prefs[keyApiKey].orEmpty(),
            certPin = prefs[keyCertPin],
        )
    }

    val pushEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyPush] ?: false }

    suspend fun save(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[keyAddress] = config.address.trim()
            prefs[keyApiKey] = config.apiKey.trim()
            if (config.certPin != null) prefs[keyCertPin] = config.certPin else prefs.remove(keyCertPin)
        }
    }

    suspend fun setPushEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyPush] = enabled }
    }

    /** Sleutels van meldingen die al als pushmelding zijn getoond. */
    suspend fun seenKeys(): Set<String> = context.dataStore.data.first()[keySeen].orEmpty()

    suspend fun setSeenKeys(keys: Set<String>) {
        context.dataStore.edit { it[keySeen] = keys }
    }

    suspend fun currentConfig(): ServerConfig = config.first()
}
