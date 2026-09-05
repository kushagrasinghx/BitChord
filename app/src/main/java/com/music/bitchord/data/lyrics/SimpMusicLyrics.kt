package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Lyrics from SimpMusic's community database, keyed on the YouTube video id.
 *
 * That key is what makes it worth having: every other provider matches on
 * title and artist and can hand back a different edit of the same song, which
 * drifts out of sync a verse in. This one is looking up the exact track that
 * is playing.
 *
 * Two caveats, both seen in the wild:
 *  - the host geoblocks some regions outright, answering 403 with a "Access
 *    denied from your region" body rather than a network error, so a miss here
 *    can be permanent for a given user and the chain must carry on past it;
 *  - the rich sync is served HTML-escaped — see [EnhancedLrc.decodeEntities].
 */
object SimpMusicLyrics {

    private const val BASE = "https://api-lyrics.simpmusic.org/v1/"

    /** Duration slack when the database holds several cuts of one video. */
    private const val DURATION_TOLERANCE_SECONDS = 10

    suspend fun artifact(videoId: String, durationMs: Long): LyricsArtifact? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null
            val body = lyricsGet(BASE + videoId) ?: return@withContext null
            val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
            if (response == null || !response.success) return@withContext null

            val seconds = (durationMs / 1000).toInt()
            val withLyrics = response.data.orEmpty().filter {
                !it.richSyncLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank()
            }
            val track = if (seconds > 0) {
                withLyrics
                    .filter { abs((it.duration ?: 0) - seconds) <= DURATION_TOLERANCE_SECONDS }
                    .minByOrNull { abs((it.duration ?: 0) - seconds) }
                    ?: withLyrics.firstOrNull()
            } else {
                withLyrics.firstOrNull()
            } ?: return@withContext null

            // Word timing first; a line-synced answer from here is no better
            // than LRCLIB's, but it is still better than nothing.
            track.richSyncLyrics?.takeIf { it.isNotBlank() }?.let { content ->
                val lines = EnhancedLrc.parse(content).takeIf { it.isNotEmpty() }
                    ?: return@withContext null
                LyricsArtifact(
                    source = LyricsSource.SIMP_MUSIC,
                    format = LyricsArtifactFormat.ENHANCED_LRC,
                    content = content,
                    lines = lines,
                )
            } ?: track.syncedLyrics?.takeIf { it.isNotBlank() }?.let { content ->
                val lines = LrcLib.parseLrc(content).takeIf { it.isNotEmpty() }
                    ?: return@withContext null
                LyricsArtifact(
                    source = LyricsSource.SIMP_MUSIC,
                    format = LyricsArtifactFormat.LRC,
                    content = content,
                    lines = lines,
                )
            }
        }

    suspend fun lyrics(videoId: String, durationMs: Long): List<LyricLine>? =
        artifact(videoId, durationMs)?.lines

    @Serializable
    internal data class Response(
        val success: Boolean = false,
        val data: List<Track>? = null,
    )

    @Serializable
    internal data class Track(
        val duration: Int? = null,
        val richSyncLyrics: String? = null,
        val syncedLyrics: String? = null,
        val plainLyrics: String? = null,
    )
}
