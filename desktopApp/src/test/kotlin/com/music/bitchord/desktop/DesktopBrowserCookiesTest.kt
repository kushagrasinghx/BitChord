package com.music.bitchord.desktop

import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBrowserCookiesTest {
    @Test
    fun `recognises a YouTube signing cookie`() {
        assertTrue(DesktopBrowserCookies.hasSigningSecret("SID=x; __Secure-3PAPISID=secret; other=y"))
    }

    @Test
    fun `reads Chromium profile display names from Local State`() {
        val state = """
            {
              "profile": {
                "info_cache": {
                  "Default": { "name": "Kushagra" },
                  "Profile 26": { "name": "Music account" }
                }
              }
            }
        """.trimIndent()

        assertEquals(
            mapOf("Default" to "Kushagra", "Profile 26" to "Music account"),
            DesktopBrowserCookies.chromiumProfileNames(state),
        )
    }

    @Test
    fun `decrypts Chromium Windows AES GCM cookies`() {
        val random = SecureRandom()
        val key = ByteArray(32).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val value = "youtube-session-value"
        val host = ".youtube.com"
        val hostDigest = MessageDigest.getInstance("SHA-256").digest(host.toByteArray())
        val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            doFinal(hostDigest + value.toByteArray())
        }
        val sealed = "v10".toByteArray() + nonce + encrypted

        assertEquals(value, DesktopBrowserCookies.decryptWindows(sealed, key, host))
        assertNull(DesktopBrowserCookies.decryptWindows(sealed, null))
    }
}
