package com.music.bitchord.ui.player

import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.CharGrowth
import com.music.bitchord.data.lyrics.Genius
import com.music.bitchord.data.lyrics.GrowingWord
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.lyrics.translationLanguageName
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.Haptics
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.rememberIsForeground
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


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
 * The romanization or translation hung under each line — caption-sized, the
 * way Apple Music prints pronunciation under the lyric, and tucked up into the
 * lead's glow inset so the two read as one line rather than two rows.
 */
private val SUB_LYRIC_FONT_SIZE = 20.sp
private val SUB_LYRIC_LINE_HEIGHT = 25.sp
private val SUB_BACKING_FONT_SIZE = 16.sp
private val SUB_BACKING_LINE_HEIGHT = 21.sp
private const val SUB_LYRIC_ALPHA = 0.85f
private val SUB_LYRIC_TUCK = 6.dp
private const val SUB_LYRIC_OPEN_MS = 460
private const val SUB_LYRIC_CLOSE_MS = 260

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

private const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
private const val LYRICS_UNAVAILABLE_FADE_MS = 900
internal sealed interface LyricsTranslationUiState {
    data object Idle : LyricsTranslationUiState
    data object Loading : LyricsTranslationUiState
    data class Ready(val lines: List<LyricLine>) : LyricsTranslationUiState
    data object SameLanguage : LyricsTranslationUiState
}

internal enum class LyricsDisplayMode { Original, Romanized, Translated }

private const val TRANSLATION_MOTION_MS = 540
private const val PARTICLES_PER_VOICE = 18

private data class TranslationParticle(
    val anchor: Offset,
    val drift: Offset,
    val radius: Float,
    val delay: Float,
)

/** Source/status caption with the provider chooser kept visually inline. */
@Composable
internal fun LyricsStatusWithChange(
    status: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.change_lyrics_provider),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.72f),
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(Haptic.Select)
                onChange()
            },
        )
    }
}

internal fun adjustedLyricsPosition(positionMs: Long, offsetMs: Int): Long =
    (positionMs - offsetMs.toLong()).coerceAtLeast(0L)

internal fun adjustedLyricsSeekTarget(lineTimeMs: Long, offsetMs: Int): Long =
    (lineTimeMs + offsetMs.toLong()).coerceAtLeast(0L)

/**
 * The current lyric, one line, directly above the scrubber — or the line
 * saying why there isn't one.
 */
@Composable
internal fun CurrentLyricStrip(
    lines: List<LyricLine>,
    trackKey: String,
    /** Read in here — a tick recomposes the strip alone. */
    positionMs: () -> Long,
    isPlaying: Boolean,
    durationMs: Long,
    lyricsUnavailable: Boolean,
    loadingText: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The slider's touch target reaches ~13dp above the drawn bar, so
            // the strip reads as further off it than it is. Nudged down into
            // that dead space, the same way the timestamps below are pulled
            // back up into it.
            .offset(y = 6.dp),
    ) {
        if (lines.isNotEmpty()) {
            CurrentLyricLine(
                lines = lines,
                trackKey = trackKey,
                positionMs = positionMs(),
                isPlaying = isPlaying,
                durationMs = durationMs,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (lyricsUnavailable) {
            LyricsUnavailableLine(
                trackKey = trackKey,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LyricsLoadingLine(
                text = loadingText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
private fun rememberLyricClock(
    trackKey: Any,
    positionMs: Long,
    isPlaying: Boolean,
): MutableLongState {
    val startedAtMs = remember(trackKey) { SystemClock.elapsedRealtime() }
    val clock = remember(trackKey) { mutableLongStateOf(positionMs) }
    val reconciler = remember(trackKey) {
        LyricClockReconciler(positionMs, startedAtMs, isPlaying)
    }
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
        clock.longValue = reconciler.reconcile(
            displayedMs = clock.longValue,
            reportedMs = positionMs,
            observedAtMs = SystemClock.elapsedRealtime(),
            isPlaying = isPlaying,
        )
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
internal fun TranslationToggleButton(
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

/** The left-hand companion to [TranslationToggleButton], producing Latin script. */
@Composable
internal fun RomanizationToggleButton(
    state: LyricsTranslationUiState,
    showingRomanization: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val active = showingRomanization || state is LyricsTranslationUiState.Loading
    val tint = when {
        !enabled || state is LyricsTranslationUiState.SameLanguage -> Color.White.copy(alpha = 0.42f)
        active -> Color.White
        else -> Color.White.copy(alpha = 0.78f)
    }
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "romanizeDisc",
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
                imageVector = Icons.Rounded.Language,
                contentDescription = stringResource(
                    if (showingRomanization) R.string.show_original_lyrics
                    else R.string.romanize_lyrics,
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
internal fun LyricsTranslationMotion(
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
 * Shared phone-panel gesture: moving forward through content hides the half
 * player, while reversing brings it back. Only direct finger input counts, so
 * the lyrics auto-follow and the queue's current-track jump cannot move chrome.
 */
@Composable
internal fun rememberPlayerControlsOnScroll(
    enabled: Boolean = true,
    onReveal: () -> Unit,
    onHide: () -> Unit,
): NestedScrollConnection {
    val controlsSlopPx = with(LocalDensity.current) { CONTROLS_SCROLL_SLOP.toPx() }
    val revealControls by rememberUpdatedState(onReveal)
    val hideControls by rememberUpdatedState(onHide)
    return remember(enabled, controlsSlopPx) {
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (enabled && source == NestedScrollSource.UserInput && available.y != 0f) {
                    if (travel != 0f && (travel > 0f) != (available.y > 0f)) travel = 0f
                    travel += available.y
                    // Finger travelling up is the list going forward.
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
internal fun LyricsPanel(
    lines: List<LyricLine>,
    /**
     * Romanized or translated copies of [lines], index for index, drawn small
     * under each original line. Null shows the originals alone.
     */
    subLines: List<LyricLine>? = null,
    trackKey: String,
    positionMs: Long,
    /** Whether a lookup for this track is still in flight. */
    looking: Boolean,
    isPlaying: Boolean,
    /** False while the panel is retained offscreen solely as a warm layout. */
    active: Boolean = true,
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
    val panelPlaying = isPlaying && active
    val clock = rememberLyricClock(trackKey, positionMs, panelPlaying)
    val subReveal = rememberSubLyricsReveal(subLines, trackKey)

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
    val controlsOnScroll = rememberPlayerControlsOnScroll(
        onReveal = onRevealControls,
        onHide = onHideControls,
    )

    val currentLine by rememberUpdatedState(focusLine)
    val activeOnScreen by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == currentLine }
        }
    }
    // Paused, there is no song to follow back to, so a hand scroll should sit
    // wherever it was left rather than snapping back on these timers.
    LaunchedEffect(browsing, activeOnScreen, listState.isScrollInProgress, panelPlaying) {
        if (panelPlaying && browsing && activeOnScreen && !listState.isScrollInProgress) {
            delay(600)
            browsing = false
        }
    }

    LaunchedEffect(browsing, listState.isScrollInProgress, panelPlaying) {
        if (panelPlaying && browsing && !listState.isScrollInProgress) {
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
    LaunchedEffect(focusLine, browsing, controlsOpen, active) {
        if (active && isSynced && !browsing &&
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
                val sub = subReveal.lines?.getOrNull(index)
                val subStyle = style.copy(
                    fontSize = SUB_LYRIC_FONT_SIZE,
                    lineHeight = SUB_LYRIC_LINE_HEIGHT,
                    fontWeight = FontWeight.Bold,
                )
                Column(modifier = shape) {
                    PanelVoice(
                        line = line,
                        clock = clock,
                        style = style,
                        isActive = isActive,
                        sung = sung,
                        synced = isSynced,
                        browsing = browsing,
                        glowAlpha = glow,
                        room = GLOW_ROOM,
                        alignEnd = alignEnd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // A line the service handed back unchanged ("falling
                    // down" in a Korean song) gets no second copy of itself.
                    sub?.takeIf { it.text.differsFrom(line.text) }?.let { subLine ->
                        PanelVoice(
                            line = subLine,
                            clock = clock,
                            style = subStyle,
                            isActive = isActive,
                            sung = sung,
                            synced = isSynced,
                            browsing = browsing,
                            glowAlpha = 0f,
                            room = 0.dp,
                            alignEnd = alignEnd,
                            // Only the rows actually in front of the reader get the
                            // particle pass. Sixty rows' worth of glyph boxes is a
                            // layout walk per frame for text nobody is looking at.
                            translationProgress = translationProgress.takeIf {
                                if (isSynced) abs(index - focusLine) <= 1 else index < 4
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .revealBelow(subReveal.progress)
                                // Tucked up into the lead's glow inset so the
                                // pair reads as one line in two scripts.
                                .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                                .offset(y = -SUB_LYRIC_TUCK)
                                .graphicsLayer { alpha = SUB_LYRIC_ALPHA },
                        )
                    }
                    line.background?.let { backing ->
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
                        sub?.background
                            ?.takeIf { it.text.differsFrom(backing.text) }
                            ?.let { subBacking ->
                                PanelVoice(
                                    line = subBacking.withoutBracketPunctuation(),
                                    clock = clock,
                                    style = subStyle.copy(
                                        fontSize = SUB_BACKING_FONT_SIZE,
                                        lineHeight = SUB_BACKING_LINE_HEIGHT,
                                    ),
                                    isActive = isActive,
                                    sung = sung,
                                    synced = isSynced,
                                    browsing = browsing,
                                    glowAlpha = 0f,
                                    room = 0.dp,
                                    alignEnd = alignEnd,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .revealBelow(subReveal.progress)
                                        .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                                        .offset(y = -SUB_LYRIC_TUCK)
                                        .graphicsLayer { alpha = BACKING_ALPHA * SUB_LYRIC_ALPHA },
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
/**
 * What [LyricsPanel] draws under each line, and how far it has opened.
 *
 * One clock for the whole panel rather than an animation per row: a long song
 * is a hundred rows, and each would otherwise start, run and stop its own
 * Animatable on every toggle. [progress] is only ever read in layout and draw
 * (see [revealBelow]), so opening it costs a relayout of the rows on screen
 * and not one recomposition.
 */
private class SubLyricsReveal(
    val progress: State<Float>,
    lines: State<List<LyricLine>?>,
) {
    /** Held through the collapse, so the words fold away rather than vanish. */
    val lines: List<LyricLine>? by lines
}

@Composable
private fun rememberSubLyricsReveal(target: List<LyricLine>?, trackKey: String): SubLyricsReveal {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    // Keyed to the track: a new song arrives with nothing under it, and must
    // not fold the last song's translation away over its own opening lines.
    val shown = remember(trackKey) { mutableStateOf(target) }
    val progress = remember(trackKey) { Animatable(if (target != null) 1f else 0f) }
    LaunchedEffect(target, trackKey, reduceAnimation) {
        if (reduceAnimation) {
            shown.value = target
            progress.snapTo(if (target != null) 1f else 0f)
            return@LaunchedEffect
        }
        // Switching straight from romanized to translated closes the one
        // before opening the other, so two scripts never share the gap.
        if (shown.value != null && shown.value !== target && progress.value > 0f) {
            progress.animateTo(0f, tween(SUB_LYRIC_CLOSE_MS, easing = FastOutSlowInEasing))
        }
        if (target != null) {
            shown.value = target
            progress.animateTo(1f, tween(SUB_LYRIC_OPEN_MS, easing = LYRIC_EASING))
        } else {
            shown.value = null
        }
    }
    return remember(trackKey) { SubLyricsReveal(progress.asState(), shown) }
}

/**
 * Opens downward out of the line above: the height grows from nothing while
 * the words slide down from behind the original and fade up. Both read
 * [progress] outside composition, so only layout and draw run per frame.
 */
private fun Modifier.revealBelow(progress: State<Float>): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val open = progress.value
        val height = (placeable.height * open).roundToInt()
        layout(placeable.width, height) {
            placeable.placeWithLayer(0, 0) {
                translationY = -placeable.height * (1f - open) * 0.6f
                alpha = open * open
            }
        }
    }

private fun String.differsFrom(original: String): Boolean =
    trim().lowercase(Locale.ROOT) != original.trim().lowercase(Locale.ROOT)

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

    val clock = rememberLyricClock(trackKey, positionMs, isPlaying)

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
 * Everything the player shows about a lyric translation or romanization, and
 * the two toggles that ask for one — see [rememberLyricsTranslation].
 */
internal class LyricsTranslationUi(
    /** The lines the one-line strip reads: the originals, or their translation. */
    val displayedLyrics: List<LyricLine>,
    /** Drawn small under each original line in the panel; null for none. */
    val subLines: List<LyricLine>?,
    val translationState: LyricsTranslationUiState,
    val romanizationState: LyricsTranslationUiState,
    val showingTranslation: Boolean,
    val showingRomanization: Boolean,
    /** Bumped on every switch between versions — the particle motion's trigger. */
    val transition: Int,
    val reduceMotion: Boolean,
    /** The line naming the lyrics' source, or what the translation is doing. */
    val status: String,
    val toggleTranslation: () -> Unit,
    val toggleRomanization: () -> Unit,
)

/**
 * The translation and romanization state machine behind the lyric panel:
 * which language, what has been asked for, what came back, and which version
 * is on screen. Reset per track, per target language and per lyric sheet.
 */
@Composable
internal fun rememberLyricsTranslation(
    trackId: String,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean,
    /** What the status line says while the lyrics are still being looked up. */
    loadingText: String,
    haptics: Haptics,
): LyricsTranslationUi {
    val context = LocalContext.current
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
    var translationState by remember(trackId, translationLanguage, lyrics) {
        mutableStateOf<LyricsTranslationUiState>(LyricsTranslationUiState.Idle)
    }
    var romanizationState by remember(trackId, translationLanguage, lyrics) {
        mutableStateOf<LyricsTranslationUiState>(LyricsTranslationUiState.Idle)
    }
    var lyricsDisplayMode by remember(trackId, translationLanguage, lyrics) {
        mutableStateOf(LyricsDisplayMode.Original)
    }
    val showingTranslation = lyricsDisplayMode == LyricsDisplayMode.Translated
    val showingRomanization = lyricsDisplayMode == LyricsDisplayMode.Romanized
    var translationTransition by remember(trackId) { mutableIntStateOf(0) }
    var translationJob by remember(trackId, translationLanguage, lyrics) {
        mutableStateOf<Job?>(null)
    }
    var romanizationJob by remember(trackId, translationLanguage, lyrics) {
        mutableStateOf<Job?>(null)
    }
    DisposableEffect(trackId, translationLanguage, lyrics) {
        onDispose {
            translationJob?.cancel()
            romanizationJob?.cancel()
        }
    }
    val displayedLyrics = when (lyricsDisplayMode) {
        LyricsDisplayMode.Translated ->
            (translationState as? LyricsTranslationUiState.Ready)?.lines ?: lyrics.orEmpty()
        LyricsDisplayMode.Romanized ->
            (romanizationState as? LyricsTranslationUiState.Ready)?.lines ?: lyrics.orEmpty()
        LyricsDisplayMode.Original -> lyrics.orEmpty()
    }
    // What the panel draws in small type under each original line, Apple
    // Music style. The panel itself always keeps the original words; only the
    // one-line strip over the scrubber swaps to [displayedLyrics]. One mode at
    // a time by construction — [lyricsDisplayMode] holds a single value.
    val lyricsSubLines = when (lyricsDisplayMode) {
        LyricsDisplayMode.Translated -> (translationState as? LyricsTranslationUiState.Ready)?.lines
        LyricsDisplayMode.Romanized -> (romanizationState as? LyricsTranslationUiState.Ready)?.lines
        LyricsDisplayMode.Original -> null
    }
    val translationScope = rememberCoroutineScope()
    val toggleTranslation: () -> Unit = toggleTranslation@{
        when (val state = translationState) {
            is LyricsTranslationUiState.Ready -> {
                lyricsDisplayMode = if (showingTranslation) {
                    LyricsDisplayMode.Original
                } else {
                    LyricsDisplayMode.Translated
                }
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
                romanizationJob?.cancel()
                if (romanizationState is LyricsTranslationUiState.Loading) {
                    romanizationState = LyricsTranslationUiState.Idle
                }
                translationJob?.cancel()
                translationJob = translationScope.launch {
                    when (
                        val result = LyricsTranslation.translate(
                            context = context.applicationContext,
                            trackId = trackId,
                            lines = source,
                            targetLanguageTag = translationLanguage,
                        )
                    ) {
                        is LyricsTranslation.Result.Translated -> {
                            translationState = LyricsTranslationUiState.Ready(result.lines)
                            lyricsDisplayMode = LyricsDisplayMode.Translated
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
    val toggleRomanization: () -> Unit = toggleRomanization@{
        when (val state = romanizationState) {
            is LyricsTranslationUiState.Ready -> {
                lyricsDisplayMode = if (showingRomanization) {
                    LyricsDisplayMode.Original
                } else {
                    LyricsDisplayMode.Romanized
                }
                translationTransition++
                haptics.play(Haptic.Select)
            }
            LyricsTranslationUiState.Loading -> Unit
            LyricsTranslationUiState.SameLanguage -> {
                haptics.play(Haptic.Tap)
                Toast.makeText(
                    context,
                    context.getString(R.string.lyrics_already_romanized),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            LyricsTranslationUiState.Idle -> {
                val source = lyrics.orEmpty()
                if (source.isEmpty()) return@toggleRomanization
                haptics.play(Haptic.Tap)
                romanizationState = LyricsTranslationUiState.Loading
                translationJob?.cancel()
                if (translationState is LyricsTranslationUiState.Loading) {
                    translationState = LyricsTranslationUiState.Idle
                }
                romanizationJob?.cancel()
                romanizationJob = translationScope.launch {
                    when (
                        val result = LyricsTranslation.romanize(
                            context = context.applicationContext,
                            trackId = trackId,
                            lines = source,
                            targetLanguageTag = translationLanguage,
                        )
                    ) {
                        is LyricsTranslation.RomanizationResult.Romanized -> {
                            romanizationState = LyricsTranslationUiState.Ready(result.lines)
                            lyricsDisplayMode = LyricsDisplayMode.Romanized
                            translationTransition++
                            haptics.play(Haptic.ToggleOn)
                        }
                        LyricsTranslation.RomanizationResult.AlreadyRomanized -> {
                            romanizationState = LyricsTranslationUiState.SameLanguage
                            Toast.makeText(
                                context,
                                context.getString(R.string.lyrics_already_romanized),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        LyricsTranslation.RomanizationResult.Unavailable -> {
                            romanizationState = LyricsTranslationUiState.Idle
                            Toast.makeText(
                                context,
                                context.getString(R.string.lyrics_romanization_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }
    // The one line that sits above the scrubber while the lyrics are open: which
    // of the providers the timings came from, or what the translation is doing.
    // Worked out once for both layouts — the portrait player puts it over its
    // half-player, the landscape one under its lyric column — so the two can
    // never disagree about what it says.
    val status = when {
        translationState is LyricsTranslationUiState.Loading ->
            stringResource(R.string.translating_lyrics_to, translationLanguageName)
        romanizationState is LyricsTranslationUiState.Loading ->
            stringResource(R.string.romanizing_lyrics)
        showingTranslation ->
            stringResource(R.string.lyrics_translated_to, translationLanguageName)
        showingRomanization ->
            stringResource(R.string.lyrics_romanized)
        translationState is LyricsTranslationUiState.SameLanguage ->
            stringResource(R.string.lyrics_already_in_language, translationLanguageName)
        romanizationState is LyricsTranslationUiState.SameLanguage ->
            stringResource(R.string.lyrics_already_romanized)
        lyricsSource != null -> stringResource(R.string.lyrics_by, lyricsSource.label)
        lyricsUnavailable -> stringResource(R.string.no_lyrics_found)
        lyrics.isNullOrEmpty() -> loadingText
        else -> stringResource(R.string.lyrics_saved_with_download)
    }

    return LyricsTranslationUi(
        displayedLyrics = displayedLyrics,
        subLines = lyricsSubLines,
        translationState = translationState,
        romanizationState = romanizationState,
        showingTranslation = showingTranslation,
        showingRomanization = showingRomanization,
        transition = translationTransition,
        reduceMotion = reduceTranslationMotion,
        status = status,
        toggleTranslation = toggleTranslation,
        toggleRomanization = toggleRomanization,
    )
}
