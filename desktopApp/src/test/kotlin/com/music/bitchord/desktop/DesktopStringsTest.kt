package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The string catalogue: what it holds, what it falls back to, and that it is really translated. */
class DesktopStringsTest {

    @Test
    fun `every language offered has a catalogue behind it`() {
        DesktopStrings.available
            .filter { it.tag.isNotBlank() }
            .forEach { language ->
                val stream = DesktopStrings::class.java.getResourceAsStream("/strings/${language.tag}.xml")
                assertTrue(stream != null, "no catalogue for ${language.tag}")
                stream?.close()
            }
    }

    @Test
    fun `every language translates every desktop-only string`() {
        val english = keysIn("/strings/desktop-en.xml")
        assertTrue(english.size > 50, "the desktop catalogue looks empty")
        DesktopStrings.available
            .filter { it.tag.isNotBlank() && it.tag != "en" }
            .forEach { language ->
                val theirs = keysIn("/strings/desktop-${language.tag}.xml")
                val missing = english - theirs
                assertTrue(missing.isEmpty(), "${language.tag} is missing ${missing.size}: ${missing.take(3)}")
            }
    }

    @Test
    fun `a desktop-only string really is translated, not copied from English`() {
        val before = DesktopStrings.language.value
        try {
            DesktopStrings.setLanguage("en")
            val english = DesktopStrings["d_output_precision", "Output precision"]
            listOf("de", "fr", "ja", "ru").forEach { tag ->
                DesktopStrings.setLanguage(tag)
                assertNotEquals(english, DesktopStrings["d_output_precision", "Output precision"], tag)
            }
        } finally {
            DesktopStrings.setLanguage(before)
        }
    }

    private fun keysIn(resource: String): Set<String> {
        val stream = DesktopStrings::class.java.getResourceAsStream(resource)
        assertTrue(stream != null, "no catalogue at $resource")
        return stream!!.use { input ->
            Regex("""name="([^"]+)"""").findAll(input.readBytes().decodeToString())
                .map { it.groupValues[1] }
                .toSet()
        }
    }

    @Test
    fun `a key with no entry anywhere reads as the sentence it was written as`() {
        assertEquals(
            "Something only the desktop says",
            DesktopStrings["no_such_key_exists_anywhere", "Something only the desktop says"],
        )
    }

    @Test
    fun `a key that exists comes back from the catalogue rather than the fallback`() {
        // The fallback is deliberately wrong, so a pass proves the catalogue was read.
        assertEquals("Settings", DesktopStrings["settings", "WRONG"])
    }

    @Test
    fun `the system default resolves to a language there is a catalogue for`() {
        val tag = DesktopStrings.resolvedTag()
        assertTrue(DesktopStrings.available.any { it.tag == tag }, "unknown tag $tag")
    }

    @Test
    fun `switching language changes what comes back, and switching back restores it`() {
        val before = DesktopStrings.language.value
        try {
            DesktopStrings.setLanguage("en")
            val english = DesktopStrings["settings", "Settings"]
            DesktopStrings.setLanguage("de")
            val german = DesktopStrings["settings", "Settings"]
            assertNotEquals(english, german, "German should not read as English")
            assertEquals("Einstellungen", german)
            DesktopStrings.setLanguage("ja")
            assertNotEquals(english, DesktopStrings["settings", "Settings"])
        } finally {
            DesktopStrings.setLanguage(before)
        }
    }

    @Test
    fun `a locale missing a key falls back to English rather than showing the key`() {
        val before = DesktopStrings.language.value
        try {
            // Every catalogue is a subset of English, so a key only English has exercises this.
            DesktopStrings.setLanguage("hi")
            val value = DesktopStrings["settings", "Settings"]
            assertTrue(value.isNotBlank())
            assertNotEquals("settings", value, "a key leaked into the UI")
        } finally {
            DesktopStrings.setLanguage(before)
        }
    }

    @Test
    fun `no escape sequence ever reaches the screen`() {
        val before = DesktopStrings.language.value
        try {
            DesktopStrings.available.forEach { language ->
                DesktopStrings.setLanguage(language.tag)
                // The apostrophe that once rendered as "Don\u2019t".
                val value = DesktopStrings["d_dont_repeat_songs_in_current_session", "fallback"]
                assertFalse(value.contains("\\u"), "${language.tag}: $value")
                assertFalse(value.contains("\\'"), "${language.tag}: $value")
            }
        } finally {
            DesktopStrings.setLanguage(before)
        }
    }

    @Test
    fun `unescaping turns source escapes into the characters they stand for`() {
        assertEquals("Don\u2019t", DesktopStrings.unescape("Don\\u2019t"))
        assertEquals("it's", DesktopStrings.unescape("it\\'s"))
        assertEquals("one\ntwo", DesktopStrings.unescape("one\\ntwo"))
        assertEquals("nothing to do", DesktopStrings.unescape("nothing to do"))
    }

    @Test
    fun `positional arguments are filled in`() {
        assertEquals(
            "Keeps up to 2 GB of it",
            DesktopStrings.format("no_such_format_key", "2 GB", fallback = "Keeps up to %1\$s of it"),
        )
    }

    @Test
    fun `the language label names the stored choice`() {
        val before = DesktopStrings.language.value
        try {
            DesktopStrings.setLanguage("")
            assertEquals("System default", DesktopStrings.languageLabel())
            DesktopStrings.setLanguage("fr")
            assertEquals("Français", DesktopStrings.languageLabel())
        } finally {
            DesktopStrings.setLanguage(before)
        }
    }
}
