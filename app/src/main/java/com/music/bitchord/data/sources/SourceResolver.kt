package com.music.bitchord.data.sources

import android.net.Uri
import android.util.Log
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.DownloadQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/**
 * Turns a queued track into an openable stream, using whichever source can
 * best serve it.
 *
 * Two things happen here that don't happen in any single [MusicSource]:
 *
 *  1. **The quality question is answered once**, from the connection in hand
 *     and the user's ceiling for it — see [requestForNow], or
 *     [requestForDownload] for the one caller whose answer becomes a file
 *     rather than a stream. Sources are told what to serve; they don't each
 *     re-derive it.
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
        // Always the best the sources can do, bounded only by the connection's
        // own ceiling. There used to be a "Prefer lossless" switch in front of
        // this and it earned its removal: every source already degrades on its
        // own terms — a module hands back its best rendition, JioSaavn its
        // 320kbps AAC, YouTube its Opus — so switching it off asked the module
        // for a *worse* file than it was holding (`StreamRequest.Best` maps to
        // the module's `HIGH` tier) while changing nothing about the two lossy
        // sources. It was a switch whose only real effect was to downgrade the
        // one source that could do better.
        return when {
            ceiling == AudioQuality.LOSSLESS -> StreamRequest.Lossless
            ceiling.maxKbps == Int.MAX_VALUE -> StreamRequest.Best
            else -> StreamRequest.Capped(ceiling.maxKbps)
        }
    }

    /**
     * What to ask a source for on behalf of a file being kept.
     *
     * Reads [AppSettings.downloadQuality] and nothing else — not the ceilings,
     * not the lossless switch. Both of those are about what a *stream* costs on
     * the connection in hand, and this is the one request whose answer outlives
     * the connection: it becomes a file.
     *
     * That independence is the point. The ceilings used to decide this by
     * proxy, so downloading on capped mobile data returned a transcode from
     * YouTube even for someone whose own FLAC server was ranked above it — a
     * setting about data spend silently deciding what a permanent file was made
     * of. Data spend on a download is now [AppSettings.wifiOnlyDownloads]'
     * question, asked once at the point of queueing rather than mixed into
     * this one.
     *
     * @param quality defaults to the setting as it stands, which is what a
     *   caller with no download in flight wants. A caller that is already
     *   fetching one passes the value it started with — a lossless search can
     *   run for twenty seconds (`Downloads.SOURCE_LOOKUP_MS`), which is long
     *   enough for someone to open Settings and change the answer underneath a
     *   file that is half written.
     */
    fun requestForDownload(
        quality: DownloadQuality = AppSettings.downloadQuality.value,
    ): StreamRequest = when {
        quality.keepsLossless -> StreamRequest.Lossless
        quality.maxKbps == Int.MAX_VALUE -> StreamRequest.Best
        else -> StreamRequest.Capped(quality.maxKbps)
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
        album = uri.getQueryParameter("l"),
        isExplicit = uri.getQueryParameter("e")?.let { it == "1" },
        isVideo = uri.getQueryParameter("m") == "1",
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
                // Only a genuinely lossless answer is an upgrade. A source that
                // *can* serve lossless but settled for a transcode of this
                // particular track has not beaten the pinned source at anything
                // — and returning its settle-for here jumped ahead of the
                // track's own source, which is the one the user picked and may
                // well hold something better. It falls through to the pinned
                // source instead, and to [bestAcross] below if that fails.
                if (upgraded.format.isLossless != true) continue
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
        val (fallbackSource, stream) =
            bestAcross(active.filterNot { it.configId == configId }, target, request) ?: return null
        TrackLog.d(TAG, "fallback: '${target.title}' served by ${fallbackSource.displayName}")
        return stream
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
     *
     * Every ranked source is asked at once and the first playable answer is
     * taken — see [bestAcross]. This is the latency-critical half of the pair:
     * it is racing YouTube's own walk, and a stream that arrives after that race
     * is lost cannot start a track. The unhurried half is [upgradeFor], which
     * runs with sound already playing and is where a slow source's better answer
     * gets its hearing.
     */
    suspend fun substituteForYouTube(target: TrackMatcher.Target): SourceStream? {
        // Video rows always use YouTube's own stream unless the listener
        // explicitly converts the current item in the player. A module match
        // is another recording and can be a wrong song altogether.
        if (target.title.isBlank() || target.isVideo) return null
        val active = SourceRegistry.active()
        val youtube = active.firstOrNull { it.kind == SourceKind.YOUTUBE } ?: return null
        val request = requestForNow()
        val (source, stream) = bestAcross(rankedAbove(youtube.configId, active), target, request)
            ?: return null
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

    /**
     * The copy of [target] held by a source quick enough to ask about *before*
     * the track is played — or null when no such source is enabled, or none of
     * them has it.
     *
     * This is the same substitution [substituteForYouTube] makes, moved earlier.
     * The difference is only which sources are asked: [SourceKind.worthPrefetching]
     * narrows it to the ones that answer in a round trip rather than in ten
     * seconds, because this runs speculatively for a track nobody has reached
     * yet and a slow lookup would still be going when they did.
     *
     * ### What the caller must do with the answer
     *
     * Pin it. A returned stream is only useful if the *same* stream is what
     * playback goes on to open, and the caller is expected to record it with
     * [StreamChoice][com.music.bitchord.playback.StreamChoice] before caching a
     * byte of it. Without that, playback re-runs the race, may land on a
     * different source, and writes a second file into the cache entry the warm
     * one already half-filled — which is the exact corruption
     * [StreamChoice] exists to prevent, arrived at from a new direction.
     *
     * Returning null is not a failure and needs no handling beyond falling back
     * to YouTube, which is what the caller would have done anyway: nothing has
     * been pinned, so the ordinary resolve at playback time is untouched.
     */
    suspend fun prefetchSubstitute(target: TrackMatcher.Target): SourceStream? {
        if (target.title.isBlank() || target.isVideo) return null
        val active = SourceRegistry.active()
        val youtube = active.firstOrNull { it.kind == SourceKind.YOUTUBE } ?: return null
        val quick = rankedAbove(youtube.configId, active).filter { it.kind.worthPrefetching }
        if (quick.isEmpty()) return null
        val (source, stream) = bestAcross(quick, target, requestForNow()) ?: return null
        TrackLog.d(
            TAG,
            "warmed: '${target.title}' from ${source.displayName} at ${stream.format.summary}",
        )
        return stream
    }

    /**
     * A stream that genuinely satisfies the current request, for a track that
     * is already playing on one that doesn't — or null if there isn't one.
     *
     * The same search as [substituteForYouTube] with two differences, both of
     * which are only affordable because sound is already coming out:
     *
     *  - Every module *within* a source is waited for, including the one the
     *    live path gave up on to get playback started (`waitForAll`). That
     *    module is frequently the point: dropping it is what left the listener
     *    on a stream from whoever happened to be quick.
     *  - A result that isn't lossless is still worth having when it is
     *    audibly better than what is playing — see [worthSwapping]. Refusing
     *    those outright is what left a track on YouTube's 160kbps Opus while
     *    a 320kbps AAC from a module sat in hand, unused, because it wasn't
     *    the FLAC that had been asked for.
     *  - Every source is asked, not only the ones that can serve lossless. That
     *    follows from the bullet above: once a lossy stream can win, a lossy
     *    *source* has to be allowed to offer one.
     *
     * Where it matches [substituteForYouTube] exactly is in taking the first
     * answer that clears the bar rather than the best of all of them — see
     * [bestAcross]. The bar here is [worthSwapping] rather than "satisfies the
     * request", and it is a high one: anything clearing it is lossless, or a
     * gain of [UPGRADE_MIN_GAIN_KBPS] over what the listener is hearing. Holding
     * such a stream back to see whether a slower source can do better trades a
     * certain improvement now for a possible improvement later, and "later" here
     * was measured at thirteen seconds.
     *
     * The cost of that choice is real and worth naming: a slow source holding a
     * FLAC can lose to a fast one holding a 320kbps AAC, and the track then
     * plays lossy for the rest of its length, because [QualityUpgrade][com.music.bitchord.playback.QualityUpgrade]
     * marks a track asked once the answer is yes. It is the same trade the live
     * path makes, made for the same reason.
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
        if (target.title.isBlank() || target.durationSec == null || target.isVideo) return null
        val request = requestForNow()
        val active = SourceRegistry.active()
        val youtube = active.firstOrNull { it.kind == SourceKind.YOUTUBE } ?: return null
        // Every source gets asked, whether or not it can serve lossless, and
        // they are asked at once.
        //
        // There used to be a `canServeLossless` skip here, applied whenever the
        // request was [StreamRequest.Lossless] — which is what an unmetered
        // connection asks for, i.e. nearly always. Its reasoning was that only a
        // lossless source can satisfy a lossless request, and that is true and
        // beside the point: this function is not serving the request, it is
        // deciding whether anything beats what is *already playing*.
        // [worthSwapping] is the bar for that, and a 320kbps source clears it
        // over YouTube's 160kbps Opus by nearly twice the required margin.
        //
        // Racing them matters as much as asking them. Walked in rank order this
        // took 13.6s on 'Bounce' — a module needed 7.6s to search and another
        // 5.0s to produce a 128kbps MP3 that was then refused, and only after
        // all of that was JioSaavn asked, which answered with 320kbps in 246ms:
        //
        // ```
        //   46:44.823  'Bounce' is playing 141 kbps … looking for a better copy
        //   46:57.477  Ricky's Addon offered MP3 · 128 kbps
        //   46:57.478  … isn't worth swapping 'Bounce' off 141 kbps
        //   46:57.478  ▶ JioSaavn searchSongs()        ← 12.65s in
        //   46:58.608  upgraded to MP4 · 320 kbps at 13569ms
        // ```
        //
        // Thirteen seconds of a 143-second track played at the wrong bitrate,
        // and the seam then landed mid-song rather than near its start. Raced,
        // the same swap happens inside a second.
        val (source, chosen) = bestAcross(
            rankedAbove(youtube.configId, active),
            target,
            request,
            waitForAll = true,
            strictLength = true,
            requireSharedArtist = true,
        ) { candidate, stream ->
            worthSwapping(stream.format, playing).also { worth ->
                // Named rather than skipped silently. This is the one refusal
                // in the upgrade path that discards a stream already found,
                // matched and length-checked, and a silent skip reads in the log
                // exactly like a source having nothing — which is how a null
                // [playing] came to quietly turn the whole cached-track path
                // lossless-only for a while without leaving a trace.
                if (!worth) {
                    TrackLog.d(
                        TAG,
                        "${candidate.displayName}'s ${stream.format.summary} isn't worth swapping " +
                            "'${target.title}' off ${playing?.summary ?: "an unmeasured stream"}",
                    )
                }
            }
        } ?: return null
        TrackLog.d(TAG, "upgrade found: '${target.title}' at ${chosen.format.summary} from ${source.displayName}")
        return chosen
    }

    /**
     * The copy of a track about to be downloaded that is worth keeping over
     * YouTube's, or null when nothing configured has one.
     *
     * The download path has never come through here. It resolves YouTube
     * directly — see
     * [resolveForDownload][com.music.bitchord.data.innertube.StreamResolver.resolveForDownload]
     * — so someone with a FLAC server ranked above YouTube was *streaming* the
     * FLAC and *downloading* a transcode of the same recording. This closes that
     * gap, and it is the only search in this class whose result becomes a file.
     *
     * ### The order it asks in
     *
     *  1. **Bit-exact, in rank order** — on the Lossless rung only, since that
     *     is the only rung asking for it. The first source that hands back a
     *     genuinely lossless file wins outright and nothing else is asked: it is
     *     the whole point of the setting and no bitrate competes with it.
     *  2. **The best lossy copy otherwise.** A lossless source that settled for
     *     a transcode is not thereby wasted — its offer is kept and weighed
     *     against what the lossy sources hold, and the better rendition wins on
     *     [isBetter]. This is where JioSaavn's 320kbps AAC comes in: it outranks
     *     YouTube for playback and used to be skipped entirely here, so a track
     *     streamed at 320 was filed at whatever YouTube's ladder gave.
     *  3. **YouTube**, by this returning null. Which is not a failure — see the
     *     floor below for when it is deliberately preferred.
     *
     * Steps 1 and 2 are started together rather than in sequence. A module
     * search runs to twenty seconds and JioSaavn answers in about four tenths of
     * one, and the whole lookup is bounded by `Downloads.SOURCE_LOOKUP_MS` —
     * queued behind the modules, the fast source would routinely have the
     * timeout land on it and the download would fall to YouTube holding a 320
     * it never got to hear about. Same reasoning as [bestAcross], for the same
     * measured reason.
     *
     * On the **High** rung step 1 is empty and every ranked source goes into
     * step 2's race, asked for [StreamRequest.Best]. A bit-exact answer to that
     * is refused rather than kept: someone who chose High over Lossless chose
     * the smaller file, and a FLAC filed under this setting would additionally
     * be missed by the already-on-disk check in `Downloads.prepare` — which only
     * looks for lossless extensions when the setting keeps lossless — and so be
     * re-downloaded on every pass through the queue.
     *
     * ### The floor, and why a lossy answer can still lose to YouTube
     *
     * A lossy candidate is only kept if it beats [YOUTUBE_BEST_AAC_KBPS] — the
     * top of YouTube's own AAC ladder. Anything at or under that is trading one
     * lossy copy for another and giving up the more reliable fetch to do it,
     * which is what the old blanket refusal of settle-fors was really protecting
     * against. So a 320 wins and a module's 128kbps MP3 does not, and neither
     * does a format that never stated a bitrate: a download has to *name the
     * file* before the first byte lands, and an unstated rendition is nothing to
     * judge. That last part is stricter than [streamBest]'s
     * [statesNothingLossy] allowance, deliberately — playback can hand an
     * undescribed URL to the decoder and let it work the codec out.
     *
     * ### Which settings get this far
     *
     * [DownloadQuality.LOSSLESS][com.music.bitchord.data.settings.DownloadQuality.LOSSLESS]
     * and [DownloadQuality.HIGH][com.music.bitchord.data.settings.DownloadQuality.HIGH];
     * not [DownloadQuality.STANDARD][com.music.bitchord.data.settings.DownloadQuality.STANDARD].
     * Standard is a 128kbps ceiling chosen to fit more on the device, and a
     * source cannot be asked for *that* rung — only for the best it has, which
     * is a bigger file than the setting exists to avoid. YouTube's ladder is the
     * only one that can be capped, so Standard stays entirely YouTube's.
     *
     * Note which setting decides this, since it used to be the wrong one: the
     * connection's ceiling ended this here instead, which meant a download on
     * mobile data took a transcode of a recording the user owns losslessly. That
     * was a data-plan setting deciding what a permanent file was made of, and
     * [AppSettings.wifiOnlyDownloads] does the data-plan job properly now —
     * before a byte is fetched rather than by quietly downgrading the result.
     *
     * @param target the recording to look for, off the row being downloaded. A
     *   blank title can only produce a wrong match; a null runtime is allowed
     *   and costs the length check rather than the search.
     * @param request what the download is for, pinned by the caller for the
     *   duration of one file — see [requestForDownload].
     */
    suspend fun forDownload(
        target: TrackMatcher.Target,
        request: StreamRequest = requestForDownload(),
    ): SourceStream? = coroutineScope {
        if (target.title.isBlank()) return@coroutineScope null
        val wantsLossless = when (request) {
            is StreamRequest.Lossless -> true
            is StreamRequest.Best -> false
            // Standard, and the one rung the sources are no use for: it is a
            // ceiling of 128kbps chosen to fit more on the device, and a source
            // has no way to be asked for *that* rung rather than for the best it
            // has. JioSaavn's 320 is not a better answer to it, it is the wrong
            // answer to it. YouTube's ladder is the only one that can be capped.
            is StreamRequest.Capped -> return@coroutineScope null
        }
        val active = SourceRegistry.active()
        // YouTube can be switched off, and a download still goes to it when
        // nothing here answers — the download path never consults this list. So
        // an absent YouTube means everything enabled outranks it, which is
        // already what [rankedAbove] says about a config that isn't in the list.
        val youtubeId = active.firstOrNull { it.kind == SourceKind.YOUTUBE }?.configId
        val ranked = rankedAbove(youtubeId.orEmpty(), active)
        val strictLength = target.durationSec != null

        // Who is in the bit-exact walk, and who is left to the race below it.
        //
        // On the High rung nobody is: there is no lossless answer to look for,
        // so every ranked source goes into the race and competes on rendition
        // alone. That is what puts JioSaavn's 320 in front of YouTube's AAC for
        // a setting whose own description is "best AAC on offer".
        val bitExact = if (wantsLossless) ranked.filter { it.kind.canServeLossless } else emptyList()
        val elsewhere = ranked - bitExact.toSet()

        // Asked now, read at the end — and raced rather than walked, because
        // these differ in speed by two orders of magnitude and the whole lookup
        // is on a clock. See [bestAcross], which takes the first answer that
        // clears the bar rather than the best of all of them: JioSaavn's four
        // tenths of a second is what that rule is tuned for, and a module that
        // beats it to the line with something thin is a case the floor below
        // catches by sending the download to YouTube.
        val elsewhereBest = async {
            bestAcross(
                elsewhere,
                target,
                // Their best lossy rendition, whichever rung is being served.
                // Putting the lossless request to a source that cannot meet it
                // would only come back marked [SourceStream.belowRequest] to
                // say so.
                StreamRequest.Best,
                waitForAll = true,
                strictLength = strictLength,
            ) { source, stream ->
                // Bit-exact audio is *over* budget on the High rung, not a
                // bonus on it. Someone who picked High over Lossless picked the
                // ~8MB file, and a FLAC filed under this setting would also be
                // missed by the already-on-disk check in `Downloads.prepare`,
                // which only looks for lossless extensions when the setting
                // keeps lossless — so it would be re-downloaded every time.
                val ok = wantsLossless || stream.format.isLossless != true
                if (!ok) {
                    TrackLog.d(
                        TAG,
                        "${source.displayName} offered ${stream.format.summary}; " +
                            "more than this download asked for",
                    )
                }
                ok
            }
        }

        // The best lossy copy seen so far and who offered it — carried together
        // so the log line at the end can say where the file came from, which is
        // the only record of it there will be once it is on disk.
        var best: Pair<MusicSource, SourceStream>? = null
        for (source in bitExact) {
            val stream = matchAndStream(
                source,
                target,
                // The request the guard above already narrowed, rather than a
                // second statement of it that could drift from the first.
                request,
                waitForAll = true,
                strictLength = strictLength,
            ) ?: continue
            // Dolby Atmos is not bit-exact PCM, but it is the native premium
            // rendition that playback already prefers.  Keep it on the same
            // first-class download path instead of throwing it away and
            // falling back to YouTube solely because it has no bitrate tag.
            if (stream.format.isLossless == true || stream.format.isDolbyAtmos) {
                TrackLog.d(
                    TAG,
                    "download: '${target.title}' from ${source.displayName} at ${stream.format.summary}",
                )
                // Nothing is waiting on it any more, and leaving it running
                // would hold this whole call open on a source whose answer has
                // just been beaten by the requested premium rendition.
                elsewhereBest.cancel()
                return@coroutineScope stream
            }
            TrackLog.d(
                TAG,
                "${source.displayName} offered ${stream.format.summary} to download; not lossless or Dolby",
            )
            if (isBetter(stream.format, best?.second?.format)) best = source to stream
        }

        // Nothing bit-exact anywhere — or nothing was being looked for.
        // Whatever is left competes on rendition alone, wherever it came from.
        elsewhereBest.await()?.let { (source, stream) ->
            if (isBetter(stream.format, best?.second?.format)) best = source to stream
        }
        val (winner, chosen) = best ?: return@coroutineScope null
        if (!beatsYouTubeAac(chosen.format)) {
            TrackLog.d(
                TAG,
                "best offered for '${target.title}' is ${winner.displayName}'s " +
                    "${chosen.format.summary}; taking YouTube's AAC over that",
            )
            return@coroutineScope null
        }
        TrackLog.d(
            TAG,
            "download: '${target.title}' from ${winner.displayName} at ${chosen.format.summary}, " +
                "over YouTube's AAC",
        )
        chosen
    }

    /**
     * Whether a lossy [candidate] is worth keeping as a file over whatever
     * YouTube's own AAC ladder would have given for the same track.
     *
     * The floor under [forDownload]'s step 2, and deliberately a floor rather
     * than a comparison: see [YOUTUBE_BEST_AAC_KBPS] for why the rung YouTube
     * actually holds is not worth the walk it would take to find out.
     *
     * An unstated bitrate is a no. A download has to name the file before the
     * first byte lands, and a rendition that described itself as nothing is
     * nothing to weigh against the alternative — which is stricter than
     * [streamBest]'s [statesNothingLossy] allowance, deliberately: playback can
     * hand an undescribed URL to the decoder and let it work the codec out.
     */
    internal fun beatsYouTubeAac(candidate: StreamFormat): Boolean =
        (candidate.kbps ?: 0) > YOUTUBE_BEST_AAC_KBPS

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
        if (candidate.isLossless == true || candidate.isDolbyAtmos) return true
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
     * The best stream the configured [sources] can serve for [target] — **all
     * of them asked at once** — or null if none of them has the recording.
     *
     * ### Why they race rather than queue
     *
     * Asking them in rank order is the obvious reading of an ordered list, and
     * it is wrong here, because the sources differ in speed by nearly two orders
     * of magnitude. Measured on '9:45':
     *
     * ```
     *   JioSaavn        search 245ms + stream 131ms   ≈ 0.4s
     *   Ricky's Addon   search → settled stream       ≈ 13.5s
     * ```
     *
     * Queued behind the module, JioSaavn's answer arrives at ~14s. Nobody is
     * waiting that long for a song to start, so YouTube wins the race in
     * [PlaybackService][com.music.bitchord.playback.PlaybackService]'s
     * `resolveWithModulePriority` every single time and the listener gets
     * 160kbps Opus — while a 320kbps copy sat four tenths of a second away.
     * Raced, the same answer arrives before YouTube's own walk finishes and the
     * track starts on it.
     *
     * ### What rank still decides, and what it no longer does
     *
     * Rank decides who is *asked* — [rankedAbove] is still what builds this list
     * — and it breaks ties between answers that arrive together, since each
     * sweep folds in everything that has already crossed the line and picks the
     * best of them with [isBetter]. What it no longer does is let a slow
     * favourite hold up a fast alternative.
     *
     * **A slow source is not thereby lost.** Whatever is returned here starts
     * playing; if it is [SourceStream.belowRequest] the track is marked for a
     * second look, and [upgradeFor] then asks *every* source again with no time
     * limit and swaps up only if what comes back genuinely beats what is playing
     * — see [worthSwapping]. So a module that needed thirteen seconds to find a
     * FLAC still gets to serve it, mid-track, and one that needed thirteen
     * seconds to find a 128kbps MP3 is correctly ignored. That is the trade this
     * whole path exists to make: sound now, quality shortly after.
     *
     * On the latency-critical path, sources still running when an answer is
     * taken are cancelled — the second look re-asks them properly, and leaving
     * them running would spend a listener's radio on a result nothing is waiting
     * for. The background-upgrade path passes [waitForAll], however: it already
     * chose to wait for every source's patient search window, so it must also
     * collect the resulting streams before choosing. Previously that flag only
     * reached each source's search call; this loop still returned the first
     * usable stream and cancelled the rest, which could discard an exact Tidal
     * FLAC or JioSaavn 320 result during an upgrade.
     *
     * @return the winning source alongside its stream, so callers can name it in
     *   a log line without searching the list again.
     */
    internal suspend fun bestAcross(
        sources: List<MusicSource>,
        target: TrackMatcher.Target,
        request: StreamRequest,
        waitForAll: Boolean = false,
        strictLength: Boolean = false,
        requireSharedArtist: Boolean = false,
        accept: (MusicSource, SourceStream) -> Boolean = { _, _ -> true },
    ): Pair<MusicSource, SourceStream>? = coroutineScope {
        val running: MutableList<Deferred<Pair<MusicSource, SourceStream?>>> = sources
            .map { source ->
                async {
                    source to matchAndStream(
                        source,
                        target,
                        request,
                        waitForAll,
                        strictLength,
                        requireSharedArtist,
                    )
                }
            }
            .toMutableList()
        var best: Pair<MusicSource, SourceStream>? = null
        try {
            while (running.isNotEmpty()) {
                val first = select {
                    running.forEach { candidate -> candidate.onAwait { candidate } }
                }
                // Anything that crossed the line while that one was being waited
                // on is already sitting there. Folding those in costs no time at
                // all and is what lets rank break a tie between two sources that
                // both answered quickly.
                val ready = listOf(first) + running.filter { it !== first && it.isCompleted }
                running -= ready.toSet()
                for (done in ready) {
                    val (source, stream) = done.await()
                    if (stream == null) continue
                    if (!accept(source, stream)) continue
                    if (isBetter(stream.format, best?.second?.format)) best = source to stream
                }
                // Playback needs the first usable answer so sound can start.
                // An upgrade is different: it runs while audio is already
                // playing, and its caller explicitly requested every source's
                // patient result. Let every source finish so a fast lower tier
                // cannot cancel a later, better rendition.
                if (best != null && !waitForAll) break
            }
        } finally {
            running.forEach { it.cancel() }
        }
        best
    }

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
     *
     * @param waitForAll holds a multi-backend search open for every backend
     *   instead of answering from whichever of them are quick — affordable only
     *   when nobody is waiting on the first note.
     * @param strictLength requires a candidate's runtime to agree with
     *   [target]'s to within [UPGRADE_DRIFT_SEC]. Kept apart from [waitForAll]
     *   because it is only meaningful when the target *has* a runtime:
     *   [TrackMatcher.withinSeconds] answers false for every candidate against a
     *   null one, so asking for this against a target that never carried a
     *   duration is not a strict search but an empty one.
     */
    private suspend fun matchAndStream(
        source: MusicSource,
        target: TrackMatcher.Target,
        request: StreamRequest,
        waitForAll: Boolean = false,
        strictLength: Boolean = false,
        requireSharedArtist: Boolean = false,
    ): SourceStream? {
        for (query in TrackMatcher.queries(target)) {
            val candidates = attempt(source) {
                source.search(query, limit = MATCH_CANDIDATES, waitForAll = waitForAll)
            } ?: return null
            var matches = TrackMatcher.ranked(candidates, target)
            if (requireSharedArtist) {
                matches = matches.filter { TrackMatcher.sharesArtist(target.artist, it.artist) }
            }
            // JioSaavn can return different audio under the same title and
            // artist on different releases. With no album on the requested
            // track there is no honest way to choose between those rows;
            // duration is not enough when the wrong recording is only a
            // second away. Treat it as this source missing and retain the
            // known-correct fallback.
            if (source.kind == SourceKind.JIOSAAVN &&
                TrackMatcher.hasConflictingAlbums(matches, target)
            ) {
                val canonical = TrackMatcher.uniquelyMostCreditedCloseMatch(matches, target)
                if (canonical == null) {
                    TrackLog.w(
                        TAG,
                        "${source.displayName} returned conflicting albums for '${target.title}'; refusing to guess",
                    )
                    continue
                }
                TrackLog.d(
                    TAG,
                    "${source.displayName} resolved conflicting albums for '${target.title}' " +
                        "using the uniquely fullest credit: '${canonical.artist}'",
                )
                matches = listOf(canonical)
            }
            // The extra bar for standing in for one specific recording: the
            // replacement has to be the same *length*, to the second or so. A
            // title and an artist can agree across two different edits of a
            // song; a runtime that agrees this closely is one recording, and
            // nothing else is worth cutting a listener's audio for — or filing
            // on their device under the name of the track they asked for.
            if (strictLength) {
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
            if (!wantsLossless || served.isLossless == true || served.isDolbyAtmos || served.statesNothingLossy) {
                TrackLog.d(
                    TAG,
                    "${source.displayName} matched '${match.title}' by '${match.artist}' " +
                        "id='${match.videoId}' album='${match.albumName ?: "?"}' " +
                        "duration=${match.durationText ?: "?"} explicit=${match.isExplicit ?: "?"} " +
                        "→ ${served.summary}",
                )
                return stream
            }
            TrackLog.d(
                TAG,
                "${source.displayName} offered ${served.summary} for '${match.title}' " +
                    "by '${match.artist}' id='${match.videoId}' album='${match.albumName ?: "?"}' " +
                    "duration=${match.durationText ?: "?"} explicit=${match.isExplicit ?: "?"}; looking further",
            )
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

    /**
     * Whether [candidate] is a better rendition than [current], by codec first
     * and bitrate second. A null [current] is beaten by anything.
     *
     * The one rule for ranking two copies of the same recording, kept in one
     * place because three different walks now need it: [streamBest] choosing
     * between rows inside a source, [bestAcross] choosing between sources, and
     * [upgradeFor] choosing what to cut into a track that is already playing.
     *
     * Note that this is *not* [worthSwapping]. This asks which of two streams is
     * better; that one asks whether the difference is worth a break in the
     * audio, which is a much higher bar and only meaningful mid-playback.
     */
    internal fun isBetter(candidate: StreamFormat, current: StreamFormat?): Boolean {
        if (current == null) return true
        if (candidate.isLossless != current.isLossless) return candidate.isLossless == true
        if (candidate.isDolbyAtmos != current.isDolbyAtmos) return candidate.isDolbyAtmos
        return (candidate.kbps ?: 0) > (current.kbps ?: 0)
    }

    /** The higher-quality of two streams — see [isBetter]. */
    private fun betterOf(current: SourceStream?, candidate: SourceStream): SourceStream =
        if (current == null || isBetter(candidate.format, current.format)) candidate else current

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
     * The top of YouTube's own AAC ladder, and so the bar a lossy source has to
     * clear to be worth keeping as a file — see [forDownload].
     *
     * Not a measurement of any one track. Most of the catalogue offers itag 140
     * at 128kbps and a signed-in account reaches itag 141 at 256, and which of
     * those a given track has cannot be known without resolving it — a full
     * player walk, spent to answer a question about a stream we may then not
     * use. So the bar is the *best* YouTube could turn out to have: clearing it
     * means the source's copy wins whichever rung was waiting, and failing it
     * means YouTube might well be better and is certainly the more reliable
     * fetch. JioSaavn's 320 clears it; its 160 does not, and neither does a
     * module's 128kbps MP3 settle-for.
     */
    private const val YOUTUBE_BEST_AAC_KBPS = 256

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
