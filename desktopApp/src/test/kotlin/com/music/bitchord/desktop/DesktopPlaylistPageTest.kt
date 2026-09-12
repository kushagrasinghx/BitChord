package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What is actually *in* a playlist, as opposed to what YouTube puts next to it. */
class DesktopPlaylistPageTest {

    @Test
    fun anEmptyPlaylistIsEmptyRatherThanItsSuggestions() {
        val page = playlistPage(rows = "", suggestions = SUGGESTED_ROW)

        assertTrue(DesktopSearchClient.parseBrowseSongs(page, null).isEmpty())
    }

    @Test
    fun onlyThePlaylistsOwnShelfCountsAsItsSongs() {
        val page = playlistPage(rows = PLAYLIST_ROW, suggestions = SUGGESTED_ROW)

        val songs = DesktopSearchClient.parseBrowseSongs(page, null)

        assertEquals(listOf("Tu Lazmi"), songs.map { it.title })
        // The row's own entry id, which is what a removal has to be expressed in — a suggestion has
        // none, which is the other half of telling them apart.
        assertEquals("SETabc", songs.single().setVideoId)
    }

    @Test
    fun theSuggestionShelfsRefreshTokenIsNotAPage() {
        // Both shelves carry a continuation.
        val page = playlistPage(
            rows = PLAYLIST_ROW,
            suggestions = SUGGESTED_ROW,
            playlistToken = "playlist-page-2",
            suggestionToken = "suggestions-refresh",
        )

        assertEquals("playlist-page-2", DesktopSearchClient.collectionContinuation(page))
    }

    @Test
    fun aPlaylistThatHasRunOutOfRowsHasNoToken() {
        val page = playlistPage(rows = PLAYLIST_ROW, suggestions = SUGGESTED_ROW, suggestionToken = "refresh")

        assertNull(DesktopSearchClient.collectionContinuation(page))
    }

    @Test
    fun anAlbumIsReadEvenThoughItIsServedLikeAPlaylist() {
        // The case that broke every album: an album page comes back under the same
        // `secondaryContents` a playlist does, but it carries no playlist shelf.
        val album = json(
            """
            {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":
            {"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{
                "playlistItemData":{"videoId":"albumTrack"},
                "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":
                  {"text":{"runs":[{"text":"Instant Crush"}]}}}]}}
            ]}}]}}}}}
            """,
        )

        assertEquals(listOf("Instant Crush"), DesktopSearchClient.parseBrowseSongs(album, null).map { it.title })
    }

    @Test
    fun anEmptiedPlaylistIsStillEmptyRatherThanItsSuggestions() {
        // The other side of the same coin, and why the two can be told apart at all: a playlist
        // with nothing left in it comes back with no playlist shelf either.
        val page = json(
            """
            {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":
            {"sectionListRenderer":{"contents":[{"musicShelfRenderer":{
              "title":{"runs":[{"text":"Suggestions"}]},
              "contents":[$SUGGESTED_ROW]}}]}}}}}
            """,
        )

        assertTrue(DesktopSearchClient.parseBrowseSongs(page, null).isEmpty())
    }

    private fun playlistPage(
        rows: String,
        suggestions: String,
        playlistToken: String? = null,
        suggestionToken: String? = null,
    ): JsonObject = json(
        """
        {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":
        {"sectionListRenderer":{"contents":[
          {"musicPlaylistShelfRenderer":{"contents":[
            $rows${continuationItem(playlistToken, leading = rows.isNotBlank())}
          ]}},
          {"musicShelfRenderer":{
            "title":{"runs":[{"text":"Suggestions"}]},
            "contents":[$suggestions${continuationItem(suggestionToken, leading = true)}]}}
        ]}}}}}
        """,
    )

    private fun continuationItem(token: String?, leading: Boolean) = token?.let {
        (if (leading) "," else "") +
            """{"continuationItemRenderer":{"continuationEndpoint":
               {"continuationCommand":{"token":"$it"}}}}"""
    }.orEmpty()

    private fun json(raw: String) = Json.parseToJsonElement(raw.trimIndent()) as JsonObject

    private companion object {
        const val PLAYLIST_ROW = """
            {"musicResponsiveListItemRenderer":{
              "playlistItemData":{"videoId":"vid1","playlistSetVideoId":"SETabc"},
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Tu Lazmi"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Ajey Kr"}]}}}]}}
        """

        const val SUGGESTED_ROW = """
            {"musicResponsiveListItemRenderer":{
              "playlistItemData":{"videoId":"vid2"},
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"MANWA LAAGE"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"VISHAL-SHEKHAR"}]}}}]}}
        """
    }
}
