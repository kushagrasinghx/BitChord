package com.music.bitchord.download

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastDownloadMetadataTest {

    @Test
    fun podcastMarkerSurvivesDownloadedMetadataPersistence() {
        val original = SavedSongMetadata(
            videoId = "episode-id",
            title = "Episode",
            artist = "Podcast show",
            uri = "file:///data/user/0/com.music.bitchord/files/episode.m4a",
            isPodcastEpisode = true,
        )

        val restored = Json.decodeFromString<SavedSongMetadata>(
            Json.encodeToString(original),
        )

        assertTrue(restored.isPodcastEpisode)
        assertTrue(restored.videoId == original.videoId)
    }

    @Test
    fun metadataWrittenBeforePodcastMarkerWasAddedRemainsReadable() {
        val legacy = """
            {
              "videoId": "old-episode",
              "title": "Old episode",
              "artist": "Podcast show",
              "uri": "file:///episode.m4a"
            }
        """.trimIndent()

        val restored = Json.decodeFromString<SavedSongMetadata>(legacy)

        assertFalse(restored.isPodcastEpisode)
    }
}
