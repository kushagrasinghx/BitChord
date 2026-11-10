/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube

import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.innertube.utils.sha1
import java.util.Locale

data class PlaybackAuthState(
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val poToken: String? = null,
    val poTokenGvs: String? = null,
    val poTokenGvsSession: String? = null,
    val poTokenGvsVideoId: String? = null,
    val poTokenPlayer: String? = null,
    val poTokenPlayerVideoId: String? = null,
    val poTokenSubs: String? = null,
    val poTokenSubsVideoId: String? = null,
    val webClientPoTokenEnabled: Boolean = false,
    /**
     * OAuth2 access token ("ya29…") for clients that authenticate with a Bearer header instead of
     * cookies — currently only the ANDROID_VR clients, via the device-code flow.
     *
     * Only the *access* token lives here. The refresh token is deliberately not part of this state:
     * nothing in core refreshes anything, so carrying it would put a long-lived credential into
     * every fingerprint and log line for no gain. The app owns the refresh lifecycle and pushes a
     * fresh access token down.
     */
    val oauthToken: String? = null,
) {
    val hasLoginCookie: Boolean
        get() = hasYouTubeLoginCookie(cookie)

    val hasPlaybackLoginContext: Boolean
        get() = hasLoginCookie && !dataSyncId.isNullOrBlank()

    /** True when a Bearer token is available, i.e. the OAuth path can authenticate a request. */
    val hasOAuthContext: Boolean
        get() = !oauthToken.isNullOrBlank()

    val sessionId: String?
        get() = if (hasPlaybackLoginContext) dataSyncId.userSessionIdOrNull() else visitorData

    val fingerprint: String
        get() =
            sha1(
                listOf(
                    cookie.orEmpty(),
                    visitorData.orEmpty(),
                    dataSyncId.orEmpty(),
                    poToken.orEmpty(),
                    poTokenGvs.orEmpty(),
                    poTokenGvsSession.orEmpty(),
                    poTokenGvsVideoId.orEmpty(),
                    poTokenPlayer.orEmpty(),
                    poTokenPlayerVideoId.orEmpty(),
                    poTokenSubs.orEmpty(),
                    poTokenSubsVideoId.orEmpty(),
                    webClientPoTokenEnabled.toString(),
                    // Included so signing in or out of the OAuth path invalidates the caches keyed
                    // on this, exactly as a cookie change does. Without it a switch between the
                    // WebView and OAuth accounts would serve the previous account's responses.
                    oauthToken.orEmpty(),
                ).joinToString(separator = "\u0000"),
            )

    val streamCacheFingerprint: String
        get() =
            sha1(
                listOf(
                    cookie.orEmpty(),
                    visitorData.orEmpty(),
                    dataSyncId.orEmpty(),
                ).joinToString(separator = "\u0000"),
            )

    fun normalized(): PlaybackAuthState =
        copy(
            cookie = cookie.normalizeAuthValue(),
            visitorData = visitorData.normalizeAuthValue(),
            dataSyncId = dataSyncId.normalizeDataSyncId(),
            poToken = poToken.normalizeAuthValue(),
            poTokenGvs = poTokenGvs.normalizeAuthValue(),
            poTokenGvsSession = poTokenGvsSession.normalizeAuthValue(),
            poTokenGvsVideoId = poTokenGvsVideoId.normalizeAuthValue(),
            poTokenPlayer = poTokenPlayer.normalizeAuthValue(),
            poTokenPlayerVideoId = poTokenPlayerVideoId.normalizeAuthValue(),
            poTokenSubs = poTokenSubs.normalizeAuthValue(),
            poTokenSubsVideoId = poTokenSubsVideoId.normalizeAuthValue(),
            oauthToken = oauthToken.normalizeAuthValue(),
        )

    fun resolvePlayerPoToken(
        client: YouTubeClient,
        explicitPoToken: String? = null,
        videoId: String? = null,
    ): String? {
        val explicit = explicitPoToken.normalizeAuthValue()
        if (explicit != null) return explicit
        if (!webClientPoTokenEnabled) return null
        if (!client.useWebPoTokens || !supportsWebPoToken(client)) return null
        return poTokenPlayer?.takeIf { poTokenPlayerVideoId == videoId }
            ?: poToken
    }

    fun resolveGvsPoToken(
        client: YouTubeClient? = null,
        videoId: String? = null,
    ): String? {
        if (client != null && (!client.useWebPoTokens || !supportsWebPoToken(client))) return null
        if (!webClientPoTokenEnabled) return null

        return poTokenGvs?.takeIf { poTokenGvsVideoId == videoId }
            ?: poToken
    }

    fun resolveSubsPoToken(
        client: YouTubeClient,
        videoId: String,
    ): String? {
        if (!client.useWebPoTokens || !supportsWebPoToken(client)) return null
        if (!webClientPoTokenEnabled) return null
        return poTokenSubs?.takeIf { poTokenSubsVideoId == videoId }
            ?: poTokenGvs?.takeIf { poTokenGvsVideoId == videoId }
            ?: poToken
    }

    companion object {
        val EMPTY = PlaybackAuthState()

        fun supportsGvsPoToken(client: YouTubeClient): Boolean {
            return supportsWebPoToken(client)
        }

        private fun supportsWebPoToken(client: YouTubeClient): Boolean {
            val name = client.clientName.uppercase(Locale.US)
            return name == "WEB" ||
                name == "WEB_REMIX" ||
                name == "WEB_CREATOR" ||
                name == "MWEB"
        }
    }
}

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? {
    val normalized = this.normalizeAuthValue()?.decodePercentEscapes() ?: return null
    val separatorIndex = normalized.indexOf("||")
    if (separatorIndex < 0) return normalized

    val delegatedSessionId = normalized.substringBefore("||").trim()
    val userSessionId = normalized.substring(separatorIndex + 2).trim()
    return when {
        delegatedSessionId.isBlank() -> userSessionId.takeIf(String::isNotBlank)
        userSessionId.isBlank() -> delegatedSessionId
        else -> "$delegatedSessionId||$userSessionId"
    }
}

private fun String?.userSessionIdOrNull(): String? {
    val normalized = this.normalizeAuthValue() ?: return null
    val separatorIndex = normalized.indexOf("||")
    return normalized
        .substring(if (separatorIndex >= 0) separatorIndex + 2 else 0)
        .trim()
        .takeIf(String::isNotBlank)
}

private fun String.decodePercentEscapes(): String {
    if (!contains('%')) return this

    val builder = StringBuilder(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '%' && index + 2 < length) {
            val high = Character.digit(this[index + 1], 16)
            val low = Character.digit(this[index + 2], 16)
            if (high >= 0 && low >= 0) {
                builder.append(((high shl 4) + low).toChar())
                index += 3
                continue
            }
        }
        builder.append(char)
        index += 1
    }
    return builder.toString()
}
