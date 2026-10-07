package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.MixBlend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/**
 * Apple Music's scrubber: a hairline capsule with no thumb knob, which
 * thickens under your finger and settles back when you let go. Material's
 * Slider can't be shaped like this — it always draws a thumb and a tall
 * track — so this is drawn directly.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    /**
     * Sends the same travelling sheen across the bar while a version swap is crossfading.
     * Kept separate from [loading]: a mix is playback feedback, not a wait state. An Automix
     * blend draws [mixPulse] instead and suppresses this.
     */
    mixing: Boolean = false,
    /**
     * The Automix blend's beat pulse — see [rememberMixPulse]. While a blend runs the fill
     * covers the whole bar, through both the outgoing and the incoming song, and breathes with
     * the blend's beat. Shared with whatever else pulses alongside the bar, so they stay in step.
     */
    mixPulse: MixPulse? = null,
    /**
     * Sends a travelling sheen across the whole bar — played *and* unplayed,
     * the bar's full thickness — for as long as it is true.
     *
     * The wait a version switch spends fetching and measuring the other cut is
     * a wait with no measurable fraction to draw, and a stock indeterminate
     * line sat on the scrubber like a second, uglier bar beside the one the
     * listener is already watching. The sheen claims the bar itself instead:
     * no extra chrome, no slot of its own, nothing shifting under it on the
     * frame the eye lands — just motion along the bar, pointing the way the
     * music is going.
     */
    loading: Boolean = false,
    /**
     * Span of the track, as fractions of its duration, that the next Automix
     * transition is planned to occupy. Drawn as a brighter stretch of the
     * unplayed bar so the mix is visible before it arrives.
     */
    transitionWindow: ClosedFloatingPointRange<Float>? = null,
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.26f),
    /** Halfway between the two track colours: visible against unplayed, invisible under played. */
    markerColor: Color = Color.White.copy(alpha = 0.5f),
) {
    var dragging by remember { mutableStateOf(false) }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderHeight",
    )
    // The sheen gets a minimum beat even when the switch resolves instantly —
    // a cached offset can land in a few hundred milliseconds — so its entry,
    // its sweep and its hand-over to the progress bar always play out as one
    // continuous morph, whenever the wait happened to start and stop. A flip
    // back to loading during the hold cancels it and the sheen simply stays,
    // so rapid toggling never blinks the bar out mid-morph.
    var shownLoading by remember { mutableStateOf(loading) }
    var sheenStart by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(loading) {
        if (loading) {
            sheenStart = System.nanoTime()
            shownLoading = true
        } else {
            val remaining = LOADING_MIN_MS - (System.nanoTime() - sheenStart) / 1_000_000L
            if (remaining > 0) delay(remaining)
            shownLoading = false
        }
    }
    // Both operations let the sheen claim the bar. A version switch is latched for a minimum
    // beat; a version swap's crossfade follows the audio engine and fades when it finishes.
    // An Automix blend has its own treatment — the beat pulse — and keeps the fill.
    val sheenVisible = shownLoading || (mixing && mixPulse?.active != true)
    val fillFactor by animateFloatAsState(
        targetValue = if (sheenVisible) 0f else 1f,
        animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
        label = "fillFactor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Generous invisible touch target — the visible bar is only ~7dp.
            .height(activeHeight + 22.dp)
            // One gesture loop for both taps and drags. Two separate detectors
            // — a drag one plus a tap one — meant taps never landed: the drag
            // detector took the pointer and a tap has no drag to report.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }

                    dragging = false
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)
            // Between the two track colours, and drawn *under* the played fill:
            // once the playhead reaches the window the transition is no longer
            // upcoming, and the ordinary progress colour taking it over is what
            // says so.
            transitionWindow?.let { window ->
                val from = size.width * window.start.coerceIn(0f, 1f)
                val to = size.width * window.endInclusive.coerceIn(0f, 1f)
                if (to > from) {
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(from, 0f),
                        size = Size(to - from, size.height),
                        cornerRadius = radius,
                    )
                }
            }
            // While a blend runs the whole bar is the fill, outgoing song and
            // incoming alike, and it is the fill's opacity that carries the mix:
            // brightest on each beat, easing down between them over the unplayed
            // track colour. A drag in progress always shows the finger.
            val cover = if (dragging) 0f else mixPulse?.cover ?: 0f
            val base = value.coerceIn(0f, 1f)
            val fraction = base + (1f - base) * cover
            // Scaled by [fillFactor]: retracted to nothing while the sheen
            // runs (two white signals on one bar would read as progress
            // fighting the wait) and slid back in when the switch lands. The
            // capsule's minimum width rides the same factor, so the nub at
            // zero progress retires with the fill instead of sitting as a
            // dot under the sheen.
            val filled = size.width * fraction * fillFactor
            if (filled > 0f) {
                val alpha = if (dragging) activeColor.alpha else mixPulse?.alpha(activeColor.alpha) ?: activeColor.alpha
                drawRoundRect(
                    color = activeColor.copy(alpha = alpha),
                    size = Size(
                        filled.coerceAtLeast(size.height * fillFactor).coerceAtMost(size.width),
                        size.height,
                    ),
                    cornerRadius = radius,
                )
            }
        }
        // Composed only while switching or mixing. The infinite animation therefore costs no
        // frames during ordinary playback, and AnimatedVisibility lets it leave gracefully.
        AnimatedVisibility(
            visible = sheenVisible,
            // Grown out of the bar's own left end — where the progress fill
            // begins — instead of slid in from a third of its own width: the
            // capsule is full-bleed, so that slide started past the screen
            // edge and flew in from outside the display. Same duration and
            // curve as the fill's retraction, so the swap reads as one morph.
            enter = fadeIn(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            exit = fadeOut(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)),
        ) {
            MixSheen(height = height, color = activeColor)
        }
    }
}

private const val SHEEN_BAND_FRACTION = 0.34f

/**
 * One beat clock for everything that pulses with an Automix blend — the seek bar's fill and
 * the "Mixing" label — so the two can never drift out of step with each other.
 *
 * Every value here is snapshot state meant to be read in draw or a graphics layer, where a
 * change costs a redraw rather than a recomposition.
 */
@Stable
class MixPulse internal constructor(private val blend: () -> MixBlend?) {
    /**
     * Whether a blend is running right now. Derived, so composition that reads it recomposes
     * when a blend starts or ends — not every time the blend's beat grid is republished.
     */
    val active: Boolean by derivedStateOf { blend() != null }

    /** Whether the blend is playing; a paused one lets the pulse settle and the clock stop. */
    internal val playing: Boolean by derivedStateOf { blend()?.playing == true }

    internal val coverAnim = Animatable(0f)
    internal val depthAnim = Animatable(0f)
    internal var level by mutableFloatStateOf(0f)

    /** Kept across restarts of the clock, so a blend ending never jumps the phase. */
    internal var phase = 0.0
    internal var locked = false

    /** 0..1: how far the blend has claimed the bar and the label, eased in and out. */
    val cover: Float get() = coverAnim.value

    /**
     * [fullAlpha] as the pulse modulates it: [fullAlpha] on the beat, [PULSE_REST] of it at the
     * half-beat, and [fullAlpha] throughout whenever the pulse is faded out or switched off.
     */
    fun alpha(fullAlpha: Float, restShare: Float = PULSE_REST): Float {
        val rest = fullAlpha * restShare
        val pulsed = rest + (fullAlpha - rest) * level
        return fullAlpha + (pulsed - fullAlpha) * depthAnim.value
    }

    internal fun blendNow(): MixBlend? = blend()
}

/**
 * Runs the beat clock for [mixBlend]. [enabled] off (reduced motion) keeps the cover — the bar
 * still fills and the label still shows — but holds them steady instead of pulsing.
 */
@Composable
fun rememberMixPulse(mixBlend: () -> MixBlend?, enabled: Boolean): MixPulse {
    val current by rememberUpdatedState(mixBlend)
    val pulse = remember { MixPulse { current() } }
    val active = pulse.active
    val playing = pulse.playing
    LaunchedEffect(active, playing, enabled) {
        launch {
            pulse.coverAnim.animateTo(
                if (active) 1f else 0f,
                tween(durationMillis = COVER_MS, easing = FastOutSlowInEasing),
            )
        }
        launch {
            // Paused mid-blend, the pulse settles to a steady bar rather than beating on
            // over silence — and the frame clock below gets to stop.
            pulse.depthAnim.animateTo(
                if (active && playing && enabled) 1f else 0f,
                tween(durationMillis = PULSE_FADE_MS, easing = FastOutSlowInEasing),
            )
        }
        if (!enabled) return@LaunchedEffect
        // The pulse's own beat clock. The blend publishes a beat anchor every fade tick, and
        // each one is re-derived from player positions that wobble by a few tens of ms — drawn
        // straight, that wobble is the pulse lurching. So this runs a free phase at the blend's
        // tempo and only leans toward the published anchor a little each frame: it follows a
        // real tempo change or the grid moving to the other song within a fraction of a
        // second, and never visibly jumps. Runs until the pulse has faded out after the blend.
        var last = System.nanoTime()
        while ((active && playing) || pulse.depthAnim.value > 0f) {
            withFrameNanos {
                val now = System.nanoTime()
                val dt = (now - last).coerceIn(0L, MAX_FRAME_NANOS)
                last = now
                val blend = pulse.blendNow()
                val beatNanos = blend?.beatMs?.takeIf { it > 0f }?.let { it * 1_000_000.0 }
                var phase = pulse.phase
                if (beatNanos != null && blend != null) {
                    phase += dt / beatNanos
                    val target = Math.floorMod(now - blend.beatAnchorNanos, beatNanos.toLong()) / beatNanos
                    // Shortest way round the circle to where the beat should be. The very first
                    // lock snaps — nothing is showing yet — and every one after only leans.
                    val error = ((target - phase) % 1.0 + 1.5) % 1.0 - 0.5
                    phase += if (pulse.locked) error * PHASE_PULL else error
                    pulse.locked = true
                } else {
                    // No tempo known, or the blend just ended: a slow breath from wherever the
                    // phase already is, which the depth fade then takes away.
                    phase += dt / BREATH_NANOS
                    if (blend != null) pulse.locked = false
                }
                phase -= kotlin.math.floor(phase)
                pulse.phase = phase
                pulse.level = beatShape(phase.toFloat())
            }
        }
        pulse.locked = false
    }
    return pulse
}

/** How long the fill takes to glide out to full width, and back, around a blend. */
private const val COVER_MS = 700

/** How long the beat pulse takes to swell in as a blend starts and settle as it ends. */
private const val PULSE_FADE_MS = 900

/** Opacity between beats, as a share of the fill's own: low enough to read, never gone. */
private const val PULSE_REST = 0.1f

/** Share of the remaining phase error corrected each frame; small enough to be invisible. */
private const val PHASE_PULL = 0.06

/** Breathing period when neither track's tempo is known. */
private const val BREATH_NANOS = 1_800_000_000.0

/** A dropped frame or a backgrounded app must not fling the phase forward. */
private const val MAX_FRAME_NANOS = 100_000_000L

/**
 * The pulse's brightness through one beat, 0..1: a raised cosine squared, peaking on the
 * beat and easing to nothing at the half-beat. Smooth in value and slope everywhere, so
 * there is no edge in it for the eye to catch — a breath on each beat rather than a flash.
 */
private fun beatShape(phase: Float): Float {
    val c = 0.5f + 0.5f * cos(2f * PI.toFloat() * phase)
    return c * c
}

/** Length of one morph step — entry, fill retraction, fill return, exit — all on the same curve. */
private const val MORPH_MS = 450

/** Shortest time the sheen stays up, so even an instant switch still plays its morph. */
private const val LOADING_MIN_MS = 600L

/**
 * A highlight sweeping the bar's full thickness — played *and* unplayed
 * alike — about once a second, at the progress fill's own brightness.
 *
 * Loading has no measurable fraction to draw, so an indeterminate indicator
 * has to draw *something*: the usual choice is a thin line claiming a
 * sliver of the scrubber's height, which reads as a second, lesser bar
 * growing out of the first. This band instead takes the whole thickness the
 * scrubber already occupies and moves along it, so the wait looks like the
 * bar itself moving rather than an alien element parked on top — no gap, no
 * slot, no shifting of the controls below it.
 *
 * One pass a second rather than the old two: any faster and the band is a
 * strobe the eye tracks instead of a wait it can ignore. The pass reverses
 * at each edge rather than restarting from the far one.
 */
@Composable
private fun MixSheen(height: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "mixSheen")
    val phase by transition.animateFloat(
        initialValue = -SHEEN_BAND_FRACTION,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            // Reverses rather than restarting: a restart teleports the band
            // back to the far edge every second, which is a hitch the eye
            // catches each time. Ping-pong has no edge to fall off.
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val band = size.width * SHEEN_BAND_FRACTION
        val x = phase * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to color,
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            size = Size(size.width, size.height),
            // The band is clipped to the same capsule the track is drawn
            // with: a plain rect bared square corners wherever the sweep
            // crossed the rounded ends.
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}
