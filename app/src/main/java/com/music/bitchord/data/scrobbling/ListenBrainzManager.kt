package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.music.bitchord.data.Http

/**
 * ListenBrainz's submit endpoint.
 *
 * The submission methods return a Boolean, but the durable queue
 * ([ScrobbleRetry]) needs to know *why* a submission failed: a 401 is a token
 * the user must replace, while a 503 is worth retrying. [submitListen] is that
 * lower-level entry point and returns the HTTP status; the Boolean methods are
 * thin wrappers over it, so there is one request path rather than two that can
 * drift apart.
 */
object ListenBrainzManager {
    private const val TAG = "ListenBrainzManager"
    private const val API_URL = "https://api.listenbrainz.org/1/submit-listens"

    /** No HTTP answer at all: connection refused, timeout, DNS failure. */
    const val HTTP_TRANSPORT_FAILURE = -1

    private const val MIN_LISTEN_TIMESTAMP = 1_033_430_400L

    suspend fun submitPlayingNow(
        token: String,
        song: Song?,
        positionMs: Long,
        durationMsOverride: Long? = null,
        primaryArtistOnly: Boolean = false,
    ): Boolean {
        if (token.isBlank() || song == null) return false
        val durationMs = durationMsOverride ?: parseDurationMs(song.durationText)
        val artist = if (primaryArtistOnly) song.artist.primaryArtist() else song.artist
        val metadata = trackMetadata(artist, song.title, song.albumName, durationMs, "\"position_ms\":$positionMs,")
        val bodyJson = "{\"listen_type\":\"playing_now\",\"payload\":[{$metadata}]}"
        return post(token, bodyJson, "playing_now") in 200..299
    }

    suspend fun submitFinished(
        token: String,
        song: Song?,
        startMs: Long,
        endMs: Long,
        durationMsOverride: Long? = null,
        primaryArtistOnly: Boolean = false,
    ): Boolean {
        if (token.isBlank() || song == null) return false
        val durationMs = durationMsOverride ?: parseDurationMs(song.durationText)
        val artist = if (primaryArtistOnly) song.artist.primaryArtist() else song.artist
        return submitListen(
            token = token,
            artist = artist,
            track = song.title,
            album = song.albumName,
            durationSeconds = durationMs.takeIf { it > 0 }?.div(1000)?.toInt(),
            startMs = startMs,
            endMs = endMs,
            listenedAtSeconds = startMs / 1000L,
        ) in 200..299
    }

    /**
     * Submits one finished listen and reports the HTTP status.
     *
     * Used both by [submitFinished] and by the retry queue, which needs the
     * status to tell a permanent rejection from a temporary one.
     */
    suspend fun submitListen(
        token: String,
        artist: String,
        track: String,
        album: String?,
        durationSeconds: Int?,
        startMs: Long,
        endMs: Long,
        listenedAtSeconds: Long,
    ): Int {
        if (token.isBlank()) return 401
        val durationMs = (durationSeconds ?: 0).coerceAtLeast(0) * 1000L
        var listenedAt = listenedAtSeconds
        if (listenedAt < MIN_LISTEN_TIMESTAMP) {
            listenedAt = System.currentTimeMillis() / 1000L
        }
        val metadata =
            trackMetadata(
                artist = artist,
                track = track,
                album = album,
                durationMs = durationMs,
                extra = "\"start_ms\":$startMs,\"end_ms\":$endMs,",
            )
        val bodyJson = "{\"listen_type\":\"single\",\"payload\":[{\"listened_at\":$listenedAt,$metadata}]}"
        return post(token, bodyJson, "finished")
    }

    private fun trackMetadata(
        artist: String,
        track: String,
        album: String?,
        durationMs: Long,
        extra: String,
    ): String {
        // The API rejects a zero/negative duration_ms, and it is optional — so
        // only send it when it is actually known.
        val durationPart = if (durationMs > 0) "\"duration_ms\":$durationMs," else ""
        val releasePart =
            album
                ?.takeIf { it.isNotBlank() }
                ?.let { "\"release_name\":\"${escapeJson(it)}\"," }
                .orEmpty()
        return "\"track_metadata\":{\"artist_name\":\"${escapeJson(artist)}\"," +
            "\"track_name\":\"${escapeJson(track)}\",$releasePart\"additional_info\":{" +
            "$durationPart$extra\"submission_client\":\"BitChord\"}}"
    }

    private suspend fun post(
        token: String,
        bodyJson: String,
        label: String,
    ): Int =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "$label: $bodyJson")
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val request =
                    Request
                        .Builder()
                        .url(API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Token $token")
                        .build()

                Http.client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.d(TAG, "$label submitted")
                    } else {
                        val bodyText = try { resp.body?.string().orEmpty() } catch (_: Exception) { "" }
                        Log.w(TAG, "$label submit failed: ${resp.code} - $bodyText")
                    }
                    resp.code
                }
            } catch (ex: Exception) {
                Log.e(TAG, "$label submit failed", ex)
                HTTP_TRANSPORT_FAILURE
            }
        }

    private fun parseDurationMs(text: String?): Long {
        if (text == null) return 0L
        val parts = text.split(":")
        if (parts.size != 2) return 0L
        val minutes = parts[0].toLongOrNull() ?: return 0L
        val seconds = parts[1].toLongOrNull() ?: return 0L
        return (minutes * 60 + seconds) * 1000
    }

    internal fun escapeJson(s: String): String =
        buildString {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
        }
}
