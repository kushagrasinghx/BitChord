package com.music.bitchord.data.settings

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncInvitationCodecTest {
    @Test
    fun invitationRoundTripsProfileKeyAndOneTimePairingToken() {
        val invitation = SyncInvitation(
            serverUrl = "https://sync.example.com/sync",
            profileId = "profile-123",
            pairingToken = "one-time-token",
            profileKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }),
        )

        val code = SyncInvitationCodec.encode(invitation)

        assertEquals(invitation, SyncInvitationCodec.decode(code))
        assertEquals("bitchord-sync:v1:", code.take("bitchord-sync:v1:".length))
    }

    @Test
    fun invalidOrCleartextRemoteInvitationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncInvitationCodec.decode("not-a-bitchord-invite")
        }
        val invalidKey = SyncInvitation(
            serverUrl = "https://sync.example.com",
            profileId = "profile-123",
            pairingToken = "one-time-token",
            profileKey = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
        )
        assertThrows(IllegalArgumentException::class.java) { SyncInvitationCodec.encode(invalidKey) }

        val cleartext = SyncInvitation(
            serverUrl = "http://sync.example.com",
            profileId = "profile-123",
            pairingToken = "one-time-token",
            profileKey = Base64.getEncoder().encodeToString(ByteArray(32)),
        )
        assertThrows(IllegalArgumentException::class.java) { SyncInvitationCodec.encode(cleartext) }
    }
}
