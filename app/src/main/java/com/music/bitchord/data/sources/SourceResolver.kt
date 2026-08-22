package com.music.bitchord.data.sources

import android.net.Uri
import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import kotlinx.coroutines.CancellationException

/**
 * Turns a queued track into an openable stream, using whichever source can
 * best serve it.
 *
 * Two things happen here that don't happen in any single [MusicSource]:
 *
 *  1. **The quality question is answered once**, from the connection in hand
 *     and the user's ceiling for it — see [requestForNow]. Sources are told
 *     what to serve; they don't each re-derive it.
 *
 *  2. **The order is applied.** A track is pinned to the source that produced
 *     it, but a pin is a starting point, not a cage: with lossless on, a
 *     higher-priority source that can serve the same recording bit-exact gets
 *     asked first, and a source that fails gets stepped over rather than
 *     failing the track.
 *
 * Whether another source *has* the same recording is [TrackMatcher]'s question,
 * not this one's. Everything here does with a candidate list is ask that, and
 * everything a source is asked for comes from the same place — so the library,
 * a playlist, radio, search and the home feed all substitute on identical
 * terms, whichever of them a track was queued from.
 */
object SourceResolver {

    private const val TAG = "BitChord"

    /**
     * What to ask a source for, right now.
     *
     * The lossless switch is a preference, not an override — it loses to the
     * connection's own ceiling, which is the setting someone reached for
     * specifically to protect a data plan. A capped connection gets a capped
     * transcode whether or not lossless is on, because the alternative is a
     * switch in one part of Settings quietly undoing a switch in another, and
     * the one being undone is the one attached to a bill.
     */
    fun requestForNow(): StreamRequest {
        val ceiling = AppSettings.effectiveAudioQuality
        return when {
            ceiling != AudioQuality.HIGH -> StreamRequest.Capped(ceiling.maxKbps)
            AppSettings.losslessAudio.value -> StreamRequest.Lossless
            else -> StreamRequest.Best
        }
    }

    /**
     * @param uri a `bitchord://source?...` URI as built by [SourceRegistry.trackUri].
     * @return the stream, or null when nothing enabled could serve the track.
     */
    suspend fun resolve(uri: Uri): SourceStream? {
        val configId = uri.getQueryParameter("s") ?: return null
        val trackId = uri.getQueryParameter("t") ?: return null
        return resolve(
            configId = configId,
            trackId = trackId,
            target = targetIn(uri),
        )
    }

    /**
     * The recording a playback URI describes, for matching it elsewhere.
     *
     * Title, artist and runtime ride in the URI because they are what a
     * cross-source match is made on, and the resolver runs on ExoPlayer's
     * loader thread with nothing but a DataSpec in hand — see
     * [toMediaItem][com.music.bitchord.playback.toMediaItem].
     */
    fun targetIn(uri: Uri) = TrackMatcher.Target(
        title = uri.getQueryParameter("n").orEmpty(),
        artist = uri.getQueryParameter("a").orEmpty(),
        durationSec = uri.getQueryParameter("d")?.toIntOrNull(),
    )

    /**
     * @param target is what a cross-source match is made on. Without it the
     *   only possible behaviour is "the pinned source or nothing", which is
     *   still a correct outcome — just a worse one.
     */
    suspend fun resolve(
        configId: String,
        trackId: String,
        target: TrackMatcher.Target,
    ): SourceStream? {
        val request = requestForNow()
        val pinned = SourceRegistry.instance(configId)
        val active = SourceRegistry.active()

        // The upgrade path: with lossless asked for and the pinned source
        // unable to serve it, anything ranked above it that can is worth
        // asking first. This is the whole reason the list is ordered — it is
        // what makes "my own FLAC of this, if I have one, else stream it"
        // expressible.
        if (request is StreamRequest.Lossless && pinned?.kind?.canServeLossless != true) {
            for (source in rankedAbove(configId, active)) {
                if (!source.kind.canServeLossless) continue
                val upgraded = matchAndStream(source, target, request) ?: continue
                TrackLog.d(TAG, "lossless upgrade: '${target.title}' served by ${source.displayName}")
                return upgraded
            }
        }

        if (pinned != null) {
            attempt(pinned) { pinned.stream(trackId, request) }?.let { return it }
        }

        // Last resort. A track whose own source is down is still a track the
        // user asked for, and another source having it is not unlikely — this
        // is the difference between a dead server skipping the queue forward
        // and a dead server being invisible.
        for (source in active) {
            if (source.configId == configId) continue
            matchAndStream(source, target, request)?.let {
                TrackLog.d(TAG, "fallback: '${target.title}' served by ${source.displayName}")
                return it
            }
        }
        return null
    }

    /**
     * The stream for a YouTube track from a source the user ranked above
     * YouTube, or null when none of them has the recording.
     *
     * A YouTube track keeps its bare video id rather than a
     * [SourceRegistry.trackKey] — see [YouTubeSource] for why — so it reaches
     * playback as `bitchord://watch?v=…` and never passes through [resolve].
     * Without this, ordering a source above YouTube did nothing for anything
     * *queued* from YouTube: the library, a playlist, radio, the home feed —
     * which is very nearly everything. The list said "prefer my server" and
     * only search results honoured it.
     *
     * The match is the same strict one [resolve] uses, for the same reason:
     * this substitutes something else for the track the user picked, and a
     * loose match plays the wrong song under the right title.
     */
    suspend fun substituteForYouTube(target: TrackMatcher.Target): SourceStream? {
        if (target.title.isBlank()) return null
        val active = SourceRegistry.active()
        val youtube = active.firstOrNull { it.kind == SourceKind.YOUTUBE } ?: return null
        val request = requestForNow()
        for (source in rankedAbove(youtube.configId, active)) {
            val stream = matchAndStream(source, target, request) ?: continue
            // Says what was found, not what the caller will do with it. This
            // line used to read "substituted" unconditionally, including for
            // streams the caller went on to refuse — which made a log of a
            // track that played on YouTube look like a track that hadn't.
            TrackLog.d(
                TAG,
                "substituted: '${target.title}' served by ${source.displayName} over YouTube" +
                    " at ${stream.format.summary}" + if (stream.belowRequest) " (below request)" else "",
            )
            return stream
        }
        return null
    }

    /**
     * A stream that genuinely satisfies the current request, for a track that
     * is already playing on one that doesn't — or null if there isn't one.
     *
     * The same search as [substituteForYouTube] with two differences, both of
     * which are only affordable because sound is already coming out:
     *
     *  - Every module is waited for, including the one the live path gave up
     *    on to get playback started. That module is frequently the point:
     *    dropping it is what left the listener on a stream from whoever
     *    happened to be quick.
     *  - A result that isn't lossless is still worth having when it is
     *    audibly better than what is playing — see [worthSwapping]. Refusing
     *    those outright is what left a track on YouTube's 160kbps Opus while
     *    a 320kbps AAC from a module sat in hand, unused, because it wasn't
     *    the FLAC that had been asked for.
     *
     * [target] must carry the runtime of the track *actually playing* — see
     * [matchAndStream]'s use of it. Swapping the audio under a listener is
     * only defensible when the replacement is the same recording, and length
     * is the check that a title cannot fake.
     *
     * @param playing what the listener is hearing now, so a lossy candidate
     *   can be judged against it rather than against the request. Null means
     *   unknown, and an unknown floor is treated as one nothing lossy clears:
     *   a swap that might be a downgrade is worse than no swap at all.
     */
    suspend fun upgradeFor(
        target: TrackMatcher.Target,
        playing: StreamFormat? = null,
    ): SourceStream? {
        if (target.title.isBlank() || target.durationSec == null) return null
        val request = requestForNow()
        if (request !is StreamRequest.Lossless) return null
        val active = SourceRegistry.active()
        val youtube = active.firstOrNull { it.kind == SourceKind.YOUTUBE } ?: return null
        for (source in rankedAbove(youtube.configId, active)) {
            if (!source.kind.canServeLossless) continue
            val stream = matchAndStream(source, target, request, patient = true) ?: continue
            if (!worthSwapping(stream.format, playing)) continue
            TrackLog.d(TAG, "upgrade found: '${target.title}' at ${stream.format.summary} from ${source.displayName}")
            return stream
        }
        return null
    }

    /**
     * Whether [candidate] is enough better than [playing] to be worth the
     * break in the audio that swapping to it costs.
     *
     * Lossless always is: it is what was asked for, and the whole point.
     *
     * A lossy candidate has to clear [UPGRADE_MIN_GAIN_KBPS] over what is
     * already playing, which is deliberately a wide gap rather than a strict
     * improvement. Bitrate compares poorly across codecs — Opus at 160kbps
     * and AAC at 256kbps are much the same thing to listen to — so a margin
     * narrow enough to be codec-sensitive would be a margin that buys a seam
     * in the audio for nothing. 160 to 320 clears it; 128 to 192 does not.
     */
    internal fun worthSwapping(candidate: StreamFormat, playing: StreamFormat?): Boolean {
        if (candidate.isLossless == true) return true
        val gain = (candidate.kbps ?: return false) - (playing?.kbps ?: return false)
        return gain >= UPGRADE_MIN_GAIN_KBPS
    }

    /**
     * Whether two runtimes are close enough to be the same recording, for a
     * swap into a track that is already playing.
     *
     * The same [UPGRADE_DRIFT_SEC] bar [matchAndStream] applies to the
     * candidates it finds itself, exposed for the one candidate it doesn't:
     * the live lookup [QualityUpgrade][com.music.bitchord.playback.QualityUpgrade]
     * inherits when the fallback wins the race.
     *
     * Either side being unknown is a no. An unverifiable length is not a
     * length that agrees, and the cost of being wrong here is a listener's
     * song replaced mid-play by a different cut of it.
     */
    fun sameRecordingAs(candidateSec: Int?, playingSec: Int?): Boolean {
        if (candidateSec == null || playingSec == null) return false
        return kotlin.math.abs(candidateSec - playingSec) <= UPGRADE_DRIFT_SEC
    }

    /**
     * Whether anything outranks YouTube right now — i.e. whether a YouTube
     * track is worth offering around before it is resolved.
     *
     * Answerable from the source list alone, without a search, which is what
     * lets the cache and the read-ahead in
     * [AudioCache][com.music.bitchord.playback.AudioCache] decide how to treat
     * a YouTube id before anyone has asked a source for it.
     */
    fun canSubstituteForYouTube(): Boolean =
        SourceRegistry.active().indexOfFirst { it.kind == SourceKind.YOUTUBE } > 0

    /**
     * The sources ranked above [configId], in order.
     *
     * A config that isn't in [active] ranks last: it is disabled or incomplete,
     * and everything that *is* enabled is worth trying ahead of it.
     */
    private fun rankedAbove(configId: String, active: List<MusicSource>): List<MusicSource> =
        active.indexOfFirst { it.configId == configId }
            .let { if (it < 0) active.size else it }
            .let { active.take(it) }

    /**
     * Searches [source] for the recording in [target] and streams it if one of
     * the answers really is that recording — see [TrackMatcher].
     *
     * Each query the matcher offers is tried in turn, because the first one
     * failing is usually the catalogue disagreeing about how a track is
     * *filed*, not about whether it holds it. Stopping at the first empty
     * answer is what made a source look like it was missing half of what it
     * had. A source that *throws* still gets no second chance: that is its
     * server having a problem, and asking it again differently won't fix it.
     */
    private suspend fun matchAndStream(
        source: MusicSource,
        target: TrackMatcher.Target,
        request: StreamRequest,
        patient: Boolean = false,
    ): SourceStream? {
        for (query in TrackMatcher.queries(target)) {
            val candidates = attempt(source) {
                source.search(query, limit = MATCH_CANDIDATES, waitForAll = patient)
            } ?: return null
            var matches = TrackMatcher.ranked(candidates, target)
            // The extra bar for an upgrade: the replacement has to be the same
            // *length* as what is playing, to the second or so. A title and an
            // artist can agree across two different edits of a song; a runtime
            // that agrees this closely is one recording, and nothing else is
            // worth cutting a listener's audio for.
            if (patient) {
                matches = matches.filter { TrackMatcher.withinSeconds(it, target, UPGRADE_DRIFT_SEC) }
            }
            if (matches.isEmpty()) continue
            return streamBest(source, matches, target, request)
        }
        return null
    }

    /**
     * Opens the best of [matches] that can actually serve [request].
     *
     * Two things happen here that a single "take the top match" cannot:
     *
     *  1. **Rows that advertise the tier asked for go first.** Every one of
     *     these is genuinely the recording, so which one plays is a question
     *     about quality, not identity — and a catalogue that has already said
     *     it holds a FLAC is a better place to ask for one than a catalogue
     *     that said nothing. Without this the order was confidence alone, and
     *     a 16-bit FLAC lost to a Deezer row over how its artists were spelt.
     *
     *  2. **What comes back is checked against what was asked for.** A module
     *     that cannot serve lossless does not always say so; some quietly walk
     *     their own fallback chain and hand back a 128kbps MP3 with the right
     *     title on it. Reading [StreamFormat] before accepting the URL is what
     *     turns that into "this one can't, try the next" instead of into the
     *     listener's evening.
     *
     * The under-quality stream is kept rather than dropped: if nothing better
     * exists anywhere, playing the MP3 is still better than skipping the
     * track. It is a floor, not a first choice.
     */
    /**
     * The matching rows, in the order they are worth opening.
     *
     * Two rules, and the order of them is the point:
     *
     *  1. **Length decides which recording, first.** When any candidate agrees
     *     with the runtime being asked for to within a couple of seconds, only
     *     the candidates that agree are eligible at all. A catalogue holding
     *     the track under its right title and right artist can still be
     *     holding a different *cut* of it — a DJ edit on a compilation, an
     *     extended mix — and the runtime is what separates those when nothing
     *     in the title does. If nothing agrees, nothing is excluded: the
     *     runtimes are simply not informative here and the score stands alone.
     *
     *  2. **Quality decides between equals, second.** Among rows that are the
     *     same recording, one advertising a lossless copy is the better place
     *     to ask. This was doing that job *first*, which is how a 185-second
     *     "Punjabi Dj Holi songs" cut beat the 180-second album track on the
     *     strength of the word `flac` in its listing. A declared tier is a
     *     reason to prefer one copy of a recording over another; it is not a
     *     reason to play a different recording.
     */
    internal fun preferred(
        matches: List<Song>,
        target: TrackMatcher.Target,
        wantsLossless: Boolean,
    ): List<Song> {
        val sameLength = matches.filter { TrackMatcher.withinSeconds(it, target, SAME_RECORDING_SEC) }
        val eligible = sameLength.ifEmpty { matches }
        if (!wantsLossless) return eligible
        // Stable, so the confidence order [TrackMatcher.ranked] produced
        // survives inside each tier.
        return eligible.sortedByDescending { it.sourceQuality == ModuleSource.LOSSLESS }
    }

    private suspend fun streamBest(
        source: MusicSource,
        matches: List<Song>,
        target: TrackMatcher.Target,
        request: StreamRequest,
    ): SourceStream? {
        val wantsLossless = request is StreamRequest.Lossless
        val ordered = preferred(matches, target, wantsLossless)
        var settleFor: SourceStream? = null
        for (match in ordered.take(STREAM_ATTEMPTS)) {
            val trackId = SourceRegistry.parseTrackKey(match.videoId)?.second ?: match.videoId
            val opened = attempt(source) { source.stream(trackId, request) } ?: continue
            // The row this URL came from knows how long the recording is; the
            // URL itself doesn't. Carried along so a caller swapping this into
            // a track already playing can check it — see [SourceStream.durationSec].
            val stream = opened.copy(durationSec = TrackMatcher.secondsOf(match.durationText))
            val served = stream.format
            if (!wantsLossless || served.isLossless == true || served.statesNothingLossy) {
                TrackLog.d(
                    TAG,
                    "${source.displayName} matched '${match.title}' by '${match.artist}' → ${served.summary}",
                )
                return stream
            }
            TrackLog.d(TAG, "${source.displayName} offered ${served.summary} for '${match.title}'; looking further")
            // The floor is the *best* of what was refused, not the first of
            // it. These arrive in match order, which has nothing to do with
            // quality: a 320kbps AAC and a 128kbps MP3 are both rejections,
            // and which one the listener ends up on if nothing better exists
            // should not come down to which catalogue happened to be asked
            // first.
            settleFor = betterOf(settleFor, stream.copy(belowRequest = true))
        }
        return settleFor
    }

    /** The higher-quality of two streams, by codec first and bitrate second. */
    private fun betterOf(current: SourceStream?, candidate: SourceStream): SourceStream {
        if (current == null) return candidate
        val mine = current.format
        val theirs = candidate.format
        if (mine.isLossless != theirs.isLossless) {
            return if (theirs.isLossless == true) candidate else current
        }
        return if ((theirs.kbps ?: 0) > (mine.kbps ?: 0)) candidate else current
    }

    /**
     * Whether a format has said nothing that rules lossless out.
     *
     * Unknown is not the same as lossy, and a source that reports neither a
     * codec nor a bitrate has not failed the request — it has declined to
     * describe it, and the decoder will say soon enough. A stated bitrate is
     * different: nothing states a bitrate for a FLAC.
     */
    private val StreamFormat.statesNothingLossy: Boolean
        get() = isLossless == null && kbps == null

    /**
     * Runs [block], turning any failure into null and a log line.
     *
     * Every call into a source is a call to somebody else's server, and a
     * source that throws must cost the *source* its turn, not the track its
     * playback. Cancellation is re-thrown: that is the caller giving up, and
     * swallowing it would keep walking sources for a track nobody is waiting
     * for any more.
     */
    private suspend fun <T> attempt(source: MusicSource, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TrackLog.w(TAG, "${source.displayName} failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /**
     * How many answers per query are worth weighing.
     *
     * Wider than it needs to be for a well-behaved catalogue, because
     * [TrackMatcher.best] scores the whole list rather than taking the first
     * acceptable row: a backend that ranks the karaoke version, three covers
     * and a sped-up edit above the album cut still has the album cut in here
     * somewhere, and the extra rows cost one response body, not one request.
     */
    private const val MATCH_CANDIDATES = 15

    /**
     * How many of the matching rows are worth actually opening.
     *
     * Each one is a round trip to a stream endpoint, so this is the budget for
     * "the first copy wasn't the quality asked for" — enough to get past a
     * module whose lossless backend is down, not enough to spend a listener's
     * patience walking a whole result list.
     */
    private const val STREAM_ATTEMPTS = 3

    /**
     * How far a replacement's runtime may sit from the playing track's before
     * it stops being the same recording.
     *
     * Far tighter than [TrackMatcher]'s own tolerance, and deliberately: that
     * one is deciding what to play, this one is deciding whether to cut the
     * audio a listener is in the middle of. Two seconds allows for a service
     * rounding a runtime differently and nothing else.
     */
    private const val UPGRADE_DRIFT_SEC = 2

    /**
     * How close two runtimes have to be to be the same cut of a song.
     *
     * Wide enough for a catalogue rounding, or a second of lead-in trimmed
     * differently. Narrow enough to separate the album track from the DJ edit
     * sitting next to it in the same search results under the same name.
     */
    private const val SAME_RECORDING_SEC = 3

    /**
     * How many kbps a lossy stream has to gain before it earns a seam in the
     * audio — see [worthSwapping].
     *
     * Sized off the two rates this actually decides between: YouTube's Opus,
     * which lands around 160, and a lossy module tier, which is 320. Anything
     * much smaller would start firing on differences no one can hear.
     */
    private const val UPGRADE_MIN_GAIN_KBPS = 96
}
