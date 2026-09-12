package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The library feeds behind the Library tab. */
class DesktopLibraryTest {

    @Test
    fun savedCollectionsComeBackAsCards() {
        val response = json(
            """
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"sectionListRenderer":{"contents":[{"gridRenderer":{"items":[
              {"musicTwoRowItemRenderer":{
                "title":{"runs":[{"text":"Liked Music"}]},
                "subtitle":{"runs":[{"text":"Auto playlist"}]},
                "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                  {"url":"https://example.invalid/liked.jpg","width":226,"height":226}]}}},
                "navigationEndpoint":{"browseEndpoint":{"browseId":"VLLM"}}}},
              {"musicTwoRowItemRenderer":{
                "title":{"runs":[{"text":"Late nights"}]},
                "subtitle":{"runs":[{"text":"Playlist"},{"text":" • "},{"text":"41 songs"}]},
                "navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLabc"}}}}
            ]}}]}}}}]}}}
            """,
        )

        val items = DesktopSearchClient.parseLibraryItems(response)

        assertEquals(listOf("Liked Music", "Late nights"), items.map { it.title })
        assertEquals("VLLM", items[0].browseId)
        assertEquals("https://example.invalid/liked.jpg", items[0].thumbnailUrl)
        // A saved collection is a place to open, never a track to play.
        assertTrue(items.all { it.videoId == null })
    }

    @Test
    fun savedArtistsArriveAsRowsAndStillBecomeCards() {
        val response = json(
            """
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{
                "flexColumns":[
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Daft Punk"}]}}},
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"12 songs"}]}}}],
                "navigationEndpoint":{"browseEndpoint":{"browseId":"UCabc",
                  "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":
                    {"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}}}
            ]}}]}}}}]}}}
            """,
        )

        val items = DesktopSearchClient.parseLibraryItems(response)

        assertEquals(1, items.size)
        assertEquals("Daft Punk", items[0].title)
        assertEquals("UCabc", items[0].browseId)
        assertNull(items[0].videoId)
    }

    @Test
    fun aCollectionListedTwiceIsListedOnce() {
        // A feed that renders the same saved album as a tile and as a row.
        val response = json(
            """
            {"contents":{"sectionListRenderer":{"contents":[
              {"gridRenderer":{"items":[
                {"musicTwoRowItemRenderer":{
                  "title":{"runs":[{"text":"Random Access Memories"}]},
                  "subtitle":{"runs":[{"text":"Album"}]},
                  "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_1"}}}}]}},
              {"musicShelfRenderer":{"contents":[
                {"musicResponsiveListItemRenderer":{
                  "flexColumns":[
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Random Access Memories"}]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Album"}]}}}],
                  "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_1"}}}}]}}
            ]}}}
            """,
        )

        assertEquals(1, DesktopSearchClient.parseLibraryItems(response).size)
    }

    @Test
    fun anEmptyFeedIsEmptyRatherThanAFailure() {
        // A fresh account has no saved albums at all.
        val response = json(
            """
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[]}}]}}}}]}}}
            """,
        )

        assertTrue(DesktopSearchClient.parseLibraryItems(response).isEmpty())
    }

    private fun json(raw: String) = Json.parseToJsonElement(raw.trimIndent()) as JsonObject
}
