package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMprisTest {
    private val song = Song(
        videoId = "track-1",
        title = "Test track",
        artist = "Test artist",
        thumbnailUrl = null,
    )

    @Test
    fun loadingFirstTrackPublishesPauseCapabilityOnlyOnce() {
        val tracker = MprisCapabilityTracker()
        val empty = DesktopPlaybackState()
        assertEquals(
            mapOf(
                "CanGoNext" to false,
                "CanGoPrevious" to false,
                "CanPlay" to false,
                "CanPause" to false,
                "CanSeek" to false,
            ),
            tracker.changed(empty, force = true).mapValues { it.value.value },
        )

        val playing = DesktopPlaybackState(song = song, isPlaying = true)
        val loadedChanges = tracker.changed(playing).mapValues { it.value.value }
        assertEquals(
            mapOf(
                "CanGoNext" to true,
                "CanGoPrevious" to true,
                "CanPlay" to true,
                "CanPause" to true,
            ),
            loadedChanges,
        )
        assertEquals(MprisCapabilities.from(playing).asProperties()["CanPause"]?.value, loadedChanges["CanPause"])
        assertEquals(emptyMap(), tracker.changed(playing))

        val paused = playing.copy(isPlaying = false)
        assertTrue(MprisCapabilities.from(paused).canPause)
        assertEquals(emptyMap(), tracker.changed(paused))

        val clearedChanges = tracker.changed(empty).mapValues { it.value.value }
        assertEquals(loadedChanges.mapValues { false }, clearedChanges)
        assertEquals(emptyMap(), tracker.changed(empty))
    }

    @Test
    fun durationAndErrorOnlyPublishCapabilitiesThatChanged() {
        val tracker = MprisCapabilityTracker()
        val playing = DesktopPlaybackState(song = song, isPlaying = true)
        tracker.changed(playing, force = true)

        val withDuration = playing.copy(durationMs = 120_000L)
        assertEquals(mapOf("CanSeek" to true), tracker.changed(withDuration).mapValues { it.value.value })

        val failed = withDuration.copy(isPlaying = false, error = "Playback failed")
        assertEquals(mapOf("CanPlay" to false), tracker.changed(failed).mapValues { it.value.value })
        assertEquals(emptyMap(), tracker.changed(failed))
    }
}
