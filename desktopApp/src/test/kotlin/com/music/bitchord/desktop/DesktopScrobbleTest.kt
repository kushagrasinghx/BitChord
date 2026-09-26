package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scrobbling arithmetic that is invisible when wrong.
 *
 * A listen submitted under the wrong artist, or at the wrong moment, still
 * looks like a working feature — it just quietly writes the wrong thing to
 * somebody's listening history. Ported alongside Android's `PrimaryArtist`.
 */
class DesktopScrobbleTest {

    @Test
    fun aJointCreditIsCutBackToTheFirstArtist() {
        assertEquals("Kendrick Lamar", "Kendrick Lamar, SZA".primaryArtist())
        assertEquals("Calvin Harris", "Calvin Harris & Dua Lipa".primaryArtist())
        assertEquals("Yoasobi", "Yoasobi ＆ Ikura".primaryArtist())
    }

    @Test
    fun aNameWithNoSeparatorIsLeftAlone() {
        // A slash is not a separator, which is what keeps "AC/DC" whole.
        assertEquals("AC/DC", "AC/DC".primaryArtist())
        assertEquals("Florence + The Machine", "Florence + The Machine".primaryArtist())
    }

    @Test
    fun aBandWhoseOwnNameHoldsASeparatorIsCutToo() {
        // The known cost of the heuristic, and Android's behaviour exactly: it
        // cannot tell a joint credit from a band that has an ampersand in its
        // name. Pinned rather than hidden, so a change here is a decision.
        assertEquals("Simon", "Simon & Garfunkel".primaryArtist())
        assertEquals("Earth", "Earth, Wind & Fire".primaryArtist())
    }

    @Test
    fun anEmptyLeadingCreditFallsBackToTheWholeName() {
        assertEquals(", SZA", ", SZA".primaryArtist())
    }

    @Test
    fun theDefaultsAreAndroidsOwn() {
        assertEquals(30, DesktopScrobbleSettings.DEFAULT_MIN_DURATION)
        assertEquals(0.5f, DesktopScrobbleSettings.DEFAULT_DELAY_PERCENT)
        assertEquals(180, DesktopScrobbleSettings.DEFAULT_DELAY_SECONDS)
    }
}
