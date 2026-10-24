package com.music.bitchord.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryTest {
    @Test
    fun identifiesEveryGalaxyBudsGenerationThroughBuds4Pro() {
        val models = listOf(
            "Galaxy Buds" to DeviceFamily.GALAXY_BUDS,
            "Galaxy Buds+" to DeviceFamily.GALAXY_BUDS_PLUS,
            "Galaxy Buds Live" to DeviceFamily.GALAXY_BUDS_LIVE,
            "Galaxy Buds Pro" to DeviceFamily.GALAXY_BUDS_PRO,
            "Galaxy Buds2" to DeviceFamily.GALAXY_BUDS2,
            "Galaxy Buds2 Pro" to DeviceFamily.GALAXY_BUDS2_PRO,
            "Galaxy Buds FE" to DeviceFamily.GALAXY_BUDS_FE,
            "Galaxy Buds Core" to DeviceFamily.GALAXY_BUDS_CORE,
            "Galaxy Buds3" to DeviceFamily.GALAXY_BUDS3,
            "Galaxy Buds3 Pro" to DeviceFamily.GALAXY_BUDS3_PRO,
            "Galaxy Buds3 FE" to DeviceFamily.GALAXY_BUDS3_FE,
            "Galaxy Buds4" to DeviceFamily.GALAXY_BUDS4,
            "Galaxy Buds4 Pro" to DeviceFamily.GALAXY_BUDS4_PRO,
        )

        models.forEach { (name, family) ->
            assertEquals(name, family, DeviceRegistry.identify(name)?.family)
        }
    }

    @Test
    fun identifiesModelsByTheirSamsungModelNumbers() {
        val modelNumbers = listOf(
            "SM-R170" to DeviceFamily.GALAXY_BUDS,
            "SM-R175" to DeviceFamily.GALAXY_BUDS_PLUS,
            "SM-R180" to DeviceFamily.GALAXY_BUDS_LIVE,
            "SM-R190" to DeviceFamily.GALAXY_BUDS_PRO,
            "SM-R177" to DeviceFamily.GALAXY_BUDS2,
            "SM-R510" to DeviceFamily.GALAXY_BUDS2_PRO,
            "SM-R400N" to DeviceFamily.GALAXY_BUDS_FE,
            "SM-R410" to DeviceFamily.GALAXY_BUDS_CORE,
            "SM-R530" to DeviceFamily.GALAXY_BUDS3,
            "SM-R630" to DeviceFamily.GALAXY_BUDS3_PRO,
            "SM-R420" to DeviceFamily.GALAXY_BUDS3_FE,
            "SM-R540" to DeviceFamily.GALAXY_BUDS4,
            "SM-R640" to DeviceFamily.GALAXY_BUDS4_PRO,
        )

        modelNumbers.forEach { (name, family) ->
            assertEquals(name, family, DeviceRegistry.identify(name)?.family)
        }
    }

    @Test
    fun exposesOnlyNoiseControlsSupportedByEachModel() {
        assertTrue(DeviceFamily.entries.filter { it.isGalaxyBuds }.all { it.supportsPresetEqualizer })
        assertTrue(DeviceFamily.GALAXY_BUDS.supportsAmbientMode)
        assertFalse(DeviceFamily.GALAXY_BUDS.supportsAnc)
        assertTrue(DeviceFamily.GALAXY_BUDS_LIVE.supportsAnc)
        assertFalse(DeviceFamily.GALAXY_BUDS_LIVE.supportsAmbientMode)
        assertTrue(DeviceFamily.GALAXY_BUDS_PRO.supportsAnc)
        assertTrue(DeviceFamily.GALAXY_BUDS_PRO.supportsAmbientMode)
        assertFalse(DeviceFamily.GALAXY_BUDS3.supportsAdaptiveMode)
        assertTrue(DeviceFamily.GALAXY_BUDS3_PRO.supportsAdaptiveMode)
        assertTrue(DeviceFamily.GALAXY_BUDS3_FE.supportsAdaptiveMode)
        assertTrue(DeviceFamily.GALAXY_BUDS4.supportsAdaptiveMode)
        assertTrue(DeviceFamily.GALAXY_BUDS4_PRO.supportsAdaptiveMode)
        assertFalse(DeviceFamily.GALAXY_BUDS2_PRO.supportsAdaptiveMode)
        assertFalse(DeviceFamily.GALAXY_BUDS_CORE.supportsAdaptiveMode)
        assertTrue(DeviceFamily.GALAXY_BUDS_PRO.supportsAudio360)
        assertTrue(DeviceFamily.GALAXY_BUDS2.supportsAudio360)
        assertTrue(DeviceFamily.GALAXY_BUDS2_PRO.supportsAudio360)
        assertTrue(DeviceFamily.GALAXY_BUDS3_PRO.supportsAudio360)
        assertTrue(DeviceFamily.GALAXY_BUDS4_PRO.supportsAudio360)
        assertFalse(DeviceFamily.GALAXY_BUDS3.supportsAudio360)
        assertTrue(DeviceFamily.GALAXY_BUDS_PRO.supportsAncLevel)
        assertTrue(DeviceFamily.GALAXY_BUDS_FE.supportsAncLevel)
        assertTrue(DeviceFamily.GALAXY_BUDS3_FE.supportsAncLevel)
        assertFalse(DeviceFamily.GALAXY_BUDS2_PRO.supportsAncLevel)
    }

    @Test
    fun keepsOtherAudioBrandsOutOfGalaxyBudsControls() {
        assertNull(DeviceRegistry.identify("Sony LinkBuds S"))
        assertEquals(DeviceFamily.MOTO_BUDS_PLUS, DeviceRegistry.identify("Moto Buds+")?.family)
    }
}
