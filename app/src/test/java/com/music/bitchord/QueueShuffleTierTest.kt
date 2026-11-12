package com.music.bitchord

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.canonicalIndex
import com.music.bitchord.playback.queueEntryId
import com.music.bitchord.playback.queueTier
import com.music.bitchord.playback.toMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueShuffleTierTest {

    private fun testSong(
        id: String,
        tier: QueueTier = QueueTier.CONTEXT,
        entryId: String = "entry-$id",
    ) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailUrl = null,
        queueTier = tier,
        queueEntryId = entryId,
    )

    private class MockPlayerState(
        val items: MutableList<MediaItem>,
        var currentIndex: Int = 0,
        var isPlaying: Boolean = false,
        var playWhenReady: Boolean = false,
        var currentPosition: Long = 0L,
        var duration: Long = 180000L,
    ) {
        var setMediaItemsCalled: Boolean = false
        val replaceCalls = mutableListOf<Triple<Int, Int, List<MediaItem>>>()

        val player: Player = java.lang.reflect.Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args[0] as Int]
                "getCurrentMediaItemIndex" -> currentIndex
                "getCurrentMediaItem" -> items.getOrNull(currentIndex)
                "getCurrentPosition" -> currentPosition
                "getDuration" -> duration
                "isPlaying" -> isPlaying
                "getPlayWhenReady" -> playWhenReady
                "getPlaybackState" -> Player.STATE_READY
                "replaceMediaItems" -> {
                    val from = args[0] as Int
                    val to = args[1] as Int
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[2] as List<MediaItem>
                    replaceCalls.add(Triple(from, to, newItems))
                    for (i in (to - 1) downTo from) {
                        items.removeAt(i)
                    }
                    items.addAll(from, newItems)
                    if (to <= currentIndex) {
                        currentIndex += newItems.size - (to - from)
                    }
                    null
                }
                "setMediaItems" -> {
                    setMediaItemsCalled = true
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[0] as List<MediaItem>
                    val startIndex = args[1] as Int
                    items.clear()
                    items.addAll(newItems)
                    currentIndex = startIndex
                    null
                }
                "seekToNextMediaItem" -> {
                    if (currentIndex < items.size - 1) currentIndex++
                    null
                }
                "seekToPreviousMediaItem" -> {
                    if (currentIndex > 0) currentIndex--
                    null
                }
                "prepare" -> null
                "play" -> {
                    isPlaying = true
                    playWhenReady = true
                    null
                }
                "pause" -> {
                    isPlaying = false
                    playWhenReady = false
                    null
                }
                else -> null
            }
        } as Player
    }

    @Before
    fun setUp() {
        val editor = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java),
        ) { proxy, method, _ ->
            if (method.returnType == android.content.SharedPreferences.Editor::class.java) proxy else null
        }
        val prefs = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java),
        ) { _, method, _ ->
            if (method.name == "edit") editor else null
        } as android.content.SharedPreferences
        val field = com.music.bitchord.data.settings.AppSettings::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        field.set(com.music.bitchord.data.settings.AppSettings, prefs)

        QueueShuffle.setEnabled(false)
        QueueShuffle.clearCanonicalContext()
        QueueShuffle.isCrossfadeTransitioning = null
    }

    @Test
    fun `startingOrder preserves USER_QUEUE items at front and shuffles context`() {
        val selected = testSong("selected", tier = QueueTier.CONTEXT)
        val u1 = testSong("u1", tier = QueueTier.USER_QUEUE)
        val u2 = testSong("u2", tier = QueueTier.USER_QUEUE)
        val c1 = testSong("c1", tier = QueueTier.CONTEXT)
        val c2 = testSong("c2", tier = QueueTier.CONTEXT)
        val c3 = testSong("c3", tier = QueueTier.CONTEXT)
        val a1 = testSong("a1", tier = QueueTier.AUTOPLAY)

        val input = listOf(selected, u1, u2, c1, c2, c3, a1)
        val result = QueueShuffle.startingOrder(input, startIndex = 0)

        assertEquals("selected", result[0].videoId)
        assertEquals("u1", result[1].videoId)
        assertEquals("u2", result[2].videoId)

        // Context items are between indices 3 and 5 inclusive
        val resultContextIds = result.subList(3, 6).map { it.videoId }.toSet()
        assertEquals(setOf("c1", "c2", "c3"), resultContextIds)

        // Autoplay at index 6
        assertEquals("a1", result[6].videoId)
    }

    @Test
    fun `restoreOrder deterministically restores duplicate songs using unique queueEntryIds`() {
        val entry1 = "uuid-1"
        val entry2 = "uuid-2"
        val original = listOf(entry1, entry2)
        val shuffled = listOf(entry2, entry1)

        val restoredIndices = QueueShuffle.restoreOrder(shuffled, original)
        val restoredEntries = restoredIndices.map { shuffled[it] }

        assertEquals(original, restoredEntries)
    }

    @Test
    fun `restoreOrder restores canonical sequence immediately after current track without resurrecting history`() {
        val collection = (1..10).map { "track-$it" }
        // Current track is track-7. History holds track-1..track-6 (not in upcoming).
        // Upcoming contains shuffled remaining unplayed context tracks:
        val upcomingShuffled = listOf("track-9", "track-8", "track-10")

        val restoredIndices = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = "track-7",
        )
        val restored = restoredIndices.map { upcomingShuffled[it] }

        // Must restore to 8, 9, 10 immediately after 7. Played history (1..6) must NOT be resurrected.
        assertEquals(listOf("track-8", "track-9", "track-10"), restored)
    }

    @Test
    fun `restoreOrder restores strictly forward after current track and does not wrap around to beginning`() {
        val collection = (1..10).map { "track-$it" }
        // Current track is track-7. History holds track-1..track-6.
        // Upcoming contains shuffled forward tracks:
        val upcomingShuffled = listOf("track-10", "track-8", "track-9")

        val restoredIndices = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = "track-7",
        )
        val restored = restoredIndices.map { upcomingShuffled[it] }

        // Must restore strictly forward starting after track-7 to [8, 9, 10].
        // Does NOT wrap around to the beginning (tracks 1..6 are never visited).
        assertEquals(listOf("track-8", "track-9", "track-10"), restored)
    }

    @Test
    fun `restoreOrder falls back predictably when currentId is null or not in original`() {
        val collection = (1..10).map { "track-$it" }
        val upcomingShuffled = listOf("track-9", "track-8", "track-10")

        val restoredIndicesUnknown = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = "unknown-seed",
        )
        val restoredUnknown = restoredIndicesUnknown.map { upcomingShuffled[it] }
        assertEquals(listOf("track-8", "track-9", "track-10"), restoredUnknown)

        val restoredIndicesNull = QueueShuffle.restoreOrder(
            upcoming = upcomingShuffled,
            original = collection,
            currentId = null,
        )
        val restoredNull = restoredIndicesNull.map { upcomingShuffled[it] }
        assertEquals(listOf("track-8", "track-9", "track-10"), restoredNull)
    }

    @Test
    fun `new shuffle session preserves unconsumed IDs and does not duplicate history tracks into upcoming`() {
        val songs = (1..5).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Start playback at index 2 (track-3). History = [track-1 (id-1), track-2 (id-2)].
        // Upcoming = [track-4 (id-4), track-5 (id-5)].
        val state = MockPlayerState(items, currentIndex = 2)
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // History items 0 and 1 must retain their exact original IDs and remain in history
        assertEquals("id-1", state.items[0].queueEntryId)
        assertEquals("id-2", state.items[1].queueEntryId)

        // Current item (track-3) stays current
        assertEquals("id-3", state.items[2].queueEntryId)
        assertEquals(2, state.items[2].canonicalIndex)

        // Total timeline size remains 5 (no duplicate songs injected into upcoming)
        assertEquals(5, state.items.size)
        val upcoming = state.items.drop(3)
        assertEquals(2, upcoming.size)

        // Canonical indices present in upcoming must be exactly unconsumed tracks 3 and 4
        val upcomingCanonicalIndices = upcoming.map { it.canonicalIndex }.toSet()
        assertEquals(setOf(3, 4), upcomingCanonicalIndices)

        // Unconsumed items (cIdx 3 and 4) must preserve their existing IDs
        val unconsumed4 = upcoming.first { it.canonicalIndex == 3 }
        val unconsumed5 = upcoming.first { it.canonicalIndex == 4 }
        assertEquals("id-4", unconsumed4.queueEntryId)
        assertEquals("id-5", unconsumed5.queueEntryId)

        // All IDs across the entire queue are completely unique
        val allIds = state.items.map { it.queueEntryId }
        assertEquals(state.items.size, allIds.toSet().size)
    }

    @Test
    fun `duplicate song occurrences have distinct canonical indices and only current occurrence is excluded`() {
        val songA1 = testSong("song-A", QueueTier.CONTEXT, "id-A1")
        val songB = testSong("song-B", QueueTier.CONTEXT, "id-B")
        val songA2 = testSong("song-A", QueueTier.CONTEXT, "id-A2")
        val songC = testSong("song-C", QueueTier.CONTEXT, "id-C")

        val rawList = listOf(songA1, songB, songA2, songC)
        val items = rawList.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Playback starts at index 0 (song-A occurrence 1, canonicalIndex 0)
        val state = MockPlayerState(items, currentIndex = 0)
        QueueShuffle.setEnabled(false)

        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Current item is song-A (id-A1, canonicalIndex 0)
        assertEquals("id-A1", state.items[0].queueEntryId)
        assertEquals(0, state.items[0].canonicalIndex)

        // Upcoming has 3 items: song-B (cIdx 1), song-A occurrence 2 (cIdx 2), song-C (cIdx 3)
        val upcoming = state.items.drop(1)
        assertEquals(3, upcoming.size)

        val upcomingCanonicalIndices = upcoming.map { it.canonicalIndex }
        assertTrue(upcomingCanonicalIndices.contains(1))
        assertTrue(upcomingCanonicalIndices.contains(2))
        assertTrue(upcomingCanonicalIndices.contains(3))

        // Occurrence 2 of song-A was unconsumed, so it retained its original ID "id-A2"
        val occurrence2 = upcoming.first { it.canonicalIndex == 2 }
        assertEquals("id-A2", occurrence2.queueEntryId)
        assertEquals("song-A", occurrence2.mediaId)
    }

    @Test
    fun `shuffle OFF restores strictly forward canonical tracks without wrap-around and without resurrecting history`() {
        val songs = (1..10).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Current track is track-7 (index 6, canonicalIndex 6).
        // History: tracks 1..6 (cIdx 0..5).
        // Upcoming: tracks 8..10 (cIdx 7..9).
        val state = MockPlayerState(items, currentIndex = 6)
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)
        assertEquals(6 + 1 + 3, state.items.size) // 6 history + 1 current + 3 upcoming = 10 (no duplicates!)

        // Turn Shuffle OFF
        QueueShuffle.toggle(state.player)
        assertTrue(!QueueShuffle.enabled.value)

        // Current track remains track-7
        assertEquals("id-7", state.items[6].queueEntryId)
        assertEquals(6, state.items[6].canonicalIndex)

        // History remains untouched: tracks 1..6
        for (i in 0..5) {
            assertEquals("id-${i + 1}", state.items[i].queueEntryId)
            assertEquals(i, state.items[i].canonicalIndex)
        }

        // Upcoming must be restored strictly forward: [track-8, track-9, track-10]
        val upcoming = state.items.drop(7)
        assertEquals(3, upcoming.size)
        assertEquals(listOf(7, 8, 9), upcoming.map { it.canonicalIndex })
        assertEquals(listOf("id-8", "id-9", "id-10"), upcoming.map { it.queueEntryId })
    }

    @Test
    fun `end of collection shuffle ON does not duplicate history tracks into upcoming`() {
        val songs = (1..5).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val items = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }.toMutableList()
        QueueShuffle.setCanonicalContext(items.toList())

        // Playing at track-5 (final track, index 4). Upcoming is empty.
        val state = MockPlayerState(items, currentIndex = 4)
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Current track remains track-5
        assertEquals("id-5", state.items[4].queueEntryId)
        assertEquals(4, state.items[4].canonicalIndex)

        // Upcoming remains empty (history tracks 1..4 are NOT duplicated into upcoming)
        val upcoming = state.items.drop(5)
        assertEquals(0, upcoming.size)
        assertEquals(5, state.items.size)

        // Toggling Shuffle OFF restores to strictly forward (which is empty after track-5)
        QueueShuffle.toggle(state.player)
        assertTrue(!QueueShuffle.enabled.value)
        assertEquals(5, state.items.size)
        assertEquals(0, state.items.drop(5).size)
    }

    @Test
    fun `album and playlist parity with identical provenance and lifecycle`() {
        fun runLifecycle(sourceType: PlaybackSourceType, sourceName: String): List<String> {
            val songs = (1..6).map {
                testSong("track-$it", QueueTier.CONTEXT, "id-$it").copy(
                    playbackSource = sourceName,
                    playbackSourceType = sourceType,
                )
            }
            val items = songs.mapIndexed { idx, s ->
                QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
            }.toMutableList()
            QueueShuffle.setCanonicalContext(items.toList())
            val state = MockPlayerState(items, currentIndex = 2) // playing track-3
            QueueShuffle.setEnabled(false)

            // Turn ON
            QueueShuffle.toggle(state.player)
            val onCanonicalIndices = state.items.drop(3).mapNotNull { it.canonicalIndex }.sorted().joinToString()

            // Turn OFF
            QueueShuffle.toggle(state.player)
            val offCanonicalIndices = state.items.drop(3).mapNotNull { it.canonicalIndex }.joinToString()

            return listOf(onCanonicalIndices, offCanonicalIndices)
        }

        val albumResult = runLifecycle(PlaybackSourceType.BROWSE, "Album Master")
        val playlistResult = runLifecycle(PlaybackSourceType.QUEUE, "Playlist Favorites")

        // Both Album and Playlist produce identical canonical index sets on Shuffle ON and identical sequences on Shuffle OFF
        assertEquals(albumResult[0], playlistResult[0])
        assertEquals(albumResult[1], playlistResult[1])
        assertEquals("3, 4, 5", albumResult[0])
        assertEquals("3, 4, 5", albumResult[1])
    }

    @Test
    fun `identifying current track falls back to queueEntryId if canonicalIndex extra is missing`() {
        val songs = (1..4).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Create player items where current track has NO canonical index extra, only queueEntryId
        val playerItems = songs.mapIndexed { idx, s ->
            if (idx == 1) {
                // track-2 with missing canonicalIndex
                QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-2", newCanonicalIndex = null)
            } else {
                QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = "id-${idx + 1}", newCanonicalIndex = idx)
            }
        }.toMutableList()

        val state = MockPlayerState(playerItems, currentIndex = 1) // playing track-2
        QueueShuffle.setEnabled(false)

        // Turn Shuffle ON: current track must be correctly identified via queueEntryId fallback
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Current track remains track-2
        assertEquals("id-2", state.items[1].queueEntryId)

        // Upcoming has unconsumed tracks (tracks 3, 4 with canonical indices 2, 3), and history track-1 is not duplicated
        val upcoming = state.items.drop(2)
        assertEquals(2, upcoming.size)
        val upcomingCanonicalIndices = upcoming.map { it.canonicalIndex }.toSet()
        assertEquals(setOf(2, 3), upcomingCanonicalIndices)
    }

    @Test
    fun `reproduce album alternating skip and shuffle toggle`() {
        val albumSongs = (1..10).map { testSong("track-$it", QueueTier.CONTEXT, "entry-$it") }
        val items = albumSongs.map { it.toMediaItem() }.toMutableList()
        val state = MockPlayerState(items, currentIndex = 0)

        // Start with shuffle off
        QueueShuffle.setEnabled(false)

        repeat(20) { step ->
            state.player.seekToNextMediaItem()
            // When reaching near the end of album (e.g. index 8), simulate AutoPlay appending tracks with stable unique IDs
            if (state.currentIndex >= 8 && state.items.size == 10) {
                val autoplay = (1..5).map { testSong("autoplay-$it", QueueTier.AUTOPLAY, "entry-auto-$it").toMediaItem() }
                state.items.addAll(autoplay)
            }
            QueueShuffle.toggle(state.player)

            // Invariants:
            // 1. All queueEntryIds remain distinct across the queue
            val entryIds = state.items.map { it.queueEntryId ?: it.mediaId }
            assertEquals("All queue entry IDs must remain unique at step $step", state.items.size, entryIds.toSet().size)
        }
    }

    @Test
    fun `header shuffle button flow establishes canonical context and restores strictly forward on shuffle OFF`() {
        val songs = (1..6).map { testSong("track-$it", QueueTier.CONTEXT, "entry-$it") }
        // Simulate tapping header Shuffle button:
        QueueShuffle.enableForNextQueue()
        assertTrue(QueueShuffle.enabled.value)

        // Random pick selects index 2 (track-3)
        val selectedIndex = 2
        val startingOrder = QueueShuffle.startingOrder(songs, selectedIndex)

        // 1. Lead track is track-3 at index 0
        assertEquals("track-3", startingOrder[0].videoId)

        // 2. Remaining 5 context tracks are shuffled
        val restIds = startingOrder.drop(1).map { it.videoId }.toSet()
        assertEquals(setOf("track-1", "track-2", "track-4", "track-5", "track-6"), restIds)

        // Set up player as if playSongs loaded this queue
        val items = startingOrder.mapIndexed { idx, s ->
            val originalCanonicalIndex = songs.indexOfFirst { it.videoId == s.videoId }
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = originalCanonicalIndex)
        }.toMutableList()

        val state = MockPlayerState(items, currentIndex = 0) // playing track-3 (canonicalIndex 2)

        // 3. User turns Shuffle OFF while playing track-3
        QueueShuffle.toggle(state.player)
        assertTrue(!QueueShuffle.enabled.value)

        // Current track remains track-3 (canonicalIndex 2) at index 2
        assertEquals(2, state.currentIndex)
        assertEquals("track-3", state.items[2].mediaId)
        assertEquals(2, state.items[2].canonicalIndex)

        // Preceding history is restored: [track-1, track-2]
        assertEquals(listOf("track-1", "track-2"), state.items.take(2).map { it.mediaId })
        assertEquals(listOf(0, 1), state.items.take(2).map { it.canonicalIndex })

        // Upcoming is restored strictly forward: [track-4, track-5, track-6]
        val upcoming = state.items.drop(3)
        assertEquals(listOf("track-4", "track-5", "track-6"), upcoming.map { it.mediaId })
        assertEquals(listOf(3, 4, 5), upcoming.map { it.canonicalIndex })

        // Total collection has all 6 tracks preserved!
        assertEquals(6, state.items.size)

        // 4. Hitting Prev steps backwards through preceding tracks
        state.player.seekToPreviousMediaItem()
        assertEquals(1, state.currentIndex)
        assertEquals("track-2", state.items[state.currentIndex].mediaId)

        state.player.seekToPreviousMediaItem()
        assertEquals(0, state.currentIndex)
        assertEquals("track-1", state.items[state.currentIndex].mediaId)
    }

    @Test
    fun `getCanonicalIndex and restore fall back cleanly to mediaId when item has a distinct or fresh queueEntryId`() {
        val songs = (1..5).map { testSong("track-$it", QueueTier.CONTEXT, "canonical-id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Simulating an active player item playing track-3 where queueEntryId was regenerated fresh or missing
        val freshActiveItem = QueueShuffle.withQueueMetadata(
            testSong("track-3", QueueTier.CONTEXT, "fresh-id-3").toMediaItem(),
            newEntryId = "fresh-id-3",
            newCanonicalIndex = null,
        )

        val resolvedIndex = QueueShuffle.getCanonicalIndex(freshActiveItem)
        assertEquals(2, resolvedIndex)

        // Set up player with fresh active item at current position
        val otherItems = listOf(1, 2, 4, 5).map {
            QueueShuffle.withQueueMetadata(testSong("track-$it", QueueTier.CONTEXT, "other-id-$it").toMediaItem(), newEntryId = "other-id-$it")
        }
        val playerList = mutableListOf(otherItems[0], otherItems[1], freshActiveItem, otherItems[2], otherItems[3])
        val state = MockPlayerState(playerList, currentIndex = 2)
        QueueShuffle.setEnabled(true)

        // Turning shuffle OFF restores canonical collection around track-3
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)
        assertEquals(2, state.currentIndex)
        assertEquals("track-3", state.items[2].mediaId)
        assertEquals(listOf("track-1", "track-2"), state.items.take(2).map { it.mediaId })
        assertEquals(listOf("track-4", "track-5"), state.items.drop(3).map { it.mediaId })
    }

    @Test
    fun `user-queued duplicate of album song restores context continuing from last played context track`() {
        // Album has 25 tracks: track-0 to track-24
        val albumSongs = (0..24).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = albumSongs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Context playback was at #5 (history has track-0..5)
        val historyContext = canonicalItems.take(6)

        // Current item is a user-queued copy of album track #20
        val userQueuedItem = QueueShuffle.withQueueMetadata(
            testSong("track-20", QueueTier.USER_QUEUE, "user-entry-track-20").toMediaItem(),
            newEntryId = "user-entry-track-20",
            newCanonicalIndex = null,
            newTier = QueueTier.USER_QUEUE,
        )

        // Upcoming has remaining context tracks (tracks 6..24, including album's track #20)
        val upcomingContext = canonicalItems.drop(6)

        // Player items: history (0..5) + userQueuedItem (at index 6) + upcomingContext (tracks 6..24)
        val playerList = (historyContext + listOf(userQueuedItem) + upcomingContext).toMutableList()
        val state = MockPlayerState(playerList, currentIndex = 6)
        QueueShuffle.setEnabled(true)

        // Turn Shuffle OFF
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)

        // Current track remains user-queued track-20 at index 6
        assertEquals(6, state.currentIndex)
        val current = state.items[state.currentIndex]
        assertEquals("track-20", current.mediaId)
        assertEquals(QueueTier.USER_QUEUE, current.queueTier)
        assertEquals("user-entry-track-20", current.queueEntryId)

        // History contains all 6 context tracks played up to #5
        assertEquals(6, state.items.take(6).size)
        assertEquals((0..5).map { "track-$it" }, state.items.take(6).map { it.mediaId })
        assertEquals((0..5).toList(), state.items.take(6).map { it.canonicalIndex })

        // Upcoming continues from track-6 onwards!
        val upcoming = state.items.drop(7)
        assertEquals("track-6", upcoming.first().mediaId)
        assertEquals(6, upcoming.first().canonicalIndex)
        assertEquals(QueueTier.CONTEXT, upcoming.first().queueTier)

        // Album's own copy of track-20 is preserved in upcoming as CONTEXT
        val albumCopy20 = upcoming.first { it.canonicalIndex == 20 }
        assertEquals("track-20", albumCopy20.mediaId)
        assertEquals("id-20", albumCopy20.queueEntryId)
        assertEquals(QueueTier.CONTEXT, albumCopy20.queueTier)

        // Total items = 6 history + 1 current + 19 upcoming = 26 items
        assertEquals(26, state.items.size)
    }

    @Test
    fun `shuffle play more than 25 tracks and restore reorders without resurrecting trimmed history`() {
        val totalTracks = 40
        val songs = (0 until totalTracks).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        // Start playing from track 0
        val state = MockPlayerState(canonicalItems.toMutableList(), currentIndex = 0)
        QueueShuffle.setEnabled(false)

        // Turn on shuffle
        QueueShuffle.toggle(state.player)
        assertEquals(true, QueueShuffle.enabled.value)

        // Simulate playing 28 tracks.
        // In BitChord's QueueCoordinator / PlaybackService, history trims when exceeding 25 tracks:
        // Older tracks are removed from the head so at most 25 history items remain.
        repeat(28) {
            state.currentIndex++
            if (state.currentIndex > 25) {
                state.items.removeAt(0)
                state.currentIndex--
            }
        }

        // We have played 28 tracks, so 3 tracks were trimmed from history.
        // Player holds 25 history tracks + 1 current track + unplayed upcoming tracks.
        val currentItem = state.items[state.currentIndex]
        val c = QueueShuffle.getCanonicalIndex(currentItem)!!
        assertTrue(c in 0 until totalTracks)

        // Now restore shuffle
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)

        // Verify:
        // 1. The current track is still current.
        assertEquals(currentItem.mediaId, state.items[state.currentIndex].mediaId)

        // 2. Only rows still in the queue were reordered: the 3 trimmed tracks
        //    stay gone, nothing is duplicated.
        assertEquals(totalTracks - 3, state.items.size)
        assertEquals(state.items.size, state.items.map { it.mediaId }.toSet().size)

        // 3. History and upcoming are each in canonical order around c.
        val history = state.items.take(state.currentIndex).map { QueueShuffle.getCanonicalIndex(it)!! }
        val upcoming = state.items.drop(state.currentIndex + 1).map { QueueShuffle.getCanonicalIndex(it)!! }
        assertEquals(history.sorted(), history)
        assertEquals(upcoming.sorted(), upcoming)
        assertTrue(history.all { it < c })
        assertTrue(upcoming.all { it > c })
        state.items.forEach { assertEquals(QueueTier.CONTEXT, it.queueTier) }
    }

    @Test
    fun `restore while paused keeps player paused and does not interrupt playback`() {
        val songs = (0..9).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        val state = MockPlayerState(
            items = canonicalItems.toMutableList(),
            currentIndex = 3,
            isPlaying = false,
            playWhenReady = false,
            currentPosition = 45000L,
        )
        QueueShuffle.setEnabled(false)

        val itemBefore = state.items[state.currentIndex]

        // Toggle shuffle on
        QueueShuffle.toggle(state.player)
        assertEquals(true, QueueShuffle.enabled.value)
        assertEquals(false, state.isPlaying)
        assertEquals(false, state.playWhenReady)
        assertEquals(45000L, state.currentPosition)

        // Toggle restore
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)

        // Player must remain strictly paused with exact same position and playing item
        assertEquals(false, state.isPlaying)
        assertEquals(false, state.playWhenReady)
        assertEquals(45000L, state.currentPosition)
        assertEquals(3, state.currentIndex)
        assertEquals(itemBefore.mediaId, state.items[state.currentIndex].mediaId)
        assertEquals(false, state.setMediaItemsCalled)
    }

    @Test
    fun `property check set of entry IDs after shuffle and restore is preserved without loss or duplicates`() {
        // Queue with mixed tiers:
        // 4 context items (history + current)
        // 2 user queue items
        // 8 context items
        // 2 user queue items
        // 3 autoplay items
        val contextSongs = (0..11).map { testSong("ctx-$it", QueueTier.CONTEXT, "id-ctx-$it") }
        val userSongs = (0..3).map { testSong("usr-$it", QueueTier.USER_QUEUE, "id-usr-$it") }
        val autoSongs = (0..2).map { testSong("auto-$it", QueueTier.AUTOPLAY, "id-auto-$it") }

        val canonicalItems = contextSongs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        val initialItems = mutableListOf<MediaItem>()
        // 4 context items (indices 0..3)
        initialItems.addAll(canonicalItems.subList(0, 4))
        // 2 user-queue items
        initialItems.addAll(userSongs.subList(0, 2).map { it.toMediaItem() })
        // 8 context items (indices 4..12)
        initialItems.addAll(canonicalItems.subList(4, 12))
        // 2 user-queue items
        initialItems.addAll(userSongs.subList(2, 4).map { it.toMediaItem() })
        // 3 autoplay items
        initialItems.addAll(autoSongs.map { it.toMediaItem() })

        val initialEntryIds = initialItems.map { it.queueEntryId ?: it.mediaId }
        // Invariant: initial set has distinct IDs
        assertEquals(initialItems.size, initialEntryIds.toSet().size)

        // Playing track is at index 2 (a context track)
        val state = MockPlayerState(initialItems.toMutableList(), currentIndex = 2)
        QueueShuffle.setEnabled(false)

        // 1. Toggle Shuffle ON
        QueueShuffle.toggle(state.player)
        assertEquals(true, QueueShuffle.enabled.value)

        val shuffledEntryIds = state.items.map { it.queueEntryId ?: it.mediaId }
        // Set equality check: no loss, no duplication
        assertEquals(initialEntryIds.toSet(), shuffledEntryIds.toSet())
        assertEquals(initialEntryIds.size, state.items.size)

        // Verify tier invariants during shuffle:
        // History (0..2) untouched
        assertEquals(initialItems[0].mediaId, state.items[0].mediaId)
        assertEquals(initialItems[1].mediaId, state.items[1].mediaId)
        assertEquals(initialItems[2].mediaId, state.items[2].mediaId)

        // Upcoming section has USER_QUEUE first, then shuffled CONTEXT, then AUTOPLAY
        val upcomingShuffled = state.items.drop(state.currentIndex + 1)
        val uqCount = userSongs.size
        val autoCount = autoSongs.size
        // All USER_QUEUE items are at the beginning of upcoming
        assertTrue(upcomingShuffled.take(uqCount).all { it.queueTier == QueueTier.USER_QUEUE })
        // All AUTOPLAY items are at the end of upcoming
        assertTrue(upcomingShuffled.takeLast(autoCount).all { it.queueTier == QueueTier.AUTOPLAY })

        // 2. Toggle Restore
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)

        val restoredEntryIds = state.items.map { it.queueEntryId ?: it.mediaId }
        // Set equality check: no loss, no duplication after restore
        assertEquals(initialEntryIds.toSet(), restoredEntryIds.toSet())
        assertEquals(initialEntryIds.size, state.items.size)

        // In upcoming after restore: USER_QUEUE pinned behind current, then CONTEXT, then AUTOPLAY
        val upcomingRestored = state.items.drop(state.currentIndex + 1)
        assertTrue(upcomingRestored.take(uqCount).all { it.queueTier == QueueTier.USER_QUEUE })
        assertTrue(upcomingRestored.takeLast(autoCount).all { it.queueTier == QueueTier.AUTOPLAY })
    }

    @Test
    fun `benchmark toggle shuffle and restore performance on 1000 items`() {
        val totalTracks = 1000
        val songs = (0 until totalTracks).map { testSong("track-$it", QueueTier.CONTEXT, "id-$it") }
        val canonicalItems = songs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(canonicalItems)

        val state = MockPlayerState(canonicalItems.toMutableList(), currentIndex = 250)
        QueueShuffle.setEnabled(false)

        // Warm up JVM
        repeat(5) {
            QueueShuffle.toggle(state.player)
            QueueShuffle.toggle(state.player)
        }

        // Benchmark Shuffle ON
        val startShuffle = System.nanoTime()
        QueueShuffle.toggle(state.player)
        val shuffleDurationMs = (System.nanoTime() - startShuffle) / 1_000_000.0

        // Benchmark Restore OFF (Current Two-Segment Replace + O(1) Provenance)
        val startRestore = System.nanoTime()
        QueueShuffle.toggle(state.player)
        val restoreDurationMs = (System.nanoTime() - startRestore) / 1_000_000.0

        // Benchmark Baseline (83fa092 linear scan indexOfFirst + full list rebuild)
        val startBaseline = System.nanoTime()
        val existingByCanonicalBaseline = HashMap<Int, MediaItem>()
        for (i in state.items.indices) {
            val item = state.items[i]
            val idx = canonicalItems.indexOfFirst { it.mediaId == item.mediaId }
            if (idx >= 0 && !existingByCanonicalBaseline.containsKey(idx)) {
                existingByCanonicalBaseline[idx] = item
            }
        }
        val precedingBaseline = (0 until 250).map { cIdx ->
            existingByCanonicalBaseline[cIdx] ?: canonicalItems[cIdx]
        }
        val followingBaseline = (251 until canonicalItems.size).map { cIdx ->
            existingByCanonicalBaseline[cIdx] ?: canonicalItems[cIdx]
        }
        val fullListBaseline = precedingBaseline + listOf(state.items[250]) + followingBaseline
        val baselineDurationMs = (System.nanoTime() - startBaseline) / 1_000_000.0

        println("BENCHMARK_RESULT: Current 1000 tracks (shuffle: ${shuffleDurationMs} ms, restore: ${restoreDurationMs} ms) vs Baseline 83fa092 restore resolution: ${baselineDurationMs} ms")
        assertTrue("Shuffle should complete in < 50ms", shuffleDurationMs < 50.0)
        assertTrue("Restore should complete in < 50ms", restoreDurationMs < 50.0)
    }

    @Test
    fun `stale canonicalContext from album A is discarded when playing different queue with overlapping song`() {
        // 1. User played Album A with 4 tracks
        val albumASongs = (0..3).map { testSong("albumA-$it", QueueTier.CONTEXT, "id-A-$it") }
        val albumAItems = albumASongs.mapIndexed { idx, s ->
            QueueShuffle.withQueueMetadata(s.toMediaItem(), newEntryId = s.queueEntryId, newCanonicalIndex = idx)
        }
        QueueShuffle.setCanonicalContext(albumAItems)

        // 2. Later, user plays a Radio queue (via a non-playSongs path)
        // containing an overlapping song from Album A (track albumA-2), but with a new radio queueEntryId
        val radioSongs = listOf(
            testSong("radio-0", QueueTier.CONTEXT, "id-R-0"),
            testSong("albumA-2", QueueTier.CONTEXT, "id-R-overlap"), // Overlapping mediaId, different entryId!
            testSong("radio-2", QueueTier.CONTEXT, "id-R-2"),
            testSong("radio-3", QueueTier.CONTEXT, "id-R-3"),
        )
        val radioItems = radioSongs.map { it.toMediaItem() }.toMutableList()
        val state = MockPlayerState(radioItems, currentIndex = 1) // currently playing overlapping song
        QueueShuffle.setEnabled(false)

        // 3. Toggle Shuffle ON
        QueueShuffle.toggle(state.player)
        assertEquals(true, QueueShuffle.enabled.value)

        // 4. Toggle Shuffle OFF (restore)
        QueueShuffle.toggle(state.player)
        assertEquals(false, QueueShuffle.enabled.value)

        // Assert: NO Album A tracks other than the radio queue's own items appear in the queue!
        val currentMediaIds = state.items.map { it.mediaId }
        val expectedMediaIds = radioSongs.map { it.videoId }
        assertEquals(expectedMediaIds, currentMediaIds)

        // Specifically assert Album A tracks albumA-0, albumA-1, albumA-3 were NOT spliced in!
        assertFalse(currentMediaIds.contains("albumA-0"))
        assertFalse(currentMediaIds.contains("albumA-1"))
        assertFalse(currentMediaIds.contains("albumA-3"))
    }

    @Test
    fun `replaceChanged preserves unchanged prefix and suffix without replacing next preloaded item`() {
        val uq1 = testSong("uq-1", QueueTier.USER_QUEUE, "id-uq-1").toMediaItem()
        val uq2 = testSong("uq-2", QueueTier.USER_QUEUE, "id-uq-2").toMediaItem()
        val c1 = testSong("c-1", QueueTier.CONTEXT, "id-c-1").toMediaItem()
        val c2 = testSong("c-2", QueueTier.CONTEXT, "id-c-2").toMediaItem()
        val auto1 = testSong("auto-1", QueueTier.AUTOPLAY, "id-auto-1").toMediaItem()

        // Timeline has: current track at 0, followed by uq1, uq2, c1, c2, auto1
        val curTrack = testSong("cur", QueueTier.CONTEXT, "id-cur").toMediaItem()
        val items = mutableListOf(curTrack, uq1, uq2, c1, c2, auto1)
        val state = MockPlayerState(items, currentIndex = 0)

        // Upcoming is indices 1..5: [uq1, uq2, c1, c2, auto1]
        // Target reorder only shuffles c1 and c2: [uq1, uq2, c2, c1, auto1]
        val target = listOf(uq1, uq2, c2, c1, auto1)

        val replaced = QueueShuffle.replaceChanged(state.player, from = 1, to = 6, new = target)
        assertTrue(replaced)

        // Assert that replaceMediaItems was called ONLY for the changed middle slice!
        // Prefix [uq1, uq2] (indices 1 and 2) was skipped (from + 2 = 3)
        // Suffix [auto1] (index 5) was skipped (to - 1 = 5)
        assertEquals(1, state.replaceCalls.size)
        val call = state.replaceCalls.first()
        assertEquals(3, call.first)  // fromIndex = 3
        assertEquals(5, call.second) // toIndex = 5
        assertEquals(listOf("c-2", "c-1"), call.third.map { it.mediaId })

        // Preloaded next items (uq1 at index 1, uq2 at index 2) are completely untouched!
        assertEquals("uq-1", state.items[1].mediaId)
        assertEquals("uq-2", state.items[2].mediaId)
        assertEquals("c-2", state.items[3].mediaId)
        assertEquals("c-1", state.items[4].mediaId)
        assertEquals("auto-1", state.items[5].mediaId)

        // Calling replaceChanged with identical list does NOT call replaceMediaItems
        state.replaceCalls.clear()
        val noOp = QueueShuffle.replaceChanged(state.player, from = 1, to = 6, new = target)
        assertFalse(noOp)
        assertEquals(0, state.replaceCalls.size)
    }

    @Test
    fun `replaceChanged key distinguishes items with identical ids but differing queueTier`() {
        val cItem = testSong("song-1", QueueTier.CONTEXT, "entry-1").toMediaItem()
        val uqItem = testSong("song-1", QueueTier.USER_QUEUE, "entry-1").toMediaItem()

        val items = mutableListOf(cItem)
        val state = MockPlayerState(items, currentIndex = 0)

        // Target replaces cItem with uqItem (tier changed from CONTEXT to USER_QUEUE)
        val replaced = QueueShuffle.replaceChanged(state.player, from = 0, to = 1, new = listOf(uqItem))
        assertTrue("replaceChanged should detect difference because key includes queueTier", replaced)
        assertEquals(1, state.replaceCalls.size)
        assertEquals(QueueTier.USER_QUEUE, state.items[0].queueTier)
    }

    @Test
    fun `original snapshot updates unconditionally on shuffle and clears on every restore`() {
        val s0 = testSong("s0", QueueTier.CONTEXT, "e0").toMediaItem()
        val s1 = testSong("s1", QueueTier.CONTEXT, "e1").toMediaItem()
        val s2 = testSong("s2", QueueTier.CONTEXT, "e2").toMediaItem()
        val s3 = testSong("s3", QueueTier.CONTEXT, "e3").toMediaItem()
        val canonicalItems = listOf(s0, s1, s2, s3)
        QueueShuffle.setCanonicalContext(canonicalItems)

        val items = mutableListOf(s0, s1, s2, s3)
        val state = MockPlayerState(items, currentIndex = 0)

        // 1. First shuffle on
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)
        assertEquals(4, QueueShuffle.originalOrder.size)
        val firstSnapshot = QueueShuffle.originalOrder

        // 2. Canonical restore
        QueueShuffle.toggle(state.player)
        assertFalse(QueueShuffle.enabled.value)
        assertTrue("original must be cleared after canonical restore", QueueShuffle.originalOrder.isEmpty())

        // 3. User modifies queue (e.g. appends s4)
        val s4 = testSong("s4", QueueTier.CONTEXT, "e4").toMediaItem()
        state.items.add(s4)

        // 4. Second shuffle on
        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)
        assertEquals("original must be updated unconditionally with fresh queue snapshot", 5, QueueShuffle.originalOrder.size)
        assertNotEquals(firstSnapshot, QueueShuffle.originalOrder)

        // 5. Clear context to force fallback restore
        QueueShuffle.clearCanonicalContext()
        QueueShuffle.toggle(state.player)
        assertFalse(QueueShuffle.enabled.value)
        assertTrue("original must be cleared after fallback restore", QueueShuffle.originalOrder.isEmpty())
    }

    @Test
    fun `isCanonicalContextValid does not clear context when playing USER_QUEUE or player is empty`() {
        val s0 = testSong("s0", QueueTier.CONTEXT, "e0").toMediaItem()
        val s1 = testSong("s1", QueueTier.CONTEXT, "e1").toMediaItem()
        QueueShuffle.setCanonicalContext(listOf(s0, s1))

        // Empty player (before setMediaItems reaches service)
        val emptyState = MockPlayerState(mutableListOf(), currentIndex = -1)
        assertTrue("Context must not be wiped when player is empty", QueueShuffle.isCanonicalContextValid(emptyState.player))

        // Player is playing a USER_QUEUE track
        val uq0 = testSong("uq0", QueueTier.USER_QUEUE, "uq-e0").toMediaItem()
        val userQueueState = MockPlayerState(mutableListOf(uq0, s0, s1), currentIndex = 0)
        assertTrue("Context must not be wiped when active track is USER_QUEUE", QueueShuffle.isCanonicalContextValid(userQueueState.player))

        // Player is playing an alien CONTEXT track
        val alienCtx = testSong("alien", QueueTier.CONTEXT, "alien-e").toMediaItem()
        val alienState = MockPlayerState(mutableListOf(alienCtx), currentIndex = 0)
        assertFalse("Context must be invalidated when active track is a foreign CONTEXT track", QueueShuffle.isCanonicalContextValid(alienState.player))
    }

    @Test
    fun `imminent next track gate keeps cur + 1 in place and shuffles cur + 2 onward`() {
        val c0 = testSong("c0", QueueTier.CONTEXT, "e0").toMediaItem()
        val c1 = testSong("c1", QueueTier.CONTEXT, "e1").toMediaItem()
        val c2 = testSong("c2", QueueTier.CONTEXT, "e2").toMediaItem()
        val c3 = testSong("c3", QueueTier.CONTEXT, "e3").toMediaItem()
        val c4 = testSong("c4", QueueTier.CONTEXT, "e4").toMediaItem()
        val canonical = listOf(c0, c1, c2, c3, c4)
        QueueShuffle.setCanonicalContext(canonical)

        val items = mutableListOf(c0, c1, c2, c3, c4)
        // Track 0 playing, duration = 180s, position = 177s -> 3s left (imminent <= 5s)
        val state = MockPlayerState(items, currentIndex = 0, currentPosition = 177000L, duration = 180000L)

        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Item at index 0 (playing) and index 1 (imminent next track) must be strictly untouched!
        assertEquals("c0", state.items[0].mediaId)
        assertEquals("c1", state.items[1].mediaId)

        // Replace calls must only touch from index >= 2
        assertTrue(state.replaceCalls.all { it.first >= 2 })
    }

    @Test
    fun `imminent next track gate keeps cur + 1 in place during restore and restores remaining tracks`() {
        val c0 = testSong("c0", QueueTier.CONTEXT, "e0").toMediaItem()
        val c1 = testSong("c1", QueueTier.CONTEXT, "e1").toMediaItem()
        val c2 = testSong("c2", QueueTier.CONTEXT, "e2").toMediaItem()
        val c3 = testSong("c3", QueueTier.CONTEXT, "e3").toMediaItem()
        val canonical = listOf(c0, c1, c2, c3)
        QueueShuffle.setCanonicalContext(canonical)

        // Shuffled queue where c3 was moved next (index 1), c1 is at index 2, c2 is at index 3
        val items = mutableListOf(c0, c3, c1, c2)
        // 4s left before track end (imminent <= 5s)
        val state = MockPlayerState(items, currentIndex = 0, currentPosition = 176000L, duration = 180000L)
        QueueShuffle.setEnabled(true)

        QueueShuffle.toggle(state.player) // restore
        assertFalse(QueueShuffle.enabled.value)

        // Playing item (c0) and imminent next item (c3) must be untouched in place!
        assertEquals("c0", state.items[0].mediaId)
        assertEquals("c3", state.items[1].mediaId)

        // The remaining tracks (c1, c2) are restored after c3
        assertEquals("c1", state.items[2].mediaId)
        assertEquals("c2", state.items[3].mediaId)

        // Replace calls must not touch index 0 or index 1
        assertTrue(state.replaceCalls.all { it.first >= 2 })
    }

    @Test
    fun `isCrossfadeTransitioning callback triggers imminent protection even with long remaining duration`() {
        val c0 = testSong("c0", QueueTier.CONTEXT, "e0").toMediaItem()
        val c1 = testSong("c1", QueueTier.CONTEXT, "e1").toMediaItem()
        val c2 = testSong("c2", QueueTier.CONTEXT, "e2").toMediaItem()
        val c3 = testSong("c3", QueueTier.CONTEXT, "e3").toMediaItem()
        QueueShuffle.setCanonicalContext(listOf(c0, c1, c2, c3))

        val items = mutableListOf(c0, c1, c2, c3)
        // 170 seconds remaining (not naturally imminent)
        val state = MockPlayerState(items, currentIndex = 0, currentPosition = 10000L, duration = 180000L)

        // Arming / transitioning in CrossfadeController
        QueueShuffle.isCrossfadeTransitioning = { true }

        QueueShuffle.toggle(state.player)
        assertTrue(QueueShuffle.enabled.value)

        // Index 1 (c1) must be preserved in place
        assertEquals("c1", state.items[1].mediaId)
        assertTrue(state.replaceCalls.all { it.first >= 2 })
    }
}


