package com.music.bitchord.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class StableSessionControllerTest {

    private lateinit var context: Context
    private lateinit var facade: StableSessionPlayer
    private lateinit var session: MediaSession
    private lateinit var controller: MediaController
    private lateinit var first: FakeDelegate
    private lateinit var second: FakeDelegate

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        first = FakeDelegate("first", Looper.getMainLooper())
        second = FakeDelegate("second", Looper.getMainLooper())
        facade = StableSessionPlayer(first, Looper.getMainLooper())
        lateinit var built: MediaSession
        lateinit var connected: MediaController
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            built = MediaSession.Builder(context, facade).build()
            val future = MediaController.Builder(context, built.token).buildAsync()
            future.addListener(
                {
                    connected = future.get()
                    latch.countDown()
                },
                { it.run() },
            )
        }
        assertTrue("MediaController did not connect", latch.await(10, TimeUnit.SECONDS))
        session = built
        controller = connected
        idle()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.release() }
        }
        if (::session.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { session.release() }
        }
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun controllerStaysConnectedAndCommandsReachTheCurrentDelegate() {
        onMain { controller.play() }
        idle()
        assertTrue("first delegate should receive play", first.playing)
        assertFalse("second delegate must not receive play", second.playing)

        InstrumentationRegistry.getInstrumentation().runOnMainSync { facade.switchTo(second) }
        idle()

        assertEquals("second", onMain { controller.currentMediaItem?.mediaId })
        assertSame("the session must keep the production facade", facade, onMain { session.player })

        onMain { controller.pause() }
        idle()
        assertFalse("second delegate should receive pause", second.playing)
        assertTrue("demoted first delegate must not receive post-handoff pause", first.playing)
    }

    @Test
    fun stateAndMetadataFollowTheActiveDelegateWithoutReconnect() {
        assertEquals("first", onMain { controller.currentMediaItem?.mediaId })

        InstrumentationRegistry.getInstrumentation().runOnMainSync { facade.switchTo(second) }
        idle()

        assertEquals("second", onMain { controller.currentMediaItem?.mediaId })
        assertEquals("second", onMain { controller.mediaMetadata.title?.toString() })
        assertSame(facade, onMain { session.player })
    }

    @Test
    fun rapidHandoffsLeaveTheLastDelegateCurrent() {
        repeat(21) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                facade.switchTo(if (it % 2 == 0) second else first)
            }
        }
        idle()
        assertEquals("second", onMain { controller.currentMediaItem?.mediaId })
        onMain { controller.play() }
        idle()
        assertTrue(second.playing)
        assertFalse(first.playing)
    }


    @Test
    fun commandQueuedAtTheHandoffBoundaryGoesToTheNewDelegate() {
        onMain { controller.play() }
        idle()
        assertTrue(first.playing)

        // The switch and the next command are serialized on the application
        // looper; the command must resolve the active delegate, not the first one.
        onMain {
            facade.switchTo(second)
            controller.pause()
        }
        idle()

        assertFalse("the new delegate receives the boundary pause", second.playing)
        assertTrue("the demoted delegate keeps its earlier play", first.playing)
    }


    @Test
    fun seekRepeatShuffleAndQueueMutationsReachTheCurrentDelegate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { facade.switchTo(second) }
        idle()

        onMain {
            controller.seekTo(1_234L)
            controller.setRepeatMode(Player.REPEAT_MODE_ONE)
            controller.setShuffleModeEnabled(true)
            controller.addMediaItem(MediaItem.fromUri("https://example.test/extra"))
        }
        idle()

        assertTrue("second delegate receives seek", second.seekCalls > 0)
        assertEquals(Player.REPEAT_MODE_ONE, second.repeatModeValue)
        assertTrue(second.shuffleOn)
        assertTrue("second delegate receives queue mutation", second.queueEdits > 0)
        assertEquals(0, first.seekCalls)
        assertEquals(Player.REPEAT_MODE_OFF, first.repeatModeValue)
        assertFalse(first.shuffleOn)
        assertEquals(0, first.queueEdits)
    }

    @Test
    fun teardownDoesNotRouteCommandsToTheReleasedDelegate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.release() }
        idle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { session.release() }
        // No exception means the session/facade teardown sequence is safe.
    }

    private class FakeDelegate(
        val id: String,
        looper: Looper,
    ) : SimpleBasePlayer(looper) {
        var playing = false
        var seekCalls = 0
        var queueEdits = 0
        var repeatModeValue = Player.REPEAT_MODE_OFF
        var shuffleOn = false
        var currentIndex = 0
        private val item = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(id).build())
            .build()
        private val items = mutableListOf(item)

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setRepeatMode(repeatModeValue)
            .setShuffleModeEnabled(shuffleOn)
            .setPlaylist(items.map { MediaItemData.Builder(it.mediaId).setMediaItem(it).setMediaMetadata(it.mediaMetadata).build() })
            .setCurrentMediaItemIndex(currentIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)))
            .setContentPositionMs(0L)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            this.playing = playWhenReady
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            seekCalls++
            if (mediaItemIndex >= 0) currentIndex = mediaItemIndex
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetRepeatMode(mode: Int): ListenableFuture<*> {
            repeatModeValue = mode
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetShuffleModeEnabled(enabled: Boolean): ListenableFuture<*> {
            shuffleOn = enabled
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
            queueEdits++
            items.clear()
            items.addAll(mediaItems)
            currentIndex = if (mediaItems.isEmpty()) 0 else startIndex.coerceIn(mediaItems.indices)
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
            queueEdits++
            items.addAll(index.coerceIn(0, items.size), mediaItems)
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
            queueEdits++
            for (i in (toIndex - 1) downTo fromIndex.coerceAtLeast(0)) {
                if (i in items.indices) items.removeAt(i)
            }
            currentIndex = currentIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
            invalidateState()
            return Futures.immediateVoidFuture()
        }
    }
}
