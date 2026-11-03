package com.music.bitchord

import com.music.bitchord.data.scrobbling.LastFM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastFmErrorTest {

    @Test
    fun `a scrobble of a track called error is accepted`() {
        val accepted = """
            {"scrobbles":{"scrobble":{"artist":{"corrected":"0","#text":"Error"},
            "track":{"corrected":"0","#text":"error"},"timestamp":"1700000000"},
            "@attr":{"ignored":0,"accepted":1}}}
        """.trimIndent()
        assertNull(LastFM.errorIn(accepted))
    }

    @Test
    fun `an error response is read as one`() {
        val error = LastFM.errorIn("""{"error":9,"message":"Invalid session key - Please re-authenticate"}""")
        assertEquals(LastFM.LastFmError(9, "Invalid session key - Please re-authenticate"), error)
    }

    @Test
    fun `a body that is not json is not an error`() {
        assertNull(LastFM.errorIn("<html>Bad Gateway</html>"))
    }
}
