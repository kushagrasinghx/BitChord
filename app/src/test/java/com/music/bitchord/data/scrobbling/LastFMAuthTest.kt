package com.music.bitchord.data.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFMAuthTest {

    @Test
    fun `lastfm auth url contains api key and token`() {
        LastFM.configure(
            endpoint = LastFM.DEFAULT_API_ENDPOINT,
            apiKey = "test_api_key_123",
            secret = "test_secret_456",
        )
        val url = LastFM.getAuthUrl("token_xyz_789")
        assertEquals(
            "https://www.last.fm/api/auth/?api_key=test_api_key_123&token=token_xyz_789",
            url,
        )
    }

    @Test
    fun `librefm auth url contains api key and token`() {
        LastFM.configure(
            endpoint = LastFM.LIBREFM_API_ENDPOINT,
            apiKey = "test_libre_key",
            secret = "test_libre_secret",
        )
        val url = LastFM.getAuthUrl("libre_token_abc")
        assertEquals(
            "https://libre.fm/api/auth?api_key=test_libre_key&token=libre_token_abc",
            url,
        )
    }

    @Test
    fun `endpoint normalization formats path properly`() {
        val normalized = LastFM.normalizeEndpoint("https://ws.audioscrobbler.com/2.0")
        assertEquals("https://ws.audioscrobbler.com/2.0/", normalized)
    }
}
