package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.withBackgroundVocals
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import java.util.concurrent.ConcurrentHashMap

/** A track's lyrics and which provider they came from, by its label. */
data class DesktopLyrics(val source: String, val lines: List<LyricLine>) {
    val plainText: String get() = lines.joinToString("\n") { it.text }
    val wordSynced: Boolean get() = lines.any(LyricLine::isWordSynced)
}

/**
 * The desktop's lyrics lookup.
 *
 * The providers, the race between them and the parsing are the phone's own
 * [LyricsRepository], shared from `:shared`. What stays here is the desktop's
 * settings — which providers are ticked, in what order, stored by label — and
 * the one source only a desktop has: the words inside a local file.
 */
object DesktopLyricsClient {
    private const val CACHE_TTL_MS = 12L * 60L * 60L * 1_000L

    /** Keys the settings screen writes and this reads. */
    internal const val KEY_SYNCED_LYRICS = "synced_lyrics"
    internal const val KEY_PRIORITIZE_SYLLABLES = "prioritize_syllable_lyrics"
    internal const val KEY_LYRICS_BLUR = "lyrics_blur"
    internal const val KEY_LYRICS_SOURCES = "lyrics_sources"

    /**
     * Which sources have ever been put in front of the listener; see
     * [DesktopPersistence.lyricsEnabledSources].
     */
    internal const val KEY_LYRICS_SOURCES_SEEN = "lyrics_sources_seen"
    internal const val KEY_LYRICS_ORDER = "lyrics_source_order"

    /** One lyric database, as the settings screen needs to describe it. */
    internal data class Source(val name: String, val detail: String, val wordSynced: Boolean)

    /** Every provider the phone has, in the order it asks them out of the box. */
    internal val sources: List<Source> = LyricsSource.entries.map { Source(it.label, it.detail, it.wordSynced) }

    /** The sources that will actually be asked, in the order they are asked. */
    internal fun enabledSources(order: List<String>, enabled: Set<String>): List<String> =
        order.filter { it in enabled }

    private class Cached(val createdAt: Long, val result: Result<DesktopLyrics>)

    private val cache = ConcurrentHashMap<String, Cached>()

    /**
     * The track's lyrics from the configured providers — or, with [only], from that one provider,
     * which is how the player's provider picker asks for a specific source's answer.
     */
    suspend fun lookup(
        song: Song,
        durationMs: Long = song.durationMillis(),
        only: String? = null,
    ): Result<DesktopLyrics> {
        val persistence = DesktopPersistence()
        // Off in Settings means no lookup at all rather than a lookup nobody sees.
        if (!persistence.boolean(KEY_SYNCED_LYRICS, true)) {
            return Result.failure(IllegalStateException("Synced lyrics are switched off"))
        }
        val configured = enabledSources(persistence.lyricsSourceOrder(), persistence.lyricsEnabledSources())
            .mapNotNull(::lyricsSourceNamed)
        val asked = only?.let(::lyricsSourceNamed)?.let(::listOf) ?: configured
        // Every source unticked is a state the listener can reach, and it means the same thing as
        // the feature being off.
        if (asked.isEmpty()) return Result.failure(IllegalStateException("No lyrics sources are enabled"))

        val cacheKey = "${song.videoId}|$durationMs|${only.orEmpty()}"
        cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.createdAt < CACHE_TTL_MS }?.let { return it.result }

        // A downloaded or local file carries its own words; asking servers for a string already
        // on disk is a round trip, and it is why a download showed nothing offline.
        val embedded = if (only == null) embedded(song) else null
        val result = if (embedded != null) {
            Result.success(DesktopLyrics("Downloaded", embedded))
        } else {
            val found = LyricsRepository.lyrics(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                durationMs = durationMs,
                album = song.albumName,
                sources = asked.toSet(),
                order = asked,
                prioritizeSyllableSync = persistence.boolean(KEY_PRIORITIZE_SYLLABLES, false),
            )
            if (found == null) {
                Result.failure(IllegalStateException("Lyrics unavailable"))
            } else {
                Result.success(DesktopLyrics(found.source.label, found.lines))
            }
        }
        cache[cacheKey] = Cached(System.currentTimeMillis(), result)
        return result
    }

    /** The words inside the file this track plays from, when it plays from one. */
    private fun embedded(song: Song): List<LyricLine>? {
        val path = song.localPath?.let { runCatching { java.nio.file.Paths.get(it) }.getOrNull() }
            ?: song.localUri?.let { runCatching { java.nio.file.Paths.get(java.net.URI(it)) }.getOrNull() }
            ?: return null
        val raw = DesktopEmbeddedLyrics.read(path) ?: return null
        // The same last pass the network sources get, as on the phone.
        return LrcLib.parseLrc(raw).takeIf { lines -> lines.any { it.text.isNotBlank() } }?.withBackgroundVocals()
    }
}
