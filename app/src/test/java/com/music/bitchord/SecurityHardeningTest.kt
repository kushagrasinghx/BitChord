package com.music.bitchord

import com.music.bitchord.data.jiosaavn.JioSaavnService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityHardeningTest {

    @Test
    fun testValidSaavnCdnUrls() {
        // Legitimate JioSaavn CDN formats across different renditions and subdomains
        assertTrue(JioSaavnService.isValidSaavnCdnUrl("https://aac.saavncdn.com/123/song_320.mp4"))
        assertTrue(JioSaavnService.isValidSaavnCdnUrl("http://h.saavncdn.com/456/stream_96.aac"))
        assertTrue(JioSaavnService.isValidSaavnCdnUrl("https://web.saavncdn.com/789/track.mp3"))
        assertTrue(JioSaavnService.isValidSaavnCdnUrl("https://saavncdn.com/stream/file.mp4"))
        assertTrue(JioSaavnService.isValidSaavnCdnUrl("https://media.jiosaavn.com/audio/sample.mp4"))
    }

    @Test
    fun testRejectsInvalidSaavnCdnUrls() {
        // SSRF, arbitrary host redirection, and malicious scheme injection attempts
        assertFalse(JioSaavnService.isValidSaavnCdnUrl(""))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("   "))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("http://malicious-attacker.com/exploit.mp4"))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("https://evil-saavncdn.com/fake.mp4"))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("https://saavncdn.com.attacker.com/steal"))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("file:///data/data/com.music.bitchord/databases/app.db"))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("javascript:alert(document.cookie)"))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("content://media/external/images/media/1"))
        assertFalse(JioSaavnService.isValidSaavnCdnUrl("not-a-valid-uri:::"))
    }
}
