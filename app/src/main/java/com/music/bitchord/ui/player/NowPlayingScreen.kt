package com.music.bitchord.ui.player

import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.playback.BACK_RESTARTS_AFTER_MS
import com.music.bitchord.playback.autoplaySectionStart
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Collapsed-header geometry, shared by the layout and its animation. */
/** Comfortably over the sleeve's drawn size on a phone, without wasting bytes. */
private const val ART_PX = 1200

/**
 * How long a canvas lookup waits for the track's album name before giving up
 * on it. Long enough to cover the album lookup on a normal connection, short
 * enough not to be noticed on a track that has no album to find.
 */
private const val ALBUM_SETTLE_MS = 700L

/**
 * How close the player's reported position has to get to a released scrub
 * handle before the handle stops being drawn where it was dropped. Wide enough
 * to swallow a coarse progress tick, tight enough that the handle doesn't hand
 * over while it is still visibly wrong.
 */
private const val SEEK_SETTLE_TOLERANCE_MS = 1_500L

/**
 * How long that handle is held at the drop point regardless. A backstop, not a
 * schedule: a seek normally settles in a tick or two, and this only decides how
 * long a seek that never settles can freeze the bar for. Generous enough that a
 * slow buffer still hands over smoothly rather than snapping back.
 */
private const val SEEK_SETTLE_TIMEOUT_MS = 4_000L

private val THUMB_SIZE = 54.dp
private val HEADER_HEIGHT = 60.dp
private val ART_TITLE_GAP = 20.dp
/** Only drags starting in this top strip reach the sheet and close the player. */
private val DISMISS_STRIP_HEIGHT = 44.dp
/** The breathing room above the sleeve, needed twice: once to apply, once to measure past. */
private val ART_BOX_TOP_PAD = 14.dp
/**
 * Share of the motion-artwork banner's height given over to its dissolve.
 *
 * Generous on purpose: the banner has no card edge to stop at, so anything
 * short enough to still be reading as artwork where it ends reads as a picture
 * that was cut off rather than one that ran out.
 */
private const val HERO_FADE_FRACTION = 0.42f
/** The player's side margin. Scrollable panels reach back across it. */
private val PLAYER_GUTTER = 30.dp
/**
 * How wide the player's content is ever allowed to get. A sleeve and a volume
 * slider stretched right across a tablet aren't a bigger player, just a coarser
 * one; past this the column stops growing and centres itself instead. Phones
 * are narrower than this, so for them it does nothing.
 */
private val PLAYER_MAX_WIDTH = 560.dp

/** Share of a lyric line's own length spent fading out, and its bounds. */
private const val LYRIC_FADE_FRACTION = 0.28f
private const val LYRIC_FADE_MIN_MS = 160f
private const val LYRIC_FADE_MAX_MS = 700f

/**
 * How far back the part of the playing line that hasn't been sung yet is held.
 *
 * The strip above the scrubber gets less of a gap than the full panel: it is
 * one line of small type with nothing around it to compare against, and taking
 * it as far down as the panel does left the words ahead of the highlight hard
 * to read at a glance.
 */
private const val UNSUNG_ALPHA = 0.45f
private const val UNSUNG_ALPHA_STRIP = 0.55f

/**
 * The bloom behind the line being sung, at its very strongest.
 *
 * Kept well under half strength: the halo is drawn from the same white as the
 * text, so at full alpha it stops reading as light and starts reading as a
 * second, badly printed copy of the words. What is actually drawn is this
 * scaled by how long the word is being held, so only a properly carried note
 * ever sees the whole of it.
 */
private const val GLOW_ALPHA = 0.62f
private val GLOW_RADIUS = 9.dp

/**
 * How far behind the sweep's leading edge the bloom reaches, at full strength.
 *
 * The glow belongs to the word being sung, not to everything sung so far —
 * lighting the whole revealed stretch made the line brighten as it went and
 * turned the last line of a verse into a slab of white. Scaled down towards
 * [GLOW_TRAIL_FLOOR] as the singing quickens; see
 * [LyricLine.glowIntensity][com.music.bitchord.data.lyrics.LyricLine.glowIntensity].
 */
private val GLOW_TRAIL = 62.dp
private const val GLOW_TRAIL_FLOOR = 0.55f

/**
 * Room reserved inside each copy of a line for the halo to spread into.
 *
 * A blur is computed on its layer's own bitmap, so a halo with nowhere to go
 * inside those bounds is a halo with a hard edge — which is what cropped the
 * bloom to the line's box. Every copy carries the same inset so they still lay
 * out identically, and the list gives the width back by taking it off its own
 * padding and row spacing.
 */
private val GLOW_ROOM = 10.dp

/** Stands in for an instrumental stretch on the single-line strip. */
private const val INSTRUMENTAL_MARK = "Instrumental"

/**
 * Shown on the strip during the intro, before the first sung line — one picked
 * at random per track, so the wait for the vocals has some character to it.
 */
private val INTRO_LINES = listOf(
    "Beat's landing",
    "Song's starting",
    "Intro's cooking",
    "Warming up",
    "Here we go",
    "Setting the mood",
    "Drums are in",
    "Bass first, words later",
    "Turn it up",
    "Vibe check",
    "Wait for it",
    "Feel that build",
    "Let it ride",
    "Just the groove for now",
    "Speakers breathing",
    "Rolling in",
    "Hold tight",
    "Riff o'clock",
    "Strings first",
    "Hook's on the way",
    "Eyes closed",
    "Loading the vibe",
    "Almost words",
    "Pure heat, no words",
    "Tuning in",
    "Buckle up",
    "Let it breathe",
    "That opening though",
    "Bass is talking",
    "Lyrics loading",
    "Give it a sec",
    "Building something",
    "Cue the vocals",
    "Slow burn",
    "First notes in",
    "Nod along",
    "Groove's on deck",
    "Melody first",
    "Ease into it",
    "Big things coming",
    "Stage is set",
    "The calm before",
    "Sit with it",
    "Any second now",
    "Volume up, phone down",
    "Drums doing the talking",
    "Locked in",
    "Something's brewing",
    "Finding its feet",
    "Deep breath",
)

/**
 * Shown on the strip while a lyrics lookup is still in flight — one picked
 * at random per track, in the same spirit as [INTRO_LINES].
 */
private val LYRICS_LOADING_LINES = listOf(
    "Getting lyrics",
    "Chasing the words",
    "Digging up the lyrics",
    "Words incoming",
    "On the hunt for lyrics",
    "Fetching the verses",
    "Tracking down the words",
    "Lyrics loading",
    "Reading between the lines",
    "Scanning for lyrics",
    "Words on the way",
    "Looking this one up",
    "Checking the lyric sheet",
    "Pulling up the words",
    "Searching the songbook",
    "Lining up the lyrics",
    "One sec, finding the words",
    "Combing through for lyrics",
    "Lyrics inbound",
    "Sourcing the verses",
    "Cross-checking the words",
    "Rounding up the lyrics",
    "Text hunt in progress",
    "Syncing up the words",
    "Peeking at the lyric sheet",
    "Almost got the words",
    "Fishing for lyrics",
    "Grabbing the transcript",
    "Lyrics, one moment",
    "Tuning in the words",
    "Locating the verses",
    "Words are en route",
    "Checking the archives",
    "Piecing the lyrics together",
    "Loading up the words",
    "Lyric search underway",
    "Finding the right words",
    "Tracking the lyric sheet",
    "Verses incoming",
    "Getting the words lined up",
    "Hang tight, fetching lyrics",
    "Looking for the hook",
    "Words are loading",
    "Lyrics on their way",
    "Checking what's sung here",
    "Reading the room for lyrics",
    "Lyric lookup in progress",
    "Bringing up the words",
    "Just a sec, finding words",
    "Lyrics coming together",
)

private const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
private const val LYRICS_UNAVAILABLE_FADE_MS = 900

/**
 * Apple Music's Now Playing, closely: artwork that shrinks when paused, a
 * hairline scrubber with elapsed / remaining either side, oversized transport
 * glyphs, a volume capsule flanked by speaker icons, and lyrics / AirPlay /
 * queue along the bottom.
 */
@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    queue: List<Song>,
    queueIndex: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    autoplayEnabled: Boolean,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    /**
     * Seek to a fraction of the track, for the scrubber.
     *
     * Separate from [onSeek] because the scrubber is the one caller that knows
     * *where along the bar* it wants to go rather than a time. Converting that
     * here would use this screen's cached duration, which lags a track change by
     * however long the session takes to report the new one — long enough to drop
     * the handle on a bar still scaled to the previous song and seek to the
     * wrong fraction of the current one. The conversion belongs wherever the
     * freshest duration is.
     */
    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val syncedLyricsEnabled by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val hideVolumeBar by AppSettings.hideVolumeBar.collectAsStateWithLifecycle()

    // Animated cover art: the looping video some labels publish alongside a
    // release, laid over the sleeve. A miss is the normal answer — see
    // CanvasRepository, which is also where the "is this actually the right
    // track" check lives.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    var canvas by remember(song.videoId) { mutableStateOf<CanvasArtwork?>(null) }
    // Whether the clip actually has a frame on screen right now, and one of
    // them — used to blow the sleeve out to the full-bleed hero treatment and
    // to re-tint the backdrop off the clip's own colours rather than the
    // still sleeve's.
    var canvasRendered by remember(song.videoId) { mutableStateOf(false) }
    var canvasFrame by remember(song.videoId) { mutableStateOf<Bitmap?>(null) }
    val meshColors = rememberArtworkColors(song.thumbnailUrl, canvasFrame)
    LaunchedEffect(song.videoId, song.albumName, canvasEnabled) {
        if (!canvasEnabled) {
            canvas = null
            return@LaunchedEffect
        }
        // Anything already settled for this track paints immediately: a
        // reopened player, or a track coming round again in the queue.
        canvas = CanvasRepository.cached(song) ?: canvas

        // The album name is looked up separately and lands a moment after the
        // player opens, and it is the field that makes the catalogue searches
        // match. Give it that moment: if it arrives, this effect restarts and
        // all that was spent waiting is the wait. If it never does — a track
        // with no album, or a lookup that failed — the search still goes out,
        // just a beat later, which is imperceptible for decoration.
        if (canvas == null && song.albumName == null) delay(ALBUM_SETTLE_MS)
        // Keep what an earlier pass found if this one comes back empty, rather
        // than pulling a playing clip out from under itself.
        canvas = CanvasRepository.canvasFor(song) ?: canvas
    }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    // The queue lives inside the player, Apple-style, rather than in a sheet.
    var queueOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(song.videoId) { lyricsOpen = false }

    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }

    // 0 = full sleeve, 1 = queue. Everything that moves reads off this.
    val queueProgress by animateFloatAsState(
        targetValue = if (queueOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "queueProgress",
    )

    // Horizontal fling anywhere on the player skips tracks; the artwork
    // follows the finger so the gesture has something to hold on to.
    val swipeThreshold = with(density) { 72.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeSettle by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset",
    )

    // After releasing the scrubber the player needs to buffer before it
    // reports the new position. Keep showing where the user dropped it so the
    // handle doesn't snap back and then jump forward once loading finishes.
    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val shown = when {
        scrubbing -> scrubValue
        pendingSeek != null -> pendingSeek!!
        else -> fraction.coerceIn(0f, 1f)
    }

    // Released as soon as the player's own position agrees with where the handle
    // was dropped — and unconditionally a few seconds later whether it agrees or
    // not.
    //
    // The agreement test alone is not enough, because it is the only thing that
    // ever cleared the override: if the position never passes close to the
    // target — a clamped or rejected seek, a rendition swapped underneath, a
    // progress sample that steps straight over the window — nothing releases it
    // and the handle sits frozen at the drop point for the rest of the track.
    // Audio and lyrics follow the real position perfectly throughout, so the
    // failure looks like a stuck seek bar on a track that is playing fine.
    //
    // Tolerance is absolute rather than a share of the duration: two percent is
    // a quarter-second on a jingle and twelve seconds on a long mix, and it is
    // the wall-clock gap that decides whether the handle appears to jump.
    LaunchedEffect(positionMs, durationMs, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (durationMs > 0 && abs(positionMs - (target * durationMs).toLong()) < SEEK_SETTLE_TOLERANCE_MS) {
            pendingSeek = null
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SEEK_SETTLE_TIMEOUT_MS)
        pendingSeek = null
    }
    LaunchedEffect(song.videoId) { pendingSeek = null }

    // Signature Apple Music touch: the sleeve shrinks back while paused.
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artScale",
    )

    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    val scope = rememberCoroutineScope()
    // Animatable rather than plain state: a hardware volume step is a jump of
    // 1/15th of the bar, which reads as a stutter unless it's tweened.
    val volume = remember {
        Animatable(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume,
        )
    }
    var volumeDragging by remember { mutableStateOf(false) }
    var systemVolume by remember { mutableFloatStateOf(volume.value) }

    // Glide to the level the system reports, but never fight the finger — a
    // drag writes the stream, which calls straight back through here.
    LaunchedEffect(systemVolume) {
        if (!volumeDragging) {
            volume.animateTo(systemVolume, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    // Hardware volume keys and the system panel change the stream behind our
    // back — watch Settings for changes so the bar tracks them live.
    DisposableEffect(audioManager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
                systemVolume = current.toFloat() / maxVolume
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // 0 = the ordinary square sleeve, 1 = motion artwork as a full-bleed
    // banner. Both states collapse the header, but the banner only ever shows
    // over a settled player: opening the queue or the lyrics hands the sleeve
    // back its card first.
    val p = if (lyricsOpen) 1f else queueProgress
    // Full-bleed is a phone idiom. Past the width the player is willing to grow
    // to, edge to edge stops meaning "the artwork *is* the screen" and starts
    // meaning "a video, and separately some controls" — the banner would be
    // running a foot wider than the column of controls under it. Tablets keep
    // the sleeve, and the clip plays inside it as before.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val heroMode = CANVAS_HERO_SUPPORTED && screenWidth <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2
    val heroT by animateFloatAsState(
        targetValue = if (heroMode && canvasRendered && p < 0.5f) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "heroCanvas",
    )
    // How tall that banner is, worked out down in the layout where the sleeve's
    // own geometry is known. Zero until the first measure, which is fine: the
    // clip has no frame to show that early either.
    var heroHeight by remember { mutableStateOf(0.dp) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize()) {
        // Keyed on the track: the backdrop drifts when the player opens and on
        // every skip, then rests. Position ticks recompose this screen twice a
        // second and must not drag a full-screen blur along with them, which is
        // why the palette is passed as one immutable value.
        MeshGradientBackground(palette = meshColors, trackKey = song.videoId)

        // Motion artwork, edge to edge and running up behind the status bar,
        // dissolving into the backdrop where the sleeve's bottom edge would
        // have been. It lives out here rather than in the sleeve because that
        // is the only way to escape the player's side gutter and its status-bar
        // inset — a banner that stops short of either reads as a misplaced card
        // rather than as the artwork the screen is made of.
        //
        // Always composed while there's a clip to play, never gated on [heroT]:
        // the clip has to be mounted and decoding *before* it can report the
        // first frame that raises heroT in the first place.
        if (heroMode && heroHeight > 0.dp) {
            canvas?.takeIf { p < 0.5f }?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = isPlaying,
                    onRenderedChanged = { canvasRendered = it },
                    onFrameCaptured = { canvasFrame = it },
                    bottomFade = HERO_FADE_FRACTION,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(heroHeight),
                )
            }

            // The clock, the signal bars and the drag handle are all white, and
            // the banner puts whatever the video happens to open on directly
            // behind them — a bright frame leaves the top of the screen
            // unreadable. Faded in with the banner and gone with it.
            if (heroT > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(statusBarTop + DISMISS_STRIP_HEIGHT)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f * heroT),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { swipeOffset = 0f },
                        onDragEnd = {
                            when {
                                total <= -swipeThreshold && hasNext -> onNext()
                                total >= swipeThreshold && hasPrevious -> onPrevious()
                            }
                            swipeOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            total += delta
                            // Damped: it's a hint, not a drag-to-position.
                            swipeOffset = total * 0.35f
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The only strip that passes drags through to the sheet, so the
            // player closes from the handle and the space around it — not from
            // a stray downward swipe on the artwork or the controls.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DISMISS_STRIP_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.32f)),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Swallow vertical drags before the sheet can read them as
                    // "dismiss me". Children that scroll consume first, so the
                    // lists are unaffected. This sits outside the side padding
                    // on purpose: inside it, the two gutters were left as bare
                    // sheet, and a swipe that strayed into one closed the whole
                    // player instead of scrolling the lyrics or the queue.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, _ -> change.consume() }
                    }
                    .padding(horizontal = PLAYER_GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // ---- Top and centre: artwork, then the credits ----
            // Everything that changes between the artwork and the queue lives
            // in this one weighted box, so the controls below it never move.
            // Read up here rather than down by the scrubber: the stats-for-nerds
            // line now lives inside the sleeve itself, so the art Box below
            // needs these before the seek bar does.
            val showNerdStats by AppSettings.showNerdStats.collectAsStateWithLifecycle()
            val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(top = ART_BOX_TOP_PAD, bottom = 18.dp),
            ) {
                // The sleeve is square, so it is bounded by whichever of the
                // two axes runs out first: the player's width on a phone, or —
                // on a tablet, where there is width to spare — the height left
                // over once the credits row and the gap above it have had
                // theirs. Sizing it off the width alone is what pushed the
                // credits down across the scrubber on anything but a phone.
                val fullArt = minOf(maxWidth, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(THUMB_SIZE)
                // Artwork and the title row travel together as one block, so
                // the pair sits centred while the queue is closed.
                val groupTop = ((maxHeight - fullArt - ART_TITLE_GAP - HEADER_HEIGHT) / 2)
                    .coerceAtLeast(0.dp)
                val artSize = lerp(fullArt, THUMB_SIZE, p)
                val artTop = lerp(groupTop, 0.dp, p)
                // Expanded and height-bound, the sleeve is narrower than the
                // player and has to be centred in it; collapsed, it belongs
                // hard against the left edge with the credits beside it.
                val artStart = lerp((maxWidth - fullArt) / 2, 0.dp, p)
                val titleTop = lerp(groupTop + fullArt + ART_TITLE_GAP, 0.dp, p)
                val titleStart = lerp(0.dp, THUMB_SIZE + 12.dp, p)

                // How far down the *screen* the sleeve's bottom edge sits, which
                // is where the full-bleed banner has to stop for the credits
                // below it not to move when it appears. Everything between the
                // screen's top and this box's own top is fixed padding, so it
                // can simply be added back up rather than measured.
                val bannerBottom = statusBarTop + DISMISS_STRIP_HEIGHT + ART_BOX_TOP_PAD +
                    groupTop + fullArt + ART_TITLE_GAP / 2
                SideEffect { heroHeight = bannerBottom }

                // Empty state lives on this Box, not the AsyncImage: a
                // background *and* a painter both trying to fill the same
                // clipped shape is what read as two overlapping squares
                // whenever there was nothing to paint. One layer, one square.
                var artLoaded by remember(song.videoId) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .offset(x = artStart, y = artTop)
                        .size(artSize)
                        .graphicsLayer {
                            // The paused shrink and the swipe nudge only make
                            // sense on the full sleeve.
                            val idle = artScale + (1f - artScale) * p
                            scaleX = idle
                            scaleY = idle
                            translationX = swipeSettle * (1f - p)
                        }
                        // Collapsed, the sleeve is the way back: tapping the
                        // thumbnail puts the queue or the lyrics away again.
                        .then(
                            if (queueOpen || lyricsOpen) {
                                Modifier.clickable {
                                    queueOpen = false
                                    lyricsOpen = false
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // The sleeve proper. Separated from the box around it so
                    // the banner can dissolve the card — shadow, corners, tile
                    // and all — without taking the stats line with it.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 1f - heroT }
                            // A drop shadow grounds a photo; on the flat
                            // placeholder tile it has nothing to sit behind, so
                            // it just reads as a second, darker square ringing
                            // the first. Only cast it once there's actually art.
                            .shadow(
                                if (artLoaded) lerp(14.dp, 6.dp, p) else 0.dp,
                                RoundedCornerShape(lerp(10.dp, 7.dp, p)),
                            )
                            .clip(RoundedCornerShape(lerp(10.dp, 7.dp, p)))
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!artLoaded) {
                            Icon(
                                imageVector = BitChordIcons.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(lerp(40.dp, 20.dp, p)),
                            )
                        }
                        AsyncImage(
                            // Decode at the sleeve's *expanded* size, always.
                            // Coil otherwise sizes the decode to however large
                            // this is when the request goes out — and changing
                            // track from the queue does that while the sleeve is
                            // collapsed to a thumbnail, leaving a thumbnail-sized
                            // bitmap to be blown back up when the queue closes.
                            // Skipping tracks with the transport keeps it sharp
                            // only because the sleeve happens to be full size at
                            // that moment.
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.artworkAt(ART_PX))
                                .size(with(LocalDensity.current) { fullArt.roundToPx() })
                                .build(),
                            contentDescription = null,
                            // Video thumbnails are 16:9; letterboxing them inside
                            // the square sleeve looks like a broken frame.
                            contentScale = ContentScale.Crop,
                            onState = { artLoaded = it is AsyncImagePainter.State.Success },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Where the clip plays when it can't have the banner:
                        // inside the same clip as the still art, taking the
                        // sleeve's corners, shadow and paused shrink for free.
                        if (!heroMode) {
                            canvas?.takeIf { p < 0.5f }?.let { clip ->
                                CanvasArtworkPlayer(
                                    canvas = clip,
                                    isPlaying = isPlaying,
                                    onRenderedChanged = { canvasRendered = it },
                                    onFrameCaptured = { canvasFrame = it },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    // Measured stats, pinned to the sleeve's own bottom-centre
                    // rather than squeezed under the seek bar with the
                    // "Lossless" badge — the badge is a claim, this is the
                    // evidence, and the two no longer swap for each other on a
                    // tap. Fades out with the sleeve as it collapses to a
                    // thumbnail, where there's no room to read it anyway.
                    if (showNerdStats && p < 0.5f) {
                        nerdStats?.describe()?.let { stats ->
                            Text(
                                text = stats,
                                // A plain white line reads fine over the usual
                                // dark tile, but a light stretch of an animated
                                // cover — sky, snow, a pale sleeve — washes it
                                // out entirely. The shadow costs nothing on a
                                // dark background and is what keeps it legible
                                // on a bright one.
                                style = MaterialTheme.typography.labelSmall.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        offset = Offset(0f, 1f),
                                        blurRadius = 4f,
                                    ),
                                ),
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .graphicsLayer { alpha = 1f - p * 2f },
                            )
                        }
                    }
                }

                // Sits in the gap under the sleeve, clear of its rounded
                // corners and shadow — no box, no clip, nothing for the art
                // itself to be cropped by. Just a glyph that fades in with
                // the drag to hint which way a release would skip.
                val swipeHintProgress = (abs(swipeSettle) / swipeThreshold)
                    .coerceIn(0f, 1f) * (1f - p) * (1f - heroT)
                if (swipeHintProgress > 0.01f) {
                    val showNext = swipeSettle < 0f
                    val enabled = if (showNext) hasNext else hasPrevious
                    Icon(
                        imageVector = if (showNext) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = Color.White.copy(
                            alpha = swipeHintProgress * if (enabled) 0.85f else 0.3f,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artTop + artSize + (ART_TITLE_GAP - 16.dp) / 2)
                            .size(16.dp),
                    )
                }

                // ---- Title + menu ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = titleTop)
                        .padding(start = titleStart)
                        .height(HEADER_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // Shrinks as the header collapses, so the queue's
                        // heading doesn't have to compete with it.
                        val titleSize = lerp(20.sp, 16.sp, p)
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = titleSize,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Only the tracks YouTube hands us a browse id for
                            // lead anywhere; the rest stay plain text.
                            modifier = Modifier.opensPage(song.albumId, onOpenAlbum),
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.W500,
                                fontSize = titleSize,
                            ),
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.opensPage(song.artistId, onOpenArtist),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // Beside the credits rather than down in the toggle row:
                    // liking is about *this song*, and the row below is about
                    // how the queue plays. Guests get nothing to tap, since
                    // there's no account to record it against — and neither
                    // does a local file or a finished download, which carries
                    // no YouTube identity to rate.
                    if (signedIn && song.localUri == null) {
                        val liked = likeStatus == LikeStatus.LIKE
                        CircleGlyph(
                            icon = if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                            contentDescription = if (liked) "Remove from Liked Music" else "Like",
                            onClick = onToggleLike,
                            active = liked,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    CircleGlyph(
                        icon = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
                        onClick = onOpenMenu,
                    )
                }

                if (lyricsOpen) {
                    LyricsPanel(
                        lines = lyrics.orEmpty(),
                        positionMs = positionMs,
                        isPlaying = isPlaying,
                        onSeekToLine = onSeek,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT + 10.dp),
                    )
                }

                // Toggles and the queue arrive after the sleeve has finished
                // travelling, and leave before it starts coming back.
                if (!lyricsOpen && queueProgress > 0.01f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT + 10.dp)
                            .graphicsLayer {
                                alpha = ((queueProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                translationY = (1f - queueProgress) * 26.dp.toPx()
                            },
                    ) {
                        InlineQueue(
                            queue = queue,
                            currentIndex = queueIndex,
                            autoplayEnabled = autoplayEnabled,
                            onJumpTo = onJumpTo,
                            onRemove = onRemoveFromQueue,
                            onMove = onMoveInQueue,
                            onClear = onClearQueue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Bottom: lyric strip, scrubber, transport, volume, toggles ----
            // One block, measured at its natural height and pinned to the foot
            // of the player. Whatever is left over above it is the artwork's,
            // which is what keeps this row of controls in the same place on
            // every screen instead of being shoved off the bottom of a tall one.
            Column(
                modifier = Modifier
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Current lyric, one line, directly above the scrubber. It stays in
            // the layout while the queue is open and only fades — dropping it
            // would shorten this block, and the controls under it would jump
            // the moment the queue started sliding in.
            //
            // Switched off in Settings it goes entirely, rather than sitting
            // there saying no lyrics were found: none were looked for. It is
            // also the only way into the full lyrics panel, so with it gone
            // the feature is properly gone.
            if (!lyricsOpen && syncedLyricsEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The slider's touch target reaches ~13dp above the
                        // drawn bar, so the strip reads as further off it than
                        // it is. Nudged down into that dead space, the same way
                        // the timestamps below are pulled back up into it.
                        .offset(y = 6.dp)
                        .graphicsLayer { alpha = 1f - queueProgress },
                ) {
                    if (!lyrics.isNullOrEmpty()) {
                        CurrentLyricLine(
                            lines = lyrics,
                            trackKey = song.videoId,
                            positionMs = positionMs,
                            isPlaying = isPlaying,
                            durationMs = durationMs,
                            // Faded out behind the queue, so it must not still
                            // be a target for a tap meant for the list.
                            onClick = { if (!queueOpen) lyricsOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (lyricsUnavailable) {
                        LyricsUnavailableLine(
                            trackKey = song.videoId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LyricsLoadingLine(
                            trackKey = song.videoId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            ThinSlider(
                value = shown,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    pendingSeek = scrubValue
                    onSeekFraction(scrubValue)
                    scrubbing = false
                },
            )
            val losslessOn by AppSettings.losslessAudio.collectAsStateWithLifecycle()
            val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
            val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
            val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
            // Whether this playback session is even asking for a lossless
            // stream — the same computation SourceResolver.requestForNow()
            // makes, mirrored here so "Loading lossless" only appears when a
            // lossless fetch is actually in flight, not on every buffering
            // YouTube track.
            val losslessRequested = losslessOn &&
                (if (metered == true) cellularQuality else wifiQuality) == AudioQuality.HIGH
            // Whether a module is still racing YouTube for this exact track —
            // see [NerdStats.racingLossless]. YouTube can win that race and
            // already be playing while the module lookup is still running
            // detached in the background, and the badge should keep saying
            // "loading" through that stretch rather than going blank only to
            // possibly say "loading" again a moment later.
            val racingLossless by NerdStats.racingLossless.collectAsStateWithLifecycle()
            val stillRacing = song.videoId in racingLossless
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The slider's touch target extends well past the drawn
                    // bar, so pull the labels back up under it.
                    .offset(y = (-9).dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime((shown * durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = "-" + formatTime(durationMs - (shown * durationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                // Pinned to the box's own center rather than squeezed into the
                // gap between the two timestamps: that gap's width changes by
                // a digit's worth every time a minute rolls over, which was
                // dragging this along with it every tick. The screen's center
                // doesn't move.
                LosslessOrStats(
                    isLoading = isLoading,
                    stillRacing = stillRacing,
                    losslessRequested = losslessRequested,
                    nerdStats = nerdStats,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                )
            }

            if (lyricsOpen) {
                Spacer(Modifier.height(16.dp))
                // Where these particular lyrics came from. This spot used to
                // hold a Close button, which the sleeve above already does —
                // and with four databases behind the panel, whose timings you
                // are looking at is the more useful thing to know. Not a
                // control: nothing to press, it just says who to credit.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = lyricsSource?.let { "Lyrics by ${it.label}" }
                            ?: "No lyrics found",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(20.dp))
            } else {

            Spacer(Modifier.height(14.dp))

            // ---- Transport ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportGlyph(
                    icon = Icons.Rounded.FastRewind,
                    contentDescription = "Previous",
                    size = 46.dp,
                    onClick = onPrevious,
                    // Lit whenever back has something to do — either a track to
                    // step to, or enough elapsed for it to restart this one.
                    enabled = hasPrevious || positionMs > BACK_RESTARTS_AFTER_MS,
                )
                // While the stream URL resolves and buffers, the play glyph
                // would be a lie — show progress instead.
                if (isLoading) {
                    // Same footprint as TransportGlyph(62.dp) — a smaller box
                    // here would shunt everything below it on every load.
                    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                } else {
                    TransportGlyph(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        size = 62.dp,
                        onClick = onPlayPause,
                    )
                }
                TransportGlyph(
                    icon = Icons.Rounded.FastForward,
                    contentDescription = "Next",
                    size = 46.dp,
                    onClick = onNext,
                    enabled = hasNext,
                )
            }

            // Hidden entirely rather than just faded out — with the setting
            // on, the slider takes up no space at all, so the transport and
            // the toggle row below it close the gap instead of leaving a
            // blank strip where the volume bar used to be.
            if (hideVolumeBar) {
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(18.dp))

                // ---- Volume ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    ThinSlider(
                        value = volume.value,
                        onValueChange = {
                            volumeDragging = true
                            // Follow the finger exactly; only external changes tween.
                            scope.launch { volume.snapTo(it) }
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (it * maxVolume).roundToInt(),
                                0,
                            )
                        },
                        onValueChangeFinished = { volumeDragging = false },
                        idleHeight = 6.dp,
                        activeHeight = 10.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            // ---- Shuffle · Repeat · AutoPlay · Queue ----
            // These live here rather than in the queue panel so their state is
            // readable without opening anything.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomGlyph(
                    icon = BitChordIcons.Shuffle,
                    contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                    onClick = onToggleShuffle,
                    highlighted = shuffleEnabled,
                )
                BottomGlyph(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                        BitChordIcons.RepeatOne
                    } else {
                        BitChordIcons.Repeat
                    },
                    contentDescription = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    onClick = onCycleRepeat,
                    highlighted = repeatMode != Player.REPEAT_MODE_OFF,
                )
                BottomGlyph(
                    icon = BitChordIcons.Infinity,
                    contentDescription = if (autoplayEnabled) "AutoPlay on" else "AutoPlay off",
                    onClick = onToggleAutoplay,
                    highlighted = autoplayEnabled,
                )
                BottomGlyph(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Up next",
                    onClick = {
                        lyricsOpen = false
                        queueOpen = !queueOpen
                    },
                    highlighted = queueOpen,
                )
            }

            Spacer(Modifier.height(18.dp))
            }
            }
            }
        }
    }
}


/**
 * The song position, ticking every frame.
 *
 * The player reports where it is about twice a second, which is fine for a
 * scrubber and far too coarse for a highlight that has to keep up with a
 * singer. This carries that report forward on the frame clock between
 * reports, and resets to the real value whenever a fresh one lands — so it
 * never drifts, it just fills in.
 *
 * Returned as state rather than a plain value on purpose: read inside a draw
 * lambda, only the draw phase re-runs each frame. Read in composition, the
 * whole line would recompose sixty times a second.
 */
@Composable
private fun rememberLyricClock(positionMs: Long, isPlaying: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying) {
        clock.longValue = positionMs
        if (!isPlaying) return@LaunchedEffect
        var previousFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                clock.longValue += frame - previousFrame
                previousFrame = frame
            }
        }
    }
    return clock
}

/**
 * A lyric line with the sung part of it lit, the rest dimmed, and the boundary
 * travelling across the words in time with the vocal.
 *
 * Two copies of the same text stacked: a dim one and a bright one clipped to
 * whatever has been sung. Same string, same style, same constraints, so the
 * two lay out identically and the bright copy lands exactly on top of the dim
 * one. The alternative — colouring an AnnotatedString word by word — can only
 * change a whole word at a time, which turns the sweep into a flicker.
 *
 * The clip is recomputed in the draw phase, so a frame costs one clip and one
 * redraw of already-measured text.
 *
 * [glowAlpha] adds Apple's bloom: a third copy, blurred, behind the other two
 * and clipped to the same boundary. Blurring *after* the clip rather than
 * before is what makes the halo bleed a little way past the sweep's leading
 * edge, which is the part that reads as light coming off the word being sung
 * rather than a drop shadow sitting under the line.
 */
@Composable
private fun SweptLyricLine(
    line: LyricLine,
    clock: MutableLongState,
    style: TextStyle,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    glowAlpha: Float = 0f,
    glowRadius: Dp = GLOW_RADIUS,
    glowRoom: Dp = 0.dp,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    // Carried by every copy: identical insets keep them laying out identically,
    // and the inset is what gives the blurred copy's layer somewhere to put the
    // halo. Sits inside the blur and outside the draw lambdas, so text-layout
    // coordinates and draw coordinates still agree.
    //
    // Off unless asked for. Only the full panel can afford it — it takes the
    // space back off its own row spacing and content padding. Handed to the
    // one-line strip above the scrubber, where there is no glow to make room
    // for and nothing paying the space back, it just left the line sitting in
    // a pocket of air with the chevron pushed off it.
    val room = if (glowRoom > 0.dp) Modifier.padding(glowRoom) else Modifier

    val sweep = Modifier.drawWithContent {
        val position = clock.longValue
        when {
            // Sung and done with: all of it is lit. Checked first so the lines
            // above and below the playing one — which are in this same state
            // for minutes at a time — cost a comparison per frame rather than
            // a walk of their words.
            position >= line.endMs -> drawContent()
            // Not started: nothing lit, the dim copy is the whole of it.
            position <= line.timeMs -> Unit
            else -> layout?.let { sweepTo(it, line.revealedChars(position)) }
        }
    }

    Box(modifier) {
        Text(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
            modifier = room,
        )
        if (glowAlpha > 0.01f) {
            Text(
                text = line.text,
                style = style,
                color = Color.White,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier
                    // Read in the layer block rather than in composition: the
                    // intensity changes every frame, and this way only the
                    // layer's alpha is recomputed, not the line.
                    .graphicsLayer { alpha = glowAlpha * line.glowIntensity(clock.longValue) }
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    .then(room)
                    // The band is masked with a DstIn gradient, which needs a
                    // layer of its own to erase into — against the backdrop it
                    // would take the artwork with it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        // Deliberately not the shared sweep: that lights
                        // everything sung so far, and this is only the front of
                        // it. No short-circuit either — the glow layer only
                        // exists for the line being sung, so it is one line's
                        // worth of arithmetic, not the whole panel's.
                        val measured = layout ?: return@drawWithContent
                        val position = clock.longValue
                        glowAt(
                            layout = measured,
                            revealedChars = line.revealedChars(position),
                            intensity = line.glowIntensity(position),
                        )
                    },
            )
        }
        Text(
            text = line.text,
            style = style,
            color = Color.White,
            maxLines = maxLines,
            overflow = overflow,
            modifier = room.then(sweep),
        )
    }
}

/**
 * Draws this text clipped to a band trailing the sweep's leading edge — the
 * word being sung, roughly, rather than the whole of what has been.
 *
 * The band widens with [intensity] as well as brightening, so a held note
 * spreads its light over the words either side of it while patter keeps its
 * halo tight to the one syllable. Alpha alone made every word glow the same
 * shape, only more or less of it.
 *
 * Only ever one band: the edge is on exactly one visual line, and a wrapped
 * line's previous row has already been left behind by the time the band would
 * have reached back into it.
 */
private fun ContentDrawScope.glowAt(
    layout: TextLayoutResult,
    revealedChars: Float,
    intensity: Float,
) {
    val length = layout.layoutInput.text.length
    if (length == 0 || revealedChars <= 0f || intensity <= 0f) return

    val edge = revealedChars.coerceIn(0f, length.toFloat())
    val visualLine = layout.getLineForOffset(edge.toInt().coerceIn(0, length - 1))
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)

    val right = horizontalAt(layout, edge.coerceIn(lineStart.toFloat(), lineEnd.toFloat()), lineStart, lineEnd)
    val trail = GLOW_TRAIL.toPx() * (GLOW_TRAIL_FLOOR + (1f - GLOW_TRAIL_FLOOR) * intensity)
    val left = (right - trail).coerceAtLeast(layout.getLineLeft(visualLine))
    if (right <= left) return

    // The band, cut out of the line. This is only the vertical and trailing
    // bounds; how it fades across is the mask below.
    clipRect(
        left = left,
        top = layout.getLineTop(visualLine),
        right = right,
        bottom = layout.getLineBottom(visualLine),
    ) {
        this@glowAt.drawContent()
    }

    // Full strength at the leading edge, ebbing away behind it. Without this
    // the band has a hard back edge, and a hard edge travelling along at a
    // constant distance behind the sweep is exactly what reads as a fixed-width
    // block of light being dragged across the words.
    //
    // Painted over the whole node rather than inside the clip on purpose:
    // DstIn keeps what the mask covers and erases the rest, and the brush
    // clamps past its ends — transparent to the left of the band, opaque to
    // the right, where the clip has already left nothing to keep.
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.45f to Color.White.copy(alpha = 0.22f),
            1f to Color.White,
            startX = left,
            endX = right,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/** Where a fractional character index sits across a visual line, in pixels. */
private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    lineStart: Int,
    lineEnd: Int,
): Float {
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    val here = layout.getHorizontalPosition(index, usePrimaryDirection = true)
    val next = layout.getHorizontalPosition(
        (index + 1).coerceAtMost(lineEnd),
        usePrimaryDirection = true,
    )
    return here + (next - here) * (chars - index)
}

/**
 * Draws this text clipped to its first [revealedChars] characters.
 *
 * Wrapped lines are handled a visual line at a time: the ones already passed
 * are drawn whole, the one holding the boundary is cut at it, and the rest are
 * left to the dim copy. Within a word the cut sits between two character
 * positions, so the edge advances smoothly rather than jumping a letter at a
 * time.
 */
private fun ContentDrawScope.sweepTo(layout: TextLayoutResult, revealedChars: Float) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        // Lines beyond the boundary have nothing lit on them, and neither has
        // anything after them.
        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val right = if (revealedChars >= end) {
            layout.getLineRight(visualLine)
        } else {
            horizontalAt(layout, revealedChars, start, end)
        }
        clipRect(
            left = layout.getLineLeft(visualLine),
            top = layout.getLineTop(visualLine),
            right = right,
            bottom = layout.getLineBottom(visualLine),
        ) {
            this@sweepTo.drawContent()
        }
    }
}


/**
 * Apple Music's lyrics view: big tight type, the playing line crisp and
 * everything else falling out of focus the further it is from it. Blur needs
 * API 31+, so alpha carries the same hierarchy on older devices.
 *
 * Scrolling by hand clears the blur and suspends the auto-follow, so you can
 * read ahead; a couple of seconds after you stop it snaps back to the song.
 */
@Composable
private fun LyricsPanel(
    lines: List<LyricLine>,
    positionMs: Long,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = rememberLyricClock(positionMs, isPlaying)

    // Which line is playing right now: the last one whose stamp has passed.
    //
    // Read off the frame clock rather than the player's own position, which
    // only lands twice a second. Taken from there, a line change was up to
    // half a second late — and with the highlight itself running on the frame
    // clock, that lateness was visible: the sweep would finish a line and sit
    // at the end of it, waiting for the screen to admit the next one had
    // started. derivedStateOf keeps the cost of the finer clock off
    // composition; it only notifies when the index actually changes, not on
    // every frame that feeds it.
    val activeLine by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= clock.longValue } }
    }
    val listState = rememberLazyListState()
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    var browsing by remember { mutableStateOf(false) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    // The bloom is a blurred copy of the line, so it is off wherever blur is:
    // below API 31 Modifier.blur does nothing and the "glow" would land as a
    // second sharp copy of the text — fake bold, not light. Both of the
    // reduce-* settings turn it off too. Reduce animation because it is the
    // switch for exactly this kind of flourish, and reduce dynamic blur
    // because adding a blur under a setting that says it drops them would be
    // the app disagreeing with itself.
    val glowing = !reduceAnimation && !reduceDynamicBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Only a finger on the list counts as browsing — watching
    // isScrollInProgress would trip on our own auto-scroll.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) browsing = true
        }
    }

    // Hand control back as soon as the playing line is on screen again,
    // whether the user scrolled to it or the song caught up to them.
    // rememberUpdatedState matters: read plainly, the derived state would
    // capture whichever line was active when it was first created.
    val currentLine by rememberUpdatedState(activeLine)
    val activeOnScreen by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == currentLine }
        }
    }
    LaunchedEffect(browsing, activeOnScreen, listState.isScrollInProgress) {
        if (browsing && activeOnScreen && !listState.isScrollInProgress) {
            delay(600)
            browsing = false
        }
    }

    // And give up browsing on its own after a while, wherever the list is.
    LaunchedEffect(browsing, listState.isScrollInProgress) {
        if (browsing && !listState.isScrollInProgress) {
            delay(5_000)
            browsing = false
        }
    }

    // Follow the song, keeping the active line a third of the way down.
    //
    // Gated on isScrollInProgress as well as browsing: browsing flips true from
    // a Flow collecting DragInteraction.Start, which lags a frame or two behind
    // the actual touch. A line change landing in that gap started this
    // animated scroll underneath a finger already dragging, and the ensuing
    // fight over the list's MutatorMutex was what leaked a stray scroll past
    // keepScrollInList and down to the sheet — reading the list's own
    // (synchronous) scroll state closes that window.
    //
    // The very first placement is a jump, not a scroll. The panel is built
    // fresh each time it is opened, so an animated scroll there is the whole
    // song racing past from the top before settling — which is where the
    // stutter on opening came from. Later moves, which are one line at a time,
    // still animate.
    var placed by remember(lines) { mutableStateOf(false) }
    LaunchedEffect(activeLine, browsing) {
        if (!browsing && !listState.isScrollInProgress &&
            activeLine >= 0 && activeLine in lines.indices
        ) {
            // A third of the way down the panel, whatever the panel's size — a
            // fixed pixel offset lands in a different place on every screen,
            // and on a tablet it put the playing line near the very top.
            // Measured height is 0 until the list has been laid out once,
            // which on the opening frame is exactly when this runs.
            val viewport = snapshotFlow { listState.layoutInfo.viewportSize.height }
                .first { it > 0 }
            val third = viewport / 3
            if (placed) {
                listState.animateScrollToItem(activeLine, scrollOffset = -third)
            } else {
                listState.scrollToItem(activeLine, scrollOffset = -third)
                placed = true
            }
        }
    }

    if (lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No lyrics for this track",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .bleedHorizontally(PLAYER_GUTTER)
            .nestedScroll(keepScroll)
            .fadingEdges(),
        // Each row carries GLOW_ROOM of its own inset for the halo, so the
        // list hands that much back — otherwise the lines would sit a glow's
        // width further apart and further in than they used to.
        contentPadding = PaddingValues(
            vertical = 40.dp - GLOW_ROOM,
            horizontal = PLAYER_GUTTER - GLOW_ROOM,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val distance = if (activeLine < 0) 0 else abs(index - activeLine)
            val isActive = index == activeLine
            // Unbounded, and ahead of the clip: the default edge treatment cuts
            // the blur off at the line's own box, which put a hard edge down
            // either side of every out-of-focus line where the halo should have
            // faded out. The list bleeds a gutter wider than its content
            // padding, so there is room for the spill.
            val blur by animateDpAsState(
                targetValue = when {
                    reduceDynamicBlur || browsing || isActive -> 0.dp
                    else -> (distance * 1.6f).coerceAtMost(7f).dp
                },
                label = "lyricBlur",
            )
            val lineAlpha by animateFloatAsState(
                targetValue = when {
                    browsing -> 1f
                    isActive -> 1f
                    else -> (0.5f - distance * 0.06f).coerceAtLeast(0.22f)
                },
                label = "lyricAlpha",
            )
            if (line.isGap) {
                val noteSize by animateDpAsState(
                    targetValue = if (isActive) 34.dp else 26.dp,
                    label = "noteSize",
                )
                Icon(
                    imageVector = BitChordIcons.MusicNote,
                    contentDescription = "Instrumental",
                    tint = Color.White.copy(alpha = lineAlpha),
                    modifier = Modifier
                        .blur(blur, BlurredEdgeTreatment.Unbounded)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSeekToLine(line.timeMs) }
                        // Matches the inset every sung line carries, so the
                        // rhythm of the list doesn't break at a break.
                        .padding(GLOW_ROOM)
                        .size(noteSize),
                )
            } else {
                val style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                )
                // The playing line swells a touch. Anchored to its left edge,
                // so the words don't slide sideways under the highlight as it
                // grows — scaling about the centre would fight the sweep.
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.04f else 1f,
                    label = "lyricScale",
                )
                // Apple's bloom on the line being sung. Fades in and out with
                // the line rather than switching, so a handover is one line's
                // light going down as the next one's comes up.
                val glow by animateFloatAsState(
                    targetValue = if (isActive && glowing) GLOW_ALPHA else 0f,
                    animationSpec = tween(durationMillis = 420),
                    label = "lyricGlow",
                )
                val shape = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        alpha = lineAlpha
                    }
                    .blur(blur, BlurredEdgeTreatment.Unbounded)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSeekToLine(line.timeMs) }
                if (line.isWordSynced && !browsing) {
                    // Every word-synced line goes through the sweep, not just
                    // the playing one — a line that has already been sung is
                    // fully revealed and one still to come is not, which falls
                    // out of the same arithmetic.
                    //
                    // Running it only on the active line meant swapping this
                    // composable for a plain Text the instant a line handed
                    // over, and the two disagreed about the brightness of the
                    // words: the tail of the line popped up to meet the rest of
                    // it in a single frame. Animating the tail instead lets a
                    // finished line close up as it dims away.
                    val tail by animateFloatAsState(
                        targetValue = if (isActive) UNSUNG_ALPHA else 1f,
                        label = "lyricTail",
                    )
                    SweptLyricLine(
                        line = line,
                        clock = clock,
                        style = style,
                        dimAlpha = tail,
                        modifier = shape,
                        glowAlpha = glow,
                        glowRoom = GLOW_ROOM,
                    )
                } else {
                    Text(
                        text = line.text,
                        style = style,
                        color = Color.White,
                        modifier = shape.padding(GLOW_ROOM),
                    )
                }
            }
        }
    }
}


/**
 * The single lyric line above the scrubber.
 *
 * A line dims away just before its time is up and the next one arrives at full
 * strength — no fade in, so the change reads as a cut rather than a dissolve.
 * The fade is a fraction of the line's own length, so rapid-fire lines snap and
 * long held ones ebb out.
 *
 * Position is interpolated between the player's twice-a-second reports,
 * otherwise the fade would step. The alpha is applied in a graphicsLayer so
 * only the draw phase runs each frame; the text itself recomposes just once
 * per line.
 */
@Composable
private fun CurrentLyricLine(
    lines: List<LyricLine>,
    trackKey: Any,
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = rememberLyricClock(positionMs, isPlaying)

    val index by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= clock.longValue } }
    }
    val current = lines.getOrNull(index)
    // Before the first line, and through instrumental breaks, show the note.
    val instrumental = current == null || current.isGap
    // Everything ahead of the first sung line is the intro — LRC files open on a
    // bare [00:00.00] gap, so that stretch is gap lines rather than nothing.
    val firstSung = remember(lines) { lines.indexOfFirst { !it.isGap } }
    val intro = instrumental && firstSung >= 0 && index < firstSung
    // The intro gets one of the slang lines; mid-song breaks stay plain.
    val introLine = remember(trackKey) { INTRO_LINES.random() }
    val text = when {
        intro -> introLine
        instrumental -> INSTRUMENTAL_MARK
        else -> current!!.text
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer {
                if (instrumental) {
                    // Nothing is being sung; hold it steady rather than fading.
                    alpha = 0.5f
                    return@graphicsLayer
                }
                val start = lines.getOrNull(index)?.timeMs ?: 0L
                val end = lines.getOrNull(index + 1)?.timeMs
                    ?: durationMs.takeIf { it > start }
                    ?: (start + 4_000L)
                val fade = ((end - start) * LYRIC_FADE_FRACTION)
                    .coerceIn(LYRIC_FADE_MIN_MS, LYRIC_FADE_MAX_MS)
                val remaining = (end - clock.longValue).toFloat()
                alpha = 0.78f * (remaining / fade).coerceIn(0f, 1f)
            },
    ) {
        if (instrumental) {
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        val swept = current?.takeIf { !instrumental && it.isWordSynced }
        if (swept != null) {
            SweptLyricLine(
                line = swept,
                clock = clock,
                style = MaterialTheme.typography.titleMedium,
                dimAlpha = UNSUNG_ALPHA_STRIP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.width(6.dp))
        // Disclosure hint: this strip opens the full lyrics screen.
        Icon(
            imageVector = BitChordIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Stands in for [CurrentLyricLine] once a lookup has come back empty — shown
 * for a few seconds so it registers, then left to fade rather than snapping
 * out or lingering for the rest of the track.
 */
@Composable
private fun LyricsUnavailableLine(trackKey: Any, modifier: Modifier = Modifier) {
    var visible by remember(trackKey) { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        delay(LYRICS_UNAVAILABLE_HOLD_MS)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(durationMillis = LYRICS_UNAVAILABLE_FADE_MS),
        label = "lyricsUnavailableAlpha",
    )
    Text(
        text = "Lyrics not available",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(vertical = 4.dp)
            .graphicsLayer { this.alpha = alpha },
    )
}

/** Stands in for [CurrentLyricLine] while a lookup is still in flight. */
@Composable
private fun LyricsLoadingLine(trackKey: Any, modifier: Modifier = Modifier) {
    val text = remember(trackKey) { LYRICS_LOADING_LINES.random() }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * Translucent circular button used for the track menu and the like control.
 *
 * [active] brightens the disc rather than only the glyph: this sits on album
 * artwork of any colour, and a white icon on a white-ish sleeve has no tint
 * change left to make. The filled heart carries the state as a shape too —
 * see [BitChordIcons.HeartFilled].
 */
@Composable
private fun CircleGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "glyphDisc",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = discAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * Transport / bottom glyphs. The circular clip belongs on the touch target,
 * never on the [Icon] — clipping the icon itself shaves the corners off wide
 * glyphs like fast-forward and the queue list.
 */
@Composable
private fun TransportGlyph(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    // Faded rather than hidden: the row keeps its shape at the ends of a queue.
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun BottomGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) Color.White.copy(alpha = 0.20f) else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Swallows whatever scroll the queue list itself didn't use. The player is a
 * ModalBottomSheet, and the sheet's own nested-scroll handler reads that
 * leftover as "drag me down" — so scrolling the queue would slide the player
 * away. Consuming it here keeps the gesture inside the list.
 *
 * A downward *fling* has to be caught in the pre-phase, before the sheet sees
 * it, but only at the top of the list — otherwise the queue could never fling.
 */
private fun keepScrollInList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** A credit that links somewhere, when [browseId] is known. */
private fun Modifier.opensPage(browseId: String?, onOpen: (String) -> Unit): Modifier =
    if (browseId == null) {
        this
    } else {
        clip(RoundedCornerShape(6.dp)).clickable { onOpen(browseId) }
    }

/**
 * Measure a child wider than its slot by [gutter] on each side and place it back
 * over that margin, still reporting the original width to the parent.
 *
 * The lists are the only things in the player you can scroll, and the side
 * padding left a strip of bare sheet down each edge. A finger that drifted into
 * one scrolled nothing and closed the player instead. Matching content padding
 * puts every row back exactly where it was drawn, so this is invisible.
 */
private fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) {
        placeable.place(-(placeable.width - width) / 2, 0)
    }
}

/** Softens the list where it meets the header and the scrubber. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** The live queue, in the player itself. */
@Composable
private fun InlineQueue(
    queue: List<Song>,
    currentIndex: Int,
    autoplayEnabled: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    // Where AutoPlay's tracks start. The queue is kept with them last, so this
    // is one boundary rather than a category to test row by row.
    val autoplayStart = remember(queue, currentIndex) {
        autoplaySectionStart(queue.map { it.fromAutoplay }, currentIndex)
    }
    // Open on what's playing, not at the top of a long queue. The heading sits
    // between the two sections, so it counts as a row once it's above this one.
    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex + if (currentIndex >= autoplayStart) 1 else 0)
        }
    }

    // Each section reorders on its own — a drag never crosses the line
    // between what was queued by hand and what AutoPlay picked, same as
    // [addToQueue] and [playNext] already respect it.
    //
    // Both draw straight from the live [queue], never from a snapshot taken
    // when the drag began: the boundary between the sections moves on its own
    // as tracks play, so a frozen copy of either one goes stale the moment it
    // does — AutoPlay's section would keep listing tracks that have long
    // since played, and the row indices behind `onJumpTo`/`onRemove` would
    // start pointing at the wrong songs. Each swap is sent to the player as
    // it happens instead, and the rows animate into place off the live order.
    val manualRows = queue.subList(0, autoplayStart)
    val autoplayRows = queue.subList(autoplayStart, queue.size)
    // A song can be queued twice, so videoId alone isn't always a unique key
    // — LazyColumn throws on a repeat. Suffixing by how many times that id
    // has already been seen keeps every key unique while staying stable
    // across a reorder, which plain videoId+index (the previous key) wasn't:
    // that changed on every swap and silently broke animateItem's ability to
    // tell "this row moved" from "this row was replaced".
    val manualKeys = remember(manualRows) { manualRows.stableQueueKeys() }
    val autoplayKeys = remember(autoplayRows) { autoplayRows.stableQueueKeys("autoplay/") }

    // The heading is a row of the same LazyColumn, so it shifts every
    // AutoPlay index below it along by one — hence the offset back to queue
    // indices, which is what [onMove] and the rest of the callbacks take.
    val headingShown = autoplayEnabled || autoplayStart < queue.size
    val headingCount = if (headingShown) 1 else 0
    // Nothing moves at or above the track playing right now: what's already
    // been played is history, and the current row is the boundary the sections
    // are drawn from. Only what's still to come is the user's to reorder.
    // AutoPlay's section needs no such limit — [autoplaySectionStart] always
    // puts it after the current track.
    val firstMovable = (currentIndex + 1).coerceIn(0, autoplayStart)
    val manualDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = firstMovable until autoplayStart,
        lazyOffset = 0,
        onMove = onMove,
    )
    val autoplayDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = (autoplayStart + headingCount) until (autoplayStart + headingCount + autoplayRows.size),
        lazyOffset = headingCount,
        onMove = onMove,
    )

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Clear",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(PLAYER_GUTTER)
                // Without this the sheet treats the list's leftover scroll as a
                // drag on itself and slides the whole player away.
                .nestedScroll(keepScroll)
                .fadingEdges(),
            contentPadding = PaddingValues(horizontal = PLAYER_GUTTER),
        ) {
            // What was asked for: the album, playlist or station the queue was
            // started from, plus anything queued by hand since.
            itemsIndexed(
                items = manualRows,
                key = { index, _ -> manualKeys[index] },
            ) { index, song ->
                val key = manualKeys[index]
                val dragging = manualDrag.draggedKey == key
                InlineQueueRow(
                    song = song,
                    isCurrent = index == currentIndex,
                    onClick = { onJumpTo(index) },
                    onRemove = { onRemove(index) },
                    // Only what's still queued ahead. The playing track and
                    // everything already played sit above the line a drag
                    // can't cross.
                    draggable = index >= firstMovable,
                    dragging = dragging,
                    onDragStart = { manualDrag.onDragStart(key) },
                    onDrag = manualDrag::onDrag,
                    onDragEnd = manualDrag::onDragEnd,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) manualDrag.dragOffset else 0f }
                        // The dragged row follows the finger, so it is the one
                        // row that must not also be animating to a slot.
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                )
            }
            // Heading first, then what AutoPlay has lined up under it. With
            // nothing lined up yet it closes the queue as a promise instead.
            if (autoplayEnabled || autoplayStart < queue.size) {
                item(key = "autoplay-heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            BitChordIcons.Infinity,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AutoPlay",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                            Text(
                                text = if (autoplayStart < queue.size) {
                                    "Similar music, picked to follow on"
                                } else {
                                    "Similar music will keep playing"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                items = autoplayRows,
                key = { index, _ -> autoplayKeys[index] },
            ) { index, song ->
                val at = autoplayStart + index
                val key = autoplayKeys[index]
                val dragging = autoplayDrag.draggedKey == key
                InlineQueueRow(
                    song = song,
                    isCurrent = at == currentIndex,
                    onClick = { onJumpTo(at) },
                    onRemove = { onRemove(at) },
                    draggable = true,
                    dragging = dragging,
                    onDragStart = { autoplayDrag.onDragStart(key) },
                    onDrag = autoplayDrag::onDrag,
                    onDragEnd = autoplayDrag::onDragEnd,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) autoplayDrag.dragOffset else 0f }
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                )
            }
        }
    }
}

/**
 * A key per row, stable across a reorder and unique even when the same song
 * appears twice — the Nth time a given videoId is seen gets suffixed with
 * that count, so two copies of one song each keep their own identity instead
 * of colliding on the same LazyColumn key.
 */
private fun List<Song>.stableQueueKeys(prefix: String = ""): List<String> {
    val seen = HashMap<String, Int>()
    return map { song ->
        val n = seen.getOrDefault(song.videoId, 0)
        seen[song.videoId] = n + 1
        if (n == 0) "$prefix${song.videoId}" else "$prefix${song.videoId}#$n"
    }
}

/**
 * Drag-to-reorder for one contiguous section of [InlineQueue]'s LazyColumn —
 * the user's own queue and AutoPlay's each get their own instance, since a
 * drag never crosses the boundary between them.
 *
 * Each swap goes to the player the moment the dragged row crosses a
 * neighbour, so the live queue is always what's on screen and the rows the
 * drag displaces animate to their new slots off it. The dragged row is
 * tracked by its LazyColumn key rather than by index, because the index under
 * it changes with every swap; [dragOffset] is corrected by the same distance
 * the row jumps so it stays put under the finger while its slot moves.
 *
 * [lazyRange] is the section's span of LazyColumn indices, and [lazyOffset]
 * the distance from those to queue indices — the AutoPlay heading is a row
 * of the list too, so below it the two no longer line up.
 */
@Composable
private fun rememberQueueDragState(
    listState: LazyListState,
    lazyRange: IntRange,
    lazyOffset: Int,
    onMove: (Int, Int) -> Unit,
): QueueDragState {
    val state = remember(listState) { QueueDragState(listState) }
    state.lazyRange = lazyRange
    state.lazyOffset = lazyOffset
    state.onMove = onMove
    return state
}

private class QueueDragState(private val listState: LazyListState) {
    var lazyRange: IntRange = IntRange.EMPTY
    var lazyOffset: Int = 0
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** LazyColumn key of the row being dragged; null at rest. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    /** Where the last swap put the row, until the list is laid out with it. */
    private var awaiting: Int? = null

    fun onDragStart(key: Any) {
        draggedKey = key
        dragOffset = 0f
        awaiting = null
    }

    fun onDrag(deltaY: Float) {
        val key = draggedKey ?: return
        dragOffset += deltaY
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.find { it.key == key } ?: return
        // A swap already sent but not yet laid out: deciding the next one off
        // a position the list has moved on from would send a second move for
        // a swap that has already happened, and the two would fight.
        awaiting?.let { if (dragged.index != it) return else awaiting = null }
        val draggedCenter = dragged.offset + dragged.size / 2f + dragOffset
        // Only rows of this section are fair targets — the heading and the
        // other section's rows share the LazyColumn but not this range.
        val target = items
            .filter { it.index in lazyRange && it.index != dragged.index }
            .minByOrNull { abs((it.offset + it.size / 2f) - draggedCenter) }
            ?: return
        // Held short of halfway the rows would swap back and forth over a
        // single pixel of travel; a full half-height of overlap is what makes
        // one swap per row crossed.
        if (abs(draggedCenter - (target.offset + target.size / 2f)) > target.size / 2f) return
        onMove(dragged.index - lazyOffset, target.index - lazyOffset)
        // The row is about to land where the target was — fold that jump back
        // into the offset so it doesn't move out from under the finger.
        dragOffset += (dragged.offset - target.offset)
        awaiting = target.index
    }

    fun onDragEnd() {
        draggedKey = null
        dragOffset = 0f
        awaiting = null
    }
}

@Composable
private fun InlineQueueRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    dragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (dragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draggable) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(20.dp)
                    // DragHandle's glyph sits well inset from the edges of
                    // its own bounding box — this pulls it back to the row's
                    // actual left edge instead of leaving a gap in front of it.
                    .offset(x = (-4).dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                        )
                    },
            )
            Spacer(Modifier.width(4.dp))
        }
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .thumbnailBorder(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = "Now playing",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove from queue",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * The gap between the two timestamps under the seek bar: just the "Lossless"
 * badge when one applies, and nothing otherwise. The measured stats line
 * that used to fall back to lives inside the sleeve now (see the bottom-centre
 * overlay on the artwork Box above), so there is no tap here to swap it in —
 * the badge is a claim, the sleeve is where the evidence is.
 */
@Composable
private fun LosslessOrStats(
    isLoading: Boolean,
    stillRacing: Boolean,
    losslessRequested: Boolean,
    nerdStats: NerdStats.Snapshot?,
    modifier: Modifier = Modifier,
) {
    when {
        // Still resolving — either the player itself is buffering, or a
        // module is still racing YouTube for this track in the background
        // (see [NerdStats.racingLossless]) even though YouTube already won
        // and is audible. Either way nothing measured yet to confirm with,
        // so this is a statement of intent, not a result — no shimmer, so
        // it never reads as "confirmed" before it is.
        // [stillRacing] on its own, not gated on the lossless preference: a
        // module outranks YouTube on the strength of the source order alone,
        // so the lookup runs — and can come back lossless — with that switch
        // off. Gating this on it left the badge blank through the wait and
        // then jumped straight to "Hi-Res Lossless".
        (stillRacing || (isLoading && losslessRequested)) && nerdStats?.isLossless != true -> LosslessLabel(
            text = "Upgrading Quality",
            animated = false,
            modifier = modifier,
        )
        nerdStats?.isLossless == true -> LosslessLabel(
            // Same line Tidal, Qobuz and Apple Music draw it at — see
            // [NerdStats.Snapshot.isHiRes].
            text = if (nerdStats.isHiRes) "Hi-Res Lossless" else "Lossless",
            // Shimmer is reserved for the thing that was asked for and
            // confirmed. It is what makes the badge read as an achievement
            // rather than a label, which only one of these two is.
            animated = true,
            modifier = modifier,
        )
        // Lossy, but the good end of lossy — a module's 320kbps tier, which
        // for a great many tracks is the best copy that exists anywhere the
        // app can reach. See [NerdStats.Snapshot.isHiQuality].
        nerdStats?.isHiQuality == true -> LosslessLabel(
            text = "Hi-Quality",
            animated = false,
            modifier = modifier,
        )
        else -> {}
    }
}

/** A headphone glyph ahead of the quality tag — "Upgrading Quality", "Hi-Quality", "Lossless". */
@Composable
private fun LosslessLabel(text: String, animated: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (animated) 0.7f else 0.45f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        if (animated) {
            ShimmerText(text = text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
                ),
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Lossless", with a highlight band sweeping left to right across it every
 * three seconds — confirmed, not just claimed, so it's worth the shine.
 *
 * The band's width is measured off the text itself via [onSizeChanged]
 * rather than assumed, so the sweep always clears the word fully at both
 * ends instead of being sized for whatever length happened to be typical.
 */
@Composable
private fun ShimmerText(text: String) {
    var widthPx by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "lossless-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lossless-shimmer-progress",
    )
    val baseColor = Color.White.copy(alpha = 0.55f)
    val brush = if (widthPx <= 0) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        val band = widthPx * 0.6f
        val center = -band + progress * (widthPx + 2 * band)
        Brush.linearGradient(
            colorStops = arrayOf(0f to baseColor, 0.5f to Color.White, 1f to baseColor),
            start = Offset(center - band, 0f),
            end = Offset(center + band, 0f),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            brush = brush,
            fontWeight = FontWeight.SemiBold,
            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onSizeChanged { widthPx = it.width },
    )
}

/**
 * "FLAC · 24-bit · 96.0 kHz · Stereo" — whichever of those the player has
 * actually reported. A figure it hasn't is dropped rather than filled in, so a
 * short line means little was known, never that something was invented.
 *
 * Bitrate is omitted once the stream is known to be lossless: the number is
 * real but says nothing useful about the quality, and reading "1411 kbps" next
 * to "FLAC" invites the comparison with a lossy figure that the two do not
 * support.
 *
 * A stream that arrived worse than its source promised gets that stated
 * outright rather than left to be spotted — see [NerdStats.Snapshot.downgraded].
 */
private fun NerdStats.Snapshot.describe(): String? {
    val parts = buildList {
        codecLabel(mimeType)?.let(::add)
        bitDepth?.let { add("$it-bit") }
        if (!isLossless) bitrateKbps?.let { add("$it kbps") }
        sampleRateHz?.let { add("%.1f kHz".format(it / 1000f)) }
        channels?.let {
            add(
                when (it) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    else -> "$it ch"
                },
            )
        }
        if (downgraded) add("↓ from ${claimed?.summary}")
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** The codec under its usual name rather than its MIME type. */
private fun codecLabel(mimeType: String?): String? = when {
    mimeType == null -> null
    mimeType.endsWith("opus") -> "Opus"
    mimeType.endsWith("mp4a-latm") -> "AAC"
    mimeType.endsWith("vorbis") -> "Vorbis"
    mimeType.endsWith("mpeg") -> "MP3"
    mimeType.endsWith("flac") -> "FLAC"
    mimeType.endsWith("alac") -> "ALAC"
    else -> mimeType.substringAfter('/').uppercase()
}
