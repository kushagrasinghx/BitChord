package com.music.bitchord.data.innertube

import com.music.bitchord.data.model.VersionTimeMap
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionPairTest {

    /** One queue row, typed as YouTube types it on the row's watch endpoint. */
    private fun panel(videoId: String, title: String, type: String, byline: String): String = """
        {
          "playlistPanelVideoRenderer": {
            "videoId": "$videoId",
            "title": { "runs": [ { "text": "$title" } ] },
            "longBylineText": { "runs": [ { "text": "$byline" } ] },
            "navigationEndpoint": { "watchEndpoint": {
              "videoId": "$videoId",
              "watchEndpointMusicSupportedConfigs": { "watchEndpointMusicConfig": { "musicVideoType": "$type" } }
            } }
          }
        }"""

    private val video = panel("omv1", "Song (Official Video)", "MUSIC_VIDEO_TYPE_OMV", "Artist • 10M views")
    private val audio = panel("atv1", "Song", "MUSIC_VIDEO_TYPE_ATV", "Artist • Album • 2024")

    // The video opens on 12s of dialogue, then plays the song through; the
    // numbers come as strings, the way protobuf's JSON writes an int64.
    private val segments = """
        "segmentMap": { "segment": [
          { "primaryVideoStartTimeMilliseconds": "12000",
            "counterpartVideoStartTimeMilliseconds": "0",
            "durationMilliseconds": "180000" }
        ] }"""

    private fun wrapper(primary: String, counterpart: String, segmentMap: String = segments) = Json.parseToJsonElement(
        """
        { "contents": [ { "playlistPanelVideoWrapperRenderer": {
            "primaryRenderer": $primary,
            "counterpart": [ { "counterpartRenderer": $counterpart${if (segmentMap.isBlank()) "" else ",$segmentMap"} } ]
        } } ] }""",
    )

    @Test
    fun videoAsPrimary_givesTheSongAndItsMap() {
        val pair = InnertubeParser.parseVersionPair(wrapper(video, audio), "omv1", wantAudio = true)!!

        assertEquals("atv1", pair.song.videoId)
        assertFalse(pair.song.isVideo)
        assertEquals(30_000L, pair.timeMap!!.map(42_000L))
    }

    @Test
    fun trackAsCounterpart_readsTheMapBackwards() {
        // Same pair, asked about from the video's side while YouTube put the
        // song first — the map has to come back the video's way round.
        val songFirst = Json.parseToJsonElement(
            """
            { "contents": [ { "playlistPanelVideoWrapperRenderer": {
                "primaryRenderer": $audio,
                "counterpart": [ { "counterpartRenderer": $video, "segmentMap": { "segment": [
                  { "primaryVideoStartTimeMilliseconds": 0,
                    "counterpartVideoStartTimeMilliseconds": 12000,
                    "durationMilliseconds": 180000 } ] } } ]
            } } ] }""",
        )
        val pair = InnertubeParser.parseVersionPair(songFirst, "omv1", wantAudio = true)!!

        assertEquals("atv1", pair.song.videoId)
        assertEquals(30_000L, pair.timeMap!!.map(42_000L))
    }

    @Test
    fun songToVideo() {
        val pair = InnertubeParser.parseVersionPair(wrapper(video, audio), "atv1", wantAudio = false)!!

        assertEquals("omv1", pair.song.videoId)
        assertTrue(pair.song.isVideo)
        assertEquals(42_000L, pair.timeMap!!.map(30_000L))
    }

    @Test
    fun wrongKindIsNotOffered() {
        // Already the song: there is no other song to switch to.
        assertNull(InnertubeParser.parseVersionPair(wrapper(audio, video), "atv1", wantAudio = true))
    }

    @Test
    fun pairWithoutSegmentsStillPairs() {
        val pair = InnertubeParser.parseVersionPair(wrapper(video, audio, segmentMap = ""), "omv1", wantAudio = true)!!

        assertEquals("atv1", pair.song.videoId)
        assertNull(pair.timeMap)
    }

    @Test
    fun unpairedQueueGivesNothing() {
        val plain = Json.parseToJsonElement("""{ "contents": [ $video ] }""")
        assertNull(InnertubeParser.parseVersionPair(plain, "omv1", wantAudio = true))
    }

    @Test
    fun timeMap_landsWhereTheMusicResumesOutsideAStretch() {
        val map = VersionTimeMap(
            listOf(
                VersionTimeMap.Segment(fromStartMs = 10_000, toStartMs = 0, durationMs = 60_000),
                // A 20s skit in the video between the two halves.
                VersionTimeMap.Segment(fromStartMs = 90_000, toStartMs = 60_000, durationMs = 60_000),
            ),
        )

        assertEquals(0L, map.map(3_000L))            // intro
        assertEquals(50_000L, map.map(60_000L))      // first half
        assertEquals(60_000L, map.map(80_000L))      // skit
        assertEquals(90_000L, map.map(120_000L))     // second half
        assertEquals(120_000L, map.map(200_000L))    // outro
        assertEquals(120_000L, map.inverted().map(90_000L))
    }
}
