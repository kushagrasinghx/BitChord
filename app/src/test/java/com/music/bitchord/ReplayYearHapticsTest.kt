package com.music.bitchord

import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.replay.year.HapticBeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins how a year story card plays the haptics its animations write down. */
class ReplayYearHapticsTest {

    @Test
    fun aBeatPlaysOnTheFrameThatReachesIt() {
        val beats = HapticBeats().apply { add(300, Haptic.Tick) }
        assertNull(beats.strongestBetween(-1f, 299.9f))
        assertEquals(Haptic.Tick, beats.strongestBetween(290f, 300f))
        // And only once: the next frame starts after it.
        assertNull(beats.strongestBetween(300f, 316f))
    }

    @Test
    fun aBeatAtTheVeryStartIsNotMissed() {
        val beats = HapticBeats().apply { add(0, Haptic.Slam) }
        assertEquals(Haptic.Slam, beats.strongestBetween(-1f, 0f))
    }

    @Test
    fun twoThingsLandingTogetherAreOneBeatTheStrongerOne() {
        val beats = HapticBeats().apply {
            add(500, Haptic.Tick)
            add(500, Haptic.Slam)
            add(500, Haptic.Tap)
        }
        assertEquals(Haptic.Slam, beats.strongestBetween(490f, 510f))
    }

    @Test
    fun aFrameCoveringSeveralBeatsStrikesTheStrongest() {
        val beats = HapticBeats().apply {
            add(100, Haptic.Tick)
            add(108, Haptic.Tap)
            add(112, Haptic.Tick)
        }
        assertEquals(Haptic.Tap, beats.strongestBetween(99f, 116f))
    }

    @Test
    fun aHeldCardStrikesNothing() {
        val beats = HapticBeats().apply { add(400, Haptic.Tick) }
        assertNull(beats.strongestBetween(400f, 400f))
    }
}
