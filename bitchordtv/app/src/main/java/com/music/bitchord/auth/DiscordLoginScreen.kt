package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
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
import com.music.bitchord.data.TrackLog

/**
 * Reads the token out of a completed login by dropping an iframe into the page
 * and alerting `localStorage.token` from it.
 *
 * The iframe is not decoration: Discord takes `localStorage` away from the
 * top-level window once the app boots, specifically so a snippet like this
 * can't read it. A fresh same-origin iframe gets an untouched handle on the
 * same store, where the key is still there. And `alert` is the channel because
 * it needs no `addJavascriptInterface` bridge — the string arrives in
 * [WebChromeClient.onJsAlert].
 *
 * Alerting `''` rather than throwing on a miss matters: the caller retries, and
 * a snippet that dies silently would leave the screen waiting forever.
 */
private const val TOKEN_SNIPPET = """
    (function () {
      try {
        var frame = document.createElement('iframe');
        document.body.appendChild(frame);
        var token = frame.contentWindow.localStorage.token;
        document.body.removeChild(frame);
        alert(token ? token.slice(1, -1) : '');
      } catch (e) {
        alert('');
      }
    })()
"""

/**
 * The token only lands in `localStorage` once the client has finished booting,
 * which is a moment or two after it starts navigating to `/app`, so the first
 * read usually comes back empty. Twelve tries at 500ms covers a slow boot
 * without spinning indefinitely on an account that never signed in.
 */
private const val MAX_TOKEN_ATTEMPTS = 12
private const val TOKEN_RETRY_MS = 500L

private val WEBVIEW_VERSION_TOKEN = Regex("""Version/\d+(\.\d+)*\s*""")

/**
 * Used only if the platform hands us a user agent we don't recognise, so that a
 * blank login page can never be the outcome of the string being unparseable.
 */
private const val FALLBACK_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

/**
 * Strips the two tokens that mark a user agent as coming from a WebView.
 *
 * Discord's parser reads the `; wv` and `Version/4.0` pair as "Android Browser
 * 4.0" — something it dropped support for years ago — and serves a shell that
 * never renders the login form, leaving the screen blank. Confirmed on a Galaxy
 * S22, where the page logs `WebRTC is not supported on Android Browser 4.0` and
 * then paints nothing. Kizzy hit the same wall and read it as a Motorola quirk
 * (dead8309/Kizzy#345), but the offending tokens are in every Android WebView's
 * user agent; only Discord's tolerance for them has changed.
 *
 * Editing the platform string rather than substituting a constant keeps the
 * real device and the real Chrome version in there, so this doesn't rot into
 * claiming to be a two-year-old browser the next time Discord raises the floor.
 */
private fun browserUserAgent(platformUserAgent: String?): String {
    val stripped = platformUserAgent
        ?.replace("; wv", "")
        ?.replace(WEBVIEW_VERSION_TOKEN, "")
        ?.replace("  ", " ")
        ?.trim()
    return stripped?.takeIf { it.contains("Chrome/") } ?: FALLBACK_USER_AGENT
}

/**
 * True once the page has left the sign-in flow, which is the point the token is
 * in `localStorage` and worth reading. Deliberately not a match on one path:
 * a completed login lands on `/app` or `/channels/@me` depending on how the
 * session was established, and matching the wrong one means a sign-in that
 * appears to succeed and then does nothing.
 */
private fun isSignedIn(url: String?): Boolean =
    url != null &&
        url.startsWith("https://discord.com/") &&
        !url.contains("/login") &&
        !url.contains("/register")

/**
 * In-app Discord sign-in for Rich Presence.
 *
 * Loads discord.com/login and lets the real page handle authentication — 2FA,
 * passkeys, the lot. Once the page leaves the sign-in flow this starts lifting
 * the session token out of it, handing it to [onTokenCaptured] once.
 *
 * There is no OAuth scope that lets a third party set a user's presence, so the
 * account's own token is the only credential that can do this. It goes straight
 * into the encrypted [AuthStore] and is never sent anywhere but Discord.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordLoginScreen(
    onTokenCaptured: (token: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Not cosmetic, and not redundant with the modifier above.
                // A WebView whose LayoutParams height is WRAP_CONTENT — which is
                // what Compose hands a bare view — makes Chromium set
                // force_zero_layout_height, so the *layout* viewport becomes 0
                // while the visual one stays correct. Discord sizes its entire
                // client off a `height: 100%` chain, so every box in it collapses
                // and the login form lays out to nothing: a fully populated DOM
                // that paints an empty screen, with no CSS able to fix it. The
                // YouTube login next door survives the same quirk only because
                // that page is ordinary document flow.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = browserUserAgent(settings.userAgentString)

                var captured = false
                var attempts = 0

                fun harvestToken() {
                    if (captured) return
                    if (attempts >= MAX_TOKEN_ATTEMPTS) {
                        TrackLog.w(
                            "Discord",
                            "no token in localStorage after $attempts tries",
                            about = null,
                        )
                        return
                    }
                    attempts++
                    evaluateJavascript(TOKEN_SNIPPET, null)
                }

                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        if (isSignedIn(url)) harvestToken()
                        return false
                    }

                    /**
                     * The one hook that also fires for `pushState`. Discord's
                     * client routes itself from the login form to the app without
                     * a real navigation, so [shouldOverrideUrlLoading] alone can
                     * miss the moment the token appears — and which URL it lands
                     * on differs between `/app` and `/channels/@me` depending on
                     * how the session was established.
                     */
                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        if (isSignedIn(url)) harvestToken()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        canGoBack = view?.canGoBack() == true
                        if (isSignedIn(url)) harvestToken()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult,
                    ): Boolean {
                        result.confirm()
                        if (captured) return true

                        val token = message.takeUnless {
                            it.isBlank() || it == "null" || it == "undefined"
                        }
                        if (token == null) {
                            // Client still booting — the key isn't written yet.
                            view.postDelayed({ harvestToken() }, TOKEN_RETRY_MS)
                            return true
                        }

                        captured = true
                        TrackLog.d("Discord", "token captured after $attempts read(s)", about = null)
                        // Hide before handing the token over, so the Discord
                        // client we're sitting on doesn't flash up as the
                        // overlay tears down.
                        view.visibility = View.GONE
                        onTokenCaptured(token)
                        return true
                    }
                }

                webView = this
                loadUrl("https://discord.com/login")
            }
        },
    )

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
