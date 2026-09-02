package com.music.bitchord.auth

import android.webkit.CookieManager
import com.music.bitchord.data.DebugLog as Log

/** What the in-app browser is being opened for. */
enum class WebSessionMode {
    /** No usable session yet: sign in to Google. */
    SIGN_IN,

    /**
     * Already signed in, but on the wrong channel. Opens YouTube Music itself
     * so its own Accounts switcher can be used, and takes the session from
     * whatever page the listener ends up on.
     */
    SWITCH_CHANNEL,
}

/**
 * A session lifted out of the in-app browser: the cookie, plus who the page
 * being looked at says it is.
 *
 * The identity fields come from the live page's `ytcfg` rather than from a
 * later server-side fetch of the shell, and that is the entire point. Which
 * channel YouTube Music serves by default is not a question this app gets to
 * answer, but which channel the page in front of the listener is *currently*
 * showing is not a question at all — it is written down in the page. Reading
 * it there is what lets "switch to the channel I want, then save" work.
 *
 * All identity fields are nullable: a page that will not give them up leaves
 * the app exactly where it was before, scraping the shell for its best guess.
 */
data class CapturedSession(
    val cookie: String,
    /** `DELEGATED_SESSION_ID` — set only while a brand channel is selected. */
    val pageId: String?,
    /** `DATASYNC_ID`, account half only. */
    val dataSyncId: String?,
    /** `SESSION_INDEX` — which Google account in the cookie jar. */
    val authUser: String?,
    val visitorData: String?,
    val clientVersion: String?,
    /** Whether the page reported itself signed in at all. */
    val loggedIn: Boolean,
)

/**
 * The WebView's own cookie jar, which is not the app's.
 *
 * These are separate stores and the difference is invisible until it bites:
 * signing out of BitChord forgets the cookie the app makes requests with, and
 * leaves the browser's copy untouched. The next sign-in then loads
 * accounts.google.com, is recognised immediately, redirects straight through
 * to music.youtube.com and hands back a cookie for the account that was just
 * signed out of — a sign-in screen that cannot be used to sign in as anyone
 * else, and shows barely a flicker while refusing to.
 */
object BrowserSession {

    /**
     * Forgets the Google login the in-app browser is holding.
     *
     * Google's cookies only, by name, rather than [CookieManager.removeAllCookies]:
     * the same jar holds the Discord and Last.fm logins from their own in-app
     * browsers, and signing out of YouTube Music is not a reason to sign out of
     * those. There is no per-domain removal in the API, so each cookie is
     * overwritten with an expired one of the same name.
     */
    fun clearGoogleCookies() {
        // Best effort throughout. CookieManager needs a WebView provider, and
        // on a device that is mid-update or has none there isn't one — which is
        // a reason for the next sign-in to be less convenient, not a reason for
        // signing out to crash.
        val manager = runCatching { CookieManager.getInstance() }.getOrElse {
            Log.w("BitChord", "no cookie manager to clear: ${it.message}")
            return
        }
        var cleared = 0
        GOOGLE_ORIGINS.forEach { origin ->
            val jar = manager.getCookie(origin) ?: return@forEach
            val host = origin.substringAfter("://")
            jar.split(';').forEach { entry ->
                val name = entry.substringBefore('=').trim()
                if (name.isEmpty()) return@forEach
                // Both the host-only and the domain-wide form: a cookie set on
                // `.google.com` is not removed by expiring it on the host, and
                // which of the two a given cookie used is not recorded here.
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/")
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$host")
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.$host")
                cleared++
            }
        }
        runCatching { manager.flush() }
        Log.d("BitChord", "cleared $cleared browser cookies for Google")
    }

    private val GOOGLE_ORIGINS = listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
        "https://accounts.google.com",
        "https://www.google.com",
        "https://google.com",
    )
}
