package com.music.bitchord

import com.music.bitchord.data.sources.addon.cleartextAddonRefusal
import com.music.bitchord.data.webdav.WebDavConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CleartextSourceTest {

    @Test
    fun `release builds refuse plain-HTTP addons with an actionable message`() {
        val refusal = cleartextAddonRefusal("http://192.168.1.10:8080", cleartextAllowed = false)
        assertNotNull(refusal)
        assertNotNull(refusal!!.contains("HTTPS"))
        assertNull(cleartextAddonRefusal("https://addon.example", cleartextAllowed = false))
    }

    @Test
    fun `debug builds still allow local HTTP addon development`() {
        assertNull(cleartextAddonRefusal("http://127.0.0.1:8080", cleartextAllowed = true))
    }

    @Test
    fun `release builds refuse plain-HTTP WebDAV`() {
        val refusal = WebDavConfig.cleartextRefusal("http://dav.local", cleartextAllowed = false)
        assertNotNull(refusal)
        assertNull(WebDavConfig.cleartextRefusal("https://dav.example", cleartextAllowed = false))
        assertNull(WebDavConfig.cleartextRefusal("http://dav.local", cleartextAllowed = true))
    }
}
