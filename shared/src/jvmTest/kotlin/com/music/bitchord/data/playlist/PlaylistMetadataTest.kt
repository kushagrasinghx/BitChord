package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistMetadataTest {
    private fun song(id: String, duration: String = "3:00") =
        Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null, durationText = duration)

    @Test fun parsesTrackCountAndExactDuration() {
        assertEquals(PlaylistMetadata.Summary(1234, 8100L),
            PlaylistMetadata.fromHeader("1,234 songs • 2 hours 15 minutes"))
    }

    @Test fun skipsApproximateDurations() {
        assertEquals(null, PlaylistMetadata.fromHeader("100 songs • 6+ hours").durationSeconds)
    }

    @Test fun ignoresHoursOnlyDurationAsCoarseMetadata() {
        assertEquals(null, PlaylistMetadata.fromHeader("2 songs • 3 hours").durationSeconds)
        assertFalse(PlaylistMetadata.hasChanged(
            listOf(song("a", "3:00"), song("b", "4:00")), "2 songs • 3 hours"))
    }

    @Test fun acceptsHourAndMinuteAbbreviations() {
        assertEquals(PlaylistMetadata.Summary(2, 5400L),
            PlaylistMetadata.fromHeader("2 tracks • 1 hr 30 min"))
    }

    @Test fun countStillDetectsChangesWithCoarseDuration() {
        assertTrue(PlaylistMetadata.hasChanged(
            listOf(song("a")), "2 songs • 3 hours"))
    }

    @Test fun countChangeTriggersRefresh() {
        assertTrue(PlaylistMetadata.hasChanged(listOf(song("a"), song("b")), "3 songs"))
    }

    @Test fun sameCountAndApproximateDurationDoesNotTriggerRefresh() {
        assertFalse(PlaylistMetadata.hasChanged(listOf(song("a"), song("b")), "2 songs • 6+ hours"))
    }

    @Test fun durationChangeTriggersRefreshWhenCountMatches() {
        assertTrue(PlaylistMetadata.hasChanged(listOf(song("a"), song("b")), "2 songs • 15 minutes"))
    }

    @Test fun absentOrUnparseableMetadataNeverInvalidatesCache() {
        assertFalse(PlaylistMetadata.hasChanged(listOf(song("a")), null))
        assertFalse(PlaylistMetadata.hasChanged(listOf(song("a")), "Playlist by somebody"))
    }

    @Test fun roundingDifferenceDoesNotCauseUnnecessaryFullScan() {
        assertFalse(PlaylistMetadata.hasChanged(
            listOf(song("a", "3:23"), song("b", "2:45")), "2 songs • 6 minutes"))
    }
}
