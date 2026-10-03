package com.music.bitchord.ui.player

import android.view.Window
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.WindowInfo
import com.music.bitchord.ui.components.MiniPlayerPull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * The spring every open and close runs on: critically damped, so it never
 * bounces, with about 0.45s of response — the way Apple Music's player sheet
 * moves. Unlike a timed ease it has no fast end: it leaves briskly and then
 * spends most of its time settling, slower and slower, into place, which is
 * where the eye is following the artwork home.
 */
private const val SLIDE_STIFFNESS = 195f

/** Close enough to stop, in pixels — rather than a spring's whole long tail. */
private const val SLIDE_SETTLED_PX = 0.5f

/** How old the sheet's last movement can be and still count as its speed. */
private const val SHEET_VELOCITY_FRESH_MS = 80f

/** How long a sheet waits to be laid out before an open starts regardless. */
private const val LAYOUT_WAIT_MS = 300L

/**
 * How long after the sheet appears its window gets its exit animation back —
 * long enough that the enter animation it was taken away from has had its
 * chance to start, and not, then, play.
 */
private const val WINDOW_ENTER_MS = 600L

/**
 * Where the player sheet stands while the app moves it, rather than M3.
 *
 * M3's sheet can only be told to show or hide, on its own curve, and has no
 * way to be told where to stand. That covers neither the pull up from the
 * mini player, which has to follow the finger, nor an open and close slow
 * enough to watch the artwork travel. So for all three the sheet is left open
 * and [held] says where its content really is: how far below fully open, in
 * pixels. The content makes up the difference itself ([contentOffsetPx]), and
 * the [PlayerDock] reads [position], so the artwork flies the same way however
 * the player is being moved.
 *
 * Opened with [open] from a tap, pulled with [drag] / [release], closed with
 * [close] — which the sheet itself is routed through too, for a drag released
 * low, a back press or a tap on the scrim (see the sheet's `confirmValueChange`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class PlayerSheetMotion(
    private val scope: CoroutineScope,
    private val dock: PlayerDock,
    private val shown: () -> Boolean,
    private val setShown: (Boolean) -> Unit,
) : MiniPlayerPull {
    /** NaN while the sheet stands where M3 put it. */
    var held by mutableFloatStateOf(Float.NaN)
        private set

    /** Whether the app, not the sheet, is placing the player. */
    val holding: Boolean get() = !held.isNaN()

    /** The sheet, while it is up. Set by the host. */
    var sheet: SheetState? = null

    /** The sheet's window, while it is up — see [attachWindow]. */
    private var window: Window? = null
    private var windowAnimations = 0
    private var windowRestore: Job? = null

    // When the current pull started and how far the finger has taken it — for
    // a flick whose own release speed came through as nothing: a short, fast
    // swipe can lift with no speed on its last event at all.
    private var pullStartNanos = 0L
    private var pullStartAt = 0f

    // How fast the sheet itself was moving under the finger, for a drag on the
    // player released low: M3 asks to hide without saying how fast, and a close
    // from rest hung for a moment before it got going.
    private var sheetSampleAt = Float.NaN
    private var sheetSampleNanos = 0L
    private var sheetVelocity = 0f

    /** Set by the host once the sheet has been laid out at all. */
    var laidOut by mutableStateOf(false)

    /**
     * The window, for its height before the sheet has ever been measured.
     * Held rather than its height read off it: read in composition, that
     * height subscribed the app's whole root to window resizes.
     */
    var windowInfo: WindowInfo? = null

    /** How fast a release has to be, in pixels a second, to be a flick. */
    var flingVelocity = 0f

    private var settle: Job? = null

    /**
     * Whether the player is already on its way down. A second close — back
     * pressed twice, say — restarted the slide from a standstill where it
     * stood, a visible stall halfway home.
     */
    private var closing = false

    private fun travel(): Float =
        dock.sheetTravel.takeIf { it > 0f } ?: (windowInfo?.containerSize?.height ?: 0).toFloat()

    private fun sheetOffset(): Float = try {
        sheet?.requireOffset() ?: Float.NaN
    } catch (_: IllegalStateException) {
        Float.NaN
    }

    /** How far below fully open the player is: what [PlayerDock] is told. */
    fun position(): Float = if (holding) held else sheetOffset()

    /**
     * What the sheet's content adds to the sheet's own offset to stand at
     * [held]. In whole pixels on both sides, so the two land exactly where
     * [position] says — and nothing at all when the sheet is in charge.
     */
    fun contentOffsetPx(): Int {
        val target = held
        val sheetAt = sheetOffset().takeUnless { it.isNaN() } ?: 0f
        if (target.isNaN()) {
            // Read every frame the sheet moves, which is what this is placed
            // on — so the sheet's speed comes for the price of a subtraction.
            sampleSheet(sheetAt)
            return 0
        }
        return target.toInt() - sheetAt.toInt()
    }

    private fun sampleSheet(at: Float) {
        val now = System.nanoTime()
        if (!sheetSampleAt.isNaN()) {
            val seconds = (now - sheetSampleNanos) / 1e9f
            if (seconds > 0f) {
                val instant = (at - sheetSampleAt) / seconds
                sheetVelocity = sheetVelocity * 0.4f + instant * 0.6f
            }
        }
        sheetSampleAt = at
        sheetSampleNanos = now
    }

    /** The sheet's speed, if it was still moving a moment ago. */
    private fun recentSheetVelocity(): Float =
        if ((System.nanoTime() - sheetSampleNanos) / 1e6f < SHEET_VELOCITY_FRESH_MS) sheetVelocity else 0f

    /** A tap on the mini player. */
    fun open() {
        if (shown()) return
        settle?.cancel()
        closing = false
        val travel = travel()
        held = travel
        // Raised already open: [held] is what slides it up.
        setShown(true)
        settle = scope.launch {
            // The sheet's window takes a frame or two to exist. Started before
            // then, the first stretch of the slide would happen to nothing.
            withTimeoutOrNull(LAYOUT_WAIT_MS) { snapshotFlow { laidOut }.first { it } }
            slide(from = travel, to = 0f)
            held = Float.NaN
        }
    }

    /**
     * The sheet's window, from the host as it appears.
     *
     * That window slides and fades itself in and out, and nothing in the app
     * can see it doing so: the player's artwork, placed to the pixel over the
     * mini player's cover, came in from below and left with a copy of itself
     * slipping down out of the cover. So its animations are off for the way in
     * and for every close this makes — and back on in between, so a player
     * closed by anything else (opening an album from it, say) still fades as
     * it always has.
     */
    fun attachWindow(window: Window?) {
        this.window = window ?: return
        windowAnimations = window.attributes.windowAnimations
        window.setWindowAnimations(0)
        windowRestore?.cancel()
        windowRestore = scope.launch {
            delay(WINDOW_ENTER_MS)
            window.setWindowAnimations(windowAnimations)
        }
    }

    /**
     * Off as a close starts, not as it ends: the change reaches the window
     * manager a little after it is asked for, and asked for on the frame the
     * sheet goes, the exit animation had already been chosen.
     */
    private fun quietWindow() {
        windowRestore?.cancel()
        window?.setWindowAnimations(0)
    }

    /** From the sheet: whatever asked for it to hide, this is how it goes. */
    fun close() {
        if (!shown() || closing) return
        closing = true
        quietWindow()
        settle?.cancel()
        val from = position().takeUnless { it.isNaN() } ?: run {
            dismissQuietly()
            return
        }
        val velocity = if (holding) 0f else recentSheetVelocity()
        held = from
        settle = scope.launch {
            slide(from = from, to = travel(), velocity = velocity.coerceAtLeast(0f))
            dismissQuietly()
        }
    }

    override fun drag(delta: Float) {
        if (!holding) {
            // Only upwards starts one, and only from a closed player: a
            // downward drag on the bar has nothing to pull.
            if (delta >= 0f || shown()) return
            settle?.cancel()
            closing = false
            held = travel()
            pullStartAt = held
            pullStartNanos = System.nanoTime()
            setShown(true)
        }
        settle?.cancel()
        held = (held + delta).coerceIn(0f, travel())
    }

    override fun release(velocity: Float) {
        if (!holding) return
        val travel = travel()
        // The finger's speed as it lifted, or across the whole pull if that
        // was faster — see [pullStartNanos].
        val seconds = (System.nanoTime() - pullStartNanos) / 1e9f
        val average = if (seconds > 0f) (held - pullStartAt) / seconds else 0f
        val speed = if (abs(average) > abs(velocity)) average else velocity
        val flick = abs(speed) >= flingVelocity
        // A flick decides on its own, either way; otherwise it is where the
        // finger left it.
        val open = if (flick) speed < 0f else held < travel / 2f
        if (!open) {
            closing = true
            quietWindow()
        }
        val from = held
        settle = scope.launch {
            // The finger's own speed goes into the spring, so the player
            // carries on as it was moving and then settles — a flick fast,
            // a slow pull gently.
            slide(from = from, to = if (open) 0f else travel, velocity = speed)
            if (open) held = Float.NaN else dismissQuietly()
        }
    }

    /** From the host, once the sheet has left composition. */
    fun onSheetGone() {
        closing = false
        sheetSampleAt = Float.NaN
        sheetVelocity = 0f
        settle?.cancel()
        windowRestore?.cancel()
        held = Float.NaN
        laidOut = false
        sheet = null
        window = null
    }

    private suspend fun slide(from: Float, to: Float, velocity: Float = 0f) {
        val travel = travel()
        animate(
            initialValue = from,
            targetValue = to,
            initialVelocity = velocity,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = SLIDE_STIFFNESS,
                visibilityThreshold = SLIDE_SETTLED_PX,
            ),
        ) { value, _ ->
            // Critically damped never bounces from rest, but thrown hard
            // enough at its target it can pass it once: never past open or
            // past the bar.
            held = value.coerceIn(0f, travel)
        }
    }

    /** Gone — with the window's own exit animation already off (see [quietWindow]). */
    private fun dismissQuietly() {
        quietWindow()
        setShown(false)
    }
}
