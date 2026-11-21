package com.music.bitchord.ui.tv.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvAuthServerOriginTest {

    @Test
    fun testSameOriginAndHostMatchingAllowed() {
        // Legitimate phone browser connecting to the TV's IP address
        assertTrue(TvAuthServer.isAllowedOrigin("http://192.168.1.105:8765", "192.168.1.105:8765"))
        assertTrue(TvAuthServer.isAllowedOrigin("http://localhost:8765", "localhost:8765"))
        assertTrue(TvAuthServer.isAllowedOrigin("http://127.0.0.1:8765", "127.0.0.1:8765"))
        // Same-origin GET requests omit the Origin header entirely
        assertTrue(TvAuthServer.isAllowedOrigin(null, "192.168.1.105:8765"))
        assertTrue(TvAuthServer.isAllowedOrigin("", "192.168.1.105:8765"))
    }

    @Test
    fun testCrossSiteOriginsRejected() {
        // Malicious web pages attempting drive-by local network commands
        assertFalse(TvAuthServer.isAllowedOrigin("http://evil.com", "192.168.1.105:8765"))
        assertFalse(TvAuthServer.isAllowedOrigin("https://malicious-site.org", "192.168.1.105:8765"))
        assertFalse(TvAuthServer.isAllowedOrigin("http://attacker.com:8765", "192.168.1.105:8765"))
        assertFalse(TvAuthServer.isAllowedOrigin("null", "192.168.1.105:8765"))
    }

    @Test
    fun testPortMismatchesAndSpoofedDomainsRejected() {
        // Port mismatch: target is running on 8765, origin came from 9999
        assertFalse(TvAuthServer.isAllowedOrigin("http://192.168.1.105:9999", "192.168.1.105:8765"))
        // Domain suffix spoofing: host includes target IP but belongs to attacker domain
        assertFalse(TvAuthServer.isAllowedOrigin("http://192.168.1.105.attacker.com:8765", "192.168.1.105:8765"))
        // Invalid URI strings
        assertFalse(TvAuthServer.isAllowedOrigin(":::not-a-valid-uri:::", "192.168.1.105:8765"))
    }
}
