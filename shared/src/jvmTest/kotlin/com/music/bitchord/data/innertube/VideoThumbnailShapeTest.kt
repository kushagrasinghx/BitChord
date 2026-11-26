package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoThumbnailShapeTest {

    /** One track row with a thumbnail of the given shape and, optionally, YouTube's own video type. */
    private fun row(
        videoId: String,
        musicVideoType: String?,
        width: Int,
        height: Int,
    ): String {
        val config = musicVideoType?.let {
            """,
            "watchEndpointMusicSupportedConfigs": {
              "watchEndpointMusicConfig": { "musicVideoType": "$it" }
            }"""
        }.orEmpty()
        return """
        {
          "musicResponsiveListItemRenderer": {
            "playlistItemData": { "videoId": "$videoId" },
            "thumbnail": {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://example.test/$videoId.jpg", "width": $width, "height": $height }
                  ]
                }
              }
            },
            "overlay": {
              "musicItemThumbnailOverlayRenderer": {
                "content": {
                  "musicPlayButtonRenderer": {
                    "playNavigationEndpoint": {
                      "watchEndpoint": { "videoId": "$videoId"$config }
                    }
                  }
                }
              }
            },
            "flexColumns": [
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "A title" } ] } } },
              { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "An artist" } ] } } }
            ]
          }
        }"""
    }

    private fun parse(vararg rows: String) =
        InnertubeParser.collectSongsDeep(Json.parseToJsonElement("""{ "contents": [ ${rows.joinToString(",")} ] }"""))

    @Test
    fun youtubeVideoTypeWinsOverThumbnailShape() {
        val songs = parse(
            row("omv", "MUSIC_VIDEO_TYPE_OMV", width = 400, height = 400),
            row("atv", "MUSIC_VIDEO_TYPE_ATV", width = 400, height = 225),
        )

        assertEquals(listOf("omv", "atv"), songs.map { it.videoId })
        assertTrue(songs[0].isVideo, "a square thumbnail must not turn a music video into a song")
        assertFalse(songs[1].isVideo, "a widescreen thumbnail must not turn a catalogue track into a video")
    }

    @Test
    fun missingVideoTypeFallsBackToThumbnailShape() {
        val songs = parse(
            row("square", null, width = 400, height = 400),
            row("wide", null, width = 400, height = 225),
        )

        assertFalse(songs[0].isVideo)
        assertTrue(songs[1].isVideo)
    }
}
