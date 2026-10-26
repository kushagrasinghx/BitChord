package com.music.bitchord.ui.player

import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AsyncVideoLifecycleTest {
    private class Queue : Executor {
        private val work = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { work.addLast(command) }
        fun drain() { while (work.isNotEmpty()) work.removeFirst().run() }
    }

    private class Fixture {
        val queue = Queue()
        val events = mutableListOf<String>()
        val failures = mutableListOf<Exception>()
        var failOutput: String? = null
        val owner = AsyncVideoLifecycle(
            executor = queue,
            createPlayer = { events += "create"; Any() },
            setOutput = { _: Any, output: String? ->
                events += "set:$output"
                if (output != null && output == failOutput) error("output failed")
            },
            releasePlayer = { _: Any -> events += "release-player" },
            onFailure = { failures += it },
        )
        fun lease(name: String) = VideoOutputLease(
            createOutput = { events += "create:$name"; name },
            destroyOutput = { events += "release:$it" },
            destroyTexture = { events += "texture:$name" },
        )
    }

    @Test fun playerConstructionAndCommandsAreEnqueued() {
        val f = Fixture()
        f.owner.command { f.events += "prepare" }
        f.owner.command { f.events += "play" }
        assertTrue(f.events.isEmpty())
        f.queue.drain()
        assertEquals(listOf("create", "prepare", "play"), f.events)
    }

    @Test fun destroyedTextureSurvivesUntilDecoderDetaches() {
        val f = Fixture()
        val surface = f.lease("a")
        f.owner.attach(surface)
        f.queue.drain()
        f.events.clear()
        f.owner.detach(surface)
        assertTrue(f.events.isEmpty())
        f.queue.drain()
        assertEquals(listOf("set:null", "release:a", "texture:a"), f.events)
    }

    @Test fun oldDestructionDoesNotClearReplacementSurface() {
        val f = Fixture()
        val old = f.lease("a")
        val next = f.lease("b")
        f.owner.attach(old)
        f.queue.drain()
        f.events.clear()
        f.owner.attach(next)
        f.queue.drain()
        assertEquals(listOf("create:b", "set:b", "release:a"), f.events)
        f.events.clear()
        f.owner.detach(old)
        f.queue.drain()
        assertEquals(listOf("texture:a"), f.events)
    }

    @Test fun closeReleasesPlayerBeforeOutputButLeavesLiveTextureToView() {
        val f = Fixture()
        val surface = f.lease("a")
        f.owner.attach(surface)
        f.queue.drain()
        f.events.clear()
        f.owner.close()
        f.owner.close()
        assertTrue(f.owner.isClosed)
        assertTrue(f.events.isEmpty())
        f.queue.drain()
        assertEquals(listOf("release-player", "release:a"), f.events)
        f.owner.detach(surface)
        f.owner.detach(surface)
        f.queue.drain()
        assertEquals(listOf("release-player", "release:a", "texture:a"), f.events)
    }

    @Test fun closeCancelsPendingStartupWithoutAllocatingDecoderOrSurface() {
        val f = Fixture()
        val surface = f.lease("a")
        f.owner.command { fail("Canceled startup must not prepare") }
        f.owner.attach(surface)
        f.owner.close()
        f.owner.detach(surface)
        f.queue.drain()
        assertEquals(listOf("texture:a"), f.events)
    }

    @Test fun commandsAfterCloseCannotResurrectPlayer() {
        val f = Fixture()
        f.owner.close()
        f.owner.command { fail("Closed player must stay closed") }
        val surface = f.lease("late")
        f.owner.attach(surface)
        f.owner.detach(surface)
        f.queue.drain()
        assertEquals(listOf("texture:late"), f.events)
    }

    @Test fun failedSurfaceSwitchKeepsBothOutputsUntilPlayerReleased() {
        val f = Fixture()
        val old = f.lease("a")
        val next = f.lease("b")
        f.owner.attach(old)
        f.queue.drain()
        f.events.clear()
        f.failOutput = "b"
        f.owner.attach(next)
        f.queue.drain()
        assertEquals(listOf("create:b", "set:b", "release-player", "release:a", "release:b"), f.events)
        assertEquals(1, f.failures.size)
        assertTrue(f.owner.isClosed)
        f.owner.detach(old)
        f.owner.detach(next)
        f.queue.drain()
        assertEquals(listOf("texture:a", "texture:b"), f.events.takeLast(2))
    }

    @Test(timeout = 5_000) fun decoderWaitCannotBlockCallerOrFreeTextureEarly() {
        val executor = Executors.newSingleThreadExecutor()
        val detaching = CountDownLatch(1)
        val allowDetach = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val failures = Collections.synchronizedList(mutableListOf<Exception>())
        val owner = AsyncVideoLifecycle(
            executor = executor,
            createPlayer = { Any() },
            setOutput = { _: Any, output: String? ->
                if (output == null) {
                    events += "detach-start"
                    detaching.countDown()
                    check(allowDetach.await(3, TimeUnit.SECONDS))
                    events += "detach-done"
                }
            },
            releasePlayer = { _: Any -> events += "release-player" },
            onFailure = { failures += it },
        )
        val surface = VideoOutputLease(
            createOutput = { "surface" },
            destroyOutput = { _: String -> events += "release-surface" },
            destroyTexture = { events += "release-texture"; completed.countDown() },
        )
        try {
            owner.attach(surface)
            owner.detach(surface)
            assertTrue(detaching.await(2, TimeUnit.SECONDS))
            owner.close() // Must return while the decoder is still blocked.
            assertEquals(listOf("detach-start"), events.toList())
            allowDetach.countDown()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
        } finally {
            allowDetach.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
        assertEquals(listOf("detach-start", "detach-done", "release-surface", "release-texture", "release-player"), events)
        assertTrue(failures.isEmpty())
    }
}
