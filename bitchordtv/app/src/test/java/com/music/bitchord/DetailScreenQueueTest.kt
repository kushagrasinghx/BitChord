package com.music.bitchord

import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.ui.screens.matching
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailScreenQueueTest {

    private fun testSong(
        id: String,
        title: String = "Title $id",
        artist: String = "Artist $id",
        album: String? = null,
        tier: QueueTier = QueueTier.CONTEXT,
        entryId: String? = null,
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        albumName = album,
        thumbnailUrl = null,
        queueTier = tier,
        queueEntryId = entryId,
    )

    @Test
    fun `unfiltered playlist preserves full playlist as context queue with correct startIndex`() {
        val playlistSongs = List(50) { i ->
            testSong(id = "track-$i", title = "Song $i", artist = "Artist $i")
        }

        // When query is blank, matching("") preserves all items and indices in order
        val matches = playlistSongs.matching("")
        assertEquals(50, matches.size)
        matches.forEachIndexed { idx, entry ->
            assertEquals(idx, entry.index)
            assertEquals(playlistSongs[idx], entry.value)
        }

        val queue = matches.map { it.value }
        assertEquals(playlistSongs, queue)

        // User taps track #20 (position = 20)
        val position = 20
        val selectedSong = queue[position]
        assertEquals("track-20", selectedSong.videoId)

        val userQueueSong = testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1")
        val currentTimeline = listOf(testSong("current"), userQueueSong)

        val result = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = queue,
            selectedIndex = position,
            contextSource = QueueSource("My Playlist", PlaybackSourceType.BROWSE, "playlist-123"),
        )

        // startIndex points to selectedSong (precedingContext.size = 20)
        assertEquals(20, result.startIndex)
        assertEquals("track-20", result.timeline[result.startIndex].videoId)

        // Preceding tracks 0..19 are in history
        for (i in 0 until 20) {
            assertEquals("track-$i", result.timeline[i].videoId)
        }

        // USER_QUEUE item is preserved immediately after the selected track
        assertEquals("u1", result.timeline[21].videoId)
        assertEquals(QueueTier.USER_QUEUE, result.timeline[21].queueTier)

        // Following tracks 21..49 follow the USER_QUEUE item
        for (i in 21 until 50) {
            assertEquals("track-$i", result.timeline[i + 1].videoId)
        }
    }

    @Test
    fun `filtered playlist search results become the context queue, not the unfiltered playlist`() {
        // Create 100 songs, with 3 matching "Taylor" at indices 11, 46, 82
        val playlistSongs = List(100) { i ->
            when (i) {
                11 -> testSong(id = "track-12", title = "Love Story", artist = "Taylor Swift")
                46 -> testSong(id = "track-47", title = "Blank Space", artist = "Taylor Swift")
                82 -> testSong(id = "track-83", title = "Cardigan", artist = "Taylor Swift")
                else -> testSong(id = "track-${i + 1}", title = "Other Song $i", artist = "Other Artist")
            }
        }

        val matches = playlistSongs.matching("Taylor")
        assertEquals(3, matches.size)

        // The original track positions are retained on entry.index for UI numbering
        assertEquals(11, matches[0].index)
        assertEquals(46, matches[1].index)
        assertEquals(82, matches[2].index)

        // queue is derived from matches
        val queue = matches.map { it.value }
        assertEquals(listOf("track-12", "track-47", "track-83"), queue.map { it.videoId })

        // User taps the 2nd search result (position = 1, "Blank Space" / track-47)
        val position = 1
        assertEquals("track-47", queue[position].videoId)

        val userQueueSong = testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1")
        val currentTimeline = listOf(testSong("current"), userQueueSong)

        val result = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = queue,
            selectedIndex = position,
            contextSource = QueueSource("My Playlist", PlaybackSourceType.BROWSE, "playlist-123"),
        )

        // Context contains ONLY the filtered search results, not track-48, track-49...
        // Expected timeline: [track-12] + [track-47] + [u1] + [track-83]
        assertEquals(1, result.startIndex)
        assertEquals(listOf("track-12", "track-47", "u1", "track-83"), result.timeline.map { it.videoId })
        assertEquals(QueueTier.CONTEXT, result.timeline[0].queueTier)
        assertEquals(QueueTier.CONTEXT, result.timeline[1].queueTier)
        assertEquals(QueueTier.USER_QUEUE, result.timeline[2].queueTier)
        assertEquals(QueueTier.CONTEXT, result.timeline[3].queueTier)
    }

    @Test
    fun `matching filters by title, artist, or albumName case-insensitively`() {
        val songs = listOf(
            testSong(id = "1", title = "Midnight City", artist = "M83", album = "Hurry Up"),
            testSong(id = "2", title = "City Lights", artist = "Various", album = "Soundtrack"),
            testSong(id = "3", title = "Something Else", artist = "City High", album = "Self-Titled"),
            testSong(id = "4", title = "Unrelated", artist = "Unknown", album = "Unknown"),
        )

        val matches = songs.matching("city")
        assertEquals(listOf("1", "2", "3"), matches.map { it.value.videoId })
    }

    @Test
    fun `tapping song in playlist while in party constructs single-song queue without surrounding context tracks and preserves upcoming manual party queue`() {
        val playlistSongs = List(50) { i ->
            testSong(id = "track-$i", title = "Song $i", artist = "Artist $i")
        }

        // Tapping track 20 in a 50-track playlist
        val selectedSong = playlistSongs[20]
        val source = QueueSource("My Playlist", PlaybackSourceType.BROWSE, "playlist-123")

        // Canonical party queue has two manual items upcoming
        val upcomingPartyTracks = listOf(
            PartyTrack(videoId = "party-manual-1", title = "Manual 1", artist = "Artist 1", fromAutoplay = false),
            PartyTrack(videoId = "party-manual-2", title = "Manual 2", artist = "Artist 2", fromAutoplay = false),
        )

        val timeline = QueueCoordinator.buildPartyPlaybackQueue(
            tappedSong = selectedSong,
            source = source,
            upcomingPartyTracks = upcomingPartyTracks,
        )

        // Resulting timeline must have ONLY: [selectedSong] + [party-manual-1] + [party-manual-2]
        // Surrounding playlist tracks (track-0..19, track-21..49) are NOT in the queue
        assertEquals(3, timeline.size)
        assertEquals("track-20", timeline[0].videoId)
        assertEquals(QueueTier.CONTEXT, timeline[0].queueTier)
        assertEquals("My Playlist", timeline[0].playbackSource)
        assertEquals(PlaybackSourceType.BROWSE, timeline[0].playbackSourceType)

        assertEquals("party-manual-1", timeline[1].videoId)
        assertEquals(QueueTier.USER_QUEUE, timeline[1].queueTier)

        assertEquals("party-manual-2", timeline[2].videoId)
        assertEquals(QueueTier.USER_QUEUE, timeline[2].queueTier)
    }

    @Test
    fun `tapping song in playlist while in party drops stale autoplay tracks from upcoming party tracks`() {
        val selectedSong = testSong(id = "new-track", title = "New Song", artist = "New Artist")
        val source = QueueSource("Album", PlaybackSourceType.BROWSE, "album-1")

        // Canonical party queue had 1 manual item and 2 autoplay items from the previous track
        val upcomingPartyTracks = listOf(
            PartyTrack(videoId = "manual-1", title = "Manual 1", fromAutoplay = false),
            PartyTrack(videoId = "stale-auto-1", title = "Auto 1", fromAutoplay = true),
            PartyTrack(videoId = "stale-auto-2", title = "Auto 2", fromAutoplay = true),
        )

        val timeline = QueueCoordinator.buildPartyPlaybackQueue(
            tappedSong = selectedSong,
            source = source,
            upcomingPartyTracks = upcomingPartyTracks,
        )

        // Resulting timeline must contain ONLY: [new-track] + [manual-1]. Stale autoplay items are pruned!
        assertEquals(listOf("new-track", "manual-1"), timeline.map { it.videoId })
        assertEquals(QueueTier.CONTEXT, timeline[0].queueTier)
        assertEquals(QueueTier.USER_QUEUE, timeline[1].queueTier)
    }

    @Test
    fun `bootstrap join with empty canonical party queue produces single song queue`() {
        val selectedSong = testSong(id = "first-track", title = "First Song", artist = "First Artist")
        val source = QueueSource("Search", PlaybackSourceType.SEARCH, null)

        val timeline = QueueCoordinator.buildPartyPlaybackQueue(
            tappedSong = selectedSong,
            source = source,
            upcomingPartyTracks = emptyList(), // Snapshot has not arrived yet or empty room
        )

        // Resulting timeline must be ONLY the tapped song
        assertEquals(1, timeline.size)
        assertEquals("first-track", timeline[0].videoId)
        assertEquals(QueueTier.CONTEXT, timeline[0].queueTier)
        assertEquals("Search", timeline[0].playbackSource)
    }
}
