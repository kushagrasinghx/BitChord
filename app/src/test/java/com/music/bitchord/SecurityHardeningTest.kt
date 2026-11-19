package com.music.bitchord

import com.music.bitchord.data.jiosaavn.isValidSaavnCdnUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityHardeningTest {

    @Test
    fun testValidSaavnCdnUrls() {
        // Legitimate JioSaavn CDN formats across different renditions and subdomains
        assertTrue(isValidSaavnCdnUrl("https://aac.saavncdn.com/123/song_320.mp4"))
        assertTrue(isValidSaavnCdnUrl("http://h.saavncdn.com/456/stream_96.aac"))
        assertTrue(isValidSaavnCdnUrl("https://web.saavncdn.com/789/track.mp3"))
        assertTrue(isValidSaavnCdnUrl("https://saavncdn.com/stream/file.mp4"))
        assertTrue(isValidSaavnCdnUrl("https://media.jiosaavn.com/audio/sample.mp4"))
    }

    @Test
    fun testRejectsInvalidSaavnCdnUrls() {
        // SSRF, arbitrary host redirection, and malicious scheme injection attempts
        assertFalse(isValidSaavnCdnUrl(""))
        assertFalse(isValidSaavnCdnUrl("   "))
        assertFalse(isValidSaavnCdnUrl("http://malicious-attacker.com/exploit.mp4"))
        assertFalse(isValidSaavnCdnUrl("https://evil-saavncdn.com/fake.mp4"))
        assertFalse(isValidSaavnCdnUrl("https://saavncdn.com.attacker.com/steal"))
        assertFalse(isValidSaavnCdnUrl("file:///data/data/com.music.bitchord/databases/app.db"))
        assertFalse(isValidSaavnCdnUrl("javascript:alert(document.cookie)"))
        assertFalse(isValidSaavnCdnUrl("content://media/external/images/media/1"))
        assertFalse(isValidSaavnCdnUrl("not-a-valid-uri:::"))
    }
}
