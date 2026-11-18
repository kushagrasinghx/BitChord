package com.music.bitchord.sync

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SyncSnapshotCryptoTest {
    private val profileKey = ByteArray(32) { it.toByte() }

    @Test
    fun encryptionRoundTripsAndUsesAFreshNonce() {
        val snapshot = SyncSnapshot(
            fields = mapOf(
                "theme_mode" to SyncField(FieldClock(3, "phone-a"), SyncValue("string", "DARK")),
                "lyrics_offset_ms" to SyncField(FieldClock(4, "phone-b"), SyncValue("int", "250")),
            ),
        )

        val first = SyncSnapshotCrypto.encrypt(snapshot, "profile-123", profileKey)
        val second = SyncSnapshotCrypto.encrypt(snapshot, "profile-123", profileKey)

        assertNotEquals(first, second)
        assertEquals(snapshot, SyncSnapshotCrypto.decrypt(first, "profile-123", profileKey))
    }

    @Test
    fun encryptionBindsCiphertextToProfileAndRejectsTampering() {
        val snapshot = SyncSnapshot(fields = mapOf(
            "theme_mode" to SyncField(FieldClock(1, "phone-a"), SyncValue("string", "DARK")),
        ))
        val ciphertext = SyncSnapshotCrypto.encrypt(snapshot, "profile-123", profileKey)

        assertFailsWith<AEADBadTagException> {
            SyncSnapshotCrypto.decrypt(ciphertext, "another-profile", profileKey)
        }

        val tamperedChars = ciphertext.toCharArray()
        val tamperIndex = tamperedChars.size / 2
        tamperedChars[tamperIndex] = if (tamperedChars[tamperIndex] == 'A') 'B' else 'A'
        val tampered = tamperedChars.concatToString()
        assertFailsWith<AEADBadTagException> {
            SyncSnapshotCrypto.decrypt(tampered, "profile-123", profileKey)
        }
    }
}
