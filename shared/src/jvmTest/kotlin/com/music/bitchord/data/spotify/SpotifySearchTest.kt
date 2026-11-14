package com.music.bitchord.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spotify's playlist search answers, trimmed from live `searchPlaylists` and `searchDesktop` responses. */
class SpotifySearchTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(items: String) = parseSearchPlaylists(
        json.parseToJsonElement("""{"data":{"searchV2":{"playlists":{"items":[$items]}}}}""").jsonObject,
    )

    @Test
    fun `a search lists each playlist with its owner and widest cover`() {
        val found = parse(
            """
            {"data":{"__typename":"Playlist","uri":"spotify:playlist:37i9dQZF1DX4sWSpwq3LiO","name":"Peaceful Piano",
              "ownerV2":{"data":{"name":"Spotify"}},
              "images":{"items":[{"sources":[{"url":"https://i.scdn.co/small","width":60},{"url":"https://i.scdn.co/big","width":640}]}]}}}
            """,
        )
        val playlist = found.single()
        assertEquals("37i9dQZF1DX4sWSpwq3LiO", playlist.id)
        assertEquals("Peaceful Piano", playlist.name)
        assertEquals("Spotify", playlist.owner)
        assertEquals("https://i.scdn.co/big", playlist.imageUrl)
    }

    @Test
    fun `the All tab's item-wrapped shape reads the same`() {
        val found = parse("""{"item":{"data":{"__typename":"Playlist","uri":"spotify:playlist:abc","name":"Road"}}}""")
        assertEquals("abc", found.single().id)
        assertNull(found.single().imageUrl)
    }

    @Test
    fun `anything that isn't a named playlist, and repeats, are left out`() {
        val found = parse(
            """
            {"data":{"__typename":"Album","uri":"spotify:album:x","name":"An album"}},
            {"data":{"__typename":"Playlist","uri":"spotify:playlist:a","name":"  "}},
            {"data":{"__typename":"Playlist","uri":"spotify:playlist:b","name":"Kept"}},
            {"data":{"__typename":"Playlist","uri":"spotify:playlist:b","name":"Kept again"}}
            """,
        )
        assertEquals(listOf("b"), found.map { it.id })
    }

    @Test
    fun `an answer with no playlists is an empty list`() {
        assertTrue(parseSearchPlaylists(json.parseToJsonElement("""{"data":{}}""").jsonObject).isEmpty())
    }

    @Test
    fun `the Playlists tab's query is asked for when declared, else the All tab's`() {
        val playlists = "a".repeat(64)
        val desktop = "b".repeat(64)
        val both = """x("searchDesktop","query","$desktop",{});y("searchPlaylists","query","$playlists",{})"""
        assertEquals("searchPlaylists" to playlists, SpotifyLibrary.findSearch(both))
        assertEquals("searchDesktop" to desktop, SpotifyLibrary.findSearch("""x("searchDesktop","query","$desktop")"""))
        assertNull(SpotifyLibrary.findSearch("""x("canvas","query","$playlists")"""))
    }
}
