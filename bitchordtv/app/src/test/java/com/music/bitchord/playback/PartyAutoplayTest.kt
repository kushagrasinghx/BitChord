package com.music.bitchord.playback

import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two questions AutoPlay asks before it does anything in a party: whether it
 * is on at all, and whose job it is to top the queue up.
 *
 * Both used to be answered in the playback service, where nothing could reach
 * them — and the first was answered a second time, differently, by the player
 * that draws the toggle. These tests exist because that divergence is invisible
 * from either side: each answer is reasonable on its own, and only together do
 * they produce a switch that reads "on" over a party getting no suggestions.
 */
class PartyAutoplayTest {

    private fun member(id: String, host: Boolean = false, connected: Boolean = true) = PartyMember(
        memberId = id,
        displayName = id,
        isHost = host,
        connected = connected,
    )

    private fun party(
        members: List<PartyMember>,
        you: PartyMember? = members.firstOrNull(),
        partyAutoplay: Boolean = true,
        hostOnly: Boolean = false,
        code: String? = "ABC123",
    ) = ListenTogether.State(
        code = code,
        you = you,
        members = members,
        hostOnlyControl = hostOnly,
        playback = PartyPlayback(autoplayEnabled = partyAutoplay),
    )

    // ------------------------------------------------------------- enabled --

    @Test
    fun `alone, AutoPlay is this phone's own setting`() {
        val solo = ListenTogether.State()
        assertTrue(autoplayEnabledFor(solo, localPreference = true))
        assertFalse(autoplayEnabledFor(solo, localPreference = false))
    }

    @Test
    fun `in a party, the party's setting wins over the phone's`() {
        // The bug this pins: a listener whose own switch is on, in a party that
        // has AutoPlay off. The service queues nothing, so the player must not
        // draw the toggle as on — which is what reading the preference did.
        val off = party(listOf(member("a", host = true)), partyAutoplay = false)
        assertFalse(autoplayEnabledFor(off, localPreference = true))

        // And the other direction, which is the same bug wearing a hat: tracks
        // keep appending for a listener whose own switch is off.
        val on = party(listOf(member("a", host = true)), partyAutoplay = true)
        assertTrue(autoplayEnabledFor(on, localPreference = false))
    }

    // ------------------------------------------------------------ supplier --

    @Test
    fun `nobody supplies outside a party`() {
        assertNull(autoplaySupplierId(ListenTogether.State()))
    }

    @Test
    fun `the host supplies while it is connected`() {
        val state = party(listOf(member("zz", host = true), member("aa")))
        assertEquals("zz", autoplaySupplierId(state))
    }

    @Test
    fun `an offline host hands over to the lowest connected member`() {
        val state = party(
            listOf(
                member("zz", host = true, connected = false),
                member("mm"),
                member("aa"),
            ),
        )
        assertEquals("aa", autoplaySupplierId(state))
    }

    @Test
    fun `every device elects the same supplier`() {
        // The whole point of the election: five phones must not append five
        // different sets of suggestions to one queue. The membership arrives in
        // whatever order the server's map iteration produced, so the answer has
        // to be independent of it.
        val members = listOf(
            member("zz", host = true, connected = false),
            member("mm"),
            member("aa"),
        )
        val answers = listOf(
            members,
            members.reversed(),
            listOf(members[1], members[2], members[0]),
        ).map { autoplaySupplierId(party(it)) }
        assertEquals(listOf("aa", "aa", "aa"), answers)
    }

    @Test
    fun `a locked party has no stand-in supplier`() {
        // Verified against the running server: a listener's queueAdd in a
        // host-only party is refused with `host_only`, so electing one here
        // bought a guaranteed-refused control and an error on their phone
        // instead of suggestions. AutoPlay waits for the host instead.
        val state = party(
            listOf(member("zz", host = true, connected = false), member("aa")),
            hostOnly = true,
        )
        assertNull(autoplaySupplierId(state))
    }

    @Test
    fun `a locked party still supplies through its own host`() {
        val state = party(
            listOf(member("zz", host = true), member("aa")),
            hostOnly = true,
        )
        assertEquals("zz", autoplaySupplierId(state))
    }

    @Test
    fun `a party with nobody connected supplies nothing`() {
        val state = party(
            listOf(
                member("zz", host = true, connected = false),
                member("aa", connected = false),
            ),
        )
        assertNull(autoplaySupplierId(state))
    }
}
