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
    fun calculateFadeVolumeKeepsFullVolumeWhenDisabledOrOutsideWindow() {
        assertEquals(1.0f, SleepTimer.calculateFadeVolume(45_000L, fadeOutEnabled = true), 0.001f)
        assertEquals(1.0f, SleepTimer.calculateFadeVolume(30_000L, fadeOutEnabled = true), 0.001f)
        assertEquals(1.0f, SleepTimer.calculateFadeVolume(15_000L, fadeOutEnabled = false), 0.001f)
    }

    @Test
    fun calculateFadeVolumeFadesLinearlyDuringLastThirtySeconds() {
        assertEquals(0.5f, SleepTimer.calculateFadeVolume(15_000L, fadeOutEnabled = true), 0.001f)
        assertEquals(0.25f, SleepTimer.calculateFadeVolume(7_500L, fadeOutEnabled = true), 0.001f)
        assertEquals(0.0f, SleepTimer.calculateFadeVolume(0L, fadeOutEnabled = true), 0.001f)
        assertEquals(0.0f, SleepTimer.calculateFadeVolume(-100L, fadeOutEnabled = true), 0.001f)
    }
}
