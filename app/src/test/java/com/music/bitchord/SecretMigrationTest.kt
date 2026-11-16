package com.music.bitchord

import com.music.bitchord.data.settings.migratedSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretMigrationTest {

    @Test
    fun `an encrypted secret is never overwritten by a stale plaintext copy`() {
        assertEquals("encrypted", migratedSecret("plain", "encrypted"))
    }

    @Test
    fun `a legacy plaintext secret is migrated when encryption has none`() {
        assertEquals("plain", migratedSecret("plain", null))
        assertEquals("plain", migratedSecret("plain", ""))
    }

    @Test
    fun `blank values migrate to nothing`() {
        assertNull(migratedSecret(null, null))
        assertNull(migratedSecret("", "  "))
    }
}
