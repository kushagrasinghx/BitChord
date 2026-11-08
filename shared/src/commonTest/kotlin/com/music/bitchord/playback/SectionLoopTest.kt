package com.music.bitchord.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SectionLoopTest {

    @Test
    fun aGreaterThanOrEqualToBRejected() {
        // Direct instantiation must throw IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            SectionLoop(startMs = 5000L, endMs = 5000L)
        }
        assertFailsWith<IllegalArgumentException> {
            SectionLoop(startMs = 6000L, endMs = 5000L)
        }
        assertFailsWith<IllegalArgumentException> {
            SectionLoop(startMs = 5000L, endMs = 5200L) // Gap < MIN_GAP_MS
        }

        // Factory .from() must safely return null
        assertNull(SectionLoop.from(startMs = 5000L, endMs = 5000L))
        assertNull(SectionLoop.from(startMs = 6000L, endMs = 5000L))
        assertNull(SectionLoop.from(startMs = 5000L, endMs = 5200L))
    }

    @Test
    fun bPastDurationClamped() {
        val durationMs = 30_000L

        // End position (45s) past track duration (30s) is clamped to duration (30s)
        val loop = SectionLoop.from(startMs = 10_000L, endMs = 45_000L, durationMs = durationMs)
        assertNotNull(loop)
        assertEquals(10_000L, loop.startMs)
        assertEquals(30_000L, loop.endMs)

        // If clamping to duration makes the gap < MIN_GAP_MS, it is rejected
        val tooCloseToEnd = SectionLoop.from(startMs = 29_800L, endMs = 35_000L, durationMs = durationMs)
        assertNull(tooCloseToEnd)

        // Start past duration is rejected
        val startPastDuration = SectionLoop.from(startMs = 32_000L, endMs = 40_000L, durationMs = durationMs)
        assertNull(startPastDuration)
    }

    @Test
    fun wrapExactlyAtB() {
        val loop = SectionLoop(startMs = 10_000L, endMs = 25_000L)

        // Exactly at point B, wrap occurs and target is startMs
        val target = evaluateLoopWrap(positionMs = 25_000L, loop = loop, isPlaying = true)
        assertEquals(10_000L, target)
    }

    @Test
    fun boundaryCasesWithinAFewMsOfB() {
        val loop = SectionLoop(startMs = 5000L, endMs = 20_000L)

        // 5ms before B: no wrap
        assertNull(evaluateLoopWrap(positionMs = 19_995L, loop = loop, isPlaying = true))

        // 1ms before B: no wrap
        assertNull(evaluateLoopWrap(positionMs = 19_999L, loop = loop, isPlaying = true))

        // Exactly at B: wraps to A
        assertEquals(5000L, evaluateLoopWrap(positionMs = 20_000L, loop = loop, isPlaying = true))

        // 1ms past B: wraps to A
        assertEquals(5000L, evaluateLoopWrap(positionMs = 20_001L, loop = loop, isPlaying = true))

        // 20ms past B (one ticker interval): wraps to A
        assertEquals(5000L, evaluateLoopWrap(positionMs = 20_020L, loop = loop, isPlaying = true))

        // Deep inside loop: no wrap
        assertNull(evaluateLoopWrap(positionMs = 5000L, loop = loop, isPlaying = true))
        assertNull(evaluateLoopWrap(positionMs = 12_000L, loop = loop, isPlaying = true))

        // Before start point A: no wrap (plays forward towards loop)
        assertNull(evaluateLoopWrap(positionMs = 1000L, loop = loop, isPlaying = true))
    }

    @Test
    fun noWrapWhenPaused() {
        val loop = SectionLoop(startMs = 10_000L, endMs = 20_000L)

        // Paused exactly at B
        assertNull(evaluateLoopWrap(positionMs = 20_000L, loop = loop, isPlaying = false))

        // Paused past B (e.g. user scrubbed or paused near end)
        assertNull(evaluateLoopWrap(positionMs = 21_000L, loop = loop, isPlaying = false))

        // Paused inside loop
        assertNull(evaluateLoopWrap(positionMs = 15_000L, loop = loop, isPlaying = false))
    }

    @Test
    fun speedChangesHandledConsistently() {
        val loop = SectionLoop(startMs = 10_000L, endMs = 20_000L)
        val tickIntervalMs = 20L

        // Normal 1.0x speed: 20ms wall-clock advances 20ms media time
        val pos1x = 19_990L + (tickIntervalMs * 1.0).toLong() // 20_010L
        assertEquals(10_000L, evaluateLoopWrap(pos1x, loop, isPlaying = true))

        // 1.5x speed: 20ms wall-clock advances 30ms media time
        val pos15x = 19_980L + (tickIntervalMs * 1.5).toLong() // 20_010L
        assertEquals(10_000L, evaluateLoopWrap(pos15x, loop, isPlaying = true))

        // 2.0x speed: 20ms wall-clock advances 40ms media time
        val pos2x = 19_980L + (tickIntervalMs * 2.0).toLong() // 20_020L
        assertEquals(10_000L, evaluateLoopWrap(pos2x, loop, isPlaying = true))

        // 0.5x speed: 20ms wall-clock advances 10ms media time
        val pos05x = 19_995L + (tickIntervalMs * 0.5).toLong() // 20_005L
        assertEquals(10_000L, evaluateLoopWrap(pos05x, loop, isPlaying = true))
    }

    @Test
    fun stateMachineBeginSettingFlow() {
        val machine = SectionLoopStateMachine()
        assertEquals(SectionLoopState.Idle, machine.state)

        machine.beginSetting()
        assertEquals(SectionLoopState.WaitingForA, machine.state)
        assertFalse(machine.isLoopActive)

        // Cannot set B while WaitingForA
        assertFalse(machine.setPointB(10_000L))
        assertEquals(SectionLoopState.WaitingForA, machine.state)

        // Set A transitions to SettingA
        assertTrue(machine.setPointA(3000L))
        assertEquals(SectionLoopState.SettingA(3000L), machine.state)

        // Set B transitions to Active
        assertTrue(machine.setPointB(8000L))
        assertTrue(machine.isLoopActive)
        assertEquals(3000L, machine.activeLoop?.startMs)
        assertEquals(8000L, machine.activeLoop?.endMs)
    }

    @Test
    fun stateMachineManualSettingFlow() {
        val machine = SectionLoopStateMachine()
        assertEquals(SectionLoopState.Idle, machine.state)
        assertNull(machine.activeLoop)
        assertFalse(machine.isLoopActive)

        // Cannot set B while Idle
        assertFalse(machine.setPointB(10_000L))
        assertEquals(SectionLoopState.Idle, machine.state)

        // Set A at 5000ms
        assertTrue(machine.setPointA(5000L))
        assertEquals(SectionLoopState.SettingA(5000L), machine.state)
        assertFalse(machine.isLoopActive)

        // Attempt invalid B (B <= A or gap < 500ms)
        assertFalse(machine.setPointB(4000L))
        assertEquals(SectionLoopState.SettingA(5000L), machine.state)
        assertFalse(machine.setPointB(5300L)) // 300ms gap
        assertEquals(SectionLoopState.SettingA(5000L), machine.state)

        // Set valid B at 12000ms
        assertTrue(machine.setPointB(12_000L))
        assertTrue(machine.isLoopActive)
        val active = machine.activeLoop
        assertNotNull(active)
        assertEquals(5000L, active.startMs)
        assertEquals(12_000L, active.endMs)

        // Update B while active
        assertTrue(machine.setPointB(15_000L))
        assertEquals(15_000L, machine.activeLoop?.endMs)

        // Clear manually
        machine.clear()
        assertEquals(SectionLoopState.Idle, machine.state)
        assertNull(machine.activeLoop)
    }

    @Test
    fun loopClearedOnTrackChange() {
        val machine = SectionLoopStateMachine()
        assertTrue(machine.setRange(5000L, 20_000L))
        assertTrue(machine.isLoopActive)
        assertNotNull(machine.activeLoop)

        // Track changes
        machine.onTrackChanged()

        // Loop must be cleared and inactive
        assertEquals(SectionLoopState.Idle, machine.state)
        assertNull(machine.activeLoop)
        assertFalse(machine.isLoopActive)

        // Wrap evaluation must now return null
        assertNull(evaluateLoopWrap(positionMs = 25_000L, loop = machine.activeLoop, isPlaying = true))
    }

    @Test
    fun negativeStartClampedToZero() {
        val loop = SectionLoop.from(startMs = -500L, endMs = 3000L)
        assertNotNull(loop)
        assertEquals(0L, loop.startMs)
        assertEquals(3000L, loop.endMs)

        assertFailsWith<IllegalArgumentException> {
            SectionLoop(startMs = -1L, endMs = 2000L)
        }
    }
}
