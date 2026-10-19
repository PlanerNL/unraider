package nl.planernl.unraider.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    val config: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            address = prefs[keyAddress].orEmpty(),
            apiKey = prefs[keyApiKey].orEmpty(),
            certPin = prefs[keyCertPin],
        )
    }

    suspend fun save(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[keyAddress] = config.address.trim()
            prefs[keyApiKey] = config.apiKey.trim()
            if (config.certPin != null) prefs[keyCertPin] = config.certPin else prefs.remove(keyCertPin)
        }
    }
}
