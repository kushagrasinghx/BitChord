package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistPlaylistsTest {
    private fun p(name: String, owner: String? = "Spotify") = SpotifyPlaylist(id = name, name = name, owner = owner, imageUrl = null)

    @Test
    fun `Spotify's own playlists about the artist, This Is first`() {
        val found = listOf(
            p("Radiohead Live"),
            p("Radiohead Radio"),
            p("This Is Radiohead"),
            p("This Is Radiohead", owner = "sean burke"),
            p("Alternative 90s"),
        )
        assertEquals(
            listOf("This Is Radiohead", "Radiohead Radio", "Radiohead Live"),
            playlistsAbout("Radiohead", found).map { it.name },
        )
    }

    @Test
    fun `the whole name, ignoring case and accents`() {
        assertEquals(listOf("This Is Beyoncé"), playlistsAbout("Beyonce", listOf(p("This Is Beyoncé"))).map { it.name })
        // "Radio" is a word inside "Radiohead", not the artist Radio.
        assertEquals(emptyList(), playlistsAbout("Radio", listOf(p("This Is Radiohead"))).map { it.name })
    }

    @Test
    fun `a blank artist finds nothing`() {
        assertEquals(emptyList(), playlistsAbout("  ", listOf(p("This Is Radiohead"))))
    }
}
