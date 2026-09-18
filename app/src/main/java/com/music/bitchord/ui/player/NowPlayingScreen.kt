package com.music.bitchord.ui.player

import com.music.bitchord.R
import com.music.bitchord.ui.components.ExplicitSongTitle

import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.music.bitchord.ui.theme.SystemBarIcons
import com.music.bitchord.ui.rememberIsForeground
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.optimizedHazeEffect
import com.music.bitchord.ui.components.AudioPipelineDialog
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.lyrics.CharGrowth
import com.music.bitchord.data.lyrics.Genius
import com.music.bitchord.data.lyrics.GrowingWord
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.lyrics.translationLanguageName
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.playback.BACK_RESTARTS_AFTER_MS
import com.music.bitchord.playback.autoplaySectionStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Collapsed-header geometry, shared by the layout and its animation. */
/**
 * Comfortably over the sleeve's drawn size on a phone, without wasting bytes.
 *
 * A rung on the app-wide ladder rather than a number of the player's own, so a
 * large home-screen widget asks for the same copy — see [PLAYER_ART_PX].
 */
private const val ART_PX = PLAYER_ART_PX

/**
 * How many further goes a cover that failed to load gets.
 *
 * Small on purpose. This is here for the connection that drops for a moment or
 * the request that loses a race with the app coming back to the foreground, not
 * for a track whose artwork has genuinely gone: past a few tries the answer is
 * not going to change, and the placeholder tile is the honest thing to draw.
 */
private const val ART_RETRIES = 3

/** How long to leave it before trying a failed cover again. */
private const val ART_RETRY_DELAY_MS = 1_500L

/**
 * How long a canvas lookup waits for the track's album name before giving up
 * on it. Long enough to cover the album lookup on a normal connection, short
 * enough not to be noticed on a track that has no album to find.
 */
private const val ALBUM_SETTLE_MS = 700L

/** Long enough for the post-upgrade rollback cue to be noticed without lingering. */
private const val REVERT_CUE_MS = 2_600L

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
private val PLAYER_GUTTER = 30.dp
/**
 * How wide the player's content is ever allowed to get. A sleeve and a volume
 * slider stretched right across a tablet aren't a bigger player, just a coarser
 * one; past this the column stops growing and centres itself instead. Phones
 * are narrower than this, so for them it does nothing.
 */
private val PLAYER_MAX_WIDTH = 560.dp
/**
 * The pane's share of a window wide enough to dock in, and the bounds it takes
 * that share within.
 *
 * A fraction alone hands a 13in screen half a metre of player. The ceiling is a
 * phone's width because that is the shape the player was drawn for and the shape
 * it looks right in — a square sleeve, one line of credits, a row of oversized
 * glyphs. Widened past that the sleeve stops being able to grow with it (it is
 * bounded by the pane's height long before that) and all the extra pane buys is
 * a scrubber and a volume slider stretched thin either side of it, which is a
 * coarser player rather than a bigger one, and a column of feed given up to pay
 * for it. The floor is there because the fraction of the narrowest window that
 * qualifies is thinner than the controls want to be.
 */
private const val DOCKED_PLAYER_FRACTION = 0.42f
private val DOCKED_PLAYER_MIN_WIDTH = 340.dp
private val DOCKED_PLAYER_MAX_WIDTH = 420.dp

/**
 * The narrowest the page is worth leaving while the player stands beside it.
 *
 * About a small phone: below this the shelves stop showing a second card, the
 * track rows lose their artist line to the ellipsis and a two-pane layout is
 * two things done badly instead of one done well.
 */
private val DOCKED_PAGE_MIN_WIDTH = 360.dp

/**
 * The least a window has to offer, now that the player fills it directly
 * rather than sharing it with a page, before splitting the lyrics beside the
 * artwork is worth doing at all: enough that half of it still holds a square
 * sleeve and a line of credits, and the other half a lyric column wide enough
 * to read — not a caption strip squeezed in beside it.
 *
 * Set to the same figure [dockedPlayerAvailable] already treats as "tablet
 * sized" for this app, rather than a number of its own — a large phone turned
 * sideways and a small tablet can land on either side of it, and that line
 * already is where this app draws it.
 */
private val WIDE_LYRICS_PLAYER_MIN_WIDTH = 700.dp

/**
 * The widest the split layout's two columns are allowed to get between them,
 * centred in whatever is left over.
 *
 * Without a cap the lyric column simply takes every pixel past the artwork
 * lane, and on a wide window that is a column of text with its left edge near
 * the middle of the screen and a hand's width of empty backdrop trailing off
 * the right of every line. Lyrics are read down, not across: past a certain
 * measure the extra width is not more room for the words, it is further for
 * the eye to travel back at the end of each one.
 *
 * Split evenly, the figure gives each column a little under
 * [PLAYER_MAX_WIDTH] — so the player half is close to the width it has to
 * itself on a phone, and the lyric half is past the point where a long line
 * stops needing to wrap.
 */
private val WIDE_LYRICS_MAX_WIDTH = 1000.dp

/**
 * How long the split takes to open or close.
 *
 * Matches the 420ms the sleeve already takes to collapse when the phone layout
 * opens its own lyrics, so the two surfaces answer the same gesture at the same
 * pace.
 */
private const val WIDE_SPLIT_MS = 420

/**
 * The artwork's own play/pause/scrub pose in the two wide layouts. Three flat
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
 * The room above a docked player's artwork, in place of the drag handle.
 *
 * The handle is a promise that the player can be pulled away, and a pane it is
 * pinned in cannot be — so what is left is the gap it used to sit in, minus the
 * strip the gesture needed.
 */
private val DOCKED_TOP_PAD = 12.dp
/**
 * How far a tall screen is allowed to push the transport from the blocks either
 * side of it.
 *
 * The spare height has to land somewhere, and above and below the play button is
 * where it reads as room rather than as a hole. Past this it stops reading as one
 * group of controls, so the rest goes back to the artwork block.
 */
private val CONTROL_GAP_SPREAD_MAX = 48.dp
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
 * How long the shuffle glyph ignores further taps after one lands.
 *
 * Toggling shuffle replaces the upcoming stretch of the live queue. A second
 * tap while that command is crossing the session boundary could otherwise ask
 * to undo work that has not landed yet. One tap is all a toggle can usefully
 * mean in that window, so the rest are dropped rather than queued behind it.
 */
private const val SHUFFLE_TAP_WINDOW_MS = 400L
/**
 * The same gate for AutoPlay, held longer because its work is heavier: the
 * toggle crosses to the playback service, tears down the in-flight suggestion
 * load, and then either strips AutoPlay's tracks out of the queue or goes back
 * to the network for a fresh set of them.
 */
private const val AUTOPLAY_TAP_WINDOW_MS = 700L

/**
 * Whether the player is ever narrow enough in this window to run artwork edge to
 * edge — the gate on both the motion-artwork banner and
 * [AppSettings.fullBleedArtwork]. Public so the settings sheet can leave the
 * switch out entirely where it would do nothing.
 *
 * Two ways to qualify. A window narrow enough that the player fills it is one:
 * edge to edge there means the artwork *is* the screen, which is the whole idea.
 * A window wide enough to dock the player is the other, and for the same reason
 * rather than in spite of it — the pane is a phone's width by construction (see
 * [dockedPlayerWidth]), so edge to edge inside it reads exactly as it does on a
 * phone. Only the band between the two has nothing to offer: too wide for the
 * player to fill, too narrow to stand something beside it.
 */
fun fullBleedArtworkAvailable(windowWidth: Dp): Boolean =
    playerFillsWindow(windowWidth) || dockedPlayerAvailable(windowWidth)

/**
 * Whether a player given the whole of a window this wide is still narrow enough
 * to run its artwork edge to edge.
 *
 * The player's own width is the question, always — this is just the form it takes
 * when the player *is* the window, which is the only time the window's width is
 * an answer to it. A docked pane has its own, much smaller width and does not go
 * through here.
 */
private fun playerFillsWindow(windowWidth: Dp): Boolean =
    windowWidth <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2

/**
 * Whether [windowWidth] is enough to keep the player open beside the page rather
 * than raising it over one: the least the page can live in and the least the
 * player can live in, side by side. Stated as the sum of the two minimums rather
 * than as a number of its own, so it cannot drift out of step with either.
 *
 * [windowWidth] is the width of the *window*, and it has to be measured rather
 * than read off `Configuration.screenWidthDp` — in a freeform or desktop window
 * that can report the display instead of the window, and it lands a beat late
 * when the window is dragged. Deciding a two-pane split from a width the app
 * does not have splits it at the wrong moment and in the wrong place.
 *
 * Public because it is not only the player's business: the page it stands next
 * to loses the mini player, gets its bottom inset back, and stops being able to
 * raise the sheet at all.
 */
fun dockedPlayerAvailable(windowWidth: Dp): Boolean =
    windowWidth >= DOCKED_PAGE_MIN_WIDTH + DOCKED_PLAYER_MIN_WIDTH

/**
 * Whether the window itself has room to run the lyrics beside the artwork,
 * standing rather than behind the toggle a narrower one is stuck with — the
 * tablet-landscape layout: a square sleeve, the credits and the transport in
 * one column, the full lyric sheet in another.
 *
 * The player fills the whole window at every size now (see `playerDocked` at
 * the call site in MainActivity) — there is no page beside it to leave room
 * for — so the width side of this is only [WIDE_LYRICS_PLAYER_MIN_WIDTH] on
 * its own, unlike [dockedPlayerAvailable]'s sum of two minimums.
 *
 * Width alone isn't enough, though: a tablet held upright can be wider than
 * [WIDE_LYRICS_PLAYER_MIN_WIDTH] in portrait too — an 11" iPad's portrait
 * width alone clears it — and the two-column layout is a landscape shape, not
 * a "wide enough" one. [windowHeight] is what tells the two apart: requiring
 * the window to be wider than it is tall is what keeps this to landscape
 * specifically.
 *
 * Upright, a tablet gets the phone layout, deliberately and with nothing
 * tablet-shaped layered on top. A portrait-specific arrangement was tried and
 * removed: a tall window is the shape the phone player was drawn for, and a
 * second layout standing in for it there was a worse version of a screen that
 * already worked.
 */
fun wideLyricsLayoutAvailable(windowWidth: Dp, windowHeight: Dp): Boolean =
    windowWidth > windowHeight && windowWidth >= WIDE_LYRICS_PLAYER_MIN_WIDTH

/** How wide that pane is. Only meaningful where [dockedPlayerAvailable] is true. */
fun dockedPlayerWidth(windowWidth: Dp): Dp =
    (windowWidth * DOCKED_PLAYER_FRACTION)
        .coerceIn(DOCKED_PLAYER_MIN_WIDTH, DOCKED_PLAYER_MAX_WIDTH)
        // Never at the page's expense. The floor above is what the player wants;
        // this is what it may actually have, and where the two disagree the page
        // wins — [dockedPlayerAvailable] is the promise that they only disagree
        // in windows narrow enough that there is no pane at all.
        .coerceAtMost(windowWidth - DOCKED_PAGE_MIN_WIDTH)

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
 * scaled by each letter's own bloom, so only a properly carried note ever sees
 * the whole of it.
 *
 * The bloom used to be a band of light trailing the sweep's leading edge across
 * every line, which is a lamp being dragged along under the words: a shape that
 * belongs to the highlight rather than to the singing, present on patter and
 * held notes alike. It is now attached to the letters of the held words
 * themselves — see [LyricLine.growingWords][com.music.bitchord.data.lyrics.LyricLine.growingWords]
 * — so a line of quick syllables has no glow at all and a carried note lights
 * up letter by letter, which is where the light was always meant to come from.
 */
private const val GLOW_ALPHA = 0.62f

/**
 * How far the bloom spreads off a letter. Tight, because it is a letter's worth
 * of light now rather than a word's: a wide radius on something this small is
 * a smudge behind the text instead of a glow coming off it.
 */
private val GLOW_RADIUS = 6.dp

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

/**
 * How the answering vocal is drawn: smaller than the lead and a shade behind
 * it, the way Apple Music hangs a backing line under the one it answers.
 *
 * Small enough to be read as a second voice at a glance and no smaller —
 * these are the words of the song, not a caption.
 */
private val BACKING_FONT_SIZE = 23.sp
private val BACKING_LINE_HEIGHT = 29.sp
private const val BACKING_ALPHA = 0.72f

/**
 * How far the sweep's leading edge fades out instead of ending on a cut.
 *
 * A hard boundary is legible as a boundary: the eye reads a bar travelling
 * across the words rather than the words themselves lighting up as they are
 * sung. Feathering it over roughly a character and a half is what turns the
 * cut back into a wavefront.
 */
private val WIPE_FEATHER = 30.dp

/**
 * How far the word being sung lifts off the line.
 *
 * Two pixels, and it has to be about two: enough that the eye catches the
 * words moving under the sweep, little enough that nothing appears to come
 * loose from the line it belongs to.
 */
private val WORD_RISE = 2.dp

/**
 * How much further up a row is opened when it holds a word being animated
 * letter by letter, in multiples of [WORD_RISE].
 *
 * A letter that swells has to be given the room above the line it grew out of
 * or the top of it is shaved off by the band it is drawn in. Covers the lift
 * and the swell together, which is why it is well over the one rise an
 * ordinary word needs.
 */
private const val GROW_HEADROOM = 3f

/**
 * The lane kept clear on the far side of a duet line.
 *
 * Only ever applied to a song that actually has two voices laid out. Without
 * it a long right-hand line reaches all the way back across the panel and the
 * split stops reading as a split at all; with it, each voice keeps its own
 * column even when only one of them is singing.
 */
private val DUET_LANE = 44.dp

/**
 * How tall a break stands while it is playing.
 *
 * Nothing when it is not: an interlude that held its row open all through the
 * verse either side of it left a hole in the list, and the panel scrolled past
 * empty space to get to the next thing sung. It opens as the singing stops and
 * closes again as it comes back, so the list only carries a break while there
 * is one.
 */
private val GAP_ROW_HEIGHT = 40.dp
private val GAP_ROW_SPACING = 16.dp

/**
 * How the stack falls away either side of the line being sung.
 *
 * Indexed by distance from it. Far subtler than a linear ramp: the two lines
 * around the playing one stay legible so you can read ahead and behind, and
 * only past that does the panel let go. The last entry stands for everything
 * further out, which is most of the list.
 */
private val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)
private val LINE_FALLOFF_BLUR = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)

/**
 * The shape of the page the lyrics are going to fill.
 *
 * One entry per line of the song, and one number per row that line wraps to.
 * That wrapping is the whole point: at this size a line of a song is rarely one
 * row, so the rows that wrap run nearly the full column and only the last one
 * of each is short. A ladder of evenly spaced bars of assorted lengths is what
 * a loading list looks like — text is blocks with ragged bottoms.
 */
private val SKELETON_BLOCKS = listOf(
    floatArrayOf(0.97f, 0.54f),
    floatArrayOf(0.92f, 0.99f, 0.41f),
    floatArrayOf(0.68f),
    floatArrayOf(0.95f, 0.73f),
    floatArrayOf(0.89f, 0.96f, 0.37f),
)

/**
 * Set to the panel's own metrics: a bar stands the cap height of the 34sp the
 * lines are drawn in, rows of one line sit a line-height apart, and lines are a
 * row's own padding further apart again than that.
 */
private val SKELETON_BAR = 26.dp
private val SKELETON_LEADING = 15.dp
private val SKELETON_BLOCK_GAP = 35.dp
private const val SKELETON_PERIOD_MS = 1_400

/** What a line reads at while the list is being scrolled by hand. */
private const val BROWSING_ALPHA = 0.8f

/** The playing line sits at 1; the rest sit fractionally back from it. */
private const val INACTIVE_SCALE = 0.98f

/** A line under a finger dips, the way a button does. */
private const val PRESSED_SCALE = 0.96f

/**
 * The break between verses, counted out rather than marked.
 *
 * Sized off the same 34sp the lines are set in, so a break sits in the list at
 * the weight of the words either side of it. [GAP_DOT_REST] is what an unlit
 * dot still shows: enough to say how many are coming, not enough to be read as
 * already counted.
 */
private const val GAP_DOTS = 3
private val GAP_DOT_SIZE = 13.dp
private val GAP_DOT_GAP = 5.dp
private const val GAP_DOT_REST = 0.25f
private const val GAP_REST_SCALE = 0.76f

/** How long the panel takes to settle on a new line, and how far ahead it starts. */
private const val SCROLL_LEAD_MIN_MS = 350L
private const val SCROLL_LEAD_MAX_MS = 500L

/**
 * The curve every handover runs on: away quickly, in slowly and softly.
 *
 * One curve for the lot — dimming, blurring, scaling and the scroll — so a
 * line handing over reads as a single movement rather than four that happen to
 * start together.
 */
private val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LYRIC_SETTLE_MS = 400

/**
 * How the rows fan out as the panel moves between lines.
 *
 * They do not travel as a block. Each row after the one being scrolled to sets
 * off slightly later than the row before it, up to a few rows back, so the
 * spacing opens as the panel leaves and closes as it arrives. A block of text
 * sliding rigidly is a list being scrolled; the same lines arriving one behind
 * another is the panel handing over.
 *
 * Deliberately under half of what the renderer this came from uses. Its lines
 * carry the whole scroll themselves, so a long delay only means arriving late;
 * here the list has already moved underneath them, and the same delay reads as
 * the rows being dragged rather than following.
 */
private const val STAGGER_STEPS = 3
private const val STAGGER_FRACTION = 0.06f

/** One handover: how far the panel is going, and how long it is taking. */
private class ScrollRun(val id: Int, val delta: Float, val durationMs: Int) {
    /** The last row to arrive does so this long after the panel sets off. */
    val spanMs: Float get() = durationMs * (1f + STAGGER_FRACTION * STAGGER_STEPS)
}

/**
 * How long before a line lands the panel starts moving to it — and how long
 * the move then takes, which is the same number.
 *
 * It is the run-up: the silence between the last word of the line being sung
 * and the first of the next. Bounded either side, because that silence is a
 * held breath in one song and half a verse in another, and neither the snap
 * nor the drift is what you want to be reading against.
 */
private fun scrollLead(lines: List<LyricLine>, positionMs: Long): Long {
    val current = lines.indexOfLast { it.timeMs <= positionMs }
    // Before the first line's own timestamp there is no current line to
    // measure a run-up from. [indexOfLast] answers -1 there, and the guard
    // below does not catch it: `current + 1` is 0, which is a perfectly real
    // line, so the elvis never fires and `lines[current]` indexes at -1.
    //
    // Only reachable while the playhead is genuinely before the first lyric —
    // a track paused at 0:00 whose words start a few seconds in, which is
    // every track that opens on an intro.
    if (current < 0) return SCROLL_LEAD_MIN_MS
    val next = lines.getOrNull(current + 1) ?: return SCROLL_LEAD_MIN_MS
    val gap = next.timeMs - lines[current].endMs
    return gap.coerceIn(SCROLL_LEAD_MIN_MS, SCROLL_LEAD_MAX_MS)
}

/**
 * How far a finger has to carry the lyric list before the player below it
 * gets out of the way — or comes back.
 *
 * Roughly a line of body text. Below that a scroll is a nudge to see one more
 * line rather than a decision to go reading, and answering every nudge put the
 * controls in and out on the same gesture.
 */
private val CONTROLS_SCROLL_SLOP = 20.dp

/** How long the player stands under the lyrics untouched before standing down. */
private const val LYRICS_CONTROLS_IDLE_MS = 5_000L

private const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
private const val LYRICS_UNAVAILABLE_FADE_MS = 900
private const val LIGHT_ARTWORK_LUMINANCE_THRESHOLD = 0.45f

private val artworkLuminanceCache = LruCache<String, Float>(20)

@Composable
private fun rememberArtworkLuminance(imageUrl: String?): Float? {
    val context = LocalContext.current
    var luminance by remember(imageUrl) { mutableStateOf<Float?>(null) }

    LaunchedEffect(imageUrl) {
        luminance = null
        if (imageUrl == null) return@LaunchedEffect

        artworkLuminanceCache.get(imageUrl)?.let { cached ->
            luminance = cached
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(imageUrl.artworkAt(ART_PX))
            .size(128)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        if (bitmap != null) {
            val lum = withContext(Dispatchers.Default) {
                bitmap.topAreaLuminance()
            }
            artworkLuminanceCache.put(imageUrl, lum)
            luminance = lum
        } else {
            // Default to dark artwork (0f) so status bar icons stay light if image fails to load
            luminance = 0f
        }
    }
    return luminance
}

private fun Bitmap.topAreaLuminance(): Float {
    val sampleHeight = (height * 0.35f).toInt().coerceIn(1, height)
    val sampleWidth = width.coerceAtLeast(1)
    val pixels = IntArray(sampleWidth * sampleHeight)
    getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)

    var totalLuminance = 0.0
    val count = pixels.size.coerceAtLeast(1)
    for (pixel in pixels) {
        val r = ((pixel shr 16) and 0xFF) / 255.0f
        val g = ((pixel shr 8) and 0xFF) / 255.0f
        val b = (pixel and 0xFF) / 255.0f
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
        totalLuminance += lum
    }
    return (totalLuminance / count).toFloat()
}

private sealed interface LyricsTranslationUiState {
    data object Idle : LyricsTranslationUiState
    data object Loading : LyricsTranslationUiState
    data class Ready(val lines: List<LyricLine>) : LyricsTranslationUiState
    data object SameLanguage : LyricsTranslationUiState
}

private const val TRANSLATION_MOTION_MS = 540
private const val PARTICLES_PER_VOICE = 18

private data class TranslationParticle(
    val anchor: Offset,
    val drift: Offset,
    val radius: Float,
    val delay: Float,
)

/**
 * Apple Music's Now Playing, closely: artwork that shrinks when paused, a
 * hairline scrubber with elapsed / remaining either side, oversized transport
 * glyphs, a volume capsule flanked by speaker icons, and lyrics / AirPlay /
 * queue along the bottom.
 */
@Composable
fun NowPlayingScreen(
    song: Song,
    /** Who selected this track in the active Listen Together session. */
    playedBy: String? = null,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    /** True only while this current item is the manual catalogue-audio match. */
    isAudioVersion: Boolean,
    /** A catalogue lookup is in progress for this video's manual conversion. */
    audioVersionSwitching: Boolean,
    /** Whether an alternate (video vs audio) version exists for this track. */
    hasAlternateVersion: Boolean = false,
    /** Legacy alias for [hasAlternateVersion]. */
    hasVideoVersion: Boolean = false,
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
    onToggleAudioVersion: () -> Unit,
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
    /** Return to the queue-level page named by the caption above the player. */
    onOpenPlaybackSource: () -> Unit,
    /**
     * Open Listen Together, from the party half of the output capsule.
     *
     * The player does not decide whether it has to get out of the way first:
     * the settings page it opens is drawn *under* a phone's player sheet and
     * *beside* a tablet's docked pane, and only the caller knows which of the
     * two it mounted — see [docked].
     */
    onListenTogether: () -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean,
    lyricsOffsetOpen: Boolean,
    onDismissLyricsOffset: () -> Unit,
    /** The width of the window the player is in — see [fullBleedArtworkAvailable]. */
    windowWidth: Dp,
    /**
     * The window's height, alongside [windowWidth] — needed for exactly one
     * thing: telling a wide portrait tablet apart from a landscape one, in
     * [wideLyricsLayoutAvailable]. Width alone can't; a big tablet's portrait
     * width comfortably clears the same threshold its landscape width does.
     */
    windowHeight: Dp,
    /**
     * Whether the player is a pane the page sits beside rather than a sheet
     * raised over it — see [dockedPlayerAvailable].
     *
     * There is no sheet under a docked player to pull away, so the handle goes
     * and with it the strip of dead space that existed to pass drags down to one.
     * The artwork is unaffected: the pane is a phone's width, so it runs the
     * cover edge to edge exactly as a phone does — see [fullBleedArtworkAvailable].
     */
    docked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()

    // Keep the header caption and the system glyphs on the same contrast
    // decision. The caption sits over the same upper part of the cover as the
    // status bar when this is a phone-sized player.
    val artLuminance = rememberArtworkLuminance(song.thumbnailUrl)
    val isLightArtwork = artLuminance?.let { it > LIGHT_ARTWORK_LUMINANCE_THRESHOLD } ?: false

    // A docked pane sits beside the page rather than covering the screen, so
    // the status bar it's under belongs to the page, not this artwork — only
    // the full-screen sheet gets to repaint it.
    if (!docked) {
        SystemBarIcons(dark = isLightArtwork)
    }

    // Kept local to the player: a modal player is not in the page's Haze
    // source tree, so it needs its own source for the same frosted material as
    // the bottom navigation pill.
    val playerHaze = remember { HazeState() }
    var showAudioPipeline by remember { mutableStateOf(false) }
    var showAudioOutput by remember { mutableStateOf(false) }
    // Gated on the Bluetooth permission the first time — see [rememberOutputPicker].
    val openAudioOutput = rememberOutputPicker { showAudioOutput = true }

    val syncedLyricsEnabled by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsOffsetMs by AppSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val lyricsPositionMs = adjustedLyricsPosition(positionMs, lyricsOffsetMs)
    val seekToLyric: (Long) -> Unit = { lineTimeMs ->
        onSeek(adjustedLyricsSeekTarget(lineTimeMs, lyricsOffsetMs))
    }
    val hideVolumeBar by AppSettings.hideVolumeBar.collectAsStateWithLifecycle()

    // Animated cover art: the looping video some labels publish alongside a
    // release, laid over the sleeve. A miss is the normal answer — see
    // CanvasRepository, which is also where the "is this actually the right
    // track" check lives.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val meteredConnection by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    // The switch turns the feature off outright; this is the narrower "not
    // over cellular" case — see [AppSettings.canvasOverCellular] for why a
    // clip's own loop makes that worth guarding separately from a still image.
    val canvasAllowedNow = canvasEnabled && (meteredConnection != true || canvasOverCellular)
    var canvas by remember(song.videoId) { mutableStateOf<CanvasArtwork?>(null) }
    // Whether the clip actually has a frame on screen right now, and one of
    // them — used to blow the sleeve out to the full-bleed hero treatment and
    // to re-tint the backdrop off the clip's own colours rather than the
    // still sleeve's.
    var canvasRendered by remember(song.videoId) { mutableStateOf(false) }
    var canvasFrame by remember(song.videoId) { mutableStateOf<Bitmap?>(null) }
    // How much of the still artwork the clip is covering, reported by the clip
    // itself. Read from a draw scope rather than in composition: it moves every
    // frame of the fade, and the still art it governs is an AsyncImage whose
    // request is rebuilt on each pass and so would not be skipped.
    val canvasCover = remember(song.videoId) { mutableFloatStateOf(0f) }
    // The one thing about it worth recomposing for: whether the clip is opaque
    // enough that the still frame under it can go entirely. Derived, so this
    // flips twice across a fade instead of once per frame of it.
    val stillCovered by remember(song.videoId) {
        derivedStateOf { canvasCover.floatValue > 0.999f }
    }
    // v1.5's backdrop, kept behind a switch — see [AppSettings.legacyMeshGradient].
    val legacyMesh by AppSettings.legacyMeshGradient.collectAsStateWithLifecycle()
    // The backdrop's colours, taken off the artwork's own arrangement rather
    // than quantised out of it — see [ArtworkMesh].
    //
    // Only read for the backdrop that uses it. Each of these keeps a decode and
    // a pixel readback of its own on every track change, and the two answer the
    // same picture in two different ways, so whichever is not on screen is pure
    // cost — the legacy path pays [rememberArtworkColors] instead.
    val artMesh = if (legacyMesh) null else rememberArtworkMesh(song.thumbnailUrl, canvasFrame, ART_PX)
    // Asked of every clip, Spotify's Canvas and every other source alike — see
    // CanvasArtworkPlayer's refreshFrameEveryMs. A clip's own colours move as
    // it plays regardless of who published it, and the backdrop should follow.
    //
    // Often enough that the backdrop moves with the clip rather than catching up
    // with it every few seconds. What keeps that affordable is the size of each
    // read, not the number of them: the frame comes back at `frameCapturePx`
    // rather than full-bleed, and is averaged on a stride off the main thread.
    val meshRefreshMs = MESH_REFRESH_MS
    LaunchedEffect(song.videoId, song.albumName, canvasAllowedNow) {
        if (!canvasAllowedNow) {
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
    var lyricsControlsOpen by remember { mutableStateOf(false) }
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
    val reduceTranslationMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val configuredLocale = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
        ?.takeIf { it.isNotBlank() }
        ?: context.resources.configuration.locales.get(0).toLanguageTag()
    val preferredTranslation by AppSettings.translationLanguage.collectAsStateWithLifecycle()
    // Settings wins where it has been set; blank means follow the app. Only the
    // app-language path is reduced to a base language — a code chosen in
    // Settings is already exactly what the endpoint wants and narrowing it
    // would throw away the script half of zh-TW.
    val translationLanguage = remember(configuredLocale, preferredTranslation) {
        preferredTranslation.ifBlank {
            Locale.forLanguageTag(configuredLocale).language.ifBlank { "en" }
        }
    }
    val translationLanguageName = remember(configuredLocale, translationLanguage) {
        translationLanguageName(translationLanguage, Locale.forLanguageTag(configuredLocale))
    }
    var translationState by remember(song.videoId, translationLanguage, lyrics) {
        mutableStateOf<LyricsTranslationUiState>(LyricsTranslationUiState.Idle)
    }
    var showingTranslation by remember(song.videoId, translationLanguage, lyrics) {
        mutableStateOf(false)
    }
    var translationTransition by remember(song.videoId) { mutableIntStateOf(0) }
    var translationJob by remember(song.videoId, translationLanguage, lyrics) {
        mutableStateOf<Job?>(null)
    }
    DisposableEffect(song.videoId, translationLanguage, lyrics) {
        onDispose { translationJob?.cancel() }
    }
    val displayedLyrics = if (showingTranslation) {
        (translationState as? LyricsTranslationUiState.Ready)?.lines ?: lyrics.orEmpty()
    } else {
        lyrics.orEmpty()
    }
    val lyricsLoadingLines = stringArrayResource(R.array.lyrics_loading_lines)
    val lyricsLoadingText = remember(song.videoId) { lyricsLoadingLines.random() }
    val translationScope = rememberCoroutineScope()
    val toggleTranslation: () -> Unit = toggleTranslation@{
        when (val state = translationState) {
            is LyricsTranslationUiState.Ready -> {
                showingTranslation = !showingTranslation
                translationTransition++
                haptics.play(Haptic.Select)
            }
            LyricsTranslationUiState.Loading -> Unit
            LyricsTranslationUiState.SameLanguage -> {
                haptics.play(Haptic.Tap)
                Toast.makeText(
                    context,
                    context.getString(R.string.lyrics_already_in_language, translationLanguageName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            LyricsTranslationUiState.Idle -> {
                val source = lyrics.orEmpty()
                if (source.isEmpty()) return@toggleTranslation
                haptics.play(Haptic.Tap)
                translationState = LyricsTranslationUiState.Loading
                translationJob?.cancel()
                translationJob = translationScope.launch {
                    when (
                        val result = LyricsTranslation.translate(
                            context = context.applicationContext,
                            trackId = song.videoId,
                            lines = source,
                            targetLanguageTag = translationLanguage,
                        )
                    ) {
                        is LyricsTranslation.Result.Translated -> {
                            translationState = LyricsTranslationUiState.Ready(result.lines)
                            showingTranslation = true
                            translationTransition++
                            haptics.play(Haptic.ToggleOn)
                        }
                        is LyricsTranslation.Result.SameLanguage -> {
                            translationState = LyricsTranslationUiState.SameLanguage
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.lyrics_already_in_language,
                                    translationLanguageName,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        LyricsTranslation.Result.Unavailable -> {
                            translationState = LyricsTranslationUiState.Idle
                            Toast.makeText(
                                context,
                                context.getString(R.string.lyrics_translation_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }
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
    val playerView = LocalView.current
    DisposableEffect(playerView, lyricsOpen) {
        playerView.keepScreenOn = lyricsOpen
        onDispose { playerView.keepScreenOn = false }
    }

    // Back out of the lyrics panel to the player, and only from the player
    // itself out to the mini player.
    //
    // The BackHandler can't do that on its own. The player is a
    // ModalBottomSheet, and from API 33 the sheet puts its own dismiss
    // straight onto the window's OnBackInvokedDispatcher when its layout
    // attaches — at PRIORITY_DEFAULT, which is also where the dialog
    // dispatcher that every BackHandler in here feeds ends up. Equal
    // priority, and the platform picks whichever registered last: the
    // sheet's, every time. So back put the whole player away with the panel
    // still open on top of it.
    //
    // Outranking it while the panel is open is the fix, and only while it is
    // open: shut, the sheet keeps its own back handling and with it the
    // predictive-back shrink, which is the right animation for a gesture
    // that really is dismissing the player. Below 33 there is no window
    // dispatcher to outrank and the BackHandler is already the newest
    // callback on the dialog's, so it wins there unaided.
    // Controls being visible must not insert an extra navigation level. Back
    // always leaves lyrics in one step, whether it starts over the lyrics list
    // or over the half-player at the bottom.
    BackHandler(enabled = lyricsOpen, onBack = closeLyrics)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, lyricsOpen) {
            val callback = if (lyricsOpen) {
                OverlayBack.register(view, closeLyrics)
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    // Back out of the queue to the player. Same problem as lyrics: the sheet's
    // own dispatcher competes at PRIORITY_DEFAULT on API 33+, so we outrank it
    // with an overlay-priority callback while the queue is open. Below 33 the
    // BackHandler alone wins because it registers after the dialog's handler.
    BackHandler(enabled = queueOpen) { queueOpen = false }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, queueOpen) {
            val callback = if (queueOpen) {
                OverlayBack.register(view) { queueOpen = false }
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    BackHandler(enabled = showAudioPipeline) { showAudioPipeline = false }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, showAudioPipeline) {
            val callback = if (showAudioPipeline) {
                OverlayBack.register(view) { showAudioPipeline = false }
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    // Same again for the output drawer, so back puts it away rather than
    // taking the whole player down from under it.
    BackHandler(enabled = showAudioOutput) { showAudioOutput = false }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, showAudioOutput) {
            val callback = if (showAudioOutput) {
                OverlayBack.register(view) { showAudioOutput = false }
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    BackHandler(enabled = lyricsOffsetOpen) { onDismissLyricsOffset() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, lyricsOffsetOpen) {
            val callback = if (lyricsOffsetOpen) {
                OverlayBack.register(view, onDismissLyricsOffset)
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    // 0 = full sleeve, 1 = queue. Everything that moves reads off this.
    //
    // Plain state driven by an animation rather than [animateFloatAsState],
    // because it has two drivers and only one of them is an animation: the
    // toggle at the foot of the player, which travels end to end, and a finger
    // dragging the sleeve upward, which sets it outright. An animation keyed on
    // [queueOpen] cannot be pushed around mid-flight by a drag — and a drag that
    // could only move the *target* would have nothing to show for itself until
    // it was released, then jump from wherever the animation had got to.
    val queueSlide = remember { mutableFloatStateOf(0f) }
    val queueProgress = queueSlide.floatValue
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
    LaunchedEffect(lyricsOpen, lyricsControlsOpen, scrubbing, volumeDragging) {
        if (lyricsOpen && lyricsControlsOpen && !scrubbing && !volumeDragging) {
            delay(LYRICS_CONTROLS_IDLE_MS)
            lyricsControlsOpen = false
        }
    }
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

    // 0 = the ordinary square sleeve, 1 = the artwork as a full-bleed banner.
    // Both states collapse the header, but the banner only ever shows over a
    // settled player: opening the queue or the lyrics hands the sleeve back its
    // card first.
    // How collapsed the sleeve is, whichever surface asked for it.
    //
    // This used to read `if (lyricsOpen) 1f else queueProgress`, which gave the
    // queue a 420ms ease and the lyrics nothing at all: opening them snapped
    // the sleeve to a thumbnail in a single frame while [heroT] — reading off
    // this same value — went on fading the banner out over the full 420. One
    // half of the artwork jumped, the other half glided after it, and the pair
    // read as a stutter rather than as either. One animation, both surfaces.
    val p by animateFloatAsState(
        targetValue = if (lyricsOpen || queueOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sleeveCollapse",
    )

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
    val panelsSettled = p >= 1f
    val panelFade by animateFloatAsState(
        targetValue = if (panelsSettled) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "panelFade",
    )
    val fullBleedArt by AppSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    // Full-bleed is a phone idiom, and a docked pane is a phone's width — so it
    // is asked of the player's own width rather than of the window's. Asking the
    // window is what left the pane with a square sleeve floating in a field of
    // backdrop: the window is wide, but the player in it never is.
    //
    // What the width has to rule out is a player running a foot wider than the
    // column of controls under it — edge to edge meaning "a picture, and
    // separately some controls" rather than "the artwork *is* the player". A pane
    // cannot do that; only the band between phone-width and dockable can, and
    // that is the band [fullBleedArtworkAvailable] excludes.
    //
    // One question for the still cover and the clip both, rather than two that
    // could disagree — and they did, twice over. Dissolving a TextureView's
    // bottom edge needs a RenderEffect, so below API 31 the clip was held in its
    // sleeve while the cover behind it went edge to edge, and the artwork
    // changed shape the moment a clip arrived. In the other direction the clip
    // ignored [fullBleedArt] entirely, so turning the setting off still left a
    // clip running the full screen. CanvasArtworkPlayer masks itself on every
    // API level now, and both layers answer to this.
    val heroMode = fullBleedArt && (docked || playerFillsWindow(windowWidth))
    // Whether there's a still image to blow out — a placeholder tile is a card
    // or it is nothing, and going full-bleed with one would just tint the top
    // third of the screen.
    //
    // Keyed on the artwork rather than on the track, because that is what it
    // actually describes and because only Coil can set it back to true. Two
    // tracks off one album share a cover, so skipping between them leaves the
    // request below byte-identical: the painter keeps the Success it already
    // had and never re-emits, so the `onState` that is the sole writer here
    // never fires again. Keyed on the track this reset to false and stayed
    // there, which pinned the sleeve fully opaque (see the alpha it feeds) on
    // top of an equally opaque banner — the same cover drawn twice, card and
    // full-bleed at once. Keyed on the cover there is nothing to reset: the
    // bitmap really is still loaded, so the state stays true and the two
    // layers go on trading places as they should.
    val artUrl = song.artworkAt(ART_PX)
    var artLoaded by remember(artUrl) { mutableStateOf(false) }
    /**
     * Which go at this cover we are on, and the reason there is more than one.
     *
     * Coil does not retry: a request that fails is over, and the state it leaves
     * behind is the state this screen keeps until the model changes — which,
     * keyed on the cover, means until the next track. One dropped connection at
     * the wrong moment and the player showed its placeholder tile for a song it
     * would have drawn perfectly a second later, with the widget and the
     * notification both showing the cover from cache the whole time.
     *
     * Bounded and spaced, because the usual reason a cover fails is that there
     * is no network at all, and a retry per recomposition — which is what an
     * unremembered request effectively gave — is a spin, not a recovery.
     */
    var artAttempt by remember(artUrl) { mutableIntStateOf(0) }
    /**
     * The one request for this cover, built once.
     *
     * Both the sleeve and the full-bleed banner draw from it, which is what
     * their own comments claim ("one ask, one decode, one bitmap for both") and
     * what building it inline at each of them quietly failed to deliver: Coil
     * compares models to decide whether to start a new load, and two separately
     * built requests are never equal — `ImageRequest` has no `equals`, and
     * neither does the size resolver `.size()` hands it. So each was its own
     * load, and worse, *every recomposition* was another one. The player
     * recomposes at least twice a second off the position tick, and each pass
     * pushed the painter back through Loading before it settled on Success
     * again, which is exactly the [artLoaded] this screen hangs the banner, the
     * sleeve's alpha, its shadow and its placeholder icon on.
     *
     * Remembered on the cover and the attempt, so it changes when the picture
     * changes and when a retry is deliberately asked for, and at no other time.
     */
    val artRequest = remember(artUrl, artAttempt) {
        ImageRequest.Builder(context)
            .data(artUrl)
            .size(ART_PX)
            // What makes a retry a new request as far as Coil's model comparison
            // is concerned. Only from the second go onwards, so the ordinary
            // request stays byte-identical to the one the mesh and the palette
            // make of the same cover and goes on sharing their memory-cache
            // entry. The disk key is unaffected either way.
            .apply { if (artAttempt > 0) memoryCacheKeyExtra("attempt", artAttempt.toString()) }
            .build()
    }
    var artFailed by remember(artUrl) { mutableStateOf(false) }
    LaunchedEffect(artUrl, artFailed) {
        // A track with no artwork at all fails immediately and would fail
        // identically three more times: there is no request to make, so there is
        // nothing a second go could do differently.
        if (artUrl == null || !artFailed || artAttempt >= ART_RETRIES) return@LaunchedEffect
        delay(ART_RETRY_DELAY_MS)
        artFailed = false
        artAttempt++
    }
    // Sticky, unlike [artLoaded]: the banner is the shape of the player rather
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
    var heroSettled by remember { mutableStateOf(false) }
    // Success from the banner's own painter, rather than from the separate
    // sleeve painter. Sharing one ImageRequest lets Coil share its cached
    // bitmap, but it does not make two AsyncImage painters enter Success in
    // the same frame. The sleeve must not hand over to a banner which is still
    // empty just because its own painter finished first.
    var heroArtLoaded by remember(artUrl, artAttempt, heroMode) { mutableStateOf(false) }
    LaunchedEffect(artLoaded, canvasRendered) {
        if (artLoaded || canvasRendered) heroSettled = true
    }
    // The clip that gets the banner, if any. Hoisted because the still frame
    // underneath keys its handover on exactly what is mounted here: both are
    // decided in the same composition pass, so opening the queue or the lyrics —
    // which takes the clip away — brings the still frame back in the very frame
    // the clip goes, instead of a frame later with the sleeve behind it still
    // transparent and no artwork anywhere.
    val heroClip = canvas?.takeIf { heroMode && p < 0.5f }
    // Whether the banner is the presentation at all: full-bleed is on, and there
    // is something to blow out. The collapse is deliberately *not* part of this
    // — see [heroVisible].
    val heroT by animateFloatAsState(
        targetValue = if (
            heroMode && (canvasRendered || artLoaded || heroSettled)
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "heroCanvas",
    )

    /**
     * How much of the banner is actually on screen: its own fade, dissolved by
     * the collapse rather than after it.
     *
     * The collapse used to be a threshold on this animation's *target* — the
     * banner was told to go once [p] passed a half. That chained two 420ms
     * animations end to end when they should have been the same one: for the
     * first half of the collapse the banner sat at full size and full opacity
     * with nothing appearing to move, since the card shrinking behind it is
     * transparent while the banner is up; then the card finished collapsing and
     * a full-screen banner cross-dissolved into a finished thumbnail. Two sizes
     * of the same artwork on screen at once, which is what made every trip in
     * and out of the lyrics look wrong.
     *
     * Multiplied by the collapse instead, the banner goes as the card shrinks:
     * one movement, and the card is fading in the whole way down.
     */
    val heroVisible = heroT * (1f - p)
    // How tall that banner is, worked out down in the layout where the sleeve's
    // own geometry is known. Zero until the first measure, which is fine: there
    // is nothing to show that early either.
    var heroHeight by remember { mutableStateOf(0.dp) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // What sits between the status bar and the artwork: the drag strip in a
    // sheet, plain padding in a pane. Read in three places — the strip itself,
    // the scrim drawn over it and the banner's own height — which all have to
    // agree or the artwork and the credits under it move.
    val topStrip = if (docked) DOCKED_TOP_PAD else DISMISS_STRIP_HEIGHT

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

    // The whole lyrics stack — translation motion, the panel itself and the
    // translate toggle floated over its foot — as one slot the wide layouts
    // below can place wherever their own shape wants it.
    //
    // A slot rather than each wide layout building its own: everything this
    // needs (the translated lines, the transition counter, the toggle's state
    // machine) is declared up here, in the one place that owns it, and handing
    // the layouts a second copy apiece is how a tablet ends up quietly a
    // version behind on lyrics. Built here, they get whatever the phone gets.
    //
    // [controlsOpen] is passed as a constant `true`, not as
    // [lyricsControlsOpen]: that flag exists because a phone hides the player
    // behind the lyrics and needs a tap to bring it back. In these layouts the
    // transport never went anywhere — it is standing beside or below the words
    // the whole time — so there is nothing for a tap to reveal, and leaving the
    // reveal gesture armed would only eat taps meant for the lines themselves.
    val wideLyricsContent: @Composable (Modifier) -> Unit = { lyricsModifier ->
        Box(modifier = lyricsModifier) {
            if (displayedLyrics.isNotEmpty()) {
                LyricsTranslationMotion(
                    trigger = translationTransition,
                    reduceMotion = reduceTranslationMotion,
                    modifier = Modifier.fillMaxSize(),
                ) { particleProgress ->
                    LyricsPanel(
                        lines = displayedLyrics,
                        trackKey = song.videoId,
                        positionMs = lyricsPositionMs,
                        looking = !lyricsUnavailable,
                        isPlaying = isPlaying,
                        onSeekToLine = seekToLyric,
                        controlsOpen = true,
                        onRevealControls = {},
                        onHideControls = {},
                        translationProgress = particleProgress,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Always up here, rather than riding the controls' fade as it
                // does on a phone: the fade is the phone's answer to a control
                // parked over the words when nobody asked for the controls,
                // and these layouts never took the controls away to begin with.
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    TranslationToggleButton(
                        state = translationState,
                        showingTranslation = showingTranslation,
                        enabled = !lyrics.isNullOrEmpty(),
                        onClick = toggleTranslation,
                    )
                }
            } else {
                // Held at a fixed line rather than through
                // [LyricsUnavailableLine] or [LyricsLoadingLine]: those exist
                // for a strip that reads over a scrubber for a few seconds and
                // then gets out of the way, and fading either one out would
                // leave a tablet's whole lyric column blank for as long as the
                // track keeps playing.
                Text(
                    text = if (lyricsUnavailable) {
                        stringResource(R.string.lyrics_not_available)
                    } else {
                        lyricsLoadingText
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    // A window wide enough to run the lyrics beside the artwork — see
    // [wideLyricsLayoutAvailable] — takes on an entirely different shape the
    // moment the lyrics are actually open: two columns instead of the single
    // one everything below this draws, artwork and transport held to a
    // phone-width lane on the left and the lyrics standing in the rest of the
    // window on the right, rather than folded underneath a collapsed sleeve.
    //
    // Gated on [lyricsOpen] rather than on the width alone: with the lyrics
    // shut there is nothing here a phone-width column doesn't already draw
    // exactly as well, just centred in the extra room — which is what this
    // screen already does on its own, being no wider on its content than
    // [PLAYER_MAX_WIDTH]. Splitting it into two columns with nothing to put in
    // the second would be a lane of empty backdrop where the rest of the
    // window used to be.
    //
    // A separate branch rather than something woven into the layout below:
    // reusing this function's collapsing sleeve, hero banner and vertical drag
    // gesture for a shape they were never drawn for would risk all three for a
    // shape none of them apply to.
    val wideSplitAvailable = wideLyricsLayoutAvailable(windowWidth, windowHeight)
    // One number for both directions, and it lives out here rather than inside
    // the layout it drives — which is the whole reason closing is an animation
    // at all. Held inside, it would be destroyed by the very unmount it was
    // meant to animate, so the split could only ever open gracefully and then
    // vanish. Out here it survives [lyricsOpen] going false, and the branch
    // below stays mounted until it has finished running back down to zero.
    //
    // At zero the split's own geometry is the closed player's — content at
    // [PLAYER_MAX_WIDTH], centred — so the hand-back to the layout underneath
    // lands on matching frames instead of on a jump.
    val wideSplitOpen = (lyricsOpen || queueOpen) && wideSplitAvailable
    val wideSplit by animateFloatAsState(
        targetValue = if (wideSplitOpen) 1f else 0f,
        animationSpec = tween(WIDE_SPLIT_MS, easing = FastOutSlowInEasing),
        label = "wideSplit",
    )
    // Which panel the right column is showing. Latched rather than derived, so
    // that closing — where both flags go false together — leaves the panel that
    // was up on screen to fade out as itself, instead of flipping to the other
    // one for the length of the exit.
    var widePanelIsQueue by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsOpen, queueOpen) {
        if (queueOpen) widePanelIsQueue = true else if (lyricsOpen) widePanelIsQueue = false
    }
    if (wideSplitAvailable && (wideSplitOpen || wideSplit > 0.001f)) {
        // Which of four databases the timings came from, or what the translation
        // is currently doing — the one line the phone layout puts directly above
        // its scrubber while the lyrics are open.
        //
        // Computed here, from the same state and with the same branches as the
        // phone's own copy a few hundred lines below, and handed to the wide
        // layouts as a finished string. The alternative was passing four pieces of
        // translation state across and restating the branches at the other end,
        // which is how the two would drift apart.
        val wideLyricsStatus = when {
            translationState is LyricsTranslationUiState.Loading ->
                stringResource(R.string.translating_lyrics_to, translationLanguageName)
            showingTranslation ->
                stringResource(R.string.lyrics_translated_to, translationLanguageName)
            translationState is LyricsTranslationUiState.SameLanguage ->
                stringResource(R.string.lyrics_already_in_language, translationLanguageName)
            lyricsSource != null -> stringResource(R.string.lyrics_by, lyricsSource.label)
            lyricsUnavailable -> stringResource(R.string.no_lyrics_found)
            lyrics.isNullOrEmpty() -> lyricsLoadingText
            else -> stringResource(R.string.lyrics_saved_with_download)
        }

        // The line above the scrubber, whichever of the two the phone would be
        // showing here: the lyrics' own source or translation state while the
        // lyric sheet is up, and the current line otherwise — which is what the
        // phone shows behind its queue too, and is far more use there than a
        // caption about lyrics nobody is looking at.
        val wideStatusContent: @Composable (Modifier) -> Unit = { statusModifier ->
            if (lyricsOpen) {
                Text(
                    text = wideLyricsStatus,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = statusModifier.padding(vertical = 4.dp),
                )
            } else if (syncedLyricsEnabled) {
                Box(modifier = statusModifier) {
                    if (displayedLyrics.isNotEmpty()) {
                        CurrentLyricLine(
                            lines = displayedLyrics,
                            trackKey = song.videoId,
                            positionMs = lyricsPositionMs,
                            isPlaying = isPlaying,
                            durationMs = durationMs,
                            onClick = openLyrics,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (lyricsUnavailable) {
                        LyricsUnavailableLine(
                            trackKey = song.videoId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LyricsLoadingLine(
                            text = lyricsLoadingText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // The queue, as a slot on the same terms as the lyrics one — built here
        // where the queue callbacks already are, so the split lists exactly what
        // the phone lists rather than a second arrangement of the same rows.
        val wideQueueContent: @Composable (Modifier) -> Unit = { queueModifier ->
            Column(modifier = queueModifier) {
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

        Box(modifier = modifier.fillMaxSize()) {
        WidePlayerControls(
            song = song,
            isPlaying = isPlaying,
            isLoading = isLoading || audioVersionSwitching,
            isAudioVersion = isAudioVersion,
            audioVersionSwitching = audioVersionSwitching,
            hasAlternateVersion = hasAlternateVersion || hasVideoVersion,
            onToggleAudioVersion = onToggleAudioVersion,
            positionMs = positionMs,
            durationMs = durationMs,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            autoplayEnabled = autoplayEnabled,
            signedIn = signedIn,
            accountName = accountName,
            likeStatus = likeStatus,
            hideVolumeBar = hideVolumeBar,
            volume = volume,
            maxVolume = maxVolume,
            audioManager = audioManager,
            onVolumeDragging = { volumeDragging = it },
            scrubbing = scrubbing,
            onScrubbingChange = { scrubbing = it },
            scrubValue = scrubValue,
            onScrubValueChange = { scrubValue = it },
            onToggleLike = onToggleLike,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeekFraction = onSeekFraction,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onToggleAutoplay = onToggleAutoplay,
            onOpenMenu = onOpenMenu,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenOutput = openAudioOutput,
            onListenTogether = onListenTogether,
            lyricsOpen = lyricsOpen,
            queueOpen = queueOpen,
            onToggleLyrics = toggleLyrics,
            onToggleQueue = {
                queueOpen = !queueOpen
                if (queueOpen) closeLyrics()
            },
            showQueue = widePanelIsQueue,
            statusContent = wideStatusContent,
            lyricsContent = wideLyricsContent,
            queueContent = wideQueueContent,
            legacyMesh = legacyMesh,
            canvasFrame = canvasFrame,
            artMesh = artMesh,
            progress = wideSplit,
        )
        // The output drawer and the pipeline dialog are drawn by the phone
        // layout below, past the `return` this branch takes — so without
        // mounting them here too the headphones segment and the quality
        // readout set their flags on a tablet and nothing ever appears. They
        // are overlays over whatever player is on screen, and this is the
        // player that is on screen.
        if (showAudioPipeline) {
            AudioPipelineDialog(
                hazeState = playerHaze,
                onDismiss = { showAudioPipeline = false },
            )
        }
        if (showAudioOutput) {
            AudioOutputSheet(
                hazeState = playerHaze,
                accountName = accountName,
                onDismiss = { showAudioOutput = false },
            )
        }
        if (lyricsOffsetOpen) {
            LyricsOffsetSheet(
                hazeState = playerHaze,
                onDismiss = onDismissLyricsOffset,
            )
        }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
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
        if (legacyMesh) {
            // v1.5's backdrop, restored verbatim: no seam, because the blobs
            // are not anchored to anything on screen — they fill the player and
            // the artwork simply sits on top of them. Keyed on the track, so
            // they drift when the player opens and on every skip, then rest.
            // Position ticks recompose this screen twice a second and must not
            // drag a full-screen blur along with them, which is why the palette
            // is passed as one immutable value.
            MeshGradientBackground(
                palette = rememberArtworkColors(song.thumbnailUrl, canvasFrame),
                trackKey = song.videoId,
            )
        } else {
            ArtworkMeshBackdrop(
                mesh = artMesh,
                seam = if (heroMode) heroHeight else 0.dp,
            )
        }

        // The artwork, edge to edge and running up behind the status bar,
        // dissolving into the backdrop where the sleeve's bottom edge would
        // have been. It lives out here rather than in the sleeve because that
        // is the only way to escape the player's side gutter and its status-bar
        // inset — a banner that stops short of either reads as a misplaced card
        // rather than as the artwork the screen is made of.
        if (heroHeight > 0.dp) {
            // The still sleeve first, so a clip fading in on top of it never
            // shows the backdrop through the gap between them — and only until
            // that fade has run. Both layers carry the same bottom gradient, so
            // a still frame left lit under a settled clip is not hidden by it:
            // down in the fade the clip is only part-opaque, and what shows
            // through it there is the cover art rather than the backdrop. That
            // is the artwork and the clip on screen at once.
            //
            // So it is dropped outright once the clip is opaque, rather than
            // held at alpha 0: nothing under a full-bleed clip is ever visible,
            // and a full-screen AsyncImage kept mounted for no one is a bitmap
            // and a layer the compositor still has to carry.
            //
            // Kept mounted through the handover in either direction rather than
            // dropped the moment [p] crosses the collapse threshold: the sleeve
            // behind it is still transparent at that point, so pulling the
            // banner straight out leaves a frame or two with no artwork anywhere
            // on screen before the card catches up.
            if (heroMode && !(stillCovered && heroClip != null) &&
                (p < 0.5f || heroVisible > 0.001f)
            ) {
                // Dropping this painter (once the canvas is opaque, or while a
                // panel is open) also drops the proof that this particular
                // destination can draw. If it is mounted again, keep the
                // sleeve visible until the new painter reports Success.
                DisposableEffect(artRequest) {
                    onDispose { heroArtLoaded = false }
                }
                AsyncImage(
                    // Decoded at the same size the sleeve asks for, so the two
                    // share one entry in Coil's cache and one bitmap: the pair
                    // cross-fade into each other, and asking twice at two sizes
                    // would decode the same art twice and let the banner fade in
                    // before its own copy had arrived.
                    //
                    // Literally the same request object as the sleeve's, not an
                    // identical one — see [artRequest] for why that distinction
                    // is the whole of it.
                    model = artRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { heroArtLoaded = it is AsyncImagePainter.State.Success },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(heroHeight)
                        // Haze must observe the drawable layer itself. A
                        // source on the surrounding layout only captured its
                        // mesh backdrop, leaving the cover sharp in the pill.
                        .hazeSource(playerHaze)
                        .graphicsLayer {
                            // Hands its opacity to the clip as the clip takes
                            // over, and takes it straight back if there is no
                            // clip mounted to hand it to.
                            alpha = heroVisible *
                                (1f - if (heroClip != null) canvasCover.floatValue else 0f)
                            // The mask below erases part of what this layer
                            // drew, which it can only do in a buffer of its own.
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height * (1f - HERO_FADE_FRACTION),
                                    endY = size.height,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
            }

            // Motion artwork over it, in the same frame.
            //
            // Always composed while there's a clip to play, never gated on
            // [heroVisible]: the clip has to be mounted and decoding *before*
            // it can report the first frame that raises heroT in the first place.
            if (heroMode) {
                heroClip?.let { clip ->
                    CanvasArtworkPlayer(
                        canvas = clip,
                        isPlaying = isPlaying,
                        onRenderedChanged = { canvasRendered = it },
                        onFrameCaptured = { canvasFrame = it },
                        refreshFrameEveryMs = meshRefreshMs,
                        onCoverChanged = { canvasCover.floatValue = it },
                        bottomFade = HERO_FADE_FRACTION,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(heroHeight)
                            .hazeSource(playerHaze),
                    )
                }
            }

            // The clock, the signal bars and the drag handle are all white, and
            // the banner puts whatever the artwork happens to have up there
            // directly behind them — a bright frame or a pale sleeve leaves the
            // top of the screen unreadable. Faded in with the banner and gone
            // with it.
            if (heroVisible > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(statusBarTop + topStrip)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f * heroVisible),
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
                .pointerInput(showAudioPipeline, panelScrolling) {
                    if (showAudioPipeline || panelScrolling) return@pointerInput
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { swipeOffset = 0f },
                        onDragEnd = {
                            // The same two buzzes the transport glyphs give, so
                            // swiping the sleeve and tapping skip feel like one
                            // gesture with two spellings.
                            when {
                                total <= -swipeThreshold -> {
                                    haptics.play(Haptic.SkipNext)
                                    onNext()
                                }
                                total >= swipeThreshold -> {
                                    haptics.play(Haptic.SkipPrevious)
                                    onPrevious()
                                }
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
            // a stray downward swipe on the artwork or the controls. Docked
            // there is no sheet to pass anything to, so all that is left of it
            // is the room it kept above the artwork.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topStrip),
                contentAlignment = Alignment.Center,
            ) {
                if (!docked) {
                    // While the origin caption is present the handle belongs at
                    // the top of the strip. As lyrics or the queue replace the
                    // album-cover player, it glides into the now-empty strip's
                    // vertical centre alongside the caption's fade.
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(
                                y = lerp(
                                    6.dp,
                                    (topStrip - 5.dp).coerceAtLeast(0.dp) / 2,
                                    p,
                                ),
                            )
                            .width(38.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.32f)),
                    )
                }
                // [p] is the shared album-to-panel transition. Keeping this in
                // composition until its final frame gives the caption a real
                // fade on both entry and exit, but removes its click target
                // entirely once lyrics or the queue owns the player.
                if (p < 0.999f) {
                    Text(
                        text = playedBy?.let {
                            stringResource(R.string.played_by, it)
                        } ?: song.radioName?.let {
                            stringResource(R.string.playing_radio, it)
                        } ?: stringResource(
                            R.string.playing_from,
                            song.playbackSource ?: song.albumName ?: stringResource(R.string.queue),
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.55f),
                                offset = Offset(0f, 1f),
                                blurRadius = 4f,
                            ),
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(if (docked) Alignment.Center else Alignment.BottomCenter)
                            .graphicsLayer { alpha = 1f - p }
                            .clickable {
                                if (playedBy != null) {
                                    onListenTogether()
                                } else if (song.playbackSourceType == PlaybackSourceType.QUEUE) {
                                    queueOpen = true
                                    closeLyrics()
                                } else {
                                    onOpenPlaybackSource()
                                }
                            }
                            .padding(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = 1.dp),
                    )
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
                    .pointerInput(showAudioPipeline, panelScrolling) {
                        if (showAudioPipeline || panelScrolling) return@pointerInput
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
                                                queueOpen = open
                                            }
                                            queueReleased++
                                        },
                                    )
                                }
                                return@awaitEachGesture
                            }
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
            // Read up here rather than down by the scrubber: the stats-for-nerds
            // line now lives inside the sleeve itself, so the art Box below
            // needs these before the seek bar does.
            val showNerdStats by AppSettings.showNerdStats.collectAsStateWithLifecycle()
            val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
            // Hoisted alongside the other two rather than read where it is drawn:
            // the stats block is inside a condition that flips as the sleeve
            // collapses, and re-subscribing to a flow on every frame of that
            // collapse is a waste of a subscription.
            val smartFadeOn by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
            // The scrubber retains its existing transition sheen while a real
            // Smart Mix is active. This state is independent from the removed
            // header icon.
            val mixing by AppSettings.smartMixInProgress.collectAsStateWithLifecycle()
            val smartAnalysis by AppSettings.smartAnalysis.collectAsStateWithLifecycle()
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
                // aspect ratios (a phone-shaped sheet as readily as a docked pane)
                // the cancellation lands a pass late, the grant overshoots, the
                // next pass corrects past it the other way, and the two chase
                // each other through the same handful of values forever instead
                // of settling — a full-amplitude standing oscillation, not the
                // single-pixel shiver this rounding alone was built to absorb.
                // See [granted] below for the fix.
                // Do not feed transitional artwork measurements back into the controls.
                // The settled player's spread is retained throughout the return animation.
                if (!lyricsOpen && p == 0f) {
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

                // Empty state lives on this Box, not the AsyncImage: a
                // background *and* a painter both trying to fill the same
                // clipped shape is what read as two overlapping squares
                // whenever there was nothing to paint. One layer, one square.
                // [artLoaded] is hoisted to the screen, where the banner needs
                // it too.
                Box(
                    modifier = Modifier
                        // The lambda overload deliberately: the Dp one reads
                        // its arguments at composition, so an animated offset
                        // recomposes and re-measures this Box — cover, clip and
                        // all — once per frame. Read at placement instead, the
                        // same movement costs a placement pass.
                        .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                        .size(artSize)
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
                                    closeLyrics()
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
                    //
                    // Held fully opaque until the destination banner has
                    // artwork of its own,
                    // regardless of [heroT]: the banner is sticky across skips
                    // by design (see [heroSettled]), but its content is not — a
                    // new track's cover has to come from somewhere while the
                    // banner waits on Coil or the clip's first frame, and the
                    // sleeve underneath, with its loading icon, is that
                    // somewhere. Once either destination source catches up,
                    // hiding the sleeve behind the banner is invisible.
                    // [artLoaded] alone is not enough: the sleeve and banner
                    // use separate painters, and the banner can still be empty
                    // for a frame after the sleeve reports Success.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // The compact sleeve is the source while full
                            // bleed artwork is off, including its Canvas.
                            .hazeSource(playerHaze)
                            .graphicsLayer {
                                alpha = if (heroArtLoaded || canvasRendered) {
                                    1f - heroVisible
                                } else {
                                    1f
                                }
                            }
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
                        if (!artLoaded && !canvasRendered) {
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
                            //
                            // Asked for at the source's own size rather than the
                            // sleeve's: it is the same request the full-bleed
                            // banner makes, and the banner is taller than the
                            // sleeve is wide. One ask, one decode, one bitmap for
                            // both — and nothing to upscale when the two swap.
                            model = artRequest,
                            contentDescription = null,
                            // Video thumbnails are 16:9; letterboxing them inside
                            // the square sleeve looks like a broken frame.
                            contentScale = ContentScale.Crop,
                            onState = {
                                artLoaded = it is AsyncImagePainter.State.Success
                                // Only the failure is latched, and only upwards:
                                // the retry that clears it is [artFailed]'s own
                                // effect, and clearing it from a Loading state
                                // here would cancel that effect's wait every time
                                // the painter passed back through Loading.
                                if (it is AsyncImagePainter.State.Success) artFailed = false
                                if (it is AsyncImagePainter.State.Error) artFailed = true
                            },
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
                            // [artLoaded] still flips the moment it does.
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent { if (artLoaded || !canvasRendered) drawContent() },
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
                                    refreshFrameEveryMs = meshRefreshMs,
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
                        // A plain white line reads fine over the usual dark
                        // tile, but a light stretch of an animated cover — sky,
                        // snow, a pale sleeve — washes it out entirely. The
                        // shadow costs nothing on a dark background and is what
                        // keeps it legible on a bright one.
                        val nerdStyle = MaterialTheme.typography.labelSmall.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.55f),
                                offset = Offset(0f, 1f),
                                blurRadius = 4f,
                            ),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .graphicsLayer { alpha = 1f - p * 2f },
                        ) {
                            nerdStats?.describe(context)?.let { stats ->
                                Text(
                                    text = stats,
                                    style = nerdStyle,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            // Only when Automix is actually switched on:
                            // otherwise this would report on analysis nothing is
                            // going to use, which is noise rather than a stat.
                            if (smartFadeOn) {
                                Text(
                                    // Both sides always named, even when they
                                    // agree, so the line reads the same way every
                                    // time and the eye can find the half it wants
                                    // without re-parsing the sentence.
                                    text = if (song.isVideoOrigin) {
                                        stringResource(R.string.automix_not_supported_video)
                                    } else {
                                        stringResource(
                                            R.string.automix_analysis_status,
                                            smartAnalysis.current.localizedLabel(),
                                            smartAnalysis.next.localizedLabel(),
                                        )
                                    },
                                    style = nerdStyle,
                                    // Dimmer than the measured line above it: that
                                    // one describes the audio, this one describes
                                    // the app, and the ranking should show.
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
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
                val swipeHintProgress = (abs(swipeSettle) / swipeThreshold)
                    .coerceIn(0f, 1f) * (1f - p)
                if (swipeHintProgress > 0.01f) {
                    val showNext = swipeSettle > 0f
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
                        // Collapsed, this row shares the header with the sleeve
                        // rather than sitting under it, and the two are not the
                        // same height — centring the credits in the taller of
                        // the two boxes left them riding low against the
                        // artwork they belong to. Only as it collapses: opened
                        // out, the row is below the sleeve and owns its band.
                        .offset(y = titleTop - lerp(0.dp, (HEADER_HEIGHT - THUMB_SIZE) / 2, p))
                        .padding(start = titleStart)
                        .height(HEADER_HEIGHT)
                        // Where the dismiss band ends — see its top on the
                        // artwork above. Taken from the row rather than added up
                        // from the sleeve so the gap between the two is inside
                        // the band as well: it is a gap in one block, not a seam
                        // between two, and a finger should not be able to find it.
                        .onGloballyPositioned { dismissBandBottom = it.boundsInRoot().bottom },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // Shrinks as the header collapses, so the queue's
                        // heading doesn't have to compete with it.
                        val titleSize = lerp(20.sp, 16.sp, p)
                        // Only the title's own overflow gates the artist's stagger
                        // below — an artist line that's long on its own has no
                        // reason to wait on a title that already fits.
                        var titleOverflowing by remember { mutableStateOf(false) }
                        // Only while these credits are the screen. Collapsed into
                        // a header over the queue or the lyrics they are a label
                        // on a list, and a label that crawls pulls the eye off
                        // whatever is being read below it.
                        val scrolls = p < 0.01f
                        MarqueeText(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = titleSize,
                            ),
                            color = Color.White,
                            enabled = scrolls,
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
                        MarqueeText(
                            text = song.artist,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.W500,
                                fontSize = titleSize,
                            ),
                            color = Color.White.copy(alpha = 0.55f),
                            enabled = scrolls,
                            // A title that's also scrolling gets to go first —
                            // starting together reads as clutter, so the artist
                            // waits a beat before it joins in.
                            startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
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
                            contentDescription = stringResource(
                                if (liked) R.string.remove_from_liked else R.string.like,
                            ),
                            onClick = onToggleLike,
                            active = liked,
                            haptic = if (liked) Haptic.ToggleOff else Haptic.ToggleOn,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    CircleGlyph(
                        icon = if (showRevertCue) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.more),
                        onClick = onOpenMenu,
                    )
                }

                if (lyricsOpen && panelsSettled) {
                        LyricsTranslationMotion(
                            trigger = translationTransition,
                            reduceMotion = reduceTranslationMotion,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = HEADER_HEIGHT)
                                // Arrives after the sleeve has finished collapsing
                                // into the header rather than during — see
                                // [panelsSettled]. Fading lyrics in over a sleeve
                                // still mid-collapse doubled the same movement in
                                // two places on screen at once, and composing them
                                // there was what made the collapse stutter.
                                .graphicsLayer { alpha = panelFade },
                        ) { particleProgress ->
                            LyricsPanel(
                                lines = displayedLyrics,
                                trackKey = song.videoId,
                                positionMs = lyricsPositionMs,
                                looking = !lyricsUnavailable,
                                isPlaying = isPlaying,
                                onSeekToLine = seekToLyric,
                                controlsOpen = lyricsControlsOpen,
                                onRevealControls = { lyricsControlsOpen = true },
                                onHideControls = { lyricsControlsOpen = false },
                                translationProgress = particleProgress,
                                onScrollingChange = { lyricsScrolling = it },
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
                    val translateShown = lyricsControlsOpen
                    val translateFade by animateFloatAsState(
                        targetValue = if (translateShown) 1f else 0f,
                        animationSpec = tween(if (translateShown) 220 else 160),
                        label = "translateFade",
                    )
                    if (translateFade > 0.01f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .graphicsLayer { alpha = translateFade },
                        ) {
                            TranslationToggleButton(
                                state = translationState,
                                showingTranslation = showingTranslation,
                                // Not tappable on the way out: a disc at 20%
                                // opacity is on its way to gone, not a target.
                                enabled = translateShown && !lyrics.isNullOrEmpty(),
                                onClick = toggleTranslation,
                            )
                        }
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
                if (!lyricsOpen && (queueDragging || (queueProgress > 0.01f && panelsSettled))) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT)
                            .graphicsLayer {
                                alpha = if (queueDragging) {
                                    ((queueProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                } else {
                                    panelFade
                                }
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
                            onScrollingChange = { queueScrolling = it },
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
            AnimatedVisibility(
                visible = !lyricsOpen || lyricsControlsOpen,
                // Fade at the final position; never animate the controls' height.
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(160)),
            ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The slider's touch target reaches ~13dp above the
                        // drawn bar, so the strip reads as further off it than
                        // it is. Nudged down into that dead space, the same way
                        // the timestamps below are pulled back up into it.
                        .offset(y = 6.dp),
                ) {
                    if (displayedLyrics.isNotEmpty()) {
                        CurrentLyricLine(
                            lines = displayedLyrics,
                            trackKey = song.videoId,
                            positionMs = lyricsPositionMs,
                            isPlaying = isPlaying,
                            durationMs = durationMs,
                            // Still visible over the queue, so still a valid way
                            // in: opens the same full lyrics panel it always has,
                            // closing the queue behind it the same way the "Up
                            // next" glyph closes lyrics behind the queue.
                            onClick = openLyrics,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (lyricsUnavailable) {
                        LyricsUnavailableLine(
                            trackKey = song.videoId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LyricsLoadingLine(
                            text = lyricsLoadingText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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
                Text(
                    text = when {
                        translationState is LyricsTranslationUiState.Loading ->
                            stringResource(R.string.translating_lyrics_to, translationLanguageName)
                        showingTranslation ->
                            stringResource(R.string.lyrics_translated_to, translationLanguageName)
                        translationState is LyricsTranslationUiState.SameLanguage ->
                            stringResource(R.string.lyrics_already_in_language, translationLanguageName)
                        lyricsSource != null -> stringResource(R.string.lyrics_by, lyricsSource.label)
                        lyricsUnavailable -> stringResource(R.string.no_lyrics_found)
                        lyrics.isNullOrEmpty() -> lyricsLoadingText
                        else -> stringResource(R.string.lyrics_saved_with_download)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = 6.dp)
                        .padding(vertical = 4.dp),
                )
            }
            val transitionWindow by AppSettings.smartTransitionWindow.collectAsStateWithLifecycle()
            ThinSlider(
                value = shown,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    // On release only. Ticking the whole way along the bar turns
                    // a scrub into a rattle, and the beat that matters is the one
                    // that says where the playhead landed.
                    haptics.play(Haptic.Select)
                    pendingSeek = scrubValue
                    onSeekFraction(scrubValue)
                    scrubbing = false
                },
                // Suppressed under the finger: the bar is already thickening and
                // tracking a drag, and a sheen sweeping through that reads as a
                // rendering glitch rather than as a signal.
                mixing = mixing && !scrubbing,
                // Hidden while scrubbing for the same reason as the sheen: the
                // planner is still describing where the transition *would* be,
                // and a marker sitting under a finger that is moving the
                // playhead invites reading it as a drag target.
                transitionWindow = transitionWindow
                    ?.takeIf { !scrubbing && it.end > it.start }
                    ?.let { it.start..it.end },
            )
            val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
            val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
            val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
            // Whether this playback session is even asking for a lossless
            // stream — the same computation SourceResolver.requestForNow()
            // makes, mirrored here so "Loading lossless" only appears when a
            // lossless fetch is actually in flight, not on every buffering
            // YouTube track.
            val effectiveQuality = if (metered == true) cellularQuality else wifiQuality
            val losslessRequested = effectiveQuality == AudioQuality.LOSSLESS
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
                    effectiveQuality = effectiveQuality,
                    nerdStats = nerdStats,
                    onBadgeClick = { showAudioPipeline = true },
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
            Spacer(Modifier.height(14.dp + controlSpread / 2))

            // ---- Transport ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportGlyph(
                    icon = R.drawable.ic_player_previous,
                    contentDescription = stringResource(R.string.widget_previous),
                    size = 48.dp,
                    onClick = onPrevious,
                    // Lit whenever back has something to do — either a track to
                    // step to, or enough elapsed for it to restart this one.
                    enabled = hasPrevious || positionMs > BACK_RESTARTS_AFTER_MS,
                    haptic = Haptic.SkipPrevious,
                )
                // While the stream URL resolves and buffers, the play glyph
                // would be a lie — show progress instead.
                if (isLoading || audioVersionSwitching) {
                    // Same footprint as the play/pause target — a smaller box
                    // here would shunt everything below it on every load.
                    Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                } else {
                    TransportGlyph(
                        icon = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        size = 72.dp,
                        touchSize = 100.dp,
                        onClick = onPlayPause,
                        haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
                    )
                }
                TransportGlyph(
                    icon = R.drawable.ic_player_next,
                    contentDescription = stringResource(R.string.widget_next),
                    size = 48.dp,
                    onClick = onNext,
                    enabled = hasNext,
                    haptic = Haptic.SkipNext,
                )
            }

            // Keep the volume slot's full footprint when its contents are
            // hidden. Removing the slot itself shortened the controls by 50dp
            // and moved every control below it. A display preference should not
            // change the half-player's geometry.
            Spacer(Modifier.height(18.dp + controlSpread / 2))

            // ---- Volume ----
            if (!hideVolumeBar) {
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
            } else {
                // ThinSlider's fixed touch target: activeHeight (10dp) + 22dp.
                Spacer(Modifier.height(32.dp))
            }

            // The volume slider already has 13dp below its drawn track.
            // Balance that invisible inset with the caption gap below the icons.
            Spacer(Modifier.height(6.dp))

            // Lyrics and queue are the two things that are true of the player in
            // both states, so they are simply always here. Only the capsule
            // between them swaps: output and party while the artwork is showing,
            // the three playback modes once the queue is.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Sized for the wider of the two capsules — the three-up one — in
            // both states. Computed for whichever was on screen it would change
            // as they swap, and the lyrics and queue glyphs would slide with it.
            val widestRow = BOTTOM_ACTION_SIZE * 2 + pillWidth(3)
            val edgeInset = ((maxWidth - widestRow) / 4).coerceAtLeast(0.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = edgeInset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomGlyph(
                    icon = BitChordIcons.LyricsQuote,
                    contentDescription = stringResource(if (lyricsOpen) R.string.close_lyrics else R.string.open_lyrics),
                    // Lyrics and the queue are two things to put over the
                    // sleeve and there is only one sleeve, so opening either
                    // closes the other. Queue has always done this; lyrics did
                    // not have to until it stopped being hidden while the queue
                    // was up, at which point both could be lit at once.
                    onClick = toggleLyrics,
                    highlighted = lyricsOpen,
                )
                AnimatedContent(
                    targetState = queueOpen,
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 140)) togetherWith fadeOut(tween(140)))
                            // Unclipped: the capsule's own rounded ends are what
                            // the eye follows through the width change, and the
                            // default clip cuts them square while it happens.
                            .using(SizeTransform(clip = false) { _, _ -> tween(220) })
                    },
                    label = "playerBottomPill",
                ) { showQueueModes ->
                    if (showQueueModes) {
                        Pill {
                            PillSegment(
                                icon = BitChordIcons.Shuffle,
                                contentDescription = stringResource(
                                    if (shuffleEnabled) R.string.shuffle_on else R.string.shuffle_off,
                                ),
                                onClick = onToggleShuffle,
                                highlighted = shuffleEnabled,
                                haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                                tapWindowMs = SHUFFLE_TAP_WINDOW_MS,
                            )
                            PillDivider()
                            PillSegment(
                                icon = if (repeatMode == Player.REPEAT_MODE_ONE) null else BitChordIcons.Repeat,
                                label = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                                contentDescription = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_one)
                                    Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_all)
                                    else -> stringResource(R.string.repeat_off)
                                },
                                onClick = onCycleRepeat,
                                highlighted = repeatMode != Player.REPEAT_MODE_OFF,
                                // Three states, so the buzz tracks the edges of
                                // the cycle: leaving off rises, returning to off
                                // falls, and the step between the two repeat
                                // modes is just a selection.
                                haptic = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Haptic.ToggleOn
                                    Player.REPEAT_MODE_ONE -> Haptic.ToggleOff
                                    else -> Haptic.Select
                                },
                            )
                            PillDivider()
                            PillSegment(
                                icon = BitChordIcons.Infinity,
                                contentDescription = stringResource(
                                    if (autoplayEnabled) R.string.autoplay_on else R.string.autoplay_off,
                                ),
                                onClick = onToggleAutoplay,
                                highlighted = autoplayEnabled,
                                haptic = if (autoplayEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                                tapWindowMs = AUTOPLAY_TAP_WINDOW_MS,
                            )
                        }
                    } else {
                        OutputPartyPill(
                            onOutput = openAudioOutput,
                            onParty = onListenTogether,
                            onChangeTrack = onToggleAudioVersion,
                            isAudioVersion = isAudioVersion,
                            audioVersionSwitching = audioVersionSwitching,
                            showChangeTrack = hasAlternateVersion || hasVideoVersion || audioVersionSwitching,
                        )
                    }
                }
                BottomGlyph(
                    icon = BitChordIcons.Queue,
                    contentDescription = stringResource(R.string.up_next),
                    onClick = {
                        closeLyrics()
                        queueOpen = !queueOpen
                    },
                    highlighted = queueOpen,
                    haptic = if (queueOpen) Haptic.Tap else Haptic.Expand,
                )
            }
            }
            // Keep the current output caption visible in both player and queue modes.
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                OutputCaption(
                    accountName = accountName,
                    onOpenOutput = openAudioOutput,
                    onOpenParty = onListenTogether,
                )
            }
            Spacer(Modifier.height(18.dp))
            }
            }
            }
            }
        }
        if (showAudioPipeline) {
            AudioPipelineDialog(
                hazeState = playerHaze,
                onDismiss = { showAudioPipeline = false },
            )
        }
        if (showAudioOutput) {
            AudioOutputSheet(
                hazeState = playerHaze,
                accountName = accountName,
                onDismiss = { showAudioOutput = false },
            )
        }
        if (lyricsOffsetOpen) {
            LyricsOffsetSheet(
                hazeState = playerHaze,
                onDismiss = onDismissLyricsOffset,
            )
        }
    }
}

internal fun adjustedLyricsPosition(positionMs: Long, offsetMs: Int): Long =
    (positionMs - offsetMs.toLong()).coerceAtLeast(0L)

internal fun adjustedLyricsSeekTarget(lineTimeMs: Long, offsetMs: Int): Long =
    (lineTimeMs + offsetMs.toLong()).coerceAtLeast(0L)

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


/**
 * The player's shape on a window too big for the phone layout to make sense
 * of, with the lyrics open — the tablet layout, in both of the two shapes it
 * takes.
 *
 * One composable for both rather than one apiece, because they are not two
 * designs: they are the same header, scrubber, transport, volume bar and
 * bottom row, arranged either side by side ([split], a landscape tablet) or
 * stacked ([split] false, a tablet held upright). Written as two functions the
 * way the original patch had it, every one of those pieces existed twice, and
 * the second copy is where a tablet quietly falls a version behind — one of
 * them gets the new control and the other doesn't.
 *
 * Deliberately a separate layout rather than a wide-window branch threaded
 * through [NowPlayingScreen]'s own: that function's collapsing sleeve, hero
 * banner and vertical drag gesture all exist to let a phone-shaped surface do
 * two jobs — the full player and the mini player it collapses into — and
 * neither of these shapes is ever doing either. This is drawn once, at the one
 * shape it takes.
 *
 * The scrub and volume state are passed in rather than kept here: they are the
 * same physical controls the phone layout drives, feeding the same
 * [AudioManager] and the same seek, and a private copy would be a second clock
 * to keep in step with the first. It also means the artwork's paused/scrubbing
 * pose answers a drag on the bar for free.
 */
@Composable
private fun WidePlayerControls(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    isAudioVersion: Boolean = false,
    audioVersionSwitching: Boolean = false,
    hasAlternateVersion: Boolean = false,
    hasVideoVersion: Boolean = false,
    onToggleAudioVersion: () -> Unit = {},
    positionMs: Long,
    durationMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    autoplayEnabled: Boolean,
    signedIn: Boolean,
    /** For the output caption's "<name>'s Phone" — see [OutputCaption]. */
    accountName: String?,
    likeStatus: LikeStatus,
    hideVolumeBar: Boolean,
    volume: Animatable<Float, AnimationVector1D>,
    maxVolume: Int,
    audioManager: AudioManager?,
    onVolumeDragging: (Boolean) -> Unit,
    scrubbing: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
    scrubValue: Float,
    onScrubValueChange: (Float) -> Unit,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenOutput: () -> Unit,
    onListenTogether: () -> Unit,
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    /**
     * Which panel the right column draws. Latched by the caller rather than
     * read off [queueOpen], so the exit keeps showing whichever panel was up.
     */
    showQueue: Boolean,
    /**
     * The line that sits above the scrubber — built by the caller so it reads
     * exactly what the phone reads in the same state.
     */
    statusContent: @Composable (Modifier) -> Unit,
    /** The whole lyrics stack, built by the caller — see [wideLyricsContent]. */
    lyricsContent: @Composable (Modifier) -> Unit,
    /** The queue list, likewise — see [wideQueueContent]. */
    queueContent: @Composable (Modifier) -> Unit,
    legacyMesh: Boolean,
    canvasFrame: Bitmap?,
    artMesh: ArtworkMesh?,
    /**
     * How far into the split this is, 0 (closed player: content at
     * [PLAYER_MAX_WIDTH], centred) to 1 (two even columns). Driven by the
     * caller so it survives the lyrics closing and can run back down — see
     * `wideSplit` there.
     */
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    val liveFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shown = if (scrubbing) scrubValue else liveFraction

    // Paused always wins outright over a scrub in progress — see
    // [ARTWORK_PAUSE_SHRINK_SCALE]. Nothing here reacts to a touch on the
    // artwork itself: a detector on the sleeve never reliably agreed with the
    // gestures already on that node, and tied to the scrubber instead the art
    // has no touch handling of its own to conflict with anything.
    val artworkScale by animateFloatAsState(
        targetValue = when {
            !isPlaying -> ARTWORK_PAUSE_SHRINK_SCALE
            scrubbing -> ARTWORK_DRAG_SHRINK_SCALE
            else -> ARTWORK_EXPANDED_SCALE
        },
        animationSpec = tween(durationMillis = ARTWORK_SCALE_DURATION_MS, easing = ArtworkScaleEasing),
        label = "wideArtworkScale",
    )

    // The right column waits for the lane to finish moving before it composes
    // anything, for the same reason the phone's panels wait for the sleeve —
    // the lyric sheet and the queue list are the two heaviest compositions on
    // this screen, and doing either one on top of a running animation is what
    // the open stuttering was.
    //
    // Kept mounted while the fade still has something to show, so the exit
    // fades the panel out rather than cutting it on the first frame of the
    // close — [panelReady] goes false immediately there, and only this keeps
    // the column alive long enough to leave gracefully.
    val panelReady = progress >= 1f
    val panelFade by animateFloatAsState(
        targetValue = if (panelReady) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "widePanelFade",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // The same choice between the two backdrop systems the ordinary player
        // makes, just without a hero seam to report: neither of these shapes
        // has a collapsing banner for the backdrop to leave a seam behind.
        if (legacyMesh) {
            MeshGradientBackground(
                palette = rememberArtworkColors(song.thumbnailUrl, canvasFrame),
                trackKey = song.videoId,
            )
        } else {
            ArtworkMeshBackdrop(mesh = artMesh, seam = 0.dp)
        }

        val controls: @Composable ColumnScope.() -> Unit = {
            // Directly above the scrubber, exactly where the phone puts it
            // while the lyrics are open — same string, same style, same nudge
            // down into the slider's dead touch space. This replaced a pill
            // and a close button floated under the lyric column, which was a
            // second way of saying something the player already has a place
            // for.
            statusContent(Modifier.fillMaxWidth().offset(y = 6.dp))
            ThinSlider(
                value = shown,
                onValueChange = {
                    onScrubbingChange(true)
                    onScrubValueChange(it)
                },
                onValueChangeFinished = {
                    haptics.play(Haptic.Select)
                    onSeekFraction(scrubValue)
                    onScrubbingChange(false)
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().offset(y = (-4).dp),
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

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportGlyph(
                    icon = R.drawable.ic_player_previous,
                    contentDescription = stringResource(R.string.widget_previous),
                    size = 48.dp,
                    onClick = onPrevious,
                    enabled = hasPrevious || positionMs > BACK_RESTARTS_AFTER_MS,
                    haptic = Haptic.SkipPrevious,
                )
                if (isLoading) {
                    Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                } else {
                    TransportGlyph(
                        icon = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.pause else R.string.play,
                        ),
                        size = 72.dp,
                        touchSize = 100.dp,
                        onClick = onPlayPause,
                        haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
                    )
                }
                TransportGlyph(
                    icon = R.drawable.ic_player_next,
                    contentDescription = stringResource(R.string.widget_next),
                    size = 48.dp,
                    onClick = onNext,
                    enabled = hasNext,
                    haptic = Haptic.SkipNext,
                )
            }

            // Preserve the same vertical rhythm whether the volume control is
            // drawn or not, just as the phone layout does.
            Spacer(Modifier.height(18.dp))
            if (!hideVolumeBar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    ThinSlider(
                        value = volume.value,
                        onValueChange = { v ->
                            onVolumeDragging(true)
                            scope.launch { volume.snapTo(v) }
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (v * maxVolume).roundToInt(),
                                0,
                            )
                        },
                        onValueChangeFinished = { onVolumeDragging(false) },
                        idleHeight = 5.dp,
                        activeHeight = 9.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                // ThinSlider's fixed touch target: activeHeight (9dp) + 22dp.
                Spacer(Modifier.height(31.dp))
            }

            Spacer(Modifier.height(18.dp))

            // The phone's own bottom row, not a tablet variation on it: the
            // lyrics glyph lit to say which panel you are in, the two-up
            // output capsule, and the way through to the queue.
            //
            // The three playback modes that used to sit here are gone, because
            // on the phone they are not part of this row at all — they are the
            // *queue's* capsule, which the row swaps in only while the queue is
            // up. A layout that shows lyrics can't be showing the queue, so
            // they never belonged here; what belongs is the two-segment output
            // capsule, which is exactly what the phone shows in this state.
            //
            // Sized off the wider, three-up capsule the queue would use, the
            // same way the phone does, so the glyphs either side sit at the
            // same inset on both and don't shift if this layout ever grows a
            // queue state of its own.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widestRow = BOTTOM_ACTION_SIZE * 2 + pillWidth(3)
                val edgeInset = ((maxWidth - widestRow) / 4).coerceAtLeast(0.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = edgeInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Lit to say which panel you are in, and the way out of
                    // it: the same glyph that opened it closes it, which is the
                    // affordance the phone has always used.
                    BottomGlyph(
                        icon = BitChordIcons.LyricsQuote,
                        contentDescription = stringResource(
                            if (lyricsOpen) R.string.close_lyrics else R.string.open_lyrics,
                        ),
                        onClick = onToggleLyrics,
                        highlighted = lyricsOpen,
                    )
                    // The capsule the phone swaps in on exactly this condition:
                    // the output pair normally, the three playback modes while
                    // the queue is up, since that is when they are what you are
                    // about to reach for.
                    AnimatedContent(
                        targetState = queueOpen,
                        transitionSpec = {
                            (fadeIn(tween(180, delayMillis = 140)) togetherWith fadeOut(tween(140)))
                                // Unclipped: the capsule's own rounded ends are
                                // what the eye follows through the width change.
                                .using(SizeTransform(clip = false) { _, _ -> tween(220) })
                        },
                        label = "widePlayerBottomPill",
                    ) { showQueueModes ->
                        if (showQueueModes) {
                            Pill {
                                PillSegment(
                                    icon = BitChordIcons.Shuffle,
                                    contentDescription = stringResource(
                                        if (shuffleEnabled) R.string.shuffle_on else R.string.shuffle_off,
                                    ),
                                    onClick = onToggleShuffle,
                                    highlighted = shuffleEnabled,
                                    haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                                    tapWindowMs = SHUFFLE_TAP_WINDOW_MS,
                                )
                                PillDivider()
                                PillSegment(
                                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) null else BitChordIcons.Repeat,
                                    label = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                                    contentDescription = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_one)
                                        Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_all)
                                        else -> stringResource(R.string.repeat_off)
                                    },
                                    onClick = onCycleRepeat,
                                    haptic = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Haptic.ToggleOn
                                        Player.REPEAT_MODE_ONE -> Haptic.ToggleOff
                                        else -> Haptic.Select
                                    },
                                    highlighted = repeatMode != Player.REPEAT_MODE_OFF,
                                )
                                PillDivider()
                                PillSegment(
                                    icon = BitChordIcons.Infinity,
                                    contentDescription = stringResource(
                                        if (autoplayEnabled) R.string.autoplay_on else R.string.autoplay_off,
                                    ),
                                    onClick = onToggleAutoplay,
                                    highlighted = autoplayEnabled,
                                    haptic = if (autoplayEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                                    tapWindowMs = AUTOPLAY_TAP_WINDOW_MS,
                                )
                            }
                        } else {
                            OutputPartyPill(
                                onOutput = onOpenOutput,
                                onParty = onListenTogether,
                                onChangeTrack = onToggleAudioVersion,
                                isAudioVersion = isAudioVersion,
                                audioVersionSwitching = audioVersionSwitching,
                                showChangeTrack = hasAlternateVersion || hasVideoVersion || audioVersionSwitching,
                            )
                        }
                    }
                    BottomGlyph(
                        icon = BitChordIcons.Queue,
                        contentDescription = stringResource(R.string.up_next),
                        onClick = onToggleQueue,
                        highlighted = queueOpen,
                        haptic = if (queueOpen) Haptic.Tap else Haptic.Expand,
                    )
                }
            }

            // Where the sound is actually going, under the capsule that
            // changes it — the phone keeps this line visible in player and
            // queue modes alike, and a tablet showing lyrics is no more
            // entitled to drop it than the queue is. Held at a fixed height
            // for the same reason the phone does: the name arrives
            // asynchronously, and letting the row size to it would shift every
            // control above as it resolved.
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                OutputCaption(
                    accountName = accountName,
                    onOpenOutput = onOpenOutput,
                    onOpenParty = onListenTogether,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            // Capped and centred rather than spread edge to edge — see
            // [WIDE_LYRICS_MAX_WIDTH].
            val contentWidth = maxWidth.coerceAtMost(WIDE_LYRICS_MAX_WIDTH)
            val halfLane = contentWidth / 2

            // Both ends of the player lane, and the journey between them is the
            // whole transition.
            //
            // At rest ([progress] 0) the lane is the closed player's: content no
            // wider than [PLAYER_MAX_WIDTH], sitting on the window's centre
            // line. Fully open it is half the content width, centred in that
            // half. Interpolating *both* the width and the centre against the
            // same progress is the fix for the entrance reading as two steps —
            // the lane used to jump to its final width on the frame this layout
            // mounted and only then travel, because the width was a layout
            // decision taken once and the travel was the only thing animated.
            val openLaneWidth = halfLane - PLAYER_GUTTER * 2
            val restLaneWidth = PLAYER_MAX_WIDTH.coerceAtMost(contentWidth - PLAYER_GUTTER * 2)
            val laneWidth = lerp(restLaneWidth, openLaneWidth, progress)
            // How far left of the container's centre the lane's own centre sits:
            // nothing at rest, a quarter of the content width once open (a
            // half-width lane centred in its half is offset by half of what is
            // left over, which is a quarter of the whole).
            val laneShift = lerp(0.dp, -(contentWidth / 4), progress)

            Box(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight(),
            ) {
                // ---- Left: the same header, scrubber and transport the
                // closed state draws, on an even half of the width rather
                // than a fixed lane. This half is the one being operated as
                // well as read, and held to a third of the window its
                // transport, volume bar and output capsule sat in a lane
                // narrower than the phone gives the very same controls.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = laneShift)
                        .width(laneWidth)
                        .fillMaxHeight()
                        .padding(vertical = DOCKED_TOP_PAD),
                    // Top-aligned to match the lyric column beside it, which
                    // starts from the top of its own space too (a scrolling list
                    // has nothing else to align it by). Centred instead, the
                    // header and transport sat noticeably lower than where the
                    // lyrics began, reading as a gap between the columns that
                    // wasn't really a gap so much as two different vertical
                    // origins.
                    verticalArrangement = Arrangement.Top,
                ) {
                    Spacer(Modifier.height(12.dp))
                    // The sleeve is square and bounded by whichever axis runs
                    // out first — the column's width on a landscape tablet, the
                    // height left over once the rows below have taken theirs on
                    // anything shorter.
                    //
                    // Expressed as a weight rather than worked out against a
                    // figure for how tall those rows come to. Two goes at that
                    // figure were both wrong in a way nothing catches: sized off
                    // the width alone, a phone turned sideways got a 360dp
                    // square in a 400dp-tall window and the transport was pushed
                    // clean off the bottom edge; sized off an estimate of the
                    // rows, a 1280x720 tablet came up about fifty dp short and
                    // dropped the output capsule off the bottom instead. The
                    // artwork is the one thing here with no natural size of its
                    // own, so it is the one thing that should be measured last:
                    // `fill = false` lets [aspectRatio] fall back from the
                    // column's width to whatever height is actually left, and
                    // every fixed row below keeps its own height by
                    // construction. There is no number left to get wrong.
                    WideArtwork(
                        song = song,
                        scale = artworkScale,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(1f)
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(20.dp))
                    WideCredits(
                        song = song,
                        signedIn = signedIn,
                        likeStatus = likeStatus,
                        onToggleLike = onToggleLike,
                        onOpenMenu = onOpenMenu,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                    )
                    Spacer(Modifier.height(18.dp))
                    controls()
                }

                // ---- Right: the lyrics, standing beside the artwork rather
                // than folded in underneath it.
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(halfLane)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = panelFade }
                        .padding(vertical = DOCKED_TOP_PAD),
                ) {
                    if (panelReady || panelFade > 0.01f) {
                    // Lyrics and queue share this column, and swapping between
                    // them is a change of contents rather than of shape — the
                    // player beside it does not move, and neither does the
                    // column. A plain crossfade is the whole transition, which
                    // is what makes going lyrics → queue and back feel like
                    // turning a page rather than reopening the panel.
                    AnimatedContent(
                        targetState = showQueue,
                        transitionSpec = {
                            fadeIn(tween(200, delayMillis = 90)) togetherWith fadeOut(tween(140))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "widePanel",
                    ) { queue ->
                        if (queue) {
                            queueContent(Modifier.fillMaxSize())
                        } else {
                            lyricsContent(Modifier.fillMaxSize())
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * The square sleeve the split draws.
 *
 * The request is remembered against the URL it is built from. Built inline in
 * the `AsyncImage` call — which is what the original patch did — a new,
 * equal-but-not-identical request is constructed on every recomposition, and
 * this screen recomposes twice a second off the position tick alone: Coil sees
 * a changed model, cancels the load in flight and starts it again, so the
 * artwork on a tablet loads erratically or not at all.
 */
@Composable
private fun WideArtwork(song: Song, scale: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val artUrl = song.artworkAt(ART_PX)
    var artLoaded by remember(artUrl) { mutableStateOf(false) }
    val request = remember(context, artUrl) {
        ImageRequest.Builder(context)
            .data(artUrl)
            .size(ART_PX)
            .build()
    }
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(if (artLoaded) 14.dp else 0.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!artLoaded) {
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxSize(0.36f),
            )
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { artLoaded = it is AsyncImagePainter.State.Success },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Title, artist, like and the three-dot menu — the split's credits block.
 *
 * Marquee rather than a plain ellipsis, matching the phone player: a tablet is
 * wider, not infinitely wide, and a long title truncated on the one surface
 * with room to scroll it would be the odd one out.
 */
@Composable
private fun WideCredits(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            var titleOverflowing by remember { mutableStateOf(false) }
            MarqueeText(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                onOverflowChange = { titleOverflowing = it },
                leading = if (song.isExplicit == true) {
                    { ExplicitBadge(Color.White) }
                } else {
                    null
                },
                modifier = Modifier.opensPage(song.albumId, onOpenAlbum),
            )
            Spacer(Modifier.height(2.dp))
            MarqueeText(
                text = song.artist,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W500),
                color = Color.White.copy(alpha = 0.55f),
                // A title that's also scrolling gets to go first — starting
                // together reads as clutter, so the artist waits a beat.
                startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                modifier = Modifier.opensPage(song.artistId, onOpenArtist),
            )
        }
        Spacer(Modifier.width(10.dp))
        // Same gate as the phone player's: no account to like against for a
        // guest, and no YouTube identity to rate a local file or a finished
        // download against either.
        if (signedIn && song.localUri == null) {
            val liked = likeStatus == LikeStatus.LIKE
            CircleGlyph(
                icon = if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                contentDescription = stringResource(
                    if (liked) R.string.remove_from_liked else R.string.like,
                ),
                onClick = onToggleLike,
                active = liked,
                haptic = if (liked) Haptic.ToggleOff else Haptic.ToggleOn,
            )
            Spacer(Modifier.width(8.dp))
        }
        CircleGlyph(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.more),
            onClick = onOpenMenu,
        )
    }
}

/**
 * The song position, ticking every frame.
 *
 * The player reports where it is about twice a second, which is fine for a
 * scrubber and far too coarse for a highlight that has to keep up with a
 * singer. This carries that report forward on the frame clock between
 * reports. Small corrections hold the highlight until playback catches up;
 * discontinuities still reset immediately so seeking remains responsive.
 *
 * Returned as state rather than a plain value on purpose: read inside a draw
 * lambda, only the draw phase re-runs each frame. Read in composition, the
 * whole line would recompose sixty times a second.
 */
@Composable
private fun rememberLyricClock(positionMs: Long, isPlaying: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(positionMs) }
    // Gated on the app being on screen. The loop asks for a frame, writes a
    // value that invalidates a drawing, and is handed the next frame for it —
    // which is a request to render continuously for as long as it runs. That is
    // the right trade for a lyric being read and the wrong one for a phone in a
    // pocket, and the composition alone cannot tell the two apart.
    //
    // Resuming needs no catch-up: [positionMs] is a key, so coming back
    // restarts the effect and reconciles the latest playback report before
    // requesting another frame.
    val foreground = rememberIsForeground()
    LaunchedEffect(positionMs, isPlaying, foreground) {
        clock.longValue = reconcileLyricPosition(clock.longValue, positionMs)
        if (!isPlaying || !foreground) return@LaunchedEffect
        val firstFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                // Advance from the authoritative report, not the held display value:
                // otherwise each small correction would accumulate permanent drift.
                clock.longValue = maxOf(clock.longValue, positionMs + frame - firstFrame)
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
 * and clipped to the letters of whatever word is being held. Blurring *after*
 * the clip rather than before is what makes the halo bleed out past the letter
 * it belongs to, which is the part that reads as light coming off a carried
 * note rather than a drop shadow sitting under the line.
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
    feather: Boolean = false,
    rise: Boolean = true,
    alignEnd: Boolean = false,
    translationProgress: State<Float>? = null,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    // Filled in and read back a letter at a time inside the draw lambdas, and
    // shared by all three copies of the line — they draw one after another on
    // the same thread, so there is only ever one letter in hand. Held here
    // rather than allocated per frame: a held word is seven letters at the
    // outside, but this runs on every frame of every line that has one.
    val growth = remember { CharGrowth() }

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

    // Sits outside [room] and outside the sweep, so what it moves is the
    // finished picture of the word — dim tail, lit head and all — rather than
    // one copy sliding out from under another. Carried by both copies from the
    // same arithmetic, which is what keeps them on top of each other.
    //
    // Off for the one-line strip above the scrubber ([rise] = false). The lift
    // belongs to a page of lyrics, where a word rising out of the line it sits
    // in is the thing being read; on a single line pinned between the credits
    // and the slider it has nothing to rise away from and reads as the strip
    // itself twitching.
    val riseAgainst: (Modifier) -> Modifier = { inner ->
        if (!rise) {
            inner
        } else {
            Modifier
                .drawWithContent {
                    val measured = layout
                    if (measured == null || line.words.isEmpty()) {
                        drawContent()
                    } else {
                        riseWith(
                            layout = measured,
                            line = line,
                            positionMs = clock.longValue,
                            inset = glowRoom.toPx(),
                            peak = WORD_RISE.toPx(),
                            growth = growth,
                        )
                    }
                }
                .then(inner)
        }
    }

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
            else -> layout?.let { sweepTo(it, line.revealedChars(position), feather) }
        }
    }

    // A right-hand duet line right-aligns twice over: the block within the row,
    // for the case where it is one short line in a wide panel, and the lines
    // within the block, for the case where it has wrapped. Neither alone is
    // enough, and the three copies all take both, so they still land on top of
    // each other.
    Box(
        modifier.lyricParticles(layout, translationProgress, glowRoom),
        contentAlignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
    ) {
        Text(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = dimAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
            modifier = riseAgainst(room),
        )
        if (glowAlpha > 0.01f) {
            Text(
                text = line.text,
                style = style,
                color = Color.White,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier
                    .graphicsLayer { alpha = glowAlpha }
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    .then(room)
                    // Each letter is masked to its own brightness with DstIn,
                    // which needs a layer of its own to erase into — against the
                    // backdrop it would take the artwork with it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        // Deliberately not the shared sweep: that lights
                        // everything sung so far, and this lights only the words
                        // being held. Most lines draw nothing here at all, which
                        // is the whole difference between this and a halo
                        // travelling under the highlight.
                        val measured = layout ?: return@drawWithContent
                        glowGrown(
                            layout = measured,
                            line = line,
                            positionMs = clock.longValue,
                            inset = glowRoom.toPx(),
                            peak = WORD_RISE.toPx(),
                            growth = growth,
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
            // The feather erases into this layer, so the layer has to exist —
            // and only while it is being drawn. Every line carrying one would
            // put the whole panel through an offscreen buffer to soften an edge
            // that at most two of them have.
            modifier = riseAgainst(
                Modifier
                    .graphicsLayer {
                        compositingStrategy = if (feather) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .then(room)
                    .then(sweep),
            ),
        )
    }
}

/**
 * Draws this text clipped to the letters of the words being held, each at its
 * own brightness — the light the singing is actually giving off, rather than a
 * band of it dragged along behind the highlight.
 *
 * Nothing at all on a line of ordinary syllables: the words that light up are
 * the ones held long enough to have earned it, so a verse of patter is simply
 * dark and costs one comparison to establish. That selectiveness is the point.
 * A glow present on every word is a property of the highlight; a glow that
 * arrives only when a note is carried is a property of the voice.
 *
 * Each letter is masked to its own bloom rather than drawn at it, because the
 * caller's layer is what this erases into — see [SweptLyricLine]. The mask
 * lands before the blur, so what spreads is already the right brightness.
 */
private fun ContentDrawScope.glowGrown(
    layout: TextLayoutResult,
    line: LyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isGrowing(positionMs)) return
    val em = layout.layoutInput.style.fontSize.toPx()
    val length = layout.layoutInput.text.length
    for (word in line.growingWords) {
        if (positionMs < word.startMs || positionMs > word.restsAtMs) continue
        val span = line.wordSpans[word.index]
        val fall = line.wordFall(word.index, positionMs)
        for (char in span.first..minOf(span.last, length - 1)) {
            word.sampleInto(char - span.first, positionMs, growth)
            if (growth.bloom <= 0.01f) continue
            val visualLine = layout.getLineForOffset(char)
            // Row-aware, for the same reason the sweep is; see [xOn].
            val from = layout.xOn(char, visualLine, inset)
            val to = layout.xOn(char + 1, visualLine, inset)
            if (to <= from) continue
            val dx = growth.shift * em
            val dy = -growth.rise * peak * fall
            val rowTop = layout.getLineTop(visualLine) + inset
            val bottom = layout.getLineBottom(visualLine) + inset
            val overhang = (to - from) * (growth.scale - 1f) / 2f
            clipRect(
                left = from - overhang + dx,
                top = rowTop - peak * GROW_HEADROOM,
                right = to + overhang + dx,
                bottom = bottom,
            ) {
                translate(left = dx, top = dy) {
                    scale(
                        growth.scale,
                        growth.scale,
                        Offset((from + to) / 2f, (rowTop + bottom) / 2f),
                    ) {
                        this@glowGrown.drawContent()
                    }
                }
                // Scoped to this letter's own clip, so it takes this letter's
                // brightness down and leaves its neighbours — which have their
                // own, a beat behind — where they are.
                drawRect(
                    color = Color.White.copy(alpha = growth.bloom),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
}

/**
 * Redraws this row with the word being sung lifted off the line, and the ones
 * behind it settling back down.
 *
 * The line is cut at word boundaries and each piece replayed at its own
 * height, which is what CSS gets for free by making every syllable its own
 * box. Cutting between words rather than inside one means no glyph is ever
 * sliced, and the pieces that are on the floor — which is most of them, most
 * of the time — are one replay between them rather than one each.
 *
 * Costs nothing at all until something is off the floor: a line with no lift
 * on it draws exactly once, the same as it did before any of this.
 */
private fun ContentDrawScope.riseWith(
    layout: TextLayoutResult,
    line: LyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isLifted(positionMs)) {
        drawContent()
        return
    }
    val em = layout.layoutInput.style.fontSize.toPx()
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
        // The row's own box. Anything standing still is clipped to exactly
        // this: a band opened upwards would take in the bottom of the row
        // above and draw it a second time, and two passes of a half-transparent
        // line do not add up to the same line. That doubled sliver along every
        // row is what read as the lines overlapping.
        val top = layout.getLineTop(visualLine) + inset
        val bottom = layout.getLineBottom(visualLine) + inset
        var at = lineStart
        var edge = layout.getLineLeft(visualLine) + inset
        for (index in line.words.indices) {
            val span = line.wordSpans[index]
            val start = maxOf(span.first, lineStart)
            val end = minOf(span.last + 1, lineEnd)
            if (start >= end) continue
            // Only while it is actually moving. Once the last letter has come to
            // rest the word is back to being an ordinary sung word settling
            // down, and the two agree exactly at the handover — a letter rests
            // at precisely the lift [LyricLine.wordLift] would give it — so the
            // cheaper single slice takes over without a step.
            val held = line.growingAt(index)?.takeIf { positionMs in it.startMs..it.restsAtMs }
            val lift = line.wordLift(index, positionMs)
            // A word with nothing happening to it is left to the flat run,
            // which is the whole of the line for all but a syllable of it.
            if (held == null && lift <= 0.01f) continue
            val from = layout.xOn(start, visualLine, inset)
            val to = layout.xOn(end, visualLine, inset)
            // Nothing to cut. Left where it is rather than stepped over, so the
            // flat run still has it and the row keeps its words.
            if (to <= from) continue
            // Everything between the last risen word and this one is flat, and
            // goes down in a single piece however many words that spans.
            if (start > at) sliceRisen(edge, top, from, bottom, 0f)
            if (held != null) {
                growEach(
                    layout, held, line, positionMs, visualLine,
                    start, end, top, bottom, inset, peak, em, growth,
                )
            } else {
                // Only what is off the floor gets room above the row to be off
                // it in; see [top].
                sliceRisen(from, top - peak, to, bottom, -lift * peak)
            }
            at = end
            edge = to
        }
        if (at < lineEnd) {
            sliceRisen(edge, top, layout.getLineRight(visualLine) + inset, bottom, 0f)
        }
    }
}

/**
 * Redraws one held word a letter at a time, each at its own swell and height.
 *
 * The word is cut between characters rather than between words, so a letter can
 * be scaled about its own centre without the ones either side of it coming
 * along. Each piece is clipped to where its letter is *going* rather than where
 * it sits: a glyph grown about its middle reaches past the box it was laid out
 * in, and clipping to that box would shave both sides off it as it swells.
 *
 * The overlap that buys — a letter's clip reaching a pixel or so into its
 * neighbour's — is why this is only ever run on a word that has earned it. Two
 * copies of a glyph edge a pixel apart is nothing on a letter mid-swell and
 * would be an obvious double image across a whole line.
 */
@Suppress("LongParameterList")
private fun ContentDrawScope.growEach(
    layout: TextLayoutResult,
    word: GrowingWord,
    line: LyricLine,
    positionMs: Long,
    visualLine: Int,
    start: Int,
    end: Int,
    top: Float,
    bottom: Float,
    inset: Float,
    peak: Float,
    em: Float,
    growth: CharGrowth,
) {
    // The settle is shared with every other word: a letter comes to rest at the
    // same small lift, and then goes down with the rest of the line.
    val fall = line.wordFall(word.index, positionMs)
    val first = line.wordSpans[word.index].first
    // Room to swell into, above the row rather than inside it. The pivot stays
    // on the row's own middle: scaling about the middle of the *band* would
    // walk every letter downwards as it grew.
    val ceiling = top - peak * GROW_HEADROOM
    val middle = (top + bottom) / 2f
    for (char in start until end) {
        word.sampleInto(char - first, positionMs, growth)
        val from = layout.xOn(char, visualLine, inset)
        val to = layout.xOn(char + 1, visualLine, inset)
        if (to <= from) continue
        val dx = growth.shift * em
        val dy = -growth.rise * peak * fall
        val overhang = (to - from) * (growth.scale - 1f) / 2f
        clipRect(
            left = from - overhang + dx,
            top = ceiling,
            right = to + overhang + dx,
            bottom = bottom,
        ) {
            translate(left = dx, top = dy) {
                scale(growth.scale, growth.scale, Offset((from + to) / 2f, middle)) {
                    this@growEach.drawContent()
                }
            }
        }
    }
}

/**
 * Where an offset sits horizontally *on the row it was cut out of*.
 *
 * [TextLayoutResult.getHorizontalPosition] answers for the row the offset
 * itself belongs to — and the offset one past the last character of a wrapped
 * row belongs to the next row, so asking where a word that runs up to a wrap
 * *ends* gives a position at the far left, one row down. A slice cut between
 * there and the word's start is empty, and the walk then treats the row as
 * finished: everything from that word to the end of the row is never drawn.
 *
 * Whole rows disappeared that way, and Japanese lines disappeared most, because
 * Apple's word spans there are whole phrases and reach a wrap on their own where
 * an English word rarely does.
 *
 * So both ends of a row are answered with the row's own edges, and anything in
 * between is held inside them.
 */
private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, inset: Float): Float {
    val left = getLineLeft(visualLine) + inset
    val right = getLineRight(visualLine) + inset
    return when {
        offset <= getLineStart(visualLine) -> left
        offset >= getLineEnd(visualLine, visibleEnd = true) -> right
        else -> (getHorizontalPosition(offset, usePrimaryDirection = true) + inset)
            .coerceIn(left, right)
    }
}

/** One piece of a line, clipped to its own width and drawn at its own height. */
private fun ContentDrawScope.sliceRisen(
    from: Float,
    top: Float,
    to: Float,
    bottom: Float,
    dy: Float,
) {
    if (to <= from) return
    clipRect(left = from, top = top, right = to, bottom = bottom) {
        translate(top = dy) { this@sliceRisen.drawContent() }
    }
}

/** Where a fractional character index sits across a visual line, in pixels. */
private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    visualLine: Int,
): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    // Row-aware at both ends: on the last character of a wrapped row the next
    // position belongs to the row below, and read straight it puts the edge
    // back at the left margin — the highlight jumped backwards a letter before
    // every wrap.
    val here = layout.xOn(index, visualLine, 0f)
    val next = layout.xOn((index + 1).coerceAtMost(lineEnd), visualLine, 0f)
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
 *
 * The boundary itself is then feathered over [WIPE_FEATHER] rather than left
 * as the cut, which needs the caller to give this an offscreen layer to erase
 * into — see [SweptLyricLine]. Only the line actually being sung carries one;
 * everywhere else the boundary is at one end of the text or the other and
 * there is nothing to soften.
 */
private fun ContentDrawScope.sweepTo(
    layout: TextLayoutResult,
    revealedChars: Float,
    feather: Boolean,
) {
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
        val cut = revealedChars < end
        val right = if (cut) {
            horizontalAt(layout, revealedChars, visualLine)
        } else {
            layout.getLineRight(visualLine)
        }
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        clipRect(
            left = layout.getLineLeft(visualLine),
            top = top,
            right = right,
            bottom = bottom,
        ) {
            this@sweepTo.drawContent()
        }
        // Only the visual line holding the boundary has an edge to soften; a
        // line revealed to its end runs into the wrap, which is not an edge.
        if (!feather || !cut) continue
        // Scoped to this line's band so the mask cannot reach the lines above
        // and below it: DstIn erases whatever the source does not cover, and
        // outside the clip there is no source at all, so they are left alone.
        // Within it the brush clamps — opaque behind the feather, gone past it.
        clipRect(top = top, bottom = bottom) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    startX = (right - WIPE_FEATHER.toPx())
                        .coerceAtLeast(layout.getLineLeft(visualLine)),
                    endX = right,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}


/**
 * The translate control, sized and lit like every other disc in the player —
 * see [CircleGlyph]. Its own composable rather than a [CircleGlyph] call
 * because it has a fourth state the others do not: a request in flight, which
 * takes the icon's place rather than sitting beside it.
 */
@Composable
private fun TranslationToggleButton(
    state: LyricsTranslationUiState,
    showingTranslation: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val active = showingTranslation || state is LyricsTranslationUiState.Loading
    val tint = when {
        !enabled || state is LyricsTranslationUiState.SameLanguage -> Color.White.copy(alpha = 0.42f)
        active -> Color.White
        else -> Color.White.copy(alpha = 0.78f)
    }
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "translateDisc",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = discAlpha))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (state is LyricsTranslationUiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = tint,
                strokeWidth = 1.7.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = stringResource(
                    if (showingTranslation) R.string.show_original_lyrics
                    else R.string.translate_lyrics,
                ),
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * A short text-material transition: the list and its playback clock stay in
 * place while a field of tiny glyph-like particles resolves into the new text.
 * Only the dedicated Canvas drawing moves, so changing language never causes a
 * second scroll, a blank frame, or a new lyrics timeline. The app's Reduce
 * animation preference collapses the whole response to an immediate swap.
 */
@Composable
private fun LyricsTranslationMotion(
    trigger: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (State<Float>?) -> Unit,
) {
    val progress = remember { Animatable(1f) }
    val foreground = rememberIsForeground()
    // Reopening the panel or returning from the background must not replay a
    // previous toggle. A new toggle cancels the previous effect automatically.
    var consumedTrigger by remember { mutableIntStateOf(trigger) }
    LaunchedEffect(trigger, reduceMotion, foreground) {
        val changed = trigger != consumedTrigger
        consumedTrigger = trigger
        if (!changed || trigger <= 0 || reduceMotion || !foreground) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = TRANSLATION_MOTION_MS, easing = LinearEasing),
            )
        }
    }

    Box(modifier = modifier) {
        // Keep the lyrics subtree completely outside the animation clock. In
        // particular, do not read progress in composition or apply a clipping
        // layer here: the panel's active line deliberately scales beyond its
        // measured bounds and its glow uses unbounded blur.
        content(progress.asState().takeIf { !reduceMotion && foreground && trigger > 0 })
    }
}

/** Glyph positions are cached at layout time; the shared clock is draw-only. */
private fun Modifier.lyricParticles(
    layout: TextLayoutResult?,
    progress: State<Float>?,
    room: Dp,
): Modifier {
    if (layout == null || progress == null) return this
    return drawWithCache {
        val text = layout.layoutInput.text.text
        val candidates = text.indices.filter { text[it].isLetterOrDigit() }
        val random = Random(text.hashCode())
        val inset = room.toPx()
        val particles = candidates.shuffled(random).take(PARTICLES_PER_VOICE).map { index ->
            val glyph = layout.getBoundingBox(index)
            TranslationParticle(
                anchor = glyph.center + Offset(inset, inset),
                drift = Offset((random.nextFloat() - 0.5f) * 12.dp.toPx(),
                    -(5f + random.nextFloat() * 11f).dp.toPx()),
                radius = (0.65f + random.nextFloat() * 0.65f).dp.toPx(),
                delay = 0.16f * index / text.length.coerceAtLeast(1),
            )
        }
        onDrawWithContent {
            drawContent()
            val value = progress.value
            if (value > 0f && value < 1f) {
                particles.forEach { particle ->
                    val t = ((value - particle.delay) / 0.84f).coerceIn(0f, 1f)
                    val envelope = sin(PI * t).toFloat()
                    val ease = 1f - (1f - t) * (1f - t)
                    val center = particle.anchor + Offset(
                        particle.drift.x * ease,
                        particle.drift.y * ease + 3.dp.toPx() * t * t,
                    )
                    // Two inexpensive circles give a soft halo without another
                    // blur layer; opacity rises and falls without a flash.
                    drawCircle(Color.White, particle.radius * 2.7f, center, alpha = envelope * 0.07f)
                    drawCircle(Color.White, particle.radius, center, alpha = envelope * 0.58f)
                }
            }
        }
    }
}

/**
 * Stands in for the lyrics while the lookup is still out.
 *
 * Without it the panel had one empty state doing two jobs: a lookup that had
 * come back with nothing and a lookup that had not come back yet both said "No
 * lyrics for this track", so every track was declared to have none for as long
 * as it took to find out that it did.
 */
@Composable
private fun LyricsSkeleton(modifier: Modifier = Modifier) {
    val sweep = rememberInfiniteTransition(label = "lyricsSkeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(SKELETON_PERIOD_MS, easing = LinearEasing),
        ),
        label = "sweep",
    )
    BoxWithConstraints(
        // No gutter of its own: the list this stands in for bleeds out to the
        // panel's full width and puts the gutter back as content padding, so
        // the words land level with the panel's own edge and so does this.
        modifier.padding(top = 40.dp),
    ) {
        // Every bar sweeps against the width of the column rather than its own,
        // so one band crosses the whole page. Measured per bar, a short row
        // lights end to end in the time a long one takes to get halfway, and
        // the block reads as a row of separate things loading separately.
        val column = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(SKELETON_BLOCK_GAP)) {
            SKELETON_BLOCKS.forEach { rows ->
                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LEADING)) {
                    rows.forEach { fraction ->
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(SKELETON_BAR)
                                .clip(RoundedCornerShape(4.dp))
                                // Read in the draw block, not the body: a
                                // pageful of these would otherwise recompose on
                                // every frame, and all any of them needs per
                                // frame is a fresh gradient.
                                .drawWithCache {
                                    val full = column.toPx()
                                    val band = full * 0.45f
                                    val startX = -band + sweep.value * (full + band * 2)
                                    val brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.26f),
                                            Color.White.copy(alpha = 0.10f),
                                        ),
                                        startX = startX,
                                        endX = startX + band,
                                    )
                                    onDrawBehind { drawRect(brush) }
                                },
                        )
                    }
                }
            }
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
    trackKey: String,
    positionMs: Long,
    /** Whether a lookup for this track is still in flight. */
    looking: Boolean,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    controlsOpen: Boolean,
    onRevealControls: () -> Unit,
    onHideControls: () -> Unit,
    translationProgress: State<Float>? = null,
    /** Reports whether the lyric list is mid-scroll, so the player above it
     * can stand down its own swipe gestures for as long as it is. */
    onScrollingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clock = rememberLyricClock(positionMs, isPlaying)

    val isSynced = remember(lines) { lines.any { it.timeMs > 0L } }
    // Only a song that actually names a second voice is laid out as one. A
    // single-voice song has every line on the left already, so splitting the
    // panel into lanes for it would just be a narrower panel.
    val duet = remember(lines) { lines.any { it.alignment == LyricAlignment.End } }

    val activeRows by remember(lines, isSynced) {
        derivedStateOf {
            if (!isSynced) emptyList() else activeLyricRows(lines, clock.longValue)
        }
    }
    // The uppermost unfinished vocal owns the scroll anchor until its end,
    // even as later rows begin their own independent highlight animations.
    val scrollLine = activeRows.firstOrNull() ?: -1
    // Where the panel is heading, which is a beat ahead of where the singing
    // is. Movement that starts on the downbeat arrives after it — the line is
    // already being sung by the time it settles, and you read it late. Started
    // during the run-up instead, the words are under your eye when they land.
    //
    // Kept apart from [scrollLine] on purpose: this leads, and the sweep must
    // not. Everything lit by the clock still goes through the real one.
    val leadLine by remember(lines, isSynced) {
        derivedStateOf {
            if (!isSynced) {
                -1
            } else {
                val now = clock.longValue
                activeLyricRows(lines, now + scrollLead(lines, now)).firstOrNull() ?: -1
            }
        }
    }
    // What the stack arranges itself around. The outgoing line starts dimming
    // as the panel leaves it rather than when its last word ends, so the dim,
    // the blur and the movement are one gesture.
    val focusLine = if (leadLine >= 0) leadLine else scrollLine
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect(onScrollingChange)
    }
    // LayoutInfo changes on every scroll frame. Observe only height here so
    // the entire lyrics list is not recomposed for every scrolling pixel.
    val viewportHeight by remember(listState) {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    var browsing by remember { mutableStateOf(false) }
    val onBottomHalfTap: () -> Unit = {
        if (!listState.isScrollInProgress) {
            onRevealControls()
        }
    }

    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val lyricsBlur by AppSettings.lyricsBlur.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    val glowing = !reduceAnimation && !reduceDynamicBlur && lyricsBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val hideControls by rememberUpdatedState(onHideControls)
    val revealControls by rememberUpdatedState(onRevealControls)
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                // Suspends the panel's own following, and nothing more. Which
                // way the drag is going is what decides the controls now — see
                // [controlsOnScroll] — and hiding them here as well meant a
                // scroll *up*, the gesture that is supposed to bring them back,
                // put them away first and then returned them.
                browsing = true
            }
        }
    }

    // Reading on hides the player; coming back up brings it out again.
    //
    // The direction is taken from the drag itself rather than from where the
    // list ends up, so it answers on the gesture rather than after it. Deltas
    // arrive a couple of pixels at a time, so they are accumulated and the
    // total is what crosses [CONTROLS_SCROLL_SLOP] — and the total resets the
    // moment the finger changes its mind, so a scroll that wanders does not
    // bank its way to the wrong answer.
    //
    // [NestedScrollSource.UserInput] is the whole guard against the panel
    // hiding the controls by itself: this list scrolls on its own every time a
    // line lands, and that is not somebody reading on.
    val controlsSlopPx = with(LocalDensity.current) { CONTROLS_SCROLL_SLOP.toPx() }
    val controlsOnScroll = remember(listState, controlsSlopPx) {
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    if (travel != 0f && (travel > 0f) != (available.y > 0f)) travel = 0f
                    travel += available.y
                    // Finger travelling up is the list going forward: reading on.
                    if (travel <= -controlsSlopPx) {
                        travel = 0f
                        hideControls()
                    } else if (travel >= controlsSlopPx) {
                        travel = 0f
                        revealControls()
                    }
                }
                return Offset.Zero
            }
        }
    }

    val currentLine by rememberUpdatedState(focusLine)
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

    LaunchedEffect(browsing, listState.isScrollInProgress) {
        if (browsing && !listState.isScrollInProgress) {
            delay(5_000)
            browsing = false
        }
    }

    // The panel's own journey, published so each row can work out how far
    // behind it should be running. Held as a plain value plus a frame clock
    // rather than an animation per row: sixty rows each with their own
    // Animatable is sixty animations to start and stop on every handover.
    var run by remember(lines) { mutableStateOf(ScrollRun(0, 0f, LYRIC_SETTLE_MS)) }
    val since = remember(lines) { mutableFloatStateOf(0f) }
    LaunchedEffect(run.id) {
        if (run.id == 0) return@LaunchedEffect
        animate(
            initialValue = 0f,
            targetValue = run.spanMs,
            animationSpec = tween(run.spanMs.toInt(), easing = LinearEasing),
        ) { value, _ -> since.floatValue = value }
    }
    // Keyed to the track, not to [lines]: toggling the translation replaces
    // every line while the reader's place in the song is unchanged, and a reset
    // here would snap the panel back to the top mid-read.
    var placed by remember(trackKey) { mutableStateOf(false) }
    // Nothing resets [browsing] off [controlsOpen] any more. It used to, so
    // that tapping the controls back resumed following — but the controls now
    // also come back by scrolling up, and clearing the flag there handed the
    // panel straight back to the song mid-gesture, scrolling the reader away
    // from the line they had gone looking for. The two timers below end a
    // browse on their own terms.
    // A newer line replaces an unfinished automatic scroll. Only a user's
    // browsing gesture should suspend following, not our own animation.
    LaunchedEffect(focusLine, browsing, controlsOpen) {
        if (isSynced && !browsing &&
            focusLine >= 0 && focusLine in lines.indices
        ) {
            snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            // Keep the same top anchor whether the playback controls are visible or hidden.
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusLine }
            when {
                !placed -> {
                    listState.scrollToItem(focusLine, scrollOffset = 0)
                    placed = true
                }
                // Already on screen, which is the ordinary case of handing over
                // to the next line: its distance is known, so the move can be
                // given the run-up's own duration and curve instead of the
                // list's default spring.
                visible != null -> {
                    val span = scrollLead(lines, clock.longValue).toInt()
                    run = ScrollRun(run.id + 1, visible.offset.toFloat(), span)
                    // The same curve the rows catch up on. Two different
                    // curves and a row with no delay at all still trails the
                    // list it is sitting in, which is most of the way to
                    // looking like the panel cannot keep up with itself.
                    listState.animateScrollBy(
                        value = visible.offset.toFloat(),
                        animationSpec = tween(durationMillis = span, easing = LYRIC_EASING),
                    )
                }
                // Somewhere off screen — after a seek, or a long instrumental
                // scrolled past. How far is not known without laying the rows
                // out, so this hands back to the list's own staged scroll.
                else -> listState.animateScrollToItem(focusLine, scrollOffset = 0)
            }
        }
    }

    if (lines.isEmpty()) {
        val empty = modifier.revealLyricsControlsOnTap(!controlsOpen, onBottomHalfTap)
        // "None" is a finding, and it is only worth reporting once the lookup
        // has actually come back with it.
        if (looking) {
            LyricsSkeleton(empty)
        } else {
            Box(empty, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_lyrics_for_track),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .bleedHorizontally(PLAYER_GUTTER)
            .nestedScroll(controlsOnScroll)
            .nestedScroll(keepScroll)
            // Browsing leaves taps to each lyric row's seek action throughout the list.
            .revealLyricsControlsOnTap(!controlsOpen, onBottomHalfTap)
            .fadingEdges(),
        // Each row carries GLOW_ROOM of its own inset for the halo, so the
        // list hands that much back — otherwise the lines would sit a glow's
        // width further apart and further in than they used to.
        contentPadding = PaddingValues(
            top = 40.dp - GLOW_ROOM,
            bottom = with(LocalDensity.current) { viewportHeight.toDp() } * 0.8f,
            start = PLAYER_GUTTER - GLOW_ROOM,
            end = PLAYER_GUTTER - GLOW_ROOM,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            if (!isSynced && Genius.isSectionHeader(line.text)) {
                val sectionTitle = line.text.removePrefix("[").removeSuffix("]").trim()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 6.dp else 24.dp, bottom = 8.dp)
                        .padding(horizontal = GLOW_ROOM),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = sectionTitle.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
                return@itemsIndexed
            }

            if (!isSynced && line.isGap) {
                Spacer(Modifier.height(14.dp))
                return@itemsIndexed
            }

            // Off the line being sung, not off the line the panel is heading
            // for. Brightness is what says "these are the words right now", so
            // it cannot run ahead of them — on a source with no word timings
            // there is no sweep behind it to keep the sung line lit, and it
            // read as dim while it was still being sung.
            val offset = if (scrollLine < 0) 0 else index - scrollLine
            val distance = abs(offset)
            val isActive = isSynced && index in activeRows
            // Symmetric either side of the playing line, and shallow: the two
            // rows around it stay readable so you can follow back over what was
            // just sung as well as ahead, and everything past that recedes to
            // the same floor rather than fading to nothing.
            val step = distance.coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)
            val blur by animateDpAsState(
                targetValue = when {
                    !isSynced || reduceDynamicBlur || !lyricsBlur || browsing || isActive -> 0.dp
                    else -> LINE_FALLOFF_BLUR[step]
                },
                animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
                label = "lyricBlur",
            )
            val lineAlpha by animateFloatAsState(
                targetValue = when {
                    !isSynced -> 0.95f
                    isActive -> 1f
                    // Reading by hand is not following along: the stack flattens
                    // to one brightness so no row is being pointed at.
                    browsing -> BROWSING_ALPHA
                    else -> LINE_FALLOFF_ALPHA[step]
                },
                animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
                label = "lyricAlpha",
            )
            if (line.isGap) {
                // A break counts itself out rather than being marked: three
                // dots lighting in turn across the interlude, so a long one
                // reads as time running down instead of a symbol parked on
                // screen waiting for the singing to come back.
                val until = lines.getOrNull(index + 1)?.timeMs ?: line.endMs
                // The row itself opens and closes with the break, so the list
                // carries no dead space through the verses either side of it —
                // which is also what stops the panel scrolling past a hole to
                // reach the next line that is actually sung.
                val swell by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (isActive) 400 else 350,
                        easing = LYRIC_EASING,
                    ),
                    label = "gapSwell",
                )
                val instrumental = stringResource(R.string.instrumental)
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .height((GAP_ROW_HEIGHT + GAP_ROW_SPACING) * swell)
                        .clipToBounds(),
                ) {
                Box(
                    modifier = Modifier
                        .blur(blur, BlurredEdgeTreatment.Unbounded)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = isSynced) { onSeekToLine(line.timeMs) }
                        // Matches the inset every sung line carries, so the
                        // rhythm of the list doesn't break at a break.
                        .padding(GLOW_ROOM)
                        .size(
                            width = GAP_DOT_SIZE * 3 + GAP_DOT_GAP * 2,
                            height = GAP_DOT_SIZE,
                        )
                        .graphicsLayer {
                            val grow = GAP_REST_SCALE + (1f - GAP_REST_SCALE) * swell
                            scaleX = grow
                            scaleY = grow
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            alpha = lineAlpha * swell
                        }
                        .drawBehind {
                            // Read here rather than in composition: the fill
                            // moves every frame, and this way a break costs a
                            // redraw of three circles, not a recomposition.
                            val span = (until - line.timeMs).coerceAtLeast(1L)
                            val through = ((clock.longValue - line.timeMs).toFloat() / span)
                                .coerceIn(0f, 1f)
                            val radius = GAP_DOT_SIZE.toPx() / 2f
                            val stride = (GAP_DOT_SIZE + GAP_DOT_GAP).toPx()
                            repeat(GAP_DOTS) { dot ->
                                // Each dot owns its share of the break and
                                // fills across it, so they light left to right.
                                val lit = (through * GAP_DOTS - dot).coerceIn(0f, 1f)
                                drawCircle(
                                    color = Color.White.copy(
                                        alpha = GAP_DOT_REST + (1f - GAP_DOT_REST) * lit,
                                    ),
                                    radius = radius,
                                    center = Offset(radius + dot * stride, size.height / 2f),
                                )
                            }
                        }
                        .semantics { contentDescription = instrumental },
                )
                }
            } else {
                val alignEnd = duet && line.alignment == LyricAlignment.End
                val style = if (isSynced) {
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                    )
                } else {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                    )
                }
                // The stack sits fractionally back and the playing line comes
                // forward to meet you, rather than the playing line swelling
                // past the others — a smaller move, and one that doesn't push
                // the type around the line it hands over to.
                //
                // Anchored to the left edge, so the words don't slide sideways
                // under the highlight; scaling about the centre would fight the
                // sweep. A row under a finger dips, the way a button does.
                // Behind the panel's focus, so the words close up to full
                // brightness as it leaves rather than when the last syllable
                // lands — the dim, the blur and the movement together.
                val sung = offset < 0
                // Rows behind the one being scrolled to are the ones that
                // fan out; the ones it is moving away from arrive together.
                val behind = if (run.delta >= 0f) index - focusLine else focusLine - index
                val staggerDelay = behind.coerceIn(0, STAGGER_STEPS) *
                    STAGGER_FRACTION * run.durationMs
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = when {
                        pressed -> PRESSED_SCALE
                        isActive -> 1f
                        else -> INACTIVE_SCALE
                    },
                    animationSpec = tween(
                        durationMillis = if (pressed) 120 else LYRIC_SETTLE_MS,
                        easing = LYRIC_EASING,
                    ),
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
                // No width held back for the swell any more: nothing draws past
                // its own bounds now that the playing line tops out at 1, so the
                // text gets the full column and wraps where the panel does.
                val shape = Modifier
                    .fillMaxWidth()
                    // The lane the other voice sings in, kept clear. Applied
                    // before the layer below so the row scales about the edge
                    // it is actually written from.
                    .padding(
                        start = if (duet && alignEnd) DUET_LANE else 0.dp,
                        end = if (duet && !alignEnd) DUET_LANE else 0.dp,
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                        alpha = lineAlpha
                        // Held back against the list's own movement: the list
                        // has already taken this row part of the way, so giving
                        // back what it has not earned yet is what leaves it
                        // trailing. One curve for both, so a row with no delay
                        // sits exactly still against the list and the rows that
                        // do have one are the only thing that moves.
                        //
                        // Rows with nothing to catch up on never read the clock
                        // at all, so a handover only invalidates the handful of
                        // layers that are actually fanning out.
                        translationY = if (staggerDelay <= 0f) {
                            0f
                        } else {
                            val elapsed = since.floatValue
                            run.delta * (
                                LYRIC_EASING.transform(
                                    (elapsed / run.durationMs).coerceIn(0f, 1f),
                                ) - LYRIC_EASING.transform(
                                    ((elapsed - staggerDelay) / run.durationMs)
                                        .coerceIn(0f, 1f),
                                )
                                )
                        }
                    }
                    .blur(blur, BlurredEdgeTreatment.Unbounded)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        enabled = isSynced,
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                    ) { onSeekToLine(line.timeMs) }
                // Lead and answering vocal are one row: they are one line of
                // the song, they scale and dim together, and tapping either
                // seeks to the same place.
                AnimatedContent(
                    targetState = line,
                    transitionSpec = {
                        val duration = if (reduceAnimation) 0 else 380
                        val fadeSpec = if (reduceAnimation) snap() else tween<Float>(duration, easing = FastOutSlowInEasing)
                        (fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)).using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ ->
                                    if (reduceAnimation) snap()
                                    else tween(duration, easing = FastOutSlowInEasing)
                                },
                            )
                        )
                    },
                    label = "lyricsTranslationLine",
                    modifier = shape,
                ) { renderedLine ->
                    Column {
                        PanelVoice(
                            line = renderedLine,
                            clock = clock,
                            style = style,
                            isActive = isActive,
                            sung = sung,
                            synced = isSynced,
                            browsing = browsing,
                            glowAlpha = glow,
                            room = GLOW_ROOM,
                            alignEnd = alignEnd,
                            // Only the rows actually in front of the reader get the
                            // particle pass. Sixty rows' worth of glyph boxes is a
                            // layout walk per frame for text nobody is looking at.
                            translationProgress = translationProgress.takeIf {
                                if (isSynced) abs(index - focusLine) <= 1 else index < 4
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        renderedLine.background?.let { backing ->
                            PanelVoice(
                                line = backing.withoutBracketPunctuation(),
                                clock = clock,
                                style = style.copy(
                                    fontSize = BACKING_FONT_SIZE,
                                    lineHeight = BACKING_LINE_HEIGHT,
                                ),
                                isActive = isActive,
                                sung = sung,
                                synced = isSynced,
                                browsing = browsing,
                                // No bloom on the second voice. The glow marks
                                // what is being sung *at you*; putting it on both
                                // makes the row read as two equal lines, which is
                                // the thing this split exists to stop.
                                glowAlpha = 0f,
                                room = 0.dp,
                                alignEnd = alignEnd,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // No top inset: the lead's own bottom room is
                                    // the gap, which leaves the two voices closer
                                    // to each other than to the rows either side.
                                    .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                                    .graphicsLayer { alpha = BACKING_ALPHA },
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * One voice of a row in [LyricsPanel] — the lead, or the answering line drawn
 * under it.
 *
 * Both go through the same sweep. A backing vocal carries its own word
 * timings, so it lights up on its own clock rather than borrowing the lead's:
 * that is the whole point of splitting it out, and it is why the bracket no
 * longer gets cut off when the next line's stamp arrives mid-phrase.
 */
@Composable
private fun PanelVoice(
    line: LyricLine,
    clock: MutableLongState,
    style: TextStyle,
    isActive: Boolean,
    /** Whether the panel has already left this line behind. */
    sung: Boolean,
    /** Whether the source stamps its lines at all. */
    synced: Boolean,
    browsing: Boolean,
    glowAlpha: Float,
    room: Dp,
    /** Whether this line is one of the right-hand voice's; see [LyricAlignment]. */
    alignEnd: Boolean,
    translationProgress: State<Float>? = null,
    modifier: Modifier = Modifier,
) {
    if (line.isWordSynced && !browsing) {
        // Every word-synced line goes through the sweep, not just the playing
        // one — a line that has already been sung is fully revealed and one
        // still to come is not, which falls out of the same arithmetic.
        //
        // Running it only on the active line meant swapping this composable
        // for a plain Text the instant a line handed over, and the two
        // disagreed about the brightness of the words: the tail of the line
        // popped up to meet the rest of it in a single frame. Animating the
        // tail instead lets a finished line close up as it dims away.
        val tail by animateFloatAsState(
            targetValue = if (sung) 1f else UNSUNG_ALPHA,
            label = "lyricTail",
        )
        SweptLyricLine(
            line = line,
            clock = clock,
            style = style,
            dimAlpha = tail,
            modifier = modifier,
            glowAlpha = glowAlpha,
            glowRoom = room,
            feather = isActive,
            alignEnd = alignEnd,
            translationProgress = translationProgress,
        )
    } else if (line.isWordSynced) {
        // Browsing: keep the sweep so sung lines stay fully lit and unsung
        // ones stay dim, but skip the bloom — it is a playback flourish, not
        // a browsing aid.  Non-active lines get the same dim tail as when we
        // are not browsing; the active line stays at full brightness.
        val tail by animateFloatAsState(
            targetValue = if (sung) 1f else UNSUNG_ALPHA,
            label = "lyricTail",
        )
        SweptLyricLine(
            line = line,
            clock = clock,
            style = style,
            dimAlpha = tail,
            modifier = modifier,
            glowAlpha = 0f,
            glowRoom = room,
            alignEnd = alignEnd,
            translationProgress = translationProgress,
        )
    } else {
        // No word timings, so there is no sweep to light the words as they are
        // sung: the line lights whole, the moment it starts.
        //
        // It still has to hold itself back until then. The parent's falloff
        // alone left a line not yet sung reading brighter here than the same
        // line does on a word-synced source, where the unsung words sit at
        // [UNSUNG_ALPHA] underneath it — the two have to agree about what "not
        // yet" looks like, or changing provider changes the panel rather than
        // the words. Lyrics with no timing at all are all "now", and stay lit.
        val lit by animateFloatAsState(
            targetValue = if (!synced || sung || isActive) 1f else UNSUNG_ALPHA,
            label = "lyricLit",
        )
        var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = lit),
            onTextLayout = { layout = it },
            modifier = modifier.lyricParticles(layout, translationProgress, room).padding(room),
        )
    }
}

/**
 * The answering vocal without the parentheses every text-only source wraps it
 * in — see [withBackgroundVocals]. Apple Music draws its own equivalent line
 * bare, and the brackets were only ever there to mark the split before there
 * was a row of its own to draw it on.
 *
 * The LRC writer still gets the line with its brackets: that punctuation is
 * what the provider published, so a downloaded file keeps it. This is a
 * display-only trim, done here rather than in the data layer, and applied to
 * the words too, not just [LyricLine.text] — [SweptLyricLine] measures the
 * words against the text it draws, and a sweep reading "(echoed" against a
 * line reading "echoed" would search for a substring that is no longer there.
 */
private fun LyricLine.withoutBracketPunctuation(): LyricLine = copy(
    text = text.stripParens(),
    words = words.mapNotNull { word ->
        word.text.stripParens().takeIf { it.isNotEmpty() }?.let { word.copy(text = it) }
    },
)

private fun String.stripParens(): String = replace("(", "").replace(")", "").trim()


/**
 * The single lyric line above the scrubber.
 *
 * Transitions between lines use [AnimatedContent] with vertical slide and
 * fade, respecting [AppSettings.reduceAnimation].
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
    val isSynced = remember(lines) { lines.any { it.timeMs > 0L } }
    if (!isSynced) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.open_lyrics),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = BitChordIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
        return
    }

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
    val introLines = stringArrayResource(R.array.lyrics_intro_lines)
    // `stringArrayResource` may return a new array on every recomposition.
    // Keying this selection to that array made the intro copy change whenever
    // the playback clock recomposed the strip. Pick it once for this track.
    val introLine = remember(trackKey) { introLines.random() }
    // The strip is one line and switches the moment the next one is due, so
    // the answering vocal — where there is one — has nowhere to go: showing
    // it would mean either cutting it short when the next line arrives or
    // holding the strip back and leaving a gap before the next line's own
    // words appear. [LyricsPanel] has the room to draw it properly; here it
    // is simply left off, same as before this line had a bracket in it.
    val text = when {
        intro -> introLine
        instrumental -> stringResource(R.string.instrumental)
        else -> current.text
    }

    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
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
        AnimatedContent(
            targetState = Triple(index, current, text),
            transitionSpec = {
                val duration = if (reduceAnimation) 0 else 340
                if (reduceAnimation) {
                    (fadeIn(snap()) togetherWith fadeOut(snap())).using(
                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() })
                    )
                } else {
                    (fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { height -> (height * 0.35f).toInt() })
                        .togetherWith(
                            fadeOut(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                                slideOutVertically(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { height -> -(height * 0.35f).toInt() }
                        ).using(
                            SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(duration, easing = FastOutSlowInEasing) })
                        )
                }
            },
            label = "currentLyricTransition",
            modifier = Modifier.weight(1f, fill = false),
        ) { (_, lineItem, lineText) ->
            val itemInstrumental = lineItem == null || lineItem.isGap
            val swept = lineItem?.takeIf { !itemInstrumental && it.isWordSynced }
            if (swept != null) {
                SweptLyricLine(
                    line = swept,
                    clock = clock,
                    style = MaterialTheme.typography.titleMedium,
                    dimAlpha = UNSUNG_ALPHA_STRIP,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    rise = false,
                )
            } else {
                Text(
                    text = lineText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (itemInstrumental) Color.White.copy(alpha = 0.5f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        text = stringResource(R.string.lyrics_not_available),
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
private fun LyricsLoadingLine(text: String, modifier: Modifier = Modifier) {
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
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
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
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = tween(durationMillis = 180),
            label = "playerMenuGlyph",
        ) { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * Transport / bottom glyphs. The circular clip belongs on the touch target,
 * never on the [Icon] — clipping the icon itself shaves the corners off wide
 * glyphs like fast-forward and the queue list.
 */
@Composable
private fun TransportGlyph(
    @DrawableRes icon: Int,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    touchSize: androidx.compose.ui.unit.Dp = size,
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    // Faded rather than hidden: the row keeps its shape at the ends of a queue.
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(touchSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(size),
        )
    }
}

private val BOTTOM_ACTION_SIZE = 44.dp

/**
 * One half of the output capsule — wider than it is tall, so the capsule reads
 * as a capsule rather than as two circles that have been pushed together.
 */
// The count reserve is kept on both halves, so entering a party never makes
// the capsule lopsided or shifts the queue control beside it.
private val PILL_SEGMENT_WIDTH = 64.dp

/**
 * Optical sizes, not equal ones.
 *
 * Headphones is a tall, narrow glyph and Person a taller, narrower one, so
 * drawn at the same nominal size the second reads as the bigger of the two.
 * These are the numbers at which they look like a matched pair.
 */
private val PILL_HEADPHONES_SIZE = 23.dp
private val PILL_PARTY_SIZE = 22.dp

/** What a segment's glyph is drawn at when it has no optical quirk to correct. */
private val PILL_ICON_SIZE = 24.dp

/** How wide a capsule of [segments] comes out, dividers included. */
private fun pillWidth(segments: Int): Dp =
    PILL_SEGMENT_WIDTH * segments + 1.dp * (segments - 1)

/**
 * A row of controls joined into one capsule.
 *
 * The join is a hairline rather than a gap, which is what makes several
 * controls read as a single object — the shape the player uses for a set of
 * choices that all answer the same question. There are two: where the sound is
 * going, and how the queue is played.
 */
@Composable
private fun Pill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

@Composable
private fun OutputPartyPill(
    onOutput: () -> Unit,
    onParty: () -> Unit,
    onChangeTrack: (() -> Unit)? = null,
    isAudioVersion: Boolean = false,
    audioVersionSwitching: Boolean = false,
    showChangeTrack: Boolean = false,
) {
    val badge = rememberPartyBadge()
    Pill {
        PillSegment(
            icon = Icons.Rounded.Headphones,
            iconSize = PILL_HEADPHONES_SIZE,
            contentDescription = stringResource(R.string.audio_output),
            onClick = onOutput,
        )
        AnimatedVisibility(
            visible = showChangeTrack && onChangeTrack != null,
            enter = fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.CenterHorizontally,
                    clip = true,
                ),
            exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                shrinkHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    shrinkTowards = Alignment.CenterHorizontally,
                    clip = true,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillDivider()
                PillSegment(
                    icon = if (isAudioVersion) BitChordIcons.MusicNote else Icons.Rounded.Videocam,
                    iconSize = 21.dp,
                    contentDescription = stringResource(
                        if (isAudioVersion) R.string.revert_to_original else R.string.convert_to_audio
                    ),
                    onClick = onChangeTrack ?: {},
                    highlighted = isAudioVersion,
                    loading = false,
                )
            }
        }
        PillDivider()
        PillSegment(
            // Person rather than Groups: the three-person glyph is drawn half
            // the height of Headphones and wider than the segment holding it,
            // so the two halves of the capsule never looked like a pair.
            icon = Icons.Rounded.Person,
            iconSize = PILL_PARTY_SIZE,
            // The count is here and nowhere else: spoken, it is the whole
            // point of the control; drawn, it would cost the capsule its
            // symmetry for something the caption below already implies.
            contentDescription = if (badge.inParty) {
                stringResource(R.string.listen_together_open_count, badge.members)
            } else {
                stringResource(R.string.listen_together_open)
            },
            onClick = onParty,
            highlighted = badge.inParty,
            trailingLabel = badge.members.takeIf { badge.inParty }?.toString(),
        )
    }
}

/**
 * One control inside a [Pill] — [BottomGlyph]'s twin, squared off.
 *
 * Same behaviour down to the tap window, and deliberately not the same
 * composable: a glyph's highlight is a circle sized to itself, and a segment's
 * has to fill its share of the capsule edge to edge or the join stops reading
 * as one.
 */
@Composable
private fun PillSegment(
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconSize: Dp = PILL_ICON_SIZE,
    label: String? = null,
    trailingLabel: String? = null,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    loading: Boolean = false,
    /** See [BottomGlyph], where the same window means the same thing. */
    tapWindowMs: Long = 0L,
) {
    val haptics = rememberHaptics()
    val lastTap = remember { mutableLongStateOf(-tapWindowMs) }
    Box(
        modifier = Modifier
            .width(PILL_SEGMENT_WIDTH)
            .height(BOTTOM_ACTION_SIZE)
           
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !loading,
            ) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTap.longValue >= tapWindowMs) {
                    lastTap.longValue = now
                    haptics.play(haptic)
                    onClick()
                }
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f)
        Crossfade(
            targetState = loading,
            animationSpec = tween(durationMillis = 200),
            label = "pillSegmentLoading",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else if (icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Crossfade(
                        targetState = icon,
                        animationSpec = tween(durationMillis = 200),
                        label = "pillSegmentIcon",
                    ) { currentIcon ->
                        Icon(
                            imageVector = currentIcon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                    if (trailingLabel != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = trailingLabel,
                            color = tint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (label != null) {
                Text(
                    text = label,
                    color = tint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * The line under the transport: normally the output, and the party's name
 * whenever there is one.
 *
 * A party overrides the output rather than sitting beside it because the two
 * are not the same kind of fact. "Kushagra's Phone" answers which speaker in
 * this room; once there are four devices playing the same song, the room is no
 * longer what the listener is checking. The tap follows the label — whichever
 * one is on screen is the thing it opens.
 */
@Composable
private fun OutputCaption(
    accountName: String?,
    onOpenOutput: () -> Unit,
    onOpenParty: () -> Unit,
) {
    val badge = rememberPartyBadge()
    val outputName = rememberAudioOutputName(accountName)
    // The host's first name, exactly as the output line already shortens the
    // account's — "Kushagra's Jam" alongside "Kushagra's Phone".
    val jamName = badge.hostFirstName
        ?.let { stringResource(R.string.listen_together_jam, it) }
        ?: stringResource(R.string.listen_together_jam_unnamed)
    Text(
        text = if (badge.inParty) jamName else outputName,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .clickable { if (badge.inParty) onOpenParty() else onOpenOutput() },
    )
}

/** The three fields of a party the player draws — see [OutputPartyPill]. */
private data class PartyBadge(
    val inParty: Boolean,
    val members: Int,
    val hostFirstName: String?,
)

private fun ListenTogether.State.badge(): PartyBadge = PartyBadge(
    inParty = inParty,
    members = members.size,
    hostFirstName = members.firstOrNull(PartyMember::isHost)
        ?.displayName
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() },
)

/**
 * [PartyBadge] as it changes, and only when it actually does.
 *
 * `distinctUntilChanged` is the point of this: the party's own state is
 * replaced on every heartbeat and every position report, none of which move any
 * of these three fields.
 */
@Composable
private fun rememberPartyBadge(): PartyBadge {
    val badges = remember {
        ListenTogether.state.map { it.badge() }.distinctUntilChanged()
    }
    return badges
        .collectAsStateWithLifecycle(initialValue = ListenTogether.state.value.badge())
        .value
}

@Composable
private fun BottomGlyph(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    label: String? = null,
    /**
     * Shortest gap between taps that both reach [onClick]. A tap inside the
     * window of the last one is dropped whole — haptic included, so a swallowed
     * tap doesn't buzz as though something happened. The default lets every tap
     * through: only the glyphs whose work is too heavy to repeat at finger speed
     * ask for a window.
     */
    tapWindowMs: Long = 0L,
) {
    val haptics = rememberHaptics()
    // Read only from the click handler, never during composition, so writing it
    // costs no recomposition. Starts a full window in the past so the first tap
    // is never the one that gets swallowed.
    val lastTap = remember { mutableLongStateOf(-tapWindowMs) }
    Box(
        modifier = Modifier
            .size(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(
                if (highlighted) Color.White.copy(alpha = 0.20f) else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTap.longValue >= tapWindowMs) {
                    lastTap.longValue = now
                    haptics.play(haptic)
                    onClick()
                }
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
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

/** How fast the title/artist marquee crawls — unhurried, not a ticker. */
private const val MARQUEE_DP_PER_SEC = 26f

/** Clear air between the tail of the line and the copy chasing it round. */
private val MARQUEE_GAP = 48.dp

/** How long a line sits back at its start before the next pass — the "5 seconds" rest. */
private const val MARQUEE_REST_MS = 5_000L

/** Artist's head start is ceded to the title when both are scrolling, so they don't start as one block. */
private const val MARQUEE_ARTIST_STAGGER_MS = 3_000L

/**
 * A single line of text that scrolls in place, only when it is too long for
 * [modifier]'s width to show in full.
 *
 * Idle text never animates — the scroll only kicks in once measurement proves
 * an ellipsis would otherwise be needed. When it does, the line is drawn twice
 * with [MARQUEE_GAP] between the copies and the pair is crawled leftwards by
 * exactly one copy-plus-gap: the trailing copy chases the leading one in from
 * the right and lands precisely where it started, so the offset reset at the
 * end of the pass falls under a copy already in position and cannot be seen.
 * The line therefore only ever travels one way — right to left, round and back
 * to its resting place — rather than bouncing back the way it came.
 *
 * A pass is: wait [startDelayMillis] (used to stagger the artist line behind
 * the title), crawl one full loop, then rest [MARQUEE_REST_MS] at the start
 * before going again. [onOverflowChange] reports whether this line is scrolling
 * at all, so a sibling line can decide whether it needs to stagger behind it.
 *
 * With [enabled] false the line is a plain ellipsised one — no copies, no
 * animation, nothing left running off screen.
 */
@Composable
private fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startDelayMillis: Long = 0L,
    leading: (@Composable () -> Unit)? = null,
    onOverflowChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        if (!enabled) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            return@Row
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false).clipToBounds()) {
            val maxWidthPx = constraints.maxWidth
            val layout = remember(text, style, maxWidthPx) {
                textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
            }
            val overflowing = layout.size.width > maxWidthPx
            LaunchedEffect(overflowing) { onOverflowChange(overflowing) }

            // One whole copy plus the gap behind it: that is the distance at
            // which the second copy is sitting exactly where the first was.
            val travelPx = if (overflowing) {
                layout.size.width + with(density) { MARQUEE_GAP.roundToPx() }
            } else {
                0
            }

            val offsetX = remember { Animatable(0f) }
            LaunchedEffect(text, travelPx, startDelayMillis) {
                offsetX.snapTo(0f)
                if (travelPx <= 0) return@LaunchedEffect
                val pxPerMs = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() } / 1000f
                val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(400)
                delay(startDelayMillis)
                while (true) {
                    offsetX.animateTo(-travelPx.toFloat(), tween(scrollMs, easing = LinearEasing))
                    // Invisible: the trailing copy has arrived at the leading
                    // one's starting mark, so the line is already back where
                    // this puts it.
                    offsetX.snapTo(0f)
                    delay(MARQUEE_REST_MS)
                }
            }

            Row(
                // Measured unbounded so the copies actually lay out at their
                // full width, wider than the clipped box around them — bounded,
                // the text is truncated during its own measurement and sliding
                // it sideways just moves an already-cut string.
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarqueeLine(text = text, style = style, color = color)
                if (overflowing) {
                    Spacer(Modifier.width(MARQUEE_GAP))
                    MarqueeLine(text = text, style = style, color = color)
                }
            }
        }
    }
}

/** One copy of a marquee's line, laid out at its full width rather than clipped. */
@Composable
private fun MarqueeLine(text: String, style: TextStyle, color: Color) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

/** The small "E" pill for explicit tracks, kept outside the scrolling text. */
@Composable
private fun ExplicitBadge(color: Color) {
    Text(
        text = "E",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp),
    )
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
    onScrollingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect(onScrollingChange)
    }
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    // Where AutoPlay's tracks start. The queue is kept with them last, so this
    // is one boundary rather than a category to test row by row.
    val autoplayStart = remember(queue, currentIndex) {
        autoplaySectionStart(queue.map { it.fromAutoplay }, currentIndex)
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

    // Open on what's playing, not at the top of a long queue. The heading sits
    // between the two sections, so it counts as a row once it's above this one.
    //
    // Never mid-drag, though. A track ending while a row is held would jump the
    // list out from under the finger, and the jump takes the list's scroll off
    // the edge auto-scroll below — which would leave the rest of that drag
    // unable to scroll at all. Reordering is also the one time the user is
    // certainly looking somewhere other than at the current track.
    LaunchedEffect(currentIndex) {
        val holding = manualDrag.draggedKey != null || autoplayDrag.draggedKey != null
        if (!holding && currentIndex in queue.indices) {
            listState.scrollToItem(currentIndex + if (currentIndex >= autoplayStart) 1 else 0)
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.clear),
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
                        .graphicsLayer { translationY = if (dragging) manualDrag.renderOffset else 0f }
                        // The dragged row follows the finger, so it is the one
                        // row that must not also be animating to a slot. Its
                        // neighbours skip the animation too, for as long as
                        // *anything* in the section is being dragged — see the
                        // note on [manualDrag] below for why.
                        .then(if (manualDrag.draggedKey != null) Modifier else Modifier.animateItem()),
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
                                text = stringResource(R.string.autoplay),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                            Text(
                                text = if (autoplayStart < queue.size) {
                                    stringResource(R.string.autoplay_queue_description)
                                } else {
                                    stringResource(R.string.autoplay_empty_description)
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
                        .graphicsLayer { translationY = if (dragging) autoplayDrag.renderOffset else 0f }
                        .then(if (autoplayDrag.draggedKey != null) Modifier else Modifier.animateItem()),
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
 * How far in from either end of the queue a held row starts scrolling the list,
 * and how fast it scrolls once it is all the way at the edge.
 *
 * The zone is a shade deeper than the 28.dp the list fades out over, so the
 * list is already moving by the time the row begins to disappear into the fade
 * rather than only once it has. The speed at the edge is about six rows a
 * second: quick enough to cross a long queue without waiting on it, slow
 * enough to still read the titles going past and stop on the right one.
 */
private val QUEUE_EDGE_SCROLL_ZONE = 40.dp
private val QUEUE_EDGE_SCROLL_SPEED = 340.dp

/**
 * The pace, in pixels a second, to scroll a list at while a row occupying
 * [top] to [bottom] is held in a viewport spanning [viewportStart] to
 * [viewportEnd] — negative towards the start of the list, positive towards its
 * end, and zero while the row is clear of both edges.
 *
 * Ramped by how far into the [zone] the row has reached, so how fast the queue
 * goes by stays the user's to choose — but from a fifth of [speed] rather than
 * from nothing, since a row just inside the zone should visibly move the list
 * instead of creeping a pixel a second until it is pushed further. A viewport
 * too short to hold the row clear of both edges at once scrolls neither way,
 * rather than picking one arbitrarily and running away with it.
 */
internal fun edgeScrollSpeed(
    top: Float,
    bottom: Float,
    viewportStart: Int,
    viewportEnd: Int,
    zone: Float,
    speed: Float,
): Float {
    if (zone <= 0f) return 0f
    val intoStart = (viewportStart + zone) - top
    val intoEnd = bottom - (viewportEnd - zone)
    val reach = when {
        intoStart > 0f && intoEnd <= 0f -> -intoStart
        intoEnd > 0f && intoStart <= 0f -> intoEnd
        else -> return 0f
    }
    val ramp = speed * (0.2f + 0.8f * (abs(reach) / zone).coerceAtMost(1f))
    return if (reach < 0f) -ramp else ramp
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
 * it changes with every swap.
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
    with(LocalDensity.current) {
        state.edgeZone = QUEUE_EDGE_SCROLL_ZONE.toPx()
        state.edgeSpeed = QUEUE_EDGE_SCROLL_SPEED.toPx()
    }

    // Held near either end of the list, the row scrolls it. A track can be
    // moved across a queue many screens long without letting go, where before
    // the only way down was to drop the row at the edge, scroll by hand and
    // pick it up again, once per screenful.
    //
    // What the scroll moves is the list, not the finger, and
    // [QueueDragState.onScrolled] says exactly that: the held position stays
    // where it is and the new layout is read back against it. The row sits
    // still on screen while the rows above or below slide past it, and swaps
    // through them on the same terms it would if the finger had covered the
    // distance itself.
    val direction = state.autoScrollDir
    LaunchedEffect(state, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // A frame the system dropped, paid back in full, lands as a
                // lurch — so it isn't.
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                val scrolled = scrollBy(state.autoScrollSpeed * seconds)
                // Nowhere left to scroll, or the row has left the edge and the
                // speed has gone to nothing. Let the list's scroll go rather
                // than spin on it holding the lock: the row can still be
                // dragged the rest of the way by hand, and coming back to an
                // edge starts this over.
                if (scrolled == 0f) break
                state.onScrolled()
            }
        }
    }
    return state
}

/**
 * Where a held row is being held, what it may do from there, and the moves it
 * has sent to the player on the way.
 *
 * The whole thing turns on one number: [heldCenter], where the row's centre is
 * being held, in the LazyColumn's own viewport pixels. The finger moves it and
 * nothing else does — not a scroll, not a swap, not a relayout. Everything
 * drawn or decided is then read back off the live layout against it: the row
 * is drawn at whatever its slot currently is plus the distance to
 * [heldCenter], and it trades places with whichever neighbour's slot
 * [heldCenter] has reached into.
 *
 * Tracking where the row is rather than how far it has come is what lets the
 * drag survive the list moving underneath it. The offset this replaces was
 * kept by hand — corrected on every scrolled pixel and again on every swap —
 * and held together only for as long as it was told about every last thing
 * that moved the list. It wasn't: LazyColumn re-anchors its own scroll
 * position when the row it measures from is reordered elsewhere (see
 * [swapTarget]), and one such jump left the offset a full row wrong, the row
 * drawn a row off the finger and its slot pushed clean out of the viewport.
 * Read fresh off the layout there is nothing left to be wrong — wherever the
 * list has ended up, the row is still under the finger.
 */
private class QueueDragState(private val listState: LazyListState) {
    var lazyRange: IntRange = IntRange.EMPTY
    var lazyOffset: Int = 0
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** [QUEUE_EDGE_SCROLL_ZONE] and [QUEUE_EDGE_SCROLL_SPEED], in pixels. */
    var edgeZone: Float = 0f
    var edgeSpeed: Float = 0f

    /** LazyColumn key of the row being dragged; null at rest. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    /**
     * How far from its own slot to draw the held row, in pixels.
     *
     * Not simply the distance to [heldCenter]: a queue longer than the screen
     * has nowhere to show a row above its first slot or below its last, so a
     * finger held past either end was drawing the row off the list into
     * nothing. Kept inside the viewport it sits at whichever edge it reached
     * and stays visible there while the auto-scroll carries the list under it.
     */
    var renderOffset by mutableFloatStateOf(0f)
        private set

    /**
     * Which way the list is scrolling itself under the held row: -1 towards the
     * start of the queue, 1 towards its end, 0 not at all. State, because this
     * is what starts and stops the loop that does the scrolling.
     */
    var autoScrollDir by mutableIntStateOf(0)
        private set

    /**
     * How fast it is doing so, signed, in pixels a second — and deliberately
     * *not* state. It changes with every pixel of drag travel, and only the
     * loop reads it, once a frame; as state it would recompose the whole queue
     * on every touch event to tell the composition something it has no use for.
     */
    var autoScrollSpeed: Float = 0f
        private set

    /**
     * Where the finger is holding the row's centre, in viewport pixels. NaN
     * until the first drag event, which takes it from the row's own slot — a
     * drag begins with the row exactly where it already was.
     */
    private var heldCenter: Float = Float.NaN

    /** Where the last swap put the row, until the list is laid out with it. */
    private var awaiting: Int? = null

    fun onDragStart(key: Any) {
        draggedKey = key
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        setAutoScroll(0f)
    }

    /** The finger moved [deltaY] pixels and the list stayed put. */
    fun onDrag(deltaY: Float) = settle(deltaY)

    /** The list moved under the finger and the finger stayed put. */
    fun onScrolled() = settle(0f)

    fun onDragEnd() {
        draggedKey = null
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        setAutoScroll(0f)
    }

    /**
     * Takes the drag in [deltaY] pixels further, then reads the list back to
     * see where that leaves the row: where to draw it, whether it has reached
     * an edge, and whether it has reached a neighbour worth trading with.
     */
    private fun settle(deltaY: Float) {
        val key = draggedKey ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        // The row's own slot is off screen. There is nothing to measure an
        // edge or a swap against and nothing to draw against either, so the
        // way back is to stand still and let the swap already sent land and
        // bring the slot into view. If the row has been disposed outright
        // rather than merely scrolled past, it ends the drag itself on the
        // way out — see the disposal guard in [InlineQueueRow].
        val dragged = items.find { it.key == key } ?: run {
            setAutoScroll(0f)
            return
        }
        val half = dragged.size / 2f
        if (heldCenter.isNaN()) heldCenter = dragged.offset + half
        heldCenter += deltaY
        holdToSection(items, dragged)

        val top = heldCenter - half
        // Aimed before the guard below, not after: a swap in flight is a frame
        // or two of the list not having caught up yet, and the scroll should
        // carry on evenly through those rather than stutter once per row.
        aimAutoScroll(top, dragged)
        renderOffset = insideViewport(top, dragged.size) - dragged.offset

        // A swap already sent but not yet laid out: deciding the next one off
        // a position the list has moved on from would send a second move for
        // a swap that has already happened, and the two would fight.
        awaiting?.let {
            if (dragged.index != it) return
            awaiting = null
        }
        val target = swapTarget(items, dragged) ?: return
        onMove(dragged.index - lazyOffset, target.index - lazyOffset)
        awaiting = target.index
    }

    /**
     * The neighbour [heldCenter] has reached far enough into to trade places
     * with, or null while there is none to trade with yet.
     */
    private fun swapTarget(
        items: List<LazyListItemInfo>,
        dragged: LazyListItemInfo,
    ): LazyListItemInfo? {
        // Only rows of this section are fair targets — the heading and the
        // other section's rows share the LazyColumn but not this range.
        val target = items
            .filter { it.index in lazyRange && it.index != dragged.index }
            .minByOrNull { abs((it.offset + it.size / 2f) - heldCenter) }
            ?: return null
        // Held short of halfway the rows would swap back and forth over a
        // single pixel of travel; a full half-height of overlap is what makes
        // one swap per row crossed.
        if (abs(heldCenter - (target.offset + target.size / 2f)) > target.size / 2f) return null
        // Never with the row the list is keeping its own place by, while there
        // is still list above it to scroll.
        //
        // LazyColumn remembers where it is scrolled to as the *key* of its
        // first visible row plus an offset into it. Reorder that particular
        // row and it follows the key to wherever the row went, which slides
        // the entire list along by a row — and the held row, which has just
        // moved into the slot that row left, goes off the top of the viewport
        // with it. LazyColumn then disposes it, and disposal cancels the drag
        // gesture outright: neither onDragEnd nor onDragCancel runs, so the
        // row was left highlighted and offset with nothing dragging it,
        // stranded a row above where it was picked up. Dragging *down* never
        // met this, because the row traded with is the one below and the list
        // anchors on the one at the top; dragging up, the row traded with is
        // precisely the one the edge scroll is drawing in at the top, which is
        // why one direction worked and the other did not.
        //
        // Declining to swap this frame is the whole fix. The scroll that
        // brought the row here carries on, the next row up becomes the one the
        // list is anchored by, and the trade goes through a few frames later —
        // by which time it moves nothing the list is holding on to. With no
        // list left above to scroll there is no jump to decline in the first
        // place, so a row can still be dropped into the first slot of its
        // section.
        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) {
            return null
        }
        return target
    }

    /**
     * Points the auto-scroll at whichever edge the row now spanning [top] has
     * reached, if either — but only while there is both a row that way for it
     * to swap with and list left to scroll. Held past the last row of its own
     * section it would otherwise keep the list moving with no move left to
     * make, carrying the row's slot away under a finger that has nothing left
     * to answer with.
     */
    private fun aimAutoScroll(top: Float, dragged: LazyListItemInfo) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + dragged.size,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
            zone = edgeZone,
            speed = edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> dragged.index <= lazyRange.first || !listState.canScrollBackward
            speed > 0f -> dragged.index >= lazyRange.last || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    /**
     * Holds the drag inside the section it started in.
     *
     * A row can only be dropped between the first and last slots of its own
     * section — the playing track and the history above it are not the user's
     * to reorder, and neither is the far side of the AutoPlay heading. The
     * swap loop already respects that, by having no target to offer past
     * either end; what it does not do is stop [heldCenter] running on past the
     * boundary, and a finger a screen beyond it then has that whole distance
     * to travel back before the row answers again. Held at the boundary it
     * stops there under the finger, which is what "this is as far as it goes"
     * ought to look like.
     *
     * Only the ends actually on screen bound anything. A section that runs off
     * the viewport has more of itself that way for the auto-scroll to bring
     * in, and holding to whichever of its rows happens to be measured would
     * stop the drag at the edge of the screen instead of at the edge of the
     * section.
     */
    private fun holdToSection(items: List<LazyListItemInfo>, dragged: LazyListItemInfo) {
        val half = dragged.size / 2f
        items.firstOrNull { it.index == lazyRange.first }?.let {
            heldCenter = heldCenter.coerceAtLeast(it.offset + half)
        }
        items.firstOrNull { it.index == lazyRange.last }?.let {
            heldCenter = heldCenter.coerceAtMost(it.offset + it.size - half)
        }
    }

    /** [top], kept where a row of [size] can still be seen — see [renderOffset]. */
    private fun insideViewport(top: Float, size: Int): Float {
        val info = listState.layoutInfo
        val minTop = info.viewportStartOffset.toFloat()
        val maxTop = (info.viewportEndOffset - size).toFloat().coerceAtLeast(minTop)
        return top.coerceIn(minTop, maxTop)
    }

    private fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        val direction = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
        if (autoScrollDir != direction) autoScrollDir = direction
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
    // LazyColumn disposes a row the instant its slot leaves the viewport, and
    // that takes the drag gesture below down with it: the coroutine running
    // [detectDragGestures] is cancelled where it stands, so neither onDragEnd
    // nor onDragCancel is ever reached and the drag is left held by nothing —
    // the row comes back into view highlighted and offset from its slot, and
    // stays that way until the queue is closed. The swap guard in
    // [QueueDragState.swapTarget] is what stops the slot being thrown out of
    // the viewport in the first place; this is here because "the gesture ended
    // and nothing was told" should not be a state the queue can be left in at
    // all, whatever put it there.
    val heldOnDispose by rememberUpdatedState(dragging)
    val endDrag by rememberUpdatedState(onDragEnd)
    DisposableEffect(Unit) {
        onDispose { if (heldOnDispose) endDrag() }
    }
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
                contentDescription = stringResource(R.string.drag_to_reorder),
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
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
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
                contentDescription = stringResource(R.string.now_playing),
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
                contentDescription = stringResource(R.string.remove_from_queue),
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
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

/**
 * The gap between the two timestamps under the seek bar: just the "Lossless"
 * badge when one applies, and nothing otherwise. The stats line that used to
 * fall back to lives inside the sleeve now (see the bottom-centre overlay on
 * the artwork Box above), so there is no tap here to swap it in — the two say
 * the same thing at different resolutions, both read off the stream being
 * decoded rather than off what a source offered to send.
 */
@Composable
private fun LosslessOrStats(
    isLoading: Boolean,
    stillRacing: Boolean,
    losslessRequested: Boolean,
    effectiveQuality: AudioQuality,
    nerdStats: NerdStats.Snapshot?,
    onBadgeClick: () -> Unit,
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
        // The [isLoading] half is gated on `nerdStats == null` rather than
        // `nerdStats?.isLossless != true`: `isLoading` is just
        // `STATE_BUFFERING`, which a seek trips for a track whose quality
        // question was already settled — swallowing back into cache still
        // rebuffers. Gating on `!= true` read that rebuffer as "resolving"
        // again and flashed "Upgrading Quality" over a track already known
        // to be, say, Hi-Quality with no lossless copy anywhere. Once
        // [nerdStats] exists there is something measured to show instead, so
        // only a genuinely unmeasured track — or a real race via
        // [stillRacing] — earns this label.
        (stillRacing && nerdStats?.isLossless != true) ||
            (isLoading && losslessRequested && nerdStats == null) -> LosslessLabel(
            // What is already true, ahead of what is still being looked for.
            // A race running over JioSaavn's 320kbps AAC and one running over
            // YouTube's 160kbps Opus were both drawn as a bare "Upgrading
            // Quality", which reads as "this is not good yet" — wrong on the
            // first, where the track is already at the top of what lossy gets
            // and the search is only chasing a lossless copy that may not
            // exist. Naming the floor first makes the label describe a track
            // rather than a wait.
            //
            // Decided on [NerdStats.Snapshot.isHiQuality] rather than on which
            // source won, for the reason that property already gives: a
            // 320kbps stream is a 320kbps stream wherever it came from. It is
            // read off the stream rather than off what the module offered, so
            // a JioSaavn AAC qualifies once its container has stated its rate;
            // YouTube's Opus sits under the threshold and keeps the plain
            // label it had.
            text = if (nerdStats?.isHiQuality == true) {
                stringResource(R.string.high_quality_upgrading)
            } else {
                stringResource(R.string.upgrading_quality)
            },
            animated = false,
            onClick = onBadgeClick,
            modifier = modifier,
        )
        nerdStats?.isLossless == true -> LosslessLabel(
            // Same line Tidal, Qobuz and Apple Music draw it at — see
            // [NerdStats.Snapshot.isHiRes].
            text = stringResource(if (nerdStats.isHiRes) R.string.hi_res_lossless else R.string.lossless),
            // Shimmer is reserved for the thing that was asked for and
            // confirmed. It is what makes the badge read as an achievement
            // rather than a label, which only one of these two is.
            animated = true,
            onClick = onBadgeClick,
            modifier = modifier,
        )
        nerdStats?.isDolbyAtmos == true -> LosslessLabel(
            text = "Dolby Atmos",
            animated = true,
            iconPainter = painterResource(R.drawable.ic_dolby_atmos),
            onClick = onBadgeClick,
            modifier = modifier,
        )
        // Lossy, but the good end of lossy — a module's 320kbps tier, which
        // for a great many tracks is the best copy that exists anywhere the
        // app can reach. See [NerdStats.Snapshot.isHiQuality].
        nerdStats?.isHiQuality == true -> LosslessLabel(
            text = stringResource(R.string.high_quality),
            animated = false,
            onClick = onBadgeClick,
            modifier = modifier,
        )
        effectiveQuality == AudioQuality.LOW && nerdStats?.isLowQuality == true -> LosslessLabel(
            text = stringResource(R.string.data_saver),
            animated = false,
            onClick = onBadgeClick,
            modifier = modifier,
        )
        effectiveQuality == AudioQuality.MEDIUM && nerdStats?.isMediumQuality == true -> LosslessLabel(
            text = stringResource(R.string.medium_quality),
            animated = false,
            onClick = onBadgeClick,
            modifier = modifier,
        )
        else -> {}
    }
}

/** A quality glyph ahead of the status label, opening Audio Pipeline when tapped. */
@Composable
private fun LosslessLabel(
    text: String,
    animated: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Headphones,
    iconPainter: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = Color.White.copy(alpha = if (animated) 0.7f else 0.45f)
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(width = 13.dp, height = 10.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
        }
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
 * "FLAC · 24-bit · 96.0 kHz · 4608 kbps · Stereo" — whichever of those the
 * player has actually reported. A figure it hasn't is dropped rather than
 * filled in, so a short line means little was known, never that something was
 * invented.
 *
 * Bitrate is stated for a lossless stream too, and is the rate its samples
 * decode to — 1411 for 16-bit/44.1kHz, 4608 for 24-bit/96kHz. It is the same
 * figure Tidal, Qobuz and Apple Music print next to a lossless track, and the
 * one a listener can carry from track to track; a FLAC's compressed rate
 * cannot, because it says more about how compressible that recording was than
 * about the copy being played.
 *
 * A stream that arrived worse than its source promised gets that stated
 * outright rather than left to be spotted — see [NerdStats.Snapshot.downgraded].
 */
private fun NerdStats.Snapshot.describe(context: android.content.Context): String? {
    val parts = buildList {
        codecLabel(mimeType)?.let(::add)
        bitDepth?.let { add(context.getString(R.string.bit_depth, it)) }
        sampleRateHz?.let { add("%.1f kHz".format(Locale.ROOT, it / 1000f)) }
        bitrateKbps?.let { add("$it kbps") }
        channels?.let {
            add(
                when (it) {
                    1 -> context.getString(R.string.mono)
                    2 -> context.getString(R.string.stereo)
                    else -> context.getString(R.string.channel_count, it)
                },
            )
        }
        if (downgraded) add(context.getString(R.string.downgraded_from, claimed?.summary.orEmpty()))
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
    else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
}

/** Wording for the stats line; see [TrackAnalysisState]. */
@Composable
private fun TrackAnalysisState.localizedLabel(): String = when (this) {
    TrackAnalysisState.ANALYSED -> stringResource(R.string.analysis_complete)
    TrackAnalysisState.REFINING -> stringResource(R.string.analysis_refining)
    TrackAnalysisState.ANALYSING -> stringResource(R.string.analysis_in_progress)
    TrackAnalysisState.WAITING -> stringResource(R.string.waiting)
    TrackAnalysisState.FAILED -> stringResource(R.string.failed)
}

/**
 * A back callback that outranks whatever else the window has registered —
 * here, the sheet the player is drawn in. See the call site in
 * [NowPlayingScreen] for why it takes that.
 *
 * Everything that names an `android.window` type lives in this object so those
 * classes, which don't exist below API 33, are only ever *loaded* on a device
 * that has them: the callback comes back as [Any] rather than as the platform
 * interface for the same reason. Gating the calls on [Build.VERSION.SDK_INT]
 * is very likely enough by itself; this way it can't come down to how eagerly
 * a particular runtime resolves a reference it is never going to use.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object OverlayBack {
    /** The registered callback, to hand back to [unregister]; null if it couldn't be. */
    fun register(view: View, onBack: () -> Unit): Any? {
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
        val callback = OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        return callback
    }

    fun unregister(view: View, callback: Any?) {
        if (callback !is OnBackInvokedCallback) return
        view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
    }
}
