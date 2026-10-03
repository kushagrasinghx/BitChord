package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import kotlin.math.min

/**
 * Scrobbling to Last.fm and ListenBrainz.
 *
 * The API key and shared secret are baked in at build time from
 * `local.properties`, as on Android; the session key and the ListenBrainz token
 * come from [DesktopScrobbleSettings], which is what signing in writes.
 */
object DesktopScrobbling {
    private const val LAST_FM_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    private const val LISTENBRAINZ_ENDPOINT = "https://api.listenbrainz.org/1/submit-listens"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeSongId: String? = null
    private var activeStartedAtMs = 0L
    private var timerStartedAtMs = 0L
    private var remainingMs = 0L
    private var scrobbleJob: Job? = null
    private var lastFmScrobbled = false

    /** Whether this build can sign in at all — no key, no login. */
    val canSignInToLastFm: Boolean
        get() = apiKey != null && apiSecret != null

    val lastFmConfigured: Boolean
        get() = apiKey != null && apiSecret != null && sessionKey() != null &&
            DesktopScrobbleSettings.lastfmEnabled.value

    val listenBrainzConfigured: Boolean
        get() = listenBrainzToken() != null && DesktopScrobbleSettings.listenBrainzEnabled.value

    private val apiKey: String?
        get() = property("bitchord.lastfm.key") ?: env("BITCHORD_LASTFM_API_KEY")

    private val apiSecret: String?
        get() = property("bitchord.lastfm.secret") ?: env("BITCHORD_LASTFM_API_SECRET")

    private fun sessionKey(): String? =
        DesktopScrobbleSettings.lastfmSessionKey.value.takeIf(String::isNotBlank)
            ?: env("BITCHORD_LASTFM_SESSION_KEY")

    private fun listenBrainzToken(): String? =
        DesktopScrobbleSettings.listenBrainzToken.value.takeIf(String::isNotBlank)
            ?: env("BITCHORD_LISTENBRAINZ_TOKEN")

    /**
     * Signs in with a username and password, the way Android's login alert
     * does: `auth.getMobileSession` trades them for a session key, and the
     * password is never stored.
     */
    suspend fun signInToLastFm(username: String, password: String): Result<String> = runCatching {
        val key = apiKey ?: error("This build has no Last.fm API key")
        val values = linkedMapOf(
            "api_key" to key,
            "method" to "auth.getMobileSession",
            "password" to password,
            "username" to username,
        )
        val body = postLastFm(values)
        val session = Json { ignoreUnknownKeys = true }
            .parseToJsonElement(body).jsonObject["session"]?.jsonObject
            ?: error("Last.fm did not return a session")
        val name = session["name"]?.jsonPrimitive?.contentOrNull ?: username
        val sessionKey = session["key"]?.jsonPrimitive?.contentOrNull
            ?: error("Last.fm did not return a session key")
        DesktopScrobbleSettings.signedInToLastfm(name, sessionKey)
        name
    }

    suspend fun updateNowPlaying(song: Song): Result<Unit> = runCatching {
        if (lastFmConfigured) updateLastFm(song)
        if (listenBrainzConfigured) submitListenBrainz(song, "playing_now", 0L, song.durationMillis(), 0L)
    }

    suspend fun scrobble(song: Song): Result<Unit> = runCatching {
        val timestamp = System.currentTimeMillis() / 1_000L
        if (lastFmConfigured) submitLastFm(song, timestamp)
        if (listenBrainzConfigured) submitListenBrainz(song, "single", activeStartedAtMs, song.durationMillis(), timestamp)
    }

    /** Mirrors Android's thresholded scrobble timer and pause/resume semantics. */
    @Synchronized
    fun onPlaybackStateChanged(state: DesktopPlaybackState) {
        val song = state.song ?: return
        if (song.videoId != activeSongId) {
            scrobbleJob?.cancel()
            activeSongId = song.videoId
            activeStartedAtMs = System.currentTimeMillis()
            timerStartedAtMs = 0L
            remainingMs = 0L
            lastFmScrobbled = false
        }
        if (!state.isPlaying) {
            pauseTimerLocked()
            return
        }
        if (remainingMs == 0L && !lastFmScrobbled) {
            val durationMs = state.durationMs.takeIf { it > 0 } ?: song.durationMillis()
            if (durationMs > DesktopScrobbleSettings.minDuration.value * 1_000L) {
                remainingMs = min(
                    (durationMs * DesktopScrobbleSettings.delayPercent.value).toLong(),
                    DesktopScrobbleSettings.delaySeconds.value * 1_000L,
                )
            }
        }
        if (remainingMs > 0L && scrobbleJob?.isActive != true) {
            timerStartedAtMs = System.currentTimeMillis()
            val id = song.videoId
            scrobbleJob = scope.launch {
                delay(remainingMs)
                val shouldSend = synchronized(this@DesktopScrobbling) {
                    if (activeSongId != id || lastFmScrobbled) false
                    else {
                        lastFmScrobbled = true
                        remainingMs = 0L
                        timerStartedAtMs = 0L
                        true
                    }
                }
                if (shouldSend && lastFmConfigured) {
                    submitLastFm(song, activeStartedAtMs / 1_000L)
                }
            }
        }
    }

    /** Sends ListenBrainz's finished listen and avoids duplicate Last.fm sends. */
    fun onPlaybackEnded(song: Song) {
        val startedAt = synchronized(this) {
            scrobbleJob?.cancel()
            timerStartedAtMs = 0L
            activeStartedAtMs.takeIf { activeSongId == song.videoId } ?: System.currentTimeMillis()
        }
        scope.launch {
            try {
                val minDurationMs = DesktopScrobbleSettings.minDuration.value * 1_000L
                if (!lastFmScrobbled && lastFmConfigured && song.durationMillis() > minDurationMs) {
                    submitLastFm(song, startedAt / 1_000L)
                }
                if (listenBrainzConfigured) {
                    submitListenBrainz(song, "single", startedAt, song.durationMillis(), System.currentTimeMillis() / 1_000L)
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            }
        }
    }

    @Synchronized
    private fun pauseTimerLocked() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        if (timerStartedAtMs != 0L) {
            remainingMs = (remainingMs - (System.currentTimeMillis() - timerStartedAtMs)).coerceAtLeast(0L)
            timerStartedAtMs = 0L
        }
    }

    private suspend fun updateLastFm(song: Song) {
        if (!DesktopScrobbleSettings.lastfmNowPlaying.value) return
        val values = linkedMapOf(
            "api_key" to apiKey!!,
            "artist" to song.artist.forLastFm(),
            "method" to "track.updateNowPlaying",
            "sk" to sessionKey()!!,
            "track" to song.title,
        )
        postLastFm(values)
    }

    private suspend fun submitLastFm(song: Song, timestamp: Long) {
        if (!DesktopScrobbleSettings.lastfmScrobble.value) return
        val values = linkedMapOf(
            "api_key" to apiKey!!,
            "artist" to song.artist.forLastFm(),
            "method" to "track.scrobble",
            "sk" to sessionKey()!!,
            "timestamp" to timestamp.toString(),
            "track" to song.title,
        )
        postLastFm(values)
    }

    private fun String.forLastFm(): String =
        if (DesktopScrobbleSettings.lastfmPrimaryArtistOnly.value) primaryArtist() else this

    private fun String.forListenBrainz(): String =
        if (DesktopScrobbleSettings.listenBrainzPrimaryArtistOnly.value) primaryArtist() else this

    private suspend fun postLastFm(values: Map<String, String>): String {
        val signed = values + ("api_sig" to sign(values)) + ("format" to "json")
        val response = client.submitForm(
            url = LAST_FM_ENDPOINT,
            formParameters = Parameters.build {
                signed.forEach { (key, value) -> append(key, value) }
            },
        )
        val body = response.bodyAsText()
        check(response.status.value in 200..299) {
            // Last.fm answers a wrong password with a 403 and a message worth
            // showing, so the body is what the login dialog reports.
            runCatching {
                Json { ignoreUnknownKeys = true }
                    .parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "Last.fm returned HTTP ${response.status.value}"
        }
        return body
    }

    private suspend fun submitListenBrainz(
        song: Song,
        listenType: String,
        startMs: Long,
        durationMs: Long,
        timestamp: Long,
    ) {
        val isPlayingNow = listenType == "playing_now"
        val response = client.post(LISTENBRAINZ_ENDPOINT) {
            header("Authorization", "Token ${listenBrainzToken()!!}")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("listen_type", listenType)
                    putJsonArray("payload") {
                        add(
                            buildJsonObject {
                                if (!isPlayingNow) put("listened_at", timestamp)
                                putJsonObject("track_metadata") {
                                    put("artist_name", song.artist.forListenBrainz())
                                    put("track_name", song.title)
                                    song.albumName?.let { put("release_name", it) }
                                    putJsonObject("additional_info") {
                                        put("submission_client", "BitChord")
                                        if (isPlayingNow) {
                                            put("position_ms", 0L)
                                            if (durationMs > 0) put("duration_ms", durationMs)
                                        } else {
                                            put("start_ms", startMs)
                                            put("end_ms", durationMs)
                                            if (durationMs > 0) put("duration_ms", durationMs)
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
        check(response.status.value in 200..299) { "ListenBrainz returned HTTP ${response.status.value}" }
    }

    private fun sign(values: Map<String, String>): String {
        val input = values.toSortedMap().entries.joinToString(separator = "") { (key, value) -> "$key$value" }
        val digest = MessageDigest.getInstance("MD5")
            .digest((input + apiSecret!!).toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf(String::isNotBlank)

    private fun property(name: String): String? =
        System.getProperty(name)?.trim()?.takeIf(String::isNotBlank)

}
