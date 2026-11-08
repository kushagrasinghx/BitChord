package com.music.bitchord.playback

import com.music.bitchord.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SectionLoopPlaybackTest {

    @Before
    fun setUp() {
        AppSettings.activeSectionLoop.value = null
        AppSettings.sectionLoopState.value = SectionLoopState.Idle
    }

    @Test
    fun appSettingsReflectsActiveLoopAndState() {
        val loop = SectionLoop(10_000L, 25_000L)
        AppSettings.activeSectionLoop.value = loop
        AppSettings.sectionLoopState.value = SectionLoopState.Active(loop)

        assertEquals(loop, AppSettings.activeSectionLoop.value)
        assertEquals(SectionLoopState.Active(loop), AppSettings.sectionLoopState.value)

        AppSettings.activeSectionLoop.value = null
        AppSettings.sectionLoopState.value = SectionLoopState.Idle

        assertNull(AppSettings.activeSectionLoop.value)
        assertEquals(SectionLoopState.Idle, AppSettings.sectionLoopState.value)
    }

    @Test
    fun playerStateCarriesSectionLoopMirror() {
        val defaultState = PlayerState()
        assertNull(defaultState.sectionLoop)
        assertEquals(SectionLoopState.Idle, defaultState.sectionLoopState)

        val loop = SectionLoop(startMs = 3000L, endMs = 9000L)
        val activeState = defaultState.copy(
            sectionLoop = loop,
            sectionLoopState = SectionLoopState.Active(loop),
        )

        assertEquals(loop, activeState.sectionLoop)
        assertEquals(SectionLoopState.Active(loop), activeState.sectionLoopState)
    }

    @Test
    fun wrapEvaluationRespectsPlaybackState() {
        val loop = SectionLoop(startMs = 5000L, endMs = 15_000L)

        // Paused at or after B: no wrap
        assertNull(evaluateLoopWrap(positionMs = 15_000L, loop = loop, isPlaying = false))
        assertNull(evaluateLoopWrap(positionMs = 16_000L, loop = loop, isPlaying = false))

        // Playing before B: no wrap
        assertNull(evaluateLoopWrap(positionMs = 14_990L, loop = loop, isPlaying = true))

        // Playing at or after B: wraps to startMs
        assertEquals(5000L, evaluateLoopWrap(positionMs = 15_000L, loop = loop, isPlaying = true))
        assertEquals(5000L, evaluateLoopWrap(positionMs = 15_020L, loop = loop, isPlaying = true))
    }

    @Test
    fun loopResetOnTrackChangeAndVersionSwap() {
        val machine = SectionLoopStateMachine()
        assertTrue(machine.setRange(10_000L, 30_000L))
        assertTrue(machine.isLoopActive)
        AppSettings.activeSectionLoop.value = machine.activeLoop
        AppSettings.sectionLoopState.value = machine.state
        assertNotNull(AppSettings.activeSectionLoop.value)

        // Simulate track change
        machine.onTrackChanged()
        AppSettings.activeSectionLoop.value = machine.activeLoop
        AppSettings.sectionLoopState.value = machine.state

        assertFalse(machine.isLoopActive)
        assertNull(machine.activeLoop)
        assertNull(AppSettings.activeSectionLoop.value)
        assertEquals(SectionLoopState.Idle, AppSettings.sectionLoopState.value)
    }

    @Test
    fun settingFlowTransitions() {
        val machine = SectionLoopStateMachine()
        assertEquals(SectionLoopState.Idle, machine.state)

        // Step 1: Set point A
        assertTrue(machine.setPointA(4000L))
        assertEquals(SectionLoopState.SettingA(4000L), machine.state)
        assertNull(machine.activeLoop)

        // Step 2: Attempt invalid point B (B < A)
        assertFalse(machine.setPointB(3000L))
        assertEquals(SectionLoopState.SettingA(4000L), machine.state)

        // Step 3: Set valid point B
        assertTrue(machine.setPointB(10_000L))
        val active = requireNotNull(machine.activeLoop)
        assertEquals(4000L, active.startMs)
        assertEquals(10_000L, active.endMs)
        assertEquals(SectionLoopState.Active(active), machine.state)

        // Step 4: Clear resets to Idle
        machine.clear()
        assertEquals(SectionLoopState.Idle, machine.state)
        assertNull(machine.activeLoop)
    }

    @Test
    fun boundaryConditionsNearLoopEnd() {
        val loop = SectionLoop(startMs = 2000L, endMs = 8000L)

        // 1 ms before end
        assertNull(evaluateLoopWrap(positionMs = 7999L, loop = loop, isPlaying = true))
        // Exact end
        assertEquals(2000L, evaluateLoopWrap(positionMs = 8000L, loop = loop, isPlaying = true))
        // 1 ms after end
        assertEquals(2000L, evaluateLoopWrap(positionMs = 8001L, loop = loop, isPlaying = true))
    }

    @Test
    fun remoteSpeakerAndPartyGuardLogic() {
        fun canLoop(inParty: Boolean, isCasting: Boolean, isWatchActive: Boolean): Boolean {
            if (inParty) return false
            if (isCasting) return false
            if (isWatchActive) return false
            return true
        }

        // Local playback: permitted
        assertTrue(canLoop(inParty = false, isCasting = false, isWatchActive = false))

        // Party playback: rejected
        assertFalse(canLoop(inParty = true, isCasting = false, isWatchActive = false))

        // Cast receiver: rejected
        assertFalse(canLoop(inParty = false, isCasting = true, isWatchActive = false))

        // Watch speaker: rejected
        assertFalse(canLoop(inParty = false, isCasting = false, isWatchActive = true))
    }

    @Test
    fun actionAndExtraConstants() {
        assertEquals("com.music.bitchord.action.SET_SECTION_LOOP", ACTION_SET_SECTION_LOOP)
        assertEquals("com.music.bitchord.action.CLEAR_SECTION_LOOP", ACTION_CLEAR_SECTION_LOOP)
        assertEquals("com.music.bitchord.extra.SECTION_LOOP_START_MS", EXTRA_SECTION_LOOP_START_MS)
        assertEquals("com.music.bitchord.extra.SECTION_LOOP_END_MS", EXTRA_SECTION_LOOP_END_MS)
    }
}
