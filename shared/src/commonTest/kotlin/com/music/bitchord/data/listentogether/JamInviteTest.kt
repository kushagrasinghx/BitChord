package com.music.bitchord.data.listentogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What counts as an invite. Anything that is not the public URL shape must be refused outright
 * rather than half-parsed: a join is an action taken on someone else's behalf.
 */
class JamInviteTest {

    @Test
    fun `it reads the code out of an invite url`() {
        assertEquals("ABC123", JamInvite.parse("${JamInvite.ORIGIN}/invite/ABC123"))
    }

    @Test
    fun `a code is normalised to upper case`() {
        assertEquals("ABC123", JamInvite.parse("${JamInvite.ORIGIN}/invite/abc123"))
    }

    @Test
    fun `query and fragment are ignored`() {
        assertEquals("ABC123", JamInvite.parse("${JamInvite.ORIGIN}/invite/ABC123?utm=x"))
        assertEquals("ABC123", JamInvite.parse("${JamInvite.ORIGIN}/invite/ABC123#top"))
    }

    @Test
    fun `another host is not an invite even with the same path`() {
        assertNull(JamInvite.parse("https://example.com/invite/ABC123"))
    }

    @Test
    fun `plain http is refused`() {
        assertNull(JamInvite.parse("http://bitchord.kushagrasingh.in/invite/ABC123"))
    }

    @Test
    fun `a wrong-length code is refused`() {
        assertNull(JamInvite.parse("${JamInvite.ORIGIN}/invite/ABC12"))
        assertNull(JamInvite.parse("${JamInvite.ORIGIN}/invite/ABC1234"))
    }

    @Test
    fun `rubbish is refused rather than guessed at`() {
        assertNull(JamInvite.parse(null))
        assertNull(JamInvite.parse(""))
        assertNull(JamInvite.parse("ABC123"))
    }

    @Test
    fun `a built url round-trips`() {
        assertEquals("ZZ99QQ", JamInvite.parse(JamInvite.url("zz99qq")))
    }
}
