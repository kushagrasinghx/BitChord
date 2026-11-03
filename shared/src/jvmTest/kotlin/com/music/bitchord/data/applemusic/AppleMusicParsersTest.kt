package com.music.bitchord.data.applemusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleMusicParsersTest {
    private fun root(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun catalogSongsReadMetadataAndExpandArtworkTemplates() {
        val page = parseTrackPage(root(
            """
            {"data":[{"id":"123","type":"songs","attributes":{
              "name":"Song","artistName":"Artist","albumName":"Album","durationInMillis":180123,
              "artwork":{"url":"https://image.example/{w}x{h}bb.{f}"},
              "isrc":"USAAA2400001","contentRating":"explicit"
            }}],"next":"/v1/catalog/us/songs?offset=1"}
            """.trimIndent(),
        ))
        assertEquals("/v1/catalog/us/songs?offset=1", page.next)
        assertEquals(
            AppleMusicTrack(
                id = "123", catalogId = "123", title = "Song", artist = "Artist", album = "Album",
                durationMs = 180123, imageUrl = "https://image.example/600x600bb.jpg",
                isrc = "USAAA2400001", explicit = true,
            ),
            page.items.single(),
        )
    }

    @Test
    fun libraryIdAndExplicitPlayParamsCatalogIdRemainSeparate() {
        val track = parseTrackPage(root(
            """
            {"data":[{"id":"i.library","type":"library-songs","attributes":{
              "name":"Saved","contentRating":"clean",
              "playParams":{"id":"i.library","catalogId":"456","isLibrary":true}
            }}]}
            """.trimIndent(),
        )).items.single()
        assertEquals("i.library", track.id)
        assertEquals("456", track.catalogId)
        assertEquals(false, track.explicit)
    }

    @Test
    fun catalogRelationshipCanSupplyAnExplicitMappingAndMissingMetadata() {
        val track = parseTrackPage(root(
            """
            {"data":[{"id":"i.saved","type":"library-songs","attributes":{"name":"My title"},
              "relationships":{"catalog":{"data":[{"id":"789","type":"songs","attributes":{
                "name":"Catalog title","artistName":"Artist","albumName":"Album",
                "durationInMillis":200000,"isrc":"GBBBB2400001",
                "artwork":{"url":"https://image.example/{w}/{h}"}
              }}]}}
            }]}
            """.trimIndent(),
        )).items.single()
        assertEquals("i.saved", track.id)
        assertEquals("789", track.catalogId)
        assertEquals("My title", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals(200000, track.durationMs)
        assertEquals("GBBBB2400001", track.isrc)
        assertEquals("https://image.example/600/600", track.imageUrl)
    }

    @Test
    fun libraryOnlyNumericIdsAreNotMistakenForCatalogIds() {
        val tracks = parseTrackPage(root(
            """
            {"data":[
              {"id":"123456","type":"library-songs","attributes":{
                "playParams":{"id":"123456","isLibrary":true}
              }},
              {"id":"i.upload","type":"library-songs","attributes":{"name":"Local upload"},
                "relationships":{"catalog":{"data":[]}}},
              {"id":"i.unavailable","type":"library-songs","attributes":null},
              {"id":"i.bare","type":"library-songs"}
            ]}
            """.trimIndent(),
        )).items
        assertEquals(4, tracks.size)
        assertTrue(tracks.all { it.catalogId == null })
        assertEquals("Local upload", tracks[1].title)
        assertEquals(AppleMusicTrack(id = "i.unavailable"), tracks[2])
        assertEquals(AppleMusicTrack(id = "i.bare"), tracks[3])
    }

    @Test
    fun aLibraryResourceInTheCatalogRelationshipIsNotACatalogMapping() {
        val track = parseTrackPage(root(
            """
            {"data":[{"id":"i.a","type":"library-songs",
              "relationships":{"catalog":{"data":[{"id":"i.b","type":"library-songs"}]}}
            }]}
            """.trimIndent(),
        )).items.single()
        assertNull(track.catalogId)
    }

    @Test
    fun searchReadsNestedSongsAndNeverIncludesVideosOrOtherResultTypes() {
        val page = parseSearchSongsPage(root(
            """
            {"results":{
              "songs":{"data":[
                {"id":"1","type":"songs","attributes":{"name":"Song"}},
                {"id":"2","type":"music-videos","attributes":{"name":"Video"}},
                {"id":"3","type":"albums"},null,7
              ],"next":"/v1/catalog/us/search?offset=25"},
              "music-videos":{"data":[{"id":"4","type":"music-videos"}]}
            },"next":"https://not-the-songs-page.example/"}
            """.trimIndent(),
        ))
        assertEquals(listOf("1"), page.items.map { it.id })
        assertFalse(page.items.single().isVideo)
        assertEquals("/v1/catalog/us/search?offset=25", page.next)
        assertTrue(parseSearchSongsPage(root("""{"results":{}}""")).items.isEmpty())
    }

    @Test
    fun mixedPlaylistTracksKeepVideosFlaggedWhileSongLookupsFilterThem() {
        val payload = root(
            """
            {"data":[
              {"id":"v1","type":"music-videos","attributes":{"name":"Video"}},
              {"id":"i.v2","type":"library-music-videos","attributes":{
                "playParams":{"catalogId":"v2"}
              }},
              {"id":"s1","type":"songs"}
            ]}
            """.trimIndent(),
        )
        val tracks = parseTrackPage(payload).items
        assertEquals(listOf(true, true, false), tracks.map { it.isVideo })
        assertEquals(listOf("v1", "v2", "s1"), tracks.map { it.catalogId })
        assertEquals(listOf("s1"), parseTrackPage(payload, songsOnly = true).items.map { it.id })
    }

    @Test
    fun missingOrMalformedOptionalMetadataUsesDefaultsWithoutDroppingRows() {
        val tracks = parseTrackPage(root(
            """
            {"data":[null,{},[],{"type":"songs"},{"id":"","type":"songs"},
              {"id":"a","type":"songs","attributes":{
                "durationInMillis":-20,"contentRating":"unrecognized","name":{},"artwork":[]
              }},
              {"id":"b","type":"songs","attributes":{
                "durationInMillis":999999999999,"contentRating":null
              }}
            ],"next":null}
            """.trimIndent(),
        )).items
        assertEquals(listOf("a", "b"), tracks.map { it.id })
        assertTrue(tracks.all { it.title.isEmpty() && it.durationMs == 0 && it.explicit == null })
    }

    @Test
    fun playlistsExposeRealIdsAndOptionalFavoriteMetadataWithoutSyntheticRows() {
        val playlists = parsePlaylistPage(root(
            """
            {"data":[
              {"id":"p.real-favorites","type":"library-playlists","attributes":{
                "name":"Favorite Songs","canEdit":false,"isFavorite":true,"playlistType":"personal-mix",
                "description":{"standard":"Saved songs"},"artwork":{"url":"https://img/{w}x{h}"}
              }},
              {"id":"p.road","type":"library-playlists","attributes":{
                "name":"Road","curatorName":"Me","canEdit":true,"playParams":{"catalogId":"pl.catalog"}
              }}
            ]}
            """.trimIndent(),
        )).items
        assertEquals(2, playlists.size)
        assertEquals("p.real-favorites", playlists[0].id)
        assertNull(playlists[0].catalogId)
        assertEquals(true, playlists[0].isFavorite)
        assertEquals("personal-mix", playlists[0].playlistType)
        assertEquals("Saved songs", playlists[0].description)
        assertEquals("https://img/600x600", playlists[0].imageUrl)
        assertFalse(playlists[0].canEdit)
        assertTrue(playlists[1].canEdit)
        assertEquals("Me", playlists[1].owner)
        assertEquals("pl.catalog", playlists[1].catalogId)
        assertNull(playlists[1].isFavorite)
        assertTrue(parsePlaylistPage(root("""{"data":[]}""")).items.isEmpty())
    }

    @Test
    fun catalogPlaylistAndLibraryAlbumAndArtistParseTheirOwnIdentities() {
        val playlist = parsePlaylistPage(root(
            """{"data":[{"id":"pl.123","type":"playlists","attributes":{
              "name":"Catalog","curatorName":"Apple Music","description":{"short":"Short"}
            }}]}""",
        )).items.single()
        assertEquals("pl.123", playlist.catalogId)
        assertEquals("Apple Music", playlist.owner)
        assertEquals("Short", playlist.description)

        val album = parseAlbumPage(root(
            """{"data":[{"id":"l.album","type":"library-albums","attributes":{
              "name":"Album","artistName":"Artist","trackCount":12,"releaseDate":"2024-01-01"
            },"relationships":{"catalog":{"data":[{"id":"10","type":"albums"}]}}}]}""",
        )).items.single()
        assertEquals("l.album", album.id)
        assertEquals("10", album.catalogId)
        assertEquals("Album", album.name)
        assertEquals("Artist", album.artist)
        assertEquals(12, album.trackCount)
        assertEquals("2024-01-01", album.releaseDate)

        val artist = parseArtistPage(root(
            """{"data":[{"id":"r.artist","type":"library-artists","attributes":{"name":"Artist"}}]}""",
        )).items.single()
        assertEquals("Artist", artist.name)
        assertNull(artist.catalogId)
    }

    @Test
    fun recentlyAddedPreservesServerOrderAndIgnoresUnknownResourceTypes() {
        val items = parseRecentlyAddedPage(root(
            """
            {"data":[
              {"id":"l.album","type":"library-albums"},
              {"id":"p.list","type":"library-playlists"},
              {"id":"future","type":"future-resource"},
              {"id":"i.song","type":"library-songs"},
              {"id":"r.artist","type":"library-artists"}
            ]}
            """.trimIndent(),
        )).items
        assertEquals(listOf("l.album", "p.list", "i.song", "r.artist"), items.map { it.id })
        assertTrue(items[0] is AppleMusicAlbum)
        assertTrue(items[1] is AppleMusicPlaylist)
        assertTrue(items[2] is AppleMusicTrack)
        assertTrue(items[3] is AppleMusicArtist)
    }

    @Test
    fun storefrontIsReadFromTheUserResponseAndNeverGuessed() {
        assertEquals("in", parseStorefront(root("""{"data":[{"id":"in","type":"storefronts"}]}""")))
        assertFailsWith<IllegalStateException> { parseStorefront(root("""{"data":[]}""")) }
        assertFailsWith<IllegalStateException> { parseStorefront(root("""{"data":[{"id":"us","type":"songs"}]}""")) }
    }

    @Test
    fun malformedEnvelopesAndNonStringNextLinksAreRejected() {
        assertFailsWith<IllegalStateException> { parseTrackPage(root("""{}""")) }
        assertFailsWith<IllegalStateException> { parseSearchSongsPage(root("""{}""")) }
        assertFailsWith<IllegalStateException> { parseTrackPage(root("""{"data":[],"next":{}}""")) }
        assertFailsWith<IllegalStateException> { parseTrackPage(root("""{"data":[],"next":123}""")) }
    }
}
