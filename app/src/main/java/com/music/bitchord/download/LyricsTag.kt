package com.music.bitchord.download

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.toLrc
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The lyrics to write into a track [Downloads] is about to save, as LRC text.
 *
 * The same lookup the player does — [LyricsRepository], the same four
 * databases, the same user's pick of which of them may be asked — turned into
 * the one string [MediaTagger] can hand a container. A download is the moment
 * to do it: the lyrics are fetched over the connection that was already good
 * enough to pull the audio down, and they land in the file rather than in a
 * cache, so they survive the app being cleared and travel with the track if it
 * is copied off the device.
 *
 * Gated on [AppSettings.syncedLyrics] and [AppSettings.lyricsSources] rather
 * than on a switch of its own. Those settings are not about the player screen,
 * they are about whether this app may contact third-party lyric services at
 * all and which ones — and a download quietly asking a source the user
 * unticked would be the same request they said no to, made somewhere they
 * weren't looking.
 *
 * Never throws for anything but cancellation. A download that has otherwise
 * succeeded must not be undone by a lyrics server being down, which is the same
 * bargain [MediaTagger] makes for cover art.
 */
internal object LyricsTag {

    private const val TAG = "BitChord"

    /**
     * An LRC document for [track], or null when there is nothing worth writing.
     *
     * Null covers four different things on purpose, because the caller does the
     * same thing for all of them: the feature is off, the track has no lyrics
     * published, the lookup failed, or the answer was unusable.
     */
    /**
     * The two forms of one track's lyrics.
     *
     * [plain] is what goes in the container's own lyrics field, where every
     * other player looks. [enhanced] is the same lines with their word timings
     * kept, in a field only this app reads — null when the source was
     * line-synced and there was nothing extra to say. See
     * [toEnhancedLrc][com.music.bitchord.data.lyrics.toEnhancedLrc] for why
     * they are two fields rather than one.
     */
    internal class Embeddable(val plain: String, val enhanced: String?)

    suspend fun forTrack(track: Song): Embeddable? {
        val sources = if (AppSettings.syncedLyrics.value) {
            AppSettings.lyricsSources.value
        } else {
            emptySet()
        }
        if (sources.isEmpty()) return null

        // Album and playlist rows do not always carry a duration. That must not
        // make bulk downloads the one path that never saves lyrics: the title,
        // artist and album still give the providers a useful match, whereas a
        // track played once gets its duration backfilled by the player and
        // would otherwise appear to be the only downloadable one with words.
        // Providers that need a runtime treat zero as unknown.
        val durationMs = track.durationMillis()
        if (durationMs <= 0L) {
            Log.d(TAG, "no duration for ${track.videoId}; looking up lyrics by title and artist")
        }

        val found = try {
            withTimeoutOrNull(LOOKUP_MS) {
                LyricsRepository.lyrics(
                    videoId = track.videoId,
                    title = track.title,
                    artist = track.artist,
                    durationMs = durationMs,
                    album = track.albumName,
                    sources = sources,
                    order = AppSettings.lyricsSourceOrder.value,
                    prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "no lyrics for ${track.videoId}: ${e.message}")
            return null
        } ?: return null

        // A result made only of blank lines is what an instrumental, or a
        // provider that answered with timing and no words, comes back as. Those
        // convert to a column of bare timestamps, which is not lyrics — and is
        // not blank either, so the length check below would let it through.
        if (found.lines.none { it.text.isNotBlank() }) return null

        val lrc = found.lines.toLrc()
        if (lrc.length > MAX_LRC_CHARS) {
            // Every byte of this is about to be copied into a file the user
            // keeps, and the size is decided by a third party's response body.
            // A cap is the difference between a bad answer costing the lyrics
            // and one costing the download's file size.
            Log.w(TAG, "lyrics for ${track.videoId} are ${lrc.length} chars; not embedding")
            return null
        }
        if (lrc.isBlank()) return null
        // Capped the same way and for the same reason as the plain form; the
        // word stamps roughly double it, so it gets its own headroom rather
        // than sharing the budget and pushing the portable field out.
        val enhanced = found.lines.toEnhancedLrc().takeIf {
            it.isNotBlank() && it.length <= MAX_LRC_CHARS * 2
        }
        Log.d(
            TAG,
            "embedding ${found.source.label} lyrics for ${track.videoId}" +
                if (enhanced != null) " (word-synced)" else "",
        )
        return Embeddable(plain = lrc, enhanced = enhanced)
    }

    /**
     * The most LRC one track may contribute to its own file.
     *
     * A long song's stamped sheet runs to a few thousand characters, so this is
     * an order of magnitude clear of anything genuine — it is a ceiling on a
     * malformed or hostile response, not a judgement about songs.
     */
    private const val MAX_LRC_CHARS = 64_000

    /**
     * How long the lookup may hold a finished download up.
     *
     * Usually nothing: [Downloads] starts this before the transfer, so by the
     * time there are bytes to tag the answer has normally been waiting a while.
     * This is the ceiling for the case where it hasn't — a short track on a fast
     * connection, or a service that has stopped answering — and it is finite
     * because the alternative is a saved file the user can see in the queue,
     * complete, held back on a lyric server.
     *
     * Note that it bounds the *wait*, not the request. Cancelling a download
     * mid-lookup cannot interrupt a blocking socket read, so the queue still
     * waits out whichever HTTP call is in flight (its own timeouts, not this
     * one) before it moves on. The same is true of the lossless search this
     * sits beside — see `Downloads.SOURCE_LOOKUP_MS`.
     */
    private const val LOOKUP_MS = 15_000L
}
