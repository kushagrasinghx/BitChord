package com.music.bitchord.desktop

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `java.util.prefs` throws when a value passes [Preferences.MAX_VALUE_LENGTH], on whichever thread
 * was saving — which for this app is the UI thread.
 */
class DesktopPreferenceChunksTest {

    private val node: Preferences = Preferences.userRoot().node("com.music.bitchord.desktop.test.chunks")

    @AfterTest
    fun tearDown() {
        runCatching { node.removeNode() }
    }

    @Test
    fun aValueLongerThanThePlatformLimitSurvivesARoundTrip() {
        val huge = buildString {
            repeat(Preferences.MAX_VALUE_LENGTH * 3) { append('a' + (it % 26)) }
        }
        assertTrue(huge.length > Preferences.MAX_VALUE_LENGTH)

        // Writing this straight into a preference is what crashed the app.
        DesktopPreferenceChunks.write(node, "entries", huge)

        assertEquals(huge, DesktopPreferenceChunks.read(node, "entries"))
    }

    @Test
    fun aShorterValueLeavesNoTailOfTheLongerOneItReplaced() {
        DesktopPreferenceChunks.write(node, "queue", "x".repeat(Preferences.MAX_VALUE_LENGTH * 2))

        DesktopPreferenceChunks.write(node, "queue", "short")

        assertEquals("short", DesktopPreferenceChunks.read(node, "queue"))
    }

    @Test
    fun aValueWrittenBeforeChunkingIsStillReadable() {
        node.put("legacy", "written by an older build")

        assertEquals("written by an older build", DesktopPreferenceChunks.read(node, "legacy"))
    }

    @Test
    fun anUnwrittenKeyReadsAsNothing() {
        assertNull(DesktopPreferenceChunks.read(node, "never-written"))
    }

    @Test
    fun anEmptyValueIsStoredRatherThanLost() {
        DesktopPreferenceChunks.write(node, "empty", "")

        assertEquals("", DesktopPreferenceChunks.read(node, "empty"))
    }

    @Test
    fun `characters xml cannot carry never reach the store`() {
        val node = Preferences.userRoot().node("bitchord-test-${System.nanoTime()}")
        try {
            // 0x1f is the one that was actually observed.
            DesktopPreferenceChunks.write(node, "k", "before\u001Fafter\u0000end")

            val stored = DesktopPreferenceChunks.read(node, "k")
            assertEquals("beforeafterend", stored)
            assertTrue(stored!!.none { it < ' ' })

            // Tab, newline and carriage return are legal XML and survive.
            DesktopPreferenceChunks.write(node, "k2", "a\tb\nc\rd")
            assertEquals("a\tb\nc\rd", DesktopPreferenceChunks.read(node, "k2"))
        } finally {
            node.removeNode()
        }
    }
}
