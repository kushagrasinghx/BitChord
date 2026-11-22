package com.music.bitchord

import com.music.bitchord.data.OfflinePlaylists
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Naming is the one part of the device playlist store that can be decided
 * without Android's preference store: two playlists with the same name are
 * indistinguishable in the picker, and a new one has to take the next free
 * variant rather than silently shadowing the first.
 */
class OfflinePlaylistsTest {

    @Test
    fun `a free title is kept as written`() {
        assertEquals("Road trip", OfflinePlaylists.uniqueTitle("Road trip", emptyList()))
    }

    @Test
    fun `a duplicate takes the first free number`() {
        assertEquals("Road trip 2", OfflinePlaylists.uniqueTitle("Road trip", listOf("Road trip")))
        assertEquals(
            "Road trip 3",
            OfflinePlaylists.uniqueTitle("Road trip", listOf("Road trip", "Road trip 2")),
        )
    }

    @Test
    fun `an existing numbered name does not make the next one skip`() {
        assertEquals(
            "Road trip 2",
            OfflinePlaylists.uniqueTitle("Road trip", listOf("Road trip", "Road trip 3")),
        )
    }

    @Test
    fun `duplicate detection ignores case and surrounding space`() {
        assertEquals("road trip 2", OfflinePlaylists.uniqueTitle(" road trip ", listOf("Road trip")))
    }

    @Test
    fun `a blank title becomes a usable default`() {
        assertEquals("Playlist", OfflinePlaylists.uniqueTitle("   ", emptyList()))
        assertEquals("Playlist 2", OfflinePlaylists.uniqueTitle("", listOf("Playlist")))
    }
}
