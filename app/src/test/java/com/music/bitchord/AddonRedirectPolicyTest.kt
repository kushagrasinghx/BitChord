package com.music.bitchord

import com.music.bitchord.data.sources.addon.addonAuthStillAllowed
import com.music.bitchord.data.sources.addon.addonRedirectTarget
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonRedirectPolicyTest {

    private val first = MockWebServer()
    private val second = MockWebServer()

    @After
    fun tearDown() {
        runCatching { first.shutdown() }
        runCatching { second.shutdown() }
    }

    @Test
    fun `cross-host redirect never keeps addon credentials`() {
        first.start()
        second.start()
        val start = first.url("/addon/search").toString()
        val next = second.url("/collect").toString()

        assertEquals(next, addonRedirectTarget(start, next))
        assertFalse(addonAuthStillAllowed(start, next))
    }

    @Test
    fun `same-host redirect keeps credentials`() {
        first.start()
        val start = first.url("/addon/search").toString()
        val next = first.url("/addon/search-v2").toString()

        assertEquals(next, addonRedirectTarget(start, "/addon/search-v2"))
        assertTrue(addonAuthStillAllowed(start, next))
    }

    @Test
    fun `scheme and port changes drop credentials`() {
        first.start()
        val https = "https://example.com/addon"
        val http = "http://example.com/addon"
        assertFalse(addonAuthStillAllowed(https, http))
        assertTrue(addonAuthStillAllowed("https://example.com:443/a", "https://example.com/b"))
        assertFalse(addonAuthStillAllowed("https://example.com:443/a", "https://example.com:8443/b"))
    }

    @Test
    fun `malformed redirect locations are unusable`() {
        first.start()
        val start = first.url("/addon").toString()
        assertNull(addonRedirectTarget(start, "javascript:alert(1)"))
        assertNull(addonRedirectTarget(start, "http://[invalid"))
    }

    @Test
    fun `relative redirect resolves against the current URL`() {
        first.start()
        val start = first.url("/api/v1/search").toString()
        assertEquals(first.url("/api/v1/next").toString(), addonRedirectTarget(start, "next"))
    }
}
