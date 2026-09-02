package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.music.bitchord.data.DebugLog as Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private const val MUSIC_ORIGIN = "https://music.youtube.com"

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin" +
        "?ltmpl=music&service=youtube&passive=true" +
    "&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/**
 * CookieManager cannot expire every Google HttpOnly cookie by name. Logging
 * out inside this WebView is the reliable way to make Add account present the
 * account chooser, without touching BitChord's separately encrypted sessions.
 */
private val LOGOUT_THEN_LOGIN_URL =
    "https://accounts.google.com/Logout?continue=${Uri.encode(LOGIN_URL)}"

private const val TAG = "BitChord"

/**
 * In-app Google sign-in for YouTube Music, and the way to change which channel
 * it listens as.
 *
 * [WebSessionMode.SIGN_IN] loads the standard Google web login with
 * `continue=music.youtube.com`. The user authenticates directly against
 * accounts.google.com (2FA, passkeys etc. all work — it's the real page). When
 * Google redirects back to music.youtube.com the session is taken automatically
 * and the screen closes. The browser's Google cookies are cleared on the way in,
 * or a listener who signed out would be waved straight back through as the
 * account they were trying to leave — see [BrowserSession.clearGoogleCookies].
 *
 * [WebSessionMode.SWITCH_CHANNEL] keeps those cookies and opens YouTube Music
 * itself, so the listener can use the avatar menu's own Accounts list — the one
 * screen that authoritatively knows which channels exist and which is which.
 * Nothing is taken automatically there: the session is read when they say so,
 * by raising [captureRequest].
 *
 * Either way what is read is the page's own `ytcfg`, not a guess made later
 * from a server-side fetch. The credential itself never passes through app code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicLoginScreen(
    mode: WebSessionMode,
    onCaptured: (CapturedSession) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Raise to take the session from the page as it stands. Ignored at its
     * initial value, so arriving on the screen doesn't capture anything.
     */
    captureRequest: Int = 0,
    /** Told when a capture was asked for and there was no session to take. */
    onCaptureUnavailable: () -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnUnavailable by rememberUpdatedState(onCaptureUnavailable)

    LaunchedEffect(captureRequest) {
        if (captureRequest == 0) return@LaunchedEffect
        val view = webView
        if (view == null || !captureFrom(view, currentOnCaptured)) currentOnUnavailable()
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            if (mode == WebSessionMode.SIGN_IN) BrowserSession.clearGoogleCookies()
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    private var captured = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Only [WebSessionMode.SIGN_IN] finishes by itself. In
                        // the switch flow the first music.youtube.com page is
                        // where the listener starts, not where they are done —
                        // grabbing the session there would save the channel
                        // they came to change.
                        if (mode != WebSessionMode.SIGN_IN) return
                        if (captured || url?.startsWith(MUSIC_ORIGIN) != true) return
                        if (view != null && captureFrom(view, currentOnCaptured)) captured = true
                    }
                }

                webView = this
                loadUrl(if (mode == WebSessionMode.SIGN_IN) LOGOUT_THEN_LOGIN_URL else "$MUSIC_ORIGIN/")
            }
        },
    )
}

/**
 * Takes the session from [view], if it is holding one.
 *
 * @return whether there was one to take. False means the cookie jar has no
 *   signing secret in it yet — the page is mid-login, or is not a YouTube page
 *   at all — and the caller should leave the screen open rather than saving
 *   something that cannot sign a request. See [AuthStore.hasApiSid].
 */
private fun captureFrom(view: WebView, onCaptured: (CapturedSession) -> Unit): Boolean {
    val cookies = CookieManager.getInstance().getCookie(MUSIC_ORIGIN)
    if (cookies == null || !AuthStore.hasApiSid(cookies)) return false
    // Flushed here rather than left to the WebView's own schedule: the screen
    // is usually closing in the next frame, and a cookie jar written after
    // that is a jar the next sign-in reads instead of this one.
    CookieManager.getInstance().flush()

    view.evaluateJavascript(YTCFG_PROBE) { raw ->
        val config = raw.parseConfig()
        if (config == null) {
            Log.w(TAG, "no ytcfg on the page; falling back to the shell for identity")
        }
        onCaptured(
            CapturedSession(
                cookie = cookies,
                // `<accountSyncId>||<sessionSyncId>` — only the first half
                // names the account; the second changes on its own schedule.
                dataSyncId = config?.string("dataSyncId")?.substringBefore("||"),
                pageId = config?.string("pageId"),
                authUser = config?.string("authUser"),
                visitorData = config?.string("visitorData"),
                clientVersion = config?.string("clientVersion"),
                loggedIn = config?.get("loggedIn").let { it is JsonPrimitive && it.content == "true" },
            ),
        )
    }
    return true
}

/**
 * The identity of the page as the page itself has it.
 *
 * Returns an object rather than a string so the WebView serialises it — a
 * probe that stringified its own result would come back double-encoded. A page
 * without `ytcfg` (an error page, a redirect that hasn't landed) returns null,
 * which is a fine answer and not an error.
 */
private const val YTCFG_PROBE = """
(function () {
  try {
    if (!window.ytcfg || !window.ytcfg.get) return null;
    var get = function (key) {
      var value = window.ytcfg.get(key);
      return (value === undefined || value === null || value === '') ? null : String(value);
    };
    return {
      loggedIn: String(!!window.ytcfg.get('LOGGED_IN')),
      pageId: get('DELEGATED_SESSION_ID'),
      dataSyncId: get('DATASYNC_ID'),
      authUser: get('SESSION_INDEX'),
      visitorData: get('VISITOR_DATA'),
      clientVersion: get('INNERTUBE_CLIENT_VERSION')
    };
  } catch (e) {
    return null;
  }
})()
"""

private val json = Json { ignoreUnknownKeys = true }

/** The probe's result, or null for anything that isn't the object it promises. */
private fun String?.parseConfig(): JsonObject? =
    this?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
