package com.music.bitchord.desktop

import com.music.bitchord.desktop.DesktopPartySync.Companion.decideSeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the playhead gets moved to follow the party.
 *
 * A seek is audible, so the rules matter more than the arithmetic: a deliberate control aligns at
 * once, ordinary drift has to be both large and persistent, and a correction that just fired
 * cannot fire again immediately.
 */
class DesktopPartySyncTest {

    @Test
    fun `a new control aligns at once when it is meaningfully out`() {
        val decision = decideSeek(
            drift = 900, controlSeq = 7, alignedSeq = 6, strikes = 0, nowMs = 0, cooldownUntilMs = 0,
        )
        assertTrue(decision.seek, "a fresh control should align immediately")
        assertEquals(7, decision.alignedSeq)
    }

    @Test
    fun `a new control that already lines up does not seek`() {
        val decision = decideSeek(
            drift = 40, controlSeq = 7, alignedSeq = 6, strikes = 0, nowMs = 0, cooldownUntilMs = 0,
        )
        assertFalse(decision.seek, "40ms is inside the align tolerance")
        assertEquals(7, decision.alignedSeq, "the control is still marked as handled")
    }

    @Test
    fun `small drift is left alone and clears the strikes`() {
        val decision = decideSeek(
            drift = 800, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 0, cooldownUntilMs = 0,
        )
        assertFalse(decision.seek)
        assertEquals(0, decision.strikes, "a reading inside the limit resets the count")
    }

    @Test
    fun `one large reading is not enough on its own`() {
        val decision = decideSeek(
            drift = 5_000, controlSeq = 7, alignedSeq = 7, strikes = 0, nowMs = 0, cooldownUntilMs = 0,
        )
        assertFalse(decision.seek, "a single stall should not cause an audible seek")
        assertEquals(1, decision.strikes)
    }

    @Test
    fun `a second large reading corrects and opens a cooldown`() {
        val decision = decideSeek(
            drift = 5_000, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 10_000, cooldownUntilMs = 0,
        )
        assertTrue(decision.seek)
        assertEquals(0, decision.strikes, "the count restarts after a correction")
        assertTrue(decision.cooldownUntilMs > 10_000, "a correction must not be able to repeat at once")
    }

    @Test
    fun `nothing fires while the cooldown is still running`() {
        val decision = decideSeek(
            drift = 5_000, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 1_000, cooldownUntilMs = 6_000,
        )
        assertFalse(decision.seek, "still inside the cooldown")
        assertEquals(1, decision.strikes, "and the count is held rather than advanced")
    }

    @Test
    fun `drift is corrected in either direction`() {
        val behind = decideSeek(
            drift = -5_000, controlSeq = 7, alignedSeq = 7, strikes = 1, nowMs = 10_000, cooldownUntilMs = 0,
        )
        assertTrue(behind.seek, "running behind the party is as wrong as running ahead")
    }
}
