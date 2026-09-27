package com.music.bitchord

import com.music.bitchord.data.innertube.PlayerClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerClientRangeTest {

    private fun gv(client: String, version: String = "1.0") =
        "https://rr1---sn-abc.googlevideo.com/videoplayback?c=$client&cver=$version&clen=5000000"

    @Test
    fun `googlevideo ranges are capped at one megabyte`() {
        assertEquals(1024L * 1024, PlayerClient.rangeBytesFor(gv("IOS")))
        assertEquals(1024L * 1024, PlayerClient.rangeBytesFor(gv("VISIONOS", "1.02")))
        assertEquals(1024L * 1024, PlayerClient.rangeBytesFor(gv("TVHTML5")))
    }

    @Test
    fun `clients refused past a megabyte get half that`() {
        assertEquals(512L * 1024, PlayerClient.rangeBytesFor(gv("ANDROID_VR", "1.65.10")))
        assertEquals(512L * 1024, PlayerClient.rangeBytesFor(gv("TVHTML5_SIMPLY")))
    }

    @Test
    fun `non-googlevideo hosts are not capped`() {
        assertEquals(Long.MAX_VALUE, PlayerClient.rangeBytesFor("https://sp-ad-cf.audio.tidal.com/x.flac"))
        assertEquals(Long.MAX_VALUE, PlayerClient.rangeBytesFor("not a url"))
    }

    @Test
    fun `visionOS URLs are fetched as visionOS, not as an iPhone`() {
        val client = PlayerClient.forStreamUrl(gv("VISIONOS", "1.02"))
        assertEquals("VISIONOS", client.clientName)
        val agent = client.mediaHeaders().getValue("User-Agent")
        assertTrue(agent, agent.startsWith("com.google.visionos.youtube/1.02"))
    }
}
