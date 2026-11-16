package com.music.bitchord.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLogRedactionTest {

    @Test
    fun `signed media URL parameters are redacted`() {
        val text = redactSensitive("https://rr1.googlevideo.com/a?itag=251&sig=SECRET&pot=TOKEN&expire=1")
        assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("TOKEN"))
        assertTrue(text.contains("sig=<redacted>"))
    }

    @Test
    fun `authorization and addon proof headers are redacted`() {
        assertEquals(
            "Authorization: Bearer <redacted>",
            redactSensitive("Authorization: Bearer abc.def"),
        )
        assertEquals(
            "X-BitChord-Token: <redacted>",
            redactSensitive("X-BitChord-Token: durable-token"),
        )
    }

    @Test
    fun `ordinary diagnostics are untouched`() {
        assertEquals("resolve: 3 candidates", redactSensitive("resolve: 3 candidates"))
    }
}
