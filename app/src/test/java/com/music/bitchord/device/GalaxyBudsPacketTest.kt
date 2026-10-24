package com.music.bitchord.device

import org.junit.Assert.assertEquals
import org.junit.Test

class GalaxyBudsPacketTest {
    @Test
    fun usesLegacyFrameForOriginalGalaxyBuds() {
        val packet = GalaxyBudsManager.buildPacket(
            msgId = 128,
            payload = byteArrayOf(1),
            family = DeviceFamily.GALAXY_BUDS,
        )

        assertEquals(8, packet.size)
        assertEquals(0xFE.toByte(), packet[0])
        assertEquals(0, packet[1].toInt())
        assertEquals(4, packet[2].toInt())
        assertEquals(128.toByte(), packet[3])
        assertEquals(1, packet[4].toInt())
        assertEquals(0xEE.toByte(), packet.last())
    }

    @Test
    fun usesStandardFrameForLaterGalaxyBuds() {
        val packet = GalaxyBudsManager.buildPacket(
            msgId = 120,
            payload = byteArrayOf(GalaxyBudsManager.NoiseControl.ADAPTIVE),
            family = DeviceFamily.GALAXY_BUDS4_PRO,
        )

        assertEquals(8, packet.size)
        assertEquals(0xFD.toByte(), packet[0])
        assertEquals(4, packet[1].toInt())
        assertEquals(0, packet[2].toInt())
        assertEquals(120, packet[3].toInt())
        assertEquals(GalaxyBudsManager.NoiseControl.ADAPTIVE, packet[4])
        assertEquals(0xDD.toByte(), packet.last())
    }

    @Test
    fun readsConfirmedNoiseModeFromFragmentedDeviceStatus() {
        val payload = ByteArray(13).apply {
            this[12] = GalaxyBudsManager.NoiseControl.ADAPTIVE
        }
        val frame = GalaxyBudsManager.buildPacket(
            msgId = 97,
            payload = payload,
            family = DeviceFamily.GALAXY_BUDS4_PRO,
        )
        val decoder = GalaxyBudsManager.BudsSppFrameDecoder()

        assertEquals(0, decoder.append(frame.copyOfRange(0, 5), 5).size)
        val decoded = decoder.append(frame.copyOfRange(5, frame.size), frame.size - 5)

        assertEquals(1, decoded.size)
        assertEquals(
            GalaxyBudsManager.NoiseControl.ADAPTIVE,
            GalaxyBudsManager.decodeNoiseStatus(decoded.single(), DeviceFamily.GALAXY_BUDS4_PRO)?.mode,
        )
    }

    @Test
    fun decodesLegacyAmbientModeFromConfirmedDeviceStatus() {
        val payload = ByteArray(8).apply { this[7] = 1 }
        val frame = GalaxyBudsManager.buildPacket(
            msgId = 97,
            payload = payload,
            family = DeviceFamily.GALAXY_BUDS,
        )

        assertEquals(
            GalaxyBudsManager.NoiseControl.AMBIENT,
            GalaxyBudsManager.decodeNoiseStatus(frame, DeviceFamily.GALAXY_BUDS)?.mode,
        )
    }

}
