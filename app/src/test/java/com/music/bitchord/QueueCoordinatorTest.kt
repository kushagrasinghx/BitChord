package com.music.bitchord

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.queueEntryId
import com.music.bitchord.playback.queueTier
import com.music.bitchord.playback.toMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Proxy

class QueueCoordinatorTest {

    private fun testSong(
        id: String,
        tier: QueueTier = QueueTier.CONTEXT,
        entryId: String? = null,
        source: String? = null,
    ) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailUrl = null,
        queueTier = tier,
        queueEntryId = entryId,
        playbackSource = source,
    )

    private fun createFakePlayer(
        items: MutableList<MediaItem>,
        currentIndex: () -> Int = { 0 },
    ): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItemIndex" -> currentIndex()
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args[0] as Int]
                "getCurrentMediaItem" -> items.getOrNull(currentIndex())
                "removeMediaItem" -> {
                    val index = args[0] as Int
                    items.removeAt(index)
                    null
                }
                "removeMediaItems" -> {
                    val from = args[0] as Int
                    val to = args[1] as Int
                    for (i in (to - 1) downTo from) {
                        items.removeAt(i)
                    }
                    null
                }
                else -> null
            }
        } as Player
    }

    @Test
    fun `asQueueEntry generates unique immutable entryId if null`() {
        val song = testSong("song1")
        val entry = song.asQueueEntry(QueueTier.USER_QUEUE)

        assertNotNull(entry.queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, entry.queueTier)
    }

    @Test
    fun `asQueueEntry preserves existing entryId immutably`() {
        val song = testSong("song1", entryId = "entry-123")
        val entry = song.asQueueEntry(QueueTier.CONTEXT)

        assertEquals("entry-123", entry.queueEntryId)
        assertEquals(QueueTier.CONTEXT, entry.queueTier)
    }

    @Test
    fun `buildContextQueue preserves sequential order, user queue behind selected, and sets correct startIndex`() {
        // Current timeline: [Track 0 (playing)] + [User Q 1] + [User Q 2] + [Old Autoplay]
        val userQ1 = testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1")
        val userQ2 = testSong("u2", tier = QueueTier.USER_QUEUE, entryId = "entry-u2")
        val currentTimeline = listOf(
            testSong("now", tier = QueueTier.CONTEXT),
            userQ1,
            userQ2,
            testSong("old-auto", tier = QueueTier.AUTOPLAY),
        )

        val albumSongs = listOf(
            testSong("album-1"),
            testSong("album-2"),
            testSong("album-3"),
        )

        val result = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = albumSongs,
            selectedIndex = 1, // User tapped album-2 (middle track)
            contextSource = QueueSource("Abbey Road", PlaybackSourceType.BROWSE, "album-id"),
        )

        // Expected: [album-1] (preceding) + [album-2] (selected) + [userQ1, userQ2] + [album-3] (following)
        // startIndex = 1 (album-2)
        assertEquals(1, result.startIndex)
        assertEquals(5, result.timeline.size)

        assertEquals("album-1", result.timeline[0].videoId)
        assertEquals(QueueTier.CONTEXT, result.timeline[0].queueTier)
        assertEquals("Abbey Road", result.timeline[0].playbackSource)

        assertEquals("album-2", result.timeline[1].videoId)
        assertEquals(QueueTier.CONTEXT, result.timeline[1].queueTier)
        assertEquals("Abbey Road", result.timeline[1].playbackSource)

        assertEquals("u1", result.timeline[2].videoId)
        assertEquals("entry-u1", result.timeline[2].queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, result.timeline[2].queueTier)

        assertEquals("u2", result.timeline[3].videoId)
        assertEquals("entry-u2", result.timeline[3].queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, result.timeline[3].queueTier)

        assertEquals("album-3", result.timeline[4].videoId)
        assertEquals(QueueTier.CONTEXT, result.timeline[4].queueTier)
        assertEquals("Abbey Road", result.timeline[4].playbackSource)
    }

    @Test
    fun `buildContextQueue starting from first track has startIndex 0 and empty preceding context`() {
        val userQ = testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1")
        val currentTimeline = listOf(testSong("now"), userQ)
        val albumSongs = listOf(testSong("album-1"), testSong("album-2"), testSong("album-3"))

        val result = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = albumSongs,
            selectedIndex = 0,
            contextSource = QueueSource("Abbey Road", PlaybackSourceType.BROWSE, "album-id"),
        )

        assertEquals(0, result.startIndex)
        assertEquals(listOf("album-1", "u1", "album-2", "album-3"), result.timeline.map { it.videoId })
    }

    @Test
    fun `buildContextQueue starting from last track has startIndex at last context item and empty following context`() {
        val userQ = testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1")
        val currentTimeline = listOf(testSong("now"), userQ)
        val albumSongs = listOf(testSong("album-1"), testSong("album-2"), testSong("album-3"))

        val result = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = albumSongs,
            selectedIndex = 2, // Last track
            contextSource = QueueSource("Abbey Road", PlaybackSourceType.BROWSE, "album-id"),
        )

        assertEquals(2, result.startIndex)
        assertEquals(listOf("album-1", "album-2", "album-3", "u1"), result.timeline.map { it.videoId })
    }

    @Test
    fun `buildContextQueue with empty user queue preserves exact context order`() {
        val currentTimeline = listOf(testSong("now", tier = QueueTier.CONTEXT))
        val albumSongs = listOf(testSong("album-1"), testSong("album-2"), testSong("album-3"))

        val result = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = albumSongs,
            selectedIndex = 1,
            contextSource = QueueSource("Abbey Road", PlaybackSourceType.BROWSE, "album-id"),
        )

        assertEquals(1, result.startIndex)
        assertEquals(listOf("album-1", "album-2", "album-3"), result.timeline.map { it.videoId })
    }

    @Test
    fun `buildContextQueue handles empty context and out of bounds selectedIndex safely`() {
        val emptyResult = QueueCoordinator.buildContextQueue(
            currentTimeline = emptyList(),
            currentIndex = -1,
            newContextSongs = emptyList(),
            selectedIndex = 0,
            contextSource = QueueSource("Empty", PlaybackSourceType.BROWSE),
        )
        assertEquals(0, emptyResult.startIndex)
        assertEquals(emptyList<Song>(), emptyResult.timeline)

        val albumSongs = listOf(testSong("album-1"), testSong("album-2"))
        val underflow = QueueCoordinator.buildContextQueue(
            currentTimeline = emptyList(),
            currentIndex = -1,
            newContextSongs = albumSongs,
            selectedIndex = -10,
            contextSource = QueueSource("Test", PlaybackSourceType.BROWSE),
        )
        assertEquals(0, underflow.startIndex)
        assertEquals("album-1", underflow.timeline[0].videoId)

        val overflow = QueueCoordinator.buildContextQueue(
            currentTimeline = emptyList(),
            currentIndex = -1,
            newContextSongs = albumSongs,
            selectedIndex = 50,
            contextSource = QueueSource("Test", PlaybackSourceType.BROWSE),
        )
        assertEquals(1, overflow.startIndex)
        assertEquals("album-2", overflow.timeline[1].videoId)
    }

    @Test
    fun `buildOneOffQueue preserves upcoming user queue behind tapped song`() {
        val userQ = testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1")
        val currentTimeline = listOf(
            testSong("now", tier = QueueTier.CONTEXT),
            userQ,
            testSong("old-auto", tier = QueueTier.AUTOPLAY),
        )

        val searchHit = testSong("search-song")
        val result = QueueCoordinator.buildOneOffQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            tappedSong = searchHit,
            source = QueueSource("Search", PlaybackSourceType.SEARCH),
        )

        assertEquals(2, result.size)
        assertEquals("search-song", result[0].videoId)
        assertEquals(QueueTier.CONTEXT, result[0].queueTier)
        assertEquals("Search", result[0].playbackSource)

        assertEquals("u1", result[1].videoId)
        assertEquals("entry-u1", result[1].queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, result[1].queueTier)
    }

    @Test
    fun `findUserQueueInsertionIndex for playNext inserts at currentIndex + 1`() {
        val timeline = listOf(
            testSong("song0"),
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("c1", tier = QueueTier.CONTEXT),
        )

        val index = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = true)
        assertEquals(1, index)
    }

    @Test
    fun `findUserQueueInsertionIndex for addToQueue inserts after last user queue item`() {
        val timeline = listOf(
            testSong("song0"),
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("u2", tier = QueueTier.USER_QUEUE),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("a1", tier = QueueTier.AUTOPLAY),
        )

        val index = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = false)
        assertEquals(3, index) // Ahead of c1
    }

    @Test
    fun `findUserQueueInsertionIndex for addToQueue with no user queue inserts ahead of context`() {
        val timeline = listOf(
            testSong("song0"),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("c2", tier = QueueTier.CONTEXT),
        )

        val index = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = false)
        assertEquals(1, index) // Ahead of c1
    }

    @Test
    fun `clearUserQueue removes only USER_QUEUE items after currentIndex`() {
        val songs = listOf(
            testSong("playing", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("u2", tier = QueueTier.USER_QUEUE),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("a1", tier = QueueTier.AUTOPLAY),
        )
        val items = songs.map { it.toMediaItem() }.toMutableList()
        val player = createFakePlayer(items, currentIndex = { 0 })

        QueueCoordinator.clearUserQueue(player, tierAt(songs, items))

        assertEquals(3, items.size)
        assertEquals("playing", items[0].mediaId)
        assertEquals("c1", items[1].mediaId)
        assertEquals("a1", items[2].mediaId)
    }

    @Test
    fun `consumePlayedUserQueue prunes user queue items when entering context`() {
        var currentIndex = 2
        val songs = listOf(
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("u2", tier = QueueTier.USER_QUEUE),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("c2", tier = QueueTier.CONTEXT),
        )
        val items = songs.map { it.toMediaItem() }.toMutableList()
        val player = createFakePlayer(items, currentIndex = { currentIndex })

        QueueCoordinator.consumePlayedUserQueue(player, tierAt(songs, items))

        assertEquals(2, items.size)
        assertEquals("c1", items[0].mediaId)
        assertEquals("c2", items[1].mediaId)
    }

    @Test
    fun `buildJumpQueue to autoplay track preserves all future user queue items and promotes target to context`() {
        val timeline = listOf(
            testSong("now", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1"),
            testSong("u2", tier = QueueTier.USER_QUEUE, entryId = "entry-u2"),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("a1", tier = QueueTier.AUTOPLAY),
            testSong("a2", tier = QueueTier.AUTOPLAY, source = "Radio Seed"),
        )

        val result = QueueCoordinator.buildJumpQueue(timeline, currentIndex = 0, targetIndex = 5)
        assertNotNull(result)
        result!!

        // Expected: [a2 as CONTEXT] + [u1, u2]
        assertEquals(3, result.size)
        assertEquals("a2", result[0].videoId)
        assertEquals(QueueTier.CONTEXT, result[0].queueTier)
        assertEquals(PlaybackSourceType.QUEUE, result[0].playbackSourceType)

        assertEquals("u1", result[1].videoId)
        assertEquals(QueueTier.USER_QUEUE, result[1].queueTier)
        assertEquals("entry-u1", result[1].queueEntryId)

        assertEquals("u2", result[2].videoId)
        assertEquals(QueueTier.USER_QUEUE, result[2].queueTier)
        assertEquals("entry-u2", result[2].queueEntryId)
    }

    @Test
    fun `buildJumpQueue to context track keeps autoplay`() {
        val timeline = listOf(
            testSong("now", tier = QueueTier.CONTEXT),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("c2", tier = QueueTier.CONTEXT),
            testSong("a1", tier = QueueTier.AUTOPLAY),
            testSong("a2", tier = QueueTier.AUTOPLAY),
        )

        val result = QueueCoordinator.buildJumpQueue(timeline, currentIndex = 0, targetIndex = 1)!!

        assertEquals(listOf("c1", "c2", "a1", "a2"), result.map { it.videoId })
        assertEquals(QueueTier.AUTOPLAY, result[2].queueTier)
        assertEquals(QueueTier.AUTOPLAY, result[3].queueTier)
    }

    @Test
    fun `buildJumpQueue to a middle autoplay track keeps the autoplay after it`() {
        val timeline = listOf(
            testSong("now", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("a1", tier = QueueTier.AUTOPLAY),
            testSong("a2", tier = QueueTier.AUTOPLAY),
            testSong("a3", tier = QueueTier.AUTOPLAY),
        )

        val result = QueueCoordinator.buildJumpQueue(timeline, currentIndex = 0, targetIndex = 4)!!

        // a1 was skipped over and c1 was the old context; a3 is still to come.
        assertEquals(listOf("a2", "u1", "a3"), result.map { it.videoId })
        assertEquals(QueueTier.CONTEXT, result[0].queueTier)
        assertEquals(QueueTier.AUTOPLAY, result[2].queueTier)
    }

    @Test
    fun `buildJumpQueue to context track with interleaved user queue preserves all user queue items`() {
        val timeline = listOf(
            testSong("now", tier = QueueTier.CONTEXT),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1"),
            testSong("c2", tier = QueueTier.CONTEXT),
            testSong("u2", tier = QueueTier.USER_QUEUE, entryId = "entry-u2"),
            testSong("c3", tier = QueueTier.CONTEXT),
        )

        // User jumps to c2 (index 3)
        val result = QueueCoordinator.buildJumpQueue(timeline, currentIndex = 0, targetIndex = 3)
        assertNotNull(result)
        result!!

        // Expected: [c2] + [u1, u2] + [c3]
        assertEquals(4, result.size)
        assertEquals("c2", result[0].videoId)
        assertEquals(QueueTier.CONTEXT, result[0].queueTier)

        assertEquals("u1", result[1].videoId)
        assertEquals(QueueTier.USER_QUEUE, result[1].queueTier)

        assertEquals("u2", result[2].videoId)
        assertEquals(QueueTier.USER_QUEUE, result[2].queueTier)

        assertEquals("c3", result[3].videoId)
        assertEquals(QueueTier.CONTEXT, result[3].queueTier)
    }

    @Test
    fun `buildJumpQueue to user queue track drops skipped user queue items but retains remaining`() {
        val timeline = listOf(
            testSong("now", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("u2", tier = QueueTier.USER_QUEUE),
            testSong("u3", tier = QueueTier.USER_QUEUE),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("a1", tier = QueueTier.AUTOPLAY),
        )

        // User jumps to u2 (index 2), skipping u1
        val result = QueueCoordinator.buildJumpQueue(timeline, currentIndex = 0, targetIndex = 2)
        assertNotNull(result)
        result!!

        // Expected: [u2] + [u3] + [c1] + [a1] (u1 was skipped and dropped)
        assertEquals(4, result.size)
        assertEquals("u2", result[0].videoId)
        assertEquals(QueueTier.USER_QUEUE, result[0].queueTier)

        assertEquals("u3", result[1].videoId)
        assertEquals(QueueTier.USER_QUEUE, result[1].queueTier)

        assertEquals("c1", result[2].videoId)
        assertEquals(QueueTier.CONTEXT, result[2].queueTier)

        assertEquals("a1", result[3].videoId)
        assertEquals(QueueTier.AUTOPLAY, result[3].queueTier)
    }

    @Test
    fun `buildJumpQueue returns null for backward, same, or out-of-bounds jumps`() {
        val timeline = listOf(
            testSong("s0"),
            testSong("s1"),
            testSong("s2"),
        )

        // Same track
        assertEquals(null, QueueCoordinator.buildJumpQueue(timeline, currentIndex = 1, targetIndex = 1))

        // Backward jump
        assertEquals(null, QueueCoordinator.buildJumpQueue(timeline, currentIndex = 2, targetIndex = 1))

        // Out of bounds
        assertEquals(null, QueueCoordinator.buildJumpQueue(timeline, currentIndex = 0, targetIndex = 10))
        assertEquals(null, QueueCoordinator.buildJumpQueue(timeline, currentIndex = -1, targetIndex = 1))
        assertEquals(null, QueueCoordinator.buildJumpQueue(timeline, currentIndex = 0, targetIndex = -1))
    }

    @Test
    fun `jumpToQueueItem updates player retaining history and setting new upcoming items`() {
        var activeIndex = 0
        val songs = listOf(
            testSong("history0", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1"),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("a1", tier = QueueTier.AUTOPLAY),
        )
        val items = songs.map { it.toMediaItem() }.toMutableList()

        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItemIndex" -> activeIndex
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args[0] as Int]
                "getCurrentMediaItem" -> items.getOrNull(activeIndex)
                "setMediaItems" -> {
                    val newItems = args[0] as List<MediaItem>
                    val startIndex = args[1] as Int
                    items.clear()
                    items.addAll(newItems)
                    activeIndex = startIndex
                    null
                }
                "seekTo" -> {
                    activeIndex = args[0] as Int
                    null
                }
                "prepare" -> null
                "play" -> null
                else -> null
            }
        } as Player

        // Jump to a1 (index 3). The timeline is passed in because a MediaItem's
        // tier can't be read back on the JVM; the promotion of a1 to CONTEXT is
        // covered by the buildJumpQueue tests.
        QueueCoordinator.jumpToQueueItem(player, targetIndex = 3, cachedTimeline = songs)

        // Playlist should now be: [history0 (retained), a1 (now active), u1 (preserved)]
        assertEquals(3, items.size)
        assertEquals(1, activeIndex)
        assertEquals(listOf("history0", "a1", "u1"), items.map { it.mediaId })
    }

    /** Tiers looked up by song, since MediaItem metadata extras don't survive on the JVM. */
    private fun tierAt(songs: List<Song>, items: List<MediaItem>): (Int) -> QueueTier {
        val byId = songs.associate { it.videoId to it.queueTier }
        return { index -> byId.getValue(items[index].mediaId) }
    }
}
