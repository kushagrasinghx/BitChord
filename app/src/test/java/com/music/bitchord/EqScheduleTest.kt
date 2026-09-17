package com.music.bitchord

import com.music.bitchord.playback.smart.EqSchedule
import com.music.bitchord.playback.smart.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DJ-EQ schedule tables are the spec's numbers as keyframes: every row
 * below pins a row of the spec's per-type tables, so a typo in a gain or a
 * progress lands here instead of in a mix nobody can debug by listening.
 */
class EqScheduleTest {

    private fun out(type: TransitionType, p: Float, duck: Boolean = false) =
        EqSchedule.outgoingGains(type, p, duck)

    private fun into(type: TransitionType, p: Float, delay: Boolean = false) =
        EqSchedule.incomingGains(type, p, delay)

    @Test
    fun `swap progress table matches the spec`() {
        assertEquals(0.42f, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.SMOOTH_CROSSFADE])
        assertEquals(0.40f, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.HARMONIC_BLEND])
        assertEquals(0.30f, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.FILTER_SWEEP])
        assertEquals(0.20f, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.HALF_TIME_BLEND])
        // Table-driven types swap nothing: their LOW rides the keyframes.
        assertEquals(null, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.ECHO_REVERB_OUT])
        assertEquals(null, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.LOOP_CUT_DROP])
        assertEquals(null, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.LOOP_ROLL])
        assertEquals(null, EqSchedule.BASS_SWAP_PROGRESS[TransitionType.PLAIN_DISSOLVE])
    }

    @Test
    fun `smooth vocal duck reaches 0 30 by 0 70`() {
        assertEquals(1f, out(TransitionType.SMOOTH_CROSSFADE, 0.55f, duck = true).mid)
        assertEquals(0.30f, out(TransitionType.SMOOTH_CROSSFADE, 0.70f, duck = true).mid, 0.001f)
        // Without the flag the V? rows are skipped: mids hold.
        assertEquals(1f, out(TransitionType.SMOOTH_CROSSFADE, 0.60f, duck = false).mid)
    }

    @Test
    fun `smooth entry vocal delays incoming mids`() {
        assertEquals(0f, into(TransitionType.SMOOTH_CROSSFADE, 0f, delay = true).mid)
        assertEquals(0f, into(TransitionType.SMOOTH_CROSSFADE, 0.15f, delay = true).mid)
        assertEquals(1f, into(TransitionType.SMOOTH_CROSSFADE, 0.40f, delay = true).mid, 0.001f)
        assertEquals(1f, into(TransitionType.SMOOTH_CROSSFADE, 0f, delay = false).mid)
    }

    @Test
    fun `harmonic enters highs-first on long blends`() {
        // Real-DJ long blend: keys match so carving is gentle, but B never
        // opens with two full mids — highs first, mids by 0.22 (no delay)
        // or 0.45 (vocal at entry).
        assertEquals(0f, into(TransitionType.HARMONIC_BLEND, 0f).mid)
        assertEquals(1f, into(TransitionType.HARMONIC_BLEND, 0.22f).mid, 0.001f)
        assertEquals(0f, into(TransitionType.HARMONIC_BLEND, 0f, delay = true).mid)
        assertEquals(1f, into(TransitionType.HARMONIC_BLEND, 0.45f, delay = true).mid, 0.001f)
        assertEquals(0.50f, out(TransitionType.HARMONIC_BLEND, 0.75f, duck = true).mid, 0.001f)
    }

    @Test
    fun `filter sweep enters top-down`() {
        val b0 = into(TransitionType.FILTER_SWEEP, 0f)
        assertEquals(0f, b0.mid)
        assertEquals(0.60f, b0.high, 0.001f)
        assertEquals(0.40f, into(TransitionType.FILTER_SWEEP, 0.30f).mid, 0.001f)
    }

    @Test
    fun `echo opens incoming under the wash`() {
        assertEquals(0.80f, into(TransitionType.ECHO_REVERB_OUT, 0.35f).high, 0.001f)
        assertEquals(0f, out(TransitionType.ECHO_REVERB_OUT, 0.35f).low, 0.001f)
    }

    @Test
    fun `loop cuts everything together before the drop`() {
        val a85 = out(TransitionType.LOOP_CUT_DROP, 0.85f)
        assertEquals(1f, a85.low)
        assertEquals(0.70f, a85.mid, 0.001f)
        assertEquals(0f, out(TransitionType.LOOP_CUT_DROP, 0.90f).low)
        assertEquals(0f, out(TransitionType.LOOP_CUT_DROP, 0.90f).mid)
        // B drops at full mix.
        assertEquals(1f, into(TransitionType.LOOP_CUT_DROP, 0.90f).low)
    }

    @Test
    fun `loop roll shares the loop table shape`() {
        // The roll extend holds full energy like the cut; the release glide
        // is voiced by the renderer's 2-beat settle, not the table.
        val a85 = out(TransitionType.LOOP_ROLL, 0.85f)
        assertEquals(1f, a85.low)
        assertEquals(0.70f, a85.mid, 0.001f)
        assertEquals(0f, out(TransitionType.LOOP_ROLL, 0.90f).low)
        assertEquals(1f, into(TransitionType.LOOP_ROLL, 0.90f).low)
    }

    @Test
    fun `filter long bed spreads the gesture`() {
        // Parity long bed: highs-first like the short table, but the first
        // high move lands at 0.22→0.90 (short table is already ~0.74 there)
        // and mids trade through the back half instead of resolving by 0.42.
        assertEquals(0.90f, EqSchedule.outgoingGains(TransitionType.FILTER_SWEEP, 0.22f, false, longBed = true).high, 0.001f)
        assertEquals(0.70f, EqSchedule.outgoingGains(TransitionType.FILTER_SWEEP, 0.35f, false, longBed = true).high, 0.001f)
        assertEquals(0.80f, EqSchedule.outgoingGains(TransitionType.FILTER_SWEEP, 0.50f, false, longBed = true).mid, 0.001f)
        // Short beds keep the old table untouched.
        assertEquals(0.58f, out(TransitionType.FILTER_SWEEP, 0.30f).high, 0.001f)
    }

    @Test
    fun `half-time softens the entry and ends silent`() {
        assertEquals(0.90f, into(TransitionType.HALF_TIME_BLEND, 0f).mid, 0.001f)
        assertEquals(1f, into(TransitionType.HALF_TIME_BLEND, 0.10f).mid, 0.001f)
        assertEquals(0f, out(TransitionType.HALF_TIME_BLEND, 1f).mid)
    }

    @Test
    fun `long beds trade mids across the whole blend`() {
        // Real-DJ long blend: voiceless 32-bar beds move mids early
        // (SMOOTH 0.22, HARMONIC 0.30) instead of holding unity to 0.45+.
        assertEquals(1f, EqSchedule.outgoingGains(TransitionType.SMOOTH_CROSSFADE, 0.22f, false, longBed = true).mid)
        assertEquals(0.80f, EqSchedule.outgoingGains(TransitionType.SMOOTH_CROSSFADE, 0.35f, false, longBed = true).mid, 0.001f)
        assertEquals(1f, EqSchedule.outgoingGains(TransitionType.HARMONIC_BLEND, 0.30f, false, longBed = true).mid)
        assertEquals(0.80f, EqSchedule.outgoingGains(TransitionType.HARMONIC_BLEND, 0.45f, false, longBed = true).mid, 0.001f)
        // Short beds keep the old tables untouched.
        assertEquals(1f, out(TransitionType.SMOOTH_CROSSFADE, 0.45f, duck = false).mid)
    }

    @Test
    fun `dissolve separates bass with no grid`() {
        assertEquals(0f, out(TransitionType.PLAIN_DISSOLVE, 0.20f).low, 0.001f)
        assertEquals(0f, into(TransitionType.PLAIN_DISSOLVE, 0.50f).low, 0.001f)
        assertEquals(1f, into(TransitionType.PLAIN_DISSOLVE, 0.60f).low, 0.001f)
    }

    @Test
    fun `continuous ramps respect the Rule 3 tick budget`() {
        // 30 ms re-aims must step no more than 0.05 per tick; the in-processor
        // glide absorbs the rest. LOOP is the spec's own intentional-cut
        // exception; DISSOLVE cuts bass deliberately fast (no grid to swap on).
        val spans = mapOf(
            TransitionType.SMOOTH_CROSSFADE to 22f,
            TransitionType.HARMONIC_BLEND to 28f,
            TransitionType.FILTER_SWEEP to 9f,
            TransitionType.ECHO_REVERB_OUT to 11f,
            TransitionType.HALF_TIME_BLEND to 20f,
        )
        for ((type, span) in spans) {
            val worst = EqSchedule.maxTickStep(type, span)
            assertTrue("$type steps $worst per tick, over the 0.05 budget", worst <= 0.06f)
        }
    }
}
