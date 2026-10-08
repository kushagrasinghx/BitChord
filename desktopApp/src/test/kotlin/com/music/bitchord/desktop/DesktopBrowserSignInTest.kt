package com.music.bitchord.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading the macOS default browser, and noticing when the sign-in profile holds a session. */
class DesktopBrowserSignInTest {

    @Test
    fun `takes the https handler, lower-cased`() {
        val handlers = """
            [
              {"LSHandlerContentType": "public.html", "LSHandlerRoleAll": "com.apple.safari"},
              {"LSHandlerURLScheme": "http", "LSHandlerRoleAll": "com.apple.safari"},
              {"LSHandlerURLScheme": "https", "LSHandlerRoleAll": "com.brave.Browser",
               "LSHandlerPreferredVersions": {"LSHandlerRoleAll": "196.61"}}
            ]
        """.trimIndent()
        assertEquals("com.brave.browser", DesktopBrowserSignIn.httpsHandlerId(handlers))
    }

    @Test
    fun `no https entry or unreadable output means no default`() {
        assertNull(DesktopBrowserSignIn.httpsHandlerId("""[{"LSHandlerURLScheme": "mailto", "LSHandlerRoleAll": "x"}]"""))
        assertNull(DesktopBrowserSignIn.httpsHandlerId(""))
    }

    @Test
    fun `a signing cookie on the service's domain counts as signed in`() {
        val youtube = DesktopBrowserSignIn.Service.YOUTUBE_MUSIC
        val spotify = DesktopBrowserSignIn.Service.SPOTIFY
        val signedOut = profileWith("YSC" to ".youtube.com", "SAPISID" to ".google.com", "sp_t" to ".spotify.com")
        val signedIn = profileWith("SAPISID" to ".youtube.com", "sp_dc" to ".spotify.com")
        // A lookalike host must not pass for the real one.
        val lookalike = profileWith("sp_dc" to ".notspotify.com")
        fun DesktopBrowserSignIn.Service.isIn(profile: Path) =
            DesktopBrowserCookies.chromiumHasCookie(profile, cookieDomain, signingCookies)
        try {
            assertFalse(youtube.isIn(signedOut))
            assertFalse(spotify.isIn(signedOut))
            assertTrue(youtube.isIn(signedIn))
            assertTrue(spotify.isIn(signedIn))
            assertFalse(spotify.isIn(lookalike))
            assertFalse(spotify.isIn(Files.createTempDirectory("empty")))
        } finally {
            listOf(signedOut, signedIn, lookalike).forEach { it.toFile().deleteRecursively() }
        }
    }

    /** A user-data directory whose `Default/Network/Cookies` holds [cookies] as name to host. */
    private fun profileWith(vararg cookies: Pair<String, String>): Path {
        val root = Files.createTempDirectory("sign-in-profile")
        val network = Files.createDirectories(root.resolve("Default/Network"))
        DriverManager.getConnection("jdbc:sqlite:${network.resolve("Cookies")}").use { db ->
            db.createStatement().use { it.execute("CREATE TABLE cookies (host_key TEXT, name TEXT, encrypted_value BLOB)") }
            db.prepareStatement("INSERT INTO cookies (host_key, name, encrypted_value) VALUES (?, ?, x'00')").use { insert ->
                cookies.forEach { (name, host) ->
                    insert.setString(1, host)
                    insert.setString(2, name)
                    insert.executeUpdate()
                }
            }
        }
        return root
    }
}
