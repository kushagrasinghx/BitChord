package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.playback.EqCurve
import com.music.bitchord.playback.TransitionFilter
import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.TransitionPlan
import com.music.bitchord.playback.smart.TransitionStyle
import com.music.bitchord.playback.smart.TransitionTrackInfo
import com.music.bitchord.playback.smart.echoTailSeconds
import com.music.bitchord.playback.smart.planTransition
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/** Playback, decoded here rather than behind JavaFX. */
/**
 * Interleaved samples the blend has fed the incoming decoder, as that track's own elapsed time.
 *
 * A crossfade plays the incoming track underneath the outgoing one for the whole fade, so by the
 * time it becomes current it is already seconds in. Reporting its start instead left the scrubber
 * and the lyrics a fade-length behind the audio — words arriving late, fixable only by dragging
 * the scrubber.
 */
internal fun blendElapsedUs(samples: Long, channels: Int, sampleRate: Int): Long {
    if (sampleRate <= 0 || channels <= 0 || samples <= 0L) return 0L
    return (samples / channels) * 1_000_000L / sampleRate
}

class DesktopPlaybackEngine(
    private val onEnded: () -> Unit,
    private val onCrossfaded: (Song) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DesktopPlaybackState())
    val state: StateFlow<DesktopPlaybackState> = _state.asStateFlow()

    private val sink = DesktopAudioSink()

    /** Automix's evidence. */
    private val analyzer = DesktopTrackAnalyzer(performance = { automixPerformance })
    private val commands = ConcurrentLinkedQueue<Command>()
    private val running = AtomicBoolean(true)

    // The output sheet changes the persisted mixer directly. Observe that single source of truth
    // here so both the shared main-player sheet and the desktop settings dialog move the live
    // stream, on Windows and Linux alike.
    private var observedOutputDevice = DesktopAudioDevices.selected.value
    private val outputDeviceJob = scope.launch {
        DesktopAudioDevices.selected.collect { selected ->
            if (selected != observedOutputDevice) {
                observedOutputDevice = selected
                commands += Command.Reconfigure
            }
        }
    }

    private var resolveJob: Job? = null
    private var upgradeJob: Job? = null
    private var nextResolveJob: Job? = null

    @Volatile private var playbackSpeed = 1f
    @Volatile private var volume = 1f
    @Volatile private var paused = true
    // Volatile, every one of them: these are set from the UI thread when a setting changes and read
    // from the audio thread on the next block.
    @Volatile private var audioQuality = "HIGH"
    @Volatile private var crossfadeSeconds = 0
    @Volatile private var automixEnabled = false
    @Volatile private var nextSong: Song? = null

    /** [nextSong]'s downloaded file, when it has one, for its analysis. */
    @Volatile private var nextLocalStream: DesktopStream? = null

    /**
     * A [nextSong] an addon may serve, not yet resolved: it is held until the transition into it
     * is close, then armed by [armAddonUpcoming].
     */
    private val awaitingArm = java.util.concurrent.atomic.AtomicReference<Song?>(null)
    private var retryingSongId: String? = null

    /** Owned by the audio thread once handed over; never touched from outside. */
    private class Track(
        val song: Song,
        val decoder: DesktopDecodeAhead,
        val stream: DesktopStream,
        /**
         * Where in this track its first rendered sample sits.
         *
         * A `var` because Automix moves it: cueing the incoming track to a downbeat seeks its
         * decoder, and a position published against an unmoved start is short by the whole cue.
         */
        var startUs: Long,
    ) {
        var gain = 1f
        var finished = false
        var tempo: DesktopTempoBuffer? = null
        var tempoRate = 1.0
        var tempoEaseStep = 0.0
        var tempoEaseEveryFrames = 0L
        var tempoEaseNextFrame = Long.MAX_VALUE
        var tempoOutputFrames = 0L
        /** The stretch has eased out and [tempo] is only playing off what it still held. */
        var tempoDrained = false
        /** Brings this track to the output stream's rate, when the two differ; see [readSource]. */
        var converter: DesktopRateConverter? = null

        /** Back to the decoder at the listener's speed, dropping any beatmatch stretch. */
        fun dropTempo() {
            // The converter's history is from before whatever moved the decoder.
            converter = null
            tempo = null
            tempoRate = 1.0
            tempoEaseStep = 0.0
            tempoEaseNextFrame = Long.MAX_VALUE
            tempoDrained = false
        }
    }

    private sealed interface Command {
        class Start(val track: Track, val playWhenReady: Boolean) : Command
        /** Replaces only the playing deck; a quality upgrade must not discard the prepared next deck. */
        class ReplaceCurrent(val track: Track, val playWhenReady: Boolean) : Command
        class Upcoming(val track: Track) : Command
        /** A better copy of the incoming track, found after it was opened; see [upgradeIncoming]. */
        class ReplaceUpcoming(val track: Track) : Command
        class SeekTo(val millis: Long) : Command
        data object ClearUpcoming : Command
        data object Reconfigure : Command
        data object Flush : Command
    }

    private val thread = Thread(::pump, "BitChord-Audio").apply {
        isDaemon = true
        priority = Thread.NORM_PRIORITY + 2
        start()
    }

    // ---- the surface the application uses -------------------------------

    fun load(song: Song, playWhenReady: Boolean = true, startAtMs: Long = 0L) {
        retryingSongId = null
        loadInternal(
            song,
            playWhenReady,
            excludedSourceId = refusedSources[song.videoId],
            startAtMs = startAtMs.coerceAtLeast(0L),
        )
    }

    /**
     * Sources that handed over a URL they could not actually serve, by track.
     *
     * Remembered for the session, not just for the retry: without it every play of the same track
     * asks the same dead source again and waits for it to fail before falling back.
     */
    private val refusedSources = ConcurrentHashMap<String, String>()

    private fun loadInternal(
        song: Song,
        playWhenReady: Boolean,
        excludedSourceId: String? = null,
        startAtMs: Long = 0L,
        /** Why the first attempt failed, when this call is the retry. */
        priorFailure: Throwable? = null,
    ) {
        resolveJob?.cancel()
        nextResolveJob?.cancel()
        upgradeJob?.cancel()
        commands += Command.Flush
        searchingBetterFor = null
        incomingSearchingFor = null
        _state.value = DesktopPlaybackState(song = song, volume = volume, isLoading = true)
        resolveJob = scope.launch {
            // Only when the file is really there: a download record can outlive the file it names,
            // and handing the decoder a path that is not there fails as "could not open stream"
            // with nothing to say why. A missing one falls through to the sources instead.
            // The plain path, not a file:// URI. FFmpeg does not percent-decode what it is given,
            // so a URI for "Justin Bieber - Peaches (feat. …).m4a" sent it looking for a file
            // literally named "Justin%20Bieber%20-%20…" and it answered ENOENT for a file that was
            // sitting right there.
            val localUrl = DesktopDownloadManager.savedFile(song)?.toAbsolutePath()?.toString()
            val resolved = localUrl?.let { Result.success(DesktopLiveResolution(DesktopStream(it))) }
                ?: DesktopMusicSources.resolveLive(song, audioQuality, excludedSourceId)
            resolved.fold(
                onSuccess = { live ->
                    val opened = openTrack(song, live.stream, startAtMs)
                    opened.fold(
                        onSuccess = { track ->
                            searchingBetterFor = song.videoId.takeIf { live.pendingSubstitute != null }
                            commands += Command.Start(track, playWhenReady)
                            live.pendingSubstitute?.let { watchForUpgrade(song, it) }
                        },
                        onFailure = { failure -> retryAfterFailure(song, playWhenReady, live.stream, failure) },
                    )
                },
                onFailure = { failure ->
                    val reported = priorFailure ?: failure
                    _state.value = DesktopPlaybackState(
                        song = song,
                        volume = volume,
                        error = reported.message ?: "Playback failed",
                    )
                },
            )
        }
    }

    /** Opens a decoder on [stream], off the audio thread. */
    private suspend fun openTrack(song: Song, stream: DesktopStream, startAtMs: Long): Result<Track> =
        withContext(Dispatchers.IO) {
            val decoder = DesktopAudioDecoder()
            decoder.open(
                url = stream.url,
                headers = stream.headers,
                // Float throughout: the processors work in it and the sink converts once, at the
                // end.
                requested = DesktopPcmFormat(44_100, 2, bytesPerSample = 4, isFloat = true),
                windowed = stream.windowedReads,
                transport = stream.transport,
            ).map {
                if (startAtMs > 0) decoder.seek(startAtMs * 1_000)
                // From here the decoder belongs to its own read-ahead thread: the audio thread only
                // ever takes what has already been decoded, so a slow network read is not a gap.
                val ahead = DesktopDecodeAhead(
                    source = decoder,
                    outputFormat = decoder.outputFormat,
                    durationUs = decoder.durationUs,
                    measuredFormat = decoder.measuredFormat,
                    name = "BitChord-Decode ${song.title.take(24)}",
                )
                Track(song, ahead, stream, startAtMs * 1_000)
            }.onFailure { decoder.close() }
        }

    /**
     * A source that cannot actually be decoded is struck off and the track asked for again, once.
     */
    private fun retryAfterFailure(song: Song, playWhenReady: Boolean, stream: DesktopStream, failure: Throwable) {
        if (retryingSongId != song.videoId) {
            retryingSongId = song.videoId
            DesktopTrackLog.log("could not decode '${song.title}' from ${DesktopMusicSources.sourceNameFor(stream)}: ${failure.message}")
            stream.sourceId?.let { refusedSources[song.videoId] = it }
            loadInternal(song, playWhenReady, excludedSourceId = stream.sourceId, priorFailure = failure)
        } else {
            _state.value = DesktopPlaybackState(
                song = song,
                volume = volume,
                error = "Could not play this track: ${failure.message}",
            )
        }
    }

    /**
     * The second look: a track that started on YouTube because the other sources were still
     * searching gets swapped once one of them answers.
     */
    private fun watchForUpgrade(song: Song, pending: Deferred<DesktopStream?>) {
        upgradeJob = scope.launch {
            try {
                if (take(song, runCatching { pending.await() }.getOrNull())) return@launch
                // The live race takes the first copy that beats YouTube, so a slower lossless
                // source can still be searching when a lossy one wins — and a source that failed
                // that minute may answer properly now. Android asks again; so does this.
                repeat(LOSSLESS_FOLLOW_UPS) {
                    val current = _state.value
                    if (current.song?.videoId != song.videoId) return@launch
                    if (current.streamFormat?.isLossless == true) return@launch
                    if (current.streamFormat?.isDolbyAtmos == true) return@launch
                    if (DesktopMusicSources.ceiling(null) != DesktopAudioQuality.LOSSLESS) return@launch
                    val playing = DesktopStream(url = "", format = current.streamFormat ?: DesktopStreamFormat(), sourceId = current.streamSourceId)
                    if (take(song, DesktopMusicSources.upgradeFor(song, playing))) return@launch
                }
                DesktopTrackLog.log("no better copy of '${song.title}' was found")
            } finally {
                // Settled either way: the badge stops saying "upgrading" the moment the search
                // stops, never on a timer.
                // Only this song's: by now the queue may have blended on to another whose own
                // search is still running.
                if (searchingBetterFor == song.videoId) searchingBetterFor = null
                _state.update { if (it.song?.videoId == song.videoId) it.copy(searchingBetter = false) else it }
            }
        }
    }

    /** Swaps [better] in when it is worth the break, and says whether the question is settled. */
    private suspend fun take(song: Song, better: DesktopStream?): Boolean {
        if (better == null) return false
        val current = _state.value
        if (current.song?.videoId != song.videoId) return true
        if (!DesktopMusicSources.worthSwapping(better.format, current.streamFormat)) {
            DesktopTrackLog.log(
                "keeping '${song.title}' on what is playing — " +
                    "${DesktopMusicSources.sourceNameFor(better)} offered nothing better",
            )
            return false
        }
        DesktopTrackLog.log(
            "upgrading '${song.title}' to ${better.format.summary.ifBlank { "another rendition" }}" +
                " from ${DesktopMusicSources.sourceNameFor(better)}",
        )
        swapStream(song, better, current.positionMs)
        return true
    }

    /** Re-opens the current track on a different stream, carrying the playhead over. */
    private fun swapStream(song: Song, stream: DesktopStream, positionMs: Long) {
        scope.launch {
            openTrack(song, stream, positionMs).fold(
                onSuccess = { track ->
                    val latest = _state.value
                    if (latest.song?.videoId != song.videoId) {
                        track.decoder.close()
                        return@fold
                    }
                    // Resolving and opening the better rendition can take a few seconds. Hand it
                    // over at the live playhead rather than jumping back to the position at which
                    // the upgrade was first offered.
                    if (kotlin.math.abs(latest.positionMs - positionMs) >= UPGRADE_RESEEK_THRESHOLD_MS) {
                        track.decoder.seek(latest.positionMs * 1_000)
                        track.startUs = latest.positionMs * 1_000
                    }
                    commands += Command.ReplaceCurrent(track, playWhenReady = latest.isPlaying)
                },
                onFailure = { failure ->
                    DesktopTrackLog.log(
                        "could not open the copy of '${song.title}' from " +
                            "${DesktopMusicSources.sourceNameFor(stream)}: ${failure.message}",
                    )
                    _state.update { it.copy(
                        error = "Could not switch version: ${failure.message ?: "that copy would not open"}",
                    ) }
                },
            )
        }
    }

    /** Re-opens the current track, keeping the playhead. */
    fun reloadCurrent(forceSourceRefresh: Boolean = false) {
        val current = _state.value
        val song = current.song ?: return
        if (forceSourceRefresh) DesktopAddonSource.clearCompletedTrackCalls()
        upgradeJob?.cancel()
        DesktopTrackLog.log(
            "re-opening '${song.title}' — pinned to the original: " +
                "${DesktopOriginalVersion.isPinned(song.videoId)}",
        )
        scope.launch {
            DesktopMusicSources.resolve(song, audioQuality).fold(
                onSuccess = { stream ->
                    if (_state.value.song?.videoId != song.videoId) return@fold
                    DesktopTrackLog.log("re-opened from ${DesktopMusicSources.sourceNameFor(stream)}")
                    swapStream(song, stream, current.positionMs)
                },
                onFailure = { failure ->
                    DesktopTrackLog.log("could not re-open '${song.title}': ${failure.message}")
                    _state.update { it.copy(
                        error = "Could not switch version: ${failure.message ?: "no source answered"}",
                    ) }
                },
            )
        }
    }

    fun togglePlayPause() {
        if (paused) play() else pause()
    }

    fun play() {
        paused = false
        _state.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        paused = true
        _state.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        // TEMP seek diagnostics: who asked, so a seek undone by a second one shows up.
        DesktopTrackLog.log(
            "seek requested: ${positionMs}ms (at ${_state.value.positionMs}ms) from " +
                Throwable().stackTrace.drop(1).take(6).joinToString(" < ") { "${it.fileName}:${it.lineNumber}" },
        )
        commands += Command.SeekTo(positionMs.coerceAtLeast(0))
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.25f, 3.0f)
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        sink.gain = volume
        _state.update { it.copy(volume = volume) }
    }

    /** Sets the source quality ceiling used the next time a track is resolved. */
    fun setAudioQuality(quality: String) {
        audioQuality = quality.uppercase()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        crossfadeSeconds = seconds.coerceIn(0, MAX_CROSSFADE_SECONDS)
        if (crossfadeSeconds == 0 && !automixEnabled) clearUpcoming()
    }

    /** Enables Android's separate Automix setting. */
    fun setAutomixEnabled(enabled: Boolean) {
        automixEnabled = enabled
        if (!enabled && crossfadeSeconds == 0) clearUpcoming()
    }

    /** Resolves and prepares the next queue item without starting it. */
    fun prepareNext(song: Song?) {
        // Past the midpoint of a blend the player already shows the incoming song, and the app
        // answers that by asking for the track *after* it — which would clear the upcoming track
        // this blend is still playing. Held until the blend ends, and then carried out.
        synchronized(blendLock) {
            if (displaySwitched) {
                deferredNext = song
                hasDeferredNext = true
                return
            }
        }
        nextResolveJob?.cancel()
        clearUpcoming()
        if (song == null || transitionSecondsFor(song) == 0) return
        nextSong = song
        // A download plays from its file, here as in [loadInternal] — never from whichever source
        // would have answered for it.
        val local = DesktopDownloadManager.savedFile(song)?.toAbsolutePath()?.toString()?.let { DesktopStream(it) }
        nextLocalStream = local
        // Measured on YouTube Opus (or the file) whatever will end up serving it, so Automix has
        // its analysis without anything being fetched from the source that will play it.
        if (automixEnabled && !song.isVideoOrigin) {
            analyzer.request(song, local, song.durationText.durationSeconds())
        }
        if (local == null && DesktopMusicSources.mayServeFromAddon(song)) {
            // Android does not read an addon's track ahead; it is asked for a stream only once the
            // track is about to play. Held here and armed shortly before the transition — see
            // [armAddonUpcoming].
            DesktopTrackLog.log("'${song.title}' may come from an addon, so it is not read ahead")
            awaitingArm.set(song)
            return
        }
        resolveUpcoming(song, local)
    }

    /**
     * Resolves [song] (or takes its file) and opens it as the incoming track, off the audio thread.
     *
     * [race] is for a track armed shortly before its transition: the sources ranked above YouTube
     * are raced against it, as when a track is started by hand, so the blend never waits on a slow
     * addon — an addon routinely takes 10-15s to answer, which outlasted the arming lead and left
     * the marker on the scrubber with no transition behind it. Whatever the race was still waiting
     * on is swapped in when it answers; see [upgradeIncoming].
     */
    private fun resolveUpcoming(song: Song, local: DesktopStream? = null, race: Boolean = false) {
        nextResolveJob = scope.launch {
            val askedAt = System.nanoTime()
            val resolved: Result<DesktopLiveResolution> = when {
                local != null -> Result.success(DesktopLiveResolution(local))
                race -> DesktopMusicSources.resolveLive(song, audioQuality)
                else -> DesktopMusicSources.resolve(song, audioQuality).map { DesktopLiveResolution(it) }
            }
            resolved.fold(
                onSuccess = { live ->
                    openTrack(song, live.stream, startAtMs = 0).fold(
                        onSuccess = { track ->
                            // A newer [prepareNext] may have asked for another track while this one
                            // was resolving.
                            if (nextSong?.videoId != song.videoId) {
                                track.decoder.close()
                                live.pendingSubstitute?.cancel()
                                return@fold
                            }
                            DesktopTrackLog.log(
                                "incoming '${song.title}' ready from ${DesktopMusicSources.sourceNameFor(live.stream)}" +
                                    " in ${(System.nanoTime() - askedAt) / 1_000_000}ms",
                            )
                            commands += Command.Upcoming(track)
                            live.pendingSubstitute?.let { upgradeIncoming(song, track.stream, it) }
                        },
                        onFailure = { failure ->
                            DesktopTrackLog.log("could not open incoming '${song.title}': ${failure.message}")
                            live.pendingSubstitute?.cancel()
                            if (nextSong?.videoId == song.videoId) nextSong = null
                        },
                    )
                },
                onFailure = { failure ->
                    DesktopTrackLog.log("could not resolve incoming '${song.title}': ${failure.message}")
                    if (nextSong?.videoId == song.videoId) nextSong = null
                },
            )
        }
    }

    /**
     * Swaps in the better copy a raced resolve of the incoming track was still waiting on.
     *
     * Not a child of [nextResolveJob]: the next [prepareNext] cancels that the moment the blend
     * moves the queue on, and by then this answer belongs to the track now playing.
     */
    private fun upgradeIncoming(song: Song, opened: DesktopStream, pending: Deferred<DesktopStream?>) {
        // The player says "upgrading" for this track from the moment it is shown, mid-blend.
        incomingSearchingFor = song.videoId
        scope.launch {
            val better = runCatching { pending.await() }.getOrNull()
                ?.takeIf { DesktopMusicSources.worthSwapping(it.format, opened.format) }
            if (better == null) {
                settleIncomingSearch(song)
                return@launch
            }
            // Not heard yet: the incoming deck itself is replaced, with nothing to interrupt.
            if (upcoming?.song?.videoId == song.videoId && fadeRemaining <= 0 && !displaySwitched) {
                openTrack(song, better, startAtMs = 0).fold(
                    onSuccess = { commands += Command.ReplaceUpcoming(it) },
                    onFailure = { settleIncomingSearch(song) },
                )
                return@launch
            }
            playingUpgrade(song, better)
        }
    }

    /** The incoming track's search is over without anything for the playing-track upgrade. */
    private fun settleIncomingSearch(song: Song) {
        if (incomingSearchingFor != song.videoId) return
        incomingSearchingFor = null
        _state.update { if (it.song?.videoId == song.videoId) it.copy(searchingBetter = false) else it }
    }

    /**
     * Hands a better copy of [song] to the ordinary playing-track upgrade, once the blend into it
     * has finished — [replaceCurrent] refuses anything mid-blend.
     */
    private fun playingUpgrade(song: Song, better: DesktopStream) {
        scope.launch {
            repeat(BLEND_WAIT_POLLS) {
                val playing = _state.value.song?.videoId == song.videoId
                if (playing && !displaySwitched && fadeRemaining <= 0) {
                    // Handed over: from here the search is the playing track's, and ends with it.
                    searchingBetterFor = song.videoId
                    if (incomingSearchingFor == song.videoId) incomingSearchingFor = null
                    watchForUpgrade(song, kotlinx.coroutines.CompletableDeferred(better))
                    return@launch
                }
                // Skipped past, or never reached.
                if (!playing && upcoming?.song?.videoId != song.videoId) {
                    settleIncomingSearch(song)
                    return@launch
                }
                delay(BLEND_WAIT_POLL_MS)
            }
            settleIncomingSearch(song)
        }
    }

    fun release() {
        analyzer.release()
        running.set(false)
        resolveJob?.cancel()
        nextResolveJob?.cancel()
        upgradeJob?.cancel()
        thread.interrupt()
        scope.cancel()
    }

    /**
     * [release], then waits for the audio thread to close its decoders and the output stream.
     *
     * For quitting. exitProcess tears native libraries down underneath any thread still inside
     * them, and the audio thread is always inside FFmpeg or WASAPI — quitting from the tray
     * mid-song ended in a native crash rather than a clean exit.
     */
    fun shutdown(timeoutMs: Long = 2_000) {
        release()
        runCatching { thread.join(timeoutMs) }
    }

    private fun clearUpcoming() {
        nextSong = null
        nextLocalStream = null
        awaitingArm.set(null)
        commands += Command.ClearUpcoming
    }

    private fun transitionSecondsFor(next: Song?): Int = when {
        next == null -> 0
        crossfadeSeconds > 0 -> crossfadeSeconds
        automixEnabled -> AUTOMIX_FALLBACK_SECONDS
        else -> 0
    }

    // ---- the audio thread -----------------------------------------------

    private var current: Track? = null
    private var upcoming: Track? = null
    private var speedProcessor: DesktopAudioSpeed? = null
    /** One widener per side of a mix, because widening is a property of a track. */
    private var spatial: DesktopSpatialAudio? = null
    private var spatialIncoming: DesktopSpatialAudio? = null

    /**
     * One equaliser over each side of a mix, matching Android's per-player pair.
     *
     * Ahead of the transition filter for the reason Android's chain gives: the equaliser belongs to
     * the listener and the whole session, while the filter belongs to one handoff and has to have
     * the last word on it.
     */
    private var equalizer = DesktopEqualizer()
    private var equalizerIncoming = DesktopEqualizer()
    private var silence: DesktopSilenceSkipper? = null

    /** The playing track's heard position, on the sink's played-frame count; see [play]. */
    private val playhead = DesktopPlayhead()
    private var fadeRemaining = 0
    private var fadeTotal = 0
    /** Fade plus any echo tail. [fadeTotal] remains the nominal fader span. */
    private var transitionTotal = 0
    private var transitionEcho: DesktopTransitionEcho? = null

    /**
     * Samples of the incoming track already rendered under the outgoing one.
     *
     * A blend plays the next track for the whole length of the fade before it
     * becomes the current one, so at handover it is seconds in — not at its
     * start. Android has no equivalent because it crossfades between two whole
     * players and the session simply moves onto the one already playing; here
     * there is a single sink, so what the incoming track has actually consumed
     * has to be counted to be known.
     */
    private var incomingSourceFrames = 0.0
    private var mixed = FloatArray(0)
    private var endPadding = FloatArray(0)
    private val transitionGains = FloatArray(2)
    private var currentReadCount = 0
    private var incomingReadCount = 0

    /**
     * Output frames handed to the sink, on the same count as [DesktopAudioSink.framesPlayed]:
     * the difference is what is queued but not yet heard. Re-synced wherever the sink drops or
     * restarts its queue.
     */
    private var framesWritten = 0L

    /**
     * Whether the player has already moved to the incoming song. Set halfway through a blend,
     * matching Android: the outgoing song stays on screen while it is the louder of the two, and
     * the incoming one takes over the moment it is. The audio carries on blending to the end.
     */
    @Volatile private var displaySwitched = false
    private val blendLock = Any()
    private var deferredNext: Song? = null
    private var hasDeferredNext = false

    /** One filter over each side of a mix. */
    private val outgoingFilter = TransitionFilter()
    private val incomingFilter = TransitionFilter()

    /** The plan the running fade was started from, for the filter ride. */
    private var activePlan: TransitionPlan? = null

    private fun pump() {
        while (running.get()) {
            runCatching { step() }.onFailure { failure ->
                if (failure is InterruptedException) return
                DesktopTrackLog.log("audio thread recovered from: ${failure.message}")
            }
        }
        closeEverything()
    }

    private fun step() {
        drainCommands()
        val track = current
        if (track == null || paused) {
            if (paused) sink.pause()
            if (paused && fadeRemaining > 0) publishBlend(playing = false)
            Thread.sleep(20)
            return
        }
        sink.resume()

        val block = readCurrent(track)
        val count = currentReadCount
        if (block == null) {
            if (fadeRemaining > 0 && upcoming != null) {
                val channels = sink.format.channels.coerceAtLeast(1)
                val wanted = MIX_END_PADDING_FRAMES * channels
                if (endPadding.size < wanted) endPadding = FloatArray(wanted)
                java.util.Arrays.fill(endPadding, 0, wanted, 0f)
                val (blended, blendedCount) = blend(track, endPadding, wanted)
                val (stretched, stretchedCount) = stretch(blended, blendedCount)
                play(track, stretched, stretchedCount)
                publishPosition(track)
                return
            }
            finishTrack(track)
            return
        }
        if (count == 0) return

        val (blended, blendedCount) = blend(track, block, count)
        val quiet = silence?.also { it.enabled = skipSilenceEnabled }
        val skippedBefore = quiet?.skippedFrames ?: 0L
        val trimmed = quiet?.process(blended, blendedCount) ?: blended
        val trimmedCount = quiet?.outputCount ?: blendedCount
        val skipped = (quiet?.skippedFrames ?: 0L) - skippedBefore
        val (stretched, stretchedCount) = stretch(trimmed, trimmedCount)
        play(track, stretched, stretchedCount, skipped)
        publishPosition(track)
    }

    /**
     * Hands a block to the sink, first telling [playhead] how it maps onto [track]: at what rate,
     * and how much silence was cut ahead of it. Recorded against the frame it starts on, so it
     * takes effect when that frame is heard rather than a queue's length early.
     */
    private fun play(track: Track, samples: FloatArray, count: Int, skippedFrames: Long = 0L) {
        val rate = sink.format.sampleRate
        if (rate > 0) {
            val deck = deckRate(track)
            playhead.change(
                frame = framesWritten,
                usPerFrame = usPerFrame(track),
                // Cut before the listener's stretch, so it is the deck's time, not the speaker's.
                skippedUs = skippedFrames.coerceAtLeast(0L) * 1_000_000.0 / rate * deck,
            )
        }
        sink.write(samples, count)
        framesWritten += count / sink.format.channels.coerceAtLeast(1)
        if (count > 0 && _state.value.awaitingAudio) _state.update { it.copy(awaitingAudio = false) }
    }

    /** The beatmatch stretch [track] is actually rendered at — [DesktopTempoBuffer]'s clamp included. */
    private fun deckRate(track: Track): Double =
        if (track.tempo != null && !track.tempoDrained) track.tempoRate.coerceIn(1.0, 1.1) else 1.0

    /** Source time each output frame of [track] covers: the listener's speed times the deck's. */
    private fun usPerFrame(track: Track): Double =
        1_000_000.0 / sink.format.sampleRate.coerceAtLeast(1) * playbackSpeed * deckRate(track)

    /** Starts [playhead] over: what the speakers are playing right now is [positionUs] of [track]. */
    private fun resetPlayhead(track: Track, positionUs: Long) {
        playhead.reset(sink.framesPlayed(), positionUs, usPerFrame(track))
    }

    /** Mixes the outgoing track with the one coming in, when a crossfade is running. */
    /** Mixes the outgoing track with the one coming in, filtering each side. */
    private fun blend(track: Track, block: FloatArray, count: Int): Pair<FloatArray, Int> {
        val incoming = upcoming
        // Widened first, whether or not a mix is running, then equalised.
        widen(spatial, track, block, count)
        equalise(equalizer, block, count)
        if (fadeRemaining <= 0 || incoming == null) return block to count

        if (mixed.size < count) mixed = FloatArray(count)
        val other = readIncoming(incoming, count)
        val otherCount = incomingReadCount
        val incomingRate = deckRate(incoming)
        incomingSourceFrames += otherCount.toDouble() / sink.format.channels.coerceAtLeast(1) * incomingRate
        if (other != null) {
            widen(spatialIncoming, incoming, other, otherCount)
            equalise(equalizerIncoming, other, otherCount)
        }
        val channels = sink.format.channels.coerceAtLeast(1)
        val plan = activePlan
        val subBlock = TransitionFilter.GLIDE_FRAMES * channels

        var index = 0
        while (index < count) {
            val progress = ((transitionTotal - fadeRemaining).toFloat() / fadeTotal.coerceAtLeast(1))
            val styleProgress = progress.coerceIn(0f, 1f)
            plan?.let { DesktopTransitionRide.aim(it, styleProgress, outgoingFilter, incomingFilter) }
            outgoingFilter.advance()
            incomingFilter.advance()

            if (plan != null) DesktopTransitionRide.gains(plan, styleProgress, transitionGains)
            val out = if (plan != null) transitionGains[0] else kotlin.math.sqrt(1f - styleProgress)
            val into = if (plan != null) transitionGains[1] else kotlin.math.sqrt(styleProgress)
            // Equal-power gains sum past 1 — up to 1.41 at the midpoint — and two loud masters
            // peaking together would be clipped hard by the sink. A smooth trim of
            // 1 / sqrt(out + into) holds that to 1.19 for at most 1.5 dB, and reshapes nothing:
            // the same trim Android's two players take.
            val outgoingLevel = if (plan != null && plan.echoSeconds > 0.0) {
                DesktopTransitionRide.echoLevel(plan, progress)
            } else {
                out
            }
            val trim = 1f / kotlin.math.sqrt((outgoingLevel + into).coerceAtLeast(1f))
            val stop = minOf(count, index + subBlock)
            val span = stop - index
            while (index < stop) {
                val channel = index % channels
                var leaving = outgoingFilter.filter(channel, block[index])
                if (plan != null && plan.echoSeconds > 0.0) {
                    val echoAt = DesktopTransitionRide.echoAt(plan)
                    val echoFrom = DesktopTransitionRide.echoFrom(plan)
                    if (progress >= echoFrom) {
                        val send = if (progress >= echoAt) 0f else ((progress - echoFrom) / (echoAt - echoFrom)).coerceIn(0f, 1f)
                        val kill = DesktopTransitionRide.echoKill(plan)
                        val dry = (1f - ((progress - echoAt) / kill).coerceIn(0f, 1f))
                        leaving = transitionEcho?.process(leaving, send, dry) ?: leaving
                    }
                }
                val arriving = if (index < otherCount) {
                    incomingFilter.filter(channel, other!![index])
                } else {
                    0f
                }
                mixed[index] = (leaving * out + arriving * into) * trim
                index++
            }
            fadeRemaining -= span
        }
        val progress = ((transitionTotal - fadeRemaining).toFloat() / fadeTotal.coerceAtLeast(1))
        val handoffAt = plan?.let(DesktopTransitionRide::handoffAt) ?: DISPLAY_SWITCH_AT
        if (!displaySwitched && progress >= handoffAt && fadeRemaining > 0) switchDisplay(incoming)
        if (fadeRemaining <= 0) promoteUpcoming() else publishBlend(playing = true)
        return mixed to count
    }

    /** Reads the playing deck, preserving a tempo buffer inherited from its incoming handoff. */
    private fun readCurrent(track: Track): FloatArray? {
        // An eased-out stretch whose held audio has all been played: back on the decoder itself.
        // Left in place, the stretcher went on windowing the track at 1x until it ended — never
        // quite transparent, and heard as the tempo and the pitch never coming back.
        if (track.tempoDrained && track.tempo?.available == 0) {
            track.tempo = null
            track.tempoDrained = false
        }
        val tempo = track.tempo ?: run {
            val block = readSource(track)
            currentReadCount = if (block == null) 0 else sourceReadCount
            return block
        }
        while (tempo.available == 0) {
            val source = readSource(track) ?: run {
                currentReadCount = 0
                return null
            }
            tempo.speed = track.tempoRate.toFloat()
            tempo.push(source, sourceReadCount)
        }
        val block = tempo.take(DEFAULT_TEMPO_OUTPUT_SAMPLES)
        currentReadCount = tempo.outputCount
        advanceTempoEase(track, currentReadCount / sink.format.channels.coerceAtLeast(1))
        if (track.tempoRate <= 1.0 && !track.tempoDrained) {
            // Nothing more is pushed from here; what the stretcher holds is queued unstretched and
            // the decoder takes over once it has played.
            tempo.finish()
            track.tempoDrained = true
        }
        return block
    }

    /** Supplies exactly one outgoing block of the incoming deck, retaining any stretched surplus. */
    private fun readIncoming(track: Track, wanted: Int): FloatArray? {
        // Always through a buffer, even unstretched. Read raw, the incoming deck gave one decoder
        // block per outgoing block whatever its size: a shorter one (Opus hands over 20ms at a
        // time) left the rest of the mix block silent, and a longer one had its end cut off — the
        // crackle all through an Automix blend. At 1x the buffer is a plain FIFO, and once the
        // track is promoted [readCurrent] drains it and goes back to the decoder.
        val tempo = track.tempo ?: DesktopTempoBuffer(
            sink.format.channels.coerceAtLeast(1),
            sink.format.sampleRate,
        ).also { track.tempo = it }
        while (tempo.available < wanted) {
            val source = readSource(track) ?: break
            tempo.speed = track.tempoRate.toFloat()
            tempo.push(source, sourceReadCount)
        }
        if (tempo.available == 0) {
            incomingReadCount = 0
            return null
        }
        val block = tempo.take(wanted)
        incomingReadCount = tempo.outputCount
        return block
    }

    /** How much of the array [readSource] returned is this block. */
    private var sourceReadCount = 0

    /**
     * The next block of [track], at the output stream's rate.
     *
     * The stream is opened at the rate of the track that started it, and a decoder hands over its
     * file's own rate. Mixed in unconverted — the incoming track of every Automix blend whose rate
     * differed — 48 kHz audio on a 44.1 kHz stream plays 8% slow and a semitone and a half flat,
     * and stayed that way for the rest of the track, until Next reopened the stream at its rate.
     * Android never meets it: each of its players has its own output.
     */
    private fun readSource(track: Track): FloatArray? {
        val block = track.decoder.readSamples() ?: return null
        val count = track.decoder.sampleCount
        val from = track.decoder.outputFormat.sampleRate
        val to = sink.format.sampleRate
        if (from <= 0 || to <= 0 || from == to) {
            track.converter = null
            sourceReadCount = count
            return block
        }
        val converter = track.converter?.takeIf { it.fromRate == from && it.toRate == to }
            ?: DesktopRateConverter(from, to, sink.format.channels.coerceAtLeast(1)).also {
                DesktopTrackLog.log("converting '${track.song.title}' from $from Hz to the stream's $to Hz")
                track.converter = it
            }
        val converted = converter.process(block, count)
        sourceReadCount = converter.outputCount
        return converted
    }

    /** Eases a promoted beatmatch stretch back by at most 0.75% on each beat. */
    private fun advanceTempoEase(track: Track, outputFrames: Int) {
        if (track.tempoEaseStep <= 0.0 || outputFrames <= 0) return
        track.tempoOutputFrames += outputFrames
        while (track.tempoOutputFrames >= track.tempoEaseNextFrame && track.tempoRate > 1.0) {
            track.tempoRate = (track.tempoRate - track.tempoEaseStep).coerceAtLeast(1.0)
            track.tempoEaseNextFrame += track.tempoEaseEveryFrames
        }
        if (track.tempoRate <= 1.0001) track.tempoRate = 1.0
    }

    /** Widens one track's samples, unless the audio is Dolby Atmos. */
    private fun widen(widener: DesktopSpatialAudio?, track: Track, samples: FloatArray, count: Int) {
        val processor = widener ?: return
        processor.enabled = spatialEnabled && !track.stream.isDolbyAtmos
        processor.process(samples, count)
    }

    /** The listener's own tuning, applied to one side of the mix. */
    private fun equalise(processor: DesktopEqualizer, samples: FloatArray, count: Int) {
        processor.setTuning(equalizerEnabled, equalizerCurve, equalizerBalance)
        processor.process(samples, count)
    }

    private fun stretch(samples: FloatArray, count: Int): Pair<FloatArray, Int> {
        val processor = speedProcessor ?: return samples to count
        processor.speed = playbackSpeed
        val out = processor.process(samples, count)
        return out to processor.outputCount
    }

    /** End of a track. */
    private fun finishTrack(track: Track) {
        if (upcoming != null) {
            promoteUpcoming()
            return
        }
        track.finished = true
        sink.drain()
        current = null
        track.decoder.close()
        _state.update {
            it.copy(isPlaying = false, positionMs = it.durationMs, positionSampledAtNanos = System.nanoTime())
        }
        onEnded()
    }

    private fun promoteUpcoming() {
        val incoming = upcoming ?: return
        val plan = activePlan
        activePlan = null
        transitionEcho = null
        outgoingFilter.open()
        incomingFilter.open()
        current?.decoder?.close()
        current = incoming
        if (incoming.tempo != null && incoming.tempoRate > 1.0) {
            val steps = ceil((incoming.tempoRate - 1.0) / TEMPO_STEP_PER_BEAT).toInt().coerceAtLeast(1)
            incoming.tempoEaseStep = (incoming.tempoRate - 1.0) / steps
            incoming.tempoEaseEveryFrames = (
                (plan?.beatSeconds ?: 0.0).takeIf { it > 0.0 } ?: EASE_FALLBACK_SECONDS
                ).times(sink.format.sampleRate).toLong().coerceAtLeast(1L)
            incoming.tempoEaseNextFrame = incoming.tempoOutputFrames + incoming.tempoEaseEveryFrames
        }
        // The equalisers swap with the tracks they belong to. The incoming one has been filtering
        // this track for the whole crossfade, and its sections — a 60 Hz shelf above all — are
        // ringing with that audio; handing the track over to the other one instead would hand it
        // the outgoing track's history at the seam.
        val promoted = equalizerIncoming
        equalizerIncoming = equalizer
        equalizer = promoted
        // Whichever is now idle starts the next incoming track clean.
        equalizerIncoming.reset()
        upcoming = null
        nextSong = null
        fadeRemaining = 0
        speedProcessor?.reset()
        // Where the incoming track really is, not where it starts. Published as
        // its start left the scrubber and the lyrics a whole fade behind the
        // audio, which only a manual seek could put right.
        // Less what is still queued in the sink: [playhead] restarts from the played-frame count
        // here, so the queued audio — this track's — is counted again as it plays out.
        val handoverUs = incoming.startUs + incomingElapsedUs() - queuedSourceUs(deckRate(incoming))
        resetPlayhead(incoming, handoverUs)
        DesktopTrackLog.log(
            "transition complete: '${incoming.song.title}' resumes at " +
                "${"%.1f".format(handoverUs / 1_000_000.0)}s " +
                "(cued ${"%.1f".format(incoming.startUs / 1_000_000.0)}s " +
                "+ ${"%.1f".format(incomingElapsedUs() / 1_000_000.0)}s blended)",
        )
        publishTrack(incoming, isPlaying = !paused, positionUs = handoverUs)
        incomingSourceFrames = 0.0
        val announced = displaySwitched
        endBlend()
        if (!announced) onCrossfaded(incoming.song)
    }

    /**
     * Moves the player onto the incoming song halfway through the blend: its title, its
     * artwork, its position — and the app's queue, through [onCrossfaded]. The audio is
     * untouched and keeps blending to the end; [promoteUpcoming] then only retires the
     * outgoing decoder.
     */
    private fun switchDisplay(incoming: Track) {
        synchronized(blendLock) { displaySwitched = true }
        DesktopTrackLog.log("blend midpoint: showing '${incoming.song.title}'")
        publishTrack(incoming, isPlaying = !paused, positionUs = incomingHeardUs(incoming))
        onCrossfaded(incoming.song)
    }

    /**
     * Clears everything a blend leaves behind — the scrubber's beat, the switched display — and
     * carries out a [prepareNext] that arrived while it ran. Called however the blend ended.
     */
    private fun endBlend() {
        DesktopPlayerSettings.smartMixBlend.value = null
        var had = false
        var next: Song? = null
        synchronized(blendLock) {
            displaySwitched = false
            had = hasDeferredNext
            next = deferredNext
            hasDeferredNext = false
            deferredNext = null
        }
        if (had) prepareNext(next)
    }

    /** Source time of audio written to the sink but not yet heard. */
    private fun queuedSourceUs(deckRate: Double = 1.0): Long {
        val rate = sink.format.sampleRate
        if (rate <= 0) return 0L
        val queued = (framesWritten - sink.framesPlayed()).coerceAtLeast(0L)
        return (queued * 1_000_000L / rate * playbackSpeed * deckRate).toLong()
    }

    /** Where in the incoming track the listener is right now, mid-blend. */
    private fun incomingHeardUs(incoming: Track): Long =
        (incoming.startUs + incomingElapsedUs() - queuedSourceUs(deckRate(incoming))).coerceAtLeast(incoming.startUs)

    /**
     * Tells the shared scrubber where the blend's beats fall — see Android's
     * `CrossfadeController.publishBlend`, whose rules this follows: the grid of whichever song is
     * the louder, placed against what is heard rather than what is decoded, and only re-sent when
     * it has moved further than a tick's jitter.
     */
    private fun publishBlend(playing: Boolean) {
        if (!automixEnabled) return
        val track = current ?: return
        val incoming = upcoming ?: return
        val last = DesktopPlayerSettings.smartMixBlend.value
        if (last != null && !playing && !last.playing) return
        val listenerSpeed = playbackSpeed.toDouble().takeIf { it > 0.0 } ?: 1.0
        val outgoingHeardUs = _state.value.let { state ->
            if (state.song?.videoId == track.song.videoId) state.positionMs * 1_000 else null
        } ?: outgoingHeardUs()
        val sides = listOf(
            Triple(analyzer.analysisFor(track.song.videoId), outgoingHeardUs, listenerSpeed * track.tempoRate),
            Triple(analyzer.analysisFor(incoming.song.videoId), incomingHeardUs(incoming), listenerSpeed * incoming.tempoRate),
        ).let { if (displaySwitched) it.reversed() else it }
        val side = sides.firstOrNull { (analysis, _, _) -> analysis.beatInterval > 0.0 || analysis.bpm > 0.0 }
        var beatMs = 0f
        var anchor = 0L
        if (side != null) {
            val (analysis, heardUs, speed) = side
            val beat = if (analysis.beatInterval > 0.0) analysis.beatInterval else 60.0 / analysis.bpm
            val sinceBeat = ((heardUs / 1_000_000.0 - analysis.firstBeat) % beat + beat) % beat
            beatMs = (beat * 1000.0 / speed).toFloat()
            anchor = System.nanoTime() - (sinceBeat / speed * 1e9).toLong()
        }
        if (last != null && last.playing == playing && sameGrid(last, beatMs, anchor)) return
        DesktopPlayerSettings.smartMixBlend.value = MixBlend(beatMs = beatMs, beatAnchorNanos = anchor, playing = playing)
    }

    /** The outgoing track's heard position, from the sink's clock — [publishPosition]'s sum. */
    private fun outgoingHeardUs(): Long = playhead.at(sink.framesPlayed())

    private fun sameGrid(last: MixBlend, beatMs: Float, anchor: Long): Boolean {
        if (beatMs <= 0f || last.beatMs <= 0f) return beatMs <= 0f && last.beatMs <= 0f
        if (kotlin.math.abs(beatMs - last.beatMs) > beatMs * 0.005f) return false
        val beatNanos = (beatMs * 1_000_000.0).toLong().coerceAtLeast(1L)
        val offset = Math.floorMod(anchor - last.beatAnchorNanos, beatNanos)
        return minOf(offset, beatNanos - offset) <= ANCHOR_TOLERANCE_NANOS
    }

    private fun drainCommands() {
        while (true) {
            when (val command = commands.poll() ?: return) {
                is Command.Start -> startTrack(command.track, command.playWhenReady)
                is Command.ReplaceCurrent -> replaceCurrent(command.track, command.playWhenReady)
                is Command.Upcoming -> {
                    upcoming?.decoder?.close()
                    upcoming = command.track
                }
                is Command.ReplaceUpcoming -> {
                    val track = command.track
                    if (upcoming?.song?.videoId == track.song.videoId && fadeRemaining <= 0 && !displaySwitched) {
                        DesktopTrackLog.log(
                            "incoming '${track.song.title}' upgraded to " +
                                "${track.stream.format.summary.ifBlank { "another rendition" }} before its transition",
                        )
                        upcoming?.decoder?.close()
                        upcoming = track
                        settleIncomingSearch(track.song)
                    } else {
                        // The blend began while it was opening: it is heard on the playing deck instead.
                        track.decoder.close()
                        playingUpgrade(track.song, track.stream)
                    }
                }
                is Command.SeekTo -> performSeek(command.millis)
                Command.Reconfigure -> reconfigureSink()
                Command.ClearUpcoming -> {
                    upcoming?.decoder?.close()
                    upcoming = null
                    val wasBlending = fadeRemaining > 0
                    fadeRemaining = 0
                    if (wasBlending) endBlend()
                }
                Command.Flush -> {
                    current?.decoder?.close()
                    current = null
                    upcoming?.decoder?.close()
                    upcoming = null
                    fadeRemaining = 0
                    sink.flush()
                    framesWritten = sink.framesPlayed()
                    endBlend()
                }
            }
        }
    }

    /**
     * Installs a better rendition without treating it as a new queue item.
     *
     * The old full [Command.Flush] path also closed [upcoming]. Analysis is keyed by song id, so
     * Stats for nerds still said both tracks were measured even though the incoming decoder had
     * vanished; with no incoming deck there could be neither a seekbar marker nor an Automix.
     */
    private fun replaceCurrent(track: Track, playWhenReady: Boolean) {
        // An upgrade that arrives after the handoff has begun is no longer worth interrupting the
        // audible transition. Keep the running pair intact and retire the unused decoder.
        if (fadeRemaining > 0 || displaySwitched) {
            track.decoder.close()
            return
        }
        sink.flush()
        framesWritten = sink.framesPlayed()
        activePlan = null
        transitionEcho = null
        outgoingFilter.open()
        incomingFilter.open()
        startTrack(track, playWhenReady)
    }

    private fun startTrack(track: Track, playWhenReady: Boolean) {
        // Never the one being started: a restart of the current track would otherwise close the
        // decoder it is about to read from.
        if (current !== track) current?.decoder?.close()
        current = track
        fadeRemaining = 0

        val decoded = track.decoder.outputFormat
        // Only renegotiate when the device would actually have to change.
        val output = if (
            !sink.isOpen ||
            !sink.isUsingSelection(DesktopAudioDevices.selected.value) ||
            sink.format.sampleRate != decoded.sampleRate ||
            sink.format.channels != decoded.channels
        ) {
            sink.open(decoded.copy(bytesPerSample = precisionBytes(), isFloat = preferFloat))
        } else {
            Result.success(sink.format)
        }
        output.onFailure { failure ->
            paused = true
            DesktopTrackLog.log("audio output failed: ${failure.message}")
            _state.update { it.copy(isPlaying = false, error = "No audio output: ${failure.message}") }
            return
        }
        sink.gain = volume
        framesWritten = sink.framesPlayed()
        speedProcessor = DesktopAudioSpeed(sink.format.channels, sink.format.sampleRate)
        spatial = DesktopSpatialAudio(sink.format.channels, sink.format.sampleRate)
        spatialIncoming = DesktopSpatialAudio(sink.format.channels, sink.format.sampleRate)
        equalizer.configure(sink.format.channels, sink.format.sampleRate)
        equalizerIncoming.configure(sink.format.channels, sink.format.sampleRate)
        silence = DesktopSilenceSkipper(sink.format.channels, sink.format.sampleRate)
            .apply { enabled = skipSilenceEnabled }
        outgoingFilter.configure(sink.format.channels, sink.format.sampleRate)
        incomingFilter.configure(sink.format.channels, sink.format.sampleRate)
        resetPlayhead(track, track.startUs)
        paused = !playWhenReady
        publishTrack(track, isPlaying = playWhenReady)
    }

    /** Reopens the output on the current decoder, keeping the track playing. */
    private fun reconfigureSink() {
        val track = current ?: return
        val decoded = track.decoder.outputFormat
        // Where the next block written carries on from: what was queued on the old line is dropped
        // with it, and the decoder is already past it.
        val positionUs = playhead.peek(framesWritten)
        sink.open(decoded.copy(bytesPerSample = precisionBytes(), isFloat = preferFloat))
            .onSuccess {
                sink.gain = volume
                framesWritten = sink.framesPlayed()
                buildChain()
                // A reopened line counts frames from zero again, so the clock has to be rebased
                // onto wherever the track had got to.
                resetPlayhead(track, positionUs)
            }
            .onFailure { failure ->
                _state.update { it.copy(error = "No audio output: ${failure.message}") }
            }
    }

    private fun buildChain() {
        speedProcessor = DesktopAudioSpeed(sink.format.channels, sink.format.sampleRate)
        spatial = DesktopSpatialAudio(sink.format.channels, sink.format.sampleRate)
        spatialIncoming = DesktopSpatialAudio(sink.format.channels, sink.format.sampleRate)
        equalizer.configure(sink.format.channels, sink.format.sampleRate)
        equalizerIncoming.configure(sink.format.channels, sink.format.sampleRate)
        silence = DesktopSilenceSkipper(sink.format.channels, sink.format.sampleRate)
            .apply { enabled = skipSilenceEnabled }
        outgoingFilter.configure(sink.format.channels, sink.format.sampleRate)
        incomingFilter.configure(sink.format.channels, sink.format.sampleRate)
    }

    /** Asks for a track to be analysed, if Automix is on and there is anything to analyse. */
    private fun requestAnalysis(track: Track) {
        if (!automixEnabled) return
        val seconds = (track.decoder.durationUs ?: 0L) / 1_000_000.0
        val known = seconds.takeIf { it > 0 }
            ?: (track.song.durationText.durationSeconds())
        analyzer.request(track.song, track.stream, known)
    }

    /** Measures the pair around the playhead — the playing track first, then the one after it. */
    private fun requestAnalysisAround(track: Track) {
        if (!automixEnabled) return
        val next = upcoming
        // A track held back from read-ahead has no decoder yet, but is measured all the same.
        val held = if (next == null) nextSong else null
        if (track.song.isVideoOrigin || next?.song?.isVideoOrigin == true || held?.isVideoOrigin == true) return
        requestAnalysis(track)
        next?.let(::requestAnalysis)
        held?.let { analyzer.request(it, nextLocalStream, it.durationText.durationSeconds()) }
    }

    private fun performSeek(millis: Long) {
        // Past the midpoint the listener is looking at — and seeking in — the incoming song, so
        // the blend is finished on the spot and the seek lands on that one.
        if (displaySwitched && upcoming != null) promoteUpcoming()
        val track = current ?: return
        val landed = track.decoder.seek(millis * 1_000)
        // TEMP seek diagnostics.
        DesktopTrackLog.log("seek performed: ${millis}ms on '${track.song.title}' ${if (landed) "ok" else "FAILED"}")
        // The stretcher still holds audio from before the seek, which would play first; and a
        // listener who has moved the playhead is past the handoff, so the ease ends here — as it
        // does on Android.
        track.dropTempo()
        sink.flush()
        framesWritten = sink.framesPlayed()
        speedProcessor?.reset()
        spatial?.reset()
        spatialIncoming?.reset()
        // A seek is not a continuous signal, so the filters are cleared rather than left ringing
        // with the audio from before it — a strong band otherwise rings that state out over the
        // first moments of the new position.
        equalizer.reset()
        equalizerIncoming.reset()
        silence?.reset()
        resetPlayhead(track, millis * 1_000)
        // Cleared by [play] once the first of the new position's audio is on its way out.
        _state.update {
            it.copy(
                positionMs = millis,
                positionSampledAtNanos = System.nanoTime(),
                seeks = it.seeks + 1,
                awaitingAudio = true,
            )
        }
    }

    /** The track a second look is running for, started as it began playing. */
    @Volatile
    private var searchingBetterFor: String? = null

    /**
     * The incoming track whose raced resolve is still waiting on a better copy — see
     * [upgradeIncoming]. Separate from [searchingBetterFor] because both can be running at once:
     * the playing track's search and the next one's.
     */
    @Volatile
    private var incomingSearchingFor: String? = null

    /** How much of the incoming track the blend has already played, in source time. */
    private fun incomingElapsedUs(): Long =
        (incomingSourceFrames * 1_000_000.0 / sink.format.sampleRate.coerceAtLeast(1)).toLong()

    private fun publishTrack(track: Track, isPlaying: Boolean, positionUs: Long = track.startUs) {
        _state.value = DesktopPlaybackState(
            song = track.song,
            isPlaying = isPlaying,
            volume = volume,
            isLoading = false,
            positionMs = positionUs / 1_000,
            positionSampledAtNanos = System.nanoTime(),
            // A track opened, or the playing one replaced, starts the playhead somewhere new.
            seeks = _state.value.seeks + 1,
            durationMs = (track.decoder.durationUs ?: 0L) / 1_000,
            // What the decoder is actually being fed, falling back to what the source promised only
            // until something has been measured.
            streamFormat = track.decoder.measuredFormat ?: track.stream.format,
            streamSourceId = track.stream.sourceId,
            searchingBetter = track.song.videoId.let { it == searchingBetterFor || it == incomingSearchingFor },
            smartAnalysis = analysisStatus(track),
        )
    }

    /** Position from what the device has actually rendered, not from what has been decoded. */
    private fun publishPosition(track: Track) {
        val format = sink.format
        if (format.sampleRate == 0) return
        if (displaySwitched) {
            val incoming = upcoming ?: return
            val positionMs = incomingHeardUs(incoming) / 1_000
            val sampledAt = System.nanoTime()
            if (_state.value.positionMs / 250 == positionMs / 250) return
            _state.update {
                it.copy(
                    positionMs = positionMs,
                    positionSampledAtNanos = sampledAt,
                    isPlaying = !paused,
                    mixing = isSmartMixInProgress(),
                )
            }
            return
        }
        val positionMs = (playhead.at(sink.framesPlayed()) / 1_000).coerceAtLeast(0)
        // Taken with the reading, here on the audio thread: see DesktopPlaybackState.
        val sampledAt = System.nanoTime()
        if (_state.value.song?.videoId != track.song.videoId) return
        requestAnalysisAround(track)
        val status = analysisStatus(track)
        // Published when the clock moves *or* when Automix's answer does.
        val moved = _state.value.positionMs / 250 != positionMs / 250
        if (!moved && _state.value.smartAnalysis == status) return
        _state.update {
            it.copy(
                positionMs = positionMs,
                positionSampledAtNanos = sampledAt,
                isPlaying = !paused,
                smartAnalysis = status,
                mixing = isSmartMixInProgress(),
            )
        }

        maybeStartCrossfade(track, positionMs)
    }

    /**
     * Matches Android's Automix signal: a plain equal-power fallback is still a crossfade, but it
     * does not light the Automix animation unless analysis changed the style, cue or tempo.
     */
    private fun isSmartMixInProgress(): Boolean {
        val plan = activePlan ?: return false
        return automixEnabled && fadeRemaining > 0 && (
            plan.transitionStyle == TransitionStyle.DJ_BLEND ||
                plan.transitionStyle == TransitionStyle.DJ_FILTER ||
                plan.incomingCueTime > 0.0 ||
                plan.incomingPlaybackRate != 1.0
            )
    }

    /** Both halves of the next transition, for the player's Automix line. */
    private fun analysisStatus(track: Track): SmartAnalysis = SmartAnalysis(
        current = analyzer.stateFor(track.song.videoId),
        next = analyzer.stateFor(upcoming?.song?.videoId ?: nextSong?.videoId.orEmpty()),
    )

    private fun maybeStartCrossfade(track: Track, positionMs: Long) {
        val incoming = upcoming
        if (incoming == null) {
            // A next track not open yet — held back from read-ahead, or still resolving — is
            // still planned for on its catalogue length, so the marker shows from the start of the
            // song rather than only once the incoming deck is open.
            val held = nextSong
            val duration = (track.decoder.durationUs ?: 0L) / 1_000
            if (held == null || duration <= 0) {
                _state.update { it.copy(transitionWindow = null) }
                return
            }
            val nextMs = (held.durationText.durationSeconds() * 1_000).toLong()
            val plan = transitionPlan(track, held, nextMs, positionMs, duration)
            publishTransitionWindow(track, plan, duration)
            armAddonUpcoming(held, plan, positionMs, duration)
            return
        }
        if (fadeRemaining > 0) return
        val duration = (track.decoder.durationUs ?: return) / 1_000
        if (duration <= 0) return

        val plan = transitionPlan(track, incoming.song, (incoming.decoder.durationUs ?: 0L) / 1_000, positionMs, duration)
        publishTransitionWindow(track, plan, duration)

        if (!plan.shouldStart || plan.blocked) return

        val seconds = plan.fadeSeconds.takeIf { it > 0 } ?: return
        // A planned transition says where the incoming track should be entered, which is the whole
        // difference between Automix and a crossfade.
        if (plan.incomingCueTime > 0) {
            val cueUs = (plan.incomingCueTime * 1_000_000).toLong()
            incoming.decoder.seek(cueUs)
            // The track now begins here, and every position reported for it is measured from it.
            // Left at zero, the scrubber and the lyrics ran the whole cue behind the audio for the
            // rest of the track — which only a manual seek could put right.
            incoming.startUs = cueUs
        }
        DesktopTrackLog.log(
            "transition into '${incoming.song.title}': ${"%.1f".format(seconds)}s" +
                ", ${plan.transitionStyle}" +
                (if (plan.transitionBeats > 0) ", ${plan.transitionBeats} beats" else "") +
                (if (plan.incomingCueTime > 0) ", cued at ${"%.1f".format(plan.incomingCueTime)}s" else ""),
        )
        fadeTotal = (seconds * sink.format.sampleRate * sink.format.channels).toInt()
        val echoTail = (echoTailSeconds(plan.echoSeconds) * sink.format.sampleRate * sink.format.channels).toInt()
        transitionTotal = fadeTotal + echoTail
        fadeRemaining = transitionTotal
        incomingSourceFrames = 0.0
        activePlan = plan
        incoming.tempo = if (plan.incomingPlaybackRate > 1.0001) {
            DesktopTempoBuffer(sink.format.channels, sink.format.sampleRate).also {
                it.speed = plan.incomingPlaybackRate.toFloat()
            }
        } else null
        incoming.tempoRate = plan.incomingPlaybackRate.coerceAtLeast(1.0)
        transitionEcho = if (plan.echoSeconds > 0.0) {
            DesktopTransitionEcho(sink.format.sampleRate, sink.format.channels).also { it.configure(plan.echoSeconds) }
        } else null
        outgoingFilter.flush()
        incomingFilter.flush()
    }

    /** The marker on the scrubber, showing where the mix will happen before it happens. */
    private fun publishTransitionWindow(track: Track, plan: TransitionPlan, duration: Long) {
        val status = analysisStatus(track)
        val markable = !plan.blocked &&
            plan.markerVisible &&
            status.current == TrackAnalysisState.ANALYSED &&
            status.next in MEASURED_ENOUGH_TO_ENTER_ON
        _state.update {
            it.copy(
                transitionWindow = if (markable) {
                    TransitionWindow(
                        start = (plan.transitionStart * 1_000.0 / duration).toFloat().coerceIn(0f, 1f),
                        end = (plan.transitionEnd * 1_000.0 / duration).toFloat().coerceIn(0f, 1f),
                    )
                } else {
                    null
                },
            )
        }
    }

    /**
     * Resolves the held-back addon track once the transition into it is [ADDON_ARM_LEAD_MS] away —
     * Android's arming, with a longer lead because an addon is slower to answer than a cache.
     *
     * The plan is made on the next song's catalogue length, since it has no decoder yet; the
     * analysis it reads is already there, measured on YouTube Opus. A track that would not blend
     * is armed the same distance before its end, so the hand-over is still gapless.
     */
    private fun armAddonUpcoming(song: Song, plan: TransitionPlan, positionMs: Long, duration: Long) {
        if (awaitingArm.get() !== song) return
        val startMs = if (!plan.blocked && plan.fadeSeconds > 0) {
            (plan.transitionStart * 1_000).toLong()
        } else {
            duration - transitionSecondsFor(song) * 1_000L
        }
        if (startMs - positionMs > ADDON_ARM_LEAD_MS) return
        // Whichever of this thread and a newer [prepareNext] gets there first owns it.
        if (!awaitingArm.compareAndSet(song, null)) return
        DesktopTrackLog.log(
            "arming '${song.title}' ${"%.1f".format((startMs - positionMs).coerceAtLeast(0) / 1_000.0)}s " +
                "before its transition",
        )
        resolveUpcoming(song, race = true)
    }

    /** What the shared planner makes of this pair. */
    private fun transitionPlan(
        track: Track,
        next: Song,
        nextDurationMs: Long,
        positionMs: Long,
        durationMs: Long,
    ): TransitionPlan {
        val manualSeconds = crossfadeSeconds
        val smart = automixEnabled
        if (!smart && manualSeconds <= 0) return TransitionPlan()
        return planTransition(
            analysis = analyzer.analysisFor(track.song.videoId),
            nextAnalysis = analyzer.analysisFor(next.videoId),
            currentTrack = track.song.transitionInfo(durationMs),
            nextTrack = next.transitionInfo(nextDurationMs),
            currentTime = positionMs / 1_000.0,
            duration = durationMs / 1_000.0,
            fadeSeconds = if (manualSeconds > 0) manualSeconds.toDouble() else AUTOMIX_FALLBACK_SECONDS.toDouble(),
            mode = if (smart) CrossfadeMode.SMART else CrossfadeMode.STANDARD,
            advanced = smart,
        )
    }

    private fun closeEverything() {
        current?.decoder?.close()
        upcoming?.decoder?.close()
        current = null
        upcoming = null
        sink.close()
    }

    // ---- output precision ------------------------------------------------

    @Volatile private var preferFloat = false
    @Volatile private var spatialEnabled = false

    @Volatile private var equalizerEnabled = false

    @Volatile private var equalizerCurve: EqCurve = EqCurve.FLAT

    @Volatile private var equalizerBalance = 0f
    @Volatile private var skipSilenceEnabled = false
    @Volatile private var automixPerformance = AutomixPerformanceMode.BALANCED

    /** Android's "Automix performance": how much CPU background analysis may use. */
    fun setAutomixPerformance(mode: AutomixPerformanceMode) {
        automixPerformance = mode
    }

    /** Android's "Spatial audio". */
    /**
     * Aims the equaliser.
     *
     * The curve is rendered by the caller because working out the make-up attenuation walks the
     * whole response ([EqCurve]), and the audio thread is the one place that must not do that.
     */
    fun setEqualizer(enabled: Boolean, curve: EqCurve, balance: Float) {
        equalizerCurve = curve
        equalizerBalance = balance.coerceIn(-1f, 1f)
        equalizerEnabled = enabled
    }

    fun setSpatialAudio(enabled: Boolean) {
        spatialEnabled = enabled
    }

    /** Android's "Skip silence", on the same terms. */
    fun setSkipSilence(enabled: Boolean) {
        skipSilenceEnabled = enabled
        if (!enabled) silence?.reset()
    }

    private fun precisionBytes(): Int = if (preferFloat) 4 else 2

    /** Android's "Output precision", which is two rungs there and two here. */
    fun setOutputPrecision(mode: String) {
        val wanted = mode.uppercase() == "FLOAT_32"
        if (wanted == preferFloat) return
        preferFloat = wanted
        commands += Command.Reconfigure
    }

    /**
     * The whole signal chain as it stands, for the pipeline readout: what arrived, what decoded it,
     * whether it is being resampled, what is processing it, and what it is being written to.
     */
    internal fun pipeline(): DesktopAudioPipeline {
        val track = current
        val decoded = track?.decoder?.outputFormat
        val out = sink.format
        return DesktopAudioPipeline(
            sourceFormat = track?.stream?.format,
            decoderName = track?.stream?.format?.codec,
            decodedSampleRateHz = decoded?.sampleRate,
            decodedChannels = decoded?.channels,
            outputSampleRateHz = out.sampleRate.takeIf { sink.isOpen },
            outputChannels = out.channels.takeIf { sink.isOpen },
            outputIsFloat = out.isFloat.takeIf { sink.isOpen },
            outputBytesPerSample = out.bytesPerSample.takeIf { sink.isOpen },
            deviceName = sink.deviceName,
            bufferBytes = sink.bufferBytes,
            equalizerEnabled = equalizerEnabled,
            skipSilence = skipSilenceEnabled,
        )
    }

    /** What the device actually accepted, for the settings screen to report. */
    fun outputSummary(): String = if (!sink.isOpen) "Not started" else with(sink.format) {
        val depth = if (isFloat) "32-bit float" else "${bytesPerSample * 8}-bit"
        "$depth · ${sampleRate / 1000.0} kHz · ${if (channels == 2) "stereo" else "$channels ch"}"
    }

    companion object {
        /** How many times a lossy substitute is asked to be beaten before the question is closed. */
        private const val LOSSLESS_FOLLOW_UPS = 2

        /** Avoid a decoder seek for sub-tick drift while an upgraded rendition is opening. */
        private const val UPGRADE_RESEEK_THRESHOLD_MS = 250L

        /** Enough of a measurement on the incoming track to cue into it. */
        val MEASURED_ENOUGH_TO_ENTER_ON = setOf(
            TrackAnalysisState.ANALYSED,
            TrackAnalysisState.REFINING,
        )

        /** Where in a blend the player moves to the incoming song: halfway, as on Android. */
        private const val DISPLAY_SWITCH_AT = 0.5f

        /** Jitter tolerated in a republished beat anchor; see [publishBlend]. */
        private const val ANCHOR_TOLERANCE_NANOS = 25_000_000L

        private const val MIX_END_PADDING_FRAMES = 2_048
        private const val DEFAULT_TEMPO_OUTPUT_SAMPLES = 4_096
        private const val TEMPO_STEP_PER_BEAT = 0.0075
        private const val EASE_FALLBACK_SECONDS = 0.5

        /**
         * How far ahead of its transition a held-back addon track is resolved and opened. Android
         * arms 4s ahead from a disk cache; an addon has a network round trip or two and a fresh
         * stream to open, and an incoming track that is not ready by the transition misses it.
         */
        private const val ADDON_ARM_LEAD_MS = 15_000L

        /** How long a better copy waits for the blend into its track to finish: up to a minute. */
        private const val BLEND_WAIT_POLLS = 300
        private const val BLEND_WAIT_POLL_MS = 200L

        const val MAX_CROSSFADE_SECONDS = 12
        const val AUTOMIX_FALLBACK_SECONDS = 6
    }
}
