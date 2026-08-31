package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val MUSIC_ORIGIN = "https://music.youtube.com"

/**
 * In-app Google sign-in for YouTube Music.
 *
 * Flow: load the standard Google web login with `continue=music.youtube.com`.
 * The user authenticates directly against accounts.google.com (2FA, passkeys
 * etc. all work — it's the real page). When Google redirects back to
 * music.youtube.com, the session cookies (SAPISID, __Secure-3PAPISID, ...)
 * land in the WebView's CookieManager; we lift the cookie header for the
 * music.youtube.com origin and hand it to [onCookiesCaptured] exactly once.
 * The credential itself never passes through app code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicLoginScreen(
    onCookiesCaptured: (cookieHeader: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    private var captured = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (captured || url?.startsWith(MUSIC_ORIGIN) != true) return
                        val cookies = CookieManager.getInstance().getCookie(MUSIC_ORIGIN)
                        // Not a substring test. See [AuthStore.hasApiSid] — the
                        // one this replaces accepted a jar with no signing
                        // secret in it, and the sign-in then appeared to
                        // succeed while every request stayed anonymous.
                        if (cookies != null && AuthStore.hasApiSid(cookies)) {
                            captured = true
                            onCookiesCaptured(cookies)
                        }
                    }
                }

                loadUrl(
                    "https://accounts.google.com/ServiceLogin" +
                        "?ltmpl=music&service=youtube&passive=true" +
                        "&continue=https%3A%2F%2Fmusic.youtube.com%2F",
                )
            }
        },
    )
}
