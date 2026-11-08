package com.music.bitchord.data.deezer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeezerLibraryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun userSessionReadsUserIdTokenAndLovedTracks() {
        val root = json.parseToJsonElement(
            """
            {"error":[],"results":{
              "checkForm":"tok_123",
              "USER":{"USER_ID":42,"LOVEDTRACKS_ID":"99999"}
            }}
            """.trimIndent(),
        ).jsonObject
        val session = parseUserSession(root, "my_arl", "my_sid")
        assertEquals("42", session.userId)
        assertEquals("tok_123", session.apiToken)
        assertEquals("99999", session.lovedTracksId)
        assertEquals("arl=my_arl; sid=my_sid", session.cookieHeader())
    }

    @Test
    fun anonymousUserSessionThrowsExpired() {
        val root = json.parseToJsonElement(
            """
            {"error":[],"results":{
              "checkForm":"tok_123",
              "USER":{"USER_ID":0,"LOVEDTRACKS_ID":0}
            }}
            """.trimIndent(),
        ).jsonObject
        assertFailsWith<IllegalStateException> {
            parseUserSession(root, "expired_arl", null)
        }
    }

    @Test
    fun profilePlaylistsParsePicturesAndOwners() {
        val root = json.parseToJsonElement(
            """
            {"results":{"TAB":{"playlists":{"data":[
              {"PLAYLIST_ID":"908622995","TITLE":"En mode 60","PARENT_USERNAME":"Alexandre",
               "PLAYLIST_PICTURE":"4fd375a41c7779ed7deab64fb1194099","PICTURE_TYPE":"playlist"}
            ]}}}}
            """.trimIndent(),
        ).jsonObject
        val item = parseProfilePlaylists(root).single()
        assertEquals("908622995", item.id)
        assertEquals("En mode 60", item.name)
        assertEquals("Alexandre", item.owner)
        assertEquals(
            "https://cdn-images.dzcdn.net/images/playlist/4fd375a41c7779ed7deab64fb1194099/500x500-000000-80-0-0.jpg",
            item.imageUrl,
        )
    }

    @Test
    fun gwTracksParseTitleVersionArtistsAlbumAndDuration() {
        val root = json.parseToJsonElement(
            """
            {"results":{"total":1,"data":[
              {"SNG_ID":"116348632","SNG_TITLE":"Hey Jude","VERSION":"(Remastered 2015)",
               "ART_NAME":"The Beatles","ARTISTS":[{"ART_NAME":"The Beatles"}],
               "ALB_TITLE":"1 (Remastered 2015)","DURATION":"429",
               "ALB_PICTURE":"c65b3bd84e81056e060be144381c06c8"}
            ]}}
            """.trimIndent(),
        ).jsonObject
        val (tracks, total) = parseGwTrackPage(root)
        assertEquals(1, total)
        val track = tracks.single()
        assertEquals("116348632", track.id)
        assertEquals("Hey Jude (Remastered 2015)", track.title)
        assertEquals("The Beatles", track.artist)
        assertEquals("1 (Remastered 2015)", track.album)
        assertEquals(429_000, track.durationMs)
        assertEquals(
            "https://cdn-images.dzcdn.net/images/cover/c65b3bd84e81056e060be144381c06c8/500x500-000000-80-0-0.jpg",
            track.imageUrl,
        )
    }

    @Test
    fun apiTracksParseShortTitleArtistAlbumAndDuration() {
        val root = json.parseToJsonElement(
            """
            {"total":1,"data":[
              {"id":116348632,"title":"Hey Jude (Remastered 2015)","title_short":"Hey Jude",
               "duration":429,"artist":{"name":"The Beatles"},
               "album":{"title":"1 (Remastered 2015)","cover_big":"https://cdn-images.dzcdn.net/cover.jpg"}}
            ]}
            """.trimIndent(),
        ).jsonObject
        val track = parseApiTrackPage(root).first.single()
        assertEquals("116348632", track.id)
        assertEquals("Hey Jude", track.title)
        assertEquals("The Beatles", track.artist)
        assertEquals("1 (Remastered 2015)", track.album)
        assertEquals(429_000, track.durationMs)
        assertEquals("https://cdn-images.dzcdn.net/cover.jpg", track.imageUrl)
    }

    @Test
    fun signedInUserWithNoLovedTracksStillHasASession() {
        val root = json.parseToJsonElement(
            """{"error":[],"results":{"checkForm":"tok","USER":{"USER_ID":42,"LOVEDTRACKS_ID":0}}}""",
        ).jsonObject
        val session = parseUserSession(root, "arl", null)
        assertEquals("42", session.userId)
        assertNull(session.lovedTracksId)
    }

    @Test
    fun apiErrorObjectWithHttp200Throws() {
        val root = json.parseToJsonElement(
            """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""",
        ).jsonObject
        assertEquals(
            "Quota limit exceeded",
            assertFailsWith<IllegalStateException> { parseApiPlaylistPage(root) }.message,
        )
        assertFailsWith<IllegalStateException> { parseApiTrackPage(root) }
    }

    @Test
    fun apiPagesWithoutErrorParseAndEmptyPageEndsPaging() {
        val root = json.parseToJsonElement("""{"data":[],"total":0}""").jsonObject
        val (items, total, rawCount) = parseApiPlaylistPage(root)
        assertTrue(items.isEmpty())
        assertEquals(0, total)
        assertEquals(0, rawCount)
    }

    @Test
    fun playlistIdsComeFromDeezerLinksAndUris() {
        val id = "908622995"
        assertEquals(id, DeezerImporter.extractPlaylistId("https://www.deezer.com/playlist/$id"))
        assertEquals(id, DeezerImporter.extractPlaylistId("  https://www.deezer.com/en/playlist/$id?utm_source=deezer  "))
        assertEquals(id, DeezerImporter.extractPlaylistId("https://deezer.com/fr/playlist/$id"))
        assertEquals(id, DeezerImporter.extractPlaylistId("deezer:playlist:$id"))
        assertNull(DeezerImporter.extractPlaylistId("https://www.deezer.com/album/$id"))
        assertNull(DeezerImporter.extractPlaylistId("https://notdeezer.com/playlist/$id"))
        assertNull(DeezerImporter.extractPlaylistId("ftp://www.deezer.com/playlist/$id"))
        assertNull(DeezerImporter.extractPlaylistId("https://www.deezer.com/playlist/notanumber"))
        assertNull(DeezerImporter.extractPlaylistId(""))
        assertTrue(DeezerImporter.isShortLink("https://link.deezer.com/s/abc123"))
        assertTrue(DeezerImporter.isShortLink("https://dzr.page.link/xyz"))
        assertFalse(DeezerImporter.isShortLink("https://www.deezer.com/playlist/$id"))
    }
}
