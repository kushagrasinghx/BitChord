package com.music.bitchord.data.applemusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicAuthConfigurationTest {
    @Test
    fun `HTTPS backend endpoint accepts explicit ports and deployment prefixes`() {
        assertTrue(validAppleMusicAuthBackend("https://backend.example/api/applemusic/token"))
        assertTrue(validAppleMusicAuthBackend("https://backend.example:8443/service/api/applemusic/token"))
    }

    @Test
    fun `missing insecure malformed and incorrect endpoint configuration is rejected`() {
        for (url in listOf(
            "", " ", "not a URL", "http://backend.example/api/applemusic/token",
            "https://backend.example/api/token", "https://backend.example/api/applemusic/token/",
            " https://backend.example/api/applemusic/token", "https://backend.example/api/applemusic/token ",
            "https://backend.example/api/applemusic/../applemusic/token",
            "https://backend.example/api/applemusic/%74oken",
            "https:\\backend.example/api/applemusic/token",
        )) assertFalse(url, validAppleMusicAuthBackend(url))
    }

    @Test
    fun `backend configuration cannot contain credentials query or fragment even when empty`() {
        for (url in listOf(
            "https://user:password@backend.example/api/applemusic/token",
            "https://@backend.example/api/applemusic/token",
            "https://backend.example/api/applemusic/token?secret=value",
            "https://backend.example/api/applemusic/token?",
            "https://backend.example/api/applemusic/token#secret",
            "https://backend.example/api/applemusic/token#",
        )) assertFalse(url, validAppleMusicAuthBackend(url))
    }
}
