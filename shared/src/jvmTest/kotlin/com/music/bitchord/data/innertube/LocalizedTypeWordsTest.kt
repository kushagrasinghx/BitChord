package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * YouTube writes a page's kind ("Album", "Song") in the language the app asks
 * for, so a Spanish album header reads "Álbum • 2023". Shapes trimmed from
 * music.youtube.com's answer for A Great Chaos with `hl=es`.
 */
class LocalizedTypeWordsTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun row(videoId: String, title: String, plays: String) = """
        { "musicResponsiveListItemRenderer": {
            "playlistItemData": { "videoId": "$videoId" },
            "flexColumns": [
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                { "text": "$title", "navigationEndpoint": { "watchEndpoint": { "videoId": "$videoId" } } }
              ] } } },
              { "musicResponsiveListItemFlexColumnRenderer": { "text": {} } },
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "$plays" } ] } } }
            ]
        } }
    """

    private fun albumPage(kind: String) = json(
        """
        { "contents": { "twoColumnBrowseResultsRenderer": {
            "tabs": [ { "tabRenderer": { "content": { "sectionListRenderer": { "contents": [
              { "musicResponsiveHeaderRenderer": {
                "title": { "runs": [ { "text": "A Great Chaos" } ] },
                "subtitle": { "runs": [ { "text": "$kind" }, { "text": " • " }, { "text": "2023" } ] },
                "straplineTextOne": { "runs": [ { "text": "Ken Carson", "navigationEndpoint": { "browseEndpoint": {
                  "browseId": "UCtylTUUVIGY_i5afsQYeBZA",
                  "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
                    "pageType": "MUSIC_PAGE_TYPE_ARTIST" } } } } } ] }
              } }
            ] } } } } ],
            "secondaryContents": { "sectionListRenderer": { "contents": [
              { "musicShelfRenderer": { "contents": [
                ${row("AFTkc39TYiQ", "Green Room", "11 M reproducciones")},
                ${row("8Kx1b2yY3Ww", "Jennifer's Body", "9,1 M reproducciones")}
              ] } }
            ] } }
        } } }
        """,
    )

    @Test
    fun aSpanishAlbumsRowsTakeTheHeadersArtist() {
        val songs = InnertubeParser.collectSongsDeep(albumPage("Álbum"))
        assertEquals(listOf("Ken Carson", "Ken Carson"), songs.map { it.artist })
        assertEquals("UCtylTUUVIGY_i5afsQYeBZA", songs.first().artistId)
    }

    @Test
    fun releaseWordsAreReadInEveryAppLanguage() {
        // Album, single and EP as YouTube writes them for some of the app's languages.
        listOf("Albüm", "Альбом", "专辑", "Đĩa nhạc", "Singolo", "Сингл", "迷你专辑", "מיני-אלבום")
            .forEach { word ->
                val songs = InnertubeParser.collectSongsDeep(albumPage(word))
                assertEquals("Ken Carson", songs.first().artist, word)
            }
    }

    @Test
    fun typeWordNamesTheKindInAnyAppLanguage() {
        assertEquals("album", InnertubeParser.typeWord("Álbum"))
        assertEquals("song", InnertubeParser.typeWord("Canción"))
        assertEquals("playlist", InnertubeParser.typeWord("Lista de reproducción"))
        assertEquals("album", InnertubeParser.typeWord("एल्‍बम"))
        assertEquals("ep", InnertubeParser.typeWord("Đĩa nhạc mở rộng (EP)"))
        assertNull(InnertubeParser.typeWord("Ken Carson"))
    }

    @Test
    fun aSpanishCardsCreditSkipsItsKind() {
        assertEquals("Ken Carson", InnertubeParser.artistFromSubtitle("Canción • Ken Carson"))
    }
}
