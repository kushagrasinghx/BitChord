package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Explore is built from `FEmusic_moods_and_genres`, whose response is grids of navigation buttons
 * rather than the carousels every other browse page returns.
 */
class DesktopExploreTest {

    @Test
    fun moodAndGenreGridsBecomeCategorySections() {
        val response = Json.parseToJsonElement(
            """
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"sectionListRenderer":{"contents":[
              {"gridRenderer":{
                "header":{"gridHeaderRenderer":{"title":{"runs":[{"text":"Moods & moments"}]}}},
                "items":[
                  {"musicNavigationButtonRenderer":{
                    "buttonText":{"runs":[{"text":"Chill"}]},
                    "clickCommand":{"browseEndpoint":{"browseId":"FEmusic_moods_1","params":"chill=="}}}},
                  {"musicNavigationButtonRenderer":{
                    "buttonText":{"runs":[{"text":"Workout"}]},
                    "navigationEndpoint":{"browseEndpoint":{"browseId":"FEmusic_moods_2"}}}}
                ]}},
              {"gridRenderer":{
                "header":{"gridHeaderRenderer":{"title":{"runs":[{"text":"Genres"}]}}},
                "items":[
                  {"musicNavigationButtonRenderer":{
                    "buttonText":{"runs":[{"text":"Bengali"}]},
                    "clickCommand":{"browseEndpoint":{"browseId":"FEmusic_genre_1","params":"bn=="}}}}
                ]}}
            ]}}}}]}}}
            """.trimIndent(),
        ) as JsonObject

        val sections = DesktopSearchClient.parseMoodAndGenres(response)

        assertEquals(listOf("Moods & moments", "Genres"), sections.map { it.title })
        assertEquals(listOf("Chill", "Workout"), sections[0].items.map { it.title })
        assertEquals("FEmusic_moods_1", sections[0].items[0].browseId)
        assertEquals("chill==", sections[0].items[0].params)
        // A button stating its destination on navigationEndpoint rather than clickCommand is the
        // same button; params are simply absent there.
        assertEquals("FEmusic_moods_2", sections[0].items[1].browseId)
        assertEquals(null, sections[0].items[1].params)
        // Category buttons never carry artwork of their own — the grid resolves it afterwards from
        // the shelves each category opens.
        assertTrue(sections.flatMap { it.items }.all { it.thumbnailUrl == null })
    }

    @Test
    fun gridsWithoutATitleOrItemsAreSkipped() {
        val response = Json.parseToJsonElement(
            """{"contents":{"gridRenderer":{"header":{"gridHeaderRenderer":{"title":{"runs":[]}}},"items":[]}}}""",
        ) as JsonObject

        assertTrue(DesktopSearchClient.parseMoodAndGenres(response).isEmpty())
    }

    @Test
    fun buttonsWithoutABrowseDestinationAreDropped() {
        val response = Json.parseToJsonElement(
            """
            {"contents":{"gridRenderer":{
              "header":{"gridHeaderRenderer":{"title":{"runs":[{"text":"Moods"}]}}},
              "items":[
                {"musicNavigationButtonRenderer":{"buttonText":{"runs":[{"text":"No endpoint"}]}}},
                {"musicNavigationButtonRenderer":{
                  "buttonText":{"runs":[{"text":"Party"}]},
                  "clickCommand":{"browseEndpoint":{"browseId":"FEmusic_moods_3"}}}}
              ]}}}
            """.trimIndent(),
        ) as JsonObject

        val items = DesktopSearchClient.parseMoodAndGenres(response).single().items

        assertEquals(listOf("Party"), items.map { it.title })
    }
}

/** Listen Now and Explore are music surfaces. */
class DesktopShelfVideoFilterTest {

    @Test
    fun videoShelvesAndVideoRowsAreDropped() {
        val response = Json.parseToJsonElement(
            """
            {"contents":{"sectionListRenderer":{"contents":[
              {"musicShelfRenderer":{
                "title":{"runs":[{"text":"Quick picks"}]},
                "contents":[
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"song1"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"A Real Song"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song • An Artist"}]}}}
                    ]}},
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"video1"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"SONG (LYRICS) | ARTIST | FILM"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Video • Some Channel"}]}}}
                    ]}}
                ]}},
              {"musicCarouselShelfRenderer":{
                "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Music videos"}]}}},
                "contents":[
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"video2"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Another Upload"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song • An Artist"}]}}}
                    ]}}
                ]}}
            ]}}}
            """.trimIndent(),
        ) as JsonObject

        val shelves = DesktopSearchClient.parseShelves(response)

        // The video shelf is gone entirely, header and all.
        assertEquals(listOf("Quick picks"), shelves.map { it.title })
        // And the video row inside the musical shelf went with it.
        assertEquals(listOf("song1"), shelves.single().items.mapNotNull { it.videoId })
    }

    @Test
    fun aShelfOfOrdinaryRowsIsKeptWhole() {
        val response = Json.parseToJsonElement(
            """
            {"contents":{"musicShelfRenderer":{
              "title":{"runs":[{"text":"Listen again"}]},
              "contents":[
                {"musicResponsiveListItemRenderer":{
                  "playlistItemData":{"videoId":"a"},
                  "flexColumns":[
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"One"}]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song • Artist"}]}}}
                  ]}},
                {"musicResponsiveListItemRenderer":{
                  "playlistItemData":{"videoId":"b"},
                  "flexColumns":[
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Two"}]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song • Artist"}]}}}
                  ]}}
              ]}}}
            """.trimIndent(),
        ) as JsonObject

        val shelf = DesktopSearchClient.parseShelves(response).single()

        assertEquals("Listen again", shelf.title)
        assertEquals(listOf("a", "b"), shelf.items.mapNotNull { it.videoId })
    }
}

/**
 * The home feed answers with a couple of shelves and a token; the rest of the Play page is behind
 * that token.
 */
class DesktopHomeContinuationTest {

    @Test
    fun theModernContinuationTokenIsFound() {
        val response = Json.parseToJsonElement(
            """
            {"contents":{"sectionListRenderer":{"contents":[
              {"continuationItemRenderer":{
                "continuationEndpoint":{"continuationCommand":{"token":"NEXT_PAGE_TOKEN"}}}}
            ]}}}
            """.trimIndent(),
        ) as JsonObject

        assertEquals("NEXT_PAGE_TOKEN", DesktopSearchClient.continuationToken(response))
    }

    @Test
    fun theOlderContinuationShapeStillWorks() {
        val response = Json.parseToJsonElement(
            """{"continuations":[{"nextContinuationData":{"continuation":"OLD_TOKEN"}}]}""",
        ) as JsonObject

        assertEquals("OLD_TOKEN", DesktopSearchClient.continuationToken(response))
    }

    @Test
    fun aLastPageHasNoToken() {
        val response = Json.parseToJsonElement("""{"contents":{"sectionListRenderer":{"contents":[]}}}""") as JsonObject

        assertNull(DesktopSearchClient.continuationToken(response))
    }
}
