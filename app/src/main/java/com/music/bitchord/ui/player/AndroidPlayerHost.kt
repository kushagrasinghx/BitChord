package com.music.bitchord.ui.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        override val hideVolumeBar get() = AppSettings.hideVolumeBar
        override val lastPlayerScreen get() = AppSettings.lastPlayerScreen
        override val legacyMeshGradient get() = AppSettings.legacyMeshGradient
        override val lyricsBlur get() = AppSettings.lyricsBlur
        override val lyricsOffsetMs get() = AppSettings.lyricsOffsetMs
        override val lyricsSourceOrder get() = AppSettings.lyricsSourceOrder
        override val meteredConnection get() = AppSettings.meteredConnection
        override val preferUsbDac get() = AppSettings.preferUsbDac
        override val prioritizeSpotifyCanvas get() = AppSettings.prioritizeSpotifyCanvas
        override val reduceAnimation get() = AppSettings.reduceAnimation
        override val reduceDynamicBlur get() = AppSettings.reduceDynamicBlur
        override val showNerdStats get() = AppSettings.showNerdStats
        override val smartAnalysis get() = AppSettings.smartAnalysis
        override val smartFadeEnabled get() = AppSettings.smartFadeEnabled
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

    @Composable
    override fun rememberRemoteArtworkUrl(song: Song?): String? =
        com.music.bitchord.ui.components.rememberRemoteArtworkUrl(song)

    // Lazy, all three: the player is not the first thing the process does, and
    // none of these objects should be woken for a launch that never opens it.
    override val volume: SystemVolume by lazy { MusicStreamVolume(app) }

    @Composable
    override fun rememberAudioOutputs(): List<AudioOutputDevice> = rememberAndroidAudioOutputs()

    @Composable
    override fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit = rememberAndroidOutputPicker(onOpen)

    override fun selectAudioOutput(id: Int?) = AudioRouting.select(id)

    override val outputFormat: StateFlow<OutputFormatUi> by lazy {
        AudioOutputStatus.current
            .map { it.toOutputFormat() }
            .stateIn(scope, SharingStarted.Eagerly, AudioOutputStatus.current.value.toOutputFormat())
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

    @Composable
    override fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) =
        com.music.bitchord.ui.components.AudioPipelineDialog(
            hazeState = hazeState,
            onDismiss = onDismiss,
            isPlaying = isPlaying,
        )
}

private fun AudioOutputStatus.Snapshot.toOutputFormat(): OutputFormatUi {
    val encoding = AudioOutputStatus.encodingLabel(this)
    val rate = actualSampleRateHz?.takeIf { it > 0 }
    val summary = if (rate == null) {
        encoding
    } else {
        "$encoding · ${"%.1f".format(Locale.ROOT, rate / 1000f).removeSuffix(".0")} kHz"
    }
    // What the route is *capable* of, read off the negotiated AudioTrack.
    val carriesHiRes = when (actualEncoding) {
        AudioFormat.ENCODING_PCM_24BIT_PACKED,
        AudioFormat.ENCODING_PCM_32BIT,
        AudioFormat.ENCODING_PCM_FLOAT,
        -> true
        else -> (actualSampleRateHz ?: 0) > 48_000
    }
    return OutputFormatUi(summary = summary, carriesHiRes = carriesHiRes)
}

private fun ListenTogether.State.toPartyUi() = PartyUi(
    inParty = inParty,
    controlsLocked = controlsLocked,
    members = members,
    you = you,
    code = code,
)

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
