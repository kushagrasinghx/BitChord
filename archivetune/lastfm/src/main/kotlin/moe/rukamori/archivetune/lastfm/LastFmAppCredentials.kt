/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lastfm

object LastFmAppCredentials {
    // NOTE ON THE "lastwave" NAME: the application name shown on Last.fm's
    // authorization page is rendered by Last.fm from THIS KEY'S registration
    // (the key was inherited from the LastWave project). Nothing in the app
    // can change it — displaying "ArchiveTune" there requires a key newly
    // registered at https://www.last.fm/api/account/create (name: ArchiveTune)
    // and swapped in here (or provided via LASTFM_API_KEY/LASTFM_SECRET in
    // local.properties, which the login screens already persist as overrides).
    // The User-Agent and every scrobble's client identifier already say
    // "ArchiveTune"; only the consent screen carries the old registration.
    const val API_KEY = "e2c8e7a67eaeb0fe5a71ee539a34641a"
    const val API_SECRET = "94b5c6aa634e459defedbf8180625e8a"

    const val AUTH_CALLBACK_URI = "archivetune://lastfm-auth-callback"

    fun authUrl(): String =
        "https://www.last.fm/api/auth/?api_key=$API_KEY&cb=$AUTH_CALLBACK_URI"
}
