package com.music.bitchord.desktop

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.StreamResolver
import java.security.MessageDigest
import java.util.Locale

/** Who this app is acting as when it talks to YouTube, and how it proves it. */
internal object DesktopYouTubeAuth {

    const val MUSIC_ORIGIN = "https://music.youtube.com"
    const val YOUTUBE_ORIGIN = "https://www.youtube.com"

    /**
     * The session in force, or null to browse as a guest.
     * @param authUser which entry in the cookie jar, as `X-Goog-AuthUser`.
     * @param pageId the brand channel, as `X-Goog-PageId`. Null while a
     * @param dataSyncId the account, as `context.user.onBehalfOfUser`. Belongs
     */
    data class Session(
        val cookie: String,
        val authUser: String = "0",
        val pageId: String? = null,
        val dataSyncId: String? = null,
    )

    @Volatile
    var session: Session? = null
        private set

    /** Whether requests will be made as somebody rather than as nobody. */
    val isSignedIn: Boolean get() = session != null

    /** The cookie a headless run can be given without an accounts screen. */
    fun environmentCookie(): String? =
        System.getenv("BITCHORD_YTMUSIC_COOKIE")?.trim()?.takeIf { it.isNotBlank() }

    fun adopt(value: Session?) {
        if (session == value) return
        session = value
        // The YouTube Music client is the phone's, shared; it signs with this
        // cookie and addresses this channel.
        Innertube.cookie = value?.cookie
        Innertube.selectChannel(value?.pageId, value?.dataSyncId, value?.authUser)
        // A new session means a new player config and PoToken binding, as on the phone.
        StreamResolver.onSessionChanged()
        DesktopTrackLog.log(
            if (value == null) {
                "youtube: signed out; requests are anonymous again"
            } else {
                "youtube: signed in, acting as authUser=${value.authUser}" +
                    (value.pageId?.let { " pageId=$it" } ?: " on the personal channel")
            },
        )
    }

    /** The headers an authenticated request carries, or nothing when signed out. */
    fun headers(origin: String = MUSIC_ORIGIN): Map<String, String> {
        val current = session ?: return emptyMap()
        val secret = signingSecret(current.cookie) ?: return emptyMap()
        return buildMap {
            put("Cookie", current.cookie)
            put("Authorization", sapisidHash(secret, origin))
            put("X-Goog-AuthUser", current.authUser)
            put("X-Origin", origin)
            current.pageId?.takeIf { it.isNotBlank() }?.let { put("X-Goog-PageId", it) }
        }
    }

    /**
     * The `Authorization` value for [cookie] at [origin], or null when the jar holds no signing
     * secret.
     */
    fun signatureFor(cookie: String, origin: String): String? =
        signingSecret(cookie)?.let { sapisidHash(it, origin) }

    /** `onBehalfOfUser`, which rides in the request body rather than a header. */
    fun onBehalfOfUser(): String? = session?.dataSyncId?.takeIf { it.isNotBlank() }

    /** The cookie the signature is taken over. */
    private fun signingSecret(cookie: String): String? {
        val jar = cookie.split(';').mapNotNull { entry ->
            val name = entry.substringBefore('=').trim()
            val value = entry.substringAfter('=', "").trim()
            if (name.isEmpty() || value.isEmpty()) null else name to value
        }.toMap()
        return SIGNING_COOKIES.firstNotNullOfOrNull { jar[it] }
    }

    /**
     * `SAPISIDHASH <seconds>_<sha1(seconds + secret + origin)>`, the scheme Google's web clients
     * use.
     */
    private fun sapisidHash(secret: String, origin: String): String {
        val timestamp = System.currentTimeMillis() / 1_000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $secret $origin".toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }

    private val SIGNING_COOKIES = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")
}
