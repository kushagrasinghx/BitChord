package com.music.bitchord.data.innertube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One client identity for the `player` endpoint.
 *
 * YouTube hands out stream URLs per client, and which clients are answered
 * changes without notice: an identity that returns `OK` today answers
 * `LOGIN_REQUIRED` next month, and one that is merely *old* is refused with a
 * bare HTTP 400 before playability is even considered. So this is a list to
 * walk rather than a constant — see [StreamResolver].
 *
 * Three things travel together and must not be separated:
 *
 *  - **[userAgent]**, which the media fetch has to repeat. googlevideo bakes
 *    the client into the URL as `c=`/`cver=` and compares it against the
 *    headers of the request that comes back for the bytes.
 *  - **[origin]**, sent only by the browser-shaped clients, and pointing at
 *    the host that client actually runs on. Native app clients send none, and
 *    sending one anyway is as wrong as omitting it from a web client.
 *  - **[needsSignatureTimestamp]**, which decides whether the player request
 *    has to carry a timestamp lifted from YouTube's player JavaScript. The
 *    clients that need it are the ones that answer with ciphered formats.
 *
 * The versions here are load-bearing and were checked against the live
 * endpoint rather than copied from anywhere; see each one's note.
 */
data class PlayerClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    /** The host this client runs on, for browser-shaped clients only. */
    val origin: String? = null,
    /** Ciphered formats can't be unlocked without one. */
    val needsSignatureTimestamp: Boolean = false,
) {
    val referer: String? get() = origin?.let { "$it/" }

    /** Browser-shaped clients are served from their own host; app clients from YouTube proper. */
    val usesMusicHost: Boolean get() = origin == MUSIC_ORIGIN

    /**
     * Headers the *media* request must carry for a URL this client minted.
     *
     * The stream fetch is a separate request from the one that produced the
     * URL, and googlevideo treats a mismatch between the two as reason enough
     * to throttle the response to a crawl or refuse it with 403.
     */
    fun mediaHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        origin?.let { put("Origin", it) }
        referer?.let { put("Referer", it) }
    }

    companion object {
        private const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

        /**
         * iPhone YouTube, and the one that carries the session in practice: it
         * is answered without a login, without a proof of origin token and
         * without a signature timestamp, and it returns plain `url` fields —
         * so a stream is one POST away with no player JavaScript in the path.
         *
         * The version is the whole ballgame. Anything Google considers stale is
         * refused with an HTTP 400 before playability is looked at, which is
         * not a "try the next format" failure but a "this identity is dead"
         * one. 19.29.1 is already past that line; these two are not.
         */
        val IOS = PlayerClient(
            clientName = "IOS",
            clientVersion = "20.03.02",
            clientId = "5",
            userAgent = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)",
            osName = "iPhone",
            osVersion = "18.2.1.22C161",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
        )

        /** A newer build of the same app: refused on a different schedule. */
        val IOS_RECENT = IOS.copy(
            clientVersion = "20.10.4",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osVersion = "18.3.2.22D82",
        )

        /**
         * The phone YouTube app. Answers `OK` where the others are turned away,
         * but every format comes back ciphered — so reaching it costs a
         * download of YouTube's player JavaScript and a signature to solve.
         * Worth it as a fallback; not worth it first.
         */
        val ANDROID = PlayerClient(
            clientName = "ANDROID",
            clientVersion = "21.10.38",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.10.38 " +
                "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro",
            androidSdkVersion = "35",
            needsSignatureTimestamp = true,
        )

        /**
         * The Quest's YouTube app, and the first thing to try: unciphered,
         * login-free, no proof-of-origin token and no signature timestamp, so
         * a stream is one POST away with no player JavaScript in the path.
         *
         * Worth knowing when this list is next revisited: whether it is
         * answered depends on the *network the request leaves from*, not on
         * the app or the account. It serves a phone on mobile data or home
         * wifi while answering the same request from a datacentre or a
         * hard-used address with `LOGIN_REQUIRED` / "Sign in to confirm you're
         * not a bot". A check run from anywhere but the device is measuring
         * the wrong thing.
         */
        val ANDROID_VR = PlayerClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
                "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
        )

        /** An older build of the same app; refused on a different schedule. */
        val ANDROID_VR_LEGACY = ANDROID_VR.copy(
            clientVersion = "1.43.32",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
                "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
        )

        /** Last resort: the web player itself. Always ciphered, and usually refused. */
        val WEB_REMIX = PlayerClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260114.01.00",
            clientId = "67",
            userAgent = WEB_USER_AGENT,
            origin = MUSIC_ORIGIN,
            needsSignatureTimestamp = true,
        )

        /**
         * Identities we never mint with, but may still have to fetch for:
         * [forStreamUrl] dresses a URL however that URL says it was made, and
         * the extraction failsafe picks its own client without asking.
         */
        private val WEB = PlayerClient(
            clientName = "WEB",
            clientVersion = "2.20260114.00.00",
            clientId = "1",
            userAgent = WEB_USER_AGENT,
            origin = YOUTUBE_ORIGIN,
        )

        private val TVHTML5 = PlayerClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260114.00.00",
            clientId = "7",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 " +
                "(KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
            origin = YOUTUBE_ORIGIN,
        )

        /**
         * The client a googlevideo URL says minted it, so the media fetch can
         * be dressed as that client whatever produced the URL — including the
         * extraction failsafe, which picks a client of its own choosing.
         *
         * Falls back to [IOS] when the URL names a client we don't model: it is
         * what mints most of them here, and being approximately right beats
         * sending a smart TV's headers for a URL an iPhone asked for.
         */
        fun forStreamUrl(url: String): PlayerClient {
            val parsed = url.toHttpUrlOrNull() ?: return IOS
            val name = parsed.queryParameter("c")?.uppercase() ?: return IOS
            val version = parsed.queryParameter("cver")
            return when {
                name.startsWith("IOS") ->
                    if (version == IOS_RECENT.clientVersion) IOS_RECENT else IOS
                name == "ANDROID_VR" ->
                    if (version == ANDROID_VR_LEGACY.clientVersion) ANDROID_VR_LEGACY else ANDROID_VR
                name.startsWith("ANDROID") -> ANDROID
                name.startsWith("TVHTML5") -> TVHTML5
                name == "WEB_REMIX" -> WEB_REMIX
                name.startsWith("WEB") || name == "MWEB" -> WEB
                else -> IOS
            }
        }
    }
}
