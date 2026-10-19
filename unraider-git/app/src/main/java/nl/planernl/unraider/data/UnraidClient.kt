package nl.planernl.unraider.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import nl.planernl.unraider.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

open class UnraidException(val text: UiText) : Exception((text as? UiText.Raw)?.value ?: "UnraidException") {
    constructor(message: String) : this(UiText.Raw(message))
}

/** De server toont een certificaat dat (nog) niet vertrouwd wordt. */
class CertificateNotTrustedException(val cert: CertInfo, val pinChanged: Boolean) : UnraidException(
    UiText.Res(if (pinChanged) R.string.err_cert_changed else R.string.err_cert_untrusted)
)

/** De server stuurt http door naar https; de app moet het https-adres gebruiken. */
class HttpsRedirectException(val newBaseUrl: String) : UnraidException(
    UiText.Res(R.string.err_redirect_https, newBaseUrl)
)

/**
 * Minimale GraphQL-client voor de Unraid API (/graphql).
 *
 * Bewust zonder codegeneratie: de antwoorden worden als JSON-boom gelezen, zodat
 * de app blijft werken als jouw Unraid-versie een iets ander schema heeft.
 */
class UnraidClient(private val config: ServerConfig) {

    private val endpoint = "${config.baseUrl}/graphql"

    suspend fun query(query: String, variables: JsonObject? = null): JsonObject =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("query", query)
                if (variables != null) put("variables", variables)
            }.toString()

            val request = Request.Builder()
                .url(endpoint)
                .header("x-api-key", config.apiKey)
                .header("Accept", "application/json")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            val (code, location, text) = try {
                httpFor(config.certPin).newCall(request).execute().use { response ->
                    Triple(response.code, response.header("Location"), response.body?.string().orEmpty())
                }
            } catch (e: IOException) {
                CertTrust.findUntrusted(e)?.let { untrusted ->
                    val cert = untrusted.cert
                    if (cert != null) throw CertificateNotTrustedException(CertTrust.info(cert), config.certPin != null)
                }
                throw UnraidException(friendlyNetworkError(e))
            }

            // Omleidingen niet automatisch volgen: OkHttp zou van de POST een GET maken
            // en de query kwijtraken. Een omleiding naar https melden we aan de app.
            if (code in 300..399) {
                val target = location?.let { runCatching { java.net.URI(config.baseUrl).resolve(it) }.getOrNull() }
                if (target != null && target.scheme == "https") {
                    val port = if (target.port == -1 || target.port == 443) "" else ":${target.port}"
                    throw HttpsRedirectException("https://${target.host}$port")
                }
                throw UnraidException(UiText.Res(R.string.err_redirect, location ?: "?"))
            }

            val root = try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: throw UnraidException(
                when (code) {
                    401, 403 -> UiText.Res(R.string.err_access_denied, code)
                    404 -> UiText.Res(R.string.err_no_api, endpoint)
                    else -> UiText.Res(R.string.err_unexpected_response, code)
                }
            )

            val data = root["data"] as? JsonObject
            val errors = (root["errors"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.str("message") }
                .orEmpty()

            val dataIsEmpty = data == null || data.values.all { it is JsonNull }
            if (errors.isNotEmpty() && dataIsEmpty) {
                throw UnraidException(errors.first())
            }
            if (data == null) throw UnraidException(UiText.Res(R.string.err_empty_response, code))
            data
        }

    /**
     * Probeert eerst de uitgebreide query; geeft de server aan dat een veld niet
     * bestaat (oudere Unraid-versie), dan valt hij terug op de basisversie.
     */
    suspend fun queryWithFallback(primary: String, fallback: String): JsonObject =
        try {
            query(primary)
        } catch (e: UnraidException) {
            if (e.message?.contains("Cannot query field", ignoreCase = true) == true) query(fallback)
            else throw e
        }

    private fun friendlyNetworkError(e: IOException): UiText = when (e) {
        is UnknownHostException -> UiText.Res(R.string.err_unknown_host)
        is ConnectException -> UiText.Res(R.string.err_connect)
        is SocketTimeoutException -> UiText.Res(R.string.err_timeout)
        is SSLException -> UiText.Res(R.string.err_ssl)
        else -> UiText.Res(R.string.err_network, e.message ?: e.javaClass.simpleName)
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        private val baseHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        // Eén client per vastgezet certificaat, zodat verbindingen hergebruikt worden
        private val clients = java.util.concurrent.ConcurrentHashMap<String, OkHttpClient>()

        private fun httpFor(pin: String?): OkHttpClient =
            clients.getOrPut(pin ?: "") { CertTrust.client(baseHttp, pin) }
    }
}

// ---- Kleine hulpfuncties om de JSON-boom te lezen ----

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Leest getallen die als nummer óf als string (BigInt) binnenkomen. */
fun JsonObject.long(key: String): Long? =
    str(key)?.trim()?.toBigDecimalOrNull()?.toLong()

fun JsonObject.int(key: String): Int? = long(key)?.toInt()

fun JsonObject.double(key: String): Double? = str(key)?.trim()?.toDoubleOrNull()

fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()
