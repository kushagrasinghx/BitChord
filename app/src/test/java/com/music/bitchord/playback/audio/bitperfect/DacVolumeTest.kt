package com.music.bitchord.playback.audio.bitperfect

import com.music.bitchord.playback.audio.bitperfect.usb.UsbVolume
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DacVolumeTest {

    private fun snapshot(current: Double, min: Double = -127.0, max: Double = 0.0) =
        UsbVolume.Snapshot(available = true, minDb = min, maxDb = max, currentDb = current, restoreLimitDb = current)

    @After
    fun clear() = DacVolume.onSnapshot(null)

    @Test
    fun travelStartsAtMinus80SoTheLowerHalfIsAudible() {
        val volume = snapshot(-40.0)
        assertEquals(0.5f, DacVolume.fractionOf(volume), 1e-6f)
        assertEquals(-40.0, DacVolume.dbOf(volume, 0.5f), 1e-9)
        assertEquals(0f, DacVolume.fractionOf(snapshot(-100.0)), 0f)
        // Only the very bottom is the DAC's own minimum.
        assertEquals(-127.0, DacVolume.dbOf(volume, 0f), 0.0)
    }

    @Test
    fun narrowRangesUseTheirOwnMinimum() {
        val volume = snapshot(-30.0, min = -60.0)
        assertEquals(0.5f, DacVolume.fractionOf(volume), 1e-6f)
        assertEquals(-60.0, DacVolume.dbOf(volume, 0f), 0.0)
    }

    @Test
    fun stepsInSixteenthsAndReportsTheEnds() {
        DacVolume.onSnapshot(snapshot(0.0))
        assertEquals(1f, DacVolume.level.value!!, 0f)
        assertFalse("already at the top", DacVolume.step(+1))
        assertTrue(DacVolume.step(-1))
        assertEquals(15f / 16f, DacVolume.level.value!!, 1e-6f)
    }

    @Test
    fun noDacMeansNoLevel() {
        DacVolume.onSnapshot(UsbVolume.Snapshot(available = false))
        assertNull(DacVolume.level.value)
        assertFalse(DacVolume.step(+1))
    }
}
