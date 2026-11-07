package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The artist off a home shelf card, whatever language YouTube wrote the card in (#1283). */
class ShelfCardArtistTest {

    private fun card(vararg runs: Pair<String, String?>) = Json.parseToJsonElement(
        """
        { "musicTwoRowItemRenderer": {
          "title": { "runs": [ { "text": "Song title" } ] },
          "navigationEndpoint": { "watchEndpoint": { "videoId": "abcdefghijk" } },
          "subtitle": { "runs": [ ${runs.joinToString(",") { (text, page) -> run(text, page) }} ] }
        } }
        """,
    )

    private fun run(text: String, pageType: String?) = if (pageType == null) {
        """{ "text": "$text" }"""
    } else {
        """{ "text": "$text", "navigationEndpoint": { "browseEndpoint": { "browseId": "UC1",
            "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": { "pageType": "$pageType" } } } } }"""
    }

    private fun artistOf(vararg runs: Pair<String, String?>): String =
        InnertubeParser.artistOf(InnertubeParser.parseTwoRowItemsDeep(card(*runs)).single())

    @Test
    fun `a Russian Listen again card is credited to its artist, not to the word for song`() {
        assertEquals("Кино", artistOf("Композиция" to null, " • " to null, "Кино" to "MUSIC_PAGE_TYPE_ARTIST"))
    }

    @Test
    fun `an English card reads the same`() {
        assertEquals("Chelsea Wolfe", artistOf("Song" to null, " • " to null, "Chelsea Wolfe" to "MUSIC_PAGE_TYPE_ARTIST"))
    }

    @Test
    fun `a video upload is credited to its channel`() {
        assertEquals("Some Channel", artistOf("Some Channel" to "MUSIC_PAGE_TYPE_USER_CHANNEL", " • " to null, "5,4 млн просмотров" to null))
    }

    @Test
    fun `a card with no linked credit falls back to reading the words`() {
        assertEquals("Unlinked Artist", artistOf("Song" to null, " • " to null, "Unlinked Artist" to null))
    }
}
