package com.music.bitchord.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmPlaylistRequestTest {

    @Test
    fun `playlist identity maps to existing session request`() {
        assertEquals("playlist:PL123", AlarmPlaylistRequest.mediaId("PL123"))
    }

    @Test
    fun `blank playlist identity is rejected`() {
        assertNull(AlarmPlaylistRequest.mediaId("   "))
    }
}
