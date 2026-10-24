package com.music.bitchord.ui.tv.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPerformancePolicyTest {
    private fun gib(value: Int) = value * 1024L * 1024 * 1024

    @Test fun twoGbBoxUsesSmoothEvenWhenFirmwareDoesNotReportLowRam() {
        val box = TvDeviceResources(lowRam = false, totalMemoryBytes = gib(2), memoryClassMb = 256)
        assertEquals(TvPerformanceProfile.SMOOTH, resolveTvPerformanceProfile(TvPerformanceMode.AUTO, box))
    }

    @Test fun reservedGpuMemoryDoesNotExcludeTwoGbBoxes() {
        val box = TvDeviceResources(totalMemoryBytes = 1800L * 1024 * 1024, memoryClassMb = 256)
        assertEquals(TvPerformanceProfile.SMOOTH, resolveTvPerformanceProfile(TvPerformanceMode.AUTO, box))
    }

    @Test fun platformLowRamFlagTakesPriorityOverReportedCapacity() {
        val box = TvDeviceResources(lowRam = true, totalMemoryBytes = gib(4), memoryClassMb = 512)
        assertEquals(TvPerformanceProfile.SMOOTH, resolveTvPerformanceProfile(TvPerformanceMode.AUTO, box))
    }

    @Test fun smallHeapAlsoSelectsSmooth() {
        val box = TvDeviceResources(totalMemoryBytes = gib(4), memoryClassMb = 192)
        assertEquals(TvPerformanceProfile.SMOOTH, resolveTvPerformanceProfile(TvPerformanceMode.AUTO, box))
    }

    @Test fun threeGbBoxWithAdequateHeapUsesStandard() {
        val box = TvDeviceResources(totalMemoryBytes = gib(3), memoryClassMb = 256)
        assertEquals(TvPerformanceProfile.STANDARD, resolveTvPerformanceProfile(TvPerformanceMode.AUTO, box))
    }

    @Test fun missingOrInvalidReadingsDoNotPretendToBeLowRam() {
        for (box in listOf(TvDeviceResources(), TvDeviceResources(totalMemoryBytes = -1, memoryClassMb = -1))) {
            assertEquals(TvPerformanceProfile.STANDARD, resolveTvPerformanceProfile(TvPerformanceMode.AUTO, box))
        }
    }

    @Test fun explicitChoiceOverridesAutomaticClassification() {
        assertEquals(TvPerformanceProfile.STANDARD, resolveTvPerformanceProfile(
            TvPerformanceMode.STANDARD, TvDeviceResources(lowRam = true, totalMemoryBytes = gib(2)),
        ))
        assertEquals(TvPerformanceProfile.SMOOTH, resolveTvPerformanceProfile(
            TvPerformanceMode.SMOOTH, TvDeviceResources(totalMemoryBytes = gib(4), memoryClassMb = 512),
        ))
    }

    @Test fun unknownStoredModeFallsBackToAutomatic() {
        assertEquals(TvPerformanceMode.AUTO, TvPerformanceMode.fromStored(null))
        assertEquals(TvPerformanceMode.AUTO, TvPerformanceMode.fromStored("REMOVED_MODE"))
    }

    @Test fun smoothNeverLoadsVideoRegardlessOfArtworkPreferences() {
        for (requested in listOf(false, true)) for (lyrics in listOf(false, true)) for (inLyrics in listOf(false, true)) {
            assertFalse(TvPerformanceProfile.SMOOTH.allowsCanvas(requested, lyrics, inLyrics))
        }
    }

    @Test fun standardRespectsBothMotionArtworkSwitches() {
        assertTrue(TvPerformanceProfile.STANDARD.allowsCanvas(true, false, false))
        assertTrue(TvPerformanceProfile.STANDARD.allowsCanvas(true, true, true))
        assertFalse(TvPerformanceProfile.STANDARD.allowsCanvas(true, true, false))
        assertFalse(TvPerformanceProfile.STANDARD.allowsCanvas(false, false, true))
    }
}
