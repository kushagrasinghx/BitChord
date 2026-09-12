package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An artist page, which is three different responses wearing one shape. */
class DesktopArtistTest {

    @Test
    fun theImmersiveHeaderCarriesThePictureTheNumbersAndTheButton() {
        val page = DesktopSearchClient.parseArtistPage(
            json(
                """
                {"header":{"musicImmersiveHeaderRenderer":{
                  "title":{"runs":[{"text":"Daft Punk"}]},
                  "description":{"runs":[{"text":"A French duo."}]},
                  "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                    {"url":"https://example.invalid/small.jpg","width":120,"height":120},
                    {"url":"https://example.invalid/large.jpg","width":1200,"height":1200}]}}},
                  "monthlyListenerCount":{"runs":[{"text":"31M monthly listeners"}]},
                  "subscriptionButton2":{"subscribeButtonRenderer":{
                    "subscribed":false,
                    "channelId":"UCdaft",
                    "subscriberCountWithSubscribeText":{"runs":[
                      {"text":"9.7M subscribers"},{"text":" • Subscribe"}]}}}}},
                "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                {"sectionListRenderer":{"contents":[]}}}}]}}}
                """,
            ),
        )

        assertEquals("Daft Punk", page.name)
        // The largest rendition, not the first.
        assertEquals("https://example.invalid/large.jpg", page.thumbnailUrl)
        assertEquals("A French duo.", page.description)
        // Only the first run: YouTube puts the word "Subscribe" in a second one, so joining them
        // reads "9.7M subscribers • Subscribe".
        assertEquals("9.7M subscribers", page.subscriberCountText)
        assertEquals("31M monthly listeners", page.monthlyListenerCount)
        assertEquals("UCdaft", page.subscription?.channelId)
        assertEquals(false, page.subscription?.subscribed)
    }

    @Test
    fun aVisualHeaderStillNamesTheArtistAndFindsTheirPicture() {
        // The other header shape: no counts, no subscribe button, and the picture in front of a
        // banner rather than as the header's thumbnail.
        val page = DesktopSearchClient.parseArtistPage(
            json(
                """
                {"header":{"musicVisualHeaderRenderer":{
                  "title":{"runs":[{"text":"Vilen"}]},
                  "foregroundThumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                    {"url":"https://example.invalid/vilen.jpg","width":540,"height":540}]}}}}},
                "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                {"sectionListRenderer":{"contents":[]}}}}]}}}
                """,
            ),
        )

        assertEquals("Vilen", page.name)
        assertEquals("https://example.invalid/vilen.jpg", page.thumbnailUrl)
        // Nothing to read, rather than a guess.
        assertNull(page.subscriberCountText)
        assertNull(page.monthlyListenerCount)
        assertNull(page.subscription)
    }

    @Test
    fun signedOutLeavesTheSubscribeButtonUnread() {
        // A guest's response carries the button as an invitation to sign in — no `subscribed` field
        // — and toggling it would be a write with nothing behind it.
        val page = DesktopSearchClient.parseArtistPage(
            json(
                """
                {"header":{"musicImmersiveHeaderRenderer":{
                  "title":{"runs":[{"text":"Daft Punk"}]},
                  "subscriptionButton":{"subscribeButtonRenderer":{
                    "channelId":"UCdaft",
                    "shortSubscriberCountText":{"runs":[{"text":"9.7M"}]}}}}},
                "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                {"sectionListRenderer":{"contents":[]}}}}]}}}
                """,
            ),
        )

        assertEquals("9.7M", page.subscriberCountText)
        assertNull(page.subscription)
    }

    @Test
    fun topSongsCarryTheHeadingsBrowseIdForTheRest() {
        val page = DesktopSearchClient.parseArtistPage(
            json(
                """
                {"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Daft Punk"}]}}},
                "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                {"sectionListRenderer":{"contents":[
                  {"musicShelfRenderer":{
                    "title":{"runs":[{"text":"Songs","navigationEndpoint":
                      {"browseEndpoint":{"browseId":"VLPLtopsongs"}}}]},
                    "contents":[
                      {"musicResponsiveListItemRenderer":{
                        "playlistItemData":{"videoId":"vid1"},
                        "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":
                          {"text":{"runs":[{"text":"Instant Crush"}]}}}]}}]}},
                  {"musicCarouselShelfRenderer":{
                    "header":{"musicCarouselShelfBasicHeaderRenderer":
                      {"title":{"runs":[{"text":"Albums"}]}}},
                    "contents":[
                      {"musicTwoRowItemRenderer":{
                        "title":{"runs":[{"text":"Random Access Memories"}]},
                        "subtitle":{"runs":[{"text":"Album"}]},
                        "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_ram"}}}}]}},
                  {"musicCarouselShelfRenderer":{
                    "header":{"musicCarouselShelfBasicHeaderRenderer":
                      {"title":{"runs":[{"text":"Music videos"}]}}},
                    "contents":[
                      {"musicTwoRowItemRenderer":{
                        "title":{"runs":[{"text":"Get Lucky"}]},
                        "subtitle":{"runs":[{"text":"4.2B views"}]},
                        "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_gl"}}}}]}}
                ]}}}}]}}}
                """,
            ),
        )

        assertEquals(listOf("Instant Crush"), page.songs.map { it.title })
        // The landing page lists about five songs; this is the playlist with the rest, and it is
        // read off the shelf's own heading.
        assertEquals("VLPLtopsongs", page.moreSongsBrowseId)
        // A shelf of music videos is a dead end here as on the home feed — the rows lead to uploads
        // rather than to releases.
        assertEquals(listOf("Albums"), page.sections.map { it.title })
        assertEquals(listOf("Random Access Memories"), page.sections.single().items.map { it.title })
    }

    @Test
    fun aCarouselOfCardsThatLeadNowhereIsDropped() {
        val page = DesktopSearchClient.parseArtistPage(
            json(
                """
                {"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Someone"}]}}},
                "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
                {"sectionListRenderer":{"contents":[
                  {"musicCarouselShelfRenderer":{
                    "header":{"musicCarouselShelfBasicHeaderRenderer":
                      {"title":{"runs":[{"text":"Featured on"}]}}},
                    "contents":[
                      {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"No endpoint"}]}}}]}}
                ]}}}}]}}}
                """,
            ),
        )

        assertTrue(page.sections.isEmpty())
    }

    private fun json(raw: String) = Json.parseToJsonElement(raw.trimIndent()) as JsonObject
}
