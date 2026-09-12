package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import com.music.bitchord.data.model.ShelfItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What may be edited on the account, and by whom. */
class DesktopPlaylistEditTest {

    @Test
    fun onlyRealPlaylistsAreOfferedToThePicker() {
        val items = listOf(
            shelfItem("Liked Music", "VLLM"),
            shelfItem("Episodes for Later", "VLSE"),
            shelfItem("My Supermix", "VLRDTMAK5uy_n"),
            shelfItem("Random Access Memories", "VLOLAK5uy_abc"),
            shelfItem("Discovery", "VLMPREb_xyz"),
            shelfItem("Late nights", "VLPLuserMade"),
            // An artist card on the same feed: no VL, so no edit is possible.
            shelfItem("Daft Punk", "UCabc"),
            ShelfItem("A track", "somebody", null, "videoId", null),
        )

        val editable = DesktopSearchClient.userPlaylists(items)

        assertEquals(listOf("Late nights"), editable.map { it.title })
        // The raw id, not the browse id: the edit endpoint refuses the prefix.
        assertEquals("PLuserMade", editable.single().playlistId)
        assertEquals("VLPLuserMade", editable.single().browseId)
    }

    @Test
    fun anEditableHeaderMeansTheAccountMadeIt() {
        val page = json(
            """
            {"header":{"musicEditablePlaylistDetailHeaderRenderer":{"header":
              {"musicDetailHeaderRenderer":{"title":{"runs":[{"text":"Late nights"}]}}}}}}
            """,
        )
        assertTrue(DesktopSearchClient.parsePlaylistOwned(page) == true)
    }

    @Test
    fun aDeleteOrEditInTheHeaderMenuMeansTheAccountMadeIt() {
        val page = json(
            """
            {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
            {"sectionListRenderer":{"contents":[{"musicResponsiveHeaderRenderer":{
              "title":{"runs":[{"text":"Late nights"}]},
              "menu":{"menuRenderer":{"items":[
                {"menuNavigationItemRenderer":{"icon":{"iconType":"DELETE"}}}
              ]}}}}]}}}}]}}}
            """,
        )
        assertTrue(DesktopSearchClient.parsePlaylistOwned(page) == true)
    }

    @Test
    fun aSaveToggleMeansTheAccountOnlySavedIt() {
        // Somebody else's playlist: the header offers to take it out of the library, which is the
        // one control an owner is never shown.
        val page = json(
            """
            {"contents":{"sectionListRenderer":{"contents":[{"musicResponsiveHeaderRenderer":{
              "title":{"runs":[{"text":"Somebody else's mix"}]},
              "buttons":[
                {"musicPlayButtonRenderer":{"playNavigationEndpoint":
                  {"watchEndpoint":{"playlistId":"PLother"}}}},
                {"toggleButtonRenderer":{
                  "defaultIcon":{"iconType":"BOOKMARK_BORDER"},
                  "toggledIcon":{"iconType":"BOOKMARK"}}}
              ]}}]}}}
            """,
        )
        assertFalse(DesktopSearchClient.parsePlaylistOwned(page) == true)
    }

    @Test
    fun aPageThatSaysNothingIsNotTreatedAsOwned() {
        // A continuation, or an album — nothing to read either way.
        assertNull(DesktopSearchClient.parsePlaylistOwned(json("""{"continuationContents":{}}""")))
    }

    @Test
    fun typeaheadReadsQueriesRatherThanTheirBoldFacedRuns() {
        val response = json(
            """
            {"contents":[{"searchSuggestionsSectionRenderer":{"contents":[
              {"searchSuggestionRenderer":{
                "suggestion":{"runs":[{"text":"star","bold":true},{"text":"boy"}]},
                "navigationEndpoint":{"searchEndpoint":{"query":"starboy"}}}},
              {"searchSuggestionRenderer":{
                "suggestion":{"runs":[{"text":"star"},{"text":"boy the weeknd"}]},
                "navigationEndpoint":{"searchEndpoint":{"query":"starboy the weeknd"}}}},
              {"searchSuggestionRenderer":{
                "suggestion":{"runs":[{"text":"starboy"}]},
                "navigationEndpoint":{"searchEndpoint":{"query":"starboy"}}}}
            ]}},
            {"searchSuggestionsSectionRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{
                "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":
                  {"text":{"runs":[{"text":"Starboy"}]}}}]}}
            ]}}]}
            """,
        )

        val terms = DesktopSearchClient.parseSearchSuggestions(response)

        // The endpoint's query, not the runs — which are split only so the typed prefix can be
        // bold-faced and carry no separator to rejoin on.
        assertEquals(listOf("starboy", "starboy the weeknd"), terms)
    }

    @Test
    fun onlyYouTubeTracksCanBeSentToTheAccount() {
        // Desktop plays from more than one place.
        assertTrue(DesktopSearchClient.isVideoId("VTMFhVAVIyY"))
        assertFalse(DesktopSearchClient.isVideoId("addon:643a786b-aada-45bc-b058-e793f14e35abtidal:177329043"))
        assertFalse(DesktopSearchClient.isVideoId("/home/listener/Music/track.flac"))
        assertFalse(DesktopSearchClient.isVideoId(""))
        // Eleven characters exactly — a longer or shorter run is not one.
        assertFalse(DesktopSearchClient.isVideoId("VTMFhVAVIy"))
        assertFalse(DesktopSearchClient.isVideoId("VTMFhVAVIyYY"))
    }

    private fun shelfItem(title: String, browseId: String) =
        ShelfItem(title, "Playlist", null, null, browseId)

    private fun json(raw: String) = Json.parseToJsonElement(raw.trimIndent()) as JsonObject
}
