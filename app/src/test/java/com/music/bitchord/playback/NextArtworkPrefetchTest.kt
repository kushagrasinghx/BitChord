package com.music.bitchord.playback

import androidx.media3.common.Player
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.remote.cancellableArtworkRead
import com.music.bitchord.data.remote.embeddedArtKey
import com.music.bitchord.data.remote.scopedRemoteCoverUrl
import java.lang.reflect.Proxy
import java.net.URI
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NextArtworkPrefetchTest {
    private class PlaybackSnapshot {
        var current = 0
        var next = 1
        var count = 3
        var repeat = Player.REPEAT_MODE_OFF
        var state = Player.STATE_READY
        var play = true
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getCurrentMediaItemIndex" -> current
                "getNextMediaItemIndex" -> next
                "getMediaItemCount" -> count
                "getRepeatMode" -> repeat
                "getPlaybackState" -> state
                "getPlayWhenReady" -> play
                else -> throw AssertionError(method.name)
            }
        } as Player
    }

    @Test
    fun onlyTheEngineSuccessorIsEligibleAcrossQueueAndShuffleChanges() = runTest {
        val engine = PlaybackSnapshot()
        var queue = listOf("A", "B", "C")
        val warmed = mutableListOf<String>()
        val prefetch = NextArtworkPrefetch<String>(backgroundScope) { target, _ -> warmed += target }
        fun refresh() {
            prefetch.replace(engine.player.expectedArtworkIndex(arming = false)?.let(queue::get))
            runCurrent()
        }

        refresh() // A → B, never C at the same time.
        queue = listOf("A", "D", "B", "C")
        engine.count = queue.size
        refresh() // insert at the same index
        queue = listOf("A", "C", "D", "B")
        refresh() // reorder
        engine.next = 3
        refresh() // the engine's shuffled order is non-adjacent
        engine.current = 3
        engine.next = 2
        refresh() // B is now current; D is the next occurrence.
        assertEquals(listOf("B", "D", "C", "B", "D"), warmed)
    }

    @Test
    fun repeatAutoplayAndStopRespectPlaybackEligibility() {
        val engine = PlaybackSnapshot().apply { current = 2; next = -1 }
        assertNull(engine.player.expectedArtworkIndex(arming = false))
        engine.count = 4
        engine.next = 3
        assertEquals(3, engine.player.expectedArtworkIndex(arming = false))
        engine.repeat = Player.REPEAT_MODE_ONE
        assertNull(engine.player.expectedArtworkIndex(arming = false))
        engine.repeat = Player.REPEAT_MODE_ALL
        engine.next = 0
        assertEquals(0, engine.player.expectedArtworkIndex(arming = false))
        engine.next = engine.current
        assertNull(engine.player.expectedArtworkIndex(arming = false))
        engine.next = 1
        engine.play = false
        assertNull(engine.player.expectedArtworkIndex(arming = false))
        assertEquals(1, engine.player.expectedArtworkIndex(arming = true))
        engine.state = Player.STATE_IDLE
        assertNull(engine.player.expectedArtworkIndex(arming = true))
    }

    @Test
    fun remoteArtworkIdentityChangesWithoutChangingTheServerPath() {
        val url = "https://example.invalid/music/cover.jpg?version=2"
        val first = scopedRemoteCoverUrl(url, 3)
        val next = scopedRemoteCoverUrl(url, 4)
        assertNotEquals(first, next)
        assertEquals(URI(url).rawPath, URI(next).rawPath)
        assertEquals(URI(url).rawQuery, URI(next).rawQuery)
        val song = Song("smb:music/file", "Title", "Artist", thumbnailUrl = null, localUri = "smb://host/share/file")
        assertNotEquals(embeddedArtKey(song, 3), embeddedArtKey(song, 4))
        assertNotEquals(embeddedArtKey(song, 3), embeddedArtKey(song.copy(localUri = "smb://host/share/other"), 3))
    }

    @Test
    fun cancellationClosesTheActualBlockingArtworkTransportBeforeJoining() = runBlocking {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val started = CompletableDeferred<Socket>()
            val stopped = CompletableDeferred<Unit>()
            val reader = launch {
                cancellableArtworkRead { transport ->
                    Socket(server.inetAddress, server.localPort).use { socket ->
                        socket.soTimeout = 3_000
                        transport.closeOnCancel(socket::close)
                        started.complete(socket)
                        try {
                            runCatching { socket.getInputStream().read() }
                        } finally {
                            stopped.complete(Unit)
                        }
                    }
                }
            }
            val socket = withTimeout(5_000) { started.await() }
            withTimeout(2_000) { reader.cancelAndJoin() }
            assertTrue(socket.isClosed)
            assertTrue(stopped.isCompleted)
        }
    }

    @Test
    fun aReplacementWaitsForTheOldWorkerAndRejectsItsLateResult() = runTest {
        val finishOld = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        val published = mutableListOf<String>()
        val prefetch = NextArtworkPrefetch<String>(backgroundScope) { target, current ->
            started += target
            if (target == "B") withContext(NonCancellable) {
                finishOld.await()
                if (current()) published += target
            } else if (current()) published += target
        }

        prefetch.replace("B")
        runCurrent()
        prefetch.replace("C")
        runCurrent()
        prefetch.replace("D")
        runCurrent()
        assertEquals(listOf("B"), started)
        finishOld.complete(Unit)
        runCurrent()
        assertEquals(listOf("B", "D"), started)
        assertEquals(listOf("D"), published)
    }

    @Test
    fun unchangedTargetsAreNotRetriedAndStopCancelsTheWorker() = runTest {
        val events = mutableListOf<String>()
        val prefetch = NextArtworkPrefetch<String>(backgroundScope) { target, _ ->
            events += "start $target"
            if (target == "missing") return@NextArtworkPrefetch
            try {
                awaitCancellation()
            } finally {
                events += "stop $target"
            }
        }

        prefetch.replace("missing")
        runCurrent()
        prefetch.replace("missing")
        runCurrent()
        prefetch.replace("autoplay")
        runCurrent()
        prefetch.replace(null)
        runCurrent()
        assertEquals(listOf("start missing", "start autoplay", "stop autoplay"), events)
    }
}
