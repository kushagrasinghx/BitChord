package com.music.bitchord.sync

import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedSyncVaultTest {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")

    @Test
    fun secretsRoundTripOnlyAsAuthenticatedCiphertextAndRemainOptIn() {
        val storage = MemoryStorage()
        val vault = EncryptedSyncVault(storage, SyncVaultKeyProvider { key })
        val state = localState(enabled = false)

        vault.save(state)

        val raw = requireNotNull(storage.value)
        assertFalse(raw.contains(state.deviceToken))
        assertFalse(raw.contains(state.profileKey))
        assertEquals(state, vault.load())
        assertFalse(requireNotNull(vault.load()).enabled)
    }

    @Test
    fun corruptOrWrongKeyStateFailsClosedWithoutDeletingStoredValue() {
        val storage = MemoryStorage()
        val vault = EncryptedSyncVault(storage, SyncVaultKeyProvider { key })
        vault.save(localState())
        val original = requireNotNull(storage.value)
        val tampered = original.toCharArray().also { chars ->
            val index = chars.size / 2
            chars[index] = if (chars[index] == 'A') 'B' else 'A'
        }.concatToString()
        storage.value = tampered

        assertThrows(SyncVaultException::class.java) { vault.load() }
        assertEquals(tampered, storage.value)

        storage.value = original
        val otherKey = SecretKeySpec(ByteArray(32) { (it + 2).toByte() }, "AES")
        val wrongKeyVault = EncryptedSyncVault(storage, SyncVaultKeyProvider { otherKey })
        assertThrows(SyncVaultException::class.java) { wrongKeyVault.load() }
        assertEquals(original, storage.value)
    }

    @Test
    fun emptyVaultLoadsNullAndInvalidProfileKeyIsRejectedBeforeWrite() {
        val storage = MemoryStorage()
        val vault = EncryptedSyncVault(storage, SyncVaultKeyProvider { key })
        assertNull(vault.load())

        val invalid = localState().copy(profileKey = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
        assertThrows(IllegalArgumentException::class.java) { vault.save(invalid) }
        assertNull(storage.value)
    }

    private fun localState(enabled: Boolean = true) = SyncLocalState(
        serverUrl = "https://sync.example.com/sync",
        profileId = "profile-123",
        deviceId = "device-456",
        deviceToken = "device-token-secret",
        profileKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }),
        revision = 4,
        baselinePreferences = mapOf("theme_mode" to SyncValue("string", "DARK")),
        enabled = enabled,
    )

    private class MemoryStorage : SyncVaultStorage {
        var value: String? = null
        override fun read(): String? = value
        override fun write(value: String): Boolean { this.value = value; return true }
        override fun clear(): Boolean { value = null; return true }
    }
}
