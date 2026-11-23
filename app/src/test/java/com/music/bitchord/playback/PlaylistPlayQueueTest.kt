package com.music.bitchord.playback

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistPlayQueueTest {
    private val source = QueueSource("My Mix 1", PlaybackSourceType.BROWSE, "mix")
    private val tracks = listOf(song("mix-1"), song("mix-2"), song("mix-3"))
    private val oldQueuedTrack = song("old-queued", QueueTier.USER_QUEUE)

    @Test
    fun `playlist Play starts the bounded queue without old upcoming tracks`() {
        val context = ContextRegistry().register("mix-run", "mix", source.id, tracks, null)
        val plan = BoundedQueuePlanner.buildInitialPlan(
            contextSnapshot = context,
            selectedIndex = 0,
            queueWindow = QueueWindow(tracks.size).startAt(0),
            preservedUserQueue = listOf(oldQueuedTrack),
            replaceUpcomingQueue = true,
        )

        assertEquals(tracks.map(Song::videoId), plan.materializedSongs().map(Song::videoId))
    }

    @Test
    fun `playlist Play starts the flat queue without old upcoming tracks`() {
        val result = QueueTimeline.buildContextQueue(
            currentTimeline = listOf(song("old-current"), oldQueuedTrack, song("old-playlist")),
            currentIndex = 0,
            newContextSongs = tracks,
            selectedIndex = 0,
            contextSource = source,
            replaceUpcomingQueue = true,
        )

        assertEquals(0, result.startIndex)
        assertEquals(tracks.map(Song::videoId), result.timeline.map(Song::videoId))
    }

    private fun song(id: String, tier: QueueTier = QueueTier.CONTEXT) = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
        queueTier = tier,
    )
}
