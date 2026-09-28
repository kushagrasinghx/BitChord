package com.music.bitchord.auth

import android.annotation.SuppressLint
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
import com.music.bitchord.data.service.ServiceConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private const val TAG = "BitChord"

/**
 * In-app sign-in for the streaming service, and the way to change which channel
 * it listens as.
 *
 * [WebSessionMode.SIGN_IN] loads the standard web login from the imported
 * service file. The user authenticates directly against the real login page
 * (2FA, passkeys etc. all work — it's the real page). When the service
 * redirects back to the login origin the listener confirms the profile shown
 * by the live page. This deliberately leaves a multiple-channel account
 * enough time to choose its identity before anything is saved. The browser's
 * local cookies are cleared on the way in without visiting any logout
 * endpoint, so adding an account cannot invalidate a previously saved
 * session — see [BrowserSession.clearLoginCookies].
 *
 * [WebSessionMode.SWITCH_CHANNEL] keeps those cookies (or installs the saved
 * snapshot for [initialCookie]) and opens the service itself, so the listener
 * can use the avatar menu's own Accounts list — the one screen that
 * authoritatively knows which channels exist and which is which. Nothing is
 * taken automatically there: the session is read when they say so, by raising
 * [captureRequest].
 *
 * Either way what is read is the page's own `ytcfg`, not a guess made later
 * from a server-side fetch. The credential itself never passes through app code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicLoginScreen(
    mode: WebSessionMode,
    /** Cookie snapshot to install before opening an existing account's channel picker. */
    initialCookie: String? = null,
    onCaptured: (CapturedSession) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Raise to take the session from the page as it stands. Ignored at its
     * initial value, so arriving on the screen doesn't capture anything.
     */
    captureRequest: Int = 0,
    /** Told when a capture was asked for and there was no session to take. */
    onCaptureUnavailable: () -> Unit = {},
    /** True once a signed-in service page is available for confirmation. */
    onPageReady: (Boolean) -> Unit = {},
    loginOrigin: String = ServiceConfig.loginOrigin(),
    loginUrl: String = ServiceConfig.loginUrl(),
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnUnavailable by rememberUpdatedState(onCaptureUnavailable)
    val currentOnPageReady by rememberUpdatedState(onPageReady)

    LaunchedEffect(captureRequest) {
        if (captureRequest == 0) return@LaunchedEffect
        val view = webView
        if (view == null) currentOnUnavailable()
        else captureFrom(view, loginOrigin, currentOnCaptured, currentOnUnavailable)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            // Clearing the local jar is enough to show a fresh login.
            // Never visit a logout endpoint here: that invalidates a
            // previously saved account on the server, so adding account B
            // silently breaks account A.
            if (mode == WebSessionMode.SIGN_IN) BrowserSession.clearLoginCookies()
            else initialCookie?.let(BrowserSession::installLoginCookies)
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Reaching the service origin only enables
                        // confirmation. A multi-channel login can still be
                        // waiting for the listener to choose an identity on
                        // this very page. Capturing automatically here is the
                        // race that used to create a fake profile and close
                        // too soon.
                        currentOnPageReady(url?.startsWith(loginOrigin) == true)
                    }
                }

                webView = this
                loadUrl(if (mode == WebSessionMode.SIGN_IN) loginUrl else "$loginOrigin/")
            }
        },
    )
}

/**
 * Takes the session from [view], if it is holding one.
 *
 * Calls [onUnavailable] when the cookie jar has no signing secret in it yet —
 * the page is mid-login, or is not a service page at all — and the caller
 * should leave the screen open rather than saving something that cannot sign
 * a request. See [AuthStore.hasApiSid].
 */
private fun captureFrom(
    view: WebView,
    loginOrigin: String,
    onCaptured: (CapturedSession) -> Unit,
    onUnavailable: () -> Unit,
) {
    val cookies = CookieManager.getInstance().getCookie(loginOrigin)
    if (cookies == null || !AuthStore.hasApiSid(cookies)) {
        onUnavailable()
        return
    }
    // Flushed here rather than left to the WebView's own schedule: the screen
    // is usually closing in the next frame, and a cookie jar written after
    // that is a jar the next sign-in reads instead of this one.
    CookieManager.getInstance().flush()

    view.evaluateJavascript(YTCFG_PROBE) { raw ->
        val config = raw.parseConfig()
        val loggedIn = config?.get("loggedIn").let {
            it is JsonPrimitive && it.content == "true"
        }
        if (config == null || !loggedIn) {
            Log.w(TAG, "confirmation requested before the page exposed a signed-in identity")
            onUnavailable()
            return@evaluateJavascript
        }
        val pageId = config.string("pageId")
        onCaptured(
            CapturedSession(
                cookie = cookies,
                // A delegated identity is the most specific answer the live
                // page can give. Otherwise normalise its DATASYNC_ID.
                dataSyncId = pageId ?: normalizeDataSyncId(config.string("dataSyncId")),
                pageId = pageId,
                authUser = config.string("authUser"),
                visitorData = config.string("visitorData"),
                clientVersion = config.string("clientVersion"),
                loggedIn = true,
            ),
        )
    }
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
