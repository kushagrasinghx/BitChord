package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.playback.EqCurve
import com.music.bitchord.playback.TransitionFilter
import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.TransitionPlan
import com.music.bitchord.playback.smart.TransitionTrackInfo
import com.music.bitchord.playback.smart.planTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

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
    private var retryingSongId: String? = null

    /** Owned by the audio thread once handed over; never touched from outside. */
    private class Track(
        val song: Song,
        val decoder: DesktopAudioDecoder,
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
    }

    private sealed interface Command {
        class Start(val track: Track, val playWhenReady: Boolean) : Command
        class Upcoming(val track: Track) : Command
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

    fun load(song: Song, playWhenReady: Boolean = true) {
        retryingSongId = null
        loadInternal(song, playWhenReady)
    }

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
        searchingBetter = false
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
                            searchingBetter = live.pendingSubstitute != null
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
            ).map {
                if (startAtMs > 0) decoder.seek(startAtMs * 1_000)
                Track(song, decoder, stream, startAtMs * 1_000)
            }.onFailure { decoder.close() }
        }

    /**
     * A source that cannot actually be decoded is struck off and the track asked for again, once.
     */
    private fun retryAfterFailure(song: Song, playWhenReady: Boolean, stream: DesktopStream, failure: Throwable) {
        if (retryingSongId != song.videoId) {
            retryingSongId = song.videoId
            DesktopTrackLog.log("could not decode '${song.title}' from ${DesktopMusicSources.sourceNameFor(stream)}: ${failure.message}")
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
                searchingBetter = false
                _state.update { it.copy(searchingBetter = false) }
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
        swapStream(song, better, current.positionMs, current.isPlaying)
        return true
    }

    /** Re-opens the current track on a different stream, carrying the playhead over. */
    private fun swapStream(song: Song, stream: DesktopStream, positionMs: Long, wasPlaying: Boolean) {
        scope.launch {
            openTrack(song, stream, positionMs).fold(
                onSuccess = { track ->
                    if (_state.value.song?.videoId != song.videoId) {
                        track.decoder.close()
                        return@fold
                    }
                    commands += Command.Flush
                    commands += Command.Start(track, playWhenReady = wasPlaying)
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
    fun reloadCurrent() {
        val current = _state.value
        val song = current.song ?: return
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
                    swapStream(song, stream, current.positionMs, current.isPlaying)
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
        nextResolveJob?.cancel()
        clearUpcoming()
        if (song == null || transitionSecondsFor(song) == 0) return
        nextSong = song
        nextResolveJob = scope.launch {
            DesktopMusicSources.resolve(song, audioQuality).fold(
                onSuccess = { stream ->
                    if (automixEnabled && !song.isVideoOrigin) {
                        analyzer.request(song, stream, song.durationText.durationSeconds())
                    }
                    openTrack(song, stream, startAtMs = 0).onSuccess { commands += Command.Upcoming(it) }
                },
                onFailure = { nextSong = null },
            )
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

    private fun clearUpcoming() {
        nextSong = null
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
    private var baseFrames = 0L
    private var fadeRemaining = 0
    private var fadeTotal = 0

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
    private var incomingSamples = 0L
    private var mixed = FloatArray(0)

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
            Thread.sleep(20)
            return
        }
        sink.resume()

        val block = track.decoder.readSamples()
        if (block == null) {
            finishTrack(track)
            return
        }
        val count = track.decoder.sampleCount
        if (count == 0) return

        val (blended, blendedCount) = blend(track, block, count)
        val quiet = silence?.also { it.enabled = skipSilenceEnabled }
        val trimmed = quiet?.process(blended, blendedCount) ?: blended
        val trimmedCount = quiet?.outputCount ?: blendedCount
        val (stretched, stretchedCount) = stretch(trimmed, trimmedCount)
        sink.write(stretched, stretchedCount)
        publishPosition(track)
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
        val other = incoming.decoder.readSamples()
        val otherCount = if (other == null) 0 else incoming.decoder.sampleCount
        // What is mixed, not what was decoded: the loop below reads at most [count] of them, and
        // counting the surplus would have the incoming track appear further along than it sounds.
        incomingSamples += minOf(otherCount, count)
        if (other != null) {
            widen(spatialIncoming, incoming, other, otherCount)
            equalise(equalizerIncoming, other, otherCount)
        }
        val channels = sink.format.channels.coerceAtLeast(1)
        val plan = activePlan
        val subBlock = TransitionFilter.GLIDE_FRAMES * channels

        var index = 0
        while (index < count) {
            val progress = 1f - (fadeRemaining.toFloat() / fadeTotal).coerceIn(0f, 1f)
            plan?.let { DesktopTransitionRide.aim(it, progress, outgoingFilter, incomingFilter) }
            outgoingFilter.advance()
            incomingFilter.advance()

            val out = kotlin.math.sqrt(1f - progress)
            val into = kotlin.math.sqrt(progress)
            val stop = minOf(count, index + subBlock)
            val span = stop - index
            while (index < stop) {
                val channel = index % channels
                val leaving = outgoingFilter.filter(channel, block[index])
                val arriving = if (index < otherCount) {
                    incomingFilter.filter(channel, other!![index])
                } else {
                    0f
                }
                mixed[index] = leaving * out + arriving * into
                index++
            }
            fadeRemaining -= span
        }
        if (fadeRemaining <= 0) promoteUpcoming()
        return mixed to count
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
        _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
        onEnded()
    }

    private fun promoteUpcoming() {
        val incoming = upcoming ?: return
        activePlan = null
        outgoingFilter.open()
        incomingFilter.open()
        current?.decoder?.close()
        current = incoming
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
        baseFrames = sink.framesPlayed()
        speedProcessor?.reset()
        // Where the incoming track really is, not where it starts. Published as
        // its start left the scrubber and the lyrics a whole fade behind the
        // audio, which only a manual seek could put right.
        val handoverUs = incoming.startUs + incomingElapsedUs()
        DesktopTrackLog.log(
            "transition complete: '${incoming.song.title}' resumes at " +
                "${"%.1f".format(handoverUs / 1_000_000.0)}s " +
                "(cued ${"%.1f".format(incoming.startUs / 1_000_000.0)}s " +
                "+ ${"%.1f".format(incomingElapsedUs() / 1_000_000.0)}s blended)",
        )
        publishTrack(incoming, isPlaying = !paused, positionUs = handoverUs)
        incomingSamples = 0L
        onCrossfaded(incoming.song)
    }

    private fun drainCommands() {
        while (true) {
            when (val command = commands.poll() ?: return) {
                is Command.Start -> startTrack(command.track, command.playWhenReady)
                is Command.Upcoming -> {
                    upcoming?.decoder?.close()
                    upcoming = command.track
                }
                is Command.SeekTo -> performSeek(command.millis)
                Command.Reconfigure -> reconfigureSink()
                Command.ClearUpcoming -> {
                    upcoming?.decoder?.close()
                    upcoming = null
                    fadeRemaining = 0
                }
                Command.Flush -> {
                    current?.decoder?.close()
                    current = null
                    upcoming?.decoder?.close()
                    upcoming = null
                    fadeRemaining = 0
                    sink.flush()
                }
            }
        }
    }

    private fun startTrack(track: Track, playWhenReady: Boolean) {
        // Never the one being started: a restart of the current track would otherwise close the
        // decoder it is about to read from.
        if (current !== track) current?.decoder?.close()
        current = track
        fadeRemaining = 0

        val decoded = track.decoder.outputFormat
        // Only renegotiate when the device would actually have to change.
        if (!sink.isOpen || sink.format.sampleRate != decoded.sampleRate || sink.format.channels != decoded.channels) {
            sink.open(decoded.copy(bytesPerSample = precisionBytes(), isFloat = preferFloat))
                .onFailure { failure ->
                    _state.update { it.copy(error = "No audio output: ${failure.message}") }
                }
        }
        sink.gain = volume
        speedProcessor = DesktopAudioSpeed(sink.format.channels, sink.format.sampleRate)
        spatial = DesktopSpatialAudio(sink.format.channels, sink.format.sampleRate)
        spatialIncoming = DesktopSpatialAudio(sink.format.channels, sink.format.sampleRate)
        equalizer.configure(sink.format.channels, sink.format.sampleRate)
        equalizerIncoming.configure(sink.format.channels, sink.format.sampleRate)
        silence = DesktopSilenceSkipper(sink.format.channels, sink.format.sampleRate)
            .apply { enabled = skipSilenceEnabled }
        outgoingFilter.configure(sink.format.channels, sink.format.sampleRate)
        incomingFilter.configure(sink.format.channels, sink.format.sampleRate)
        baseFrames = sink.framesPlayed()
        paused = !playWhenReady
        publishTrack(track, isPlaying = playWhenReady)
    }

    /** Reopens the output on the current decoder, keeping the track playing. */
    private fun reconfigureSink() {
        val track = current ?: return
        val decoded = track.decoder.outputFormat
        sink.close()
        sink.open(decoded.copy(bytesPerSample = precisionBytes(), isFloat = preferFloat))
            .onFailure { failure ->
                _state.update { it.copy(error = "No audio output: ${failure.message}") }
            }
        sink.gain = volume
        buildChain()
        // A reopened line counts frames from zero again, so the clock has to be rebased onto
        // wherever the track had got to.
        baseFrames = sink.framesPlayed()
        seekOffsetUs = _state.value.positionMs * 1_000
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
        if (track.song.isVideoOrigin || next?.song?.isVideoOrigin == true) return
        requestAnalysis(track)
        next?.let(::requestAnalysis)
    }

    private fun performSeek(millis: Long) {
        val track = current ?: return
        track.decoder.seek(millis * 1_000)
        sink.flush()
        speedProcessor?.reset()
        spatial?.reset()
        spatialIncoming?.reset()
        // A seek is not a continuous signal, so the filters are cleared rather than left ringing
        // with the audio from before it — a strong band otherwise rings that state out over the
        // first moments of the new position.
        equalizer.reset()
        equalizerIncoming.reset()
        silence?.reset()
        baseFrames = sink.framesPlayed()
        seekOffsetUs = millis * 1_000
        _state.update { it.copy(positionMs = millis) }
    }

    private var seekOffsetUs = 0L

    /** Whether a second look is running for the track now playing. */
    @Volatile
    private var searchingBetter = false

    /** How much of the incoming track the blend has already played, in source time. */
    private fun incomingElapsedUs(): Long =
        blendElapsedUs(incomingSamples, sink.format.channels, sink.format.sampleRate)

    private fun publishTrack(track: Track, isPlaying: Boolean, positionUs: Long = track.startUs) {
        seekOffsetUs = positionUs
        _state.value = DesktopPlaybackState(
            song = track.song,
            isPlaying = isPlaying,
            volume = volume,
            isLoading = false,
            positionMs = positionUs / 1_000,
            durationMs = (track.decoder.durationUs ?: 0L) / 1_000,
            // What the decoder is actually being fed, falling back to what the source promised only
            // until something has been measured.
            streamFormat = track.decoder.measuredFormat ?: track.stream.format,
            streamSourceId = track.stream.sourceId,
            searchingBetter = searchingBetter,
            smartAnalysis = analysisStatus(track),
        )
    }

    /** Position from what the device has actually rendered, not from what has been decoded. */
    private fun publishPosition(track: Track) {
        val format = sink.format
        if (format.sampleRate == 0) return
        val played = sink.framesPlayed() - baseFrames + (silence?.skippedFrames ?: 0L)
        val elapsedUs = played * 1_000_000L / format.sampleRate
        // Stretched output covers more or less source time than it occupies.
        val sourceUs = seekOffsetUs + (elapsedUs * playbackSpeed).toLong()
        val positionMs = (sourceUs / 1_000).coerceAtLeast(0)
        if (_state.value.song?.videoId != track.song.videoId) return
        requestAnalysisAround(track)
        val status = analysisStatus(track)
        // Published when the clock moves *or* when Automix's answer does.
        val moved = _state.value.positionMs / 250 != positionMs / 250
        if (!moved && _state.value.smartAnalysis == status) return
        _state.update {
            it.copy(
                positionMs = positionMs,
                isPlaying = !paused,
                smartAnalysis = status,
                mixing = fadeRemaining > 0,
            )
        }

        maybeStartCrossfade(track, positionMs)
    }

    /** Both halves of the next transition, for the player's Automix line. */
    private fun analysisStatus(track: Track): SmartAnalysis = SmartAnalysis(
        current = analyzer.stateFor(track.song.videoId),
        next = analyzer.stateFor(upcoming?.song?.videoId ?: nextSong?.videoId.orEmpty()),
    )

    private fun maybeStartCrossfade(track: Track, positionMs: Long) {
        val incoming = upcoming
        if (incoming == null) {
            _state.update { it.copy(transitionWindow = null) }
            return
        }
        if (fadeRemaining > 0) return
        val duration = (track.decoder.durationUs ?: return) / 1_000
        if (duration <= 0) return

        val plan = transitionPlan(track, incoming, positionMs, duration)

        // The marker on the scrubber, showing where the mix will happen before it happens.
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
        fadeRemaining = fadeTotal
        incomingSamples = 0L
        activePlan = plan
        outgoingFilter.flush()
        incomingFilter.flush()
    }

    /** What the shared planner makes of this pair. */
    private fun transitionPlan(
        track: Track,
        incoming: Track,
        positionMs: Long,
        durationMs: Long,
    ): TransitionPlan {
        val manualSeconds = crossfadeSeconds
        val smart = automixEnabled
        if (!smart && manualSeconds <= 0) return TransitionPlan()
        return planTransition(
            analysis = analyzer.analysisFor(track.song.videoId),
            nextAnalysis = analyzer.analysisFor(incoming.song.videoId),
            currentTrack = track.song.transitionInfo(durationMs),
            nextTrack = incoming.song.transitionInfo((incoming.decoder.durationUs ?: 0L) / 1_000),
            currentTime = positionMs / 1_000.0,
            duration = durationMs / 1_000.0,
            fadeSeconds = if (manualSeconds > 0) manualSeconds.toDouble() else AUTOMIX_FALLBACK_SECONDS.toDouble(),
            mode = if (smart) CrossfadeMode.SMART else CrossfadeMode.STANDARD,
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

    /** What the device actually accepted, for the settings screen to report. */
    fun outputSummary(): String = if (!sink.isOpen) "Not started" else with(sink.format) {
        val depth = if (isFloat) "32-bit float" else "${bytesPerSample * 8}-bit"
        "$depth · ${sampleRate / 1000.0} kHz · ${if (channels == 2) "stereo" else "$channels ch"}"
    }

    companion object {
        /** How many times a lossy substitute is asked to be beaten before the question is closed. */
        private const val LOSSLESS_FOLLOW_UPS = 2

        /** Enough of a measurement on the incoming track to cue into it. */
        val MEASURED_ENOUGH_TO_ENTER_ON = setOf(
            TrackAnalysisState.ANALYSED,
            TrackAnalysisState.REFINING,
        )

        const val MAX_CROSSFADE_SECONDS = 12
        const val AUTOMIX_FALLBACK_SECONDS = 6
    }
}
