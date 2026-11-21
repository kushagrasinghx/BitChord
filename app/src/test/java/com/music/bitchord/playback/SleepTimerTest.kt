package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {

    @Test
    fun customSliderSpansOneStepToTwelveHours() {
        assertEquals(SleepTimer.STEP_MINUTES, SleepTimer.minutesAt(0f))
        assertEquals(SleepTimer.MAX_MINUTES, SleepTimer.minutesAt(1f))
        // Dragged past either end, it stays inside the range.
        assertEquals(SleepTimer.STEP_MINUTES, SleepTimer.minutesAt(-0.5f))
        assertEquals(SleepTimer.MAX_MINUTES, SleepTimer.minutesAt(1.5f))
    }

    @Test
    fun customSliderLandsOnWholeSteps() {
        for (i in 0..100) {
            assertEquals(0, SleepTimer.minutesAt(i / 100f) % SleepTimer.STEP_MINUTES)
        }
    }

    @Test
    fun fractionOfIsTheInverseOfMinutesAt() {
        for (minutes in SleepTimer.STEP_MINUTES..SleepTimer.MAX_MINUTES step SleepTimer.STEP_MINUTES) {
            assertEquals(minutes, SleepTimer.minutesAt(SleepTimer.fractionOf(minutes)))
        }
    }

    @Test
    fun timerStartSetsDeadlineAndMinutes() {
        SleepTimer.start(30)
        assertEquals(30, SleepTimer.minutes.value)
        assertEquals(false, SleepTimer.afterTrack.value)
        assertEquals(true, SleepTimer.isRunning)
        SleepTimer.cancel()
    }

    @Test
    fun timerStartAfterTrackSetsAfterTrackFlag() {
        SleepTimer.startAfterTrack()
        assertEquals(null, SleepTimer.minutes.value)
        assertEquals(null, SleepTimer.deadline.value)
        assertEquals(true, SleepTimer.afterTrack.value)
        assertEquals(true, SleepTimer.isRunning)
        SleepTimer.cancel()
    }

    @Test
    fun timerCancelClearsStateAndTriggersListener() {
        var listenerCalled = false
        SleepTimer.onCancelListener = { listenerCalled = true }
        SleepTimer.start(15)
        assertEquals(true, SleepTimer.isRunning)

        SleepTimer.cancel()
        assertEquals(false, SleepTimer.isRunning)
        assertEquals(null, SleepTimer.deadline.value)
        assertEquals(null, SleepTimer.minutes.value)
        assertEquals(false, SleepTimer.afterTrack.value)
        assertEquals(true, listenerCalled)
        SleepTimer.onCancelListener = null
    }
}
