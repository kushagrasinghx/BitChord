package com.music.bitchord.playback.vocalremover

import android.content.Context
import android.util.Log
import app.stemsep.dsp.StemMode
import app.stemsep.separation.AudioDecoder
import app.stemsep.separation.DecodedAudio
import app.stemsep.separation.LiveSeparationSession
import app.stemsep.separation.LiveStemPlayer
import app.stemsep.separation.ModelCatalog
import app.stemsep.separation.ModelDownloader
import app.stemsep.separation.ModelSpec
import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.player.VocalRemoverController
import com.music.bitchord.ui.player.VocalRemoverDebug
import com.music.bitchord.ui.player.VocalRemoverPhase
import com.music.bitchord.ui.player.VocalRemoverState
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * The service capabilities the remover drives. ExoPlayer keeps owning the
 * queue, the media session and the position clock; the remover only borrows
 * the speaker. See docs/VOCAL_REMOVER.md.
 */
interface VocalRemoverHost {
    val context: Context
    val scope: CoroutineScope
    fun currentSong(): Song?
    fun positionMs(): Long
    fun isPlaying(): Boolean

    /**
     * Forces both ExoPlayers silent (and every player built afterwards) or
     * restores them. The sync loop re-asserts this every tick because the
     * crossfade/adopt paths set volumes behind our back.
     */
    fun applyRemoverMuted(muted: Boolean)
}

/**
 * Live vocal remover for the currently playing track.
 *
 * Not a DSP effect: the vocals come out because a UVR MDX-Net ONNX model —
 * the backend ported from StemSep — separates them. The playing track's
 * stream bytes are re-fetched (or its local file used), decoded to 44.1 kHz
 * float PCM, separated **from the current play position onward** into growing
 * stem files, and a stem player sounds those while ExoPlayer — which still
 * owns focus, queue and the clock — is held muted. The capsule on the lyrics
 * screen toggles it and mixes the vocal level back.
 */
object VocalRemover : VocalRemoverController {

    private const val TAG = "VocalRemover"
    private const val SAMPLE_RATE = 44_100
    private const val SYNC_INTERVAL_MS = 250L
    private const val DRIFT_SEEK_MS = 600L
    private const val JUMP_RESTART_MS = 10_000L

    private var host: VocalRemoverHost? = null
    private var downloader: ModelDownloader? = null
    private val state = MutableStateFlow(VocalRemoverState())
    override val snapshot: StateFlow<VocalRemoverState> = state

    @Volatile
    private var session: Session? = null

    private var trackWaitJob: Job? = null

    private var restartCounter = 0

    private class Session(
        val videoId: String,
        val trackDir: File,
        val stemDir: File,
        @Volatile var streamFile: File?,
        @Volatile var decoded: DecodedAudio?,
        @Volatile var startFrame: Long,
        @Volatile var player: LiveStemPlayer?,
        @Volatile var cancelled: Boolean = false,
        var job: Job? = null,
        var syncJob: Job? = null,
    )

    // ---- public surface (UI + service) ----

    fun attach(newHost: VocalRemoverHost) {
        host = newHost
        downloader = ModelDownloader(newHost.context)
    }

    fun detach() {
        trackWaitJob?.cancel()
        stopSession()
        host = null
        downloader = null
        state.update { it.copy(enabled = false, phase = VocalRemoverPhase.OFF, fraction = 0f) }
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled == state.value.enabled) return
        state.update { it.copy(enabled = enabled, error = null) }
        if (!enabled) {
            trackWaitJob?.cancel()
            stopSession()
            state.update { it.copy(phase = VocalRemoverPhase.OFF, fraction = 0f) }
            return
        }
        val host = host ?: return
        val song = host.currentSong()
        if (song == null) {
            state.update { it.copy(phase = VocalRemoverPhase.WAITING_FOR_TRACK) }
            awaitTrack()
        } else {
            startForSong(song)
        }
    }

    /** Polls for a track while enabled-but-idle; one loop per enable. */
    private fun awaitTrack() {
        val host = host ?: return
        if (trackWaitJob?.isActive == true) return
        trackWaitJob = host.scope.launch {
            while (isActive && state.value.enabled) {
                val song = host.currentSong()
                if (song != null) {
                    startForSong(song)
                    return@launch
                }
                delay(500L)
            }
        }
    }

    override fun setVocalLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        session?.player?.setVocalLevel(clamped)
        state.update { it.copy(vocalLevel = clamped) }
    }

    fun onMediaChanged() {
        val host = host ?: return
        if (!state.value.enabled) return
        val song = host.currentSong()
        if (song == null) {
            stopSession()
            state.update { it.copy(phase = VocalRemoverPhase.WAITING_FOR_TRACK) }
            awaitTrack()
            return
        }
        if (song.videoId != session?.videoId) startForSong(song)
    }

    fun currentModelSpec(): ModelSpec {
        val url = AppSettings.vocalRemoverModelUrl.value
        return if (url.isBlank()) {
            ModelCatalog.byId(null)
        } else {
            ModelCatalog.custom(
                url = url,
                sha256 = AppSettings.vocalRemoverModelSha.value.ifBlank { null },
                sizeBytes = AppSettings.vocalRemoverModelSize.value,
            )
        }
    }

    /** Settings screen: is the active model already on disk? */
    fun isModelCached(): Boolean {
        val downloader = downloader ?: return false
        return runCatching { downloader.isCached(currentModelSpec()) }.getOrDefault(false)
    }

    /** Settings screen: fetch the active model now; [onProgress] gets 0..1. */
    suspend fun downloadModel(onProgress: (Float) -> Unit): Result<File> {
        val downloader = downloader
            ?: return Result.failure(IllegalStateException("player service not running"))
        return runCatching { downloader.ensureModel(currentModelSpec(), onProgress) }
    }

    // ---- session lifecycle ----

    private fun startForSong(song: Song) {
        val host = host ?: return
        stopSession()
        val localFile = song.localPath?.let(::File)?.takeIf { it.isFile }
        val streamUsable = localFile != null || isYouTubeId(song.videoId)
        if (!streamUsable) {
            host.applyRemoverMuted(false)
            state.update {
                it.copy(
                    phase = VocalRemoverPhase.UNSUPPORTED_SOURCE,
                    error = "This track's source cannot be re-fetched for separation.",
                )
            }
            return
        }
        val trackDir = trackDirectory(host.context, song.videoId)
        trackDir.mkdirs()
        trackDir.listFiles()
            ?.filter { it.name.startsWith("stem-") }
            ?.forEach { it.deleteRecursively() }
        val session = Session(
            videoId = song.videoId,
            trackDir = trackDir,
            stemDir = File(trackDir, "stem-" + UUID.randomUUID().toString().take(8)),
            streamFile = localFile,
            decoded = null,
            startFrame = 0,
            player = null,
        )
        session.stemDir.mkdirs()
        this.session = session
        host.applyRemoverMuted(true)
        session.job = host.scope.launch { runPipeline(session) }
    }

    /**
     * Re-anchor separation to the current position, reusing the decoded PCM of
     * this track. Used when the user seeks outside the live window.
     */
    private fun restartFromPosition(reason: String) {
        val host = host ?: return
        val old = session ?: return
        val decoded = old.decoded ?: run {
            startForSong(host.currentSong() ?: return)
            return
        }
        val song = host.currentSong() ?: return
        if (song.videoId != old.videoId) {
            startForSong(song)
            return
        }
        Log.d(TAG, "restart ($reason) for ${old.videoId}")
        restartCounter++
        old.cancelled = true
        old.job?.cancel()
        old.syncJob?.cancel()
        runCatching { old.player?.close() }
        runCatching { old.stemDir.deleteRecursively() }
        val newStemDir = File(old.trackDir, "stem-" + UUID.randomUUID().toString().take(8))
        newStemDir.mkdirs()
        val session = Session(
            videoId = old.videoId,
            trackDir = old.trackDir,
            stemDir = newStemDir,
            streamFile = old.streamFile,
            decoded = decoded,
            startFrame = 0,
            player = null,
        )
        this.session = session
        host.applyRemoverMuted(true)
        session.job = host.scope.launch { runPipeline(session) }
    }

    private fun stopSession() {
        val session = session ?: return
        this.session = null
        session.cancelled = true
        session.job?.cancel()
        session.syncJob?.cancel()
        runCatching { session.player?.close() }
        // trackDir holds everything the session fetched itself (stream bytes,
        // PCM, stems). The user's own local file lives outside it.
        runCatching { session.trackDir.deleteRecursively() }
        host?.applyRemoverMuted(false)
    }

    // ---- the pipeline ----

    private suspend fun runPipeline(session: Session) {
        val host = host ?: return
        val model = currentModelSpec()
        val downloader = downloader ?: return
        try {
            withContext(Dispatchers.IO) {
                setPhase(session, VocalRemoverPhase.DOWNLOADING_MODEL, model)
                val modelFile = downloader.ensureModel(model) { fraction ->
                    if (session === this@VocalRemover.session) {
                        state.update { it.copy(fraction = fraction) }
                    }
                }
                checkAlive(session)

                var decoded = session.decoded
                if (decoded == null || session.streamFile == null) {
                    if (session.streamFile == null) {
                        setPhase(session, VocalRemoverPhase.FETCHING_STREAM, model)
                        val stream = fetchStream(session) { bytes ->
                            updateDebug(session) { it.copy(streamBytes = bytes) }
                        }
                        session.streamFile = stream
                        checkAlive(session)
                    }
                    setPhase(session, VocalRemoverPhase.DECODING, model)
                    val pcmFile = File(session.trackDir, "pcm.f32")
                    decoded = AudioDecoder().decode(
                        file = session.streamFile!!,
                        output = pcmFile,
                        isCancelled = { session.cancelled },
                    )
                    checkAlive(session)
                    session.decoded = decoded
                }

                // The separation window opens at *now*, not at the toggle: the
                // listener hears "from the middle" without paying for the
                // frames that went by during preparation.
                session.startFrame = msToFrame(host.positionMs().coerceAtLeast(0))
                    .coerceIn(0L, (decoded.frameCount - 1).coerceAtLeast(0))
                val totalStemFrames = (decoded.frameCount - session.startFrame).coerceAtLeast(0)
                if (totalStemFrames <= 0) {
                    fail(session, "The track is already at its end")
                    return@withContext
                }
                state.update {
                    it.copy(totalStemMs = frameToMs(totalStemFrames), availableStemMs = 0)
                }

                val vocals = File(session.stemDir, "vocals.f32")
                val instrumental = File(session.stemDir, "instrumental.f32")
                // The player opens both stems for reading before the writer
                // creates them.
                vocals.createNewFile()
                instrumental.createNewFile()
                val player = LiveStemPlayer(
                    vocalsFile = vocals,
                    instrumentalFile = instrumental,
                    totalFrames = totalStemFrames,
                    sampleRate = decoded.sampleRate,
                    onPosition = { frame ->
                        if (session === this@VocalRemover.session) {
                            state.update { it.copy(playheadMs = frameToMs(frame)) }
                        }
                    },
                )
                player.setMode(StemMode.MIX)
                player.setVocalLevel(state.value.vocalLevel)
                session.player = player
                startSyncLoop(session)
                if (host.isPlaying()) player.play()

                setPhase(session, VocalRemoverPhase.SEPARATING, model)
                val threads = AppSettings.vocalRemoverCpuThreads.value
                LiveSeparationSession().run(
                    model = model,
                    modelFile = modelFile,
                    decoded = decoded,
                    startFrame = session.startFrame,
                    vocalsFile = vocals,
                    instrumentalFile = instrumental,
                    cpuThreads = threads,
                    playbackLeadSeconds = {
                        val p = session.player ?: return@run null
                        if (!host.isPlaying()) null
                        else frameToMs(
                            (p.availableFrames() - p.currentFrame()).coerceAtLeast(0),
                        ) / 1000f
                    },
                    onStats = { stats ->
                        updateDebug(session) {
                            it.copy(
                                chunkCount = stats.chunks,
                                avgChunkMs = stats.averageMillis,
                                lastChunkMs = stats.lastChunkMillis,
                                workerThreads = stats.workerThreads,
                                backend = stats.activeBackend.cacheId,
                                fallback = stats.fallbackReason,
                                threadsSetting = threads,
                            )
                        }
                    },
                    onPreview = { preview ->
                        state.update {
                            it.copy(availableStemMs = frameToMs(preview.availableFrames))
                        }
                    },
                    isCancelled = { session.cancelled },
                )
                if (!session.cancelled) setPhase(session, VocalRemoverPhase.ACTIVE, model)
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (!session.cancelled) {
                Log.w(TAG, "remover session failed", error)
                fail(session, error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun checkAlive(session: Session) {
        if (session.cancelled || session !== this.session) {
            throw kotlinx.coroutines.CancellationException("session replaced")
        }
    }

    private suspend fun fetchStream(
        session: Session,
        onBytes: (Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val videoId = session.videoId
        val url = StreamResolver.resolve(videoId)
        val request = Request.Builder().url(url).apply {
            StreamResolver.mediaHeadersFor(url).forEach { (name, value) -> header(name, value) }
        }.build()
        // Lives in trackDir so session teardown owns it; a user's own local
        // file never ends up here.
        val target = File(session.trackDir, "stream.bin")
        target.parentFile?.mkdirs()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Stream fetch failed with HTTP ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                        onBytes(total)
                    }
                }
            } ?: throw IOException("Stream fetch returned no body")
        }
        target
    }

    /**
     * The clock-following loop: ExoPlayer's (muted) position is the truth the
     * stem player is steered towards; the separated window is re-anchored
     * whenever the gap can no longer be closed by waiting.
     */
    private fun startSyncLoop(session: Session) {
        val host = host ?: return
        session.syncJob = host.scope.launch {
            var stallingTicks = 0
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                if (session.cancelled || session !== this@VocalRemover.session) return@launch
                val player = session.player ?: continue
                // The queue moved on while we were listening; the remover
                // follows the new track the same way it followed the old one.
                val song = host.currentSong()
                if (song != null && song.videoId != session.videoId) {
                    onMediaChanged()
                    return@launch
                }
                // Crossfade/adopt paths keep resetting player volumes; this is
                // the one place that guarantees silence from ExoPlayer while
                // the remover is audible.
                host.applyRemoverMuted(true)
                val expected = msToFrame(host.positionMs()) - session.startFrame
                val current = player.currentFrame()
                val available = player.availableFrames()
                if (expected < 0) {
                    restartFromPosition("seeked before the live window")
                    return@launch
                }
                if (host.isPlaying()) {
                    if (expected > available) {
                        stallingTicks++
                        if (frameToMs(expected - available) > JUMP_RESTART_MS) {
                            restartFromPosition("seeked beyond reachable buffer")
                            return@launch
                        }
                        if (!player.isPlaying()) player.play()
                    } else {
                        if (kotlin.math.abs(expected - current) > msToFrame(DRIFT_SEEK_MS)) {
                            player.seekTo(expected)
                        }
                        if (!player.isPlaying()) player.play()
                    }
                } else if (player.isPlaying()) {
                    player.pause()
                }
                val driftMs = frameToMs(expected) - frameToMs(current)
                updateDebug(session) {
                    it.copy(
                        expectedFrame = expected,
                        playheadFrame = current,
                        availableFrames = available,
                        leadMs = frameToMs((available - current).coerceAtLeast(0)),
                        driftMs = driftMs,
                        stallingTicks = stallingTicks,
                    )
                }
                if (state.value.phase == VocalRemoverPhase.SEPARATING && available > current) {
                    setPhaseSimple(VocalRemoverPhase.ACTIVE)
                }
            }
        }
    }

    // ---- small helpers ----

    private fun trackDirectory(context: Context, videoId: String): File {
        val safe = videoId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
        return File(context.cacheDir, "vocalremover/track-$safe")
    }

    private fun isYouTubeId(id: String?): Boolean =
        id != null && id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun msToFrame(ms: Long): Long = ms * SAMPLE_RATE / 1000

    private fun frameToMs(frames: Long): Long = frames * 1000 / SAMPLE_RATE

    private fun fail(session: Session, message: String) {
        if (session.cancelled) return
        host?.applyRemoverMuted(false)
        state.update {
            it.copy(
                phase = VocalRemoverPhase.ERROR,
                error = message,
                debug = it.debug.copy(lastError = message),
            )
        }
    }

    // ---- debug/state plumbing (TEMPORARY panel) ----

    private fun baseDebug(session: Session, model: ModelSpec): VocalRemoverDebug {
        val recorded = runCatching {
            downloader?.recordedSha256(model) ?: "unverified"
        }.getOrDefault("?")
        return VocalRemoverDebug(
            videoId = session.videoId,
            sessionUuid = session.stemDir.name,
            modelId = model.id,
            modelSha = if (model.sha256 != null) "pinned" else recorded,
            threadsSetting = AppSettings.vocalRemoverCpuThreads.value,
            deviceCores = Runtime.getRuntime().availableProcessors(),
            paths = session.stemDir.absolutePath,
            startFrame = session.startFrame,
            pcmFrames = session.decoded?.frameCount ?: 0,
            restartCount = restartCounter,
        )
    }

    private fun setPhase(
        session: Session,
        phase: VocalRemoverPhase,
        model: ModelSpec,
    ) {
        if (session !== this.session) return
        state.update {
            it.copy(
                phase = phase,
                fraction = 0f,
                error = null,
                debug = baseDebug(session, model).copy(
                    phaseStartedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun setPhaseSimple(phase: VocalRemoverPhase) {
        state.update { it.copy(phase = phase) }
    }

    private fun updateDebug(
        session: Session,
        transform: (VocalRemoverDebug) -> VocalRemoverDebug,
    ) {
        if (session !== this.session) return
        state.update { it.copy(debug = transform(it.debug)) }
    }
}
