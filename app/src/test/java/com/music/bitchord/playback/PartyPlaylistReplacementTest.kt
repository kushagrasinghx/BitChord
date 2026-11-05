package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyPlayback
import com.music.bitchord.data.listentogether.PartyQueue
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class PartyPlaylistReplacementTest {
    @Test
    fun `a newer playlist replaces an unpublished playlist and cannot refill its tail`() = runTest {
        val oldTracks = List(60) { PartyTrack("old-$it", title = "Old $it") }
        val stateField = ListenTogether::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = stateField.get(ListenTogether) as MutableStateFlow<ListenTogether.State>
        val previous = state.value
        val previousAwaitingStart = ListenTogether.awaitingStart.value
        val oldParty = ListenTogether.State(
            code = "ABC123",
            connection = ListenTogether.Connection.LIVE,
            clockSynced = true,
            you = PartyMember(memberId = "me", isHost = true),
            playback = PartyPlayback(
                track = oldTracks[0],
                queueSeq = 1,
                queueLength = 26,
                queueIndex = 0,
                isPlaying = true,
            ),
            queue = PartyQueue(seq = 1, index = 0, items = oldTracks.take(26)),
        )
        val player = FakePlayer(oldTracks.take(26).map { it.toSong().toMediaItem() }.toMutableList())
        val sync = PartySync(this) { player.player }
        try {
            state.value = oldParty
            sync.playPlaylist(request(oldTracks.map { it.toSong() }, "playlist-a"))
            sync.playPlaylist(request(listOf(song("new")), "playlist-b"))
            PartySync::class.java.getDeclaredField("lastPartyCode").apply {
                isAccessible = true
                set(sync, "ABC123")
            }
            sync.start()

            advanceTimeBy(200L)
            runCurrent()
            assertEquals(listOf("new"), player.items.map { it.mediaId })

            state.value = oldParty.copy(
                playback = PartyPlayback(
                    seq = 1,
                    track = PartyTrack("new"),
                    queueSeq = 2,
                    queueLength = 1,
                    queueIndex = 0,
                    isPlaying = true,
                    updatedBy = "me",
                ),
                queue = PartyQueue(seq = 2, index = 0, items = listOf(PartyTrack("new"))),
            )
            runCurrent()
            PartySync::class.java.getDeclaredMethod("reconcile").apply {
                isAccessible = true
                invoke(sync)
            }

            assertEquals(listOf("new"), player.items.map { it.mediaId })
        } finally {
            sync.stop()
            state.value = previous
            ListenTogether.setAwaitingStart(previousAwaitingStart)
        }
    }

    private fun request(songs: List<Song>, id: String) = BoundedContextRequests.Request(
        songs = songs,
        startIndex = 0,
        source = QueueSource(id, PlaybackSourceType.BROWSE, id),
        continuationToken = null,
        shuffle = false,
    )

    private fun song(id: String) = Song(id, id, "Artist", thumbnailUrl = null)

    private fun PartyTrack.toSong() = Song(videoId, title, artist, thumbnailUrl)

    private class FakePlayer(val items: MutableList<MediaItem>) {
        var currentIndex = 0
        var currentPosition = 0L
        var playWhenReady = true
        var playbackState = Player.STATE_READY

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> items.getOrNull(currentIndex)
                "getCurrentMediaItemIndex" -> currentIndex
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args!![0] as Int]
                "getCurrentPosition" -> currentPosition
                "getDuration" -> 180_000L
                "getPlayWhenReady" -> playWhenReady
                "getPlaybackState" -> playbackState
                "getPlaybackSuppressionReason" -> Player.PLAYBACK_SUPPRESSION_REASON_NONE
                "setMediaItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val next = args!![0] as List<MediaItem>
                    items.clear()
                    items.addAll(next)
                    currentIndex = args[1] as Int
                    currentPosition = args[2] as Long
                    null
                }
                "addMediaItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val additions = args!![if (args.size == 1) 0 else 1] as List<MediaItem>
                    val at = if (args.size == 1) items.size else args[0] as Int
                    items.addAll(at, additions)
                    null
                }
                "removeMediaItems" -> {
                    val from = args!![0] as Int
                    val to = args[1] as Int
                    repeat(to - from) { items.removeAt(from) }
                    null
                }
                "seekTo" -> {
                    if (args!!.size == 1) {
                        currentPosition = args[0] as Long
                    } else {
                        currentIndex = args[0] as Int
                        currentPosition = args[1] as Long
                    }
                    null
                }
                "play" -> {
                    playWhenReady = true
                    null
                }
                "pause" -> {
                    playWhenReady = false
                    null
                }
                else -> null
            }
        } as Player
    }
}
