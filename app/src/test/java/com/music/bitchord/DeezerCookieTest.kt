package com.music.bitchord

import com.music.bitchord.auth.arlFromCookies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which cookie jar the Deezer login reads `arl` from. */
class DeezerCookieTest {

    @Test
    fun `arl on deezer_com is found when www holds only other cookies`() {
        assertEquals("tok", arlFromCookies("consent=1; dzr_uniq_id=x", "arl=tok; sid=s"))
    }

    @Test
    fun `arl on www is found`() {
        assertEquals("tok", arlFromCookies("consent=1; arl=tok", null))
    }

    @Test
    fun `no arl, an empty arl, or a lookalike name is not a token`() {
        assertNull(arlFromCookies(null, null))
        assertNull(arlFromCookies("consent=1", "sid=s"))
        assertNull(arlFromCookies("arl=", "xarl=tok"))
    }
}
