package com.music.bitchord.ui.player

import com.music.bitchord.wear.WearBridge
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.audio.bitperfect.BitPerfectOutput
import com.music.bitchord.playback.audio.bitperfect.DacVolume
import com.music.bitchord.playback.cast.CastController
import com.music.bitchord.ui.components.backdrop.backdrops.SnapshotBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberSnapshotBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.snapshotBackdropSource
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.menuBackdropFrost
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The phone's answers to [PlayerHost]: AppSettings, the Media3 Canvas decoder,
 * AudioRouting and the music stream's volume, the party, and the lyric
 * translation endpoint — each exactly what the player read directly before it
 * moved into the shared module.
 */
class AndroidPlayerHost(context: Context) : PlayerHost {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    override val settings: PlayerSettingsSource = object : PlayerSettingsSource {
        override val animatedCanvas get() = AppSettings.animatedCanvas
        override val audioQualityCellular get() = AppSettings.audioQualityCellular
        override val audioQualityWifi get() = AppSettings.audioQualityWifi
        override val canvasOverCellular get() = AppSettings.canvasOverCellular
        override val fullBleedArtwork get() = AppSettings.fullBleedArtwork
        override val hideSongStatus get() = AppSettings.hideSongStatus
        override val showQueueHistory get() = AppSettings.showQueueHistory
        override val hideVolumeBar get() = AppSettings.hideVolumeBar
        override val hideStatusBar get() = AppSettings.hideStatusBarInPlayer
        override val lastPlayerScreen get() = AppSettings.lastPlayerScreen
        override val legacyMeshGradient get() = AppSettings.legacyMeshGradient
        override val lyricsBlur get() = AppSettings.lyricsBlur
        override val lyricsOffsetMs get() = AppSettings.lyricsOffsetMs
        override val lyricsSourceOrder get() = AppSettings.lyricsSourceOrder
        override val meteredConnection get() = AppSettings.meteredConnection
        override val streamCeilingOverride get() = AppSettings.streamCeilingOverride
        override val preferUsbDac get() = AppSettings.preferUsbDac
        override val prioritizeSpotifyCanvas get() = AppSettings.prioritizeSpotifyCanvas
        override val reduceAnimation get() = AppSettings.reduceAnimation
        override val reduceDynamicBlur get() = AppSettings.reduceDynamicBlur
        override val showNerdStats get() = AppSettings.showNerdStats
        override val showListenTogetherButton get() = AppSettings.showListenTogetherButton
        override val smartAnalysis get() = AppSettings.smartAnalysis
        override val automixDetail get() = AppSettings.automixDetail
        override val smartFadeEnabled get() = AppSettings.smartFadeEnabled
        override val smartMixInProgress get() = AppSettings.smartMixInProgress
        override val smartMixBlend get() = AppSettings.smartMixBlend
        override val smartMixView get() = AppSettings.smartMixView
        override val smartMixProgress get() = AppSettings.smartMixProgress
        override val smartTransitionWindow get() = AppSettings.smartTransitionWindow
        override val spotifyCanvasAutoHide get() = AppSettings.spotifyCanvasAutoHide
        override val syncedLyrics get() = AppSettings.syncedLyrics
        override val translationLanguage get() = AppSettings.translationLanguage
        override val versionAlignmentInProgress get() = AppSettings.versionAlignmentInProgress

        override fun setLastPlayerScreen(value: LastPlayerScreen) = AppSettings.setLastPlayerScreen(value)
        override fun setLyricsOffsetMs(value: Int) = AppSettings.setLyricsOffsetMs(value)
    }

    override fun cachedCanvas(song: Song): CanvasArtwork? = CanvasRepository.cached(song)

    override suspend fun canvasFor(song: Song): CanvasArtwork? = CanvasRepository.canvasFor(song)

    @Composable
    override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) = AndroidCanvasVideo(spec, modifier)

    override val musicVideoSupported: Boolean get() = true

    @Composable
    override fun MusicVideo(videoId: String, fit: Boolean, onRenderedChanged: (Boolean) -> Unit, modifier: Modifier) =
        AndroidMusicVideo(videoId, fit, onRenderedChanged, modifier)

    override val musicVideoAspect: StateFlow<Float> get() = MusicVideoEngine.aspect

    override val musicVideoQuality: StateFlow<MusicVideoQualityUi> get() = MusicVideoEngine.quality

    override fun setMusicVideoQuality(height: Int?) = MusicVideoEngine.setQuality(height)

    @Composable
    override fun rememberRemoteArtworkUrl(song: Song?): String? =
        com.music.bitchord.ui.components.rememberRemoteArtworkUrl(song)

    // Lazy, all three: the player is not the first thing the process does, and
    // none of these objects should be woken for a launch that never opens it.
    override val volume: SystemVolume by lazy { CastAwareVolume(MusicStreamVolume(app), scope) }

    @Composable
    override fun rememberAudioOutputs(): List<AudioOutputDevice> {
        val outputs = rememberAndroidAudioOutputs()
        val cast by CastController.state.collectAsStateWithLifecycle()
        val watch by WearBridge.watchOutput.collectAsStateWithLifecycle()
        // While a receiver is the speaker no output of this phone is the one
        // playing, so none of them is lit; the Cast or watch row is.
        return if (cast.casting || watch.active) outputs.map { it.copy(isActive = false) } else outputs
    }

    @Composable
    override fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit = rememberAndroidOutputPicker(onOpen)

    override fun selectAudioOutput(id: Int?) {
        // Picking an output of this phone is picking this phone: the receiver
        // is let go of, and the music carries on here if it was playing.
        if (CastController.state.value.casting) CastController.disconnect(resumeHere = true)
        AudioRouting.select(id)
        WearBridge.leaveWatchOutput()
    }

    override val watchOutput: StateFlow<WatchOutputUi> by lazy {
        WearBridge.watchOutput
            .map { WatchOutputUi(available = it.available, name = it.name, active = it.active) }
            .stateIn(scope, SharingStarted.Eagerly, WatchOutputUi())
    }

    override fun refreshWatchOutput() = WearBridge.refreshWatchOutput(app)

    override fun selectWatchOutput() {
        // One speaker at a time: a receiver lets go first, quietly, and the
        // watch picks the queue up from where the phone's player is.
        if (CastController.state.value.casting) CastController.disconnect(resumeHere = false)
        WearBridge.selectWatchOutput()
    }

    override val outputFormat: StateFlow<OutputFormatUi> by lazy {
        combine(AudioOutputStatus.current, BitPerfectOutput.status) { status, bitPerfect ->
            status.toOutputFormat(bitPerfect)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            AudioOutputStatus.current.value.toOutputFormat(BitPerfectOutput.status.value),
        )
    }

    override val party: StateFlow<PartyUi> by lazy {
        ListenTogether.state
            .map { it.toPartyUi() }
            .stateIn(scope, SharingStarted.Eagerly, ListenTogether.state.value.toPartyUi())
    }

    override suspend fun translateLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsTranslationResult =
        when (val result = LyricsTranslation.translate(trackId, lines, targetLanguageTag)) {
            is LyricsTranslation.Result.Translated -> LyricsTranslationResult.Translated(result.lines)
            is LyricsTranslation.Result.SameLanguage -> LyricsTranslationResult.SameLanguage(result.language)
            LyricsTranslation.Result.Unavailable -> LyricsTranslationResult.Unavailable
        }

    override suspend fun romanizeLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsRomanizationResult =
        when (val result = LyricsTranslation.romanize(trackId, lines, targetLanguageTag)) {
            is LyricsTranslation.RomanizationResult.Romanized -> LyricsRomanizationResult.Romanized(result.lines)
            LyricsTranslation.RomanizationResult.AlreadyRomanized -> LyricsRomanizationResult.AlreadyRomanized
            LyricsTranslation.RomanizationResult.Unavailable -> LyricsRomanizationResult.Unavailable
        }

    override fun showMessage(message: String) {
        main.post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }

    override val castState: StateFlow<CastUi> by lazy {
        CastController.ensureStarted(app)
        CastController.state
            .map { CastUi(supported = it.supported, connectedName = it.connectedName, connecting = it.connecting) }
            .stateIn(scope, SharingStarted.Eagerly, CastUi())
    }

    @Composable
    override fun CastDialog(hazeState: HazeState, onDismiss: () -> Unit) =
        com.music.bitchord.ui.components.CastDialog(hazeState = hazeState, onDismiss = onDismiss)

    @Composable
    override fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) =
        com.music.bitchord.ui.components.AudioPipelineDialog(
            hazeState = hazeState,
            onDismiss = onDismiss,
            isPlaying = isPlaying,
        )

    // The ⋯ menu's own material: a still of the player, blurred under each
    // panel exactly as [menuBackdropFrost] blurs it under the menu. Below
    // Android 12 there is no RenderEffect to blur it with.
    @Composable
    override fun rememberPlayerFrost(): PlayerFrost? {
        if (!isGlassSupported()) return null
        val backdrop = rememberSnapshotBackdrop()
        return remember(backdrop) { BackdropPlayerFrost(backdrop) }
    }

    // The phone is the platform that can turn picked lyrics into a picture: a
    // bitmap, a canvas, the gallery and the chooser are all Android's here. The
    // desktop has no answer to it, so it never sees the pick mode at all.
    override val lyricsShareAvailable: Boolean get() = true

    @Composable
    override fun LyricsShareSheet(
        hazeState: HazeState,
        request: LyricsShareRequest,
        onDismiss: () -> Unit,
    ) = AndroidLyricsShareSheet(hazeState = hazeState, request = request, onDismiss = onDismiss)
}

/**
 * The output's format line, and whether the track is reaching the DAC
 * bit-perfect. On the exclusive output the format is the one written to
 * the DAC, not the ordinary AudioTrack's, which is idle meanwhile.
 */
private fun AudioOutputStatus.Snapshot.toOutputFormat(bitPerfect: BitPerfectOutput.Status): OutputFormatUi {
    val encoding = if (bitPerfect.active) "${bitPerfect.outputBits}-bit PCM" else AudioOutputStatus.encodingLabel(this)
    val rate = (if (bitPerfect.active) bitPerfect.sampleRateHz else actualSampleRateHz)?.takeIf { it > 0 }
    val summary = if (rate == null) {
        encoding
    } else {
        "$encoding · ${"%.1f".format(Locale.ROOT, rate / 1000f).removeSuffix(".0")} kHz"
    }
    return OutputFormatUi(summary = summary, bitPerfect = bitPerfect.active && bitPerfect.exact)
}

private fun ListenTogether.State.toPartyUi() = PartyUi(
    inParty = inParty,
    controlsLocked = controlsLocked,
    members = members,
    you = you,
    code = code,
)

/**
 * The volume bar's level: the receiver's while the music is on one, the DAC's
 * own while direct USB output drives it, the music stream's otherwise. The two are different numbers on different devices, and
 * the bar must never show one while moving the other.
 */
private class CastAwareVolume(
    private val system: SystemVolume,
    scope: CoroutineScope,
) : SystemVolume {
    override val level: StateFlow<Float> = combine(
        system.level,
        CastController.volume,
        CastController.state.map { it.casting },
        WearBridge.watchOutput,
        DacVolume.level,
    ) { phone, receiver, casting, watch, dac ->
        when {
            casting -> receiver
            watch.active -> watch.volume
            // Direct USB: the music stream no longer reaches the DAC, so its
            // own hardware volume is the only one that means anything.
            dac != null -> dac
            else -> phone
        }
    }
        .stateIn(scope, SharingStarted.Eagerly, system.level.value)

    override fun set(level: Float) {
        when {
            CastController.state.value.casting -> CastController.setVolume(level)
            WearBridge.watchOutput.value.active -> WearBridge.setWatchVolume(level)
            DacVolume.level.value != null -> DacVolume.set(level)
            else -> system.set(level)
        }
    }

    override fun refresh() = system.refresh()
}

/**
 * The music stream's volume. Hardware keys and the system panel change it
 * behind the app's back, so Settings is watched and the level follows.
 */
private class MusicStreamVolume(private val context: Context) : SystemVolume {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val _level = MutableStateFlow(read())
    override val level: StateFlow<Float> = _level.asStateFlow()

    init {
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh()
            },
        )
    }

    private fun max(): Int = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15

    private fun read(): Float =
        (manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / max()

    override fun set(level: Float) {
        manager?.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max()).roundToInt(), 0)
    }

    override fun refresh() {
        _level.value = read()
    }
}

private class BackdropPlayerFrost(private val backdrop: SnapshotBackdrop) : PlayerFrost {
    @Composable
    override fun Modifier.frostSource(): Modifier = snapshotBackdropSource(backdrop)

    override fun capture() = backdrop.capture()

    override fun release() = backdrop.release()

    override fun Modifier.frostPanel(shape: Shape, fill: Color): Modifier = menuBackdropFrost(backdrop, shape, fill)
}
