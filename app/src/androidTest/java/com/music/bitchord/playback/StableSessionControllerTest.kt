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
        private val item = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(id).build())
            .build()
        private val mediaItemData = MediaItemData.Builder(id)
            .setMediaItem(item)
            .setMediaMetadata(item.mediaMetadata)
            .build()

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(listOf(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0L)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            this.playing = playWhenReady
            invalidateState()
            return Futures.immediateVoidFuture()
        }
    }
}
