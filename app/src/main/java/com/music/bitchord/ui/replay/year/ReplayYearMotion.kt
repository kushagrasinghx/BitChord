package com.music.bitchord.ui.replay.year

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.replay.HeadlineRun
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The time on the card being shown, in ms since it arrived.
 *
 * Every animation on a card is a function of this one number rather than an
 * animation of its own. That is what lets holding the card freeze the whole
 * scene at once, and the reason it reads as a read of [elapsed] inside layer
 * and draw lambdas: those run again on a new frame without composing anything,
 * so a card full of moving parts costs one invalidated layer per part.
 */
class SlideClock(
    val elapsed: () -> Float,
    val reduceMotion: Boolean,
    /** Where this card's haptics are written down; null on a card that is leaving. */
    val beats: HapticBeats? = null,
) {
    /**
     * Asks for [haptic] at [atMs] into the card. Called by each animation as it
     * is set up, with the moment its own movement lands, so the hand feels
     * exactly what the eye sees and nothing is timed twice.
     */
    fun beat(atMs: Long, haptic: Haptic) {
        beats?.add(atMs, haptic)
    }
}

val LocalSlideClock = compositionLocalOf { SlideClock({ Float.MAX_VALUE }, reduceMotion = true) }

/**
 * One card's haptics: what to strike, and when, in ms into the card.
 *
 * Filled in while the card composes, by the animations themselves, and played
 * by the story as the card's clock passes each one. Two things landing at the
 * same moment are one beat, the stronger of the two; composing again writes
 * the same beats again, which changes nothing. Main thread only.
 */
class HapticBeats {
    private val at = java.util.TreeMap<Long, Haptic>()

    fun add(atMs: Long, haptic: Haptic) {
        val had = at[atMs]
        if (had == null || weight(haptic) > weight(had)) at[atMs] = haptic
    }

    /**
     * The strongest beat after [afterMs] up to and including [untilMs]: the one
     * to play for a frame that covered that stretch. Several in one frame are
     * closer together than the motor can tell apart anyway.
     */
    fun strongestBetween(afterMs: Float, untilMs: Float): Haptic? {
        if (untilMs <= afterMs) return null
        val from = kotlin.math.floor(afterMs).toLong() + 1
        val to = kotlin.math.floor(untilMs).toLong()
        if (to < from) return null
        return at.subMap(from, true, to, true).values.maxByOrNull(::weight)
    }

    private fun weight(haptic: Haptic): Int = when (haptic) {
        Haptic.Slam -> 3
        Haptic.Tap -> 2
        else -> 1
    }
}

// ── Curves ──────────────────────────────────────────────────────────────────

/** How far through [start]..[start]+[duration] the time [t] is, 0..1. */
fun window(t: Float, start: Long, duration: Long): Float =
    ((t - start) / duration.coerceAtLeast(1)).coerceIn(0f, 1f)

fun easeOutCubic(x: Float): Float = 1f - (1f - x).let { it * it * it }

fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

/** Past the end and back: the spring-like overshoot the entrances land with. */
fun easeOutBack(x: Float, overshoot: Float = 1.70158f): Float {
    val c3 = overshoot + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + overshoot * y * y
}

// ── Entrances ───────────────────────────────────────────────────────────────

/**
 * Arrives at [start]: rises from [fromY] (or slides from [fromX]), grows from
 * [fromScale], turns from [fromRotation], and lands with a small overshoot.
 * With motion reduced it only fades.
 */
fun Modifier.enter(
    clock: SlideClock,
    start: Long,
    duration: Long = 560,
    fromY: Dp = 36.dp,
    fromX: Dp = 0.dp,
    fromScale: Float = 1f,
    fromRotation: Float = 0f,
    haptic: Haptic? = Haptic.Tick,
): Modifier {
    // Felt as it lands: the overshoot curve first reaches its place a little
    // under halfway through.
    if (haptic != null) clock.beat(start + duration * 45 / 100, haptic)
    return enterLayer(clock, start, duration, fromY, fromX, fromScale, fromRotation)
}

private fun Modifier.enterLayer(
    clock: SlideClock,
    start: Long,
    duration: Long,
    fromY: Dp,
    fromX: Dp,
    fromScale: Float,
    fromRotation: Float,
): Modifier = graphicsLayer {
    val p = window(clock.elapsed(), start, duration)
    alpha = easeOutCubic((p * 1.6f).coerceAtMost(1f))
    if (clock.reduceMotion) return@graphicsLayer
    val e = easeOutBack(p)
    translationY = fromY.toPx() * (1f - e)
    translationX = fromX.toPx() * (1f - e)
    val s = fromScale + (1f - fromScale) * e
    scaleX = s
    scaleY = s
    rotationZ = fromRotation * (1f - e)
}

/**
 * The big reveal: drops in from far too big, lands hard, and shakes the frame
 * for a moment as it hits.
 */
fun Modifier.slam(clock: SlideClock, start: Long, duration: Long = 480, from: Float = 2.6f): Modifier {
    // The thud lands when it first hits full size, the moment the shake starts from.
    clock.beat(start + duration * 6 / 10, Haptic.Slam)
    return slamLayer(clock, start, duration, from)
}

private fun Modifier.slamLayer(clock: SlideClock, start: Long, duration: Long, from: Float): Modifier = graphicsLayer {
    val t = clock.elapsed()
    val p = window(t, start, duration)
    alpha = easeOutCubic((p * 2.2f).coerceAtMost(1f))
    if (clock.reduceMotion) return@graphicsLayer
    val s = from + (1f - from) * easeOutBack(p, overshoot = 1.2f)
    scaleX = s
    scaleY = s
    val after = t - start - duration
    if (after in 0f..SHAKE_MS) {
        val decay = exp(-after / 90f)
        translationX = sin(after / 14f) * 14.dp.toPx() * decay
        rotationZ = sin(after / 19f) * 2.2f * decay
    }
}

/** The whole card jolts as a [slam] lands under it. */
fun Modifier.shake(clock: SlideClock, at: Long): Modifier = graphicsLayer {
    if (clock.reduceMotion) return@graphicsLayer
    val after = clock.elapsed() - at
    if (after in 0f..SHAKE_MS) {
        val decay = exp(-after / 110f)
        translationX = sin(after / 11f) * 7.dp.toPx() * decay
        translationY = cos(after / 13f) * 5.dp.toPx() * decay
    }
}

/** A slow swell once [start] has passed, so nothing on a card is ever quite still. */
fun Modifier.breathe(clock: SlideClock, start: Long, amount: Float = 0.025f, periodMs: Float = 2_600f): Modifier =
    graphicsLayer {
        if (clock.reduceMotion) return@graphicsLayer
        val after = clock.elapsed() - start
        if (after <= 0f) return@graphicsLayer
        val ramp = (after / 600f).coerceAtMost(1f)
        val s = 1f + amount * ramp * sin(2f * PI.toFloat() * after / periodMs)
        scaleX = s
        scaleY = s
    }

// ── Words ───────────────────────────────────────────────────────────────────

/**
 * A sentence that arrives a word at a time, each word rising and turning into
 * place a beat after the one before it. The emphasised runs are set heavy and
 * the rest light, as on the ordinary Replay story, so the number is still the
 * loud part of an ordinary sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KineticText(
    runs: List<HeadlineRun>,
    start: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    stagger: Long = 55,
    color: Color = Color.White,
    center: Boolean = false,
) {
    val clock = LocalSlideClock.current
    val words = remember(runs) {
        runs.flatMap { run ->
            run.text.split(' ').filter { it.isNotEmpty() }.map { it to run.bold }
        }
    }
    // Each word is its own text, carrying its side bearings into the gap, so the
    // gap is set well under a typed space to read as one.
    val gap = style.fontSize.value.dp * 0.16f
    FlowRow(
        modifier = modifier,
        horizontalArrangement = if (center) Arrangement.spacedBy(gap, Alignment.CenterHorizontally) else Arrangement.spacedBy(gap),
    ) {
        words.forEachIndexed { index, (word, bold) ->
            Text(
                text = word,
                style = style,
                fontWeight = if (bold) FontWeight.W900 else FontWeight.W500,
                color = if (bold) color else color.copy(alpha = 0.82f),
                modifier = Modifier.enter(
                    clock,
                    start = start + index * stagger,
                    fromY = 30.dp,
                    fromRotation = if (index % 2 == 0) 7f else -5f,
                ),
            )
        }
    }
}

/**
 * A number running up to [target] between [start] and [start]+[duration],
 * fast at first and settling onto the figure. Only this text recomposes as it
 * runs, and only when the shown figure changes.
 */
@Composable
fun CountUp(
    target: Long,
    start: Long,
    duration: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    format: (Long) -> String,
) {
    val clock = LocalSlideClock.current
    // Ticks that slow down as the figure does: one each time it passes another
    // fourteenth of the way, on the same curve it counts on, then a tap as it lands.
    for (k in 1 until COUNT_TICKS) {
        val p = 1.0 - Math.cbrt(1.0 - k.toDouble() / COUNT_TICKS)
        clock.beat(start + (duration * p).toLong(), Haptic.Tick)
    }
    clock.beat(start + duration, Haptic.Tap)
    val shown by remember(target, start, duration, clock) {
        derivedStateOf {
            val p = if (clock.reduceMotion) 1f else easeOutCubic(window(clock.elapsed(), start, duration))
            (target * p).toLong()
        }
    }
    Text(text = format(shown), style = style, color = color, modifier = modifier)
}

// ── Decoration ──────────────────────────────────────────────────────────────

/**
 * A burst of confetti from the middle of its box at [at]: flecks and dots in
 * [colors] flung out, falling, turning and fading over a second and a half.
 * Drawn in one layer from arrays made once, so the burst allocates nothing per
 * frame. Nothing at all with motion reduced.
 */
@Composable
fun Burst(at: Long, colors: List<Color>, modifier: Modifier = Modifier, count: Int = 56, seed: Int = 7) {
    val clock = LocalSlideClock.current
    if (clock.reduceMotion) return
    val parts = remember(count, seed) {
        val random = Random(seed)
        FloatArray(count * 5) { i ->
            when (i % 5) {
                0 -> random.nextFloat() * 2f * PI.toFloat() // direction
                1 -> 0.45f + random.nextFloat() * 0.75f // speed, in box half-widths per second
                2 -> random.nextFloat() * 360f // spin
                3 -> 4f + random.nextFloat() * 7f // size, dp
                else -> random.nextFloat() // colour pick and shape
            }
        }
    }
    Spacer(
        modifier.drawBehind {
            val t = (clock.elapsed() - at) / 1000f
            if (t < 0f || t > BURST_SECONDS || colors.isEmpty()) return@drawBehind
            val fade = 1f - (t / BURST_SECONDS).let { it * it }
            val reach = size.minDimension / 2f
            val gravity = reach * 0.9f
            for (i in 0 until count) {
                val b = i * 5
                val angle = parts[b]
                val speed = parts[b + 1] * reach * 1.7f
                val drag = 1f - exp(-t * 3.2f)
                val x = center.x + cos(angle) * speed * drag / 3.2f
                val y = center.y + sin(angle) * speed * drag / 3.2f + gravity * t * t * 0.5f
                val side = parts[b + 3].dp.toPx()
                val color = colors[(parts[b + 4] * colors.size).toInt().coerceIn(0, colors.lastIndex)]
                    .copy(alpha = fade)
                if (parts[b + 4] > 0.5f) {
                    rotate(parts[b + 2] + t * 540f, Offset(x, y)) {
                        drawRect(color, Offset(x - side / 2f, y - side / 4f), androidx.compose.ui.geometry.Size(side, side / 2f))
                    }
                } else {
                    drawCircle(color, side / 2.6f, Offset(x, y))
                }
            }
        },
    )
}

/**
 * Slow rings turning behind a card's subject: the colour of the card, drawn
 * thin, so the space around the artwork has some life without competing
 * with it.
 */
@Composable
fun OrbitRings(start: Long, color: Color, modifier: Modifier = Modifier) {
    val clock = LocalSlideClock.current
    Spacer(
        modifier.drawBehind {
            val t = clock.elapsed()
            val grow = easeOutCubic(window(t, start, 900))
            if (grow <= 0f) return@drawBehind
            val turn = if (clock.reduceMotion) 0f else t / 40f
            val base = size.minDimension / 2f
            for (i in 0 until 3) {
                val radius = base * (0.62f + i * 0.17f) * grow
                rotate(turn * (if (i % 2 == 0) 1f else -0.7f) + i * 40f) {
                    drawArc(
                        color = color.copy(alpha = 0.55f - i * 0.14f),
                        startAngle = 0f,
                        sweepAngle = 250f - i * 50f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = (2.5f - i * 0.5f).dp.toPx()),
                    )
                }
            }
        },
    )
}

private const val SHAKE_MS = 420f
private const val BURST_SECONDS = 1.6f

private const val COUNT_TICKS = 14
