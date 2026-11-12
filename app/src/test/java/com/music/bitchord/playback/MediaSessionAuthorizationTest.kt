package com.music.bitchord.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionAuthorizationTest {

    @Test
    fun `own app and system media controllers are allowed`() {
        assertTrue(isControllerAllowed("com.music.bitchord", "com.music.bitchord", false, false, false))
        assertTrue(isControllerAllowed("com.android.systemui", "com.music.bitchord", false, false, false))
        assertTrue(isControllerAllowed("com.google.android.projection.gearhead", "com.music.bitchord", false, false, false))
    }

    @Test
    fun `automotive and trusted controllers keep working`() {
        assertTrue(isControllerAllowed("vendor.car", "com.music.bitchord", false, true, false))
        assertTrue(isControllerAllowed("vendor.companion", "com.music.bitchord", false, false, true))
        assertTrue(isControllerAllowed("vendor.trusted", "com.music.bitchord", true, false, false))
    }

    @Test
    fun `an unknown third-party app is rejected`() {
        assertFalse(isControllerAllowed("com.example.malware", "com.music.bitchord", false, false, false))
    }
}
