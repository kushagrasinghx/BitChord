package com.music.bitchord

import android.content.ContextWrapper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.applemusic.AmPlayer
import com.music.bitchord.playback.applemusic.AmPlayerFactory
import com.music.bitchord.playback.applemusic.AppleMusicSpeaker
import com.music.bitchord.playback.applemusic.AppleMusicQueuePolicy
import com.music.bitchord.playback.cast.CastSink
import com.music.bitchord.playback.cast.NoSpeaker
import com.music.bitchord.playback.cast.RemoteSpeaker
import com.music.bitchord.playback.cast.Speakers
import java.lang.reflect.Proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AppleMusicSpeakerTest {
    private class FakeAmPlayer : AmPlayer {
        override var available = true
        override var listener: AmPlayer.Listener? = null
        override var positionMs = C.TIME_UNSET
        val loads = mutableListOf<AmPlayer.Request>()
        var plays = 0
        var pauses = 0
        var stops = 0
        var releases = 0
        var seek = 0L
        var loadAction: suspend () -> Unit = {}
        var throwOnPlay = false

        override suspend fun load(request: AmPlayer.Request) {
            loads += request
            loadAction()
            state(playing = false)
        }
        override fun play() {
            if (throwOnPlay) error("transport failed")
            plays++
            state(playing = true)
        }
        override fun pause() { pauses++; state(playing = false) }
        override fun seekTo(positionMs: Long) {
            seek = positionMs
            // SDKs can synchronously report their paused preparation here.
            state(playing = false, position = positionMs)
        }
        override fun stop() { stops++ }
        override fun release() { releases++ }
        fun state(playing: Boolean, position: Long = seek) {
            listener?.onStateChanged(AmPlayer.Snapshot(loads.last().requestId,
                playWhenReady = playing, isPlaying = playing, positionMs = position, durationMs = 180_000))
        }
        fun end(request: Long = loads.last().requestId) {
            listener?.onStateChanged(AmPlayer.Snapshot(request, Player.STATE_ENDED,
                positionMs = 180_000, durationMs = 180_000))
        }
        fun error(request: Long = loads.last().requestId) { listener?.onError(request) }
    }

    private class Queue(vararg ids: String) {
        val items = ids.map { MediaItem.Builder().setMediaId(it).build() }.toMutableList()
        var index = 0
        var wantsPlay = true
        var position = 0L
        var repeat = Player.REPEAT_MODE_OFF
        var nextCalls = 0
        var prepares = 0
        var starts = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> items.getOrNull(index)
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args!![0] as Int]
                "getCurrentMediaItemIndex" -> index
                "getCurrentTimeline" -> Timeline.EMPTY
                "getShuffleModeEnabled" -> false
                "getPlayWhenReady" -> wantsPlay
                "getPlaybackState" -> Player.STATE_IDLE
                "getCurrentPosition" -> position
                "getRepeatMode" -> repeat
                "hasNextMediaItem" -> index < items.lastIndex ||
                    (repeat == Player.REPEAT_MODE_ALL && items.isNotEmpty())
                "pause" -> { wantsPlay = false; null }
                "play" -> { starts++; wantsPlay = true; null }
                "setPlayWhenReady" -> { wantsPlay = args!![0] as Boolean; if (wantsPlay) starts++; null }
                "prepare" -> { prepares++; null }
                "seekTo" -> {
                    if (args!!.size == 2) { index = args[0] as Int; position = args[1] as Long }
                    else position = args[0] as Long
                    null
                }
                "seekToNextMediaItem" -> {
                    nextCalls++
                    index = if (index < items.lastIndex) index + 1 else 0
                    position = 0
                    null
                }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }

    private class Fixture(scope: CoroutineScope, vararg ids: String, sdkPresent: Boolean = true,
        timeout: Long = 2_500) {
        val queue = Queue(*ids)
        val am = FakeAmPlayer()
        var config = AppleMusicSpeaker.Settings(true, "user", "us")
        var cast = false
        var watch = false
        var matchCalls = 0
        val matchStarted = CompletableDeferred<Unit>()
        var matching: suspend (Song) -> String? = { "123" }
        val prefetched = mutableListOf<List<String>>()
        val failures = mutableListOf<Boolean>()
        val failed = CompletableDeferred<Unit>()
        var routes = 0
        var changes = 0
        val speaker = AppleMusicSpeaker(scope, { queue.player }, am.takeIf { sdkPresent },
            settings = { config }, higherPriorityActive = { cast || watch },
            developerToken = { "backend-only-developer-token" },
            match = { song, _ -> matchCalls++; matchStarted.complete(Unit); matching(song) },
            prefetch = { songs, _ -> prefetched += songs.map { it.videoId } },
            matchTimeoutMs = timeout,
            onFailure = { failures += it; failed.complete(Unit) },
            songOf = { Song(it.mediaId, "Title", "Artist", null) })
        init {
            speaker.sink = object : CastSink {
                override fun onRemoteRouteChanged() { routes++ }
                override fun onRemotePlaybackChanged() { changes++ }
                override fun onRemoteVolumeChanged() = Unit
            }
        }
        fun transition() = speaker.onLocalTransition(queue.player.currentMediaItem,
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        suspend fun settle() { repeat(5) { yield() } }
    }

    @Test fun `unregistered player factory is unavailable without probing an SDK`() {
        assertNull(AmPlayerFactory.create(ContextWrapper(null)))
    }

    @Test fun `claims buffering before matching and never starts the local audio`() = runBlocking {
        val f = Fixture(this, "yt", "next1", "next2", "next3", "next4")
        val answer = CompletableDeferred<String?>()
        f.matching = { answer.await() }
        f.transition()
        assertTrue(f.speaker.active)
        assertEquals(Player.STATE_BUFFERING, f.speaker.playbackState)
        assertFalse(f.queue.wantsPlay)
        assertTrue(f.speaker.holdsLocalAudio("yt"))
        assertTrue(f.speaker.holdsLocalAudio("next1"))
        f.settle()
        answer.complete("456")
        f.settle()
        assertEquals("456", f.am.loads.single().catalogId)
        assertTrue(f.speaker.isPlaying)
        assertEquals(1, f.am.plays)
        assertEquals(0, f.queue.starts)
        assertEquals(listOf("next1", "next2", "next3"), f.prefetched.last())
        assertTrue(f.routes > 0 && f.changes > 0)
        f.speaker.release()
    }

    @Test fun `activation requires real transport selected provider user token and storefront`() = runBlocking {
        val configs = listOf(
            AppleMusicSpeaker.Settings(false, "user", "us"),
            AppleMusicSpeaker.Settings(true, null, "us"),
            AppleMusicSpeaker.Settings(true, "", "us"),
            AppleMusicSpeaker.Settings(true, "user", null),
            AppleMusicSpeaker.Settings(true, "user", "invalid"),
        )
        for (config in configs) {
            val f = Fixture(this, "yt")
            f.config = config
            f.transition(); f.settle()
            assertFalse(f.speaker.active)
            assertTrue(f.am.loads.isEmpty())
            f.speaker.release()
        }
        val absent = Fixture(this, "yt", sdkPresent = false)
        absent.transition(); absent.settle()
        assertFalse(absent.speaker.active)
        assertFalse(absent.speaker.holdsLocalAudio("yt"))
        assertTrue(absent.queue.wantsPlay)
        assertEquals(0, absent.matchCalls)
        absent.speaker.release()
    }

    @Test fun `Cast and watch outrank Apple and suppress a pending or active load`() = runBlocking {
        for (useCast in listOf(true, false)) {
            val f = Fixture(this, "yt")
            f.transition(); f.settle()
            f.am.state(true, 19_000)
            if (useCast) f.cast = true else f.watch = true
            f.speaker.refresh()
            assertFalse(f.speaker.active)
            assertFalse(f.speaker.holdsLocalAudio("yt"))
            assertEquals(19_000, f.queue.position)
            assertFalse(f.queue.wantsPlay)
            val stops = f.am.stops
            f.am.end(); f.am.error()
            assertEquals(0, f.queue.nextCalls)
            assertEquals(stops, f.am.stops)
            f.cast = false; f.watch = false
            f.speaker.refresh(); f.settle()
            assertTrue(f.speaker.active)
            assertFalse(f.speaker.playWhenReady) // No unsolicited playback on receiver disconnect.
            f.speaker.release()
        }
        val f = Fixture(this, "yt")
        f.cast = true
        f.transition(); f.settle()
        assertFalse(f.speaker.active)
        assertEquals(0, f.matchCalls)
        f.speaker.release()
    }

    @Test fun `handoff transfers live clock and play intent before Cast or watch samples ExoPlayer`() = runBlocking {
        val f = Fixture(this, "yt", "next")
        f.transition(); f.settle()
        f.am.positionMs = 31_234
        val old = f.am.loads.last().requestId
        val handoff = f.speaker.handoffToRemote()!!
        assertEquals(31_234, handoff.positionMs)
        assertTrue(handoff.playWhenReady)
        assertEquals(31_234, f.queue.position)
        assertFalse(f.speaker.active)
        assertFalse(f.speaker.holdsLocalAudio("yt"))
        f.cast = true
        f.speaker.refresh()
        f.am.end(old); f.am.error(old)
        assertEquals(1, f.am.loads.size)
        assertEquals(0, f.queue.nextCalls)
        f.speaker.release()
    }

    @Test fun `transport forwards play pause seek and the live clock but not receiver volume`() = runBlocking {
        val f = Fixture(this, "yt")
        f.transition(); f.settle()
        assertTrue(f.speaker.isLocalOutput)
        f.speaker.pause()
        assertFalse(f.speaker.playWhenReady)
        f.speaker.seekTo(-10)
        assertEquals(0, f.am.seek)
        f.speaker.seekTo(28_000)
        assertEquals(28_000, f.am.seek)
        f.speaker.play()
        assertTrue(f.speaker.isPlaying)
        f.am.positionMs = 29_321
        assertEquals(29_321, f.speaker.positionMs)
        f.speaker.setVolume(0.1f)
        assertEquals(0, f.queue.starts)
        f.speaker.release()
    }

    @Test fun `pause and seek during loading survive paused SDK preparation callbacks`() = runBlocking {
        val f = Fixture(this, "yt")
        val ready = CompletableDeferred<Unit>()
        f.am.loadAction = { ready.await() }
        f.transition(); f.settle()
        f.speaker.pause(); f.speaker.seekTo(42_000)
        ready.complete(Unit); f.settle()
        assertEquals(42_000, f.am.seek)
        assertEquals(0, f.am.plays)
        assertFalse(f.speaker.playWhenReady)
        f.speaker.play()
        assertEquals(1, f.am.plays)
        f.speaker.release()
    }

    @Test fun `end advances the authoritative queue and ignores duplicate and stale callbacks`() = runBlocking {
        val f = Fixture(this, "one", "two")
        f.transition(); f.settle()
        val old = f.am.loads.last().requestId
        f.am.end(old); f.settle()
        assertEquals(1, f.queue.index)
        assertEquals(1, f.queue.nextCalls)
        assertEquals(2, f.am.loads.size)
        assertTrue(f.speaker.isPlaying)
        f.am.end(old); f.am.error(old)
        assertEquals(1, f.queue.nextCalls)
        f.am.end(); f.settle()
        assertEquals(Player.STATE_ENDED, f.speaker.playbackState)
        assertFalse(f.speaker.playWhenReady)
        assertTrue(f.speaker.active)
        f.speaker.release()
    }

    @Test fun `repeat one reloads only current song and repeat all wraps last queue item`() = runBlocking {
        val one = Fixture(this, "one", "two")
        one.queue.repeat = Player.REPEAT_MODE_ONE
        one.transition(); one.settle()
        one.am.end(); one.settle()
        assertEquals(0, one.queue.index)
        assertEquals(0, one.queue.nextCalls)
        assertEquals(2, one.am.loads.size)
        assertEquals(0, one.am.loads.last().positionMs)
        assertTrue(one.speaker.isPlaying)
        one.speaker.release()
        val all = Fixture(this, "one", "two")
        all.queue.index = 1
        all.queue.repeat = Player.REPEAT_MODE_ALL
        all.transition(); all.settle()
        all.am.end(); all.settle()
        assertEquals(0, all.queue.index)
        assertEquals(2, all.am.loads.size)
        assertTrue(all.speaker.isPlaying)
        all.speaker.release()
    }

    @Test fun `repeat all restarts a single item even when Media3 reports a next item`() = runBlocking {
        val f = Fixture(this, "only")
        f.queue.repeat = Player.REPEAT_MODE_ALL
        f.transition(); f.settle()
        assertTrue(f.queue.player.hasNextMediaItem())
        f.am.end(); f.settle()
        assertEquals(2, f.am.loads.size)
        assertEquals(0, f.queue.nextCalls)
        assertTrue(f.speaker.isPlaying)
        f.speaker.release()
    }

    @Test fun `consecutive duplicate queue ids still load each queue entry`() = runBlocking {
        val f = Fixture(this, "same", "same")
        f.transition(); f.settle()
        f.am.end(); f.settle()
        assertEquals(1, f.queue.index)
        assertEquals(2, f.am.loads.size)
        f.speaker.release()
    }

    @Test fun `match miss and player error respect fallback off and on`() = runBlocking {
        for (fallback in listOf(false, true)) {
            for (miss in listOf(false, true)) {
                val f = Fixture(this, "yt", "next")
                f.config = f.config.copy(fallback = fallback)
                if (miss) f.matching = { null }
                f.transition(); f.settle()
                if (!miss) f.am.error()
                f.settle()
                assertEquals(listOf(fallback), f.failures)
                assertEquals(!fallback, f.speaker.active)
                assertEquals(fallback, f.queue.wantsPlay)
                assertEquals(if (fallback) 1 else 0, f.queue.starts)
                assertEquals(!fallback, f.speaker.holdsLocalAudio("yt"))
                assertEquals(!fallback, f.speaker.playerError != null)
                // Edits and delayed state do not reclaim a fallback track.
                f.speaker.onLocalQueueChanged()
                if (!miss) f.am.end()
                f.settle()
                assertEquals(listOf(fallback), f.failures)
                f.speaker.release()
            }
        }
    }

    @Test fun `timeout falls back but caller cancellation is never treated as a miss`() = runBlocking {
        val timeout = Fixture(this, "yt", timeout = 20)
        timeout.matching = { awaitCancellation() }
        timeout.transition()
        withTimeout(2_000) { timeout.failed.await() }
        assertFalse(timeout.speaker.active)
        assertEquals(listOf(true), timeout.failures)
        timeout.speaker.release()
        val cancelled = Fixture(this, "yt")
        val observed = CompletableDeferred<Unit>()
        cancelled.matching = {
            try { awaitCancellation() } finally { observed.complete(Unit) }
        }
        cancelled.transition()
        cancelled.matchStarted.await()
        cancelled.speaker.release()
        withTimeout(2_000) { observed.await() }
        assertTrue(cancelled.failures.isEmpty())
        assertTrue(cancelled.am.loads.isEmpty())
    }

    @Test fun `disconnect cancels lookup and stale noncooperative match cannot load or fallback`() = runBlocking {
        val f = Fixture(this, "old", "new")
        val late = CompletableDeferred<String?>()
        f.matching = { withContext(NonCancellable) { late.await() } }
        f.transition(); f.matchStarted.await()
        f.config = f.config.copy(selected = false, userToken = null, storefront = null)
        f.speaker.refresh()
        late.complete("789"); f.settle()
        assertFalse(f.speaker.active)
        assertTrue(f.am.loads.isEmpty())
        assertTrue(f.failures.isEmpty())
        assertTrue(f.queue.wantsPlay)
        f.speaker.release()
    }

    @Test fun `queue transition cancels old lookup and ignores its eventual result`() = runBlocking {
        val f = Fixture(this, "old", "new")
        val late = CompletableDeferred<String?>()
        f.matching = { song -> if (song.videoId == "old") withContext(NonCancellable) { late.await() } else "222" }
        f.transition(); f.matchStarted.await()
        f.queue.index = 1
        f.transition(); f.settle()
        late.complete("111"); f.settle()
        assertEquals(listOf("222"), f.am.loads.map { it.catalogId })
        assertTrue(f.failures.isEmpty())
        f.speaker.release()
    }

    @Test fun `direct catalog rows bypass matcher and never use YouTube even on error`() = runBlocking {
        val f = Fixture(this, "am-miss:12345")
        f.transition(); f.settle()
        assertEquals("12345", f.am.loads.single().catalogId)
        assertEquals(0, f.matchCalls)
        f.am.error(); f.settle()
        assertTrue(f.speaker.active)
        assertNotNull(f.speaker.playerError)
        assertFalse(f.queue.wantsPlay)
        assertEquals(0, f.queue.starts)
        f.speaker.release()
        val pending = Fixture(this, "am:library-id")
        pending.transition(); pending.settle()
        assertTrue(pending.am.loads.isEmpty())
        assertEquals(0, pending.matchCalls)
        assertNotNull(pending.speaker.playerError)
        assertFalse(pending.queue.wantsPlay)
        pending.speaker.release()
        val invalid = Fixture(this, "am-miss:library-upload")
        invalid.transition(); invalid.settle()
        assertEquals(0, invalid.matchCalls)
        assertTrue(invalid.am.loads.isEmpty())
        assertNotNull(invalid.speaker.playerError)
        invalid.speaker.release()
    }

    @Test fun `runtime transport failure falls back without restoring a paused user intent`() = runBlocking {
        val f = Fixture(this, "yt")
        f.transition(); f.settle()
        f.speaker.pause()
        f.am.error(); f.settle()
        assertFalse(f.queue.wantsPlay)
        assertFalse(f.speaker.active)
        f.speaker.release()
        val transport = Fixture(this, "yt")
        transport.am.throwOnPlay = true
        transport.transition(); transport.settle()
        assertFalse(transport.speaker.active)
        assertEquals(listOf(true), transport.failures)
        transport.speaker.release()
    }

    @Test fun `release stops transport clears callbacks and is idempotent`() = runBlocking {
        val f = Fixture(this, "yt", "next")
        f.transition(); f.settle()
        val listener = f.am.listener!!
        val old = f.am.loads.last().requestId
        f.speaker.release(); f.speaker.release()
        assertFalse(f.speaker.active)
        assertFalse(f.speaker.holdsLocalAudio("yt"))
        assertNull(f.am.listener)
        assertEquals(1, f.am.releases)
        listener.onError(old)
        listener.onStateChanged(AmPlayer.Snapshot(old, Player.STATE_ENDED))
        assertEquals(0, f.queue.nextCalls)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun `Cast arriving during lookup cancels it without a late load or fallback`() = runBlocking {
        val f = Fixture(this, "yt")
        val late = CompletableDeferred<String?>()
        f.matching = { withContext(NonCancellable) { late.await() } }
        f.transition(); f.matchStarted.await()
        f.cast = true; f.speaker.refresh()
        late.complete("123"); f.settle()
        assertFalse(f.speaker.active)
        assertTrue(f.am.loads.isEmpty())
        assertTrue(f.failures.isEmpty())
        f.speaker.release()
    }

    @Test fun `account switch cancels suspended SDK preparation and ignores old callbacks`() = runBlocking {
        val f = Fixture(this, "yt")
        val preparing = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        f.am.loadAction = {
            preparing.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        f.transition(); preparing.await()
        val old = f.am.loads.last().requestId
        f.am.loadAction = {}
        f.config = f.config.copy(userToken = "other-user", storefront = "gb")
        f.speaker.refresh(); cancelled.await(); f.settle()
        assertEquals("other-user", f.am.loads.last().userToken)
        assertEquals("gb", f.am.loads.last().storefront)
        f.am.error(old); f.am.end(old)
        assertTrue(f.failures.isEmpty())
        assertTrue(f.speaker.isPlaying)
        f.speaker.release()
    }

    @Test fun `queue clear cancels lookup and stops rather than falling back`() = runBlocking {
        val f = Fixture(this, "yt")
        val cancelled = CompletableDeferred<Unit>()
        f.matching = { try { awaitCancellation() } finally { cancelled.complete(Unit) } }
        f.transition(); f.matchStarted.await()
        f.queue.items.clear()
        f.speaker.onLocalQueueChanged(); cancelled.await(); f.settle()
        assertFalse(f.speaker.active)
        assertTrue(f.am.loads.isEmpty())
        assertTrue(f.failures.isEmpty())
        f.speaker.release()
    }

    @Test fun `Apple-only recommendations use the closest real YouTube seed or make no request`() {
        fun song(id: String) = Song(id, "Title", "Artist", null)
        val direct = song("am-miss:123")
        val before = song("abcdefghijk")
        val after = song("12345678901")
        val queue = listOf(before, song("am:pending"), direct, after)
        assertEquals(after, AppleMusicQueuePolicy.recommendationSeed(direct, queue, 2))
        assertEquals(before, AppleMusicQueuePolicy.recommendationSeed(before, queue, 0))
        assertNull(AppleMusicQueuePolicy.recommendationSeed(direct, listOf(direct), 0))
    }

    @Test fun `speaker aggregation preserves default TV compatibility and local output flag`() = runBlocking {
        val f = Fixture(this, "yt")
        val receiver = object : RemoteSpeaker by NoSpeaker { override var active = false }
        val all = Speakers(listOf(receiver, f.speaker), onRouteChanged = { f.speaker.refresh() })
        all.sink = object : CastSink {
            override fun onRemoteRouteChanged() = Unit
            override fun onRemotePlaybackChanged() = Unit
            override fun onRemoteVolumeChanged() = Unit
        }
        f.transition(); f.settle()
        assertTrue(all.isLocalOutput)
        receiver.active = true; f.cast = true
        receiver.sink?.onRemoteRouteChanged()
        assertFalse(all.isLocalOutput)
        assertFalse(f.speaker.active)
        assertFalse(NoSpeaker.isLocalOutput)
        assertNull(NoSpeaker.playerError)
        all.sink = null
        f.speaker.release()
    }
}
