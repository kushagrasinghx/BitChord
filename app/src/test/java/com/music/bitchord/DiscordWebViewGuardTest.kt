package com.music.bitchord

import com.music.bitchord.auth.isAllowedDiscordWebUrl
import com.music.bitchord.auth.isDiscordTokenShape
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordWebViewGuardTest {

    @Test
    fun `only Discord hosts may navigate`() {
        assertTrue(isAllowedDiscordWebUrl("https://discord.com/login"))
        assertTrue(isAllowedDiscordWebUrl("https://canary.discord.com/channels/@me"))
        assertFalse(isAllowedDiscordWebUrl("https://discord.com.evil.example/"))
        assertFalse(isAllowedDiscordWebUrl("javascript:alert(1)"))
        assertFalse(isAllowedDiscordWebUrl(null))
    }

    @Test
    fun `arbitrary alert text is not a Discord token`() {
        assertFalse(isDiscordTokenShape("hello"))
        assertFalse(isDiscordTokenShape("<html>"))
        assertTrue(isDiscordTokenShape("MTA" + "x".repeat(40) + ".Gaaa." + "y".repeat(40)))
    }
}
