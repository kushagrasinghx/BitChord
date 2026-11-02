package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.MixTransitionView
import com.music.bitchord.data.settings.TransitionWindow
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the player takes from the application it is drawn in.
 *
 * The player itself — its layout, its gestures, its animations, the lyric sheet
 * and the queue — is one piece of code for the phone and the desktop alike.
 * What differs is underneath it: where settings are kept, how a looping clip is
 * decoded, what an audio output is, which party this device is in. Each
 * application answers those once, here, and installs its answer with
 * [PlayerPlatform.install] before the player is first drawn.
 */
interface PlayerHost {
    val settings: PlayerSettingsSource

    /** A Canvas already settled for [song], without a lookup. */
    fun cachedCanvas(song: Song): CanvasArtwork?

    /** Looks a Canvas up for [song]; null is the normal answer. */
    suspend fun canvasFor(song: Song): CanvasArtwork?

    /** The looping clip over the cover, decoded however this platform decodes video. */
    @Composable
    fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier)

    /**
     * Whether this platform shows a music video's picture over the playing
     * audio. False keeps every video surface out of the player altogether.
     */
    val musicVideoSupported: Boolean get() = false

    /**
     * The playing music video's picture, fitted into these bounds ([fit]) or
     * cropped to fill them. Draws nothing until a frame of [videoId] has
     * landed, reported through [onRenderedChanged], so the caller keeps its
     * still artwork under it until then.
     */
    @Composable
    fun MusicVideo(videoId: String, fit: Boolean, onRenderedChanged: (Boolean) -> Unit, modifier: Modifier) = Unit

    /** The playing music video's width over its height. */
    val musicVideoAspect: StateFlow<Float> get() = DefaultVideoAspect

    /** The heights the playing music video offers, and which one is held. */
    val musicVideoQuality: StateFlow<MusicVideoQualityUi> get() = NoVideoQuality

    /** Holds the picture at [height] from now on, or lets it choose with null. Remembered. */
    fun setMusicVideoQuality(height: Int?) = Unit

    /**
     * Artwork for [song], resolving covers that live inside a remote file
     * (WebDAV, SMB) where the platform can. Returns [Song.thumbnailUrl] otherwise.
     */
    @Composable
    fun rememberRemoteArtworkUrl(song: Song?): String?

    /** The system output level the player's volume bar drives, if it drives one. */
    val volume: SystemVolume

    /** The outputs the output drawer lists, the active one marked. */
    @Composable
    fun rememberAudioOutputs(): List<AudioOutputDevice>

    /**
     * Wraps opening the output drawer in whatever it needs first — on Android,
     * the Bluetooth permission that lets it name headphones.
     */
    @Composable
    fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit

    fun selectAudioOutput(id: Int?)

    /** The negotiated output format, as the pipeline row and the hi-res shine read it. */
    val outputFormat: StateFlow<OutputFormatUi>

    /** The Listen Together party this device is in, as the player draws it. */
    val party: StateFlow<PartyUi>

    suspend fun translateLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsTranslationResult

    suspend fun romanizeLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsRomanizationResult

    /** A short, non-blocking notice — a Toast on the phone. */
    fun showMessage(message: String)

    /** The full signal-chain readout, opened from the output drawer. */
    @Composable
    fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit)

    /** Casting to a network receiver. Absent, and so never drawn, where the platform has none. */
    val castState: StateFlow<CastUi> get() = NoCast

    /**
     * The BitChord watch app as an output: the watch plays the queue the way a
     * Cast receiver does. Absent, and so never drawn, where there is no watch.
     */
    val watchOutput: StateFlow<WatchOutputUi> get() = NoWatch

    /** The output drawer opening: look for a watch in reach again. */
    fun refreshWatchOutput() = Unit

    /** Make the watch the speaker. */
    fun selectWatchOutput() = Unit

    /** The device picker the output drawer's Cast row opens, in the pipeline dialog's card. */
    @Composable
    fun CastDialog(hazeState: HazeState, onDismiss: () -> Unit) = Unit

    /**
     * The ⋯ menu's frost for the player's drawers and popups — see
     * [PlayerFrost]. Null where the platform can't draw it, and the drawers
     * fall back to the player's haze.
     */
    @Composable
    fun rememberPlayerFrost(): PlayerFrost? = null

    /**
     * Whether lines can be put on a share card here. False hides the pick mode
     * altogether rather than offering a mode with nowhere to put its result.
     */
    val lyricsShareAvailable: Boolean get() = false

    /**
     * The picked lines, drawn as a picture this platform can write out and send.
     * Absent, and so never called, where [lyricsShareAvailable] is false.
     */
    @Composable
    fun LyricsShareSheet(hazeState: HazeState, request: LyricsShareRequest, onDismiss: () -> Unit) = Unit
}

private val NoCast = MutableStateFlow(CastUi())

private val DefaultVideoAspect = MutableStateFlow(16f / 9f)

private val NoVideoQuality = MutableStateFlow(MusicVideoQualityUi())

/** What the video quality box draws — the web player's VideoQuality, by the same rules. */
data class MusicVideoQualityUi(
    /** Every height the loaded video offers, largest first. */
    val heights: List<Int> = emptyList(),
    /** The listener's pick, or null for Auto. */
    val held: Int? = null,
    /** The height actually on screen. */
    val activeHeight: Int? = null,
)

/** How a height is named on screen: the marketing name where there is one, the height otherwise. */
internal fun videoQualityLabel(height: Int): String = when {
    height >= 4320 -> "8K"
    height >= 2160 -> "4K"
    height >= 1440 -> "2K"
    height >= 1080 -> "FullHD"
    height >= 720 -> "HD"
    else -> "${height}p"
}

/** Whether [song]'s picture can be shown here — a music video, on a platform that plays one. */
internal fun showsMusicVideo(song: Song): Boolean =
    song.isVideo && PlayerPlatform.host.musicVideoSupported

/** @see PlayerHost.MusicVideo */
@Composable
internal fun MusicVideoSurface(
    videoId: String,
    modifier: Modifier = Modifier,
    fit: Boolean = true,
    onRenderedChanged: (Boolean) -> Unit = {},
) = PlayerPlatform.host.MusicVideo(videoId, fit, onRenderedChanged, modifier)

private val NoWatch = MutableStateFlow(WatchOutputUi())

/** What the output drawer needs to draw the watch's row. */
data class WatchOutputUi(
    /** A watch with BitChord on it is in reach. False (and not [active]) hides the row. */
    val available: Boolean = false,
    /** What the watch calls itself; the drawer has a generic name for when it says nothing. */
    val name: String? = null,
    /** The watch is the speaker. */
    val active: Boolean = false,
)

/** What the output drawer needs to know about casting to draw its row. */
data class CastUi(
    /** Whether this device can cast at all. False hides the row. */
    val supported: Boolean = false,
    /** The receiver the music is on now, or null when it is on this device. */
    val connectedName: String? = null,
    val connecting: Boolean = false,
)

/** Where the installed [PlayerHost] lives. */
object PlayerPlatform {
    @Volatile
    private var installed: PlayerHost? = null

    val host: PlayerHost
        get() = installed ?: error("PlayerPlatform.install() was never called")

    fun install(host: PlayerHost) {
        installed = host
    }
}

/** The settings the player reads, named and typed as the phone's AppSettings has them. */
interface PlayerSettingsSource {
    val animatedCanvas: StateFlow<Boolean>
    val audioQualityCellular: StateFlow<AudioQuality>
    val audioQualityWifi: StateFlow<AudioQuality>
    val canvasOverCellular: StateFlow<Boolean>
    val fullBleedArtwork: StateFlow<Boolean>
    val hideSongStatus: StateFlow<Boolean>
    val showQueueHistory: StateFlow<Boolean>
    val hideVolumeBar: StateFlow<Boolean>
    /** Hide the device status bar while the player is up. Always false where there is none. */
    val hideStatusBar: StateFlow<Boolean>
    val lastPlayerScreen: StateFlow<LastPlayerScreen>
    val legacyMeshGradient: StateFlow<Boolean>
    val lyricsBlur: StateFlow<Boolean>
    val lyricsOffsetMs: StateFlow<Int>
    val lyricsSourceOrder: StateFlow<List<LyricsSource>>
    val meteredConnection: StateFlow<Boolean?>
    /** A temporary cap over both ceilings, null when none applies — the watch's, on the phone. */
    val streamCeilingOverride: StateFlow<AudioQuality?>
    val preferUsbDac: StateFlow<Boolean>
    val prioritizeSpotifyCanvas: StateFlow<Boolean>
    val reduceAnimation: StateFlow<Boolean>
    val reduceDynamicBlur: StateFlow<Boolean>
    val showNerdStats: StateFlow<Boolean>
    /** Whether the output / Listen Together pill sits under the transport. */
    val showListenTogetherButton: StateFlow<Boolean>
    val smartAnalysis: StateFlow<SmartAnalysis>
    /**
     * Automix's detailed stats line — both songs' analysis, then the planned
     * mix (loop lengths and what cleared them, the tempo ramp) — or null when
     * there is nothing to say beyond [smartAnalysis].
     */
    val automixDetail: StateFlow<String?>
    val smartFadeEnabled: StateFlow<Boolean>
    /** True only while an analysed Automix transition is audibly mixing two tracks. */
    val smartMixInProgress: StateFlow<Boolean>
    /** The Automix blend in flight, for the scrubber's beat glow; null between blends. */
    val smartMixBlend: StateFlow<MixBlend?>
    /** The Automix blend in flight laid out for the mix popup; null between blends. */
    val smartMixView: StateFlow<MixTransitionView?>
    /** Progress through [smartMixView]'s blend, 0..1. */
    val smartMixProgress: StateFlow<Float>
    val smartTransitionWindow: StateFlow<TransitionWindow?>
    val spotifyCanvasAutoHide: StateFlow<Boolean>
    val syncedLyrics: StateFlow<Boolean>
    val translationLanguage: StateFlow<String>
    val versionAlignmentInProgress: StateFlow<Boolean>

    fun setLastPlayerScreen(value: LastPlayerScreen)
    fun setLyricsOffsetMs(value: Int)
}

/** The player's name for the installed host's settings, read where AppSettings used to be. */
internal val PlayerSettings: PlayerSettingsSource
    get() = PlayerPlatform.host.settings

/** The lyric offset sheet's range, in milliseconds either way. */
const val MIN_LYRICS_OFFSET_MS = -5_000
const val MAX_LYRICS_OFFSET_MS = 5_000

/** A system output level, 0..1, and the means to set it. */
interface SystemVolume {
    /** The level as the system reports it, following hardware keys and other apps. */
    val level: StateFlow<Float>
    fun set(level: Float)

    /**
     * Reads the level again. Android keeps one media volume per output and
     * swaps which is in force on a route change without announcing it.
     */
    fun refresh()
}

enum class AudioOutputKind { PHONE, WIRED, USB, BLUETOOTH, HDMI, OTHER }

data class AudioOutputDevice(
    val id: Int,
    /** Blank for a device with no name of its own; the drawer supplies one. */
    val name: String,
    val kind: AudioOutputKind,
    val isActive: Boolean = false,
    /**
     * What shape of thing a [AudioOutputKind.BLUETOOTH] device is. Buds and
     * speakers connect as the same A2DP sink, so this is worked out from the
     * device's name and class — and [BluetoothForm.UNKNOWN] wherever neither
     * says, rather than a guess drawn as fact.
     */
    val form: BluetoothForm = BluetoothForm.UNKNOWN,
)

enum class BluetoothForm { UNKNOWN, BUDS, HEADPHONES, SPEAKER, CAR }

data class OutputFormatUi(
    /** "24-bit PCM · 48 kHz" — whichever parts are known. */
    val summary: String = "",
    /**
     * Whether the music is reaching the DAC bit-perfect right now: on the
     * exclusive output, with every sample of the track carried exactly.
     */
    val bitPerfect: Boolean = false,
    /**
     * On the exclusive output, converted to the best format the DAC has because
     * it can't take the track's own. As good as this DAC gets, but not bit-perfect.
     */
    val dacLimited: Boolean = false,
)

data class PartyUi(
    val inParty: Boolean = false,
    val controlsLocked: Boolean = false,
    val members: List<PartyMember> = emptyList(),
    val you: PartyMember? = null,
    val code: String? = null,
)

sealed interface LyricsTranslationResult {
    data class Translated(val lines: List<LyricLine>) : LyricsTranslationResult
    data class SameLanguage(val language: String) : LyricsTranslationResult
    data object Unavailable : LyricsTranslationResult
}

sealed interface LyricsRomanizationResult {
    data class Romanized(val lines: List<LyricLine>) : LyricsRomanizationResult
    data object AlreadyRomanized : LyricsRomanizationResult
    data object Unavailable : LyricsRomanizationResult
}

/** How a clip fills the bounds supplied by its caller. */
enum class CanvasContentMode {
    CROP,
    FIT_PORTRAIT,
}

/** Everything [CanvasArtworkPlayer] is asked, handed to the platform's decoder as one value. */
class CanvasVideoSpec(
    val canvas: CanvasArtwork,
    val isPlaying: Boolean,
    val contentMode: CanvasContentMode,
    val alignPortraitTop: Boolean,
    val onAspectRatioChanged: (Float) -> Unit,
    val portraitRevealBounds: IntSize,
    val presentationAlpha: () -> Float,
    val onRenderedChanged: (Boolean) -> Unit,
    val onFrameCaptured: (ImageBitmap) -> Unit,
    val refreshFrameEveryMs: Long?,
    val frameCapturePx: Int,
    val onCoverChanged: (Float) -> Unit,
    val bottomFade: Float,
    val bottomFadeEndPx: Float?,
    val pausedForTransition: Boolean,
    val resumeDelayMs: Long,
    val startPositionMs: Long,
    val revealInstantly: Boolean,
    val captureStill: Boolean,
    val onStillCaptured: (ImageBitmap?, Long) -> Unit,
    val onResumed: () -> Unit,
)

/**
 * The looping video that plays over a track's cover art — see
 * [PlayerHost.CanvasVideo] for the decoder behind it on each platform.
 */
@Composable
fun CanvasArtworkPlayer(
    canvas: CanvasArtwork,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentMode: CanvasContentMode = CanvasContentMode.CROP,
    alignPortraitTop: Boolean = false,
    onAspectRatioChanged: (Float) -> Unit = {},
    portraitRevealBounds: IntSize = IntSize.Zero,
    presentationAlpha: () -> Float = { 1f },
    onRenderedChanged: (Boolean) -> Unit = {},
    onFrameCaptured: (ImageBitmap) -> Unit = {},
    refreshFrameEveryMs: Long? = null,
    /** 0 for no frames at all, which saves a main-thread readback on each fade-in. */
    frameCapturePx: Int = CANVAS_FRAME_CAPTURE_PX,
    onCoverChanged: (Float) -> Unit = {},
    bottomFade: Float = 0f,
    bottomFadeEndPx: Float? = null,
    pausedForTransition: Boolean = false,
    /** How long the clip holds still after [pausedForTransition] lifts before it plays on. */
    resumeDelayMs: Long = 0L,
    /** Where in the clip to start — a still's position, so its first frame is that still. */
    startPositionMs: Long = 0L,
    /** Shows the first frame at once rather than fading it in: there is a still under it that it matches. */
    revealInstantly: Boolean = false,
    /**
     * Asks for the frame on screen, once each time this turns true, through
     * [onStillCaptured] with the position it was taken at — null if it could
     * not be read. Only while [pausedForTransition], so the frame holds still.
     */
    captureStill: Boolean = false,
    onStillCaptured: (ImageBitmap?, Long) -> Unit = { _, _ -> },
    /** The clip has moved on from the frame it was last captured or started at. */
    onResumed: () -> Unit = {},
) {
    PlayerPlatform.host.CanvasVideo(
        CanvasVideoSpec(
            canvas = canvas,
            isPlaying = isPlaying,
            contentMode = contentMode,
            alignPortraitTop = alignPortraitTop,
            onAspectRatioChanged = onAspectRatioChanged,
            portraitRevealBounds = portraitRevealBounds,
            presentationAlpha = presentationAlpha,
            onRenderedChanged = onRenderedChanged,
            onFrameCaptured = onFrameCaptured,
            refreshFrameEveryMs = refreshFrameEveryMs,
            frameCapturePx = frameCapturePx,
            onCoverChanged = onCoverChanged,
            bottomFade = bottomFade,
            bottomFadeEndPx = bottomFadeEndPx,
            pausedForTransition = pausedForTransition,
            resumeDelayMs = resumeDelayMs,
            startPositionMs = startPositionMs,
            revealInstantly = revealInstantly,
            captureStill = captureStill,
            onStillCaptured = onStillCaptured,
            onResumed = onResumed,
        ),
        modifier,
    )
}

/** The longest edge of a frame handed to [CanvasArtworkPlayer]'s onFrameCaptured. */
const val CANVAS_FRAME_CAPTURE_PX = 128

/** Media3's repeat modes, by value, so the transport can speak them without Media3. */
object RepeatModes {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

@Composable
internal fun rememberAudioOutputs(): List<AudioOutputDevice> = PlayerPlatform.host.rememberAudioOutputs()

@Composable
internal fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit =
    PlayerPlatform.host.rememberOutputPicker(onOpen)

@Composable
internal fun rememberRemoteArtworkUrl(song: Song?): String? =
    PlayerPlatform.host.rememberRemoteArtworkUrl(song)

@Composable
internal fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) =
    PlayerPlatform.host.AudioPipelineDialog(hazeState, isPlaying, onDismiss)
