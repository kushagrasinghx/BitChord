package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadedTracksTest {

    @AfterTest
    fun reset() = UploadedTracks.clear()

    /** One library-style track row; [extra] is spliced in as the row's own extra JSON members. */
    private fun row(videoId: String, extra: String = "", musicVideoType: String? = null): String {
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
            ]$extra
          }
        }"""
    }

    private fun parse(vararg rows: String) =
        InnertubeParser.collectSongsDeep(Json.parseToJsonElement("""{ "contents": [ ${rows.joinToString(",")} ] }"""))

    @Test
    fun registry_remembersAndForgets() {
        assertFalse(UploadedTracks.contains("abc"))
        UploadedTracks.mark("abc")
        assertTrue(UploadedTracks.contains("abc"))
        UploadedTracks.clear()
        assertFalse(UploadedTracks.contains("abc"))
    }

    @Test
    fun registry_ignoresBlankIds() {
        UploadedTracks.mark("")
        assertFalse(UploadedTracks.contains(""))
    }

    @Test
    fun deleteMenuMarksRowAsUpload() {
        val menu = """,
            "menu": { "menuRenderer": { "items": [ { "menuNavigationItemRenderer": {
              "navigationEndpoint": { "confirmDialogEndpoint": { "content": { "confirmDialogRenderer": {
                "confirmButton": { "buttonRenderer": { "command": {
                  "musicDeletePrivatelyOwnedEntityCommand": { "entityId": "e1" } } } } } } } } } } ] } }"""
        val songs = parse(row("up1", menu), row("pub1"))

        assertEquals(2, songs.size)
        assertTrue(UploadedTracks.contains("up1"))
        assertFalse(UploadedTracks.contains("pub1"))
    }

    @Test
    fun privatelyOwnedVideoTypeMarksRowAsUpload() {
        parse(
            row("up2", musicVideoType = "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"),
            row("pub2", musicVideoType = "MUSIC_VIDEO_TYPE_ATV"),
        )

        assertTrue(UploadedTracks.contains("up2"))
        assertFalse(UploadedTracks.contains("pub2"))
    }

    @Test
    fun ordinaryRowIsNotMarked() {
        val songs = parse(row("plain"))

        assertEquals(listOf("plain"), songs.map { it.videoId })
        assertFalse(UploadedTracks.contains("plain"))
    }
}
