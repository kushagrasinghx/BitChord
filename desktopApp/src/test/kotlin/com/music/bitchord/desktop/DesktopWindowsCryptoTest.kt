package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class DesktopWindowsCryptoTest {

    @Test
    fun `windows source credentials round trip through current user DPAPI`() {
        if (!DesktopPlatform.isWindows) return
        val plain = "https://addon.example/private-token".toByteArray()
        val sealed = assertNotNull(DesktopWindowsCrypto.protect(plain))

        assertContentEquals(plain, DesktopWindowsCrypto.unprotect(sealed))
    }
}
