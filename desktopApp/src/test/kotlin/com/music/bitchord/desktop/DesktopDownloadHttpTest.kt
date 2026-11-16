package com.music.bitchord.desktop

import okhttp3.CookieJar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopDownloadHttpTest {
    @Test
    fun `downloads never send the JVM cookie handler's cookies`() {
        assertEquals(CookieJar.NO_COOKIES, DesktopDownloadHttp.client.cookieJar)
    }

    @Test
    fun `download requests ask for raw bytes from the resume offset`() {
        val request = DesktopDownloadHttp.request("https://example.com/a.flac", "UA", mapOf("X-Stream" to "1"), rangeFrom = 10)

        assertEquals("bytes=10-", request.header("Range"))
        assertEquals("identity", request.header("Accept-Encoding"))
        assertEquals("1", request.header("X-Stream"))
        assertNull(request.header("Cookie"))
    }
}
