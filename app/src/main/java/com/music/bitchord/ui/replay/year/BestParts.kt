package com.music.bitchord.ui.replay.year

import android.content.Context
import android.util.Log
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Songs' best parts ([BestPart]) for the Replay year story: the heat curves
 * fetched and kept on disk, and the starts worked out from them.
 *
 * ## What is kept, and for how long
 *
 * Only the curves, never the starts. A start depends on the lyrics as well,
 * and those are not stored anywhere in this app, so it is worked out again each
 * time from the curve plus whatever lyrics come back. The curve keeps for a
 * month: YouTube's own replays don't move much in that time. "This video has no
 * curve" keeps for a week, since a song that is new now has the views for one
 * soon.
 *
 * A failed request is kept nowhere. Offline, or a timeout, is not an answer
 * about the video, and remembering it as "none" would keep the song on its
 * fallback for a week after the network came back.
 */
object BestParts {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val lock = Mutex()
    private var file: File? = null
    private val stored = ConcurrentHashMap<String, StoredHeat>()

    /** Starts already worked out this run, so reopening the story doesn't ask twice. */
    private val starts = ConcurrentHashMap<String, BestPartStart>()

    /** What is already known about [videoId], without asking anything. */
    fun known(videoId: String): BestPartStart? = starts[videoId]

    /**
     * Where [song]'s best part starts.
     *
     * Asks youtube.com for the heat curve unless one is on disk, then the
     * listener's own lyrics sources for synced words, each on its own clock.
     * Whatever hasn't answered in time is done without, so this always
     * returns: at worst with [BestPart.fallbackMs].
     */
    suspend fun startFor(context: Context, song: Song): BestPartStart {
        starts[song.videoId]?.let { return it }
        ensureLoaded(context)
        // The Replay's songs come off the listening files, which keep no
        // length, and a lyrics lookup can't match without one. The curve covers
        // the whole video, so its length stands in: which is why the words are
        // asked for after the curve rather than alongside it.
        val heat = heatmapFor(song)
        val durationMs = song.durationText.durationMillis().takeIf { it > 0 } ?: heat?.durationMs ?: 0L
        val lines = syncedLines(context, song, durationMs).orEmpty()
        val result = BestPart.pick(heat, lines, durationMs)
        // A fallback is not remembered: the network may be back next time.
        if (result.source != BestPartStart.Source.FALLBACK) starts[song.videoId] = result
        return result
    }

    // ── The curve ───────────────────────────────────────────────────────────

    private suspend fun heatmapFor(song: Song): Heatmap? {
        // A file on the phone, or a row from an addon, has no YouTube video and
        // so no curve. The listening files keep such tracks under a content
        // URI or an addon key rather than an 11-character video id.
        val id = song.videoId
        if (song.localUri != null || !isYouTubeId(id)) return null
        val now = System.currentTimeMillis()
        stored[id]?.let { kept ->
            val heat = kept.heatmap()
            val fresh = now - kept.fetchedAt < if (heat != null) MONTH_MS else WEEK_MS
            if (fresh) return heat
        }
        val answer = withTimeoutOrNull(HEATMAP_TIMEOUT_MS) {
            try {
                Innertube.webNext(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "no heatmap answer for $id: ${e.message}")
                null
            }
        } ?: return stored[id]?.heatmap() // Stale beats nothing when the network won't answer.
        val heat = BestPart.parseHeatmap(answer)
        stored[id] = StoredHeat(heat?.segmentMs ?: 0L, heat?.intensity.orEmpty(), now)
        save()
        return heat
    }

    // ── The words ───────────────────────────────────────────────────────────

    /**
     * Synced lyrics for [song], or null.
     *
     * The same sources and order the player uses, and nothing when synced
     * lyrics are switched off: a source somebody turned off is one this app
     * doesn't contact, and that holds here too. A file's own embedded lyrics
     * come first, since they cost nothing.
     */
    private suspend fun syncedLines(context: Context, song: Song, durationMs: Long): List<LyricLine>? {
        if (!AppSettings.syncedLyrics.value) return null
        return withTimeoutOrNull(LYRICS_TIMEOUT_MS) {
            try {
                song.localUri
                    ?.let { EmbeddedLyrics.forUri(context, it) }
                    ?.takeIf(EmbeddedLyrics::isSynced)
                    ?: if (durationMs > 0) {
                        LyricsRepository.lyrics(
                            videoId = song.videoId,
                            title = song.title,
                            artist = song.artist,
                            durationMs = durationMs,
                            album = song.albumName,
                            sources = AppSettings.lyricsSources.value,
                            order = AppSettings.lyricsSourceOrder.value,
                        )?.lines?.takeIf(EmbeddedLyrics::isSynced)
                    } else {
                        null
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "no lyrics for ${song.videoId}: ${e.message}")
                null
            }
        }
    }

    /** Whether [id] is a YouTube video id rather than a file or an addon's key. */
    fun isYouTubeId(id: String): Boolean = YOUTUBE_ID.matches(id)

    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

    // ── Disk ────────────────────────────────────────────────────────────────

    private suspend fun ensureLoaded(context: Context) = lock.withLock {
        if (file != null) return@withLock
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        file = target
        withContext(Dispatchers.IO) {
            if (!target.exists()) return@withContext
            runCatching { json.decodeFromString(Stored.serializer(), target.readText()) }
                .onSuccess { kept -> stored.putAll(kept.heats) }
                .onFailure {
                    Log.w(TAG, "Discarding unreadable heatmap cache", it)
                    target.delete()
                }
        }
    }

    private suspend fun save() = lock.withLock {
        val target = file ?: return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                val kept = stored.entries
                    .sortedByDescending { it.value.fetchedAt }
                    .take(MAX_KEPT)
                    .associate { it.key to it.value }
                target.writeText(json.encodeToString(Stored.serializer(), Stored(heats = kept)))
            }.onFailure { Log.w(TAG, "Could not write heatmap cache", it) }
        }
    }

    @Serializable
    private data class Stored(val version: Int = 1, val heats: Map<String, StoredHeat> = emptyMap())

    /** A curve as fetched. An empty [intensity] means the video has none. */
    @Serializable
    private data class StoredHeat(val segmentMs: Long, val intensity: List<Float>, val fetchedAt: Long) {
        fun heatmap(): Heatmap? = if (segmentMs > 0 && intensity.isNotEmpty()) Heatmap(segmentMs, intensity) else null
    }

    private const val TAG = "BitChordBestPart"
    private const val FILE_NAME = "replay_heatmaps.json"
    private const val MAX_KEPT = 500
    private const val HEATMAP_TIMEOUT_MS = 8_000L
    private const val LYRICS_TIMEOUT_MS = 6_000L
    private const val MONTH_MS = 30L * 24 * 3600 * 1000
    private const val WEEK_MS = 7L * 24 * 3600 * 1000
}
