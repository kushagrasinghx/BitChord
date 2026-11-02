package com.music.bitchord

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.QueueTimeline
import com.music.bitchord.playback.queueEntryId
import com.music.bitchord.playback.queueTier
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.canonicalIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
        onRemoval: () -> Unit = {},
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
                    onRemoval()
                    val index = args[0] as Int
                    items.removeAt(index)
                    null
                }
                "removeMediaItems" -> {
                    onRemoval()
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
    fun `clearUserQueue edits the queue once per contiguous run`() {
        val songs = listOf(
            testSong("playing", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("u2", tier = QueueTier.USER_QUEUE),
            testSong("u3", tier = QueueTier.USER_QUEUE),
            testSong("c1", tier = QueueTier.CONTEXT),
            testSong("u4", tier = QueueTier.USER_QUEUE),
            testSong("u5", tier = QueueTier.USER_QUEUE),
            testSong("a1", tier = QueueTier.AUTOPLAY),
            testSong("u6", tier = QueueTier.USER_QUEUE),
        )
        val items = songs.map { it.toMediaItem() }.toMutableList()
        var edits = 0
        val player = createFakePlayer(items, currentIndex = { 0 }, onRemoval = { edits++ })

        QueueCoordinator.clearUserQueue(player, tierAt(songs, items))

        assertEquals(3, edits)
        assertEquals(listOf("playing", "c1", "a1"), items.map { it.mediaId })
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
                "replaceMediaItems" -> {
                    val from = args[0] as Int
                    val to = args[1] as Int
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[2] as List<MediaItem>
                    repeat(to - from) { items.removeAt(from) }
                    items.addAll(from, newItems)
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

    @Test
    fun `buildContextQueue produces identical queue semantics for Album and Playlist`() {
        val userQ = testSong("u1", tier = QueueTier.USER_QUEUE, entryId = "entry-u1")
        val currentTimeline = listOf(testSong("current", tier = QueueTier.CONTEXT), userQ)

        val trackSet = listOf(
            testSong("track-1"),
            testSong("track-2"),
            testSong("track-3"),
            testSong("track-4"),
            testSong("track-5"),
        )

        val albumSource = QueueSource("Abbey Road", PlaybackSourceType.BROWSE, "album-123")
        val playlistSource = QueueSource("Classic Rock", PlaybackSourceType.BROWSE, "playlist-456")

        val albumResult = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = trackSet,
            selectedIndex = 2, // Track 3 tapped
            contextSource = albumSource,
        )

        val playlistResult = QueueCoordinator.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = trackSet,
            selectedIndex = 2, // Track 3 tapped
            contextSource = playlistSource,
        )

        // Queue semantics must be identical
        assertEquals(albumResult.startIndex, playlistResult.startIndex)
        assertEquals(albumResult.timeline.size, playlistResult.timeline.size)
        assertEquals(
            albumResult.timeline.map { it.videoId },
            playlistResult.timeline.map { it.videoId },
        )
        assertEquals(
            albumResult.timeline.map { it.queueTier },
            playlistResult.timeline.map { it.queueTier },
        )

        // Verify entry identity semantics
        albumResult.timeline.forEach { assertNotNull(it.queueEntryId) }
        playlistResult.timeline.forEach { assertNotNull(it.queueEntryId) }

        // Source-specific fields reflect their respective collection sources
        assertEquals(List(5) { "Abbey Road" }, albumResult.timeline.filter { it.queueTier == QueueTier.CONTEXT }.map { it.playbackSource })
        assertEquals(List(5) { "Classic Rock" }, playlistResult.timeline.filter { it.queueTier == QueueTier.CONTEXT }.map { it.playbackSource })
    }

    @Test
    fun `shuffledStartingOrder produces identical queue semantics for Album and Playlist`() {
        val tracks = listOf(
            testSong("t1", tier = QueueTier.CONTEXT),
            testSong("t2", tier = QueueTier.CONTEXT),
            testSong("t3", tier = QueueTier.CONTEXT),
            testSong("u1", tier = QueueTier.USER_QUEUE),
            testSong("a1", tier = QueueTier.AUTOPLAY),
        )

        val albumTimeline = tracks.map { it.copy(playbackSource = "Album A", playbackSourceId = "alb-1") }
        val playlistTimeline = tracks.map { it.copy(playbackSource = "Playlist B", playbackSourceId = "pl-2") }

        val albumShuffled = QueueTimeline.shuffledStartingOrder(albumTimeline, startIndex = 1)
        val playlistShuffled = QueueTimeline.shuffledStartingOrder(playlistTimeline, startIndex = 1)

        // Selected track (t2) leads at index 0 for both
        assertEquals("t2", albumShuffled[0].videoId)
        assertEquals("t2", playlistShuffled[0].videoId)

        // User queue item pinned at index 1 for both
        assertEquals("u1", albumShuffled[1].videoId)
        assertEquals("u1", playlistShuffled[1].videoId)

        // Context items present in indices 2..3 for both
        assertEquals(setOf("t1", "t3"), albumShuffled.subList(2, 4).map { it.videoId }.toSet())
        assertEquals(setOf("t1", "t3"), playlistShuffled.subList(2, 4).map { it.videoId }.toSet())

        // Autoplay at index 4 for both
        assertEquals("a1", albumShuffled[4].videoId)
        assertEquals("a1", playlistShuffled[4].videoId)

        // Size and tiers match identically
        assertEquals(albumShuffled.map { it.queueTier }, playlistShuffled.map { it.queueTier })
    }

    @Test
    fun `mid-playback shuffle restoration behaves identically for Album and Playlist`() {
        val originalIds = listOf("track-1", "track-2", "track-3", "track-4", "track-5")
        // Playing at track-2, remaining upcoming unplayed tracks are track-3, track-4, track-5
        // Shuffled order of upcoming:
        val upcomingShuffled = listOf("track-5", "track-3", "track-4")

        val albumRestoredIndices = QueueShuffle.restoreOrder(upcomingShuffled, originalIds, "track-2")
        val playlistRestoredIndices = QueueShuffle.restoreOrder(upcomingShuffled, originalIds, "track-2")

        // Both restore to the exact same relative indices [1, 2, 0] corresponding to track-3, track-4, track-5
        assertEquals(albumRestoredIndices, playlistRestoredIndices)
        val restoredIds = albumRestoredIndices.map { upcomingShuffled[it] }
        assertEquals(listOf("track-3", "track-4", "track-5"), restoredIds)
    }

    @Test
    fun `duplicate track restore resolves identically regardless of collection source`() {
        // Deluxe album with reprise or playlist with duplicate track
        val originalWithDuplicates = listOf("song-A", "song-B", "song-A", "song-C")
        val upcomingShuffled = listOf("song-C", "song-A", "song-B", "song-A")

        val restored = QueueShuffle.restoreOrder(upcomingShuffled, originalWithDuplicates)
        val restoredIds = restored.map { upcomingShuffled[it] }

        // FIFO resolution restores first song-A, then song-B, second song-A, then song-C
        assertEquals(listOf("song-A", "song-B", "song-A", "song-C"), restoredIds)
    }

    @Test
    fun `album and playlist context queues maintain identical shuffle session invariants`() {
        val tracks = listOf(
            testSong("song-A", tier = QueueTier.CONTEXT, entryId = "e-A1"),
            testSong("song-B", tier = QueueTier.CONTEXT, entryId = "e-B"),
            testSong("song-A", tier = QueueTier.CONTEXT, entryId = "e-A2"), // duplicate
            testSong("song-C", tier = QueueTier.CONTEXT, entryId = "e-C"),
        )
        val albumResult = QueueCoordinator.buildContextQueue(
            currentTimeline = emptyList(),
            currentIndex = 0,
            newContextSongs = tracks,
            selectedIndex = 0,
            contextSource = QueueSource("Album Deluxe", PlaybackSourceType.BROWSE, "alb-1"),
        )
        val playlistResult = QueueCoordinator.buildContextQueue(
            currentTimeline = emptyList(),
            currentIndex = 0,
            newContextSongs = tracks,
            selectedIndex = 0,
            contextSource = QueueSource("My Playlist", PlaybackSourceType.QUEUE, "pl-1"),
        )

        // Structure matches identically
        assertEquals(albumResult.startIndex, playlistResult.startIndex)
        assertEquals(albumResult.timeline.size, playlistResult.timeline.size)
        assertEquals(albumResult.timeline.map { it.videoId }, playlistResult.timeline.map { it.videoId })
        assertEquals(albumResult.timeline.map { it.queueTier }, playlistResult.timeline.map { it.queueTier })

        // Both duplicate occurrences retain distinct queueEntryIds
        assertNotEquals(albumResult.timeline[0].queueEntryId, albumResult.timeline[2].queueEntryId)
        assertNotEquals(playlistResult.timeline[0].queueEntryId, playlistResult.timeline[2].queueEntryId)
    }

    @Before
    fun setUp() {
        val editor = Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java),
        ) { proxy, method, _ ->
            if (method.returnType == android.content.SharedPreferences.Editor::class.java) proxy else null
        }
        val prefs = Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "edit") editor else null
        } as android.content.SharedPreferences
        val field = com.music.bitchord.data.settings.AppSettings::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(com.music.bitchord.data.settings.AppSettings, prefs)

        QueueShuffle.setEnabled(false)
        QueueShuffle.canonicalContext = emptyList()
    }

    private class TestPlayer(
        val items: MutableList<MediaItem>,
        var activeIndex: Int = 0,
        var repeatMode: Int = Player.REPEAT_MODE_OFF,
    ) {
        var seekCallCount = 0
        /** setMediaItems or replaceMediaItems: any edit to the queue's contents. */
        var queueEditCallCount = 0

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItemIndex" -> activeIndex
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args[0] as Int]
                "getCurrentMediaItem" -> items.getOrNull(activeIndex)
                "getCurrentPosition" -> 0L
                "getDuration" -> 180000L
                "isPlaying" -> false
                "getPlaybackState" -> Player.STATE_READY
                "hasPreviousMediaItem" -> activeIndex > 0
                "hasNextMediaItem" -> activeIndex < items.size - 1 || repeatMode == Player.REPEAT_MODE_ALL
                "seekToPreviousMediaItem" -> {
                    if (activeIndex > 0) activeIndex--
                    null
                }
                "seekToNextMediaItem" -> {
                    if (activeIndex < items.size - 1) {
                        activeIndex++
                    } else if (repeatMode == Player.REPEAT_MODE_ALL && items.isNotEmpty()) {
                        activeIndex = 0
                    }
                    null
                }
                "getRepeatMode" -> repeatMode
                "setRepeatMode" -> {
                    repeatMode = args[0] as Int
                    null
                }
                "replaceMediaItems" -> {
                    val from = args[0] as Int
                    val to = args[1] as Int
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[2] as List<MediaItem>
                    queueEditCallCount++
                    for (i in (to - 1) downTo from) {
                        items.removeAt(i)
                    }
                    items.addAll(from, newItems)
                    if (to <= activeIndex) {
                        activeIndex += newItems.size - (to - from)
                    }
                    null
                }
                "setMediaItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[0] as List<MediaItem>
                    val startIndex = args[1] as Int
                    queueEditCallCount++
                    items.clear()
                    items.addAll(newItems)
                    activeIndex = startIndex
                    null
                }
                "seekTo" -> {
                    seekCallCount++
                    activeIndex = args[0] as Int
                    null
                }
                "removeMediaItem" -> {
                    val index = args[0] as Int
                    items.removeAt(index)
                    if (activeIndex > index) activeIndex--
                    null
                }
                "prepare" -> null
                "play" -> null
                else -> null
            }
        } as Player
    }

    @Test
    fun `jumpToQueueItem to final context track seeks directly preserving complete collection in timeline`() {
        val songs = (0..4).map { testSong("c$it", tier = QueueTier.CONTEXT, entryId = "entry-c$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        val testPlayer = TestPlayer(items, activeIndex = 0)

        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4, cachedTimeline = songs)

        assertEquals(1, testPlayer.seekCallCount)
        assertEquals(0, testPlayer.queueEditCallCount)
        assertEquals(4, testPlayer.activeIndex)
        assertEquals(5, testPlayer.items.size)
        assertEquals(listOf("c0", "c1", "c2", "c3", "c4"), testPlayer.items.map { it.mediaId })
        val history = (0 until testPlayer.activeIndex).map { testPlayer.items[it].mediaId }
        val upcoming = (testPlayer.activeIndex + 1 until testPlayer.items.size).map { testPlayer.items[it].mediaId }
        assertEquals(listOf("c0", "c1", "c2", "c3"), history)
        assertEquals(emptyList<String>(), upcoming)
    }

    @Test
    fun `jumpToQueueItem to middle context track seeks directly preserving history and upcoming context`() {
        val songs = (0..4).map { testSong("c$it", tier = QueueTier.CONTEXT, entryId = "entry-c$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        val testPlayer = TestPlayer(items, activeIndex = 0)

        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 2, cachedTimeline = songs)

        assertEquals(1, testPlayer.seekCallCount)
        assertEquals(0, testPlayer.queueEditCallCount)
        assertEquals(2, testPlayer.activeIndex)
        assertEquals(5, testPlayer.items.size)
        assertEquals(listOf("c0", "c1", "c2", "c3", "c4"), testPlayer.items.map { it.mediaId })
        val history = (0 until testPlayer.activeIndex).map { testPlayer.items[it].mediaId }
        val upcoming = (testPlayer.activeIndex + 1 until testPlayer.items.size).map { testPlayer.items[it].mediaId }
        assertEquals(listOf("c0", "c1"), history)
        assertEquals(listOf("c3", "c4"), upcoming)
    }

    @Test
    fun `jumpToQueueItem backward seeks directly within history without modifying timeline`() {
        val songs = (0..4).map { testSong("c$it", tier = QueueTier.CONTEXT, entryId = "entry-c$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        val testPlayer = TestPlayer(items, activeIndex = 3)

        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 1, cachedTimeline = songs)

        assertEquals(1, testPlayer.seekCallCount)
        assertEquals(0, testPlayer.queueEditCallCount)
        assertEquals(1, testPlayer.activeIndex)
        assertEquals(5, testPlayer.items.size)
        assertEquals(listOf("c0", "c1", "c2", "c3", "c4"), testPlayer.items.map { it.mediaId })
    }

    @Test
    fun `jumpToQueueItem to context track crossing user queue reorders user queue after target and preserves preceding context in history (Timelines A, B, C, D)`() {
        fun makeItem(song: Song, cIdx: Int?): MediaItem =
            QueueShuffle.withQueueMetadata(song.toMediaItem(), newEntryId = song.queueEntryId, newCanonicalIndex = cIdx, newTier = song.queueTier)

        // Timeline A: [C0, U1, C1, U2, C2] -> tap C2
        run {
            val songs = listOf(
                testSong("c0", QueueTier.CONTEXT, "e-c0"),
                testSong("u1", QueueTier.USER_QUEUE, "e-u1"),
                testSong("c1", QueueTier.CONTEXT, "e-c1"),
                testSong("u2", QueueTier.USER_QUEUE, "e-u2"),
                testSong("c2", QueueTier.CONTEXT, "e-c2"),
            )
            val items = mutableListOf(
                makeItem(songs[0], 0),
                makeItem(songs[1], null),
                makeItem(songs[2], 1),
                makeItem(songs[3], null),
                makeItem(songs[4], 2),
            )
            val p = TestPlayer(items, activeIndex = 0)
            QueueCoordinator.jumpToQueueItem(p.player, targetIndex = 4, cachedTimeline = songs)

            assertEquals(1, p.queueEditCallCount)
            assertEquals(2, p.activeIndex)
            assertEquals(listOf("c0", "c1", "c2", "u1", "u2"), p.items.map { it.mediaId })
            assertEquals("e-u1", p.items[3].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[3].queueTier)
            assertEquals("e-u2", p.items[4].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[4].queueTier)
        }

        // Timeline B: [C0, U1, U2, C1, C2] -> tap C2
        run {
            val songs = listOf(
                testSong("c0", QueueTier.CONTEXT, "e-c0"),
                testSong("u1", QueueTier.USER_QUEUE, "e-u1"),
                testSong("u2", QueueTier.USER_QUEUE, "e-u2"),
                testSong("c1", QueueTier.CONTEXT, "e-c1"),
                testSong("c2", QueueTier.CONTEXT, "e-c2"),
            )
            val items = mutableListOf(
                makeItem(songs[0], 0),
                makeItem(songs[1], null),
                makeItem(songs[2], null),
                makeItem(songs[3], 1),
                makeItem(songs[4], 2),
            )
            val p = TestPlayer(items, activeIndex = 0)
            QueueCoordinator.jumpToQueueItem(p.player, targetIndex = 4, cachedTimeline = songs)

            assertEquals(1, p.queueEditCallCount)
            assertEquals(2, p.activeIndex)
            assertEquals(listOf("c0", "c1", "c2", "u1", "u2"), p.items.map { it.mediaId })
            assertEquals("e-u1", p.items[3].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[3].queueTier)
            assertEquals("e-u2", p.items[4].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[4].queueTier)
        }

        // Timeline C: [C0, C1, U1, U2, C2, C3] -> tap C2
        run {
            val songs = listOf(
                testSong("c0", QueueTier.CONTEXT, "e-c0"),
                testSong("c1", QueueTier.CONTEXT, "e-c1"),
                testSong("u1", QueueTier.USER_QUEUE, "e-u1"),
                testSong("u2", QueueTier.USER_QUEUE, "e-u2"),
                testSong("c2", QueueTier.CONTEXT, "e-c2"),
                testSong("c3", QueueTier.CONTEXT, "e-c3"),
            )
            val items = mutableListOf(
                makeItem(songs[0], 0),
                makeItem(songs[1], 1),
                makeItem(songs[2], null),
                makeItem(songs[3], null),
                makeItem(songs[4], 2),
                makeItem(songs[5], 3),
            )
            val p = TestPlayer(items, activeIndex = 0)
            QueueCoordinator.jumpToQueueItem(p.player, targetIndex = 4, cachedTimeline = songs)

            assertEquals(1, p.queueEditCallCount)
            assertEquals(2, p.activeIndex)
            assertEquals(listOf("c0", "c1", "c2", "u1", "u2", "c3"), p.items.map { it.mediaId })
            assertEquals("e-u1", p.items[3].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[3].queueTier)
            assertEquals("e-u2", p.items[4].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[4].queueTier)
            assertEquals(QueueTier.CONTEXT, p.items[5].queueTier)
        }

        // Timeline D: [C0, U1, C1, C2, U2, C3] -> tap C1
        run {
            val songs = listOf(
                testSong("c0", QueueTier.CONTEXT, "e-c0"),
                testSong("u1", QueueTier.USER_QUEUE, "e-u1"),
                testSong("c1", QueueTier.CONTEXT, "e-c1"),
                testSong("c2", QueueTier.CONTEXT, "e-c2"),
                testSong("u2", QueueTier.USER_QUEUE, "e-u2"),
                testSong("c3", QueueTier.CONTEXT, "e-c3"),
            )
            val items = mutableListOf(
                makeItem(songs[0], 0),
                makeItem(songs[1], null),
                makeItem(songs[2], 1),
                makeItem(songs[3], 2),
                makeItem(songs[4], null),
                makeItem(songs[5], 3),
            )
            val p = TestPlayer(items, activeIndex = 0)
            QueueCoordinator.jumpToQueueItem(p.player, targetIndex = 2, cachedTimeline = songs)

            assertEquals(1, p.queueEditCallCount)
            assertEquals(1, p.activeIndex)
            assertEquals(listOf("c0", "c1", "u1", "u2", "c2", "c3"), p.items.map { it.mediaId })
            assertEquals("e-u1", p.items[2].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[2].queueTier)
            assertEquals("e-u2", p.items[3].queueEntryId)
            assertEquals(QueueTier.USER_QUEUE, p.items[3].queueTier)
            assertEquals(listOf("c2", "c3"), p.items.drop(4).map { it.mediaId })
        }
    }

    @Test
    fun `jumpToQueueItem to user queue track consumes bypassed user queue items and preserves following context`() {
        val songs = listOf(
            testSong("c0", QueueTier.CONTEXT, "e-c0"),
            testSong("u1", QueueTier.USER_QUEUE, "e-u1"),
            testSong("u2", QueueTier.USER_QUEUE, "e-u2"),
            testSong("u3", QueueTier.USER_QUEUE, "e-u3"),
            testSong("c1", QueueTier.CONTEXT, "e-c1"),
        )
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = if (s.queueTier == QueueTier.CONTEXT) idx else null, newTier = s.queueTier)
        }.toMutableList()
        val testPlayer = TestPlayer(items, activeIndex = 0)

        // Tap U2 (index 2)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 2, cachedTimeline = songs)

        assertEquals(1, testPlayer.queueEditCallCount)
        assertEquals(1, testPlayer.activeIndex)
        assertEquals(listOf("c0", "u2", "u3", "c1"), testPlayer.items.map { it.mediaId })
        assertEquals("e-u2", testPlayer.items[1].queueEntryId)
        assertEquals("e-u3", testPlayer.items[2].queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, testPlayer.items[1].queueTier)
        assertEquals(QueueTier.USER_QUEUE, testPlayer.items[2].queueTier)
        assertEquals(QueueTier.CONTEXT, testPlayer.items[3].queueTier)
    }

    @Test
    fun `jumpToQueueItem to autoplay track promotes target to context, drops old context, and preserves user queue`() {
        val songs = listOf(
            testSong("c0", QueueTier.CONTEXT, "e-c0"),
            testSong("u1", QueueTier.USER_QUEUE, "e-u1"),
            testSong("c1", QueueTier.CONTEXT, "e-c1"),
            testSong("a1", QueueTier.AUTOPLAY, "e-a1"),
            testSong("a2", QueueTier.AUTOPLAY, "e-a2"),
        )
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = if (s.queueTier == QueueTier.CONTEXT) idx else null, newTier = s.queueTier)
        }.toMutableList()
        val testPlayer = TestPlayer(items, activeIndex = 0)

        // Tap A2 (index 4)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4, cachedTimeline = songs)

        assertEquals(1, testPlayer.queueEditCallCount)
        assertEquals(1, testPlayer.activeIndex)
        assertEquals(listOf("c0", "a2", "u1"), testPlayer.items.map { it.mediaId })
        assertEquals(QueueTier.CONTEXT, testPlayer.items[1].queueTier)
        assertEquals("e-a2", testPlayer.items[1].queueEntryId)
        assertEquals(QueueTier.USER_QUEUE, testPlayer.items[2].queueTier)
        assertEquals("e-u1", testPlayer.items[2].queueEntryId)
    }

    @Test
    fun `jumpToQueueItem to final context track allows backward navigation through all preceding tracks`() {
        val songs = (0..4).map { testSong("c$it", tier = QueueTier.CONTEXT, entryId = "entry-c$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        val testPlayer = TestPlayer(items, activeIndex = 0)

        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4, cachedTimeline = songs)
        assertEquals(4, testPlayer.activeIndex)

        assertTrue(testPlayer.player.hasPreviousMediaItem())
        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(3, testPlayer.activeIndex)
        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(2, testPlayer.activeIndex)
        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(1, testPlayer.activeIndex)
        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(0, testPlayer.activeIndex)
        assertEquals("c0", testPlayer.items[testPlayer.activeIndex].mediaId)
    }

    @Test
    fun `jumpToQueueItem to final context track enables REPEAT_MODE_ALL to loop complete collection`() {
        val songs = (0..4).map { testSong("c$it", tier = QueueTier.CONTEXT, entryId = "entry-c$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        val testPlayer = TestPlayer(items, activeIndex = 0, repeatMode = Player.REPEAT_MODE_ALL)

        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4, cachedTimeline = songs)
        assertEquals(4, testPlayer.activeIndex)

        assertTrue(testPlayer.player.hasNextMediaItem())
        testPlayer.player.seekToNextMediaItem()
        assertEquals(0, testPlayer.activeIndex)
        assertEquals("c0", testPlayer.items[testPlayer.activeIndex].mediaId)
    }

    @Test
    fun `jumpToQueueItem followed by Shuffle OFF restores strictly forward canonical tracks`() {
        val songs = (0..4).map { testSong("c$it", tier = QueueTier.CONTEXT, entryId = "entry-c$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())
        QueueShuffle.setEnabled(true)

        val testPlayer = TestPlayer(items, activeIndex = 0)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4, cachedTimeline = songs)
        assertEquals(4, testPlayer.activeIndex)

        QueueShuffle.toggle(testPlayer.player)
        assertEquals(false, QueueShuffle.enabled.value)

        val upcoming = testPlayer.items.drop(testPlayer.activeIndex + 1)
        assertEquals(emptyList<MediaItem>(), upcoming)
        assertEquals("c4", testPlayer.items[testPlayer.activeIndex].mediaId)
    }

    @Test
    fun `jumpToQueueItem followed by Shuffle ON shuffles remaining context tracks without duplicating history`() {
        val songs = (0..4).map { testSong("c$it", tier = QueueTier.CONTEXT, entryId = "entry-c$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())
        QueueShuffle.setEnabled(false)

        val testPlayer = TestPlayer(items, activeIndex = 0)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 2, cachedTimeline = songs)
        assertEquals(2, testPlayer.activeIndex)

        QueueShuffle.toggle(testPlayer.player)
        assertEquals(true, QueueShuffle.enabled.value)

        val upcoming = testPlayer.items.drop(testPlayer.activeIndex + 1)
        assertEquals(2, upcoming.size)
        assertEquals(setOf(3, 4), upcoming.map { it.canonicalIndex }.toSet())

        // Timeline size is preserved (exactly 5 collection songs, zero duplicates)
        assertEquals(5, testPlayer.items.size)
    }

    @Test
    fun `jumpToQueueItem with duplicate context tracks preserves respective canonical occurrences`() {
        val songs = listOf(
            testSong("song-A", QueueTier.CONTEXT, "id-1"),
            testSong("song-B", QueueTier.CONTEXT, "id-2"),
            testSong("song-A", QueueTier.CONTEXT, "id-3"),
        )
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()

        val testPlayer = TestPlayer(items, activeIndex = 0)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 2, cachedTimeline = songs)

        assertEquals(2, testPlayer.activeIndex)
        val active = testPlayer.items[testPlayer.activeIndex]
        assertEquals("id-3", active.queueEntryId)
        assertEquals(2, active.canonicalIndex)
        assertEquals("song-A", active.mediaId)

        val historyOccurrence = testPlayer.items[0]
        assertEquals("id-1", historyOccurrence.queueEntryId)
        assertEquals(0, historyOccurrence.canonicalIndex)
        assertEquals("song-A", historyOccurrence.mediaId)
    }

    @Test
    fun `jumpToQueueItem preserves canonicalIndex on all retained context items`() {
        val songs = listOf(
            testSong("c0", QueueTier.CONTEXT, "e-c0"),
            testSong("u1", QueueTier.USER_QUEUE, "e-u1"),
            testSong("c1", QueueTier.CONTEXT, "e-c1"),
            testSong("c2", QueueTier.CONTEXT, "e-c2"),
        )
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = if (s.queueTier == QueueTier.CONTEXT) idx else null, newTier = s.queueTier)
        }.toMutableList()

        val testPlayer = TestPlayer(items, activeIndex = 0)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 3, cachedTimeline = songs)

        val c0 = testPlayer.items.first { it.mediaId == "c0" }
        val c1 = testPlayer.items.first { it.mediaId == "c1" }
        val c2 = testPlayer.items.first { it.mediaId == "c2" }

        assertEquals(0, c0.canonicalIndex)
        assertEquals(2, c1.canonicalIndex)
        assertEquals(3, c2.canonicalIndex)
    }

    @Test
    fun `jumpToQueueItem preserves queueEntryId on all retained items`() {
        val songs = listOf(
            testSong("c0", QueueTier.CONTEXT, "id-c0"),
            testSong("u1", QueueTier.USER_QUEUE, "id-u1"),
            testSong("c1", QueueTier.CONTEXT, "id-c1"),
            testSong("u2", QueueTier.USER_QUEUE, "id-u2"),
            testSong("c2", QueueTier.CONTEXT, "id-c2"),
        )
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = s.queueTier)
        }.toMutableList()

        val testPlayer = TestPlayer(items, activeIndex = 0)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4, cachedTimeline = songs)

        for (item in testPlayer.items) {
            val originalSong = songs.first { it.videoId == item.mediaId }
            assertEquals(originalSong.queueEntryId, item.queueEntryId)
        }
    }

    @Test
    fun `jumpToQueueItem preserves queueTier on all retained items`() {
        val songs = listOf(
            testSong("c0", QueueTier.CONTEXT, "id-c0"),
            testSong("u1", QueueTier.USER_QUEUE, "id-u1"),
            testSong("c1", QueueTier.CONTEXT, "id-c1"),
            testSong("u2", QueueTier.USER_QUEUE, "id-u2"),
            testSong("c2", QueueTier.CONTEXT, "id-c2"),
        )
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = s.queueTier)
        }.toMutableList()

        val testPlayer = TestPlayer(items, activeIndex = 0)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4, cachedTimeline = songs)

        assertEquals(QueueTier.CONTEXT, testPlayer.items[0].queueTier)
        assertEquals(QueueTier.CONTEXT, testPlayer.items[1].queueTier)
        assertEquals(QueueTier.CONTEXT, testPlayer.items[2].queueTier)
        assertEquals(QueueTier.USER_QUEUE, testPlayer.items[3].queueTier)
        assertEquals(QueueTier.USER_QUEUE, testPlayer.items[4].queueTier)
    }

    @Test
    fun `Album and Playlist produce identical jump semantics`() {
        val albumTracks = (0..4).map {
            testSong("track-$it", QueueTier.CONTEXT, "alb-$it", source = "Album A")
        }
        val playlistTracks = (0..4).map {
            testSong("track-$it", QueueTier.CONTEXT, "pl-$it", source = "Playlist P")
        }

        val albumItems = albumTracks.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()
        val playlistItems = playlistTracks.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx, newTier = QueueTier.CONTEXT)
        }.toMutableList()

        val albumPlayer = TestPlayer(albumItems, activeIndex = 0)
        val playlistPlayer = TestPlayer(playlistItems, activeIndex = 0)

        QueueCoordinator.jumpToQueueItem(albumPlayer.player, targetIndex = 4, cachedTimeline = albumTracks)
        QueueCoordinator.jumpToQueueItem(playlistPlayer.player, targetIndex = 4, cachedTimeline = playlistTracks)

        assertEquals(albumPlayer.seekCallCount, playlistPlayer.seekCallCount)
        assertEquals(albumPlayer.queueEditCallCount, playlistPlayer.queueEditCallCount)
        assertEquals(albumPlayer.activeIndex, playlistPlayer.activeIndex)
        assertEquals(albumPlayer.items.map { it.mediaId }, playlistPlayer.items.map { it.mediaId })
    }

    @Test
    fun `start with shuffle and loop all on in album then toggle OFF restores preceding tracks and allows previous without wrapping to album end`() {
        val albumTracks = (0..9).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it", source = "Album A") }
        val canonicalItems = albumTracks.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Simulate starting with shuffle ON picking track-4 (index 4)
        val selectedIndex = 4
        val startingOrder = QueueShuffle.startingOrder(albumTracks, selectedIndex)
        assertEquals("track-4", startingOrder[0].videoId)

        val playerItems = startingOrder.map { s ->
            val cIdx = albumTracks.indexOfFirst { it.videoId == s.videoId }
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = cIdx)
        }.toMutableList()

        val testPlayer = TestPlayer(playerItems, activeIndex = 0, repeatMode = Player.REPEAT_MODE_ALL)
        QueueShuffle.setEnabled(true)

        // Turn Shuffle OFF while playing track-4
        QueueShuffle.toggle(testPlayer.player)
        assertEquals(false, QueueShuffle.enabled.value)

        // The entire album must be restored: 10 items
        assertEquals(10, testPlayer.items.size)
        // Current index must be 4 (track-4)
        assertEquals(4, testPlayer.activeIndex)
        assertEquals("track-4", testPlayer.items[testPlayer.activeIndex].mediaId)

        // History contains track-0..3 in canonical order
        assertEquals(listOf("track-0", "track-1", "track-2", "track-3"), testPlayer.items.take(4).map { it.mediaId })
        // Upcoming contains track-5..9 in canonical order
        assertEquals(listOf("track-5", "track-6", "track-7", "track-8", "track-9"), testPlayer.items.drop(5).map { it.mediaId })

        // Hitting Prev steps backwards through preceding tracks: 3 -> 2 -> 1 -> 0
        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(3, testPlayer.activeIndex)
        assertEquals("track-3", testPlayer.items[testPlayer.activeIndex].mediaId)

        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(2, testPlayer.activeIndex)
        assertEquals("track-2", testPlayer.items[testPlayer.activeIndex].mediaId)

        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(1, testPlayer.activeIndex)
        assertEquals("track-1", testPlayer.items[testPlayer.activeIndex].mediaId)

        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(0, testPlayer.activeIndex)
        assertEquals("track-0", testPlayer.items[testPlayer.activeIndex].mediaId)
    }

    @Test
    fun `start with shuffle and loop all on in playlist then toggle OFF restores preceding tracks and allows previous without wrapping to playlist end`() {
        val playlistTracks = (0..9).map { testSong("pl-$it", QueueTier.CONTEXT, "id-$it", source = "Playlist P") }
        val canonicalItems = playlistTracks.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Starting with shuffle ON picking pl-6 (index 6)
        val selectedIndex = 6
        val startingOrder = QueueShuffle.startingOrder(playlistTracks, selectedIndex)
        assertEquals("pl-6", startingOrder[0].videoId)

        val playerItems = startingOrder.map { s ->
            val cIdx = playlistTracks.indexOfFirst { it.videoId == s.videoId }
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = cIdx)
        }.toMutableList()

        val testPlayer = TestPlayer(playerItems, activeIndex = 0, repeatMode = Player.REPEAT_MODE_ALL)
        QueueShuffle.setEnabled(true)

        // Turn Shuffle OFF
        QueueShuffle.toggle(testPlayer.player)
        assertEquals(false, QueueShuffle.enabled.value)

        assertEquals(10, testPlayer.items.size)
        assertEquals(6, testPlayer.activeIndex)
        assertEquals("pl-6", testPlayer.items[testPlayer.activeIndex].mediaId)

        // History: pl-0..5
        assertEquals((0..5).map { "pl-$it" }, testPlayer.items.take(6).map { it.mediaId })
        // Upcoming: pl-7..9
        assertEquals((7..9).map { "pl-$it" }, testPlayer.items.drop(7).map { it.mediaId })

        // Hitting Prev steps back to pl-5
        testPlayer.player.seekToPreviousMediaItem()
        assertEquals(5, testPlayer.activeIndex)
        assertEquals("pl-5", testPlayer.items[testPlayer.activeIndex].mediaId)
    }

    @Test
    fun `jumping forward in context queue after shuffle OFF preserves entire collection for backward navigation`() {
        val songs = (0..9).map { testSong("c$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Started shuffled at c3
        val startingOrder = QueueShuffle.startingOrder(songs, 3)
        val playerItems = startingOrder.map { s ->
            val cIdx = songs.indexOfFirst { it.videoId == s.videoId }
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = cIdx)
        }.toMutableList()

        val testPlayer = TestPlayer(playerItems, activeIndex = 0)
        QueueShuffle.setEnabled(true)

        // Turn shuffle OFF: full album restored with c3 at index 3
        QueueShuffle.toggle(testPlayer.player)
        assertEquals(3, testPlayer.activeIndex)
        assertEquals("c3", testPlayer.items[testPlayer.activeIndex].mediaId)

        // Now user jumps forward in context queue to c7 (index 7)
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 7, cachedTimeline = songs)
        assertEquals(7, testPlayer.activeIndex)
        assertEquals("c7", testPlayer.items[testPlayer.activeIndex].mediaId)
        assertEquals(10, testPlayer.items.size)

        // All preceding tracks (c0..c6) remain intact in history!
        assertEquals((0..6).map { "c$it" }, testPlayer.items.take(7).map { it.mediaId })

        // Hitting Prev steps backward through history: 6 -> 5 -> 4 -> 3 -> 2 -> 1 -> 0
        for (expectedIdx in 6 downTo 0) {
            testPlayer.player.seekToPreviousMediaItem()
            assertEquals(expectedIdx, testPlayer.activeIndex)
            assertEquals("c$expectedIdx", testPlayer.items[testPlayer.activeIndex].mediaId)
        }
    }

    @Test
    fun `jumping forward in queue while shuffle ON then turning shuffle OFF restores full canonical collection around jumped track`() {
        val songs = (0..9).map { testSong("c$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Started shuffled at c2 (index 0 in player)
        val startingOrder = QueueShuffle.startingOrder(songs, 2)
        val playerItems = startingOrder.map { s ->
            val cIdx = songs.indexOfFirst { it.videoId == s.videoId }
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = cIdx)
        }.toMutableList()

        val testPlayer = TestPlayer(playerItems, activeIndex = 0)
        QueueShuffle.setEnabled(true)

        // While shuffle is ON, user jumps forward to item at index 4 in the shuffled queue
        val targetItem = testPlayer.items[4]
        val targetCanonicalIdx = targetItem.canonicalIndex!!
        QueueCoordinator.jumpToQueueItem(testPlayer.player, targetIndex = 4)
        assertEquals(4, testPlayer.activeIndex)
        assertEquals(targetItem.mediaId, testPlayer.items[testPlayer.activeIndex].mediaId)

        // Now user turns shuffle OFF
        QueueShuffle.toggle(testPlayer.player)
        assertEquals(false, QueueShuffle.enabled.value)

        // Entire collection is restored around targetCanonicalIdx!
        assertEquals(10, testPlayer.items.size)
        assertEquals(targetCanonicalIdx, testPlayer.activeIndex)
        assertEquals(targetItem.mediaId, testPlayer.items[testPlayer.activeIndex].mediaId)

        // All canonical tracks are in order 0..9
        assertEquals((0..9).map { "c$it" }, testPlayer.items.map { it.mediaId })
    }

    /** Tiers looked up by song, since MediaItem metadata extras don't survive on the JVM. */
    private fun tierAt(songs: List<Song>, items: List<MediaItem>): (Int) -> QueueTier {
        val byId = songs.associate { it.videoId to it.queueTier }
        return { index -> byId.getValue(items[index].mediaId) }
    }
}
