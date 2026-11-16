package com.music.bitchord.desktop

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWebCookiesTest {
    private val spotify = URI("https://open.spotify.com/")

    private fun cookieHeader() = DesktopWebCookies.get(spotify, emptyMap())["Cookie"].orEmpty().joinToString("; ")

    @Test
    fun `starting over leaves no cookie behind`() {
        DesktopWebCookies.put(spotify, mapOf("Set-Cookie" to listOf("sp_dc=secret; Domain=.spotify.com; Path=/; Secure; HttpOnly")))
        assertEquals("sp_dc=secret", cookieHeader())

        DesktopWebCookies.reset()

        assertTrue(cookieHeader().isEmpty())
    }
}
