package com.music.bitchord.playback

import android.os.SystemClock
import com.music.bitchord.data.sources.SourceStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Which copy of a track is being served, remembered for as long as the track
 * is being served from it.
 *
 * ExoPlayer opens a source many times over one play: the initial read, a seek
 * past the buffer, a resume, and — the one that matters here — the
 * continuation fetch when playback reaches the end of what the cache holds.
 * Every one of those goes back through the resolving data source, and until
 * this existed, every one of them was free to come back with a *different*
 * answer.
 *
 * That is not a hypothetical. Measured on this device: a track started on
 * YouTube with 57 seconds pre-buffered, played to the end of those 57 seconds,
 * and its continuation fetch resolved — correctly, by its own lights — to a
 * module's 320kbps AAC instead. The player was handed the middle of an MP4
 * where it expected the rest of a WebM, and `MatroskaExtractor` threw
 * `EOFException`:
 *
 * ```
 *   12:09:04  first audio          (YouTube Opus, buffered position 57761)
 *   12:10:12  Source error
 *             Caused by: java.io.EOFException
 *               at MatroskaExtractor.read
 * ```
 *
 * Two sources cannot share one cache entry, and [AudioCache]'s key factory
 * cannot tell them apart: it runs *before* the resolve, off the
 * `bitchord://watch?v=…` URI, and at that point which server will answer is
 * not yet known. So the fix goes the other way round — the entry does not
 * learn who is filling it, the resolver is held to whoever filled it first.
 *
 * [QualityUpgrade] already worked this way for the streams it swaps in, for
 * exactly the same reason, and the reasoning simply hadn't been carried back
 * to the first resolve.
 *
 * ### Why a time limit
 *
 * A module's stream URL is signed and expires — around five hours, on the
 * catalogues in use here. Holding one indefinitely would eventually hand the
 * player a dead URL rather than a wrong one, which is no better. [TTL_MS] is
 * far inside every expiry seen, so a choice is either reused while it is
 * certainly still good or made again from scratch.
 */
object StreamChoice {

    private class Choice(val stream: SourceStream, val at: Long, val substituted: Boolean)

    private val chosen = ConcurrentHashMap<String, Choice>()

    /**
     * The stream already serving [videoId], or null if this is the first read
     * for it — or the last one was long enough ago that its URL is no longer
     * worth trusting.
     */
    fun of(videoId: String): SourceStream? {
        val choice = chosen[videoId] ?: return null
        if (SystemClock.elapsedRealtime() - choice.at > TTL_MS) {
            chosen.remove(videoId)
            return null
        }
        return choice.stream
    }

    /**
     * Records [stream] as the one copy of [videoId] this play is reading.
     *
     * @param substituted whether this came from a source standing in for
     *   YouTube rather than from YouTube itself. Only the resolver knows, and
     *   only [refuseSubstitutes] needs it — a substitution that turns out to be
     *   unplayable has somewhere else to fall back to, and a YouTube stream
     *   that fails has not.
     */
    fun remember(videoId: String, stream: SourceStream, substituted: Boolean) {
        if (chosen.size >= MAX_REMEMBERED) {
            // Drop what can no longer be honoured, and only then the oldest of
            // what can. This used to `clear()`, which is the one eviction
            // capable of causing the corruption this class exists to prevent:
            // every live entry is a promise that a half-filled cache entry will
            // be finished by the stream that started filling it, and emptying
            // the map frees *all* of those entries to be finished by a
            // different server instead.
            //
            // Harmless while only playback wrote here — an entry was made and
            // consumed within one track. Read-ahead now pins the next track
            // before it is reached and caches its bytes on the strength of that
            // pin, so a promise can outlive several other tracks' worth of
            // entries, and the blunt version became reachable.
            val now = SystemClock.elapsedRealtime()
            chosen.entries.removeIf { now - it.value.at > TTL_MS }
            if (chosen.size >= MAX_REMEMBERED) {
                chosen.minByOrNull { it.value.at }?.let { chosen.remove(it.key) }
            }
        }
        chosen[videoId] = Choice(stream, SystemClock.elapsedRealtime(), substituted)
    }

    /**
     * Whether the copy currently serving [videoId] came from a source standing
     * in for YouTube.
     *
     * Read on the recovery path, before [forget], to tell a track that died on
     * a substitution from one that died on YouTube's own stream. The two have
     * opposite right answers: the first should stop substituting, the second
     * has nothing better to try.
     */
    fun isSubstitute(videoId: String): Boolean = chosen[videoId]?.substituted == true

    /**
     * Releases [videoId] to be resolved afresh.
     *
     * Called when the bytes behind the choice are gone or were never any good:
     * the queue moved past the track, its cache entry was discarded after a
     * read error, or [QualityUpgrade] replaced the whole stream with a better
     * one.
     */
    fun forget(videoId: String) {
        chosen.remove(videoId)
    }

    /**
     * Tracks whose substituted stream refused to serve its bytes, and when.
     *
     * [forget] alone is not enough to recover from one. The search behind a
     * substitution is deterministic — the same module, asked the same query for
     * the same tier, answers with the same URL — and it is also *fast*, because
     * by the second attempt its index and its module are cached. So a retry
     * that is free to substitute again wins the race against YouTube by the
     * same margin it won it the first time, and resolves straight back to the
     * URL that just failed. Three attempts of that is a track that never plays
     * at all, while a working YouTube URL sits in
     * [StreamResolver][com.music.bitchord.data.innertube.StreamResolver]'s cache
     * unused.
     *
     * ### Why this expires
     *
     * The first version of this held a refusal for the life of the process, on
     * the reasoning that a deterministic search deserves a permanent answer.
     * Nothing measured supports going that far. What still reaches here is a URL
     * that was well formed and would not serve anyway, and the ordinary causes
     * of that — a signature that expired between minting and use, a backend
     * having a bad minute — clear up by themselves. Held forever, one of those
     * would cost the lossless copy of every track it touched for the rest of the
     * session: a real loss traded against an unproven gain. So the refusal is a
     * cooling-off period, not a verdict — long enough to get the track playing
     * and keep it playing, short enough to ask again in the same sitting.
     *
     * Most of what used to arrive here doesn't any more: [ModuleSource.malformed]
     * rejects a URL carrying its own origin twice before anything tries to play
     * it. This is the net under that, for the failures only playback can find.
     */
    private val refusedSubstitutes = ConcurrentHashMap<String, Long>()

    /**
     * Stops [videoId] being substituted for [REFUSAL_MS], and sends it to
     * YouTube instead. Called from the recovery path; see
     * [PlaybackService.recoverFrom].
     */
    fun refuseSubstitutes(videoId: String) {
        if (refusedSubstitutes.size >= MAX_REMEMBERED) refusedSubstitutes.clear()
        refusedSubstitutes[videoId] = SystemClock.elapsedRealtime()
    }

    /** Whether a substitution has broken [videoId] recently enough to still count. */
    fun substitutesRefused(videoId: String): Boolean {
        val at = refusedSubstitutes[videoId] ?: return false
        if (SystemClock.elapsedRealtime() - at <= REFUSAL_MS) return true
        refusedSubstitutes.remove(videoId)
        return false
    }

    /** Long enough to outlast any one play, far short of any signed URL's life. */
    private const val TTL_MS = 15 * 60 * 1000L

    /**
     * How long a track stays off substitution after one broke it.
     *
     * Comfortably longer than the recovery it exists to protect — the retry
     * happens within seconds — and longer than a play of the track, so nothing
     * swaps back mid-song. Short enough that a module which was briefly
     * returning bad URLs gets another chance inside the same listening session.
     */
    private const val REFUSAL_MS = 10 * 60 * 1000L

    private const val MAX_REMEMBERED = 32
}
