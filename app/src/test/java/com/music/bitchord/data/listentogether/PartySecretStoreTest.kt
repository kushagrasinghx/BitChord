package com.music.bitchord.data.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartySecretStoreTest {

    @Test
    fun existingSecureSecretIsUsedWithoutTouchingLegacy() {
        var removed = false
        val store = PartySecretStore(
            readSecure = { "secure-key" },
            writeSecureVerified = { throw AssertionError("secure key already exists") },
            readLegacy = { throw AssertionError("legacy should not be read") },
            removeLegacy = { removed = true },
        )
        assertEquals("secure-key", store.secretOrNull())
        assertFalse(removed)
    }

    @Test
    fun migratesLegacyOnlyAfterVerifiedSecureWrite() {
        var secure: String? = null
        var removed = false
        val store = PartySecretStore(
            readSecure = { secure },
            writeSecureVerified = { secure = it; true },
            readLegacy = { "legacy-key" },
            removeLegacy = { removed = true },
        )
        assertEquals("legacy-key", store.secretOrNull())
        assertEquals("legacy-key", secure)
        assertTrue(removed)
    }

    @Test
    fun generatesAndPersistsWhenNoLegacyExists() {
        var secure: String? = null
        val store = PartySecretStore(
            readSecure = { secure },
            writeSecureVerified = { secure = it; true },
            readLegacy = { null },
            removeLegacy = {},
            generate = { "generated-key" },
        )
        assertEquals("generated-key", store.secretOrNull())
        assertEquals("generated-key", secure)
    }

    @Test
    fun failedSecureWriteFailsClosedAndKeepsLegacy() {
        var removed = false
        val store = PartySecretStore(
            readSecure = { null },
            writeSecureVerified = { false },
            readLegacy = { "legacy-key" },
            removeLegacy = { removed = true },
        )
        assertNull(store.secretOrNull())
        assertFalse("legacy must survive a failed migration", removed)
    }

    @Test
    fun interruptedMigrationWithBadReadbackFailsClosed() {
        var removed = false
        val store = PartySecretStore(
            readSecure = { null },
            writeSecureVerified = { false }, // commit succeeded but readback mismatch is modelled here
            readLegacy = { "legacy-key" },
            removeLegacy = { removed = true },
        )
        assertNull(store.secretOrNull())
        assertFalse(removed)
    }
}
