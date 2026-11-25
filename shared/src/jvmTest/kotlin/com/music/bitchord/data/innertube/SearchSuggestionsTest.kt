package com.music.bitchord.data.innertube

import com.music.bitchord.data.model.SearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** Shapes trimmed from a `music/get_search_suggestions` response for "radiohead". */
class SearchSuggestionsTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun thumb(size: Int, height: Int = size) = """
        "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
          { "url": "https://lh3.googleusercontent.com/x=w$size-h$height", "width": $size, "height": $height }
        ] } } }"""

    private fun column(vararg runs: String) = """
        { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
          ${runs.joinToString(",") { """{ "text": "$it" }""" }}
        ] } } }"""

    private fun track(videoId: String, title: String, subtitle: String, width: Int = 120, height: Int = 120) = """
        { "musicResponsiveListItemRenderer": {
          ${thumb(width, height)},
          "flexColumns": [ ${column(title)}, ${column(subtitle)} ],
          "navigationEndpoint": { "watchEndpoint": { "videoId": "$videoId" } },
          "playlistItemData": { "videoId": "$videoId" }
        } }"""

    private val response = json(
        """
        { "contents": [
          { "searchSuggestionsSectionRenderer": { "contents": [
            { "searchSuggestionRenderer": {
              "suggestion": { "runs": [ { "text": "radiohead", "bold": true } ] },
              "navigationEndpoint": { "searchEndpoint": { "query": "radiohead" } }
            } },
            { "searchSuggestionRenderer": {
              "suggestion": { "runs": [ { "text": "radiohead", "bold": true }, { "text": " creep" } ] },
              "navigationEndpoint": { "searchEndpoint": { "query": "radiohead creep" } }
            } }
          ] } },
          { "searchSuggestionsSectionRenderer": { "contents": [
            { "musicResponsiveListItemRenderer": {
              ${thumb(120)},
              "flexColumns": [ ${column("Radiohead")}, ${column("206M monthly audience")} ],
              "navigationEndpoint": { "browseEndpoint": {
                "browseId": "UCr_iyUANcn9OX_yy9piYoLw",
                "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
                  "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                } }
              } }
            } },
            ${track("ZVgHPSyEIqk", "Let Down", "Song • Radiohead • 216M plays")},
            ${track("9RfVp-GhKfs", "Creep", "Song • Radiohead • 2.3B plays")},
            ${track("XFkzRNyygfk", "Creep (Official Video)", "Video • Radiohead • 1B views", 400, 225)}
          ] } }
        ] }
        """,
    )

    @Test
    fun queryStrings_comeFromTheFirstSectionOnly() {
        assertEquals(listOf("radiohead", "radiohead creep"), InnertubeParser.parseSearchSuggestions(response))
    }

    @Test
    fun entityRows_becomeLiveCards_inYouTubesOrder_withoutVideos() {
        val cards = InnertubeParser.parseSearchSuggestionEntities(response)
        assertEquals(3, cards.size)

        val artist = cards[0] as SearchResult.Browse
        assertEquals("UCr_iyUANcn9OX_yy9piYoLw", artist.item.browseId)
        assertEquals("Radiohead", artist.item.title)

        val songs = cards.drop(1).map { (it as SearchResult.Track).song }
        assertEquals(listOf("ZVgHPSyEIqk", "9RfVp-GhKfs"), songs.map { it.videoId })
        assertEquals(listOf("Radiohead", "Radiohead"), songs.map { it.artist })
    }
}
