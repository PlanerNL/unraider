package nl.planernl.unraider.data

import android.content.Context
import android.webkit.CookieManager
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Laadt icoontjes van Docker-containers en plugins.
 *
 * - Externe icoontjes (meestal GitHub) gaan gewoon via internet.
 * - Icoontjes op de server zelf staan achter de webGUI-login. Voor verzoeken naar de
 *   server sturen we daarom de cookies van het WebGUI-tabblad mee: ben je daar één keer
 *   ingelogd, dan verschijnen ook plugin-icoontjes. Zonder login blijft het standaardicoon staan.
 * - Het goedgekeurde servercertificaat (Use SSL/TLS: Yes) wordt ook hier geaccepteerd.
 */
object IconLoader {

    fun create(context: Context, config: ServerConfig): ImageLoader {
        val serverHost = runCatching { URI(config.baseUrl).host }.getOrNull()
        val base = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                if (serverHost != null && req.url.host == serverHost) {
                    val cookies = runCatching { CookieManager.getInstance().getCookie(req.url.toString()) }.getOrNull()
                    if (!cookies.isNullOrBlank()) {
                        return@addInterceptor chain.proceed(req.newBuilder().header("Cookie", cookies).build())
                    }
                }
                chain.proceed(req)
            }
            .build()
        val client = CertTrust.client(base, config.certPin)

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
            .build()
    }
}

/** De ImageLoader voor de huidige server; null = Coil-standaard. */
val LocalIconLoader = staticCompositionLocalOf<ImageLoader?> { null }
