package nl.planernl.unraider.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import nl.planernl.unraider.R
import nl.planernl.unraider.data.CertTrust
import nl.planernl.unraider.ui.ErrorState

/**
 * De gewone Unraid-webGUI als vangnet voor alles wat (nog) niet native in de app zit.
 * Inloggen gaat met je normale Unraid-gebruiker; cookies blijven bewaard.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebGuiScreen(baseUrl: String, certPin: String?, path: String = "") {
    val startUrl = baseUrl + path
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Vooraf ophalen: de WebView-callbacks zijn geen composables
    val unreachable = stringResource(R.string.webgui_unreachable)
    val certRejected = stringResource(R.string.webgui_cert_rejected)

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            loading = true
                            error = null
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            canGoBack = view.canGoBack()
                            CookieManager.getInstance().flush()
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                        }

                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, err: SslError) {
                            // Alleen het certificaat accepteren dat je in de instellingen hebt goedgekeurd
                            val cert = CertTrust.fromSslCertificate(err.certificate)
                            if (certPin != null && cert != null && CertTrust.fingerprint(cert) == certPin) {
                                handler.proceed()
                            } else {
                                handler.cancel()
                                loading = false
                                error = certRejected
                            }
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                            if (request.isForMainFrame) {
                                loading = false
                                error = unreachable.format(err.description)
                            }
                        }
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#141619"))
                    loadUrl(startUrl)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
        )

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = nl.planernl.unraider.ui.theme.Rack.accent)

        error?.let { msg ->
            ErrorState(msg) {
                error = null
                webView?.loadUrl(startUrl)
            }
        }
    }
}
