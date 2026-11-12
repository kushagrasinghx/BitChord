package com.music.bitchord

import com.music.bitchord.data.canvas.isAllowedSpotifyWebUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyWebViewGuardTest {

    @Test
    fun `only Spotify origins can drive the token bridge`() {
        assertTrue(isAllowedSpotifyWebUrl("https://open.spotify.com/"))
        assertTrue(isAllowedSpotifyWebUrl("https://accounts.spotify.com/login"))
        assertFalse(isAllowedSpotifyWebUrl("https://open.spotify.com.evil.example/"))
        assertFalse(isAllowedSpotifyWebUrl("https://example.com/"))
        assertFalse(isAllowedSpotifyWebUrl("javascript:alert(1)"))
        assertFalse(isAllowedSpotifyWebUrl(null))
    }
}
