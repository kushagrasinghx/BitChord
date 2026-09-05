package com.music.bitchord

import com.music.bitchord.data.sources.module.SharedCalls
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The contract that keeps a module's server from being asked the same question
 * over and over — see [SharedCalls] for the traffic that prompted it.
 */
class SharedCallsTest {

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun <T> shared(
        ttlMs: Long,
        now: () -> Long = System::currentTimeMillis,
        onReuse: () -> Unit = {},
    ) = SharedCalls<T>(ttlMs = ttlMs, scope = scope(), log = { onReuse() }, now = now)

    @Test
    fun `a repeated question inside the window costs one call`() = runBlocking {
        val calls = AtomicInteger()
        val shared = shared<String>(ttlMs = 60_000)

        repeat(5) {
            val answer = shared.get("q", { "" }) {
                calls.incrementAndGet()
                Result.success("flac")
            }
            assertEquals("flac", answer.getOrNull())
        }
        assertEquals(1, calls.get())
    }

    @Test
    fun `an empty answer is kept, because it is an answer`() = runBlocking {
        val calls = AtomicInteger()
        val shared = shared<List<String>>(ttlMs = 60_000)

        repeat(3) {
            shared.get("missing", { "" }) {
                calls.incrementAndGet()
                Result.success(emptyList())
            }
        }
        // "This catalogue does not hold that recording" is exactly the answer
        // the second look and the follow-up pass would otherwise re-ask for.
        assertEquals(1, calls.get())
    }

    @Test
    fun `a failure is not kept`() = runBlocking {
        val calls = AtomicInteger()
        val shared = shared<String>(ttlMs = 60_000)

        repeat(3) {
            shared.get("q", { "" }) {
                calls.incrementAndGet()
                Result.failure(IllegalStateException("server having a bad minute"))
            }
        }
        assertEquals(3, calls.get())
    }

    @Test
    fun `an expired answer is asked again`() = runBlocking {
        val calls = AtomicInteger()
        var clock = 0L
        val shared = shared<String>(ttlMs = 1_000, now = { clock })

        shared.get("q", { "" }) { calls.incrementAndGet(); Result.success("a") }
        clock = 999
        shared.get("q", { "" }) { calls.incrementAndGet(); Result.success("a") }
        assertEquals(1, calls.get())

        clock = 1_001
        shared.get("q", { "" }) { calls.incrementAndGet(); Result.success("a") }
        assertEquals(2, calls.get())
    }

    @Test
    fun `callers arriving mid-flight share the one call`() = runBlocking {
        val calls = AtomicInteger()
        val joined = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        // ttl 0, so nothing is kept once the call completes and only the
        // in-flight sharing can account for the count below.
        val shared = shared<String>(ttlMs = 0, onReuse = { joined.incrementAndGet() })

        val waiters: List<Deferred<Result<String>>> = List(4) {
            async(Dispatchers.Default) {
                shared.get("q", { "" }) {
                    calls.incrementAndGet()
                    release.await()
                    Result.success("flac")
                }
            }
        }
        // Every one of the other three has to have *joined* the running call
        // before it is allowed to finish. Releasing on the first producer alone
        // is a race: with ttl 0 the window shuts the moment the work completes,
        // so a straggler arriving after that legitimately starts its own.
        while (joined.get() < 3) yield()
        release.complete(Unit)

        assertTrue(waiters.map { it.await() }.all { it.getOrNull() == "flac" })
        assertEquals(1, calls.get())
    }

    /**
     * The behaviour the module's operator was actually complaining about: this
     * app cancels losing lookups routinely, and a cancelled caller used to take
     * the request down with it *after* the server had already been asked.
     */
    @Test
    fun `a cancelled caller does not kill the call others still want`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val shared = shared<String>(ttlMs = 60_000)

        val abandoning = scope().launch {
            shared.get("q", { "" }) {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                Result.success("flac")
            }
        }
        started.await()
        abandoning.cancelAndJoin()
        release.complete(Unit)

        // The abandoned request finished into the cache, so the next caller —
        // which in the app is the second look, moments later — pays nothing.
        val answer = withTimeout(5_000) {
            shared.get("q", { "" }) { Result.success("never asked") }
        }
        assertEquals("flac", answer.getOrNull())
        assertEquals(1, calls.get())
    }

    @Test
    fun `held answers stay bounded`() = runBlocking {
        val shared = shared<String>(ttlMs = 60_000)
        repeat(400) { at -> shared.get("q$at", { "" }) { Result.success("a") } }
        assertTrue("held ${shared.size()}", shared.size() <= 128)
    }
}
