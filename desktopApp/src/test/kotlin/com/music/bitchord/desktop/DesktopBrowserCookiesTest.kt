package com.music.bitchord.desktop

import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBrowserCookiesTest {
    @Test
    fun `finds an installed browser for interactive Windows sign-in`() {
        if (DesktopPlatform.isWindows) assertNotNull(DesktopBrowserSignIn.preferred())
    }

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

    @Test
    fun `decrypts Chromium macOS Keychain cookies`() {
        // Sealed outside this codebase (Python's PBKDF2 and the openssl CLI) with the scheme
        // Chromium uses on macOS: "v10", PBKDF2-SHA1 over "saltysalt" for 1003 rounds, AES-128-CBC
        // with an IV of sixteen spaces.
        val password = "test-keychain-password".toByteArray()
        val plain = "7631302595b364aadb6db3b4f00b02fad12f7679a8a9b462c6da164edc8e63235d5abe".hexBytes()
        val hosted = ("763130e0b1a0e4e2cfe4b66cdc53ff4a090007f4a042eebd01593207fd5d3d085b77464f" +
            "22930ff0e40cabe36c65f6366a02ac").hexBytes()

        assertEquals("SAPISID-value-123", DesktopBrowserCookies.decryptMac(plain, "", password))
        // Newer cookie stores prefix the value with the SHA-256 of its host.
        assertEquals("hosted-value", DesktopBrowserCookies.decryptMac(hosted, ".youtube.com", password))
        assertNull(DesktopBrowserCookies.decryptMac(plain, "", "wrong-password".toByteArray()))
        assertNull(DesktopBrowserCookies.decryptMac("v11".toByteArray() + plain.copyOfRange(3, plain.size), "", password))
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
