package nl.planernl.unraider.data

import android.net.http.SslCertificate
import android.os.Build
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Gegevens van een servercertificaat, om aan de gebruiker te tonen. */
data class CertInfo(
    val fingerprint: String,
    val subject: String,
    val issuer: String,
    val validUntil: Date,
)

/**
 * "Trust on first use" voor het zelfgemaakte certificaat van Unraid (Use SSL/TLS: Yes).
 *
 * Certificaten die Android zelf vertrouwt, blijven gewoon werken. Daarnaast accepteren we
 * precies één extra certificaat: dat met de SHA-256-vingerafdruk die je in de app hebt
 * goedgekeurd. Verandert het certificaat, dan weigert de app tot je het nieuwe goedkeurt.
 */
object CertTrust {

    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

    fun info(cert: X509Certificate) = CertInfo(
        fingerprint = fingerprint(cert),
        subject = cert.subjectX500Principal.name,
        issuer = cert.issuerX500Principal.name,
        validUntil = cert.notAfter,
    )

    private val systemTrust: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** Wordt gegooid tijdens de TLS-handshake; bevat het certificaat dat de server liet zien. */
    class UntrustedCertificateException(val cert: X509Certificate?, cause: Throwable?) :
        CertificateException("Untrusted certificate", cause)

    private class PinningTrustManager(private val pin: String?) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            systemTrust.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                systemTrust.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                val leaf = chain.firstOrNull()
                if (pin != null && leaf != null && fingerprint(leaf) == pin) return
                lastRejected = leaf
                throw UntrustedCertificateException(leaf, e)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrust.acceptedIssuers
    }

    /** OkHttp-client die systeemcertificaten plus het vastgezette certificaat vertrouwt. */
    fun client(base: OkHttpClient, pin: String?): OkHttpClient {
        val tm = PinningTrustManager(pin)
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        return base.newBuilder()
            .sslSocketFactory(ctx.socketFactory, tm)
            .hostnameVerifier { host, session ->
                // Het Unraid-certificaat staat op de servernaam, niet op het Tailscale-adres.
                // Voor het goedgekeurde certificaat is de naamcontrole dus niet nodig.
                defaultVerifier.verify(host, session) ||
                    (pin != null && (session.peerCertificates.firstOrNull() as? X509Certificate)
                        ?.let { fingerprint(it) == pin } == true)
            }
            .build()
    }

    /** Laatst geweigerde certificaat, voor als de TLS-laag onze exception inpakt. */
    @Volatile
    private var lastRejected: X509Certificate? = null

    /** Zoekt in een foutketen naar het certificaat dat niet vertrouwd werd. */
    fun findUntrusted(e: Throwable): UntrustedCertificateException? {
        var t: Throwable? = e
        var depth = 0
        var sawCertProblem = false
        while (t != null && depth < 10) {
            if (t is UntrustedCertificateException) return t
            if (t is CertificateException || t is javax.net.ssl.SSLHandshakeException) sawCertProblem = true
            t = t.cause
            depth++
        }
        val rejected = lastRejected
        return if (sawCertProblem && rejected != null) UntrustedCertificateException(rejected, e) else null
    }

    /** Certificaat uit een WebView-SSL-fout halen (ook op Android 8–9). */
    fun fromSslCertificate(cert: SslCertificate?): X509Certificate? {
        if (cert == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cert.x509Certificate
            } else {
                val bytes = SslCertificate.saveState(cert).getByteArray("x509-certificate") ?: return null
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
            }
        } catch (e: Exception) {
            null
        }
    }
}
