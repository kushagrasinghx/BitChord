package com.music.bitchord.ui.player

import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.Constraints
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.sharedui.resources.*

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.stringArrayResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.ui.theme.StatusBarIcons
import com.music.bitchord.ui.theme.rememberArtworkTopBandLuminance
import com.music.bitchord.ui.theme.topBandScrimAlpha
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.components.optimizedHazeEffect
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.canvas.CanvasSource
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.BACK_RESTARTS_AFTER_MS
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt


/** Collapsed-header geometry, shared by the layout and its animation. */
/**
 * Comfortably over the sleeve's drawn size on a phone, without wasting bytes.
 *
 * A rung on the app-wide ladder rather than a number of the player's own, so a
 * large home-screen widget asks for the same copy — see [PLAYER_ART_PX].
 */
internal const val ART_PX = PLAYER_ART_PX

/**
 * How many further goes a cover that failed to load gets.
 *
 * Small on purpose. This is here for the connection that drops for a moment or
 * the request that loses a race with the app coming back to the foreground, not
 * for a track whose artwork has genuinely gone: past a few tries the answer is
 * not going to change, and the placeholder tile is the honest thing to draw.
 */
internal const val ART_RETRIES = 3

/** How long to leave it before trying a failed cover again. */
internal const val ART_RETRY_DELAY_MS = 1_500L

/**
 * How long a canvas lookup waits for the track's album name before giving up
 * on it. Long enough to cover the album lookup on a normal connection, short
 * enough not to be noticed on a track that has no album to find.
 */
internal const val ALBUM_SETTLE_MS = 700L

/** Long enough for the post-upgrade rollback cue to be noticed without lingering. */
private const val REVERT_CUE_MS = 2_600L

/**
 * How close the player's reported position has to get to a released scrub
 * handle before the handle stops being drawn where it was dropped. Wide enough
 * to swallow a coarse progress tick, tight enough that the handle doesn't hand
 * over while it is still visibly wrong.
 */
internal const val SEEK_SETTLE_TOLERANCE_MS = 1_500L

/**
 * How long that handle is held at the drop point regardless. A backstop, not a
 * schedule: a seek normally settles in a tick or two, and this only decides how
 * long a seek that never settles can freeze the bar for. Generous enough that a
 * slow buffer still hands over smoothly rather than snapping back.
 */
internal const val SEEK_SETTLE_TIMEOUT_MS = 4_000L

private val THUMB_SIZE = 54.dp
private val HEADER_HEIGHT = 60.dp
private val ART_TITLE_GAP = 20.dp
/**
 * How long the sleeve takes to travel the whole way between the full player and
 * the queue's header.
 *
 * Spent in proportion rather than in full: a drag released four fifths of the
 * way up has a fifth of the journey left and gets a fifth of the time for it.
 * Only the toggle, which travels end to end, ever spends all of it.
 */
private const val QUEUE_TRAVEL_MS = 420
/**
 * How far up the sleeve has to have been dragged for a release to carry on
 * opening the queue rather than falling back, as a share of the sleeve's travel.
 *
 * Well under half, because the gesture is only ever *started* deliberately —
 * there is nothing else an upward drag on the artwork could have meant — so the
 * doubt a halfway line exists to settle isn't there.
 */
private const val QUEUE_CARRY_FRACTION = 0.3f
/**
 * How fast a release has to be moving, in pixels a second, to decide the queue
 * on its own and overrule [QUEUE_CARRY_FRACTION].
 *
 * A flick is a whole gesture in its own right: it says "open" without ever
 * asking the finger to travel, and the distance it covered is beside the point.
 */
private const val QUEUE_FLICK_VELOCITY = 450f
/**
 * The handle strip above the artwork, which always hands drags to the sheet.
 *
 * It isn't the only place that does — the artwork and the credits under it pass
 * theirs on as well, which is what makes the whole top of the player closable
 * rather than just its topmost 32dp. See the dismiss band in `NowPlayingScreen`.
 */
private val DISMISS_STRIP_HEIGHT = 32.dp
/** The breathing room above the sleeve, needed twice: once to apply, once to measure past. */
private val ART_BOX_TOP_PAD = 8.dp
/**
 * How far below the sleeve's top edge the video/audio pill floats.
 *
 * Far enough to clear the artwork's rounded corners, so the pill reads as
 * something laid on the cover rather than something clipped by it.
 */
private val VERSION_PILL_ART_INSET = 12.dp
/**
 * Share of the motion-artwork banner's height given over to its dissolve.
 *
 * Generous on purpose: the banner has no card edge to stop at, so anything
 * short enough to still be reading as artwork where it ends reads as a picture
 * that was cut off rather than one that ran out.
 */
private const val HERO_FADE_FRACTION = 0.42f
/** Kept transparent so the cover remains edge-to-edge, while aiding icon contrast. */
/** A modest floor while a subview replaces the hero with its artwork-derived mesh. */
private const val SUBVIEW_STATUS_SCRIM_MIN_ALPHA = 0.40f

/**
 * How often the backdrop re-reads the colours of a playing Canvas clip.
 *
 * Every three seconds, with `MESH_FADE_MS` easing each read into the last so
 * the backdrop arrives at its new colour rather than cutting to it. The read is
 * the expensive half — a texture readback off the GPU — and this is the number
 * that decides how many of them there are; the fade is the cheap half and is
 * over well inside the gap, which leaves the backdrop still for most of it.
 */
private const val MESH_REFRESH_MS = 3_000L

/** The player's side margin. Scrollable panels reach back across it. */
internal val PLAYER_GUTTER = 30.dp
/**
 * How wide the player's content is ever allowed to get. A sleeve and a volume
 * slider stretched right across a tablet aren't a bigger player, just a coarser
 * one; past this the column stops growing and centres itself instead. Phones
 * are narrower than this, so for them it does nothing.
 */
internal val PLAYER_MAX_WIDTH = 560.dp
/**
 * The width from which the player counts as tablet-sized: its backdrop is
 * the full-cover blur in every state rather than the phone's seam-aware
 * mesh, and Settings keeps the full-bleed artwork switch listed — see
 * [fullBleedArtworkAvailable].
 *
 * 700dp is the figure the docked-player layout used to call "tablet sized"
 * (a 360dp page beside a 340dp pane). That layout is gone; the number stays,
 * so removing it moved no breakpoint.
 */
private val TABLET_PLAYER_MIN_WIDTH = 700.dp

/**
 * The least a landscape window has to offer before the player splits into its
 * two columns: enough that each half still holds what it is given — the sleeve
 * above the lyrics / output / queue row on the left, the credits and transport
 * on the right — with the bottom row's three-up capsule still fitting across
 * the narrower half.
 *
 * Low enough to take in a phone turned sideways, which is the point: a phone in
 * landscape and a tablet in landscape are the same shape, and they get the same
 * layout rather than the portrait player squashed into a window it was never
 * drawn for.
 */
private val LANDSCAPE_PLAYER_MIN_WIDTH = 560.dp

/**
 * The artwork's own play/pause/scrub pose in the landscape layout. Three flat
 * scales and one priority rule: paused always wins outright over a scrub in
 * progress, rather than the two combining — there is one artwork, in one of
 * three settled poses, never a blend of two.
 */
private const val ARTWORK_EXPANDED_SCALE = 1f
private const val ARTWORK_PAUSE_SHRINK_SCALE = 0.88f
private const val ARTWORK_DRAG_SHRINK_SCALE = 0.94f

/**
 * The curve and duration those three poses move between — an ease-out cubic
 * over 500ms rather than a spring. A spring reads wrong for a press-and-release
 * gesture specifically: it visibly lags a quick scrub and keeps settling after
 * the finger has already lifted.
 *
 * The phone layout keeps its own bouncy spring ([artScale]) — it is answering a
 * different thing there, a sleeve that also collapses into a header, and the
 * bounce is the signature.
 */
private val ArtworkScaleEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
private const val ARTWORK_SCALE_DURATION_MS = 500

/**
 * How far a tall screen is allowed to push the transport from the blocks either
 * side of it.
 *
 * The spare height has to land somewhere, and above and below the play button is
 * where it reads as room rather than as a hole. Past this it stops reading as one
 * group of controls, so the rest goes back to the artwork block.
 */
private val CONTROL_GAP_SPREAD_MAX = 24.dp
/**
 * The spread [NowPlayingScreen] settled on the last time it was laid out.
 *
 * It follows from the window, so it is very nearly the same answer on every open
 * — and the player is torn down with its sheet, so without this the first frame
 * of each open would show the unspread gaps and then step to the real ones. Only
 * a head start: the frame after re-derives it either way. A plain var because
 * that is all it is, a cache of a measurement, not state anything observes.
 */
private var lastControlSpread: Dp = 0.dp

/**
 * Whether the full-bleed banner had settled the last time the player was open
 * — the same kind of head start as [lastControlSpread], for the same reason:
 * the player is torn down with its sheet, and without this every open would
 * start on the card and then grow it out into the banner it was a moment ago.
 */
private var lastHeroSettled = false

/**
 * How much of the sheet's travel the rest of the player has gone by before
 * the artwork arrives at the mini player: it is fully faded with this share of
 * the way still to go.
 */
private const val DOCK_FADE_LEAD = 0.15f

/** The least the faded player is drawn at while docking — see the player's fade. */
private const val DOCK_FADE_FLOOR = 1f / 255f

/**
 * What one frame of docking into the mini player worked out — see
 * [PlayerDock]. Written while the sleeve is laid out and read further down
 * the same frame, by the tile inside the sleeve and by the player's draw; plain
 * fields rather than state, because nothing should recompose or re-lay out for
 * them — the sheet's offset already drives every frame that reads them.
 */
private class DockFrame {
    /**
     * Where the player sits on screen with the sheet fully open. State, the
     * one field here that is: it arrives a frame after the sheet first lays
     * out, and the frame it arrives in has to hear about it.
     */
    var anchor: Offset? by mutableStateOf(null)
    /** The player's size [anchor] was measured at. */
    var anchorFor = IntSize.Zero
    /** The mini player's cover, in the sleeve's box. */
    var miniInBox: Rect? = null
    /** The sheet's offset this frame, in the whole pixels it is placed at. */
    var offset = 0f
    /** How much of that the artwork rides — see `dockRide`. */
    var ride = 0f
    /** The sleeve as laid out, in the same box. */
    var sleeve: Rect = Rect.Zero
    /**
     * The sleeve's corner as a shape, made again only when its radius moves:
     * both of the sleeve's clipping layers ask for it, on every frame of any
     * transition, and an unchanged radius needs no new shape.
     */
    private var cornerRadius = Dp.Unspecified
    private var corner: Shape = RectangleShape
    fun cornerShape(radius: Dp): Shape {
        if (radius != cornerRadius) {
            cornerRadius = radius
            corner = RoundedCornerShape(radius)
        }
        return corner
    }
    /** Where the sleeve's pixels land in the player, for drawing it on top. */
    var portalLeft = 0f
    var portalTop = 0f
    /**
     * How much smaller than laid out the sleeve is drawn there. Always 1 for
     * the portrait sleeve, which is re-laid out at its size each frame; the
     * landscape one keeps its layout and is scaled down onto the cover.
     */
    var portalScale = 1f
    /** The player itself, for placing the landscape sleeve within it. */
    var host: LayoutCoordinates? = null
    /** The landscape sleeve, as last placed. */
    var landscapeArt: LayoutCoordinates? = null
}

/**
 * One frame of the landscape sleeve's trip to the mini player: where it is
 * drawn in the player, and how small. See `landscapeDockPose`.
 */
private class LandscapeDockPose(val left: Float, val top: Float, val scale: Float)

/** The placeholder tile's corners — one shape, not a new one every frame it moves. */
private val TileShape = RoundedCornerShape(8.dp)

/**
 * [content], for as long as [shown] says so.
 *
 * Asked in a scope of its own, which is the whole point: the player's
 * collapse thresholds flip partway through a moving sleeve, and read where the
 * gated thing sits — in the player's own body, or in the box around the
 * artwork — each flip recomposed all of that on a frame of the movement, for
 * one caption or one line of stats coming or going.
 */
@Composable
private fun WhileShown(shown: () -> Boolean, content: @Composable () -> Unit) {
    if (shown()) content()
}

/**
 * Whether the full-bleed artwork switch is worth listing in Settings for a
 * window this wide. Public so the settings sheet can leave the switch out
 * entirely where it would do nothing.
 *
 * A window narrow enough that the player fills it is where the setting
 * acts: edge to edge there means the artwork *is* the screen. The tablet
 * half is left from the docked player, whose phone-width pane ran the
 * artwork edge to edge too; with the pane gone the portrait player no
 * longer goes full bleed at these widths, but the switch is still listed
 * there, unchanged, until that is decided on its own.
 */
fun fullBleedArtworkAvailable(windowWidth: Dp): Boolean =
    playerFillsWindow(windowWidth) || tabletSizedPlayer(windowWidth)

/**
 * Whether a player given the whole of a window this wide is still narrow enough
 * to run its artwork edge to edge.
 */
private fun playerFillsWindow(windowWidth: Dp): Boolean =
    windowWidth <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2

/**
 * Whether the player is tablet-sized — see [TABLET_PLAYER_MIN_WIDTH].
 *
 * [windowWidth] is the width of the *window*, measured rather than read off
 * `Configuration.screenWidthDp`: in a freeform or desktop window that can
 * report the display instead of the window, and it lands a beat late when
 * the window is dragged.
 */
private fun tabletSizedPlayer(windowWidth: Dp): Boolean =
    windowWidth >= TABLET_PLAYER_MIN_WIDTH

/**
 * Whether the player takes its landscape shape: the sleeve and the lyrics /
 * output / queue row in the left column, and the right column showing the
 * credits and transport, the lyrics or the queue — one of the three at a time.
 *
 * The same answer for a tablet and a phone on its side. Both are asked the
 * same question, the window's own proportions, rather than what kind of
 * device this is.
 *
 * Width alone isn't enough: a tablet held upright can be as wide as a phone
 * held sideways, and the two-column layout is a landscape shape, not a "wide
 * enough" one. Upright, every device gets the portrait player — a tall window
 * is the shape it was drawn for.
 */
fun landscapePlayerAvailable(windowWidth: Dp, windowHeight: Dp): Boolean =
    windowWidth > windowHeight && windowWidth >= LANDSCAPE_PLAYER_MIN_WIDTH

/** How long the player stands under the lyrics untouched before standing down. */
private const val LYRICS_CONTROLS_IDLE_MS = 5_000L
/** Default Spotify Canvas delay before its optional automatic collapse. */
private const val SPOTIFY_CANVAS_CONTROLS_IDLE_MS = 5_000L
/** One shared travel time keeps the deck, credits and stats moving as a unit. */
private const val SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS = 420
/** The deck's fade back in on returning from lyrics or the queue. */
private const val PLAYER_DECK_FADE_IN_MS = 700
/** Shared top-only scrim transition for the Canvas lower deck. */
private const val SPOTIFY_DECK_TOP_FADE_FRACTION = 0.28f

// The lower glass and the controls use one piece of motion. Keeping the slide
// specification here prevents a busy frame from exposing slightly different
// timings between the surface and the player drawn over it.
private fun playerDeckSlideIn() = slideInVertically(
    animationSpec = tween(
        SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS,
        easing = FastOutSlowInEasing,
    ),
    initialOffsetY = { it },
)

private fun playerDeckSlideOut() = slideOutVertically(
    animationSpec = tween(
        SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS,
        easing = FastOutSlowInEasing,
    ),
    targetOffsetY = { it },
)

/**
 * One lower-deck component, with one animation value owning both its pixels
 * and the space it occupies.
 *
 * A slide-only AnimatedVisibility kept the full height until its last frame;
 * combining slide and size transitions fixed that but let two transition
 * layers overlap when the presentation mode changed while returning to main.
 * This layout reports a fraction of the deck's real height instead. Since the
 * weighted player/panel above it consumes the remainder, the deck's top and
 * everything drawn from it move together. The subtree is removed only after
 * the exit reaches zero, retaining neither an invisible player nor a duplicate.
 *
 * [fadeIn], read at the moment [visible] turns true, reveals the deck at its
 * full height at once and fades it up instead. Returning to the main player
 * with the deck stood down, the sleeve sized itself against the deck's missing
 * height and was then squeezed as the deck slid up under it — the artwork
 * swelling to fill the screen and shrinking back over one 420ms trip.
 */
@Composable
private fun SlidingPlayerDeck(
    visible: Boolean,
    reveal: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    fadeIn: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    var mounted by remember { mutableStateOf(visible) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        if (visible) {
            if (fadeIn() && reveal.value < 1f) {
                alpha.snapTo(0f)
                reveal.snapTo(1f)
                mounted = true
                // Slower than the slide, and linear: on FastOutSlowIn the deck
                // was most of the way up within a few frames and read as
                // simply being there.
                alpha.animateTo(
                    1f,
                    tween(PLAYER_DECK_FADE_IN_MS, easing = LinearEasing),
                )
            } else {
                // Hidden part-way through a fade: the slide starts from
                // wherever the height is, with the pixels whole again.
                alpha.snapTo(1f)
                mounted = true
                reveal.animateTo(
                    1f,
                    tween(
                        SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        } else {
            reveal.animateTo(
                0f,
                tween(
                    SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
            mounted = false
        }
    }

    if (mounted) {
        Box(
            modifier = modifier
                .graphicsLayer { this.alpha = alpha.value }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val animatedHeight = (placeable.height * reveal.value)
                        .roundToInt()
                        .coerceIn(constraints.minHeight, constraints.maxHeight)
                    layout(placeable.width, animatedHeight) {
                        // No second translation: the weighted sibling above moves
                        // this component's origin as its reported height changes.
                        placeable.placeRelative(0, 0)
                    }
                },
        ) {
            content()
        }
    }
}

/**
 * Apple Music's Now Playing, closely: artwork that shrinks when paused, a
 * hairline scrubber with elapsed / remaining either side, oversized transport
 * glyphs, a volume capsule flanked by speaker icons, and lyrics / AirPlay /
 * queue along the bottom.
 */
@Composable
@OptIn(ExperimentalHazeApi::class, ExperimentalHazeMaterialsApi::class)
fun NowPlayingScreen(
    song: Song,
    /** Who selected this track in the active Listen Together session. */
    playedBy: String? = null,
    isPlaying: Boolean,
    isLoading: Boolean,
    /**
     * The playhead, read only where it is drawn — never here. Passed as the
     * object rather than its value because a value read at this level is a
     * read in this function's own scope, and it ticks twice a second: the whole
     * player recomposed with it. See [PlaybackPosition].
     */
    position: PlaybackPosition,
    durationMs: Long,
    /** A version switch (video vs audio-only), triggered from the player's
     * menu, is fetching and measuring the target cut. */
    audioVersionSwitching: Boolean,
    /** The player has just swapped this item to a higher-quality source. */
    qualityUpgraded: Boolean,
    queue: List<Song>,
    queueIndex: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    autoplayEnabled: Boolean,
    signedIn: Boolean,
    accountName: String?,
    likeStatus: LikeStatus,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    /**
     * A control the host has taken away was reached for anyway.
     *
     * The buttons are gone while a party is locked, but a swipe has no button
     * to remove — so the gesture answers instead of silently doing nothing.
     */
    onBlockedControl: () -> Unit,
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
    /**
     * A queue row started or stopped being dragged.
     *
     * Lets the caller tell a jam's party sync that a reorder is in progress, so
     * it can hold its publish until the row is dropped instead of sending one
     * for every neighbour the drag crosses. See [PartySync.beginQueueDrag].
     */
    onQueueDragActiveChange: (Boolean) -> Unit = {},
    onClearQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    /**
     * Opens an artist's page. [browseId] is null for anyone but the lead credit:
     * a track carries a channel id for its main artist and nothing for the rest,
     * so the host resolves those by [name].
     */
    onOpenArtist: (browseId: String?, name: String) -> Unit,
    /** Return to the queue-level page named by the caption above the player. */
    onOpenPlaybackSource: () -> Unit,
    /**
     * Open Listen Together, from the party half of the output capsule.
     *
     * The player does not decide whether it has to get out of the way first:
     * the settings page it opens is drawn under the player sheet, and closing
     * the sheet is the caller's business.
     */
    onListenTogether: () -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsProviderStates: Map<LyricsSource, LyricsProviderState>,
    onSelectLyricsProvider: (LyricsSource) -> Unit,
    lyricsUnavailable: Boolean,
    lyricsOffsetOpen: Boolean,
    onDismissLyricsOffset: () -> Unit,
    /** The width of the window the player is in — see [fullBleedArtworkAvailable]. */
    windowWidth: Dp,
    /**
     * The window's height, alongside [windowWidth] — needed for exactly one
     * thing: telling a portrait window apart from a landscape one, in
     * [landscapePlayerAvailable]. Width alone can't; a big tablet's portrait
     * width comfortably clears a phone's landscape width.
     */
    windowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()

    // Remote tracks whose art lives inside the file resolve it here, once —
    // every surface below reads the same value rather than each triggering
    // its own extraction.
    val remoteArt = rememberRemoteArtworkUrl(song)
    // This is produced by the palette's existing 128 px decode and cache. It
    // samples the upper band rather than the whole sleeve because that is what
    // lies beneath the status bar when the player expands to full bleed.
    val artTopLuminance = rememberArtworkTopBandLuminance(remoteArt, ART_PX)
    val artworkStatusScrimAlpha = topBandScrimAlpha(artTopLuminance)

    // Media-player convention: the player always owns light status icons. The
    // top treatment below, rather than a window-flag flip per cover, provides
    // their contrast and stays visually stable through artwork transitions.
    StatusBarIcons(dark = false)

    // Kept local to the player: a modal player is not in the page's Haze
    // source tree, so it needs its own source for the same frosted material as
    // the bottom navigation pill.
    val playerHaze = remember { HazeState() }
    var showAudioPipeline by remember { mutableStateOf(false) }
    var showCast by remember { mutableStateOf(false) }
    var showAudioOutput by remember { mutableStateOf(false) }
    var showLyricsProviders by remember { mutableStateOf(false) }
    // Gated on the Bluetooth permission the first time — see [rememberOutputPicker].
    val openAudioOutput = rememberOutputPicker { showAudioOutput = true }
    // Listening in a party whose host has taken the controls: the transport
    // keeps only play/pause, which from here moves this device alone.
    val controlsLocked = rememberControlsLocked()
    var showListenTogetherMembers by remember { mutableStateOf(false) }
    // Who's actually in the party is worth a look before the settings page —
    // see [ListenTogetherMembersSheet]. Only meaningful once there is a party
    // to show, so the pill and the caption fall back to [onListenTogether]
    // itself (create/join) when there isn't one.
    val openListenTogetherMembers: () -> Unit = { showListenTogetherMembers = true }

    val syncedLyricsEnabled by PlayerSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsOffsetMs by PlayerSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    // A lambda, not a value: read by the lyric strip and panel in scopes of
    // their own, so a tick recomposes them and not the player around them.
    val lyricsPlayhead = rememberLyricPlayhead(position)
    val seekToLyric: (Long) -> Unit = { lineTimeMs ->
        onSeek(adjustedLyricsSeekTarget(lineTimeMs, lyricsOffsetMs))
    }
    val hideVolumeBar by PlayerSettings.hideVolumeBar.collectAsStateWithLifecycle()
    val hideSongStatus by PlayerSettings.hideSongStatus.collectAsStateWithLifecycle()

    // Animated cover art: the looping video some labels publish alongside a
    // release, laid over the sleeve. A miss is the normal answer — see
    // CanvasRepository, which is also where the "is this actually the right
    // track" check lives.
    val spotifyCanvasAutoHide by PlayerSettings.spotifyCanvasAutoHide.collectAsStateWithLifecycle()
    val mixing by PlayerSettings.smartMixInProgress.collectAsStateWithLifecycle()
    val canvas = rememberCanvasArtwork(song)
    var canvasAspect by remember(canvas) { mutableFloatStateOf(0f) }
    // Whether the clip actually has a frame on screen right now, and one of
    // them — used to blow the sleeve out to the full-bleed hero treatment and
    // to re-tint the backdrop off the clip's own colours rather than the
    // still sleeve's.
    // All five keyed on the clip rather than the song, so a switch that keeps
    // the same clip — the common case, same title, same search — carries them
    // through untouched: the player never re-reports a first frame, the
    // sleeve never flashes back in under a clip that never went away, and
    // the Spotify deck keeps its state and captured measurements instead of
    // snapping open mid-loop. A different clip resets them with itself.
    val canvasRenderedState = remember(canvas?.url) { mutableStateOf(false) }
    var canvasRendered by canvasRenderedState
    var canvasFrame by remember(canvas?.url) { mutableStateOf<ImageBitmap?>(null) }
    // Spotify's phone presentation starts with the full control deck over its
    // video. It leaves on a Canvas tap, or after the optional idle timeout.
    var spotifyCanvasControlsOpen by remember(canvas?.url) { mutableStateOf(true) }
    // Captured while the deck is fully laid out. SlidingPlayerDeck collapses
    // that deck's measured height, which makes the weighted region grow;
    // retaining both measurements keeps the credits' destination stationary.
    var spotifyCanvasDeckHeight by remember(canvas?.url) { mutableStateOf(0.dp) }
    var spotifyCanvasExpandedTopHeight by remember(canvas?.url) { mutableStateOf(0.dp) }
    // How much of the still artwork the clip is covering, reported by the clip
    // itself. Read from a draw scope rather than in composition: it moves every
    // frame of the fade, and the still art it governs is an AsyncImage whose
    // request is rebuilt on each pass and so would not be skipped.
    val canvasCover = remember(canvas?.url) { mutableFloatStateOf(0f) }
    // v1.5's backdrop, kept behind a switch — see [PlayerSettings.legacyMeshGradient].
    val legacyMesh by PlayerSettings.legacyMeshGradient.collectAsStateWithLifecycle()
    // The backdrop's colours, taken off the artwork's own arrangement rather
    // than quantised out of it — see [ArtworkMesh].
    //
    // Only read for the backdrop that uses it. Each of these keeps a decode and
    // a pixel readback of its own on every track change, and the two answer the
    // same picture in two different ways, so whichever is not on screen is pure
    // cost — the legacy path pays [rememberArtworkColors] instead.
    // Asked of every non-Spotify clip — see CanvasArtworkPlayer's
    // refreshFrameEveryMs. Spotify now occupies the full phone screen and has no
    // frame-derived backdrop to re-tint; other providers retain that treatment.
    //
    // Often enough that the backdrop moves with the clip rather than catching up
    // with it every few seconds. What keeps that affordable is the size of each
    // read, not the number of them: the frame comes back at `frameCapturePx`
    // rather than full-bleed, and is averaged on a stride off the main thread.
    val meshRefreshMs = MESH_REFRESH_MS

    val scrub = rememberPlayerScrub(song.videoId, position, durationMs)
    // Read by the scrubber itself — see [PlayerScrubber.shown].
    val shown: () -> Float = { scrub.shown(position.positionMs, durationMs) }
    // Back restarts the track rather than stepping back once it is a few
    // seconds in. Derived, so this scope hears about the playhead once, when
    // it crosses that point, rather than on every tick.
    val pastRestartPoint by remember(position) {
        derivedStateOf { position.positionMs > BACK_RESTARTS_AFTER_MS }
    }
    // The queue lives inside the player, Apple-style, rather than in a sheet.
    // Read once when this expanded-player instance is created. Every change is
    // written below, so reopening the player (and a process restart) returns to
    // the same surface on phones and tablets without making this UI state a
    // continuously collected setting.
    val restoredPlayerScreen = remember { PlayerSettings.lastPlayerScreen.value }
    var queueOpen by remember {
        mutableStateOf(restoredPlayerScreen == LastPlayerScreen.QUEUE)
    }
    var lyricsOpen by remember {
        mutableStateOf(restoredPlayerScreen == LastPlayerScreen.LYRICS)
    }
    val queueSlide = remember { mutableFloatStateOf(0f) }
    // Expensive, song-scoped preparation is deliberately staggered. The small
    // backdrop decode runs after the track hand-off has settled; lyrics and
    // queue are then composed offscreen on separate beats rather than all three
    // competing with the song change. Opening a panel early bypasses its wait.
    var playerPrewarmStage by remember(song.videoId) { mutableIntStateOf(0) }
    LaunchedEffect(song.videoId) {
        delay(600)
        playerPrewarmStage = 1 // 128px backdrop decode + CPU blur
        delay(650)
        playerPrewarmStage = 2 // lyrics layout
        delay(650)
        playerPrewarmStage = 3 // queue layout and initial scroll
    }
    // Whether the lyrics or queue list is actively mid-scroll. The player's own
    // swipe gestures — skip-by-drag and the dismiss band — are suppressed for
    // as long as either is true, so a scroll that grazes past a list's edge
    // can never be misread as a drag meant for the player underneath it. Reset
    // whenever the owning panel closes, since a list scrolled mid-transition
    // out never gets a matching "stopped scrolling" event of its own.
    var lyricsScrolling by remember { mutableStateOf(false) }
    var queueScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsOpen) { if (!lyricsOpen) lyricsScrolling = false }
    LaunchedEffect(queueOpen) { if (!queueOpen) queueScrolling = false }
    val panelScrolling = lyricsScrolling || queueScrolling
    var lyricsControlsOpen by remember {
        mutableStateOf(restoredPlayerScreen == LastPlayerScreen.LYRICS)
    }
    var queueControlsOpen by remember { mutableStateOf(true) }
    // Shared with Spotify's title placement below. The deck and credits must
    // read one clock: the weighted region changes size as this value moves, so
    // independently animating the title towards that moving target makes it
    // trail behind and overlap the deck on the way back in.
    val playerDeckReveal = remember { Animatable(1f) }
    val playerDeckSettledOpen by remember(playerDeckReveal) {
        derivedStateOf { playerDeckReveal.value >= 0.999f }
    }
    LaunchedEffect(lyricsOpen, queueOpen) {
        PlayerSettings.setLastPlayerScreen(
            when {
                lyricsOpen -> LastPlayerScreen.LYRICS
                queueOpen -> LastPlayerScreen.QUEUE
                else -> LastPlayerScreen.MAIN
            },
        )
        // A fresh visit always starts with the half player present. Scrolling
        // the queue can then dismiss it without this effect firing again.
        if (queueOpen) queueControlsOpen = true
    }
    // Change the panel and its controls in the same snapshot. Driving the
    // controls from a LaunchedEffect left one composed frame where lyrics were
    // open but the half-player was not, so every trip into lyrics briefly
    // started an exit animation and reversed it on the following frame.
    val openLyrics: () -> Unit = {
        lyricsControlsOpen = true
        lyricsOpen = true
        queueOpen = false
    }
    val closeLyrics: () -> Unit = {
        lyricsControlsOpen = false
        lyricsOpen = false
    }
    val toggleLyrics: () -> Unit = {
        if (lyricsOpen) closeLyrics() else openLyrics()
    }
    val toggleQueue: () -> Unit = {
        val opening = !queueOpen
        if (opening && lyricsOpen) queueSlide.floatValue = 1f
        // With the queue rather than after it, for the reason [openLyrics]
        // gives: left to the effect above, it was a second recomposition of
        // the whole player on the sleeve's first frame of moving.
        if (opening) queueControlsOpen = true
        queueOpen = opening
        if (opening) closeLyrics()
    }
    val lyricsLoadingLines = stringArrayResource(Res.array.lyrics_loading_lines)
    val lyricsLoadingText = remember(song.videoId) { lyricsLoadingLines.random() }
    val lyricsTranslation = rememberLyricsTranslation(
        trackId = song.videoId,
        lyrics = lyrics,
        lyricsSource = lyricsSource,
        lyricsUnavailable = lyricsUnavailable,
        loadingText = lyricsLoadingText,
        haptics = haptics,
    )
    // The drawer holding the finished card, set the moment Share is confirmed.
    // Nothing is drawn here: the picture is a bitmap and a canvas, which is the
    // phone's to make, so the request is handed down and the sheet comes back
    // through [PlayerHost.LyricsShareSheet].
    var lyricsShare by remember { mutableStateOf<LyricsShareRequest?>(null) }
    val lyricsShareEnabled = lyricsShareAvailable
    val lyricPicker = rememberLyricsPicker(
        song = song,
        lines = lyrics,
        subLines = lyricsTranslation.subLines,
        artworkUrl = remoteArt,
        haptics = haptics,
        onCard = { lyricsShare = it },
    )
    // Nothing here resets [lyricsOpen] on a track change, deliberately. The
    // panel is a place, not a property of the track: someone reading along who
    // skips — or who simply lets the queue run on — means to carry on reading,
    // so the words change underneath them and the panel stays. Closing it
    // dropped them back onto the artwork every few minutes with no gesture of
    // their own behind it.
    // A brief, non-modal confirmation that the three-dot menu now contains a
    // way back to the original YouTube rendition. The control keeps its usual
    // action — opening the menu — so the cue teaches rather than surprises.
    var showRevertCue by remember(song.videoId) { mutableStateOf(false) }
    LaunchedEffect(song.videoId, qualityUpgraded) {
        if (!qualityUpgraded) {
            showRevertCue = false
            return@LaunchedEffect
        }
        showRevertCue = true
        delay(REVERT_CUE_MS)
        showRevertCue = false
    }

    // Lyrics are meant to be read continuously, so hold off the device's normal
    // screen timeout — but only while the panel is actually up. Closing it hands
    // the screen back, and the system starts its own timeout from that moment
    // rather than from whenever the panel was opened.
    //
    // Keyed to [lyricsOpen] rather than written from a SideEffect on every
    // recomposition: this is a piece of state on the window, not a per-frame
    // value, and the panel now outlives a track change (see above), so there is
    // no longer a whole-player recomposition standing behind it as a backstop.
    KeepScreenOn(enabled = lyricsOpen)

    // Back out of the lyrics panel to the player, and only from the player
    // itself out to the mini player.
    //
    // See [PlayerBackHandler] for why this needs more than a BackHandler.
    // Controls being visible must not insert an extra navigation level. Back
    // always leaves lyrics in one step, whether it starts over the lyrics list
    // or over the half-player at the bottom.
    PlayerBackHandler(enabled = lyricsOpen, onBack = closeLyrics)

    // Back out of the queue to the player.
    PlayerBackHandler(enabled = queueOpen) { queueOpen = false }

    // Registered ahead of the pipeline dialog's own handler below: the
    // pipeline is now only ever opened from the row at the bottom of this
    // drawer, so it is always the topmost of the two when both are up, and
    // back has to close it first rather than taking the drawer out from
    // under it.
    PlayerBackHandler(enabled = showAudioOutput) { showAudioOutput = false }

    PlayerBackHandler(enabled = showLyricsProviders) { showLyricsProviders = false }

    PlayerBackHandler(enabled = showListenTogetherMembers) { showListenTogetherMembers = false }

    PlayerBackHandler(enabled = showAudioPipeline) { showAudioPipeline = false }

    PlayerBackHandler(enabled = showCast) { showCast = false }

    PlayerBackHandler(enabled = lyricsOffsetOpen, onBack = onDismissLyricsOffset)

    // Both of these sit ahead of [lyricsOpen]'s own handler — see the note on
    // the queue above — because both are drawn *over* the panel rather than
    // instead of it: back should take away whichever of them is up and leave the
    // lyrics underneath exactly where the reader left them.
    PlayerBackHandler(enabled = lyricsShare != null) { lyricsShare = null }

    // Backing out of a pick drops the pick, not the panel: somebody who changed
    // their mind lands on the same verses they started from rather than having
    // to open the whole panel again.
    PlayerBackHandler(enabled = lyricPicker.picking) { lyricPicker.cancel() }

    // 0 = full sleeve, 1 = queue. Everything that moves reads off this.
    //
    // Plain state driven by an animation rather than [animateFloatAsState],
    // because it has two drivers and only one of them is an animation: the
    // toggle at the foot of the player, which travels end to end, and a finger
    // dragging the sleeve upward, which sets it outright. An animation keyed on
    // [queueOpen] cannot be pushed around mid-flight by a drag — and a drag that
    // could only move the *target* would have nothing to show for itself until
    // it was released, then jump from wherever the animation had got to.
    //
    // Read only inside layout and draw lambdas and the thresholds below, never
    // as a value here — see [p].
    // Whether a finger is on the sleeve right now. Parks the settle below rather
    // than leaving the two to write the same value on alternate frames.
    var queueDragging by remember { mutableStateOf(false) }
    // Bumped when a drag hands the value back, so the settle runs again even
    // though [queueOpen] may not have moved: a swipe that gave up short of
    // [QUEUE_CARRY_FRACTION] has to fall back to 0 just as surely as one that
    // carried has to finish reaching 1.
    var queueReleased by remember { mutableIntStateOf(0) }
    LaunchedEffect(queueOpen, queueDragging, queueReleased) {
        if (queueDragging) return@LaunchedEffect
        val target = if (queueOpen) 1f else 0f
        val from = queueSlide.floatValue
        if (from == target) return@LaunchedEffect
        animate(
            initialValue = from,
            targetValue = target,
            animationSpec = tween(
                durationMillis = (QUEUE_TRAVEL_MS * abs(target - from)).roundToInt(),
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ -> queueSlide.floatValue = value }
    }

    // Horizontal fling anywhere on the player skips tracks; the artwork
    // follows the finger so the gesture has something to hold on to.
    val swipeThreshold = with(density) { 72.dp.toPx() }
    // Where the finger has dragged the sleeve to, and the sleeve springing
    // after it. What animateFloatAsState does inside — a conflated channel of
    // targets, each chased from the current velocity — but fed straight from
    // the gesture: as state read here, every pointer move recomposed the whole
    // player just to hand the spring a new target, when the only readers are
    // draw-phase layers.
    val swipeSettle = remember { Animatable(0f) }
    val swipeTargets = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(swipeTargets) {
        for (target in swipeTargets) {
            val newest = swipeTargets.tryReceive().getOrNull() ?: target
            launch {
                if (newest != swipeSettle.targetValue) {
                    swipeSettle.animateTo(newest, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }
    val setSwipeOffset: (Float) -> Unit = { swipeTargets.trySend(it) }
    // The skip hint under the sleeve only needs composing while there is a
    // drag to hint at, and which way it points; its fade is drawn, not composed.
    val swipeHintShown by remember(swipeThreshold) {
        derivedStateOf { abs(swipeSettle.value) / swipeThreshold > 0.01f }
    }
    val swipeHintNext by remember { derivedStateOf { swipeSettle.value > 0f } }

    // Signature Apple Music touch: the sleeve shrinks back while paused.
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artScale",
    )

    val volume = rememberPlayerVolume()
    // Present when you arrive, out of the way once you are actually reading.
    //
    // The panel opens with the player under it so the scrubber and transport
    // are there to be reached, and this is the other half of that bargain: left
    // alone for [LYRICS_CONTROLS_IDLE_MS] it stands down and gives the words the
    // whole screen. Scrolling up brings it back and restarts the wait, since
    // that write to [lyricsControlsOpen] re-keys this effect.
    //
    // Never while a finger is on the scrubber or the volume bar: those are the
    // two controls that are *being used* while nothing else on screen moves,
    // and timing out underneath them would take the thing away mid-gesture.
    LaunchedEffect(lyricsOpen, lyricsControlsOpen, scrub.scrubbing, volume.dragging) {
        if (lyricsOpen && lyricsControlsOpen && !scrub.scrubbing && !volume.dragging) {
            delay(LYRICS_CONTROLS_IDLE_MS)
            lyricsControlsOpen = false
        }
    }
    // How collapsed the sleeve is, whichever surface asked for it — one 420ms
    // ease for the queue and the lyrics both.
    val animatedCollapse = animateFloatAsState(
        targetValue = if (lyricsOpen || queueOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sleeveCollapse",
    )
    // A queue tap/drag already owns a 420ms progress value. Reusing it avoids
    // driving this large layout from two independent animations on every frame.
    // Lyrics retain the ordinary collapse animation; switching queue -> lyrics
    // stays at one because its target never changed.
    //
    // A function rather than a value. It moves on every frame of a 420ms
    // collapse and of a finger dragging the queue, and read here as a value it
    // recomposed this whole function on each of those frames — the sleeve, the
    // credits, both panels and the controls, just to move a few of them. Layout
    // and draw lambdas call it where they place or paint; composition only asks
    // the thresholds below, each of which changes once per collapse.
    val p: () -> Float = {
        val queueOwnsCollapse = !lyricsOpen &&
            (queueOpen || queueDragging || queueSlide.floatValue > 0.001f)
        if (queueOwnsCollapse) queueSlide.floatValue else animatedCollapse.value
    }
    val collapseStarted by remember { derivedStateOf { p() > 0f } }
    val collapseAtRest by remember { derivedStateOf { p() == 0f } }
    val collapsePastSettling by remember { derivedStateOf { p() >= 0.01f } }
    val collapsePastHalf by remember { derivedStateOf { p() >= 0.5f } }
    val collapseAlmostDone by remember { derivedStateOf { p() >= 0.999f } }
    val collapseDone by remember { derivedStateOf { p() >= 1f } }
    val queueShowing by remember { derivedStateOf { queueSlide.floatValue > 0.01f } }

    // Whether the sleeve has finished getting out of the way, and how far the
    // panel that replaces it has faded up since.
    //
    // The lyric sheet and the queue list are the two most expensive things this
    // screen can compose — measuring every line of a song, or building a lazy
    // list with drag-reorder state per row — and both used to be composed on
    // the frame the panel was asked for, which is the frame the 420ms collapse
    // above starts on. That put the single heaviest composition of the whole
    // screen directly on top of the one animation the eye is following, and it
    // read as the open stuttering.
    //
    // Held back until [p] has actually arrived, the expensive frame lands while
    // nothing is moving, where a dropped frame costs nothing to look at, and
    // the panel then fades up on its own short curve. The open is a little
    // longer end to end and visibly smoother for it.
    //
    // Declared out here rather than beside either panel on purpose: an
    // [animateFloatAsState] created at the moment its target becomes true is
    // created *at* that target and has nothing left to animate. Living above
    // both panels, this one is already at 0 when they mount.
    //
    // Followed off the snapshot rather than handed a target in composition:
    // read here, [collapseDone] recomposed this whole screen on the first frame
    // of every close — a sleeve already moving, and the heaviest pass this
    // player has, landing on its second frame.
    val panelFade = remember {
        // Already up for a player opened onto a panel, as the animated value
        // this replaces started at its first target.
        Animatable(if (Snapshot.withoutReadObservation { collapseDone }) 1f else 0f)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { collapseDone }.collectLatest { settled ->
            panelFade.animateTo(
                if (settled) 1f else 0f,
                tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
        }
    }
    val fullBleedArt by PlayerSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    // Full-bleed is a phone idiom. What the width has to rule out is a player
    // running a foot wider than the column of controls under it — edge to edge
    // meaning "a picture, and separately some controls" rather than "the
    // artwork *is* the player".
    //
    // One question for the still cover and the clip both, rather than two that
    // could disagree — and they did, twice over. Dissolving a TextureView's
    // bottom edge needs a RenderEffect, so below API 31 the clip was held in its
    // sleeve while the cover behind it went edge to edge, and the artwork
    // changed shape the moment a clip arrived. In the other direction the clip
    // ignored [fullBleedArt] entirely, so turning the setting off still left a
    // clip running the full screen. CanvasArtworkPlayer masks itself on every
    // API level now, and both layers answer to this.
    val spotifyCanvasOnPhone = canvas?.source == CanvasSource.SPOTIFY &&
        playerFillsWindow(windowWidth)
    // Spotify Canvas is the player background on a phone, independent of the
    // still-art full-bleed preference. Other providers and static artwork keep
    // answering to that preference exactly as before.
    val heroMode = spotifyCanvasOnPhone ||
        (fullBleedArt && playerFillsWindow(windowWidth))

    // Whether there's a still image to blow out — a placeholder tile is a card
    // or it is nothing, and going full-bleed with one would just tint the top
    // third of the screen.
    //
    // Keyed on the artwork rather than on the track, because that is what it
    // actually describes and because only Coil can set it back to true. Two
    // tracks off one album share a cover, so skipping between them leaves the
    // request below byte-identical: the painter keeps the Success it already
    // had and never re-emits, so the `onState` that is the sole writer here
    // never fires again. Keyed on the cover there is nothing to reset: the
    // bitmap really is still loaded, so the state stays true.
    val art = rememberPlayerArtwork(remoteArt)
    // Sticky, unlike [PlayerArtwork.loaded]: the banner is the shape of the player rather
    // than a property of the track in it. Waiting on each new cover would
    // collapse the banner into a card and blow it back out on every skip —
    // twice the length of the whole screen's worth of movement for a change the
    // artwork itself already announces. The frame stays; the cover arrives in
    // it, fading in as Coil fades in everywhere else.
    //
    // Latched off the clip as well as the still art, for a cover that never
    // arrives at all and leaves the banner standing on the clip alone: the clip
    // gives its frame up and takes it back every time the app leaves the screen,
    // and a banner that answered only to that would collapse behind the user's
    // back and blow itself out again in front of them on the way in.
    //
    // Carried across opens too — see [lastHeroSettled]. Settling afresh on
    // every open would be the card visibly growing into the banner each time.
    var heroSettled by remember { mutableStateOf(lastHeroSettled) }
    // Listened for rather than keyed on. As keys, a clip's first frame — which
    // can land in the middle of the sleeve coming back out of a panel —
    // recomposed this whole screen to run a block that, every time after the
    // first, had nothing left to do.
    LaunchedEffect(art, canvasRenderedState) {
        if (heroSettled) return@LaunchedEffect
        snapshotFlow { art.loaded || canvasRenderedState.value }.first { it }
        heroSettled = true
        lastHeroSettled = true
    }
    // The clip that gets the banner, if any. Hoisted because the sleeve keys
    // its opacity on exactly what is mounted here: both are decided in the same
    // composition pass, so the still art is whole again in the very frame the
    // clip goes, rather than a frame later with no artwork anywhere.
    val heroClip = canvas?.takeIf { heroMode && !collapsePastHalf }
    val spotifyCanvasFullscreen = spotifyCanvasOnPhone &&
        heroClip?.source == CanvasSource.SPOTIFY
    val spotifyCanvasPresentation = spotifyCanvasFullscreen && canvasRendered
    val canvasFirstPortrait = !spotifyCanvasFullscreen &&
        heroClip != null && canvasAspect > 0f && canvasAspect < 1f
    // The clip's player, which outlives [heroClip] by the second half of a
    // collapse: mounted from the moment the player is asked back, and let go
    // of once the sleeve has settled into the header. Faded out by the half
    // either way (see its `presentationAlpha`), so nothing on screen tells
    // the two apart — but tearing a decoder and its TextureView down at the
    // half, and building them again there on the way back, blocked the main
    // thread in the middle of the one movement the eye was following. At rest
    // it costs a frame nobody is watching; on the way back it rides the
    // composition the tap already pays for.
    //
    // Everything it is handed is worked out for this clip rather than read off
    // [heroClip], which has gone by the time it is still lingering: re-read
    // off that, its last few frames would resize the view and rebuild its
    // fade for a picture nobody can see.
    val heroClipMounted = canvas?.takeIf {
        heroMode && (!(lyricsOpen || queueOpen) || !collapseDone)
    }
    val clipFullscreen = spotifyCanvasOnPhone &&
        heroClipMounted?.source == CanvasSource.SPOTIFY
    val clipPortrait = !clipFullscreen &&
        heroClipMounted != null && canvasAspect > 0f && canvasAspect < 1f

    // The setting defaults on, restoring the five-second stand-down, but the
    // listener can keep the deck open indefinitely from Spotify integration.
    // Pausing or interacting with a continuous control suspends the countdown;
    // it starts fresh once playback/interaction resumes.
    LaunchedEffect(
        spotifyCanvasPresentation,
        spotifyCanvasControlsOpen,
        spotifyCanvasAutoHide,
        isPlaying,
        scrub.scrubbing,
        volume.dragging,
        lyricsOpen,
        queueOpen,
        mixing,
    ) {
        if (!spotifyCanvasPresentation || !spotifyCanvasControlsOpen ||
            !spotifyCanvasAutoHide || !isPlaying || scrub.scrubbing || volume.dragging ||
            lyricsOpen || queueOpen || mixing
        ) return@LaunchedEffect
        delay(SPOTIFY_CANVAS_CONTROLS_IDLE_MS)
        spotifyCanvasControlsOpen = false
    }

    // Returning from a subview, or regaining the TextureView after backgrounding,
    // is a fresh visit, so show the deck again before any optional countdown.
    LaunchedEffect(spotifyCanvasPresentation, lyricsOpen, queueOpen) {
        if (spotifyCanvasPresentation && !lyricsOpen && !queueOpen) {
            spotifyCanvasControlsOpen = true
        }
    }
    // A transition can begin while Canvas is standing alone. Restore and pin
    // the compact deck so both the outgoing and incoming song are identified;
    // normal tap/idle collapsing resumes as soon as the mix finishes.
    LaunchedEffect(spotifyCanvasPresentation, mixing) {
        if (spotifyCanvasPresentation && mixing) spotifyCanvasControlsOpen = true
    }
    // Portrait clips always use the existing artwork mesh, even if the user
    // selected the legacy backdrop for ordinary artwork.
    val artMesh = if (legacyMesh && !canvasFirstPortrait) null else
        key(song.videoId) { rememberArtworkMesh(remoteArt, canvasFrame, ART_PX) }
    // Whether the banner is the presentation at all: full-bleed is on, and there
    // is something to blow out. The collapse is deliberately *not* part of this:
    // the sleeve goes from banner to thumbnail as one movement, rather than the
    // banner waiting on a threshold and then dissolving into a finished
    // thumbnail — see `sleeveRect` down in the layout.
    val heroT = animateFloatAsState(
        targetValue = if (
            // Latched first, so a clip coming and going isn't listened for
            // once the banner has settled — see [heroSettled].
            heroMode && (heroSettled || art.loaded || canvasRendered)
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "heroCanvas",
    )
    val spotifyChromeAlpha by animateFloatAsState(
        targetValue = if (!spotifyCanvasPresentation || spotifyCanvasControlsOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "spotifyCanvasChrome",
    )
    // How tall that banner is, worked out down in the layout where the sleeve's
    // own geometry is known. Zero until the first measure, which is fine: there
    // is nothing to show that early either.
    var heroHeight by remember { mutableStateOf(0.dp) }
    var playerBounds by remember { mutableStateOf(IntSize.Zero) }
    // The bottom of a top-aligned, contained clip in the full-player view.
    // Use measured pixels and the decoded display aspect, never screen constants.
    fun canvasBottom(portrait: Boolean): Dp =
        if (portrait && playerBounds.width > 0 && playerBounds.height > 0) {
            val viewAspect = playerBounds.width.toFloat() / playerBounds.height
            val videoHeight = if (canvasAspect >= viewAspect) playerBounds.width / canvasAspect
                else playerBounds.height.toFloat()
            with(density) { videoHeight.toDp() }
        } else 0.dp
    val renderedCanvasBottom = canvasBottom(canvasFirstPortrait)
    // The same for the mounted clip — see [heroClipMounted].
    val clipBottom = canvasBottom(clipPortrait)
    // Match the old hero's fade height in physical pixels, not its much larger
    // percentage of a portrait video. The mask ends at the real video bottom.
    val clipFadeFraction = if (clipBottom > 0.dp) {
        (heroHeight.value * HERO_FADE_FRACTION / clipBottom.value).coerceIn(0f, 1f)
    } else 0f
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // What sits between the status bar and the artwork: the drag strip. Read
    // in three places — the strip itself,
    // the scrim drawn over it and the banner's own height — which all have to
    // agree or the artwork and the credits under it move.
    val topStrip = DISMISS_STRIP_HEIGHT

    // The band of the player a vertical drag belongs to rather than to whatever
    // is under it: from the top of the artwork to the bottom of the credits, in
    // root coordinates. Everything in between is one block — the full sleeve
    // with the title and artist beneath it — and a drag on it closes the player
    // downwards and opens the queue upwards.
    //
    // Read off the layout rather than recomputed, so it stays the block's own
    // shape whatever the screen: a height-bound sleeve on a tablet, a full-bleed
    // banner on a phone.
    //
    // Only ever the *expanded* block, though. Once a panel is up the band is not
    // this pair at all but the header, worked out from the state instead — see
    // the gesture below. The two edges do travel with the sleeve as it collapses,
    // which reads like the band could simply follow them the whole way, and that
    // is exactly what went wrong: the sleeve takes [QUEUE_TRAVEL_MS] to get
    // there, and for that whole half second the queue was already listed and
    // scrollable underneath a band still lying across it. A drag on a row came
    // out as the player closing.
    //
    // Bare numbers rather than a rect: the band runs the full width of the
    // player either way, and on a height-bound sleeve the bare backdrop down
    // each side of it should close the player too — it is part of the same
    // gesture, and a hole there would be a strip the finger mysteriously
    // slides off.
    //
    // Both start at zero, which is a band with no height and so no hole at all
    // until the first layout pass. There is nothing on screen to drag then
    // either.
    var dismissBandTop by remember { mutableFloatStateOf(0f) }
    var dismissBandBottom by remember { mutableFloatStateOf(0f) }
    // The suppressing Column's own coordinates, to put a pointer's local
    // position into the same space as the two edges above.
    var dismissBandSpace by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Where the caption above the credits leads: the party for a track someone
    // else queued, the queue itself for a queue-built session, and otherwise
    // back to the album or playlist the session was started from.
    val openPlaybackOrigin: () -> Unit = {
        if (playedBy != null) {
            onListenTogether()
        } else if (song.playbackSourceType == PlaybackSourceType.QUEUE) {
            queueOpen = true
            closeLyrics()
        } else {
            onOpenPlaybackSource()
        }
    }

    // Horizontal fling skips tracks. The portrait player hangs it on the whole
    // screen, the landscape one on the sleeve alone — the right column there is
    // full of horizontal sliders and a lyric list that should not be one stray
    // sideways drag away from changing the song.
    val skipSwipeGesture = Modifier.pointerInput(showAudioPipeline, showCast, panelScrolling, controlsLocked) {
        if (showAudioPipeline || showCast || panelScrolling) return@pointerInput
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { setSwipeOffset(0f) },
            onDragEnd = {
                // The same two buzzes the transport glyphs give, so swiping the
                // sleeve and tapping skip feel like one gesture with two
                // spellings.
                val crossed = total <= -swipeThreshold || total >= swipeThreshold
                when {
                    // Still tracks the finger and still springs back, so the
                    // sleeve does not feel dead — it just says why it did not
                    // move on.
                    controlsLocked -> if (crossed) onBlockedControl()
                    total <= -swipeThreshold -> {
                        haptics.play(Haptic.SkipNext)
                        onNext()
                    }
                    total >= swipeThreshold -> {
                        haptics.play(Haptic.SkipPrevious)
                        onPrevious()
                    }
                }
                setSwipeOffset(0f)
            },
            onHorizontalDrag = { _, delta ->
                total += delta
                // Damped: it's a hint, not a drag-to-position.
                setSwipeOffset(total * 0.35f)
            },
        )
    }

    // The scrubber's two halves, shared by both layouts so a drop point is held
    // the same way in each — see [PlayerScrub.pendingSeek].
    val onScrub: (Float) -> Unit = scrub::drag
    val onScrubFinished: () -> Unit = {
        // On release only. Ticking the whole way along the bar turns a scrub
        // into a rattle, and the beat that matters is the one that says where
        // the playhead landed.
        haptics.play(Haptic.Select)
        scrub.release(onSeekFraction)
    }
    val onVolumeChange: (Float) -> Unit = volume::drag
    val onVolumeChangeFinished: () -> Unit = volume::release

    // Lyrics, output and queue, with the output name under them — the row both
    // layouts end on, and the only thing in the landscape player's left column
    // besides the sleeve.
    val playerActions: @Composable () -> Unit = {
        PlayerActionRow(
            lyricsOpen = lyricsOpen,
            queueOpen = queueOpen,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            autoplayEnabled = autoplayEnabled,
            onToggleLyrics = toggleLyrics,
            onToggleQueue = toggleQueue,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onToggleAutoplay = onToggleAutoplay,
            onOpenOutput = openAudioOutput,
            onListenTogether = onListenTogether,
            onOpenListenTogetherMembers = openListenTogetherMembers,
        )
        // Keep the current output caption visible in every state.
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            OutputCaption(
                accountName = accountName,
                onOpenOutput = openAudioOutput,
                onOpenMembers = openListenTogetherMembers,
            )
        }
    }

    // The drawers and dialogs the player raises over itself. Mounted by
    // whichever layout is on screen — they are overlays over the player, not
    // part of either shape of it.
    val playerOverlays: @Composable () -> Unit = {
        if (showAudioOutput) {
            AudioOutputSheet(
                hazeState = playerHaze,
                accountName = accountName,
                onDismiss = { showAudioOutput = false },
                onOpenPipeline = { showAudioPipeline = true },
                onOpenCast = { showCast = true },
            )
        }
        if (showLyricsProviders) {
            LyricsProviderSheet(
                hazeState = playerHaze,
                currentSource = lyricsSource,
                states = lyricsProviderStates,
                onSelect = onSelectLyricsProvider,
                onDismiss = { showLyricsProviders = false },
            )
        }
        if (showAudioPipeline) {
            AudioPipelineDialog(
                hazeState = playerHaze,
                isPlaying = isPlaying,
                onDismiss = { showAudioPipeline = false },
            )
        }
        if (showCast) {
            PlayerPlatform.host.CastDialog(
                hazeState = playerHaze,
                onDismiss = { showCast = false },
            )
        }
        if (showListenTogetherMembers) {
            ListenTogetherMembersSheet(
                hazeState = playerHaze,
                onDismiss = { showListenTogetherMembers = false },
                onManage = {
                    showListenTogetherMembers = false
                    onListenTogether()
                },
            )
        }
        if (lyricsOffsetOpen) {
            LyricsOffsetSheet(
                hazeState = playerHaze,
                onDismiss = onDismissLyricsOffset,
            )
        }
        lyricsShare?.let { request ->
            PlayerPlatform.host.LyricsShareSheet(
                hazeState = playerHaze,
                request = request,
                onDismiss = { lyricsShare = null },
            )
        }
    }

    // A landscape window — a tablet, or a phone on its side — takes an entirely
    // different shape from the portrait player below: two columns rather than
    // one, see [landscapePlayerAvailable] and [LandscapePlayerLayout].
    //
    // A separate branch rather than something woven into the layout below:
    // the portrait player's collapsing sleeve, hero banner and vertical drag
    // gesture exist to let one tall column be the player, the lyrics and the
    // queue in turn, and in landscape none of them has anything to do — the
    // sleeve never has to get out of anything's way.
    val landscape = landscapePlayerAvailable(windowWidth, windowHeight)
    // Tablet-sized players use the full-cover blur in all three states. On a
    // phone only the lyrics and queue replace the main player's seam-aware,
    // reflected mesh; the main player deliberately keeps its existing look.
    val tabletArtworkBackdrop = tabletSizedPlayer(windowWidth)
    // Owned by the song-level player composition, not by any one screen state.
    // Opening lyrics/queue, and rotating into or out of landscape, therefore
    // reuse the same tiny pre-blurred bitmap instead of running a screen-sized
    // effect.
    val fullArtworkBlurImage = rememberFullArtworkBlurImage(
        imageUrl = remoteArt,
        artPx = ART_PX,
        prepare = landscape || tabletArtworkBackdrop || lyricsOpen || queueOpen ||
            playerPrewarmStage >= 1,
    )
    // One movable Image node, not two backdrop call sites. Compose carries it
    // between the portrait and landscape players; only the cached bitmap
    // changes when the artwork preparation above completes for another song.
    val currentFullArtworkBlurImage = rememberUpdatedState(fullArtworkBlurImage)
    val fullArtworkBlurContent = remember {
        movableContentOf<Modifier> { backdropModifier ->
            FullArtworkBlurBackdrop(
                image = currentFullArtworkBlurImage.value,
                modifier = backdropModifier,
            )
        }
    }

    // Docking into the mini player — see [PlayerDock]. Both shapes of the
    // player take part: the portrait sleeve morphs into the cover (see the
    // sleeve), the landscape one is carried and scaled onto it (see
    // [landscapeDockPose]).
    val dock = LocalPlayerDock.current
    if (dock != null) {
        // Attached on first placement below rather than here: until then the
        // sheet's window may not have drawn anything, and the mini player's
        // cover is the only artwork on screen.
        DisposableEffect(dock) {
            onDispose { dock.attached = false }
        }
    }
    val dockFrame = remember { DockFrame() }
    // 1 with the player fully open, falling to 0 as the sheet reaches the mini
    // player. Held at 1 — the player exactly as it always was — with no dock,
    // no cover to dock into, or before the sheet's first placement has told
    // this where on screen it is.
    //
    // The sheet's fraction is read first and always, whatever comes of it: the
    // layout and draw blocks calling this are re-run only for the state they
    // read, and one that bailed out before reading it would never hear the
    // sheet move.
    val dockT: () -> Float = {
        if (dock == null) {
            1f
        } else {
            val open = dock.openFraction()
            if (dockFrame.anchor != null && dock.hasMiniArt()) open else 1f
        }
    }
    val docking: () -> Boolean = { dockT() < 1f }
    // The same, for composition: derived, so it recomposes the player twice a
    // trip — as the sheet leaves fully open and as it gets back — rather than
    // on each frame between.
    //
    // What it is for: a clip decoding behind the player while the artwork is
    // flying is work nobody can see — the player is fading out round it — and
    // it was costing frames of exactly the movement the eye is on. Paused for
    // the trip, as for the collapse into the lyrics or the queue; a paused
    // clip keeps its last frame, so nothing blinks.
    val dockMoving by remember { derivedStateOf { docking() } }
    // The rest of the player gets out of the way a little ahead of the artwork,
    // so the cover lands on the bar rather than on a ghost of the player.
    val dockFade: () -> Float = { ((dockT() - DOCK_FADE_LEAD) / (1f - DOCK_FADE_LEAD)).coerceIn(0f, 1f) }
    // The sleeve, drawn over the faded player while it docks — see the sleeve.
    val sleeveLayer = rememberGraphicsLayer()

    // The player's own end of the docking, for whichever shape it takes: where
    // it sits on screen, the fade, and the artwork drawn over that fade.
    val dockHost: Modifier = Modifier
        .then(
            if (dock != null) {
                Modifier.onGloballyPositioned { coordinates ->
                    dockFrame.host = coordinates
                    // Where the player would be fully open, on screen: its
                    // position now, less the sheet's offset that placed it
                    // there. Constant for the life of the sheet, and that
                    // plus the offset of any later frame is where the
                    // player is in that frame.
                    //
                    // Measured once, and again only if the player's size
                    // changes: this is called on every frame the sheet
                    // moves, with the same answer each time. Measuring it
                    // each time also walked the artwork back and forth by a
                    // pixel, the sheet rounding its offset its own way.
                    if (dockFrame.anchor != null && dockFrame.anchorFor == coordinates.size) {
                        return@onGloballyPositioned
                    }
                    val offset = dock.offset() ?: return@onGloballyPositioned
                    dockFrame.anchorFor = coordinates.size
                    dockFrame.anchor = coordinates.positionOnScreen() - Offset(0f, offset.toInt().toFloat())
                    if (!dock.attached) dock.attached = true
                }
            } else {
                Modifier
            },
        )
        // Outside the fade below, so the artwork is the one thing on the
        // player that doesn't fade on its way to the bar. Drawn on top of
        // everything while it travels, as the one thing still moving.
        .drawWithContent {
            drawContent()
            if (docking()) {
                translate(dockFrame.portalLeft, dockFrame.portalTop) {
                    val portalScale = dockFrame.portalScale
                    scale(portalScale, portalScale, pivot = Offset.Zero) { drawLayer(sleeveLayer) }
                }
            }
        }
        // Never quite zero while docking. A layer at alpha 0 is skipped
        // outright, children and all — and the sleeve records itself for
        // the draw above from inside it, so for the last stretch of the
        // way home the cover went on being drawn from a stale recording,
        // bigger and higher than the bar's. One step of 255 is nothing to
        // look at and keeps the subtree drawing.
        .graphicsLayer { alpha = if (docking()) dockFade().coerceAtLeast(DOCK_FADE_FLOOR) else 1f }

    if (landscape) {
        // Paused always wins outright over a scrub in progress — see
        // [ARTWORK_PAUSE_SHRINK_SCALE].
        val landscapeArtScale by animateFloatAsState(
            targetValue = when {
                !isPlaying -> ARTWORK_PAUSE_SHRINK_SCALE
                scrub.scrubbing -> ARTWORK_DRAG_SHRINK_SCALE
                else -> ARTWORK_EXPANDED_SCALE
            },
            animationSpec = tween(durationMillis = ARTWORK_SCALE_DURATION_MS, easing = ArtworkScaleEasing),
            label = "landscapeArtworkScale",
        )
        val versionAligning by PlayerSettings.versionAlignmentInProgress.collectAsStateWithLifecycle()
        val transitionWindow by PlayerSettings.smartTransitionWindow.collectAsStateWithLifecycle()
        val panelOpen = lyricsOpen || queueOpen

        // Where the sleeve is drawn this frame on its way to or from the mini
        // player's cover, or null when it can't be worked out.
        //
        // Unlike the portrait sleeve, this one keeps its layout and is carried:
        // it is square and so is the cover, so a move and a scale take it the
        // whole way, with nothing about the picture's crop to re-lay out. It
        // rides as much of the sheet's travel as it can without passing the
        // cover — the same share, for the same reason, as the portrait
        // sleeve's `dockRide`.
        fun landscapeDockPose(): LandscapeDockPose? {
            if (dock == null) return null
            val t = dockT()
            val mini = dock.miniArtOnScreen() ?: return null
            val anchor = dockFrame.anchor ?: return null
            val host = dockFrame.host?.takeIf { it.isAttached } ?: return null
            val sleeve = dockFrame.landscapeArt?.takeIf { it.isAttached } ?: return null
            val offset = (dock.offset() ?: return null).toInt().toFloat()
            val travel = dock.sheetTravel.takeIf { it > 0f } ?: return null
            if (sleeve.size.width <= 0) return null
            val open = Rect(
                offset = anchor + host.localPositionOf(sleeve, Offset.Zero),
                size = Size(sleeve.size.width.toFloat(), sleeve.size.height.toFloat()),
            )
            val ride = ((mini.top - open.top) / travel).coerceIn(0f, 1f)
            val rect = lerp(mini, open.translate(0f, offset * ride), t)
            // In the player's own pixels, where it sits this frame.
            return LandscapeDockPose(
                left = rect.left - anchor.x,
                top = rect.top - anchor.y - offset,
                scale = rect.width / open.width,
            )
        }
        // The sleeve's corner as laid out, rounding off into the cover's as it
        // lands. Laid out at full size and drawn scaled, so asked for as the
        // radius that comes out right once it has been scaled.
        val landscapeArtShape: () -> Shape = {
            val t = dockT()
            if (dock == null || t >= 1f) {
                dockFrame.cornerShape(LANDSCAPE_ART_CORNER)
            } else {
                val scale = landscapeDockPose()?.scale?.takeIf { it > 0f } ?: 1f
                dockFrame.cornerShape(lerp(dock.miniArtCorner(), LANDSCAPE_ART_CORNER, t) / scale)
            }
        }

        Box(modifier = modifier.fillMaxSize().then(dockHost)) {
            LandscapePlayerLayout(
                pane = when {
                    lyricsOpen -> PlayerPane.Lyrics
                    queueOpen -> PlayerPane.Queue
                    else -> PlayerPane.Main
                },
                background = { backgroundModifier ->
                    // The whole screen is the sheets' frost source: there is
                    // no full-bleed banner here to be it instead.
                    fullArtworkBlurContent(backgroundModifier.hazeSource(playerHaze))
                },
                artwork = { artworkModifier ->
                    LandscapeArtwork(
                        artRequest = art.request,
                        artLoaded = art.loaded,
                        onArtState = art::onState,
                        canvas = canvas,
                        canvasRendered = canvasRendered,
                        isPlaying = isPlaying,
                        onCanvasRenderedChange = { canvasRendered = it },
                        // A clip decoding behind a player on its way to the
                        // bar is work nobody sees — see [dockMoving].
                        pausedForTransition = dockMoving,
                        sleeveShape = landscapeArtShape,
                        // Let go of on the way to the mini player, whose
                        // cover sits flat in its bar.
                        shadowFraction = dockT,
                        modifier = artworkModifier
                            .then(
                                if (dock != null) {
                                    Modifier.onGloballyPositioned { dockFrame.landscapeArt = it }
                                } else {
                                    Modifier
                                },
                            )
                            // While it docks the sleeve is drawn by the player
                            // itself, over everything and outside the player's
                            // fade — see [dockHost] — at the place and size
                            // [landscapeDockPose] gives it.
                            .drawWithContent {
                                val pose = if (docking()) landscapeDockPose() else null
                                if (pose != null) {
                                    dockFrame.portalLeft = pose.left
                                    dockFrame.portalTop = pose.top
                                    dockFrame.portalScale = pose.scale
                                    sleeveLayer.record { this@drawWithContent.drawContent() }
                                } else {
                                    if (docking()) {
                                        // Nowhere to fly to this frame: the
                                        // portal draws nothing rather than a
                                        // stale copy.
                                        dockFrame.portalScale = 0f
                                    }
                                    drawContent()
                                }
                            }
                            .then(skipSwipeGesture)
                            .graphicsLayer {
                                // The paused shrink and the swipe nudge are
                                // let go of on the way, as the portrait
                                // sleeve's are: the cover does neither.
                                val t = dockT()
                                val scale = if (t >= 1f) landscapeArtScale else 1f + (landscapeArtScale - 1f) * t
                                scaleX = scale
                                scaleY = scale
                                translationX = if (t >= 1f) swipeSettle.value else swipeSettle.value * t
                            }
                            // With a panel up the sleeve is the way back to
                            // the player, as the portrait thumbnail is.
                            .clickable(
                                enabled = panelOpen,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                queueOpen = false
                                closeLyrics()
                            },
                    ) {
                        // The stats belong to the player, not to the sleeve, so
                        // they leave it along with the rest of the player.
                        SleeveNerdStats(
                            song = song,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                // Faded with the player rather than flown with
                                // the sleeve: the cover in the bar has none.
                                .graphicsLayer { alpha = if (panelOpen) 0f else dockFade() },
                        )
                    }
                },
                actions = playerActions,
                mainPane = { compact ->
                    LandscapeMainPane(
                        compact = compact,
                        caption = if (hideSongStatus) null else playbackOriginText(song, playedBy),
                        onOpenCaption = openPlaybackOrigin,
                        credits = {
                            LandscapeCredits(
                                song = song,
                                signedIn = signedIn,
                                likeStatus = likeStatus,
                                showRevertCue = showRevertCue,
                                onToggleLike = onToggleLike,
                                onOpenMenu = onOpenMenu,
                                onOpenAlbum = onOpenAlbum,
                                onOpenArtist = onOpenArtist,
                            )
                        },
                        lyricStrip = if (syncedLyricsEnabled) {
                            {
                                CurrentLyricStrip(
                                    lines = lyricsTranslation.displayedLyrics,
                                    trackKey = song.videoId,
                                    playhead = lyricsPlayhead,
                                    isPlaying = isPlaying && position.advancing,
                                    durationMs = durationMs,
                                    lyricsUnavailable = lyricsUnavailable,
                                    loadingText = lyricsLoadingText,
                                    onClick = openLyrics,
                                )
                            }
                        } else {
                            null
                        },
                        scrubber = {
                            PlayerScrubber(
                                shown = shown,
                                durationMs = durationMs,
                                loading = versionAligning || audioVersionSwitching,
                                mixing = mixing && !scrub.scrubbing,
                                transitionWindow = transitionWindow
                                    ?.takeIf { !scrub.scrubbing && it.end > it.start }
                                    ?.let { it.start..it.end },
                                onScrub = onScrub,
                                onScrubFinished = onScrubFinished,
                            ) {
                                PlaybackQualityLabel(
                                    song = song,
                                    isLoading = isLoading,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 8.dp),
                                )
                            }
                        },
                        transport = {
                            TransportRow(
                                isPlaying = isPlaying,
                                isLoading = isLoading || audioVersionSwitching,
                                previousEnabled = !controlsLocked &&
                                    (hasPrevious || pastRestartPoint),
                                nextEnabled = !controlsLocked && hasNext,
                                onPrevious = onPrevious,
                                onPlayPause = onPlayPause,
                                onNext = onNext,
                                compact = compact,
                            )
                        },
                        volume = if (hideVolumeBar) {
                            null
                        } else {
                            {
                                VolumeRow(
                                    value = { volume.level.value },
                                    onValueChange = onVolumeChange,
                                    onValueChangeFinished = onVolumeChangeFinished,
                                )
                            }
                        },
                    )
                },
                lyricsPane = {
                    LandscapeLyricsPane(
                        hasLyrics = lyricsTranslation.displayedLyrics.isNotEmpty(),
                        placeholder = if (lyricsUnavailable) {
                            stringResource(Res.string.lyrics_not_available)
                        } else {
                            lyricsLoadingText
                        },
                        status = lyricsTranslation.status,
                        onStatusClick = { showLyricsProviders = true },
                        picking = lyricPicker.picking,
                        pickBar = {
                            LyricsPickBar(
                                shareEnabled = lyricPicker.picks.isNotEmpty() &&
                                    !lyricPicker.overBudget,
                                onCancel = lyricPicker.cancel,
                                onShare = lyricPicker.share,
                            )
                        },
                        romanizationToggle = {
                            RomanizationToggleButton(
                                state = lyricsTranslation.romanizationState,
                                showingRomanization = lyricsTranslation.showingRomanization,
                                enabled = !lyrics.isNullOrEmpty(),
                                onClick = lyricsTranslation.toggleRomanization,
                            )
                        },
                        translationToggle = {
                            TranslationToggleButton(
                                state = lyricsTranslation.translationState,
                                showingTranslation = lyricsTranslation.showingTranslation,
                                enabled = !lyrics.isNullOrEmpty(),
                                onClick = lyricsTranslation.toggleTranslation,
                            )
                        },
                    ) { panelModifier ->
                        LyricsTranslationMotion(
                            trigger = lyricsTranslation.transition,
                            reduceMotion = lyricsTranslation.reduceMotion,
                            modifier = panelModifier,
                        ) { particleProgress ->
                            // [controlsOpen] is a constant `true`: that flag
                            // exists because the portrait player hides its
                            // transport behind the lyrics and needs a tap to
                            // bring it back. Here there is nothing hidden for
                            // a tap to reveal, and leaving the reveal gesture
                            // armed would only eat taps meant for the lines.
                            LyricsPanel(
                                lines = lyrics.orEmpty(),
                                subLines = lyricsTranslation.subLines,
                                trackKey = song.videoId,
                                playhead = lyricsPlayhead,
                                looking = !lyricsUnavailable,
                                isPlaying = isPlaying && position.advancing,
                                onSeekToLine = seekToLyric,
                                controlsOpen = true,
                                onRevealControls = {},
                                onHideControls = {},
                                translationProgress = particleProgress,
                                canPick = lyricsShareEnabled,
                                picking = lyricPicker.picking,
                                picked = lyricPicker.picks,
                                onPickLine = lyricPicker.pick,
                                onTogglePick = lyricPicker.toggle,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
                queuePane = {
                    InlineQueue(
                        queue = queue,
                        currentIndex = queueIndex,
                        autoplayEnabled = autoplayEnabled,
                        controlsLocked = controlsLocked,
                        onJumpTo = onJumpTo,
                        onRemove = onRemoveFromQueue,
                        onMove = onMoveInQueue,
                        onClear = onClearQueue,
                        onDragActiveChange = onQueueDragActiveChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
            playerOverlays()
        }
        return
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { playerBounds = it }
            .then(dockHost)
            .background(Color.Black),
    ) {
        // Anchored to the sleeve's bottom edge, so the screen carries on in the
        // colours the artwork ended in rather than in a quantiser's idea of what
        // the artwork was about. Position ticks recompose this screen twice a
        // second and must not drag a full-screen blur along with them, which is
        // why the mesh is passed as one immutable value.
        //
        // The seam is the *expanded* banner's bottom edge and is left there as
        // the player collapses, rather than following the sleeve down: it is
        // the anchor for a blurred layer, and moving it would re-blur the whole
        // screen on every frame of the drag. Above it the mesh holds one colour,
        // so a seam left behind a collapsed sleeve shows nothing at all.
        // Both phone backgrounds stay mounted for the entire song. Only these
        // retained layers' alpha changes when a panel opens, so the full-cover
        // blur is neither rebuilt nor switched in on a hard frame boundary.
        // The tablet has no mirrored main-player treatment to crossfade from.
        // Legacy mesh is the whole backdrop on every page, so the lyrics and
        // queue panels must not crossfade the blurred artwork over it.
        val legacyMeshBackdrop = !tabletArtworkBackdrop && !spotifyCanvasPresentation &&
            legacyMesh && !canvasFirstPortrait
        val fullArtworkBackdropAlpha by animateFloatAsState(
            targetValue = if (
                !legacyMeshBackdrop &&
                (tabletArtworkBackdrop || lyricsOpen || queueOpen) &&
                (tabletArtworkBackdrop || fullArtworkBlurImage != null)
            ) 1f else 0f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            label = "fullArtworkBackdropCrossfade",
        )
        if (legacyMeshBackdrop) {
            // v1.5's backdrop, restored verbatim: no seam, because the blobs
            // are not anchored to anything on screen — they fill the player and
            // the artwork simply sits on top of them. Keyed on the track, so
            // they drift when the player opens and on every skip, then rest.
            // Position ticks recompose this screen twice a second and must not
            // drag a full-screen blur along with them, which is why the palette
            // is passed as one immutable value.
            MeshGradientBackground(
                palette = rememberArtworkColors(remoteArt, canvasFrame),
                trackKey = song.videoId,
                modifier = Modifier.graphicsLayer { alpha = 1f - fullArtworkBackdropAlpha },
            )
        } else if (!tabletArtworkBackdrop && !spotifyCanvasPresentation) {
            ArtworkMeshBackdrop(
                mesh = artMesh,
                seam = if (canvasFirstPortrait) renderedCanvasBottom else if (heroMode) heroHeight else 0.dp,
                modifier = Modifier.graphicsLayer { alpha = 1f - fullArtworkBackdropAlpha },
            )
        }
        fullArtworkBlurContent(
            Modifier.graphicsLayer { alpha = fullArtworkBackdropAlpha },
        )

        // Mount once, behind the controls. A known portrait aspect changes the
        // invisible view to full-player bounds before its first frame is shown.
        if (heroMode && heroHeight > 0.dp) {
            heroClipMounted?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = isPlaying,
                    // Paused for the whole collapse into the queue/lyrics panel
                    // and back, not just once it hands off to the still frame at
                    // p >= 0.5 — see [CanvasArtworkPlayer.pausedForTransition].
                    // And for the whole trip to or from the mini player.
                    //
                    // The panels asked first: a collapse a tap asked for is
                    // paused from the tap, and this screen doesn't then listen
                    // for the sleeve's first frame of movement as well.
                    pausedForTransition = lyricsOpen || queueOpen || queueDragging ||
                        collapseStarted || dockMoving,
                    // Spotify's 9:16 Canvas is the phone background, so it
                    // covers every edge. Other providers retain the contained
                    // portrait treatment introduced for motion cover art.
                    contentMode = if (clipFullscreen) {
                        CanvasContentMode.CROP
                    } else {
                        CanvasContentMode.FIT_PORTRAIT
                    },
                    alignPortraitTop = clipPortrait,
                    onAspectRatioChanged = { canvasAspect = it },
                    portraitRevealBounds = playerBounds,
                    // Gone by the half, where [heroClip] lets it go — and the
                    // still sleeve collapsing over it takes exactly the cover
                    // this gives up, so the two never leave a gap or a pop
                    // between them.
                    // And with the rest of the player as it docks: a
                    // TextureView doesn't reliably take the fade from a
                    // Compose layer around it.
                    presentationAlpha = { (1f - 2f * p()).coerceIn(0f, 1f) * dockFade() },
                    onRenderedChanged = { canvasRendered = it },
                    onFrameCaptured = {
                        if (!clipFullscreen && !tabletArtworkBackdrop &&
                            !lyricsOpen && !queueOpen && !queueDragging
                        ) canvasFrame = it
                    },
                    // The full-screen Spotify video has no mesh to re-tint.
                    // Keeping this null also removes the old three-second GPU
                    // readback cadence from this provider alone.
                    refreshFrameEveryMs = if (
                        clipFullscreen || tabletArtworkBackdrop || lyricsOpen || queueOpen ||
                        queueDragging || dockMoving
                    ) {
                        null
                    } else {
                        meshRefreshMs
                    },
                    onCoverChanged = { canvasCover.floatValue = it },
                    bottomFade = when {
                        clipFullscreen -> 0f
                        clipPortrait -> clipFadeFraction
                        else -> HERO_FADE_FRACTION
                    },
                    bottomFadeEndPx = if (clipPortrait) with(density) { clipBottom.toPx() }
                        else null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .then(
                            if (clipFullscreen || clipPortrait) Modifier.fillMaxSize()
                            else Modifier.fillMaxWidth().height(heroHeight),
                        )
                        .hazeSource(playerHaze),
                )
            }
        }

        // This transparent top gradient is always present while the modal
        // player owns the system bar. Its opacity follows the actual top-band
        // artwork, rather than changing the status-bar glyph colour per cover.
        // A subview replaces that hero with an artwork-derived mesh, so it gets
        // only a modest floor rather than an opaque status-bar surface.
        val playerSubviewOpen = lyricsOpen || queueOpen || lyricsOffsetOpen ||
            showAudioPipeline || showCast || showAudioOutput || showLyricsProviders ||
            lyricsShare != null
        val topGradientAlpha = if (playerSubviewOpen) {
            maxOf(artworkStatusScrimAlpha, SUBVIEW_STATUS_SCRIM_MIN_ALPHA)
        } else {
            artworkStatusScrimAlpha
        }

        val steps = 8
        val gradientColors = remember(topGradientAlpha) {
            List(steps) { index ->
                val progress = index / (steps - 1).toFloat()
                val factor = (1f - progress).toDouble().pow(1.5).toFloat()
                Color.Black.copy(alpha = topGradientAlpha * factor)
            }
        }
        val topScrimBrush = remember(gradientColors) {
            Brush.verticalGradient(gradientColors)
        }
        // Painted by the handle strip rather than laid out here — see there.

        if (canvasFirstPortrait && canvasRendered) {
            // The image remains visible through the controls; a plain scrim
            // protects text and touch targets without erasing the video.
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = canvasCover.floatValue }
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.40f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.70f),
                        ),
                    ),
            )
        }

        // Spotify Canvas keeps its pixels sharp across the whole phone. Pure
        // glass blur belongs only under the temporary lower control deck,
        // above the video. When the deck slides away this layer leaves with
        // it as well.
        AnimatedVisibility(
            visible = spotifyCanvasPresentation && spotifyCanvasControlsOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.56f),
            enter = fadeIn(tween(SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS)) + playerDeckSlideIn(),
            exit = fadeOut(tween(SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS)) + playerDeckSlideOut(),
        ) {
            // Subscribe only while Spotify's glass layer exists. Static art and
            // non-Spotify motion covers do not observe this new state at all.
            val reduceDynamicBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (reduceDynamicBlur || !renderEffectBlurSupported) {
                            // A real backdrop RenderEffect is unavailable on
                            // older Android versions and intentionally skipped
                            // when dynamic blur is reduced. Retain the deck's
                            // contrast without paying for a fake blur path,
                            // while keeping the same progressive top edge as
                            // the live glass surface.
                            Modifier.background(
                                Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    SPOTIFY_DECK_TOP_FADE_FRACTION to Color.Black.copy(alpha = 0.40f),
                                    1.00f to Color.Black.copy(alpha = 0.40f),
                                ),
                            )
                        } else {
                            Modifier.optimizedHazeEffect(
                                state = playerHaze,
                                // Do not use HazeMaterials with Transparent:
                                // that preset treats transparent RGB as black
                                // and replaces its alpha with a dark 0.8 tint.
                                style = HazeStyle(
                                    backgroundColor = Color.Transparent,
                                    tints = emptyList(),
                                    // Canvas stays sharp above the deck; the
                                    // lower glass needs stronger separation
                                    // from a moving video than static artwork.
                                    blurRadius = 48.dp,
                                    noiseFactor = 0f,
                                    fallbackTint = HazeTint(Color.Transparent),
                                ),
                            ) {
                                // This is a continuously changing video, so a
                                // full third-resolution backdrop is still more
                                // detail than a 48dp blur can preserve. At 18%
                                // this processes roughly 30% of the pixels used
                                // by optimizedHazeEffect's normal 33% path.
                                inputScale = HazeInputScale.Fixed(0.18f)
                                canDrawArea = { true }
                                mask = Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    SPOTIFY_DECK_TOP_FADE_FRACTION to Color.Black,
                                    1.00f to Color.Black,
                                )
                            }
                        },
                    ),
            )
        }

        // While the control deck is up, the whole Canvas sits under a flat 50%
        // dim so the buttons and text read against any clip. It fades out as
        // the deck collapses to the full-screen video, and back in with the
        // deck, on the deck's own timing. Alpha is read in the draw phase, so
        // the fade never recomposes the player. Drawn above the glass deck, not
        // under it: the deck's blur samples the video layer itself, so a dim
        // beneath it never reached the glass and the deck stayed bright.
        val spotifyCanvasDim = animateFloatAsState(
            targetValue = if (spotifyCanvasPresentation && spotifyCanvasControlsOpen) 0.5f else 0f,
            animationSpec = tween(SPOTIFY_CANVAS_CONTROLS_ANIMATION_MS),
            label = "spotifyCanvasDim",
        )
        val spotifyCanvasDimShowing by remember { derivedStateOf { spotifyCanvasDim.value > 0f } }
        if (spotifyCanvasPresentation || spotifyCanvasDimShowing) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = spotifyCanvasDim.value }
                    .background(Color.Black),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // Observe unclaimed taps across the whole Canvas. Buttons and
                // the compact metadata row consume their own taps first; empty
                // video above or below them toggles the lower deck either way.
                .toggleSpotifyCanvasControlsOnTap(
                    enabled = spotifyCanvasPresentation && !mixing,
                    onToggle = { spotifyCanvasControlsOpen = !spotifyCanvasControlsOpen },
                )
                .then(skipSwipeGesture),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The only strip that passes drags through to the sheet, so the
            // player closes from the handle and the space around it — not from
            // a stray downward swipe on the artwork or the controls.
            //
            // Drawn above the column under it, and the status-bar scrim with
            // it: the full-bleed sleeve lives in that column and runs up behind
            // both, and the handle, the caption and the scrim all belong on
            // top of the artwork. The scrim reaches up past the strip's own
            // top to the screen's, the band it always covered.
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .height(topStrip)
                    .drawBehind {
                        // Translated rather than offset: the gradient is laid
                        // out from the canvas origin, not from the rect's.
                        val above = statusBarTop.toPx()
                        translate(top = -above) {
                            drawRect(
                                brush = topScrimBrush,
                                size = Size(size.width, size.height + above),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // While the origin caption is present the handle belongs at
                // the top of the strip. As lyrics or the queue replace the
                // album-cover player, it glides into the now-empty strip's
                // vertical centre alongside the caption's fade.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = lerp(
                                    6.dp,
                                    (topStrip - 5.dp).coerceAtLeast(0.dp) / 2,
                                    p(),
                                ).roundToPx(),
                            )
                        }
                        .width(38.dp)
                        .height(5.dp)
                        .graphicsLayer { alpha = spotifyChromeAlpha }
                        .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.70f)),
                )
                // [p] is the shared album-to-panel transition. Keeping this in
                // composition until its final frame gives the caption a real
                // fade on both entry and exit, but removes its click target
                // entirely once lyrics or the queue owns the player.
                if (!hideSongStatus) {
                    WhileShown({ !collapseAlmostDone }) {
                        PlaybackOriginCaption(
                            text = playbackOriginText(song, playedBy),
                            onClick = openPlaybackOrigin,
                            textAlign = TextAlign.Center,
                            contentPadding = PaddingValues(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = 1.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .graphicsLayer { alpha = (1f - p()) * spotifyChromeAlpha },
                        )
                    }
                }
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
                    //
                    // With one hole in it, and where that hole is depends on
                    // which screen of the player is up:
                    //
                    //  * The main player — the artwork-and-credits block. Down is
                    //    left unconsumed for the sheet to dismiss with, so the
                    //    player closes from the picture as well as from the
                    //    handle; up is taken here and drags the queue in.
                    //  * The queue or the lyrics — the header those panels sit
                    //    below, and nothing else. Down closes the player, up does
                    //    nothing: there is no sleeve left to pull away from.
                    //
                    // The header is worked out from the state rather than read
                    // off the sleeve, which is the whole point of doing it here:
                    // the sleeve is still on its way for [QUEUE_TRAVEL_MS] after
                    // the queue opens, and a hole that waited for it spent that
                    // half second lying across a list the finger was already
                    // scrolling.
                    .onGloballyPositioned { dismissBandSpace = it }
                    .pointerInput(showAudioPipeline, showCast, panelScrolling) {
                        if (showAudioPipeline || showCast || panelScrolling) return@pointerInput
                        awaitEachGesture {
                            // Unconsumed on purpose, as the blanket version was:
                            // the collapsed sleeve's own clickable — the way back
                            // out of the queue — has taken the press by the time
                            // an ancestor sees it.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val space = dismissBandSpace
                            val y = space?.localToRoot(down.position)?.y
                                ?: down.position.y
                            // A panel is up from the moment it is asked for to
                            // the moment the sleeve has finished growing back —
                            // never mind where the sleeve is in between.
                            val panelUp = queueOpen || lyricsOpen ||
                                queueSlide.floatValue > 0.01f
                            val bandTop: Float
                            val bandBottom: Float
                            if (panelUp) {
                                bandTop = space?.positionInRoot()?.y ?: 0f
                                bandBottom = bandTop +
                                    (ART_BOX_TOP_PAD + HEADER_HEIGHT).toPx()
                            } else {
                                bandTop = dismissBandTop
                                bandBottom = dismissBandBottom
                            }
                            if (y >= bandTop && y <= bandBottom) {
                                if (!panelUp) {
                                    dragQueueIn(
                                        down = down,
                                        travel = bandBottom - bandTop -
                                            HEADER_HEIGHT.toPx(),
                                        slide = queueSlide,
                                        onHold = { queueDragging = it },
                                        onSettle = { open ->
                                            if (open != queueOpen) {
                                                haptics.play(
                                                    if (open) Haptic.Expand else Haptic.Tap,
                                                )
                                                if (open) queueControlsOpen = true
                                                queueOpen = open
                                            }
                                            queueReleased++
                                        },
                                    )
                                }
                                return@awaitEachGesture
                            }
                            // A down already taken means the sheet grabbed it
                            // while "settling" after a scroll (see
                            // guardSheetFromContentTouches), and the guard is
                            // about to hand it back to the list under the
                            // finger. Holding it here would strand that list
                            // mid-handback, and the list would never scroll.
                            if (down.isConsumed) return@awaitEachGesture
                            // What detectVerticalDragGestures does, minus the
                            // callbacks: cross the slop, then hold the gesture
                            // to the end so nothing downstream of the first
                            // event reaches the sheet either.
                            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (drag != null) verticalDrag(drag.id) { it.consume() }
                        }
                    }
                    .padding(horizontal = PLAYER_GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // ---- Top and centre: artwork, then the credits ----
            // Everything that changes between the artwork and the queue lives
            // in this one weighted box, so the controls below it never move.
            // The loading state the scrubber wears for the length of a version
            // switch — see [ThinSlider.loading]. The flag is the alignment
            // half (fetch + measure); [audioVersionSwitching] is the resolve.
            // Both named here, so there is no frame of the switch with nothing
            // on screen saying the player is still working.
            val versionAligning by PlayerSettings.versionAlignmentInProgress.collectAsStateWithLifecycle()
            val versionSwitching = versionAligning || audioVersionSwitching
            // Height the artwork block below turns out not to need, spent by the
            // controls at the foot of the screen. Filled in from inside the box,
            // where the sleeve's real size is known; see [lastControlSpread].
            var controlSpread by remember { mutableStateOf(lastControlSpread) }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(top = ART_BOX_TOP_PAD, bottom = 18.dp),
            ) {
                if (spotifyCanvasPresentation && playerDeckSettledOpen &&
                    maxHeight != spotifyCanvasExpandedTopHeight
                ) {
                    SideEffect { spotifyCanvasExpandedTopHeight = maxHeight }
                }
                // The height this box would have if the controls at the foot of
                // the screen were at their natural size. They aren't: they are
                // holding [controlSpread] of extra gap, which came out of here,
                // so adding it back cancels the only thing down there that
                // depends on what is decided up here.
                //
                // The sleeve and the slack below are both worked out from this
                // rather than from the box as it actually stands, and that is
                // what keeps the hand-off from creeping. Measured off the real
                // height, granting the gaps 20dp came back as a box 20dp
                // shorter and read as a *further* 20dp going spare — so any
                // moment the controls were briefly shorter than usual (a track
                // change, where the lyric strip drops back to its loading line,
                // or coming back from the lyrics panel, where the strip is
                // rebuilt from scratch) was pocketed for good. The gaps
                // ratcheted open a little at a time and the sleeve paid for it.
                val roomy = maxHeight + controlSpread
                // The sleeve is square, so it is bounded by whichever of the
                // two axes runs out first: the player's width on a phone, or —
                // on a tablet, where there is width to spare — the height left
                // over once the credits row and the gap above it have had
                // theirs. Sizing it off the width alone is what pushed the
                // credits down across the scrubber on anything but a phone.
                val wantArt = minOf(maxWidth, roomy - ART_TITLE_GAP - HEADER_HEIGHT)
                // Held to what the box has actually got, for the single frame it
                // takes the gaps below to catch up with a change in their own
                // height: a sleeve a few dp under for one frame is a better
                // failure than a credits row overhanging the lyric strip.
                val fullArt = minOf(wantArt, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(THUMB_SIZE)
                // What's left over once the sleeve, the gap and the credits have
                // had theirs. A few dp on a phone; the better part of a
                // centimetre on anything taller, and since the group is centred,
                // half of it used to land between the credits and the lyric strip
                // as one wide hole in the middle of the controls.
                val slack = (roomy - wantArt - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp)
                // Handed to the two gaps around the transport row instead, which
                // is where a tall screen should be doing its breathing.
                //
                // Assigned, not added to: [slack] is stated in terms the spread
                // cannot move, so this is the whole answer in one step, and it
                // gives the room back just as readily when the controls grow
                // into it again.
                //
                // The settled spread is retained while either panel is up. It is
                // part of the controls' footprint, not part of the artwork, and
                // removing it only for lyrics made the half-player jump shorter
                // at the exact moment the sleeve started collapsing.
                //
                // Granted in whole even pixels, and only when it actually moves.
                // This is a measurement feeding the layout it was measured from,
                // and [roomy] cancels that by adding the grant back — but only if
                // this pass's [maxHeight] already reflects the grant about to be
                // written, which needs the Column above to have re-measured the
                // controls at that grant already. It doesn't always have: on some
                // aspect ratios
                // the cancellation lands a pass late, the grant overshoots, the
                // next pass corrects past it the other way, and the two chase
                // each other through the same handful of values forever instead
                // of settling — a full-amplitude standing oscillation, not the
                // single-pixel shiver this rounding alone was built to absorb.
                // See [granted] below for the fix.
                // Do not feed transitional artwork measurements back into the controls.
                // The settled player's spread is retained throughout the return animation.
                // Spotify's compact Canvas state removes the entire deck, making this
                // weighted box much taller. That newly empty space is not control slack:
                // recording it here inflated both transport gaps when a queue swipe
                // brought the deck back. Only measure while the deck is actually present.
                val controlDeckIsMeasured = !spotifyCanvasPresentation || playerDeckSettledOpen
                // The panels asked about first, so a collapse on its way in
                // — whose sleeve leaves rest on its first frame — isn't
                // listened for here at all.
                if (!lyricsOpen && !queueOpen && !queueDragging && collapseAtRest && controlDeckIsMeasured) {
                    val target = with(density) {
                        val half = slack
                            .coerceAtMost(CONTROL_GAP_SPREAD_MAX * 2)
                            .toPx()
                            .div(2f)
                            .roundToInt()
                        (half * 2).toDp()
                    }
                    // Stepped towards [target] rather than jumped there in one
                    // grant, so a late cancellation (see above) decays instead of
                    // standing: still one pass to settle when the cancellation
                    // does land on time, and a fast-converging approach rather
                    // than a full-amplitude swing on the passes where it doesn't.
                    val granted = with(density) {
                        val steppedPx = (controlSpread.toPx() +
                            (target.toPx() - controlSpread.toPx()) * 0.4f)
                            .roundToInt()
                        steppedPx.toDp()
                    }
                    if (granted != controlSpread) {
                        SideEffect {
                            controlSpread = granted
                            lastControlSpread = granted
                        }
                    }
                }
                // Artwork and the title row travel together as one block, so
                // the pair sits centred while the queue is closed — in whatever
                // the controls couldn't take, which on all but the tallest
                // screens is nothing.
                val groupTop = (maxHeight - fullArt - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp) / 2
                // Functions of the collapse, called at placement and measure
                // — see [p]. Composition never reads them.
                fun artSize(): Dp = lerp(fullArt, THUMB_SIZE, p())
                fun artTop(): Dp = lerp(groupTop, 0.dp, p())
                // Expanded and height-bound, the sleeve is narrower than the
                // player and has to be centred in it; collapsed, it belongs
                // hard against the left edge with the credits beside it.
                fun artStart(): Dp = lerp((maxWidth - fullArt) / 2, 0.dp, p())
                fun regularTitleTop(): Dp = lerp(groupTop + fullArt + ART_TITLE_GAP, 0.dp, p())
                // Once Spotify's control deck has left, keep the only remaining
                // player chrome against the bottom edge instead of stranding it
                // where the artwork's title normally sits near mid-screen.
                val spotifyTitleRange = if (spotifyCanvasPresentation) {
                    val expandedTopHeight = spotifyCanvasExpandedTopHeight
                        .takeIf { it > 0.dp }
                        ?: maxHeight
                    val collapsedTitleTop = (
                        expandedTopHeight + spotifyCanvasDeckHeight - HEADER_HEIGHT
                    ).coerceAtLeast(0.dp)
                    collapsedTitleTop
                } else {
                    null
                }
                fun currentTitleTop(): Dp = spotifyTitleRange?.let { collapsedTitleTop ->
                    // Read in the placement lambda below. This invalidates only
                    // placement, not composition or measurement, and keeps the
                    // credits on the deck's exact reveal frame.
                    lerp(collapsedTitleTop, regularTitleTop(), playerDeckReveal.value)
                } ?: regularTitleTop()
                fun titleStart(): Dp = lerp(0.dp, THUMB_SIZE + 12.dp, p())

                // How far down the *screen* the sleeve's bottom edge sits, which
                // is where the full-bleed banner has to stop for the credits
                // below it not to move when it appears. Everything between the
                // screen's top and this box's own top is fixed padding, so it
                // can simply be added back up rather than measured.
                val bannerBottom = statusBarTop + topStrip + ART_BOX_TOP_PAD +
                    groupTop + fullArt + ART_TITLE_GAP / 2
                // Frozen while lyrics are up. The controls now retain their full
                // footprint across the transition, so this answer is identical
                // on both sides; avoiding writes during the panel keeps the
                // backdrop independent of its animation. The first pass is
                // exempt so a player composed with lyrics already open still
                // receives an anchor.
                //
                // Guarded, like the spread above: this runs on every pass, and a
                // state write from inside a layout is a recomposition asked for
                // from inside a layout. Writing the same answer back costs a
                // comparison here and a whole frame if it is left to the snapshot
                // to notice.
                val bannerSettled = !lyricsOpen || heroHeight == 0.dp
                if (bannerSettled && bannerBottom != heroHeight) {
                    SideEffect { heroHeight = bannerBottom }
                }

                // Where the sleeve is drawn, as a function of the collapse — see
                // [p] — in this box's own pixels. Called at measure, placement
                // and draw; composition never reads it.
                //
                // Three rects rather than two. The card is the square sleeve
                // ([artStart], [artTop], [artSize]); the banner is the
                // full-bleed artwork — the player's whole width, up behind the
                // status bar, down to [heroHeight]; the thumbnail is the header.
                // [heroT] carries the expanded sleeve from the card to the
                // banner, and the collapse carries either one down to the
                // thumbnail. One layer the whole way: opening the queue or the
                // lyrics shrinks the full-bleed artwork itself into the header,
                // instead of dissolving it over a second, card-sized copy that
                // shrinks underneath.
                //
                // The banner is placed off this box's own edges: above it is
                // fixed padding (see [bannerBottom]), and either side the
                // gutter, which the two widths give back between them.
                val bannerTopPx = with(density) {
                    -(statusBarTop.roundToPx() + topStrip.roundToPx() + ART_BOX_TOP_PAD.roundToPx())
                }
                val boxWidthPx = constraints.maxWidth
                // How far the expanded sleeve is the banner rather than the
                // card. Zero until there is a banner to be, which is to say
                // until [heroHeight] and the player's width have been measured.
                fun bannerShare(): Float =
                    if (heroHeight > 0.dp && playerBounds.width > 0) heroT.value else 0f
                // How much of the banner is on screen: its share, given up as
                // the sleeve collapses — the same movement as the collapse
                // rather than a fade chained after it.
                fun bannerShown(): Float = bannerShare() * (1f - p())
                // The card's paused shrink, let go of as it collapses.
                fun cardScale(): Float = artScale + (1f - artScale) * p()
                fun cardRect(): Rect = with(density) {
                    val side = artSize().roundToPx().toFloat()
                    Rect(
                        offset = Offset(artStart().roundToPx().toFloat(), artTop().roundToPx().toFloat()),
                        size = Size(side, side),
                    )
                }
                fun sleeveRect(): Rect {
                    val card = cardRect()
                    val share = bannerShare()
                    if (share == 0f) return card
                    val banner = with(density) {
                        Rect(
                            offset = Offset(-(playerBounds.width - boxWidthPx) / 2f, bannerTopPx.toFloat()),
                            size = Size(playerBounds.width.toFloat(), heroHeight.roundToPx().toFloat()),
                        )
                    }
                    val thumbSide = with(density) { THUMB_SIZE.roundToPx().toFloat() }
                    val thumb = Rect(Offset.Zero, Size(thumbSide, thumbSide))
                    return lerp(card, lerp(banner, thumb, p()), share)
                }
                // The box's left edge in the player — the gutter, which the two
                // widths give back between them.
                val boxLeftPx = ((playerBounds.width - boxWidthPx) / 2f).roundToInt()
                // The mini player's cover in this box's own pixels, this frame,
                // or null when the player isn't docking. The box sits a fixed
                // padding below the player's top (see [bannerBottom]), and the
                // player wherever the sheet has put it this frame.
                fun miniInBox(): Rect? {
                    if (dock == null || !docking()) return null
                    val mini = dock.miniArtOnScreen() ?: return null
                    val anchor = dockFrame.anchor ?: return null
                    val offset = dock.offset() ?: return null
                    dockFrame.offset = offset.toInt().toFloat()
                    return mini.translate(
                        -(anchor.x + boxLeftPx),
                        -(anchor.y + dockFrame.offset - bannerTopPx),
                    )
                }
                // How much of the sheet's own travel the artwork rides on its
                // way between the player and the cover.
                //
                // Riding all of it — blending from the cover to where the sleeve
                // sits on the moving sheet — sent the cover past its mark: that
                // end of the blend is off the bottom of the screen as the sheet
                // goes, and for the last stretch it dragged the artwork a good
                // twenty pixels below the bar before it climbed back. Riding
                // none of it leaves the artwork standing still under a finger
                // dragging the player down. This much is the most it can ride and
                // still never pass the cover — it lands tangentially, its speed
                // run down to nothing exactly on the cover — and on the way it
                // still follows the finger most of the way.
                //
                // Worked out off the sleeve, once a frame, and shared with the
                // tile inside it so the two travel together.
                fun dockRide(sleeve: Rect, mini: Rect): Float {
                    val travel = dock?.sheetTravel ?: return 0f
                    if (travel <= 0f) return 0f
                    // The cover's distance below where the sleeve sits with the
                    // sheet fully open, as a share of the sheet's travel.
                    return ((mini.top + dockFrame.offset - sleeve.top) / travel).coerceIn(0f, 1f)
                }
                // A rect of the sleeve's, carried as far towards the cover as
                // the sheet has gone.
                fun towardsDock(rect: Rect, mini: Rect?): Rect {
                    if (mini == null) return rect
                    val unridden = dockFrame.offset * (1f - dockFrame.ride)
                    return lerp(mini, rect.translate(0f, -unridden), dockT())
                }
                // The banner's dissolve, firmed up into the cover's hard edge as
                // it docks.
                fun sleeveShown(): Float = bannerShown() * dockT()
                fun sleeveCorner(): Dp {
                    val corner = 8.dp * (1f - sleeveShown())
                    val t = dockT()
                    return if (dock == null || t >= 1f) corner else lerp(dock.miniArtCorner(), corner, t)
                }

                // The sleeve proper — card, banner and thumbnail in turn, see
                // [sleeveRect]. Separate from the slot below, which keeps the
                // card's place for the stats line and the gestures: a banner
                // is not where the stats sit, nor where a drag should start.
                Box(
                    modifier = Modifier
                        // Measured and placed at layout time rather than in
                        // composition, so the collapse costs a layout pass and
                        // not a recomposition per frame. Reports no size of its
                        // own: the banner is wider than this box, and a child
                        // measured past its constraints gets re-centred on them.
                        .layout { measurable, _ ->
                            // Stashed for the tile inside, which is measured
                            // within this, and for the player's own draw.
                            val mini = miniInBox()
                            val open = sleeveRect()
                            if (mini != null) dockFrame.ride = dockRide(open, mini)
                            val rect = towardsDock(open, mini)
                            dockFrame.miniInBox = mini
                            dockFrame.sleeve = rect
                            val placeable = measurable.measure(
                                Constraints.fixed(rect.width.roundToInt(), rect.height.roundToInt()),
                            )
                            layout(0, 0) {
                                val x = rect.left.roundToInt()
                                val y = rect.top.roundToInt()
                                dockFrame.portalLeft = (boxLeftPx + x).toFloat()
                                dockFrame.portalScale = 1f
                                dockFrame.portalTop = (y - bannerTopPx).toFloat()
                                placeable.place(x, y)
                            }
                        }
                        // While it docks the sleeve is drawn by the player
                        // itself, over everything and outside the player's
                        // fade, rather than here among the things fading
                        // around it. The same node, laid out right here: only
                        // where its pixels land changes.
                        .drawWithContent {
                            if (docking()) {
                                sleeveLayer.record { this@drawWithContent.drawContent() }
                            } else {
                                drawContent()
                            }
                        }
                        .graphicsLayer {
                            // The paused shrink and the swipe nudge belong to
                            // the full card alone. A banner shrinking or sliding
                            // would open bare backdrop down the screen's edges,
                            // and the mini player's cover does neither.
                            val share = bannerShare()
                            val idle = cardScale()
                            val t = dockT()
                            val open = idle + (1f - idle) * share
                            val scale = if (t >= 1f) open else 1f + (open - 1f) * t
                            scaleX = scale
                            scaleY = scale
                            translationX = swipeSettle.value * (1f - p()) * (1f - share) * t
                        }
                        // Haze must observe the drawable layer itself, the
                        // banner included — a source on the surrounding layout
                        // only captured its mesh backdrop.
                        .hazeSource(playerHaze)
                        .graphicsLayer {
                            // Hands its opacity to a full-bleed clip as the clip
                            // takes over, and takes it straight back if there
                            // is no clip mounted to hand it to. The clip is gone
                            // by the collapse's half, so the sleeve is whole
                            // again by the time it unmounts.
                            alpha = if (heroClip != null) 1f - canvasCover.floatValue else 1f
                        }
                        .graphicsLayer {
                            val shown = sleeveShown()
                            // A drop shadow grounds a photo; on the flat
                            // placeholder tile it has nothing to sit behind, so
                            // it just reads as a second, darker square ringing
                            // the first. Only cast it once there's actually art,
                            // and only as a card: the banner has no edge to cast
                            // one from, so it comes in over the second half of a
                            // collapse, once the dissolve has all but gone. Let
                            // go of on the way to the mini player, whose cover
                            // sits flat in its bar.
                            shadowElevation = if (art.loaded) {
                                10.dp.toPx() * (1f - 2f * shown).coerceAtLeast(0f) * dockT()
                            } else {
                                0f
                            }
                            shape = dockFrame.cornerShape(sleeveCorner())
                            // What `.shadow` did: it clips only when it casts.
                            clip = art.loaded
                        }
                        .graphicsLayer {
                            // Clipped a second time, as `.shadow` and then
                            // `.clip` always did: a card's anti-aliased corners
                            // come out as they did before the banner moved in.
                            shape = dockFrame.cornerShape(sleeveCorner())
                            clip = true
                            // The mask below erases part of what this layer
                            // drew, which it can only do in a buffer of its own.
                            compositingStrategy = if (sleeveShown() > 0f) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                        }
                        // The banner dissolves into the backdrop where the
                        // card's bottom edge would have been, and firms back
                        // up into that edge as it collapses.
                        .drawWithContent {
                            drawContent()
                            val shown = sleeveShown()
                            if (shown > 0f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - shown)),
                                        startY = size.height * (1f - HERO_FADE_FRACTION),
                                        endY = size.height,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                ) {
                    // Empty state lives on this tile, not the AsyncImage: a
                    // background *and* a painter both trying to fill the same
                    // clipped shape is what read as two overlapping squares
                    // whenever there was nothing to paint. One layer, one square.
                    //
                    // Kept at the card's place and size however much of the
                    // banner the sleeve is standing in for: a placeholder is a
                    // card or it is nothing, and blown out to the banner it
                    // would only tint the top third of the screen. Under the
                    // cover, which hides it the moment there is one.
                    Box(
                        modifier = Modifier
                            .layout { measurable, _ ->
                                val card = towardsDock(cardRect(), dockFrame.miniInBox)
                                val sleeve = dockFrame.sleeve
                                val placeable = measurable.measure(
                                    Constraints.fixed(card.width.roundToInt(), card.height.roundToInt()),
                                )
                                layout(0, 0) {
                                    placeable.place(
                                        (card.left - sleeve.left).roundToInt(),
                                        (card.top - sleeve.top).roundToInt(),
                                    )
                                }
                            }
                            .graphicsLayer {
                                // The card's own shrink and nudge, where the
                                // sleeve around it is not taking them.
                                val share = bannerShare()
                                val idle = cardScale()
                                val t = dockT()
                                val scale = if (t >= 1f) {
                                    idle / (idle + (1f - idle) * share)
                                } else {
                                    (1f + (idle - 1f) * t) / (1f + (idle + (1f - idle) * share - 1f) * t)
                                }
                                scaleX = scale
                                scaleY = scale
                                translationX = swipeSettle.value * (1f - p()) * share * t
                                // Its own corners only once it is smaller than
                                // the sleeve; as the whole card it takes the
                                // sleeve's clip, as it always did.
                                shape = TileShape
                                clip = share > 0f
                            }
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!art.loaded && !canvasRendered) {
                            Icon(
                                imageVector = BitChordIcons.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        val scale = 1f - 0.5f * p()
                                        scaleX = scale
                                        scaleY = scale
                                    },
                            )
                        }
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
                        //
                        // Asked for at the source's own size rather than the
                        // sleeve's: the banner is taller than the card is
                        // wide, and the one bitmap has to serve both shapes
                        // with nothing to upscale between them.
                        model = art.request,
                        contentDescription = null,
                        // Video thumbnails are 16:9; letterboxing them inside
                        // the square sleeve looks like a broken frame.
                        contentScale = ContentScale.Crop,
                        onState = art::onState,
                        // TextureView-backed canvas frames can arrive
                        // before Coil has decoded the sleeve. Alpha alone
                        // doesn't hide this layer for that window: a
                        // TextureView composites through its own hardware
                        // layer, and on some devices that layer wins the
                        // stacking order against a sibling Compose layer
                        // even when that layer's alpha is zero — so the
                        // still image's empty placeholder still shows
                        // through, above a perfectly healthy animated
                        // cover. Skipping the draw call outright leaves
                        // nothing there to composite, in the wrong order
                        // or otherwise; the request stays mounted so
                        // loading still finishes in the background and
                        // [PlayerArtwork.loaded] still flips the moment it does.
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent { if (art.loaded || !canvasRendered) drawContent() },
                    )

                    // Where the clip plays when it can't have the banner:
                    // inside the same clip as the still art, taking the
                    // sleeve's corners, shadow and paused shrink for free.
                    if (!heroMode && canvas != null) {
                        WhileShown({ !collapsePastHalf }) {
                            CanvasArtworkPlayer(
                                canvas = canvas,
                                isPlaying = isPlaying,
                                // The panels first: a collapse the tap asked
                                // for is paused from the tap, and then never
                                // asks after its first frame of movement.
                                pausedForTransition = lyricsOpen || queueOpen || queueDragging ||
                                    collapseStarted || dockMoving,
                                onRenderedChanged = { canvasRendered = it },
                                onFrameCaptured = {
                                    if (!tabletArtworkBackdrop && !lyricsOpen && !queueOpen) {
                                        canvasFrame = it
                                    }
                                },
                                refreshFrameEveryMs = if (
                                    tabletArtworkBackdrop || lyricsOpen || queueOpen || dockMoving
                                ) null else meshRefreshMs,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // The card's slot: where the sleeve sits as a card, whether or
                // not it is being drawn as one. Holds the stats line and takes
                // the gestures, both of which belong to the card's place even
                // while the artwork runs full bleed around it.
                Box(
                    modifier = Modifier
                        // The lambda overload deliberately: the Dp one reads
                        // its arguments at composition, so an animated offset
                        // recomposes and re-measures this Box once per frame.
                        // Read at placement instead, the same movement costs a
                        // placement pass.
                        .offset { IntOffset(artStart().roundToPx(), artTop().roundToPx()) }
                        // What `.size(artSize)` measured, asked at measure
                        // time instead of composition.
                        .layout { measurable, constraints ->
                            val side = artSize().roundToPx()
                            val placeable = measurable.measure(
                                constraints.constrain(Constraints.fixed(side, side)),
                            )
                            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                        }
                        // Where the dismiss band starts. Read here, above the
                        // paused shrink below, so the band covers the sleeve's
                        // slot rather than the 86% of it that is drawn while
                        // paused — the ring of backdrop the shrink opens up is
                        // still the artwork as far as a finger is concerned, and
                        // a band that breathed with the shrink would hand it
                        // back and forth on every play and pause.
                        .onGloballyPositioned { dismissBandTop = it.boundsInRoot().top }
                        .graphicsLayer {
                            // The paused shrink and the swipe nudge only make
                            // sense on the full sleeve.
                            val idle = cardScale()
                            scaleX = idle
                            scaleY = idle
                            translationX = swipeSettle.value * (1f - p())
                        }
                        // Collapsed, the sleeve is the way back: tapping the
                        // thumbnail puts the queue or the lyrics away again.
                        .then(
                            if (queueOpen || lyricsOpen) {
                                Modifier.clickable {
                                    queueOpen = false
                                    closeLyrics()
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Measured stats stay on the sleeve's bottom centre. They
                    // fade away with Spotify's lower control deck rather than
                    // following the compact credits to the bottom edge.
                    WhileShown({ !collapsePastHalf }) {
                        SleeveNerdStats(
                            song = song,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .graphicsLayer {
                                    alpha = (1f - p() * 2f) * spotifyChromeAlpha
                                },
                        )
                    }
                }

                // Sits in the gap under the sleeve, clear of its rounded
                // corners and shadow — no box, no clip, nothing for the art
                // itself to be cropped by. Just a glyph that fades in with
                // the drag to hint which way a release would skip.
                //
                // Shown under the banner as well as under the card, and it is
                // the only feedback the drag has there: a card can slide with
                // the finger, but a full-bleed image sliding would open a strip
                // of bare backdrop down one edge of the screen. It lands where
                // the banner has all but dissolved, so it reads against the
                // backdrop rather than against the artwork.
                if (swipeHintShown) {
                    val showNext = swipeHintNext
                    val enabled = if (showNext) hasNext else hasPrevious
                    val hintAlpha = if (enabled) 0.85f else 0.3f
                    Icon(
                        imageVector = if (showNext) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset {
                                IntOffset(0, (artTop() + artSize() + (ART_TITLE_GAP - 16.dp) / 2).roundToPx())
                            }
                            .size(16.dp)
                            .graphicsLayer {
                                alpha = (abs(swipeSettle.value) / swipeThreshold)
                                    .coerceIn(0f, 1f) * (1f - p()) * hintAlpha
                            },
                    )
                }

                // ---- Title + menu ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Collapsed, this row shares the header with the sleeve
                        // rather than sitting under it, and the two are not the
                        // same height — centring the credits in the taller of
                        // the two boxes left them riding low against the
                        // artwork they belong to. Only as it collapses: opened
                        // out, the row is below the sleeve and owns its band.
                        // Read the animated value during placement. The Dp
                        // overload would recompose and remeasure this whole
                        // weighted player region on every animation frame.
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (
                                    currentTitleTop() -
                                        lerp(0.dp, (HEADER_HEIGHT - THUMB_SIZE) / 2, p())
                                ).roundToPx(),
                            )
                        }
                        // What `.padding(start = titleStart)` laid out, asked at
                        // measure time instead of composition.
                        .layout { measurable, constraints ->
                            val start = titleStart().roundToPx()
                            val placeable = measurable.measure(constraints.offset(horizontal = -start))
                            layout(
                                constraints.constrainWidth(placeable.width + start),
                                constraints.constrainHeight(placeable.height),
                            ) { placeable.placeRelative(start, 0) }
                        }
                        .height(HEADER_HEIGHT)
                        // Where the dismiss band ends — see its top on the
                        // artwork above. Taken from the row rather than added up
                        // from the sleeve so the gap between the two is inside
                        // the band as well: it is a gap in one block, not a seam
                        // between two, and a finger should not be able to find it.
                        .onGloballyPositioned { dismissBandBottom = it.boundsInRoot().bottom },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            // Scaling the already measured type is a draw-only
                            // operation. Animating fontSize forced both marquee
                            // texts through shaping and measurement every frame.
                            .graphicsLayer {
                                val scale = 1f - 0.2f * p()
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            },
                    ) {
                        // Only the title's own overflow gates the artist's stagger
                        // below — an artist line that's long on its own has no
                        // reason to wait on a title that already fits.
                        var titleOverflowing by remember { mutableStateOf(false) }
                        // Only while these credits are the screen. Collapsed into
                        // a header over the queue or the lyrics they are a label
                        // on a list, and a label that crawls pulls the eye off
                        // whatever is being read below it.
                        //
                        // Asked inside the crossfade's own content, so the
                        // switch recomposes the two lines and not the box
                        // round the whole sleeve — and asked of the panels
                        // first, so a collapse a tap asked for stops the crawl
                        // with the tap, instead of a frame into the movement.
                        val scrolls = {
                            !(lyricsOpen || queueOpen || queueDragging) && !collapsePastSettling
                        }
                        // Targeted on the words rather than the videoId: two
                        // cuts that share a title cross-fade into the exact
                        // same text, which is nothing at all and leaves the
                        // marquee where it was; a cut that renames the song
                        // ("… (Live)") dissolves into the new one instead of
                        // snapping while the rest of the switch moves around
                        // it.
                        Crossfade(
                            targetState = song.title to song.artist,
                            animationSpec = tween(durationMillis = 300),
                            label = "playerCredits",
                        ) {
                            val scrolling = scrolls()
                            Column {
                                MarqueeText(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 20.sp,
                                    ),
                                    color = Color.White,
                                    enabled = scrolling,
                                    leading = if (song.isExplicit == true) {
                                        { ExplicitBadge(color = Color.White) }
                                    } else {
                                        null
                                    },
                                    onOverflowChange = { titleOverflowing = it },
                                    // Only the tracks YouTube hands us a browse id for
                                    // lead anywhere; the rest stay plain text.
                                    modifier = Modifier.opensPage(song.albumId, onOpenAlbum),
                                )
                                ArtistCreditsMarquee(
                                    text = song.artist,
                                    credits = remember(song.artist, song.artists, song.artistId) {
                                        artistCredits(song.artist, song.artists, song.artistId)
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.W500,
                                        fontSize = 20.sp,
                                    ),
                                    color = Color.White.copy(alpha = 0.55f),
                                    enabled = scrolling,
                                    // A title that's also scrolling gets to go first —
                                    // starting together reads as clutter, so the artist
                                    // waits a beat before it joins in.
                                    startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                                    onOpenArtist = onOpenArtist,
                                )
                            }
                        }
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
                            contentDescription = stringResource(
                                if (liked) Res.string.remove_from_liked else Res.string.like,
                            ),
                            onClick = onToggleLike,
                            active = liked,
                            haptic = if (liked) Haptic.ToggleOff else Haptic.ToggleOn,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    // While the lyrics are up the menu glyph becomes the share
                    // entry: the same pick mode a long-press on a line opens,
                    // only on phones where a card can actually be made. While
                    // picking it is the way back out again — the bar below
                    // already holds Share.
                    val lyricsShareGlyphVisible = lyricsOpen && lyricsShareEnabled &&
                        !lyrics.isNullOrEmpty()
                    CircleGlyph(
                        icon = when {
                            showRevertCue -> Icons.AutoMirrored.Rounded.Undo
                            lyricsShareGlyphVisible && lyricPicker.picking -> Icons.Rounded.Close
                            lyricsShareGlyphVisible -> Icons.Rounded.IosShare
                            else -> Icons.Rounded.MoreHoriz
                        },
                        contentDescription = stringResource(
                            when {
                                showRevertCue -> Res.string.more
                                lyricsShareGlyphVisible && lyricPicker.picking -> Res.string.cancel
                                lyricsShareGlyphVisible -> Res.string.share
                                else -> Res.string.more
                            },
                        ),
                        onClick = when {
                            showRevertCue -> onOpenMenu
                            lyricsShareGlyphVisible && lyricPicker.picking -> lyricPicker.cancel
                            lyricsShareGlyphVisible -> lyricPicker.beginPicking
                            else -> onOpenMenu
                        },
                        haptic = if (lyricsShareGlyphVisible && !showRevertCue) {
                            Haptic.Select
                        } else {
                            Haptic.Tap
                        },
                    )
                }

                // [lyricsOpen] asked first: a close has already hidden the
                // panel, and doesn't then listen for the sleeve leaving the
                // header — which is its first frame of moving.
                val lyricsPanelVisible = lyricsOpen && collapseDone
                if (lyricsPanelVisible || playerPrewarmStage >= 2) {
                        LyricsTranslationMotion(
                            trigger = lyricsTranslation.transition,
                            reduceMotion = lyricsTranslation.reduceMotion,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = HEADER_HEIGHT)
                                // Keep the prepared list measured but completely
                                // outside hit-testing/drawing until it is needed.
                                .offset {
                                    if (lyricsPanelVisible) IntOffset.Zero else IntOffset(100_000, 0)
                                }
                                // Arrives after the sleeve has finished collapsing
                                // into the header rather than during — see
                                // [collapseDone]. Fading lyrics in over a sleeve
                                // still mid-collapse doubled the same movement in
                                // two places on screen at once, and composing them
                                // there was what made the collapse stutter.
                                .graphicsLayer { alpha = if (lyricsPanelVisible) panelFade.value else 0f },
                        ) { particleProgress ->
                            LyricsPanel(
                                lines = lyrics.orEmpty(),
                                subLines = lyricsTranslation.subLines,
                                trackKey = song.videoId,
                                playhead = lyricsPlayhead,
                                looking = !lyricsUnavailable,
                                isPlaying = isPlaying && position.advancing,
                                active = lyricsPanelVisible,
                                onSeekToLine = seekToLyric,
                                controlsOpen = lyricsControlsOpen,
                                onRevealControls = { lyricsControlsOpen = true },
                                onHideControls = { lyricsControlsOpen = false },
                                translationProgress = particleProgress,
                                onScrollingChange = { lyricsScrolling = it },
                                canPick = lyricsShareEnabled,
                                picking = lyricPicker.picking,
                                picked = lyricPicker.picks,
                                onPickLine = lyricPicker.pick,
                                onTogglePick = lyricPicker.toggle,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                    // Floated over the foot of the lyrics rather than placed in
                    // the controls below them. In the controls it was a row of
                    // layout like any other, and the bottom block is measured at
                    // its natural height — so the button's 34dp came straight
                    // off the panel above it and the lyrics lost a line. Drawn
                    // here it costs the panel nothing and still reads as sitting
                    // on top of the half player, because that is where it is.
                    //
                    // Arrives and leaves on the controls' own fade: the panel is
                    // for reading, and a control parked over the words when
                    // nobody asked for the controls is one more thing between
                    // the reader and them.
                    val translateShown = lyricsPanelVisible && lyricsControlsOpen
                    val translateFade by animateFloatAsState(
                        targetValue = if (translateShown) 1f else 0f,
                        animationSpec = tween(if (translateShown) 220 else 160),
                        label = "translateFade",
                    )
                    if (translateFade > 0.01f && !lyricPicker.picking) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .graphicsLayer { alpha = translateFade },
                        ) {
                            RomanizationToggleButton(
                                state = lyricsTranslation.romanizationState,
                                showingRomanization = lyricsTranslation.showingRomanization,
                                enabled = translateShown && !lyrics.isNullOrEmpty(),
                                onClick = lyricsTranslation.toggleRomanization,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .graphicsLayer { alpha = translateFade },
                        ) {
                            TranslationToggleButton(
                                state = lyricsTranslation.translationState,
                                showingTranslation = lyricsTranslation.showingTranslation,
                                // Not tappable on the way out: a disc at 20%
                                // opacity is on its way to gone, not a target.
                                enabled = translateShown && !lyrics.isNullOrEmpty(),
                                onClick = lyricsTranslation.toggleTranslation,
                            )
                        }
                    }
                    // The bar arrives and leaves without a transition of its own:
                    // it is an instruction over the words, and one that has to
                    // be legible the instant it is on. Anything that animated
                    // would be over the reader's first pick anyway.
                    if (lyricPicker.picking) {
                        LyricsPickBar(
                            shareEnabled = lyricPicker.picks.isNotEmpty() &&
                                !lyricPicker.overBudget,
                            onCancel = lyricPicker.cancel,
                            onShare = lyricPicker.share,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                // Toggles and the queue arrive after the sleeve has finished
                // travelling, and leave before it starts coming back.
                // Held back until the sleeve has settled, exactly as the lyric
                // sheet above is — except while a finger is actually dragging
                // the queue in. A drag is direct manipulation: the queue has to
                // be under the finger the whole way for the gesture to mean
                // anything, and the person doing it is setting the pace, so
                // there is no animation of ours for the composition to trip up.
                //
                // Gone with [queueOpen], as the lyrics are with theirs, rather
                // than a frame later when the sleeve first moves: listening
                // for that frame was a recomposition on it.
                val queuePanelVisible = !lyricsOpen &&
                    (queueDragging || (queueOpen && queueShowing && collapseDone))
                if (queuePanelVisible || playerPrewarmStage >= 3) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT)
                            .offset {
                                if (queuePanelVisible) IntOffset.Zero else IntOffset(100_000, 0)
                            }
                            .graphicsLayer {
                                alpha = if (!queuePanelVisible) {
                                    0f
                                } else if (queueDragging) {
                                    ((queueSlide.floatValue - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                } else {
                                    panelFade.value
                                }
                                translationY = if (queuePanelVisible) {
                                    (1f - queueSlide.floatValue) * 26.dp.toPx()
                                } else {
                                    0f
                                }
                            },
                    ) {
                        InlineQueue(
                            queue = queue,
                            currentIndex = queueIndex,
                            autoplayEnabled = autoplayEnabled,
                            controlsLocked = controlsLocked,
                            onJumpTo = onJumpTo,
                            onRemove = onRemoveFromQueue,
                            onMove = onMoveInQueue,
                            onClear = onClearQueue,
                            onScrollingChange = { queueScrolling = it },
                            onDragActiveChange = onQueueDragActiveChange,
                            collapsePlayerOnScroll = true,
                            onRevealPlayer = { queueControlsOpen = true },
                            onHidePlayer = { queueControlsOpen = false },
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
            SlidingPlayerDeck(
                // A pick owns the screen: the transport is hidden for as long
                // as it is on, and a scroll that would reveal it is refused
                // (see [LyricsPanel]'s bottom-half tap), so nothing brings the
                // player back under somebody choosing lines.
                visible = !lyricPicker.picking &&
                    (!lyricsOpen || lyricsControlsOpen) &&
                    (!queueOpen || queueControlsOpen) &&
                    (!spotifyCanvasPresentation || spotifyCanvasControlsOpen || mixing),
                reveal = playerDeckReveal,
                // Back to the main player from lyrics or the queue with the
                // deck stood down: the sleeve is about to come back out, so
                // the deck has to hold its full height from the first frame.
                // Asked when the deck is shown rather than read here — [p]
                // read in this scope recomposes the whole player with it.
                fadeIn = { !lyricsOpen && !queueOpen && p() > 0f },
            ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.onSizeChanged { size ->
                    if (spotifyCanvasPresentation) {
                        val measuredHeight = with(density) { size.height.toDp() }
                        if (measuredHeight != spotifyCanvasDeckHeight) {
                            spotifyCanvasDeckHeight = measuredHeight
                        }
                    }
                },
            ) {
            Column(
                modifier = Modifier
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Current lyric, one line, directly above the scrubber. It stays in
            // the layout — and stays fully visible — whether or not the queue
            // is open: dropping it would shorten this block and the controls
            // under it would jump the moment the queue started sliding in, and
            // fading it away behind the queue left this the one place in the
            // player where the current line simply vanished.
            //
            // Switched off in Settings it goes entirely, rather than sitting
            // there saying no lyrics were found: none were looked for. It is
            // accompanied by a dedicated lyrics button in the bottom row. Its
            // one-line slot remains, invisibly, so opening lyrics cannot grow
            // the half-player merely to make room for the source label.
            if (!lyricsOpen && syncedLyricsEnabled) {
                CurrentLyricStrip(
                    lines = lyricsTranslation.displayedLyrics,
                    trackKey = song.videoId,
                    playhead = lyricsPlayhead,
                    isPlaying = isPlaying && position.advancing,
                    durationMs = durationMs,
                    lyricsUnavailable = lyricsUnavailable,
                    loadingText = lyricsLoadingText,
                    // Still visible over the queue, so still a valid way in:
                    // opens the same full lyrics panel it always has, closing
                    // the queue behind it the same way the "Up next" glyph
                    // closes lyrics behind the queue.
                    onClick = openLyrics,
                )
            }
            if (!lyricsOpen && !syncedLyricsEnabled) {
                Text(
                    text = "\u00A0",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 6.dp)
                        .padding(vertical = 4.dp),
                )
            }
            if (lyricsOpen) {
                LyricsStatusWithChange(
                    status = lyricsTranslation.status,
                    onStatusClick = { showLyricsProviders = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 6.dp)
                        .padding(vertical = 4.dp),
                )
            }
            val transitionWindow by PlayerSettings.smartTransitionWindow.collectAsStateWithLifecycle()
            PlayerScrubber(
                shown = shown,
                durationMs = durationMs,
                loading = versionSwitching,
                mixing = mixing && !scrub.scrubbing,
                // Hidden while scrubbing: the planner is still describing
                // where the transition *would* be, and a marker sitting under
                // a finger that is moving the playhead invites reading it as
                // a drag target.
                transitionWindow = transitionWindow
                    ?.takeIf { !scrub.scrubbing && it.end > it.start }
                    ?.let { it.start..it.end },
                onScrub = onScrub,
                onScrubFinished = onScrubFinished,
            ) {
                PlaybackQualityLabel(
                    song = song,
                    isLoading = isLoading,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                )
            }

            // The transport rides midway between the two blocks it separates:
            // the scrubber above it, and the volume bar and toggle row below,
            // which sit close enough together to read as one. Both of its own
            // gaps take half the spread, so on a tall screen it holds the
            // centre rather than drifting up under the seek bar.
            Spacer(Modifier.height(8.dp + controlSpread / 2))

            TransportRow(
                isPlaying = isPlaying,
                isLoading = isLoading || audioVersionSwitching,
                previousEnabled = !controlsLocked &&
                    (hasPrevious || pastRestartPoint),
                nextEnabled = !controlsLocked && hasNext,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
            )

            // Keep the volume slot's full footprint when its contents are
            // hidden. Removing the slot itself shortened the controls by 50dp
            // and moved every control below it. A display preference should not
            // change the half-player's geometry.
            Spacer(Modifier.height(12.dp + controlSpread / 2))

            if (!hideVolumeBar) {
                VolumeRow(
                    value = { volume.level.value },
                    onValueChange = onVolumeChange,
                    onValueChangeFinished = onVolumeChangeFinished,
                )
            } else {
                Spacer(Modifier.height(VOLUME_ROW_HEIGHT))
            }

            // The volume slider already has 13dp below its drawn track.
            // Balance that invisible inset with the caption gap below the icons.
            Spacer(Modifier.height(6.dp))

            playerActions()
            Spacer(Modifier.height(18.dp))
            }
            }
            }
            }
        }
        playerOverlays()
    }
}

/**
 * The upward half of the sleeve's vertical gesture: dragged up, the artwork
 * block pulls the queue in behind it, following the finger the whole way and
 * settling to whichever end it was nearer on release.
 *
 * Downward is deliberately not ours. The sheet the player sits in is what closes
 * when the sleeve is dragged that way, and it can only read a drag it was
 * allowed to see — so a downward crossing of the touch slop is left entirely
 * alone and this returns having consumed nothing at all.
 *
 * Which of the two it is can only be known at the crossing, which is why the
 * decision is made there rather than at the press. A pointer event reaches a
 * child before its parent, so consuming the very event that crossed the slop is
 * enough to keep the sheet out of an upward drag, and letting that one event
 * through is enough to hand it a downward one — the sheet's own slop detector
 * gives up the moment it sees a change already spoken for.
 *
 * @param travel how far the sleeve has to be dragged for the queue to arrive.
 * @param slide the 0..1 the player's whole layout reads off.
 * @param onHold true while the finger owns [slide] and false when it hands it
 *   back; the settling animation is parked in between so the two never write the
 *   same value on alternate frames.
 * @param onSettle the state the release decided on, which that animation then
 *   finishes reaching from wherever the finger left off.
 */
private suspend fun AwaitPointerEventScope.dragQueueIn(
    down: PointerInputChange,
    travel: Float,
    slide: MutableFloatState,
    onHold: (Boolean) -> Unit,
    onSettle: (Boolean) -> Unit,
) {
    // A block with nowhere to travel — a player not yet measured — would divide
    // by nothing and snap the queue open on the first pixel of movement.
    if (travel < 1f) return

    var pulled = 0f
    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
        if (overSlop < 0f) {
            pulled = -overSlop
            change.consume()
        }
    }
    if (drag == null || pulled <= 0f) return

    onHold(true)
    val velocity = VelocityTracker()
    velocity.addPointerInputChange(drag)
    slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
    verticalDrag(drag.id) { change ->
        velocity.addPointerInputChange(change)
        pulled -= change.positionChange().y
        slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
        change.consume()
    }

    // A flick decides on its own — it says "open" without asking the finger to
    // travel at all. Anything slower goes to whichever end it got nearer to.
    val flick = -velocity.calculateVelocity().y
    val open = when {
        flick >= QUEUE_FLICK_VELOCITY -> true
        flick <= -QUEUE_FLICK_VELOCITY -> false
        else -> slide.floatValue >= QUEUE_CARRY_FRACTION
    }
    onHold(false)
    onSettle(open)
}
