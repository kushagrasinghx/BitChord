package com.music.bitchord.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import com.music.bitchord.auth.CapturedSession
import com.music.bitchord.auth.WebSessionMode

/**
 * Sign in from a bare cookie — what TV pairing hands over, where there is no
 * page to capture a session from. Everything past the session itself is the
 * phone's [MainViewModel.onWebSession].
 */
fun MainViewModel.onSignedIn(cookie: String, onComplete: (Boolean) -> Unit = {}) {
    val session = CapturedSession(
        cookie = cookie.trim(),
        pageId = null,
        dataSyncId = null,
        authUser = null,
        visitorData = null,
        clientVersion = null,
        loggedIn = true,
    )
    onWebSession(session, WebSessionMode.SIGN_IN, onComplete)
}

/**
 * The sign-in WebView, set up for a television: the default embedded user agent
 * makes Google refuse the login as "not secure", and a remote needs the page to
 * be focusable.
 */
@SuppressLint("SetJavaScriptEnabled")
fun configureTvSignInWebView(webView: WebView) {
    webView.apply {
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false

        // Stripping "; wv" and "Version/x" presents a standard browser user
        // agent, which Google's OAuth page accepts.
        val cleanUa = settings.userAgentString
            ?.replace("; wv", "")
            ?.replace(Regex("Version/[0-9.]+"), "")
            ?.replace("  ", " ")
            ?.trim()
        settings.userAgentString = cleanUa
            ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        // D-pad navigation.
        isFocusable = true
        isFocusableInTouchMode = true
    }
}
