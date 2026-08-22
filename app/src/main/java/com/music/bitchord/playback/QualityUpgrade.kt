package com.music.bitchord.playback

import android.net.Uri
import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.StreamRequest
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.Deferred
import java.util.concurrent.ConcurrentHashMap

/**
 * The second look: a track that started on less than was asked for gets the
 * question asked again, properly, while it plays.
 *
 * The live path has to answer in the time a listener will wait for a track to
 * start, and it buys that by giving up on the slow catalogue — which is
 * regularly the one holding the lossless copy. Measured on this device: the
 * fastest module answered a search in 1.3s and the slowest in 5.4s, and the
 * slow one then took a further 8.2s to walk its own fallback chain down to a
 * 128kbps MP3. Waiting for all of that costs 14 seconds of silence; not
 * waiting costs the FLAC. Neither is a good trade, and neither has to be made
 * once the search can happen with sound already coming out of the speaker.
 *
 * So: play whatever can be had now, then look again with no time limit, and
 * swap only if the answer is genuinely the same recording *and* genuinely
 * better than what is playing — which is usually the lossless copy that was
 * asked for, but is also a 320kbps module stream against YouTube's 160kbps
 * Opus. See [SourceResolver.worthSwapping] for where that line is drawn.
 * The swap is not free — ExoPlayer cannot change sources
 * gaplessly mid-track, so there is a short break in the audio — which is why
 * every guard here errs towards not doing it. A missed upgrade is a quieter
 * failure than an interrupted song.
 *
 * ### How the swap reaches the player
 *
 * The queue holds `bitchord://watch?v=…` URIs that
 * [PlaybackService][PlaybackService]'s resolving data source turns into real
 * URLs at open time. An upgrade re-points that indirection rather than
 * touching the queue: the stream is parked in [forced], the item is replaced
 * with the same URI plus a `q=` marker, and the marker does two jobs — it
 * makes the item unequal to its old self so Media3 actually rebuilds the media
 * source, and it keys the disk cache separately so the FLAC is not written
 * into the middle of the half-cached MP3 it is replacing.
 */
object QualityUpgrade {

    private const val TAG = "BitChord"

    /** The marker that distinguishes an upgraded item from the one it replaced. */
    const val MARKER = "q"
    private const val UPGRADED = "hifi"

    /**
     * A track playing on less than was asked for.
     *
     * [inFlight] is the live lookup that lost the race rather than ran out of
     * answers — still running, and worth waiting on rather than repeating,
     * because whatever it returns is precisely the stream that would have
     * played had it been quicker. Null once the live path has finished and
     * come back with nothing better; the second look then has to go find its
     * own candidates.
     */
    private data class Pending(
        val target: TrackMatcher.Target,
        val inFlight: Deferred<SourceStream?>? = null,
        /**
         * What the listener is actually hearing — the yardstick a lossy
         * candidate is measured against in [SourceResolver.worthSwapping].
         * Known by the time a track is marked pending: whichever stream won
         * the race has already named its format. Null only when nothing did,
         * and an unknown floor is one nothing lossy clears.
         */
        val playing: StreamFormat? = null,
    )

    private val pending = ConcurrentHashMap<String, Pending>()
    private val forced = ConcurrentHashMap<String, SourceStream>()

    /**
     * Tracks whose upgraded stream is being *proved* rather than played — see
     * [PlaybackService][com.music.bitchord.playback.PlaybackService]'s
     * audition.
     *
     * An audition reaches its bytes through the same resolving data source the
     * real player does, which is where [forcedStream] hands over the URL and
     * where the format it promises is recorded for "stats for nerds". That
     * recording is right for a stream being played and wrong for one being
     * tried out: for the length of an audition the listener is still hearing
     * the old stream, and a badge that reads "Lossless" over it is describing
     * a swap that has not happened and might never.
     */
    private val auditioning = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun beginAudition(mediaId: String) {
        auditioning += mediaId
    }

    fun endAudition(mediaId: String) {
        auditioning -= mediaId
    }

    /** Whether the fetch about to happen for [videoId] is an audition, not playback. */
    fun isAuditioning(videoId: String?): Boolean = videoId != null && videoId in auditioning

    /**
     * Upgrades that were found, proved and cached, and then never got to
     * happen because the queue moved on in the last moments before the swap.
     *
     * Everything expensive about an upgrade is already spent by that point —
     * the catalogue search, the audition, the megabytes on disk — and all of it
     * was being thrown away over a quarter of a second of timing. Measured: a
     * FLAC found in 10.1s, proved in 2.0s and cached in full, discarded because
     * the listener skipped 254ms before the swap; skipping straight back to the
     * track could not use any of it.
     *
     * Held against exactly that — the listener coming back. The stream stays in
     * [forced] and its bytes stay under the rendition key, so the swap that
     * follows is the cheap kind: no search, no download, and an audition that
     * reads from disk.
     */
    private val shelved = ConcurrentHashMap<String, SourceStream>()

    /** Keeps a proved-but-unused upgrade for [mediaId] against a return visit. */
    fun shelve(mediaId: String, stream: SourceStream) {
        shelved[mediaId] = stream
        // The answer was yes. Recording it as a settled question is what would
        // stop [couldStillUpgrade] ever offering the track again.
        asked -= mediaId
    }

    /** The upgrade already proved for [mediaId], if one ran out of track. */
    fun shelvedFor(mediaId: String): SourceStream? = shelved[mediaId]

    /**
     * Takes [mediaId]'s upgrade off the shelf — it has happened.
     *
     * Without this the entry outlives the swap it describes, and the next time
     * the listener comes back to the track it is offered again: the item URI
     * already carries the marker, so the swap declines, and the decline is read
     * as another missed one and shelved afresh.
     */
    fun unshelve(mediaId: String) {
        shelved.remove(mediaId)
    }

    /**
     * Records that [mediaId] is playing on less than was asked for — whether
     * that is a lossy stream a module handed over, or YouTube's own because no
     * module answered in time.
     *
     * Called from the resolving data source, which is the only place that
     * knows both what was requested and what actually came back. The track
     * stays in [NerdStats.racingLossless] from here until the second look
     * finishes, so the player keeps saying "Loading lossless" rather than
     * going blank and then possibly changing its mind — the badge should
     * describe the search that is genuinely still running, and go out for good
     * once the answer is known to be no.
     *
     * Does nothing unless lossless is what the connection and the settings
     * currently add up to. There is no such thing as an upgrade from a stream
     * that is already everything that was asked for, and marking one pending
     * would light the badge for a search with no possible outcome.
     */
    fun settledForLess(
        mediaId: String,
        target: TrackMatcher.Target,
        inFlight: Deferred<SourceStream?>? = null,
        playing: StreamFormat? = null,
    ): Boolean {
        if (target.title.isBlank() ||
            mediaId in refused ||
            SourceResolver.requestForNow() !is StreamRequest.Lossless ||
            !SourceResolver.canSubstituteForYouTube()
        ) {
            inFlight?.cancel()
            return false
        }
        pending[mediaId] = Pending(target, inFlight, playing)
        NerdStats.onLosslessRaceStart(mediaId)
        TrackLog.d(
            TAG,
            if (inFlight != null) {
                "'${target.title}' started on the fallback; its lookup is still running"
            } else {
                "below request for '${target.title}'; will look again during playback"
            },
        )
        return true
    }

    /** Whether [mediaId] is worth a second look — and hasn't already had one. */
    fun isPending(mediaId: String?) = mediaId != null && pending.containsKey(mediaId)

    /**
     * Tracks whose upgrade broke the playback it was supposed to improve.
     *
     * A swapped-in stream that fails to serve its bytes costs a cut in the
     * audio and a recovery, and the search that produced it is deterministic —
     * ask again and the same catalogue returns the same dead URL. Nothing here
     * expires: the entry is worth exactly as long as the queue that holds the
     * track, and the map is cleared with the rest when the process goes.
     */
    private val refused = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Stops offering [mediaId] any further upgrades this session — its last
     * one is what killed it. Called from the recovery path; see
     * [PlaybackService][com.music.bitchord.playback.PlaybackService].
     */
    fun refuseUpgrades(mediaId: String) {
        refused += mediaId
        TrackLog.d(TAG, "$mediaId broke on its upgrade; no more swaps for it")
    }

    /**
     * Tracks that have already had their second look, whether it found
     * anything or not.
     *
     * [pending] cannot answer this on its own, because [lookAgain] empties it
     * as the question is asked: by the next progress sample a track that has
     * been asked about and a track that was never a candidate look identical.
     * That distinction costs nothing on the resolve path — nothing marks a
     * track pending twice — but it is the whole difference for
     * [adoptUnresolved], which is offered the same playing track every five
     * seconds for as long as it lasts.
     */
    private val asked = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Whether [mediaId] is worth *evaluating* for a second look, given that
     * nothing has resolved it.
     *
     * The cheap half of [adoptUnresolved], split out because it is asked on the
     * main thread every progress sample while the other half has to wait for
     * the decoder to settle first. Everything here is a fact about the queue
     * entry and the settings; nothing here touches the network.
     */
    fun couldStillUpgrade(mediaId: String, uri: Uri?): Boolean {
        if (uri == null || uri.getQueryParameter("v") == null) return false
        // Already upgraded: this *is* the better copy.
        if (uri.getQueryParameter(MARKER) != null) return false
        if (mediaId in asked || mediaId in refused || pending.containsKey(mediaId)) return false
        return SourceResolver.requestForNow() is StreamRequest.Lossless &&
            SourceResolver.canSubstituteForYouTube()
    }

    /**
     * Marks a track that is playing without ever having been resolved.
     *
     * [settledForLess] is reached from the resolving data source, which is the
     * only place that knows what was asked for and what came back — and which
     * a track playing off the disk cache never reaches at all. `CacheDataSource`
     * wraps the resolver rather than the other way round, so bytes already on
     * disk are served without a resolve, without a lookup, and so without
     * anything marking the track worth a second look. The tracks that hit this
     * hardest are the ones restored into the queue at startup: their bytes were
     * written by a previous process, so nothing in *this* one has ever asked a
     * module about them, and they would play at last session's bitrate for the
     * rest of the session and every session after it, while a freshly queued
     * copy of the same song upgraded within seconds.
     *
     * The floor is deliberately left unknown. A lossy stream from the cache
     * cannot say what bitrate it is — YouTube's WebM and MP4 containers carry
     * no bitrate field, and there is no resolver figure to fall back on here
     * because there was no resolve — so [SourceResolver.worthSwapping] is given
     * a null and treats it as a floor nothing lossy clears. Only genuine
     * lossless cuts into a cached track, which is the conservative half of the
     * feature and the half the disk copy cannot already be.
     *
     * @param playingMime what the *decoder* says about the bytes it is being
     *   fed, and it must be this track's — see
     *   [PlaybackService.audioFormatFor][com.music.bitchord.playback.PlaybackService].
     *   The one thing worth not doing here is hunting a lossless copy of a
     *   track that is already playing one, which is exactly what a cache entry
     *   written by a previous session's successful upgrade holds.
     * @return true if the track is now pending, i.e. worth calling
     *   [lookAgain] for.
     */
    fun adoptUnresolved(
        mediaId: String,
        uri: Uri,
        target: TrackMatcher.Target,
        playingMime: String?,
    ): Boolean {
        if (!couldStillUpgrade(mediaId, uri)) return false
        // [asked] is set only on the two *verdicts* below, not on adoption.
        // Both are facts about the bytes on disk and the row that queued them,
        // neither changes while the track plays, and neither is worth
        // re-deciding every five seconds. A track that gets adopted needs no
        // entry here at all: [pending] keeps [couldStillUpgrade] off it for
        // exactly as long as the question is genuinely open, and marking it
        // answered before it has been asked is what made a skip permanent.
        if (NerdStats.isLosslessMime(playingMime)) {
            asked += mediaId
            // The codec is named because this line is a dead end — the track is
            // in [asked] by now and will never be offered an upgrade again — and
            // without it there is no way to tell a correct verdict from one
            // reached on the previous track's format.
            TrackLog.d(
                TAG,
                "'${target.title}' is already playing $playingMime from cache; no second look needed",
            )
            return false
        }
        if (target.title.isBlank()) {
            asked += mediaId
            return false
        }
        pending[mediaId] = Pending(target, inFlight = null, playing = null)
        NerdStats.onLosslessRaceStart(mediaId)
        TrackLog.d(
            TAG,
            "'${target.title}' is playing from cache and was never resolved; looking for a better copy",
        )
        return true
    }

    /**
     * Looks for a stream that actually satisfies the request, for a track
     * already playing.
     *
     * The track stays in [NerdStats.racingLossless] when this returns a stream
     * — the caller ends it once the swap has landed or been given up on. The
     * badge describes the *upgrade*, not the search behind it, and those stop
     * being the same thing as soon as the search can finish before the track
     * it was for comes round. That is now the ordinary case: a track is
     * resolved while its predecessor plays, so by the time a listener is
     * hearing it the lookup has often been sitting on the answer for a minute.
     * Ended here, "Upgrading Quality" was drawn for the few milliseconds
     * between the track becoming current and this returning, and the audio
     * then changed five seconds later with nothing on screen having said so.
     *
     * @param playingDurationSec the runtime the *decoder* reports, which is
     *   the one thing here that is measured rather than claimed. A candidate
     *   has to match it — see [SourceResolver.upgradeFor].
     * @return the better stream, or null if there isn't one, in which case
     *   this track is never asked about again.
     */
    suspend fun lookAgain(mediaId: String, playingDurationSec: Int?): SourceStream? {
        val waiting = pending[mediaId] ?: return null
        var found: SourceStream? = null
        /**
         * Whether the search got as far as an answer, as opposed to being
         * cancelled on its way to one. The difference is the whole of what
         * [asked] is allowed to mean.
         */
        var answered = false
        return try {
            // The lookup that was still running when the fallback won the race
            // gets first refusal: what it returns is the stream that would
            // have played with no seam at all had it been a few seconds
            // quicker.
            //
            // It is not taken on trust, though, and the reason is the whole
            // difference between where it was going to be used and where it is
            // used now. The live path's match is made against a runtime
            // *claimed* by whoever queued the track, and where nothing in the
            // results agrees with that runtime, [SourceResolver.preferred]
            // lets the title and artist decide alone — correctly, for picking
            // what to play from the start. Cutting into a track already
            // playing is a stricter question, and it gets the stricter test
            // that [SourceResolver.upgradeFor] applies to its own candidates,
            // against the length the decoder is reporting. Measured here, the
            // difference was a 189-second cut swapped into a 163-second song,
            // which played for five seconds of silence and was then put back.
            waiting.inFlight?.let { lookup ->
                val late = runCatching { lookup.await() }.getOrNull()
                if (late != null &&
                    SourceResolver.worthSwapping(late.format, waiting.playing) &&
                    SourceResolver.sameRecordingAs(late.durationSec, playingDurationSec)
                ) {
                    found = late
                    answered = true
                    return late
                }
            }
            // It finished with nothing better, so the question gets asked
            // again from scratch — this time waiting on every module, which is
            // what the live path could not afford to do.
            SourceResolver.upgradeFor(
                waiting.target.copy(durationSec = playingDurationSec ?: waiting.target.durationSec),
                playing = waiting.playing,
            ).also {
                found = it
                answered = true
            }
        } finally {
            if (answered) {
                // The question has now been asked, whatever the answer. Leaving
                // it pending would re-run the whole search on every pause and
                // resume, and leaving it out of [asked] would let
                // [adoptUnresolved] offer the same track again at the next
                // progress sample.
                pending.remove(mediaId)
                asked += mediaId
            }
            // Otherwise the search was cancelled — the queue moved on while it
            // was still running — and *nothing was learned*. The track is left
            // exactly as it was found: still pending, still worth asking about
            // if the listener comes back to it.
            //
            // It was not, and that was the single biggest hole in this feature.
            // Two seconds on another track was enough to record a search that
            // never finished as a settled "nothing better exists", and the
            // track then played on at its original bitrate for the rest of the
            // session with no badge, no search and no way back:
            //
            // ```
            //   21:36:44  'double take' … looking for a better copy
            //   21:36:46  TIMING track selected: e-9zmBhCfmk
            //   21:36:48  TIMING track selected: IYOfGK5Zos4   ← and nothing
            // ```
            //
            // Only a *no* ends the race here. A yes leaves the badge up for
            // the caller to close out when the swap it describes has actually
            // happened — see this function's own documentation.
            if (found == null) NerdStats.onLosslessRaceEnd(mediaId)
        }
    }

    /** Abandons the second look for [mediaId] — the queue has moved on. */
    fun forget(mediaId: String) {
        pending.remove(mediaId)?.inFlight?.cancel()
        forced.remove(mediaId)
        shelved.remove(mediaId)
        auditioning -= mediaId
        NerdStats.onLosslessRaceEnd(mediaId)
    }

    // ── Handing the stream to the player ────────────────────────────────────

    /** Parks [stream] for [mediaId], to be picked up when the item is reopened. */
    fun force(mediaId: String, stream: SourceStream) {
        forced[mediaId] = stream
    }

    /**
     * The upgraded stream for a request carrying the [MARKER], or null.
     *
     * Read rather than consumed: ExoPlayer reopens a source more than once
     * over a track's life — a seek past the buffer, a resumed playback, a
     * cache miss — and each of those has to arrive at the same bytes.
     */
    fun forcedStream(uri: Uri): SourceStream? {
        if (uri.getQueryParameter(MARKER) != UPGRADED) return null
        return uri.getQueryParameter("v")?.let(forced::get)
    }

    /** The same URI, marked so that Media3 rebuilds the source and the cache keys it apart. */
    fun upgradedUri(uri: String): String = "$uri&$MARKER=$UPGRADED"

    /**
     * The suffix that keeps an upgraded track's bytes off the copy it
     * replaced — see [AudioCache]'s key factory for why sharing one entry
     * between two renditions corrupts both.
     */
    fun cacheTag(uri: Uri): String? = uri.getQueryParameter(MARKER)
}
