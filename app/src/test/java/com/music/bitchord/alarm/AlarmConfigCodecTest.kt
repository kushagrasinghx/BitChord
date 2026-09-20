package com.music.bitchord.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlarmConfigCodecTest {

    @Test
    fun `important state round trips`() {
        val config = AlarmConfig(
            enabled = true,
            hour = 6,
            minute = 45,
            repeatDays = setOf(1, 3, 5),
            playlistId = "PL123",
            playlistTitle = "Wake up",
            playlistArtworkUrl = "https://example.invalid/art",
            generation = 8L,
            scheduledEpochMillis = 123_456L,
            scheduledToken = "8:abc",
            scheduleMode = AlarmScheduleMode.INEXACT,
        )
        assertEquals(config, AlarmConfigCodec.decode(AlarmConfigCodec.encode(config)))
    }

    @Test
    fun `malformed json fails closed`() {
        val decoded = AlarmConfigCodec.decode("{not-json")
        assertFalse(decoded.enabled)
        assertEquals(AlarmConfig(), decoded)
    }

    @Test
    fun `unsupported schema fails closed`() {
        val decoded = AlarmConfigCodec.decode("""{"schemaVersion":99,"enabled":true}""")
        assertFalse(decoded.enabled)
    }
}
