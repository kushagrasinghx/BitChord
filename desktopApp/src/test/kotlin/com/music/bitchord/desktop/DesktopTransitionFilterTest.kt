package com.music.bitchord.desktop

import com.music.bitchord.playback.TransitionFilter
import com.music.bitchord.playback.smart.TransitionPlan
import com.music.bitchord.playback.smart.TransitionStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/** The transition filter, measured rather than assumed. */
class DesktopTransitionFilterTest {

    private val rate = 44_100
    private val channels = 2

    @Test
    fun `a parked filter passes audio through untouched`() {
        val filter = TransitionFilter().apply { configure(channels, rate) }
        val tone = tone(1_000.0, 0.2)
        val before = rms(tone)

        val out = run(filter, tone)

        assertTrue(filter.parked, "a filter nobody aimed should be parked")
        assertTrue(abs(rms(out) - before) < before * 0.01, "a parked filter changed the signal")
    }

    @Test
    fun `a closing low-pass removes the top and keeps the bottom`() {
        val high = tone(8_000.0, 0.5)
        val low = tone(200.0, 0.5)

        val onHigh = rms(run(lowPassed(), high))
        val onLow = rms(run(lowPassed(), low))

        // 300 Hz, four poles: 8 kHz is far outside and should be all but gone, while 200 Hz sits
        // inside the passband.
        assertTrue(onHigh < rms(high) * 0.2, "the low-pass left the top in: ${onHigh / rms(high)}")
        assertTrue(onLow > rms(low) * 0.5, "the low-pass ate the bottom: ${onLow / rms(low)}")
    }

    @Test
    fun `vocal separation aims the two sides in opposite directions`() {
        // A pair that the planner says is singing over itself.
        val plan = TransitionPlan(
            transitionStyle = TransitionStyle.EQUAL_POWER,
            vocalOverlap = 1.0,
            fadeSeconds = 6.0,
        )
        val outgoing = TransitionFilter().apply { configure(channels, rate) }
        val incoming = TransitionFilter().apply { configure(channels, rate) }

        DesktopTransitionRide.aim(plan, progress = 0.5f, outgoing = outgoing, incoming = incoming)
        repeat(400) {
            outgoing.advance()
            incoming.advance()
        }

        // The outgoing track loses its top; the incoming one enters with its body lifted out.
        assertTrue(outgoing.active, "the outgoing filter never engaged")
        assertTrue(incoming.active, "the incoming filter never engaged")

        val bass = tone(120.0, 0.5)
        assertTrue(
            rms(run(incoming, bass)) < rms(bass) * 0.9,
            "the incoming track kept its low end, so nothing was separated",
        )
    }

    @Test
    fun `no measured collision leaves both sides open`() {
        val plan = TransitionPlan(transitionStyle = TransitionStyle.EQUAL_POWER, vocalOverlap = 0.0)
        val outgoing = TransitionFilter().apply { configure(channels, rate) }
        val incoming = TransitionFilter().apply { configure(channels, rate) }

        DesktopTransitionRide.aim(plan, progress = 0.5f, outgoing = outgoing, incoming = incoming)
        repeat(400) {
            outgoing.advance()
            incoming.advance()
        }

        // A pair that does not collide, or either track lacking a vocal mask, has to sound exactly
        // as it did before any of this existed.
        assertTrue(!outgoing.active && !incoming.active, "an unmeasured pair was filtered anyway")
    }

    @Test
    fun `a bass swap hands the low end over once, in the right direction`() {
        val plan = TransitionPlan(
            transitionStyle = TransitionStyle.DJ_BLEND,
            bassSwap = true,
            bassSwapFraction = 0.5,
            vocalOverlap = 0.0,
        )
        val bass = tone(80.0, 0.4)

        // Early in the overlap the low end belongs to the outgoing track: it keeps its bass, the
        // incoming one has none.
        val early = sides(plan, progress = 0.05f)
        assertTrue(
            rms(run(early.first, bass)) > rms(run(early.second, bass)),
            "before the swap the incoming track already had the low end",
        )

        // Late in the overlap it has changed hands, exactly once.
        val late = sides(plan, progress = 0.95f)
        assertTrue(
            rms(run(late.second, bass)) > rms(run(late.first, bass)),
            "after the swap the outgoing track still had the low end",
        )
    }

    @Test
    fun `the low end changes hands once and does not come back`() {
        val plan = TransitionPlan(
            transitionStyle = TransitionStyle.DJ_BLEND,
            bassSwap = true,
            bassSwapFraction = 0.5,
        )
        val bass = tone(80.0, 0.3)

        // Sampled across the overlap: the incoming track's low end only ever rises and the outgoing
        // track's only ever falls.
        val incomingLow = (0..10).map { rms(run(sides(plan, it / 10f).second, bass)) }
        val outgoingLow = (0..10).map { rms(run(sides(plan, it / 10f).first, bass)) }

        assertTrue(
            incomingLow.zipWithNext().all { (a, b) -> b >= a - 1e-4f },
            "the incoming low end went backwards: $incomingLow",
        )
        assertTrue(
            outgoingLow.zipWithNext().all { (a, b) -> b <= a + 1e-4f },
            "the outgoing low end came back: $outgoingLow",
        )
        // And it genuinely travels: ends held apart rather than both sitting open.
        assertTrue(incomingLow.last() > incomingLow.first() * 2, "the incoming track never gained the low end")
        assertTrue(outgoingLow.last() < outgoingLow.first() * 0.5, "the outgoing track never lost the low end")
    }

    /** The two filters as the ride aims them at [progress], glided to target. */
    private fun sides(plan: TransitionPlan, progress: Float): Pair<TransitionFilter, TransitionFilter> {
        val outgoing = TransitionFilter().apply { configure(channels, rate) }
        val incoming = TransitionFilter().apply { configure(channels, rate) }
        DesktopTransitionRide.aim(plan, progress, outgoing, incoming)
        repeat(600) {
            outgoing.advance()
            incoming.advance()
        }
        return outgoing to incoming
    }

    private fun lowPassed() = TransitionFilter().apply {
        configure(channels, rate)
        setCutoffs(300f, TransitionFilter.OFF_HZ)
        // Glide to the target before measuring; the ride reaches it over a fade.
        repeat(600) { advance() }
    }

    private fun run(filter: TransitionFilter, samples: FloatArray): FloatArray {
        val out = FloatArray(samples.size)
        var index = 0
        while (index < samples.size) {
            filter.advance()
            val stop = minOf(samples.size, index + TransitionFilter.GLIDE_FRAMES * channels)
            while (index < stop) {
                out[index] = filter.filter(index % channels, samples[index])
                index++
            }
        }
        // The opening transient is the filter settling, not its response.
        return out.copyOfRange(out.size / 2, out.size)
    }

    private fun tone(hz: Double, seconds: Double): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * channels) { index ->
            (sin(2.0 * PI * hz * (index / channels) / rate) * 0.5).toFloat()
        }
    }

    private fun rms(samples: FloatArray): Float {
        var sum = 0.0
        for (value in samples) sum += value.toDouble() * value
        return sqrt(sum / samples.size).toFloat()
    }
}
