package com.music.bitchord.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.data.Http
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.innertube.PlaybackTracker
import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.ListenBrainzManager
import com.music.bitchord.data.scrobbling.ScrobbleManager
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlinx.coroutines.TimeoutCancellationException

/** Past this point in a track, back restarts it instead of skipping to the previous one. */
const val BACK_RESTARTS_AFTER_MS = 10_000L

/**
 * Background playback via Media3. A [MediaSessionService] gives us the media
 * notification, lockscreen/Bluetooth controls, and Android Auto surface for
 * free; UI processes attach with a MediaController.
 *
 * Queue items carry a `bitchord://watch?v=<videoId>` URI. The actual stream
 * URL is resolved lazily by [ResolvingDataSource] the moment ExoPlayer opens
 * the item — stream URLs expire after a few hours, so resolving at play time
 * (on Media3's loader thread, hence runBlocking is safe) keeps queues valid.
 *
 * A single ExoPlayer owns the queue and backs the session for the service's
 * whole life; [CrossfadeController] rides on top of it as volume automation.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var crossfade: CrossfadeController? = null
    private val spatialAudioProcessor = SpatialAudioProcessor()

    /**
     * Smart Fade's filter ride and bass swap, one per audio sink. The session
     * player's carries the track arriving; the ghost's carries the track
     * leaving. Both sit parked open outside a transition and cost a buffer copy.
     */
    private val transitionFilter = TransitionFilterProcessor()
    private val ghostTransitionFilter = TransitionFilterProcessor()

    /** Smart Fade's DSP analyzer — see [com.music.bitchord.playback.smart.TrackAnalyzer]. */
    private val trackAnalyzer = com.music.bitchord.playback.smart.TrackAnalyzer(this, AudioCache)

    /** Shared with the crossfade's tail player, so both read the same disk cache. */
    private var mediaSourceFactory: DefaultMediaSourceFactory? = null

    /** Last sampled position of the playing track, in seconds. */
    private var lastPositionSeconds = 0L

    /** When the current track was chosen, for the time-to-first-audio log. */
    private var trackSelectedAt: Long? = null

    private var scrobbleManager: ScrobbleManager? = null
    private var listenBrainzSong: Song? = null

    private var listenBrainzStartMs: Long = 0L

    private var listenBrainzDurationMs: Long? = null
    /**
     * The crossfade's tail player runs its own audio sink, so it needs its own
     * instance of the effect — [SpatialAudioProcessor] carries a delay line and
     * filter state that two sinks cannot share.
     */
    private val ghostSpatialAudioProcessor = SpatialAudioProcessor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_logo) },
        )

        // No user agent on the factory: the right one depends on which client
        // minted the URL, so it is set per request below. Setting it here as
        // well would not override that — OkHttpDataSource *appends* the
        // factory's agent after the request's, and the fetch would go out
        // carrying two contradictory User-Agent headers.
        val resolvingFactory = ResolvingDataSource.Factory(
            // Innermost, so it chunks the real googlevideo URL the resolver
            // below has already substituted in — see [ChunkedDataSource] for
            // why an open-ended read of one is worth avoiding.
            ChunkedDataSource.Factory(OkHttpDataSource.Factory(Http.client), STREAM_CHUNK_BYTES),
        ) { dataSpec ->
            // A source-backed track is resolved by whichever source can serve
            // it, which is not necessarily the one it was queued from — see
            // [SourceResolver.resolve]. Handled ahead of the YouTube path
            // because these carry no `v` parameter and would otherwise fall
            // straight through unresolved.
            if (dataSpec.uri.authority == "source") {
                val stream = runBlocking {
                    withTimeout(RESOLVE_TIMEOUT_MS) { SourceResolver.resolve(dataSpec.uri) }
                } ?: throw java.io.IOException("No enabled source could serve ${dataSpec.uri.getQueryParameter("n")}")
                NerdStats.onSourceStream(dataSpec.uri.getQueryParameter("t"), stream.format)
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(stream.url))
                    .setHttpRequestHeaders(stream.headers)
                    .build()
            }
            val videoId = dataSpec.uri.getQueryParameter("v")
                ?: return@Factory dataSpec
            // An upgraded item carries a marker and its stream has already
            // been found — see [QualityUpgrade]. Answered before anything
            // else, and without re-resolving: this exact URL is what the
            // player was told it was getting when it agreed to the swap.
            QualityUpgrade.forcedStream(dataSpec.uri)?.let { upgraded ->
                // An audition opens this same stream before a note of the one
                // playing has been touched — see [auditionUpgrade] — so what it
                // is about to be handed describes a swap that has not happened
                // and may never. Recording it here would light "Lossless" over
                // the lossy stream still coming out of the speaker. The real
                // open, moments later, records it.
                val proving = QualityUpgrade.isAuditioning(videoId)
                if (!proving) NerdStats.onSourceStream(videoId, upgraded.format)
                // Logged because the alternative — a swap that silently never
                // reached its stream — is indistinguishable in the logs from
                // one that reached it and got nothing back, and the two have
                // opposite fixes.
                TrackLog.d(
                    "BitChord",
                    "${if (proving) "auditioning" else "serving"} upgraded $videoId " +
                        "from ${Uri.parse(upgraded.url).host} " +
                        "at ${dataSpec.position} (${upgraded.format.summary})",
                )
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(upgraded.url))
                    .setHttpRequestHeaders(upgraded.headers)
                    .build()
            }
            val downloadedUri = runBlocking { com.music.bitchord.download.Downloads.savedUri(this@PlaybackService, videoId) }
            if (downloadedUri != null) {
                return@Factory dataSpec.buildUpon().setUri(downloadedUri).build()
            }
            // Whoever is already filling this track's cache entry keeps it.
            // Everything below decides between servers holding *different
            // files*, and this method is called again for every re-open of a
            // track — including the continuation fetch when playback runs off
            // the end of the cached bytes. Deciding afresh each time is how
            // the middle of an MP4 ended up appended to a WebM. See
            // [StreamChoice].
            StreamChoice.of(videoId)?.let { serving ->
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(serving.url))
                    .setHttpRequestHeaders(serving.headers)
                    .build()
            }
            // A track queued from YouTube may be held by a source the user
            // ranked above it — see [SourceResolver.substituteForYouTube] and
            // [raceYouTubeOrModule]. Only worth the extra lookup when
            // something actually outranks YouTube; otherwise this is the
            // plain resolve every build before this one made.
            if (!SourceResolver.canSubstituteForYouTube()) {
                val streamUrl = try {
                    runBlocking {
                        withTimeout(RESOLVE_TIMEOUT_MS) { StreamResolver.resolve(videoId) }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw java.io.IOException("Stream resolution timed out for $videoId", e)
                }
                // googlevideo names the client that minted the URL inside the
                // URL itself, and compares it against the request that comes
                // back for the bytes. A mismatch is answered with a throttled
                // trickle or a 403 rather than an error worth the name, so the
                // fetch is dressed as whatever the URL says it should be.
                val headers = PlayerClient.forStreamUrl(streamUrl).mediaHeaders()
                // Recorded even though only one server can answer here: a
                // source enabled from Settings mid-track flips the branch
                // above under a half-filled cache entry, and the entry would
                // then be finished by a different file.
                StreamChoice.remember(videoId, SourceStream(streamUrl, headers = headers))
                return@Factory dataSpec.buildUpon()
                    .setUri(Uri.parse(streamUrl))
                    .setHttpRequestHeaders(headers)
                    .build()
            }
            val won = runBlocking {
                resolveWithModulePriority(
                    videoId = videoId,
                    target = SourceResolver.targetIn(dataSpec.uri),
                )
            }
            when (won) {
                is Resolved.Module -> {
                    NerdStats.onSourceStream(videoId, won.stream.format)
                    StreamChoice.remember(videoId, won.stream)
                    dataSpec.buildUpon()
                        .setUri(Uri.parse(won.stream.url))
                        .setHttpRequestHeaders(won.stream.headers)
                        .build()
                }
                // A module could have served this and didn't — it missed, its
                // server was slow, or the lookup ran out of budget. The last
                // of those is worth chasing rather than accepting: measured
                // here, a module's stream URL arrived 66ms after the live path
                // gave up on it, and the difference between a FLAC and a
                // YouTube Opus stream came down to that. The second look has
                // no such deadline, so what was nearly in hand is asked for
                // again while the fallback plays.
                is Resolved.YouTube -> {
                    val headers = PlayerClient.forStreamUrl(won.url).mediaHeaders()
                    StreamChoice.remember(videoId, SourceStream(won.url, headers = headers))
                    dataSpec.buildUpon()
                        .setUri(Uri.parse(won.url))
                        .setHttpRequestHeaders(headers)
                        .build()
                }
            }
        }
        // Read-ahead resolves streams through the same chain the player does.
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, resolvingFactory)
        AudioCache.setUpstream(defaultDataSourceFactory)
        mediaSourceFactory = DefaultMediaSourceFactory(AudioCache.playbackFactory(defaultDataSourceFactory))

        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(silenceSkippingRenderers(spatialAudioProcessor, transitionFilter))
            .setMediaSourceFactory(requireNotNull(mediaSourceFactory))
            .setLoadControl(farBufferingLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Back restarts the track once you're this far into it; only a
            // press before that steps to the previous one.
            .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)
            .build()
        player = exoPlayer

        AppSettings.audioSessionId.value = exoPlayer.audioSessionId
        applySettings(exoPlayer)
        observeSettings()
        observeScrobbling()
        watchSleepTimer()
        // Before the listener below is attached, so loading the queue doesn't
        // read as a track change and set the read-ahead going.
        restoreLastQueue(exoPlayer)

        // History pings fire once a track is actually audible — both when
        // playback starts and when the queue moves on while already playing.
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // The only number that describes what a listener actually
                // waits through. Every other timing in this app measures one
                // leg of getting a track started — a resolve, a client walk, an
                // extraction — and a leg being fast has repeatedly turned out
                // to say nothing about whether sound arrived quickly, because
                // the legs that were measured were the ones running in the
                // background for tracks nobody was waiting on.
                if (isPlaying) {
                    trackSelectedAt?.let {
                        TrackLog.d(
                            "BitChord",
                            "TIMING first audio: ${SystemClock.elapsedRealtime() - it}ms since track selected",
                        )
                        trackSelectedAt = null
                    }
                }
                if (isPlaying) registerCurrentPlay()
                // Nothing to read ahead for while paused, and a pause is often
                // the last thing that happens before the process goes idle.
                if (isPlaying) prefetchAround(exoPlayer) else AudioCache.cancel()
                if (isPlaying) lookForBetterCopy(exoPlayer)
                saveQueue()

                val song = exoPlayer.currentMediaItem?.toSong()
                val durationMs = exoPlayer.duration.takeIf { it > 0 }
                scrobbleManager?.onPlayerStateChanged(isPlaying, song, durationMs)

                // ListenBrainz: "now playing" on play/resume too, not just on
                // transition — a track started from idle or resumed from pause
                // otherwise stays silent on the site.
                if (isPlaying && song != null) {
                    if (listenBrainzSong == null) {
                        listenBrainzSong = song
                        listenBrainzStartMs = System.currentTimeMillis()
                        listenBrainzDurationMs = durationMs
                    }
                    submitListenBrainzPlayingNow(song, exoPlayer.currentPosition, durationMs)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // A quality swap replaces the playing item, which Media3
                // reports here as a playlist change — indistinguishable, from
                // this callback's point of view, from the queue moving on. It
                // is not the queue moving on: it is the same song, at the same
                // position, from a better source. Letting the bookkeeping below
                // run for it scrobbled the track twice, wrote a second history
                // entry, resubmitted it to ListenBrainz and closed out its
                // play count mid-play — all of which happened, and all of which
                // are invisible until someone reads their listening history.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                    mediaItem?.mediaId != null &&
                    mediaItem.mediaId == swappingMediaId
                ) {
                    swappingMediaId = null
                    return
                }

                // A new track is a clean slate for [recoverFrom]. The count
                // exists to stop one broken stream looping, not to hold a
                // grudge against a track for the rest of the session.
                recoveries.clear()

                // Where the wait starts, for the log in onIsPlayingChanged.
                trackSelectedAt = SystemClock.elapsedRealtime()
                // And the same instant on the wall clock, which is the one
                // logcat stamps its lines with — see [TrackLog].
                mediaItem?.mediaId?.let(TrackLog::onTrackStarted)
                TrackLog.d("BitChord", "TIMING track selected: ${mediaItem?.mediaId} (reason=$reason)")

                // currentPosition already belongs to the new item by now, so
                // the outgoing track is closed out on the last sampled value.
                PlaybackTracker.onTrackChanged(lastPositionSeconds)
                lastPositionSeconds = 0

                // Scrobbling: stop old song, start new song
                scrobbleManager?.onSongStop()
                val newSong = mediaItem?.toSong()
                val durationMs = exoPlayer.duration.takeIf { it > 0 }
                scrobbleManager?.onSongStart(newSong, durationMs)

                // ListenBrainz: submit finished for old song, playing_now for new song.
                // The finished listen only counts when the track actually ended —
                // an auto-advance, a repeat, or a crossfade at the very end. A
                // manual skip (SEEK) means the song wasn't listened to, so it must
                // not be scrobbled.
                val crossfaded = crossfade?.consumeAutoAdvance() == true
                val ended = crossfaded ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                val prevSong = listenBrainzSong
                val prevStart = listenBrainzStartMs
                if (prevSong != null && ended) {
                    submitListenBrainzFinished(prevSong, prevStart, listenBrainzDurationMs)
                }
                listenBrainzSong = newSong
                listenBrainzStartMs = System.currentTimeMillis()
                listenBrainzDurationMs = durationMs
                if (newSong != null) {
                    submitListenBrainzPlayingNow(newSong, 0L, durationMs)
                }

                // "Sleep after this song": the queue moving on by itself is the
                // moment the track the user meant has finished. REPEAT counts
                // too, or the timer would never fire with repeat-one on.
                if (ended && SleepTimer.afterTrack.value) {
                    exoPlayer.pause()
                    SleepTimer.cancel()
                }
                if (exoPlayer.isPlaying) registerCurrentPlay()
                prefetchAround(exoPlayer)
                // The second look belongs to the track it was started for; the
                // queue moving on ends it, whatever it had found — and starts
                // the new track's own, which nothing else here would. The
                // track arriving has usually been resolved already, by
                // ExoPlayer preparing the next item while this one played, so
                // it is pending by now; the ones that aren't are picked up by
                // the sampler in [reportProgress].
                upgradeJob?.cancel()
                lookForBetterCopy(exoPlayer)
                saveQueue()
                // Cleared rather than re-published. The renderer is still
                // configured for the track that just ended at this point, so
                // reading the format here reports the *previous* song — which
                // is how a lossy track spent its whole resolve showing the
                // "Hi-Res Lossless" badge the track before it had earned.
                // Nothing measured is better than something wrong, and the
                // gap is exactly when "Loading lossless" should be showing
                // instead. The periodic sampler below and
                // onAudioInputFormatChanged both re-publish once the decoder
                // has actually settled on this track, so the same-format case
                // the old call was here to cover is still covered.
                NerdStats.current.value = null
            }

            /**
             * A failed stream is not a failed track: nothing else in this
             * service ever calls [Player.prepare] again, so before this
             * existed a single read error left the player in `STATE_IDLE` for
             * good. The notification kept the song on it, the play button kept
             * being pressed, and nothing happened — which is exactly what a
             * broken app looks like from the outside.
             */
            override fun onPlayerError(error: PlaybackException) {
                recoverFrom(error, exoPlayer)
            }

            // Nothing follows the last track, so there is no transition to
            // pause on — the queue simply runs out and the timer is spent.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    SleepTimer.cancel()
                    // The last track finished with nothing after it, so no
                    // transition will ever close it out. Scrobble it now.
                    val lastSong = listenBrainzSong
                    if (lastSong != null) {
                        val lastStart = listenBrainzStartMs
                        val lastDuration = listenBrainzDurationMs
                            ?: exoPlayer.duration.takeIf { it > 0 }
                        submitListenBrainzFinished(lastSong, lastStart, lastDuration)
                        listenBrainzSong = null
                    }
                }
            }

            /**
             * AutoPlay appends to the queue after the transition that ran it
             * dry, so the track to read ahead for often only exists once the
             * timeline has changed.
             */
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (exoPlayer.isPlaying) prefetchAround(exoPlayer)
            }
        })

        // Only the analytics listener reports the format the audio renderer was
        // configured with. Treated as a trigger rather than a source: the
        // publisher reads the format off the player, so it can't go stale
        // against the track the bitrate is looked up for.
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                // Taken off the event's own window rather than off the player,
                // so it names the track this format arrived for even if the
                // queue has moved on again since. See [audioFormatFor].
                audioFormatFor = eventTime.timeline
                    .takeIf { eventTime.windowIndex < it.windowCount }
                    ?.getWindow(eventTime.windowIndex, Timeline.Window())
                    ?.mediaItem
                    ?.mediaId
                // Ground truth for a real-device listening test: this is the
                // renderer's own Format, straight off the decoder with none of
                // the app's caching/upgrade logic in between, so it's the one
                // line that can prove a "hi-res" session never quietly slid
                // onto a lower-rate stream mid-track. `adb logcat -s DECODE:I`.
                val khz = format.sampleRate.takeIf { it != Format.NO_VALUE }
                    ?.let { "%.1fkHz".format(it / 1000.0) } ?: "?kHz"
                val kbps = format.bitrate.takeIf { it != Format.NO_VALUE }
                    ?.let { "${it / 1000}kbps" } ?: "bitrate n/a"
                val depth = bitDepthOf(format.pcmEncoding)?.let { "${it}-bit" } ?: "?-bit"
                TrackLog.i(
                    "DECODE",
                    "$audioFormatFor <- ${format.sampleMimeType} $khz $kbps $depth ${format.channelCount}ch",
                )
                publishNerdStats()
            }

            /**
             * The seam, measured rather than described. This fires when the
             * audio track starts putting samples out again after the sink was
             * flushed, which for a quality swap is the exact instant the music
             * comes back — and the gap between it and the swap is the only
             * number that says whether any of the work above paid off. Every
             * other timing here brackets a fetch, and a fetch being fast has
             * repeatedly said nothing about whether the listener heard a hole.
             */
            override fun onAudioPositionAdvancing(
                eventTime: AnalyticsListener.EventTime,
                playoutStartSystemTimeMs: Long,
            ) {
                val cutAt = swapCutAt ?: return
                swapCutAt = null
                TrackLog.d("BitChord", "swap seam: ${SystemClock.elapsedRealtime() - cutAt}ms of silence")
            }

            /**
             * The three legs the seam breaks into, logged separately because
             * they have entirely different fixes: getting the new source
             * loaded and past the load control's gate, standing a decoder up,
             * and opening an audio track. Only the first is ours to shorten.
             */
            override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
                val cutAt = swapCutAt ?: return
                if (state == Player.STATE_READY) {
                    TrackLog.d("BitChord", "swap leg: ready ${SystemClock.elapsedRealtime() - cutAt}ms after the cut")
                }
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                val cutAt = swapCutAt ?: return
                TrackLog.d(
                    "BitChord",
                    "swap leg: $decoderName stood up in ${initializationDurationMs}ms, " +
                        "${SystemClock.elapsedRealtime() - cutAt}ms after the cut",
                )
            }
        })

        reportProgress(exoPlayer)

        val controller = CrossfadeController(
            scope,
            exoPlayer,
            ::buildGhostPlayer,
            analysisFor = { item -> trackAnalyzer.analysisFor(item.mediaId) },
            requestAnalysis = { item ->
                item.localConfiguration?.uri?.let { uri -> trackAnalyzer.request(item.mediaId, uri, 0.0) }
            },
            // "Incoming" and "outgoing" are roles, not players, and they only
            // line up with these two once the lap has handed the queue over —
            // which is the only point at which the controller filters anything.
            filters = object : TransitionFilters {
                override fun incoming(lowPassHz: Float, highPassHz: Float) =
                    transitionFilter.setCutoffs(lowPassHz, highPassHz)

                override fun outgoing(lowPassHz: Float, highPassHz: Float) =
                    ghostTransitionFilter.setCutoffs(lowPassHz, highPassHz)
            },
        )
        crossfade = controller
        controller.start()

        mediaSession = MediaSession.Builder(this, SessionPlayer(exoPlayer, controller))
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity())
            .build()
    }

    /**
     * The crossfade's tail player: plays out the last seconds of the track
     * being left behind while the real player gets on with the next one.
     *
     * Deliberately not a second copy of the main player:
     *
     *  - **No audio focus.** Focus belongs to the session player, and two
     *    requests from one app mean the second replaces the first — the ghost
     *    abandoning focus as it finishes would take the whole app's focus with
     *    it.
     *  - **No "becoming noisy" handling, no wake mode, no session.** Unplugging
     *    headphones pauses the session player, and the ghost dies with the fade
     *    that owns it; a second component reacting to the same events would
     *    only ever fight the first.
     *  - **Same audio session id**, so the system equalizer and any other
     *    effects attached to the app apply to the tail as well as to the track
     *    fading up. Without it a crossfade would audibly change EQ halfway.
     *
     * It shares the media source factory, so the tail is served from the same
     * on-disk cache the track was just playing from rather than re-resolving a
     * stream URL for audio that is already local.
     */
    private fun buildGhostPlayer(): ExoPlayer = ExoPlayer.Builder(this)
        .setRenderersFactory(silenceSkippingRenderers(ghostSpatialAudioProcessor, ghostTransitionFilter))
        .setMediaSourceFactory(requireNotNull(mediaSourceFactory))
        .setLoadControl(farBufferingLoadControl())
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ false,
        )
        .build()
        .also { ghost ->
            player?.let { ghost.audioSessionId = it.audioSessionId }
            ghost.skipSilenceEnabled = AppSettings.skipSilence.value
            ghost.setPlaybackSpeed(AppSettings.playbackSpeed.value)
            ghostSpatialAudioProcessor.enabled = DolbyAtmos.spatialAudioActive
        }

    /**
     * Where a tap on the session lands. Media3 uses this both as the media
     * notification's contentIntent and as the session activity handed to the
     * platform MediaSession.
     *
     * This is not cosmetic on One UI: Samsung's Now Bar / Live Notification
     * chip is a launcher for the session, so a session that advertises nowhere
     * to go is skipped and only the plain shade notification survives. Same
     * reason the notification itself was previously un-tappable.
     */
    private fun sessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            // MainActivity is singleTask, so this resumes the existing task
            // rather than stacking a second copy of the UI.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun registerCurrentPlay() {
        player?.currentMediaItem?.mediaId?.let(PlaybackTracker::onPlaying)
    }

    /**
     * Loads the queue from the last session so the app opens on the track it
     * was left on, rather than with nothing in the mini player.
     *
     * Deliberately no `prepare()`. Preparing would resolve the stream — a
     * NewPipe extraction over the network — on every cold start, for a track
     * that may never be played, and would post a media notification for a
     * session nobody has touched yet (Media3 shows one as soon as the player
     * leaves IDLE with a non-empty queue). Left idle, restoring costs nothing:
     * [MediaSession] routes every play request through
     * `Util.handlePlayButtonAction`, which prepares an idle player first, so
     * the mini player, the notification and Bluetooth all resume from here
     * without knowing the queue was cold.
     */
    private fun restoreLastQueue(player: ExoPlayer) {
        val last = LastPlayed.load() ?: return
        player.setMediaItems(
            last.songs.map { it.toMediaItem() },
            last.index,
            last.positionMs,
        )
    }

    /** The background hunt for a better copy of whatever is playing. */
    private var upgradeJob: Job? = null

    /** Which track [upgradeJob] is hunting for — see [lookForBetterCopy]. */
    private var upgradeFor: String? = null

    /**
     * How many times each track has been picked up off the floor, so a stream
     * that fails the same way every time stops rather than loops. Reset when
     * the queue genuinely moves on, not when a track merely re-prepares.
     */
    private val recoveries = mutableMapOf<String, Int>()

    /**
     * Puts a track that died mid-read back on its feet.
     *
     * Two things get thrown away before trying again, because both have been
     * seen to be the actual fault and neither is visible from the exception:
     *
     *  - The cached bytes. An entry filled from two different files reads
     *    fine until playback reaches the seam and then throws forever, and no
     *    number of retries against the same entry will do anything else.
     *  - The choice of who serves the track. If the source that was picked is
     *    the one handing over something unreadable, resolving again from
     *    scratch is the only way to land anywhere else.
     *
     * The position is kept: this should look like a hiccup, not like the song
     * starting over.
     */
    private fun recoverFrom(error: PlaybackException, player: ExoPlayer) {
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        val uri = item.localConfiguration?.uri
        val position = player.currentPosition.coerceAtLeast(0L)
        val attempts = recoveries.getOrDefault(mediaId, 0) + 1
        recoveries[mediaId] = attempts
        TrackLog.w(
            "BitChord",
            "playback failed for $mediaId at ${position}ms (${error.errorCodeName}), attempt $attempts",
            error,
        )
        if (attempts > MAX_RECOVERIES) {
            TrackLog.w("BitChord", "$mediaId has failed $attempts times; leaving it alone")
            return
        }
        // The upgraded rendition goes with the cache entry it lived in, so the
        // marker on the URI would otherwise point at nothing.
        QualityUpgrade.forget(mediaId)
        // A track that died on an upgraded URI died on the *upgrade*, and it
        // must not be offered that same swap again the moment it recovers.
        // Left unrecorded, the second look starts over on the retry, finds the
        // same FLAC at the same dead URL, cuts the audio for it again, and
        // fails again — twice more before [MAX_RECOVERIES] stops it. Observed
        // on a Tidal URL answering ERROR_CODE_IO_BAD_HTTP_STATUS.
        if (uri?.let(QualityUpgrade::cacheTag) != null) {
            QualityUpgrade.refuseUpgrades(mediaId)
        }
        // Whatever failed took its claimed format with it. The stream that
        // recovers is a different one and has not promised anything yet, so
        // leaving the old claim behind is how a badge earned by a FLAC ends up
        // sitting over the Opus that replaced it.
        NerdStats.clearDeclared(mediaId)
        uri?.getQueryParameter("v")?.let(StreamChoice::forget)
        scope.launch {
            // Long enough for the released source to let go of the cache keys
            // about to be removed, short enough to read as a stutter.
            delay(RECOVERY_DELAY_MS)
            uri?.let { withContext(Dispatchers.IO) { AudioCache.discard(it) } }
            withContext(Dispatchers.Main) {
                val player = this@PlaybackService.player ?: return@withContext
                if (player.currentMediaItem?.mediaId != mediaId) return@withContext
                TrackLog.d("BitChord", "retrying $mediaId from ${position}ms")
                player.seekTo(player.currentMediaItemIndex, position)
                player.prepare()
            }
        }
    }

    /**
     * The track whose item this service is about to replace under it, so that
     * [Player.Listener.onMediaItemTransition] can tell a quality swap from the
     * queue actually moving on. Cleared by the transition it describes.
     */
    private var swappingMediaId: String? = null

    /**
     * When the audio was last cut for a quality swap, so the analytics listener
     * can say how long it stayed cut. Null except across a swap.
     */
    private var swapCutAt: Long? = null

    /**
     * The track [ExoPlayer.getAudioFormat] is currently describing.
     *
     * `audioFormat` is a property of the *renderer*, not of the queue item, and
     * it keeps naming the outgoing track's codec until the renderer has read a
     * sample of the incoming one. Anything that asks "what is playing right
     * now" in the moments after a transition is therefore told about the track
     * before it, and [adoptCachedTrack] is asked exactly there — a queue
     * advance is one of the places [lookForBetterCopy] runs from.
     *
     * Observed: 'Harleys In Hawaii' came up fifteen milliseconds after the
     * queue moved onto it, twenty seconds after the previous track had been
     * upgraded to FLAC. The renderer still said `audio/flac`, so a WebM Opus
     * stream — verified by the `1A 45 DF A3` on its cache entry — was written
     * off as "already lossless from cache" and, because that verdict is
     * recorded once and for good, never offered an upgrade again for the rest
     * of the session.
     */
    private var audioFormatFor: String? = null

    /**
     * Starts the second look for the playing track, if it settled for less
     * than was asked for — see [QualityUpgrade].
     *
     * Runs at most once per track: [QualityUpgrade.lookAgain] drops the track
     * from its pending set whatever the answer, so the repeated calls this
     * gets cost nothing after the first. It needs to be called from several
     * places for that reason — a track becomes eligible at a different moment
     * depending on how it was reached. Called only from
     * `onIsPlayingChanged`, it fired for the first track of a session and for
     * nothing after it: the queue advancing while already playing is not a
     * change in `isPlaying`, so every track but the first kept a lookup that
     * had already found its FLAC and was never asked for it.
     *
     * Eligibility has two sources, because being resolved and being played are
     * not the same event. A track the resolver saw is already marked; a track
     * served from the disk cache was never resolved at all and is judged here
     * instead — see [adoptCachedTrack] and [QualityUpgrade.adoptUnresolved].
     */
    private fun lookForBetterCopy(player: ExoPlayer) {
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        val uri = item.localConfiguration?.uri
        val alreadyPending = QualityUpgrade.isPending(mediaId)
        val shelved = QualityUpgrade.shelvedFor(mediaId)
        if (shelved == null && !alreadyPending && !QualityUpgrade.couldStillUpgrade(mediaId, uri)) return
        if (upgradeJob?.isActive == true) {
            // Already hunting for this track. One left over from a track the
            // queue has moved past is a different matter: it can only come
            // back with an answer about a song nobody is listening to, and
            // until it does it holds the slot the current track needs.
            if (upgradeFor == mediaId) return
            upgradeJob?.cancel()
        }
        upgradeFor = mediaId
        if (alreadyPending) TrackLog.d("BitChord", "looking again for a better copy of $mediaId")
        upgradeJob = scope.launch {
            // A previous visit to this track already did all the expensive
            // parts and lost the swap to a skip. Nothing about the answer has
            // gone stale — the stream is still parked and its bytes are still
            // on disk — so this goes straight to the swap and skips the ten
            // seconds of catalogue searching it would otherwise repeat.
            if (shelved != null) {
                TrackLog.d("BitChord", "re-offering the upgrade already proved for $mediaId")
                NerdStats.onLosslessRaceStart(mediaId)
                try {
                    swapIn(mediaId, shelved)
                } finally {
                    NerdStats.onLosslessRaceEnd(mediaId)
                }
                return@launch
            }
            // The runtime the decoder reports is the only measured evidence
            // about what is playing, and everything downstream weighs
            // candidates against it — so it is worth a short wait rather than
            // a null. It is genuinely not known yet at some of the moments
            // this is called from: a queue advance runs its transition before
            // the item it moved onto has finished preparing.
            val playingSeconds = withTimeoutOrNull(DURATION_SETTLE_MS) {
                while (true) {
                    val ms = withContext(Dispatchers.Main) {
                        this@PlaybackService.player
                            ?.takeIf { it.currentMediaItem?.mediaId == mediaId }
                            ?.duration
                            ?: 0L
                    }
                    if (ms > 0) return@withTimeoutOrNull (ms / 1000).toInt()
                    delay(UPGRADE_PROVE_STEP_MS)
                }
                @Suppress("UNREACHABLE_CODE") null
            }
            // Re-asked rather than carried down from above, because the wait
            // is long enough for the answer to have changed: the resolver runs
            // on the loader thread and marks a track pending as it opens the
            // source, which for a track being fetched is precisely what has to
            // happen before the decoder can report the runtime waited for just
            // above. Reading the flag from before the wait meant a freshly
            // resolved track arrived here looking un-resolved, was handed to
            // the cached-track path, was refused by it for being pending, and
            // lost its upgrade until the next progress sample came round.
            if (!QualityUpgrade.isPending(mediaId) &&
                (uri == null || !adoptCachedTrack(mediaId, uri))
            ) {
                return@launch
            }
            val better = withContext(Dispatchers.IO) {
                QualityUpgrade.lookAgain(mediaId, playingSeconds)
            } ?: return@launch
            try {
                swapIn(mediaId, better)
            } finally {
                // The badge comes down when the upgrade is done, not when the
                // search that found it was — including the deliberate wait in
                // [swapIn] before the audio is allowed to be cut. See
                // [QualityUpgrade.lookAgain]. In `finally` because a queue
                // that moves on cancels this job, and a cancelled swap has to
                // put the badge out as surely as a completed one.
                NerdStats.onLosslessRaceEnd(mediaId)
            }
        }
    }

    /**
     * Decides whether a track nothing resolved is worth a second look, now that
     * the decoder has settled enough to say what it is playing.
     *
     * Two questions that need the player rather than the queue entry:
     *
     *  - **What codec is actually coming out.** A cache entry can already hold
     *    the FLAC a previous session upgraded to, and hunting a lossless copy
     *    of a track that is already lossless buys a break in the audio for
     *    nothing. An unknown codec is not read as "lossy": it means the
     *    renderer has not been configured yet, so the track is left un-adopted
     *    and the progress sampler asks again a few seconds later. A codec the
     *    renderer is reporting for *some other track* gets the same treatment,
     *    and has to, because it is indistinguishable from an answer — see
     *    [audioFormatFor] for what it cost to read one on trust.
     *  - **Whether the listener owns the file.** A downloaded track resolves to
     *    its own copy on disk — see the resolving data source above, which
     *    answers it before the module race is ever reached, so a download has
     *    never been a candidate for substitution. Reproduced here because this
     *    path skips that resolver entirely; without it the second look would
     *    spend data replacing a file the user deliberately saved.
     */
    private suspend fun adoptCachedTrack(mediaId: String, uri: Uri): Boolean {
        val mime = withContext(Dispatchers.Main) {
            player
                ?.takeIf { it.currentMediaItem?.mediaId == mediaId && audioFormatFor == mediaId }
                ?.audioFormat
                ?.sampleMimeType
        } ?: return false
        val videoId = uri.getQueryParameter("v") ?: return false
        val downloaded = com.music.bitchord.download.Downloads.savedUri(this, videoId) != null
        if (downloaded) return false
        return QualityUpgrade.adoptUnresolved(
            mediaId = mediaId,
            uri = uri,
            target = SourceResolver.targetIn(uri),
            playingMime = mime,
        )
    }

    /** Where the playing track stands, read off the player in one hop. */
    private class SwapPoint(
        val item: MediaItem,
        val uri: String,
        val position: Long,
        val duration: Long,
    )

    /**
     * Replaces the playing track's audio with [stream], keeping the position.
     *
     * The break this causes is the whole cost of the feature, so the guards
     * are worth more than the swap is:
     *
     *  - The track must still be the one the search was started for. A skip
     *    during the lookup makes the answer worthless, not merely late.
     *  - There has to be enough of it left to be worth interrupting. Cutting
     *    the last few seconds of a song to improve the last few seconds of a
     *    song is a straight loss.
     *  - **The replacement has to be ready before anything is taken away.**
     *    See [auditionUpgrade]; this is what the break costs, so it is what
     *    the cut is bought against.
     *
     * The mechanism is [MediaItem.buildUpon] with a marked URI rather than a
     * new item: Media3 only rebuilds a media source when the replacement's
     * playback URI differs, so an item rebuilt identically would be accepted
     * and quietly keep playing the old stream.
     */
    private suspend fun swapIn(mediaId: String, stream: SourceStream) {
        val at = withContext(Dispatchers.Main) { swapPointFor(mediaId) } ?: return
        if (at.duration > 0 && at.duration - at.position < UPGRADE_MIN_REMAINING_MS) {
            TrackLog.d("BitChord", "upgrade abandoned: only ${at.duration - at.position}ms of the track left")
            return
        }

        val upgradedUri = QualityUpgrade.upgradedUri(at.uri)
        // Whether the rendition entry already holds *this* stream's bytes,
        // asked before [force] overwrites the record of what filled it. True
        // only for a shelved upgrade being re-offered, where throwing the entry
        // away would mean paying for the same megabytes twice — and where
        // keeping it is safe for the one reason the discard exists: the file
        // under that key came from this very URL.
        val alreadyFilled = QualityUpgrade.forcedStream(Uri.parse(upgradedUri))?.url == stream.url
        // Parked before the audition rather than at the swap: the silent player
        // reaches its bytes through the same resolving data source the real one
        // does, and that is where a marked URI is turned back into a stream.
        QualityUpgrade.force(mediaId, stream)
        val warmedThrough = auditionUpgrade(mediaId, at, upgradedUri, stream, alreadyFilled)
        if (warmedThrough == null) {
            // Nothing was cut, so there is nothing to put back: the listener
            // keeps the stream they already had and never learns this
            // happened. Which is the point — this is the failure that used to
            // arrive as a break in the audio followed by the same lossy stream
            // returning a few seconds later. Dropping the parked stream stops
            // [QualityUpgrade.forcedStream] serving a URL that has just failed
            // to prove itself.
            QualityUpgrade.forget(mediaId)
            withContext(Dispatchers.IO) { AudioCache.discardRendition(Uri.parse(upgradedUri)) }
            return
        }

        // Never in the first few seconds. An upgrade that lands the instant a
        // track starts would otherwise cut it a millisecond in — the listener
        // hears the song begin, stop and begin again, which reads as a bug
        // whatever the bitrate afterwards. Letting the opening play through
        // costs nothing: the better copy is not going anywhere.
        //
        // Almost always already past by now: the audition above spends seconds
        // on the network, and it spends them with the music still playing.
        val settled = withContext(Dispatchers.Main) { player?.currentPosition ?: 0L }
        if (settled < UPGRADE_NOT_BEFORE_MS) {
            delay(UPGRADE_NOT_BEFORE_MS - settled)
        }

        // Never cut into a crossfade in flight. `replaceMediaItem` tears the
        // session player's source down and rebuilds it — CrossfadeController
        // is either syncing its tail player's position against that same
        // source (arming), riding a ~90ms handoff between the two (lapping),
        // or ramping volume off the incoming track's own position (fading),
        // and all three read a session-player discontinuity as either an
        // unrecognised seek (bail, with an audible ramp-out) or a progress
        // calculation reset to whatever position the new source opens at.
        // Either way the blend breaks rather than merely waits.
        //
        // Bounded so a stuck flag can never leave the upgrade waiting forever;
        // past the timeout this falls through to the same check made again,
        // authoritatively, below — so an unusually long-running crossfade
        // still gets one more look rather than being forced through.
        //
        // This loop is only the coarse wait. [crossfade] is read again inside
        // the `withContext` below with no suspension between that read and
        // `replaceMediaItem` — both run on the same single-threaded Main
        // dispatcher [scope] does — so that second check is the one this
        // logic actually depends on for correctness, not this one.
        var waitedForCrossfade = 0L
        while (withContext(Dispatchers.Main) { crossfade?.isTransitioning() } == true &&
            waitedForCrossfade < UPGRADE_CROSSFADE_WAIT_TIMEOUT_MS
        ) {
            delay(UPGRADE_CROSSFADE_POLL_MS)
            waitedForCrossfade += UPGRADE_CROSSFADE_POLL_MS
        }

        withContext(Dispatchers.Main) {
            val now = swapPointFor(mediaId)
            if (crossfade?.isTransitioning() == true) {
                // Caught right before the swap that would have broken it —
                // everything spent proving this stream is still worth keeping
                // for next time rather than throwing away, exactly like the
                // "queue moved on" case just below.
                QualityUpgrade.shelve(mediaId, stream)
                TrackLog.d("BitChord", "upgrade for $mediaId shelved: a crossfade was still running")
                return@withContext
            }
            if (now == null) {
                // The queue moved on between the upgrade being proved and the
                // swap being made — a skip, or a track that ran out. Everything
                // this cost is still in hand, so it goes on the shelf rather
                // than in the bin; see [QualityUpgrade.shelve]. Logged because
                // this used to be the one exit here that left no trace at all,
                // and from the logs "found a FLAC, cached it, swapped nothing"
                // was indistinguishable from never having looked.
                QualityUpgrade.shelve(mediaId, stream)
                TrackLog.d("BitChord", "upgrade for $mediaId proved but the queue moved on; shelved")
                return@withContext
            }
            val player = player ?: return@withContext
            if (now.duration > 0 && now.duration - now.position < UPGRADE_MIN_REMAINING_MS) {
                TrackLog.d("BitChord", "upgrade abandoned: only ${now.duration - now.position}ms of the track left")
                QualityUpgrade.forget(mediaId)
                return@withContext
            }
            // The parked stream can be taken away underneath a swap in flight:
            // a playback failure on the *old* stream runs [recoverFrom], which
            // forgets the pending upgrade along with everything else it clears.
            // Swapping onto a marked URI with nothing parked behind it would
            // send the resolver off to find a stream of its own and write it
            // into the rendition entry the audition just filled — two files,
            // one key, which is the corruption the audition exists to avoid.
            if (QualityUpgrade.forcedStream(Uri.parse(upgradedUri)) == null) {
                TrackLog.d("BitChord", "upgrade abandoned: its stream was dropped while it was being proved")
                return@withContext
            }
            // Not fatal, just slower than intended, and worth being able to see
            // in a log: the audition buffers ahead of a moving target and can
            // only lose that race on a connection that is barely keeping up.
            if (now.position > warmedThrough) {
                TrackLog.d(
                    "BitChord",
                    "upgrade landing at ${now.position}ms, past the ${warmedThrough}ms warmed for it",
                )
            }

            // Read before the swap overwrites it — see [watchUpgrade]'s
            // NerdStats cleanup for why the pre-upgrade claim has to be
            // captured here rather than looked up again on revert.
            val previousFormat = NerdStats.declaredFormat(mediaId)
            swappingMediaId = mediaId
            swapCutAt = SystemClock.elapsedRealtime()
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                now.item.buildUpon().setUri(upgradedUri).build(),
            )
            player.seekTo(player.currentMediaItemIndex, now.position)
            player.prepare()
            QualityUpgrade.unshelve(mediaId)
            TrackLog.d("BitChord", "upgraded to ${stream.format.summary} at ${now.position}ms")
            watchUpgrade(mediaId, now.uri, now.position, now.duration, previousFormat)
        }
    }

    /** Main thread. Null unless [mediaId] is still current and still un-upgraded. */
    private fun swapPointFor(mediaId: String): SwapPoint? {
        val player = player ?: return null
        val item = player.currentMediaItem ?: return null
        if (item.mediaId != mediaId) return null
        val uri = item.localConfiguration?.uri?.toString() ?: return null
        if (uri.contains("${QualityUpgrade.MARKER}=")) return null
        return SwapPoint(item, uri, player.currentPosition, player.duration)
    }

    /**
     * Proves the upgraded stream on a second, silent player before a note of
     * the one playing is touched.
     *
     * This is the difference between a swap that is heard and one that is not.
     * `replaceMediaItem` + `prepare` tears the old source down first and builds
     * the new one from nothing: a connection to the CDN, a container header, a
     * range request for wherever the seek lands, a decoder configured, and only
     * then audio. Measured on this device that ran to about a second of silence
     * every time, and the whole of it was spent doing work that had no reason to
     * wait for the music to stop.
     *
     * So it doesn't. A throwaway player opens the same upgraded URI, seeked to
     * where the listener is, and fills the *same on-disk cache entry* the real
     * player will read from — [QualityUpgrade.MARKER] keys that entry apart from
     * the rendition being replaced, which is what makes this safe. When the swap
     * finally happens the bytes are already local, the container is already
     * known to parse, and what is left is a decoder init. The old stream plays
     * through all of it.
     *
     * The second thing it buys is that a failed upgrade stops costing anything.
     * Every way this can go wrong — a dead URL, a 403, a truncated body, a
     * catalogue that matched the wrong cut of the song, a source that promised
     * FLAC and serves Opus — now happens to a player nobody is listening to, and
     * the answer is simply that no swap occurs. Before, all of them were
     * discovered *after* the audio had been cut, and cost a break, several
     * seconds of silence in `STATE_BUFFERING`, and a second break putting the
     * old stream back. See [watchUpgrade], which is now the backstop for this
     * rather than the first line of defence.
     *
     * Silent by construction rather than by volume: with `playWhenReady` false
     * the renderers are enabled and decode — which is all the proof needed —
     * but nothing is started and no second `AudioTrack` is ever opened. It takes
     * no audio focus and backs no session, so nothing else in the app can see it.
     *
     * @return how far into the track the upgrade is buffered and ready, or null
     *   if it never got there.
     */
    private suspend fun auditionUpgrade(
        mediaId: String,
        at: SwapPoint,
        upgradedUri: String,
        stream: SourceStream,
        renditionAlreadyFilled: Boolean,
    ): Long? {
        QualityUpgrade.beginAudition(mediaId)
        val startedAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            // A clean entry first, because `#hifi` names a *slot* and not a
            // file. Every audition is a fresh candidate — a different catalogue,
            // a different master, a different length — and anything left under
            // that key by an earlier attempt at the same track belongs to a
            // different one of those. Media3 will happily read the two as one
            // stream, which is how a whole contiguous 32MB entry ended up
            // decoding to this:
            //
            // ```
            //   Target buffer size reached with less than 500ms of buffered media
            //   IllegalStateException: Playback stuck buffering and not loading
            // ```
            //
            // — a spliced file that cost the swap, the recovery, and seven
            // seconds of silence. The cost of being wrong the other way is one
            // re-download of a track being upgraded twice in a session, which
            // is why a re-offered upgrade is exempt: there the bytes under the
            // key are known to have come from the URL about to be used again.
            if (!renditionAlreadyFilled) AudioCache.discardRendition(Uri.parse(upgradedUri))
            // Then the opening, on its own, because the audition will not cache
            // it: a progressive source parses the container from byte zero and
            // then *seeks away*, leaving behind only the handful of bytes it
            // read before jumping. The real player has to parse the same header
            // from scratch after the swap, and it was reaching the network to do
            // it — the one read nothing can start without. Ahead of the audition
            // rather than beside it, since Media3 locks a cache entry to a
            // single writer.
            AudioCache.warmRange(Uri.parse(upgradedUri), 0, UPGRADE_HEADER_BYTES)
        }
        val audition = withContext(Dispatchers.Main) {
            buildAuditionPlayer().apply {
                setMediaItem(at.item.buildUpon().setUri(upgradedUri).build())
                seekTo(at.position)
                prepare()
            }
        }
        val warmedThrough: Long?
        try {
            warmedThrough = withTimeoutOrNull(UPGRADE_AUDITION_MS) {
                while (true) {
                    val verdict = withContext(Dispatchers.Main) {
                        auditionVerdict(audition, at.duration, stream)
                    }
                    when (verdict) {
                        is Audition.Ready -> return@withTimeoutOrNull verdict.bufferedTo
                        is Audition.Rejected -> {
                            TrackLog.w("BitChord", "upgrade dropped before it was heard: ${verdict.why}")
                            return@withTimeoutOrNull null
                        }
                        Audition.Waiting -> delay(UPGRADE_PROVE_STEP_MS)
                    }
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        } finally {
            // Not optional and not cancellable: a queue that moves on cancels
            // this job, and a player left behind holds an audio decoder and a
            // write lock on a cache entry for the rest of the session.
            withContext(NonCancellable + Dispatchers.Main) { audition.release() }
            QualityUpgrade.endAudition(mediaId)
        }
        val took = SystemClock.elapsedRealtime() - startedAt
        if (warmedThrough == null) {
            TrackLog.d("BitChord", "upgrade for $mediaId never proved itself in ${took}ms")
            return null
        }
        TrackLog.d(
            "BitChord",
            "upgrade to ${stream.format.summary} proved in ${took}ms, buffered through ${warmedThrough}ms",
        )
        // Media3 locks a cache entry to one writer, and the audition lets go of
        // its hold as the sources are released rather than as `release()`
        // returns. Swapping onto a key still held would have the real player
        // stream bytes it has already paid to cache, or block behind the lock —
        // the stall [AudioCache]'s key factory documents. Free to wait for: the
        // old stream is still playing.
        delay(AUDITION_RELEASE_MS)
        TrackLog.d("BitChord", AudioCache.cachedSummary(Uri.parse(upgradedUri)))
        return warmedThrough
    }

    /** How an audition in progress is coming along — see [auditionUpgrade]. */
    private sealed interface Audition {
        data object Waiting : Audition

        /** Good, and buffered through this position in the track. */
        class Ready(val bufferedTo: Long) : Audition

        class Rejected(val why: String) : Audition
    }

    /**
     * Main thread. Everything that has to be true before the audio is cut,
     * asked of the audition player rather than of the catalogue that made the
     * claims.
     */
    private fun auditionVerdict(
        audition: ExoPlayer,
        previousDuration: Long,
        stream: SourceStream,
    ): Audition {
        audition.playerError?.let {
            return Audition.Rejected("${it.errorCodeName} opening ${stream.format.summary}")
        }
        // The failure a mid-track swap cannot survive, and the one that never
        // raises an error: a replacement that came up short does not fail, it
        // reaches the end of what it has and reports the track as over. Caught
        // here it costs nothing at all; caught after the swap it costs the
        // listener their song. See [watchUpgrade].
        if (audition.playbackState == Player.STATE_ENDED) {
            return Audition.Rejected("replacement ended immediately")
        }
        if (audition.playbackState != Player.STATE_READY) return Audition.Waiting
        val length = audition.duration
        if (length <= 0) return Audition.Waiting
        if (previousDuration > 0 && abs(length - previousDuration) > UPGRADE_LENGTH_SLACK_MS) {
            return Audition.Rejected("replacement is ${length}ms against ${previousDuration}ms")
        }
        // What the decoder was actually configured with, against what the
        // source said it was sending. The one failure mode a claim cannot
        // catch, because the claim is the thing that is wrong: a catalogue
        // advertising FLAC and serving a transcode buys a break in the audio
        // for no gain whatsoever.
        val mime = audition.audioFormat?.sampleMimeType
        if (mime != null && stream.format.isLossless == true && !NerdStats.isLosslessMime(mime)) {
            return Audition.Rejected("promised ${stream.format.summary}, decoder was handed $mime")
        }
        val buffered = audition.bufferedPosition
        // Aimed at where the listener will be, not where they were when this
        // started: the audition buffers ahead of a track that is still playing,
        // so the window it has to cover keeps moving. On any connection worth
        // upgrading over, buffering outruns playback and this converges in a
        // couple of seconds; on one where it doesn't, the swap would have
        // stalled anyway and the timeout is the right answer.
        val wantedThrough = (player?.currentPosition ?: 0L) + UPGRADE_PREBUFFER_MS
        // The only reason to settle for less: there is no more track to buffer.
        //
        // `isLoading` was tried here as a second escape — "the loader has
        // stopped of its own accord, so this is as good as it gets" — and it
        // was wrong every single time. [ChunkedDataSource] closes and reopens
        // the upstream every two megabytes, and `isLoading` goes false in the
        // gap between one range finishing and the next being asked for. A poll
        // landing in that gap read it as a full buffer, so every upgrade was
        // declared ready with roughly one chunk in hand and the swap then
        // landed seconds past the end of it, back on the network:
        //
        // ```
        //   upgrade to FLAC proved in 8701ms, buffered through 32496ms
        //   upgrade landing at 39889ms, past the 32496ms warmed for it
        // ```
        if (buffered >= wantedThrough || audition.bufferedPercentage >= 100) {
            return Audition.Ready(buffered)
        }
        return Audition.Waiting
    }

    /**
     * The throwaway player an upgrade is proved on.
     *
     * Shares the media source factory, and therefore the disk cache, with the
     * real one — which is the entire point: what this fetches is what the real
     * player reads a moment later. Deliberately plainer than the ghost player
     * [buildGhostPlayer] builds, because nothing here is ever heard: stock
     * renderers, no spatial processor, no audio session, no focus, no session.
     *
     * The one thing it does not share is the load control. [farBufferingLoadControl]
     * stops at [FAR_BUFFER_BYTES], which is sized for a player that only has to
     * stay ahead of itself; this one has to buffer past a *moving* target —
     * [UPGRADE_PREBUFFER_MS] beyond wherever the listener has got to by the time
     * it finishes — and eight megabytes is under fifteen seconds of hi-res FLAC,
     * which the drift alone can eat. Held for seconds and then released with the
     * player.
     */
    private fun buildAuditionPlayer(): ExoPlayer = ExoPlayer.Builder(this)
        .setMediaSourceFactory(requireNotNull(mediaSourceFactory))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ AUDITION_BUFFER_MS,
                    /* maxBufferMs = */ AUDITION_BUFFER_MS,
                    /* bufferForPlaybackMs = */ START_PLAYBACK_MS,
                    /* bufferForPlaybackAfterRebufferMs = */ START_PLAYBACK_MS,
                )
                .setTargetBufferBytes(AUDITION_BUFFER_BYTES)
                .build(),
        )
        .build()
        .apply {
            playWhenReady = false
            volume = 0f
        }

    /**
     * Puts the old stream back if the upgraded one turns out to be broken.
     *
     * Learned the hard way: a swapped-in source that comes up short — a
     * truncated body, a CDN that answers a range request with something other
     * than the file — does not raise an error. It reports no duration, plays
     * for a few seconds and hits end-of-stream, and ExoPlayer does the correct
     * thing with a track that has ended, which is to advance to the next one.
     * The listener's song simply vanishes eight seconds in. That is a far worse
     * outcome than the lossy stream this was trying to improve on, so the new
     * source has to prove itself against the length the old one already knew
     * before it is allowed to keep the track.
     */
    private fun watchUpgrade(
        mediaId: String,
        previousUri: String,
        position: Long,
        previousDuration: Long,
        previousFormat: StreamFormat?,
    ) {
        if (previousDuration <= 0) return
        scope.launch {
            val agreed = withTimeoutOrNull(UPGRADE_PROVE_MS) {
                while (true) {
                    val current = player?.takeIf { it.currentMediaItem?.mediaId == mediaId }
                        ?: return@withTimeoutOrNull false
                    val now = current.duration
                    if (now > 0) return@withTimeoutOrNull abs(now - previousDuration) <= UPGRADE_LENGTH_SLACK_MS
                    // The failure this whole check exists for, caught when it
                    // happens rather than at the ceiling: a replacement that
                    // came up short does not raise an error, it reaches the
                    // end of what it has and reports the track as over. That
                    // is a decisive no, and waiting out the rest of the window
                    // for it only delays the old stream coming back.
                    if (current.playbackState == Player.STATE_ENDED) return@withTimeoutOrNull false
                    delay(UPGRADE_PROVE_STEP_MS)
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            if (agreed == true) return@launch
            val player = player ?: return@launch
            val item = player.currentMediaItem ?: return@launch
            if (item.mediaId != mediaId) return@launch
            // State and buffered position alongside the length: a replacement
            // that loaded and disagreed about the track looks identical here
            // to one that never loaded at all, and only the second is a fault
            // in the stream rather than a wrong match.
            TrackLog.w(
                "BitChord",
                "upgrade reverted: replacement reports ${player.duration}ms against " +
                    "${previousDuration}ms (state=${player.playbackState}, " +
                    "buffered=${player.bufferedPosition}ms)",
            )
            QualityUpgrade.forget(mediaId)
            // The FLAC/whatever claim recorded when the swap went out is no
            // longer what's playing — restore what was declared before it
            // (or clear it, if nothing was), so "stats for nerds" doesn't
            // keep calling the fallback lossless after the upgrade it
            // borrowed that claim from got reverted.
            if (previousFormat != null) {
                NerdStats.onSourceStream(mediaId, previousFormat)
            } else {
                NerdStats.clearDeclared(mediaId)
            }
            swappingMediaId = mediaId
            val abandoned = item.localConfiguration?.uri
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                item.buildUpon().setUri(previousUri).build(),
            )
            player.seekTo(player.currentMediaItemIndex, position)
            player.prepare()
            // Whatever the replacement wrote is a prefix of a file nothing will
            // ever finish, under a key the *next* upgrade of this track would
            // key to as well — see [AudioCache.discardRendition]. Off the main
            // thread and behind the same pause a recovery takes, because the
            // source just released still holds the entry for a moment.
            abandoned?.let {
                launch(Dispatchers.IO) {
                    delay(RECOVERY_DELAY_MS)
                    AudioCache.discardRendition(it)
                }
            }
        }
    }

    /** What [resolveWithModulePriority] settled on. */
    private sealed interface Resolved {
        data class Module(val stream: SourceStream) : Resolved
        data class YouTube(val url: String) : Resolved
    }

    /**
     * Resolves a YouTube-queued track by racing the higher-ranked modules
     * against YouTube itself, and handing whatever the modules are still doing
     * to [QualityUpgrade] if YouTube gets there first.
     *
     * Nobody gets a head start. An earlier version gave the modules six
     * seconds of silence to answer in before the fallback was even *asked*
     * for, on the reasoning that a module answering inside that window plays
     * with no seam in it. What that actually bought, on every track the
     * modules were slow on, was six seconds of nothing followed by a YouTube
     * client walk starting from cold — the wait and the seam, rather than one
     * or the other. Starting both at once removes the first of those: the
     * track begins as soon as *anything* can serve it.
     *
     * The speculative resolve this reinstates was dropped once before, for a
     * real reason — it is several round trips to `youtubei.googleapis.com`
     * competing for the same radio and connection pool as the lookup beside
     * it, and on a track the modules do have, that work is thrown away. What
     * changed is that it is no longer speculative: YouTube is now the expected
     * outcome for anything the modules don't answer quickly, so its walk is on
     * the critical path rather than hedging one. It is also coalesced and
     * cached — see [StreamResolver.resolve] — so even a discarded walk warms
     * the URL this track will want if the upgrade later falls through.
     *
     * A module that wins the race outright still wins the track, which is the
     * one thing worth keeping from the old head start: the lossless copy plays
     * from the first note and there is no swap at all. That is a narrower
     * window than it sounds, and deliberately so — read-ahead warms the
     * YouTube URL for the queue (see [AudioCache.prefetchQueue]), so on a
     * track that was read ahead the fallback answers in milliseconds and
     * almost always wins. The swap is the ordinary path now; playing from the
     * first note is the prize for a module quick enough to beat a cached URL.
     *
     * A lookup that loses is not cancelled. It is handed over still running,
     * because it is not wrong, only late, and the thing it is about to return
     * is exactly the stream that would have played seamlessly had it been
     * quicker. It finishes on its own time and the track swaps up to it
     * mid-song, which is the trade this whole path exists to make: a short
     * break in the audio, in exchange for the listener hearing something now
     * rather than waiting in silence for the good copy.
     */
    private suspend fun resolveWithModulePriority(
        videoId: String,
        target: TrackMatcher.Target,
    ): Resolved {
        NerdStats.onLosslessRaceStart(videoId)
        val lookup = scope.async(Dispatchers.IO) {
            withTimeoutOrNull(SUBSTITUTE_TIMEOUT_MS) { SourceResolver.substituteForYouTube(target) }
        }
        // Started now rather than after the modules have had their say, and
        // wrapped rather than thrown from: it is awaited only on the paths
        // that need it, and an async that fails without ever being awaited is
        // an unhandled exception in this service's scope.
        val fallback = scope.async(Dispatchers.IO) { runCatching { StreamResolver.resolve(videoId) } }

        // First past the post. A null because [lookup] won is a module miss; a
        // null because [fallback] won means YouTube has a URL and the modules
        // are still looking — [lookup.isActive] below is what tells those
        // apart, which is the question the old head start answered by timing
        // out rather than by asking.
        val quick: SourceStream? = select {
            lookup.onAwait { it }
            fallback.onAwait { null }
        }

        if (quick != null) {
            // The modules got there first, so the YouTube walk is genuinely
            // spare work now. Cancelling drops only this service's wait on it;
            // [StreamResolver] parents the walk itself elsewhere and lets it
            // finish into its own cache.
            fallback.cancel()
            // Everything that was asked for, ahead of the fallback: the
            // ordinary good case, and the one with no seam in it.
            if (!quick.belowRequest) {
                NerdStats.onLosslessRaceEnd(videoId)
                return Resolved.Module(quick)
            }
            // Less than was asked for — but a lossy copy from a module still
            // beats going back to YouTube for one. Worth a second look, and
            // with this lookup already finished that look starts from scratch.
            val settled = QualityUpgrade.settledForLess(
                mediaId = videoId,
                target = target,
                playing = quick.format,
            )
            if (!settled) NerdStats.onLosslessRaceEnd(videoId)
            return Resolved.Module(quick)
        }

        val url = fallback.await().getOrThrow()
        // Marked pending only here, with the fallback's own bitrate in hand:
        // that figure is the answer to "better than what?" the second look
        // measures candidates against, and it isn't known until the client
        // walk has picked a format. A lookup still running is handed over to
        // be waited on rather than repeated; one that already finished with
        // nothing leaves the second look to find its own candidates.
        val pending = QualityUpgrade.settledForLess(
            mediaId = videoId,
            target = target,
            inFlight = lookup.takeIf { lookup.isActive },
            playing = NerdStats.pickedBitrateKbps(videoId)?.let { StreamFormat(kbps = it) },
        )
        if (!pending) NerdStats.onLosslessRaceEnd(videoId)
        return Resolved.YouTube(url)
    }

    /**
     * Publishes what the decoder is really being fed, for "stats for nerds".
     *
     * Bitrate is the awkward one: YouTube's WebM and MP4 containers carry no
     * bitrate field, so [Format.bitrate] arrives as `NO_VALUE` and the honest
     * figure is whatever named this stream instead. The source's own figure
     * comes ahead of YouTube's because a track can have both: one resolved
     * through YouTube and then upgraded to a module stream mid-song has a
     * stale 160 sitting in [NerdStats.pickedBitrateKbps] describing audio that
     * stopped playing several seconds ago. Anything still unknown is left null
     * for the UI to omit — better a shorter line than a made-up number.
     */
    private fun publishNerdStats() {
        val player = player ?: return
        val format = player.audioFormat
        val mediaId = player.currentMediaItem?.mediaId
        NerdStats.current.value = NerdStats.Snapshot(
            mimeType = format?.sampleMimeType,
            bitrateKbps = format?.bitrate?.takeIf { it != Format.NO_VALUE }?.div(1000)
                ?: NerdStats.declaredFormat(mediaId)?.kbps
                ?: NerdStats.pickedBitrateKbps(mediaId),
            sampleRateHz = format?.sampleRate?.takeIf { it != Format.NO_VALUE },
            channels = format?.channelCount?.takeIf { it != Format.NO_VALUE },
            bitDepth = format?.pcmEncoding?.let(::bitDepthOf),
            claimed = NerdStats.declaredFormat(mediaId),
        )
    }

    /**
     * PCM sample depth the renderer settled on, in bits.
     *
     * This is the figure that decides whether a hi-res file is being played as
     * one. A 24-bit FLAC whose renderer reports 16-bit PCM has been truncated
     * somewhere between the decoder and the sink, and no other number on the
     * stats line would show it — the sample rate and the codec both survive
     * that unharmed.
     *
     * `ENCODING_INVALID` and `NO_VALUE` mean the renderer hasn't said, which is
     * common for pass-through and for formats decoded straight to float, and
     * is reported as unknown rather than as a failure.
     */
    private fun bitDepthOf(pcmEncoding: Int): Int? = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    /** Snapshot the queue so the next launch can open where this one stopped. */
    private fun saveQueue() {
        val player = player ?: return
        if (player.mediaItemCount == 0) return
        LastPlayed.save(
            songs = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
        )
    }

    /**
     * Hands the cache the queue ahead of the one playing: [AudioCache.QUEUE_DEPTH]
     * tracks is more than it does anything with, but it decides that, not this.
     */
    private fun prefetchAround(player: ExoPlayer) {
        val nextIndex = player.nextMediaItemIndex
        val upcomingIds = if (nextIndex != C.INDEX_UNSET) {
            val end = (nextIndex + AudioCache.QUEUE_DEPTH - 1).coerceAtMost(player.mediaItemCount - 1)
            (nextIndex..end).map { player.getMediaItemAt(it).mediaId }
        } else {
            emptyList()
        }
        AudioCache.prefetchQueue(upcomingIds)
    }

    /**
     * Feeds played-seconds to [PlaybackTracker]. The tracker can't read the
     * player itself — ExoPlayer is confined to this thread — and a history
     * entry with no watchtime behind it barely registers as a listen, so the
     * sampling has to come from here.
     */
    private fun reportProgress(player: ExoPlayer) {
        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    lastPositionSeconds = player.currentPosition / 1000
                    player.currentMediaItem?.mediaId?.let {
                        PlaybackTracker.onProgress(it, lastPositionSeconds)
                    }
                    // Same cadence for the resume point: the process can be
                    // killed at any moment without another callback arriving.
                    saveQueue()
                    // The renderer can settle on its format a moment after the
                    // track change, which no callback of ours follows up on.
                    publishNerdStats()
                    // The backstop for the second look. The callbacks that
                    // start it fire at moments a track may not be resolved
                    // yet — the resolve happens on the loader thread when the
                    // source is opened, which for a track skipped to directly
                    // is after its own transition has been and gone. Cheap to
                    // repeat: it returns immediately unless the track is
                    // pending and nothing is already looking.
                    lookForBetterCopy(player)
                }
                delay(PROGRESS_SAMPLE_MS)
            }
        }
    }

    /**
     * Pause when the sleep timer runs out.
     *
     * `collectLatest` is what makes re-setting the timer work: the pending wait
     * is cancelled and restarted on the new deadline instead of both firing.
     */
    private fun watchSleepTimer() {
        scope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining > 0) delay(remaining)
                player?.pause()
                SleepTimer.cancel()
            }
        }
    }

    /**
     * Buffers as far ahead as a whole track rather than a rolling window.
     *
     * Media3's audio default stops loading at 13 buffer segments — around 830kB,
     * or 40 seconds of a 160kbps stream — and everything past that is fetched
     * only as playback consumes it. Since the data source writes through to
     * [AudioCache], how far ahead the player loads is also how much of the
     * track ends up on disk, and a seek past the buffered part is the one that
     * has to wait on the network.
     *
     * This matters for the track playback *starts* on. Everything after it is
     * on disk in full before it is reached, read ahead while it was still the
     * queued track — a first track has had no such chance.
     *
     * The byte ceiling is what governs; the duration is set past any song so
     * that it never becomes the binding constraint.
     *
     * Two further departures from the defaults, both about how long the
     * listener waits for sound:
     *
     *  - **Back buffer.** Media3 keeps nothing behind the playhead, so a seek
     *    *backwards* drops the buffer and reloads, while a seek forwards lands
     *    in samples already held. Half a minute of history closes that gap for
     *    the seek people actually make — nudging back a few seconds to catch a
     *    lyric — and it is deliberately no longer than that. The byte ceiling
     *    above counts *everything* the player holds, history included, so a
     *    back buffer wide enough to keep a whole track would spend the entire
     *    read-ahead budget on audio already heard: past the ceiling, loading
     *    stops, and since every second played moves a second from the front of
     *    the buffer to the back, the total never falls again and it never
     *    restarts. Read-ahead collapses and the track stalls every couple of
     *    seconds for the rest of its length. Seeking further back than this
     *    window is a disk read anyway, not a network one — [AudioCache] has
     *    written every byte already played.
     *  - **Thresholds to (re)start playback.** The defaults — 2.5s of audio
     *    before starting, 5s before resuming after a rebuffer — are sized for
     *    streaming video over a network that might stall again. Here the bytes
     *    are usually already on disk, so those seconds are spent waiting on a
     *    buffer that fills instantly and are simply dead air after a seek.
     *    Resuming is given more room than starting: a stall means the network
     *    is genuinely struggling, and coming back with a second of audio in
     *    hand only buys the next stall.
     */
    private fun farBufferingLoadControl() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            /* maxBufferMs = */ FAR_BUFFER_MS,
            /* bufferForPlaybackMs = */ START_PLAYBACK_MS,
            /* bufferForPlaybackAfterRebufferMs = */ RESUME_PLAYBACK_MS,
        )
        .setTargetBufferBytes(FAR_BUFFER_BYTES)
        .setBackBuffer(/* backBufferDurationMs = */ BACK_BUFFER_MS, /* retainBackBufferFromKeyframe = */ true)
        .build()

    /**
     * Renderers whose audio sink only skips silence worth skipping.
     *
     * Media3's stock threshold is 100ms, which eats the breaths, rests and
     * pre-chorus beats *inside* a song — the setting reads as "make the music
     * sound rushed" rather than "trim dead air". A second-long floor leaves
     * musical pauses alone and still collapses the run-in and run-out of a
     * track. Everything else about the chain stays default, so
     * `skipSilenceEnabled` keeps driving it as before.
     */
    private fun silenceSkippingRenderers(
        spatial: SpatialAudioProcessor,
        transition: TransitionFilterProcessor,
    ) = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(
                    // Transition filtering last of the two: widening is a
                    // property of the track, and a bass swap that ran before it
                    // would have its own low end fed back in by the crossfeed.
                    arrayOf(spatial, transition),
                    SilenceSkippingAudioProcessor(
                        MIN_SILENCE_US,
                        SilenceSkippingAudioProcessor.DEFAULT_SILENCE_RETENTION_RATIO,
                        SilenceSkippingAudioProcessor.DEFAULT_MAX_SILENCE_TO_KEEP_DURATION_US,
                        SilenceSkippingAudioProcessor.DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE,
                        SilenceSkippingAudioProcessor.DEFAULT_SILENCE_THRESHOLD_LEVEL,
                    ),
                    SonicAudioProcessor(),
                ),
            )
            .build()
    }

    /** Push current settings onto the player. */
    private fun applySettings(player: ExoPlayer) {
        player.skipSilenceEnabled = AppSettings.skipSilence.value
        player.setPlaybackSpeed(AppSettings.playbackSpeed.value)
        spatialAudioProcessor.enabled = DolbyAtmos.spatialAudioActive
    }

    private fun observeSettings() {
        scope.launch {
            AppSettings.skipSilence.collect { player?.skipSilenceEnabled = it }
        }
        scope.launch {
            AppSettings.playbackSpeed.collect { player?.setPlaybackSpeed(it) }
        }
        // Spatial audio is the user's switch *and* the device's: Atmos going
        // off in system settings mid-track has to stop the effect, not wait for
        // the next track or the next launch.
        scope.launch {
            combine(
                AppSettings.spatialAudio,
                DolbyAtmos.supported,
                DolbyAtmos.enabledOnDevice,
            ) { wanted, supported, atmosOn -> wanted && supported && atmosOn }
                .collect {
                    spatialAudioProcessor.enabled = it
                    ghostSpatialAudioProcessor.enabled = it
                }
        }
    }

    private fun observeScrobbling() {
        // Rebuild the ScrobbleManager whenever scrobbling settings change.
        scope.launch {
            // Explicit <Any, _>: these flows have mixed element types, and letting
            // the reified vararg combine() infer T lands on an intersection type.
            combine<Any, ScrobblingSnapshot>(
                AppSettings.lastfmEnabled,
                AppSettings.lastfmScrobbleEnabled,
                AppSettings.lastfmNowPlaying,
                AppSettings.lastfmSessionKey,
                AppSettings.lastfmApiKey,
                AppSettings.lastfmSecret,
                AppSettings.lastfmEndpoint,
                AppSettings.scrobbleMinDuration,
                AppSettings.scrobbleDelayPercent,
                AppSettings.scrobbleDelaySeconds,
            ) { values ->
                ScrobblingSnapshot(
                    lastfmEnabled = values[0] as Boolean,
                    scrobbleEnabled = values[1] as Boolean,
                    nowPlaying = values[2] as Boolean,
                    sessionKey = values[3] as String,
                    apiKey = values[4] as String,
                    secret = values[5] as String,
                    endpoint = values[6] as String,
                    minDuration = values[7] as Int,
                    delayPercent = values[8] as Float,
                    delaySeconds = values[9] as Int,
                )
            }.collectLatest { snapshot ->
                scrobbleManager?.destroy()
                scrobbleManager = null

                if (snapshot.lastfmEnabled && snapshot.sessionKey.isNotBlank()) {
                    // Configure LastFM client
                    val endpoint = snapshot.endpoint.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
                    val apiKey = snapshot.apiKey.ifBlank { LastFM.FALLBACK_COMPAT_API_KEY }
                    val secret = snapshot.secret.ifBlank { LastFM.FALLBACK_COMPAT_SECRET }
                    LastFM.configure(
                        endpoint = endpoint,
                        apiKey = apiKey,
                        secret = secret,
                        sessionKey = snapshot.sessionKey,
                    )
                    scrobbleManager = ScrobbleManager(
                        scope = scope,
                        minSongDuration = snapshot.minDuration,
                        scrobbleDelayPercent = snapshot.delayPercent,
                        scrobbleDelaySeconds = snapshot.delaySeconds,
                    ).apply {
                        useNowPlaying = snapshot.nowPlaying
                    }
                }
            }
        }
    }

    private data class ScrobblingSnapshot(
        val lastfmEnabled: Boolean,
        val scrobbleEnabled: Boolean,
        val nowPlaying: Boolean,
        val sessionKey: String,
        val apiKey: String,
        val secret: String,
        val endpoint: String,
        val minDuration: Int,
        val delayPercent: Float,
        val delaySeconds: Int,
    )

    /**
     * Submits a finished ListenBrainz listen, but only if the service is
     * actually scrobbling — the settings are read at call time so the helper
     * stays a no-op whenever ListenBrainz is switched off.
     */
    private fun submitListenBrainzFinished(song: Song, startMs: Long, durationMs: Long?) {
        val lbEnabled = AppSettings.listenBrainzEnabled.value
        val lbToken = AppSettings.listenBrainzToken.value
        if (!lbEnabled || lbToken.isBlank()) return
        val endMs = System.currentTimeMillis()
        scope.launch {
            ListenBrainzManager.submitFinished(lbToken, song, startMs, endMs, durationMs)
        }
    }

    /** Sends a ListenBrainz "now playing" update for the current track. */
    private fun submitListenBrainzPlayingNow(song: Song, positionMs: Long, durationMs: Long?) {
        val lbEnabled = AppSettings.listenBrainzEnabled.value
        val lbToken = AppSettings.listenBrainzToken.value
        if (!lbEnabled || lbToken.isBlank()) return
        scope.launch {
            ListenBrainzManager.submitPlayingNow(lbToken, song, positionMs, durationMs)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Called by Android when the user swipes this app's task away from the
     * recent apps screen.
     *
     * When [AppSettings.stopOnTaskRemoved] is on we stop the player and let the
     * service die naturally; otherwise we leave it running in the background so
     * music continues past the swipe, which is the default Android behaviour for
     * a foreground-service-backed media session.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (AppSettings.stopOnTaskRemoved.value) {
            player?.stop()
            stopSelf()
        }
    }


    override fun onDestroy() {
        // Last chance to record the resume point, while the player still exists.
        saveQueue()
        AudioCache.cancel()
        trackAnalyzer.release()
        // Also the last chance to close out the track that was playing — a
        // swipe-away or stop never fires STATE_ENDED, so the session would
        // otherwise end with an un-scrobbled song. This must not ride on the
        // service scope: it is cancelled a few lines down, and the request
        // should still reach ListenBrainz.
        val lastSong = listenBrainzSong
        if (lastSong != null) {
            val lbEnabled = AppSettings.listenBrainzEnabled.value
            val lbToken = AppSettings.listenBrainzToken.value
            if (lbEnabled && lbToken.isNotBlank()) {
                val lastStart = listenBrainzStartMs
                val lastDuration = player?.duration?.takeIf { it > 0 }
                CoroutineScope(Dispatchers.IO).launch {
                    ListenBrainzManager.submitFinished(
                        lbToken, lastSong, lastStart, System.currentTimeMillis(), lastDuration,
                    )
                }
            }
        }
        scrobbleManager?.destroy()
        scrobbleManager = null
        scope.cancel()
        crossfade?.release()
        crossfade = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /**
     * What the MediaSession, and so every control surface, actually talks to.
     *
     * Two behaviours are grafted onto the player here rather than left to
     * ExoPlayer's defaults:
     *
     * **Back restarts the track.** ExoPlayer already implements
     * restart-then-skip in [Player.seekToPrevious], gated on
     * `maxSeekToPreviousPosition`. External surfaces don't use it:
     * [DefaultMediaNotificationProvider] binds its previous button to
     * `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM`, which skips unconditionally. So
     * that command is redirected rather than left to behave differently
     * depending on which back button was pressed.
     *
     * **A skip cancels the crossfade.** Blending is for a track running out,
     * not for one being changed: told to move on, the listener wants the song
     * they were on to stop, not to keep playing over the one they asked for.
     * So every skip tells [CrossfadeController] to drop whatever is in flight
     * and then moves the queue plainly.
     *
     * Command availability is deliberately untouched — mutating it through a
     * [ForwardingPlayer] means intercepting listener callbacks too. The one
     * consequence is the first track of a queue, where ExoPlayer withholds
     * `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM` for want of a previous item: back
     * stays inert on those surfaces, exactly as it already was. In the app it
     * restarts, since that path asks for `COMMAND_SEEK_TO_PREVIOUS`.
     */
    private class SessionPlayer(
        player: Player,
        private val crossfade: CrossfadeController,
    ) : ForwardingPlayer(player) {

        override fun seekToPreviousMediaItem() {
            crossfade.onSkipRequested()
            wrappedPlayer.seekToPrevious()
        }

        override fun seekToNextMediaItem() {
            crossfade.onSkipRequested()
            wrappedPlayer.seekToNextMediaItem()
        }

        override fun seekToNext() {
            crossfade.onSkipRequested()
            wrappedPlayer.seekToNext()
        }
    }

    private companion object {
        const val CHANNEL_ID = "bitchord_playback"
        const val SESSION_ID = "BitChordPlayback"

        /** How often played-seconds are sampled off the player. */
        const val PROGRESS_SAMPLE_MS = 5_000L

        /**
         * Size of each range the player fetches. The same figure read-ahead
         * uses, and for the same reason — see [ChunkedDataSource].
         */
        const val STREAM_CHUNK_BYTES = 2L * 1024 * 1024

        /** Shortest gap "skip silence" is allowed to touch. */
        const val MIN_SILENCE_US = 1_000_000L

        /** Past any song, so the byte ceiling is what stops loading. */
        const val FAR_BUFFER_MS = 15 * 60 * 1000

        /** ~6 minutes at 160kbps: a whole track, for all but the longest. */
        const val FAR_BUFFER_BYTES = 8 * 1024 * 1024

        /**
         * A short nudge backwards, and no more: this shares the byte ceiling
         * above with the read-ahead it would otherwise starve.
         */
        const val BACK_BUFFER_MS = 30 * 1000

        /** Enough to cover the decoder's own latency, not seconds of dead air. */
        const val START_PLAYBACK_MS = 500

        /** More room after a stall than at the start — see the load control. */
        const val RESUME_PLAYBACK_MS = 2_000

        /**
         * Outer cap on stream resolution. Individual client calls and probes
         * have their own timeouts, but iterating all seven plus the NewPipe
         * fallback can accumulate far beyond what a listener should wait.
         *
         * The NewPipe fallback alone — a scrape of the watch page, shaped
         * harder than anything else this app asks Google for — routinely
         * takes 45-90s on its own when every player client is bot-checked, a
         * state that has become the common case rather than the rare one. A
         * cap shorter than that doesn't bound the wait; it cancels the
         * resolve just as it was about to succeed, and the retry that
         * follows restarts the same slow walk from zero, so the listener
         * waits *longer* under a tighter cap than a looser one.
         */
        const val RESOLVE_TIMEOUT_MS = 120_000L

        /**
         * Cap on offering a YouTube track to a higher-ranked source.
         *
         * Nothing like [RESOLVE_TIMEOUT_MS], because the two are not the same
         * kind of wait: that one bounds the only way to hear the track, this
         * one bounds an optional upgrade over a stream YouTube will serve
         * anyway. Generous enough for a cold module — index fetch, JS
         * download, engine init, search, then the stream URL — and short
         * enough that a dead server costs a pause rather than a stall.
         */
        const val SUBSTITUTE_TIMEOUT_MS = 20_000L

        /**
         * How much of a track has to be left for a mid-track quality swap to
         * be worth the break in the audio it costs.
         */
        const val UPGRADE_MIN_REMAINING_MS = 20_000L

        /**
         * How far into a track a swap may happen at the earliest, so an
         * upgrade that arrives with the first note doesn't cut it immediately.
         */
        const val UPGRADE_NOT_BEFORE_MS = 5_000L

        /** How often to recheck [CrossfadeController.isTransitioning] while an upgrade waits on one. */
        const val UPGRADE_CROSSFADE_POLL_MS = 250L

        /**
         * Longest an upgrade waits on a crossfade before giving up and
         * checking once more, authoritatively, right at the swap point. Well
         * past the longest transition either mode plans — 12s for a manual
         * crossfade, or a Smart Fade's own beat-bounded overlap, plus its arm
         * lead — so this is a guard against something stuck, not a limit
         * expected to bind in the ordinary case.
         */
        const val UPGRADE_CROSSFADE_WAIT_TIMEOUT_MS = 20_000L

        /**
         * How long a replacement gets to report a length before it is
         * disbelieved.
         *
         * This is silence, not patience: the swap has already cut the audio,
         * and the track sits in `STATE_BUFFERING` for the whole of it before
         * the old stream comes back. It was cut from eight seconds to two and
         * a half on the strength of "a replacement that works reports its
         * length in well under a tenth of this" — which was true of what the
         * swap landed on at the time, and is not true of a FLAC. Measured
         * here, an upgrade to a 16-bit Qobuz stream was still buffering its
         * first chunk when the window closed:
         *
         * ```
         *   upgrade reverted: replacement reports -9223372036854775807ms
         *     against 259141ms (state=2, buffered=5002ms)
         * ```
         *
         * — a working FLAC thrown away for being slower to open than a lossy
         * MP4, which is the one thing this feature exists to fetch. The
         * failure the short window was protecting against is caught by state
         * now rather than by the clock (see [watchUpgrade]), so the ceiling
         * only bounds the genuinely stuck case, and can afford to be long
         * enough for a large file over a phone connection.
         */
        const val UPGRADE_PROVE_MS = 10_000L
        const val UPGRADE_PROVE_STEP_MS = 200L

        /**
         * How long an upgrade gets to prove itself before the swap is dropped.
         *
         * Nothing like [UPGRADE_PROVE_MS], and for one reason: that window is
         * silence and this one is music. The audition runs on a player nobody
         * is listening to while the old stream plays through the whole of it,
         * so the only thing a longer ceiling costs is a decoder held open a
         * few seconds more. Generous enough for a cold hi-res FLAC over a
         * phone connection, since a stream slow to open is exactly the one
         * this feature exists to fetch and exactly the one the old
         * cut-then-wait order threw away.
         */
        const val UPGRADE_AUDITION_MS = 25_000L

        /**
         * How far past the listener an upgrade has to be buffered before it is
         * allowed to take over.
         *
         * This is the number that makes the swap inaudible. Everything inside
         * this window is on disk by the time the real player asks for it, so
         * the seam is a decoder init rather than a round trip to a CDN. It has
         * to cover the drift as well: the track keeps playing while the
         * audition buffers, so the swap lands some seconds past where the
         * audition started, and a window shorter than the audition takes would
         * put the swap point back on the network. Twelve seconds is comfortably
         * more than either.
         */
        const val UPGRADE_PREBUFFER_MS = 12_000L

        /**
         * How much of the upgraded file's opening is fetched before the
         * audition starts — see [AudioCache.warmRange] for why the audition
         * cannot be relied on to leave it behind.
         *
         * A megabyte because a FLAC header is not a header: STREAMINFO is 34
         * bytes, but the seek table, the tags and an embedded cover in front of
         * the first audio frame routinely run to hundreds of kilobytes, and a
         * range that stops short of the first frame buys nothing at all.
         */
        const val UPGRADE_HEADER_BYTES = 1L * 1024 * 1024

        /**
         * The audition's own buffer, in time and in bytes.
         *
         * Both well past [FAR_BUFFER_MS]'s byte ceiling, and deliberately: this
         * player has to end up [UPGRADE_PREBUFFER_MS] ahead of a position that
         * keeps moving while it works, so what it needs is the window plus
         * however long it took to fill — and at 4.6Mbit/s a hi-res FLAC eats
         * eight megabytes in under fifteen seconds. Transient, and freed with
         * the player a moment later.
         */
        const val AUDITION_BUFFER_MS = 40_000

        const val AUDITION_BUFFER_BYTES = 24 * 1024 * 1024

        /**
         * The pause between releasing the audition player and swapping onto
         * what it cached. Same reason as [RECOVERY_DELAY_MS] — Media3 lets go
         * of a cache entry as the source is released, not as the call returns —
         * and free here, because the old stream is still playing.
         */
        const val AUDITION_RELEASE_MS = 250L

        /**
         * How long the second look waits for the playing track to report its
         * own length before giving up and going on the claimed one.
         *
         * Costs nothing when it isn't needed — a prepared track answers on the
         * first poll — and the swap it feeds cannot happen inside
         * [UPGRADE_NOT_BEFORE_MS] anyway.
         */
        const val DURATION_SETTLE_MS = 8_000L

        /**
         * How far the replacement's length may sit from the length already
         * known for this track. Anything past this is a different file, or a
         * broken one, and either way not what is being listened to.
         */
        const val UPGRADE_LENGTH_SLACK_MS = 3_000L

        /** How many times one track is picked up off the floor — see [recoverFrom]. */
        const val MAX_RECOVERIES = 2

        /**
         * The pause before a retry. Media3 refuses to remove a cache entry a
         * reader still holds, and the reader is let go asynchronously as the
         * failed source is released, so the discard needs a moment to land
         * before the same track is asked for again.
         */
        const val RECOVERY_DELAY_MS = 350L
    }
}
