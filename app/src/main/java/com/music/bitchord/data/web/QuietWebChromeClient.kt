package com.music.bitchord.data.web

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient

/**
 * A [WebChromeClient] that keeps a page's `console` output out of logcat.
 *
 * Unless the client reports a console message handled, WebView writes it to
 * logcat itself, as a `chromium` CONSOLE line, and R8's Log stripping cannot
 * reach that. Every WebView the app opens uses this or a subclass: what a page
 * logs is either recorded where Copy Log reads it, or nowhere.
 */
internal open class QuietWebChromeClient : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
}
