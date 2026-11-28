package com.music.bitchord.playback

import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The facade must hand each transport command to the matching method of the
 * delegate (a SessionPlayer in the app), not reduce it to seekTo(index, pos):
 * SessionPlayer gives next/previous/scrub/play their own party, Cast and
 * crossfade handling.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class StableSessionDispatchTest {

    /** Three items, all commands, nothing else. */
    private class Queue(looper: Looper) : SimpleBasePlayer(looper) {
        var index = 1
        var items = List(3) { MediaItem.Builder().setMediaId("t$it").build() }

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaybackState(if (items.isEmpty()) Player.STATE_IDLE else Player.STATE_READY)
            .setPlaylist(items.map { MediaItemData.Builder(it.mediaId).setMediaItem(it).build() })
            .setCurrentMediaItemIndex(if (items.isEmpty()) 0 else index.coerceIn(0, items.lastIndex))
            .setContentPositionMs(0L)
            .build()

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            if (mediaItemIndex >= 0) index = mediaItemIndex
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = Futures.immediateVoidFuture()

        override fun handleSetMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
            this.items = items
            index = if (startIndex == androidx.media3.common.C.INDEX_UNSET) 0 else startIndex
            invalidateState()
            return Futures.immediateVoidFuture()
        }
    }

    /** Records which entry point the facade used, like SessionPlayer's overrides would see. */
    private class Recorder(player: Player) : ForwardingPlayer(player) {
        val calls = mutableListOf<String>()
        override fun seekToNext() { calls += "seekToNext"; super.seekToNext() }
        override fun seekToNextMediaItem() { calls += "seekToNextMediaItem"; super.seekToNextMediaItem() }
        override fun seekToPrevious() { calls += "seekToPrevious"; super.seekToPrevious() }
        override fun seekToPreviousMediaItem() { calls += "seekToPreviousMediaItem"; super.seekToPreviousMediaItem() }
        override fun seekTo(positionMs: Long) { calls += "seekTo(pos)"; super.seekTo(positionMs) }
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) { calls += "seekTo(index)"; super.seekTo(mediaItemIndex, positionMs) }
        override fun play() { calls += "play"; super.play() }
        override fun pause() { calls += "pause"; super.pause() }
        override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
            calls += "setMediaItems(reset=$resetPosition)"; super.setMediaItems(mediaItems, resetPosition)
        }
        override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            calls += "setMediaItems(index)"; super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun transportCommandsReachTheMatchingDelegateMethod() {
        val recorder = onMain { Recorder(Queue(Looper.getMainLooper())) }
        val facade = onMain { StableSessionPlayer(recorder, Looper.getMainLooper()) }
        onMain {
            facade.seekToNext()
            facade.seekToPrevious()
            facade.seekToNextMediaItem()
            facade.seekToPreviousMediaItem()
            facade.seekTo(5_000L)
            facade.seekTo(2, 0L)
            facade.play()
            facade.pause()
        }
        assertEquals(
            listOf(
                "seekToNext", "seekToPrevious", "seekToNextMediaItem", "seekToPreviousMediaItem",
                "seekTo(pos)", "seekTo(index)", "play", "pause",
            ),
            recorder.calls,
        )
    }

    @Test
    fun resetPositionSetMediaItemsResetsInsteadOfKeepingTheOldIndex() {
        val queue = onMain { Queue(Looper.getMainLooper()).apply { index = 2 } }
        val recorder = onMain { Recorder(queue) }
        val facade = onMain { StableSessionPlayer(recorder, Looper.getMainLooper()) }
        val shorter = List(1) { MediaItem.Builder().setMediaId("new$it").build() }
        onMain { facade.setMediaItems(shorter, /* resetPosition = */ true) }
        assertEquals(listOf("setMediaItems(reset=true)"), recorder.calls)
        assertEquals(0, onMain { facade.currentMediaItemIndex })
    }
}
