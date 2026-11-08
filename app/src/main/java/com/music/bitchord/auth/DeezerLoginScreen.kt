package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val LOGIN_URL = "https://www.deezer.com/login"

private val DEEZER_COOKIE_URLS = listOf(
    "https://www.deezer.com",
    "https://deezer.com",
    "https://connect.deezer.com",
)

private val LOGIN_HOST_SUFFIXES = listOf(
    "deezer.com", "dzcdn.net", "google.com", "gstatic.com", "facebook.com", "apple.com",
)

private fun hostMatches(uri: Uri?, suffixes: List<String>): Boolean {
    if (uri?.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return suffixes.any { host == it || host.endsWith(".$it") }
}

private fun isLoginHost(uri: Uri?) = hostMatches(uri, LOGIN_HOST_SUFFIXES)

/**
 * The `arl` value from any of [cookieHeaders]. Every jar is searched: www.deezer.com
 * can hold consent cookies while `arl` sits host-only on deezer.com.
 */
internal fun arlFromCookies(vararg cookieHeaders: String?): String? =
    cookieHeaders.asSequence()
        .filterNotNull()
        .flatMap { it.split(";") }
        .map { it.trim() }
        .firstOrNull { it.startsWith("arl=") && it.length > "arl=".length }
        ?.substringAfter("=")

/**
 * Signs the login WebView out of Deezer so disconnecting does not immediately
 * sign the next WebView session back into the same account.
 */
fun clearDeezerWebSession() {
    val cookies = CookieManager.getInstance()
    for (url in DEEZER_COOKIE_URLS) {
        val names = cookies.getCookie(url)?.split(";")
            ?.map { it.substringBefore("=").trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        for (name in names) {
            val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
            cookies.setCookie(url, expired)
            cookies.setCookie(url, "$expired; Domain=.deezer.com")
        }
    }
    cookies.flush()
}

/**
 * In-app Deezer sign-in. Lets `deezer.com/login` handle authentication, then
 * hands the `arl` cookie to [onConnected] as soon as it is set on `deezer.com`.
 *
 * Hosted as a root overlay outside the blurred content subtree, like [SpotifyLoginScreen].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(
    onConnected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = browserUserAgent(settings.userAgentString)
                setBackgroundColor(0xFF121212.toInt())
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                var sent = false
                fun checkCookie(view: WebView) {
                    if (sent) return
                    val cookies = CookieManager.getInstance()
                    val token = arlFromCookies(
                        cookies.getCookie("https://www.deezer.com"),
                        cookies.getCookie("https://deezer.com"),
                    ) ?: return
                    sent = true
                    view.visibility = View.GONE
                    onConnected(token)
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = !isLoginHost(request.url)

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        canGoBack = view.canGoBack()
                        checkCookie(view)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()
                        checkCookie(view)
                    }
                }
                webView = this
                // A leftover arl (restored app data, an earlier token flow) would
                // be "found" at once and re-link a stale account unasked.
                clearDeezerWebSession()
                loadUrl(LOGIN_URL)
            }
        },
        onRelease = { it.destroy() },
    )

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
