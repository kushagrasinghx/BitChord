package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyQueue
import com.music.bitchord.data.listentogether.PartyPlayback
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.toMediaItem
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow

class PartyAutomaticTransitionTest {

    @Test
    fun `own automatic transition echo does not replay the intro`() = runTest {
        checkTransitionEcho(positionMs = 600L, updatedBy = "me", expectedSeeks = emptyList())
    }

    @Test
    fun `automatic transition still aligns a control from another member`() = runTest {
        checkTransitionEcho(positionMs = 600L, updatedBy = "other", expectedSeeks = listOf(0L))
    }

    @Test
    fun `automatic transition still corrects a large timing error`() = runTest {
        checkTransitionEcho(positionMs = 2_000L, updatedBy = "me", expectedSeeks = listOf(0L))
    }

    @Test
    fun `user intent after automatic transition still aligns precisely`() = runTest {
        checkTransitionEcho(
            positionMs = 600L, updatedBy = "me", expectedSeeks = listOf(0L), userIntent = true,
        )
    }

    @Test
    fun `own transition echo waits for the player to leave buffering before aligning`() = runTest {
        checkTransitionEcho(
            positionMs = 300L,
            updatedBy = "me",
            expectedSeeks = emptyList(),
            bufferingBeforeEcho = true,
        )
    }

    @Test
    fun `same-track manual seek echo still aligns precisely`() = runTest {
        withPartyState(liveParty().copy(
            playback = PartyPlayback(
                track = PartyTrack(videoId = "next"),
                isPlaying = true,
            ),
            queue = PartyQueue(items = listOf(PartyTrack(videoId = "next")), index = 0),
        )) {
            val fake = FakePlayer("next", initialPosition = 5_000L)
            val sync = PartySync(this) { fake.player }
            try {
                sync.onLocalIntent()
                PartySync::class.java.getDeclaredField("lastPartyCode").apply {
                    isAccessible = true
                    set(sync, "ABC123")
                }
                sync.start()
                advanceTimeBy(200L)
                runCurrent()
                assertTrue(fake.seeks.isEmpty())

                fake.currentPosition = 5_300L
                withPartyState(liveParty().copy(
                    playback = PartyPlayback(
                        seq = 1L,
                        track = PartyTrack(videoId = "next"),
                        isPlaying = true,
                        positionMs = 5_000L,
                        effectivePositionMs = 5_000L,
                        updatedBy = "me",
                    ),
                    queue = PartyQueue(items = listOf(PartyTrack(videoId = "next")), index = 0),
                )) {
                    runCurrent()
                    val reconcile = PartySync::class.java.getDeclaredMethod("reconcile").apply {
                        isAccessible = true
                    }
                    reconcile.invoke(sync)
                    assertEquals(listOf(5_000L), fake.seeks)
                }
            } finally {
                sync.stop()
            }
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.checkTransitionEcho(
        positionMs: Long,
        updatedBy: String,
        expectedSeeks: List<Long>,
        userIntent: Boolean = false,
        bufferingBeforeEcho: Boolean = false,
    ) {
        withPartyState(liveParty()) {
            val fake = FakePlayer("next")
            val sync = PartySync(this) { fake.player }
            try {
                if (bufferingBeforeEcho) fake.playbackState = Player.STATE_BUFFERING
                sync.onLocalIntent()
                // This test starts in an existing party, without the personal-queue stash.
                PartySync::class.java.getDeclaredField("lastPartyCode").apply {
                    isAccessible = true
                    set(sync, "ABC123")
                }
                sync.start()
                advanceTimeBy(200L)
                runCurrent()
                assertTrue(fake.playWhenReady)
                assertEquals(0, fake.pauseCalls)
                assertFalse(ListenTogether.awaitingStart.value)

                if (userIntent) sync.onLocalIntent()
                // Audio advances while the server confirms the position published above.
                fake.currentPosition = positionMs
                withPartyState(liveParty().copy(playback = PartyPlayback(
                    seq = 1L,
                    track = PartyTrack(videoId = "next"),
                    isPlaying = true,
                    updatedBy = updatedBy,
                ), queue = PartyQueue(seq = 1L, index = 0, items = listOf(PartyTrack(videoId = "next"))))) {
                    runCurrent()
                    if (bufferingBeforeEcho) {
                        assertTrue(fake.seeks.isEmpty())
                        fake.playbackState = Player.STATE_READY
                    } else if (userIntent) {
                        // The second local intent reset the quiet window; its same-track publish
                        // settles before explicit reconcile handles the transition echo.
                        advanceTimeBy(200L)
                        runCurrent()
                    }
                    assertTrue(fake.playWhenReady)
                    val reconcile = PartySync::class.java.getDeclaredMethod("reconcile").apply {
                        isAccessible = true
                    }
                    reconcile.invoke(sync)
                    assertEquals("echo is handled only once", expectedSeeks, fake.seeks)
                    reconcile.invoke(sync)
                    assertEquals("repeat reconcile does not seek again", expectedSeeks, fake.seeks)
                }
            } finally {
                sync.stop()
            }
        }
    }

    @Test
    fun `automatic transition leaves playback alone when party cannot schedule it`() = runTest {
        val base = liveParty()
        val cases = listOf(
            "no party" to base.copy(code = null),
            "offline" to base.copy(connection = ListenTogether.Connection.OFFLINE),
            "unsynced clock" to base.copy(clockSynced = false),
            "locked listener" to base.copy(
                hostOnlyControl = true,
                you = PartyMember(memberId = "me", isHost = false),
            ),
            "no party track" to base.copy(playback = base.playback.copy(track = null)),
            "party already on this track" to base.copy(
                playback = base.playback.copy(track = PartyTrack(videoId = "next")),
            ),
        )

        for ((label, state) in cases) {
            withPartyState(state) {
                val fake = FakePlayer("next")
                val sync = PartySync(this) { fake.player }
                try {
                    sync.onLocalIntent()
                    assertTrue(label, fake.playWhenReady)
                    assertEquals(label, 0, fake.pauseCalls)
                    assertFalse(label, ListenTogether.awaitingStart.value)
                } finally {
                    sync.stop()
                    ListenTogether.setAwaitingStart(false)
                }
            }
        }
    }

    @Test
    fun `automatic transition does not pause a device file`() = runTest {
        withPartyState(liveParty()) {
            val fileItem = MediaItem.Builder().setMediaId("content://local/song").build()
            val fake = FakePlayer("ignored", item = fileItem)
            val sync = PartySync(this) { fake.player }
            try {
                sync.onLocalIntent()
                assertTrue(fake.playWhenReady)
                assertEquals(0, fake.pauseCalls)
                assertFalse(ListenTogether.awaitingStart.value)
            } finally {
                sync.stop()
                ListenTogether.setAwaitingStart(false)
            }
        }
    }

    private suspend fun withPartyState(state: ListenTogether.State, block: suspend () -> Unit) {
        val stateField = ListenTogether::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(ListenTogether) as MutableStateFlow<ListenTogether.State>
        val previous = stateFlow.value
        val previousAwaiting = ListenTogether.awaitingStart.value
        try {
            ListenTogether.setAwaitingStart(false)
            stateFlow.value = state
            block()
        } finally {
            stateFlow.value = previous
            ListenTogether.setAwaitingStart(previousAwaiting)
        }
    }

    private fun liveParty() = ListenTogether.State(
        code = "ABC123",
        connection = ListenTogether.Connection.LIVE,
        clockSynced = true,
        you = PartyMember(memberId = "me", isHost = true),
        playback = PartyPlayback(
            track = PartyTrack(videoId = "old"),
            isPlaying = true,
        ),
    )

    private class FakePlayer(
        id: String,
        item: MediaItem = Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null).toMediaItem(),
        initialPosition: Long = 0L,
    ) {
        var playWhenReady = true
        var pauseCalls = 0
        var playCalls = 0
        val seeks = mutableListOf<Long>()
        var currentPosition = initialPosition
        var playbackState = Player.STATE_READY
        private val currentItem = item

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getPlayWhenReady" -> playWhenReady
                "getCurrentMediaItem" -> currentItem
                "getCurrentMediaItemIndex" -> 0
                "getMediaItemCount" -> 1
                "getMediaItemAt" -> currentItem
                "getCurrentPosition" -> currentPosition
                "seekTo" -> {
                    currentPosition = args!![0] as Long
                    seeks += currentPosition
                    null
                }
                "getDuration" -> 180_000L
                "getPlaybackState" -> playbackState
                "getPlaybackSuppressionReason" -> Player.PLAYBACK_SUPPRESSION_REASON_NONE
                "pause" -> {
                    pauseCalls++
                    playWhenReady = false
                    null
                }
                "play" -> {
                    playCalls++
                    playWhenReady = true
                    null
                }
                else -> null
            }
        } as Player
    }
}
