package com.music.bitchord.playback

import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyPlayback
import com.music.bitchord.data.listentogether.PartySnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What "only the host controls the music" means on this side of the wire.
 *
 * The enforcement itself is the server's — see `backend/party.MayControl` — so
 * what is worth pinning down here is the derivation every player surface reads,
 * and the two compatibility cases that decide whether a party is locked or not
 * when the answer is missing.
 */
class PartyHostOnlyControlTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun member(id: String, host: Boolean) = PartyMember(
        memberId = id,
        displayName = id,
        isHost = host,
    )

    private fun state(
        code: String? = "ABC123",
        you: PartyMember? = member("me", host = false),
        hostOnly: Boolean = false,
    ) = ListenTogether.State(
        code = code,
        you = you,
        members = listOfNotNull(you),
        hostOnlyControl = hostOnly,
        playback = PartyPlayback(),
    )

    @Test
    fun `a listener is locked out while the host holds the controls`() {
        assertTrue(state(hostOnly = true).controlsLocked)
    }

    @Test
    fun `the host is never locked out of their own party`() {
        val asHost = state(you = member("me", host = true), hostOnly = true)
        assertFalse(asHost.controlsLocked)
    }

    @Test
    fun `an open party locks nobody`() {
        assertFalse(state(hostOnly = false).controlsLocked)
    }

    @Test
    fun `no party is not a locked party`() {
        // The setting is meaningless outside a party, and a stale true here
        // would silently disable the transport for somebody listening alone.
        assertFalse(state(code = null, hostOnly = true).controlsLocked)
    }

    @Test
    fun `being promoted to host unlocks the controls`() {
        // The server hands the role to an arbitrary survivor when a host
        // leaves, and says so only by reissuing the member list.
        val locked = state(hostOnly = true)
        assertTrue(locked.controlsLocked)

        val promoted = locked.copy(you = member("me", host = true))
        assertFalse(promoted.controlsLocked)
    }

    @Test
    fun `a snapshot from a server without the setting reads as unlocked`() {
        // Old server, new app: the field is simply absent, and the party has to
        // behave exactly as it did before the feature existed.
        val snapshot = json.decodeFromString(
            PartySnapshot.serializer(),
            """{"code":"ABC123","maxMembers":5,"members":[]}""",
        )
        assertFalse(snapshot.hostOnlyControl)
    }

    @Test
    fun `a snapshot carrying the setting keeps it`() {
        val snapshot = json.decodeFromString(
            PartySnapshot.serializer(),
            """{"code":"ABC123","maxMembers":5,"hostOnlyControl":true,"members":[]}""",
        )
        assertTrue(snapshot.hostOnlyControl)
        assertEquals("ABC123", snapshot.code)
    }
}
