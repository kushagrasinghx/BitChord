package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Shapes trimmed from YouTube Music's own responses for a podcast show (`MPSPP…`). */
class PodcastTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun episode(videoId: String, title: String, runtime: String, extra: String = "") = """
        {
          "musicMultiRowListItemRenderer": {
            "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
              { "url": "https://i.ytimg.com/vi/$videoId/hq720.jpg", "width": 853, "height": 479 }
            ] } } },
            "onTap": { "watchEndpoint": {
              "videoId": "$videoId",
              "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" }
              }
            } },
            "subtitle": { "runs": [ { "text": "128K views" }, { "text": " • " }, { "text": "2d ago" } ] },
            "playbackProgress": { "musicPlaybackProgressRenderer": {
              "durationText": { "runs": [ { "text": " • " }, { "text": "$runtime" } ] }
            } },
            "title": { "runs": [ { "text": "$title" } ] }$extra
          }
        }"""

    private val showPage = json(
        """
        {
          "contents": { "twoColumnBrowseResultsRenderer": {
            "tabs": [ { "tabRenderer": { "content": { "sectionListRenderer": { "contents": [
              { "musicResponsiveHeaderRenderer": {
                "title": { "runs": [ { "text": "Huberman Lab" } ] },
                "subtitle": {},
                "straplineTextOne": { "runs": [ { "text": "Andrew Huberman" } ] }
              } }
            ] } } } } ],
            "secondaryContents": { "sectionListRenderer": { "contents": [
              { "musicShelfRenderer": {
                "contents": [
                  ${episode("NlVu0P1RdHs", "Ross Edgley", "2 hr 43 min")},
                  ${episode("eYSOabR3YP0", "Essentials: Supplementation", "34 min")}
                ],
                "continuations": [ { "nextContinuationData": { "continuation": "PAGE2" } } ]
              } }
            ] } }
          } }
        }
        """,
    )

    @Test
    fun showPage_listsItsEpisodesAsPlayableTracks() {
        val show = InnertubeParser.podcastShow(showPage)
        assertEquals("Huberman Lab", show)

        val songs = InnertubeParser.collectSongsDeep(showPage, show)
        assertEquals(listOf("NlVu0P1RdHs", "eYSOabR3YP0"), songs.map { it.videoId })
        assertEquals(listOf("Huberman Lab", "Huberman Lab"), songs.map { it.artist })
        assertEquals(listOf("2:43:00", "34:00"), songs.map { it.durationText })
        // Widescreen art, but an episode is listened to.
        assertFalse(songs.any { it.isVideo })
        assertEquals("PAGE2", InnertubeParser.continuationToken(showPage))
    }

    @Test
    fun continuation_hasNoHeaderSoTheShowIsCarriedIn() {
        val page = json(
            """
            { "continuationContents": { "musicShelfContinuation": { "contents": [
              ${episode("P-h5WSQG1Sw", "Emotions", "2 hr 9 min")}
            ] } } }
            """,
        )
        assertNull(InnertubeParser.podcastShow(page))
        assertNull(InnertubeParser.parsePlaylistShelf(page))
        val song = InnertubeParser.collectSongsDeep(page, "Huberman Lab").single()
        assertEquals("Huberman Lab", song.artist)
        assertEquals("2:09:00", song.durationText)
    }

    @Test
    fun episodeAwayFromItsShow_namesTheShowItself() {
        val shelf = json(
            """{ "contents": [ ${episode(
                "TPXdcOJRmRc", "World News Tonight", "20 min",
                """, "secondTitle": { "runs": [ { "text": "ABC World News Tonight" } ] }""",
            )} ] }""",
        )
        assertEquals("ABC World News Tonight", InnertubeParser.collectSongsDeep(shelf, "Ignored").single().artist)
    }

    @Test
    fun searchEpisode_isCreditedToItsShowAndKeptOnTheAllTab() {
        val response = json(
            """
            { "contents": [ { "musicResponsiveListItemRenderer": {
              "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
                { "url": "https://i.ytimg.com/vi/Y566_T-YlNQ/hqdefault.jpg", "width": 400, "height": 225 }
              ] } } },
              "overlay": { "musicItemThumbnailOverlayRenderer": { "content": { "musicPlayButtonRenderer": {
                "playNavigationEndpoint": { "watchEndpoint": {
                  "videoId": "Y566_T-YlNQ",
                  "watchEndpointMusicSupportedConfigs": {
                    "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" }
                  }
                } }
              } } } },
              "flexColumns": [
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Become Mentally Dangerous" } ] } } },
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                  { "text": "Episode" }, { "text": " • " }, { "text": "Sep 1" }, { "text": " • " },
                  { "text": "Figuring Out With Raj Shamani", "navigationEndpoint": { "browseEndpoint": {
                    "browseId": "MPSPPLE0Jo6NF_JYO5-phess8GKafKMtPv3tfZ",
                    "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
                      "pageType": "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
                    } }
                  } } }
                ] } } }
              ]
            } } ] }
            """,
        )
        val song = InnertubeParser.parseSearchSongs(response).single()
        assertEquals("Y566_T-YlNQ", song.videoId)
        assertEquals("Figuring Out With Raj Shamani", song.artist)
        assertFalse(song.isVideo)
    }
}
