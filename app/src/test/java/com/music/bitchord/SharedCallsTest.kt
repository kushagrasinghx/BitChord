package com.music.bitchord

import com.music.bitchord.data.sources.module.SharedCalls
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
    fun `an explicit refresh drops a completed answer`() = runBlocking {
        val calls = AtomicInteger()
        val shared = shared<String>(ttlMs = 60_000)

        shared.get("q", { "" }) { Result.success("answer-${calls.incrementAndGet()}") }
        shared.clearCompleted()
        val refreshed = shared.get("q", { "" }) {
            Result.success("answer-${calls.incrementAndGet()}")
        }

        assertEquals("answer-2", refreshed.getOrNull())
        assertEquals(2, calls.get())
    }

    @Test
    fun `an explicit refresh keeps a request already in flight`() = runBlocking {
        val calls = AtomicInteger()
        val joined = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val shared = shared<String>(ttlMs = 60_000, onReuse = { joined.incrementAndGet() })

        val first = async(Dispatchers.Default) {
            shared.get("q", { "" }) {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                Result.success("flac")
            }
        }
        started.await()
        shared.clearCompleted()
        val second = async(Dispatchers.Default) {
            shared.get("q", { "" }) {
                calls.incrementAndGet()
                Result.success("duplicate")
            }
        }
        while (joined.get() < 1) yield()
        release.complete(Unit)

        assertEquals("flac", first.await().getOrNull())
        assertEquals("flac", second.await().getOrNull())
        assertEquals(1, calls.get())
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

    /**
     * The other half of the test above, and the failure that one does not
     * cover: the abandoned request *fails*.
     *
     * A caller that is cancelled never runs the cleanup at the end of [SharedCalls.get],
     * but the request it started keeps running in the shared scope — that is
     * the point of the scope. When it comes back with a failure there is then
     * nobody left to drop the entry, and the next caller reads that failure as
     * the answer for the whole window. Seen in the field as a `CACHE HIT`
     * logged in the same millisecond as a timeout: one bad minute at an addon
     * turned into ten minutes of instant failures for that query.
     */
    @Test
    fun `a failure is not kept when its only caller was cancelled`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val shared = shared<String>(ttlMs = 60_000)

        val abandoning = scope().launch {
            shared.get("q", { "" }) {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                Result.failure(IllegalStateException("timeout"))
            }
        }
        started.await()
        abandoning.cancelAndJoin()
        release.complete(Unit)

        // Nothing may be left holding that failure: the next caller has to be
        // able to ask again rather than be handed the same answer.
        withTimeout(5_000) {
            while (shared.size() != 0) delay(10)
        }
        val answer = shared.get("q", { "" }) {
            calls.incrementAndGet()
            Result.success("flac")
        }
        assertEquals("flac", answer.getOrNull())
        assertEquals(2, calls.get())
    }

    @Test
    fun `held answers stay bounded`() = runBlocking {
        val shared = shared<String>(ttlMs = 60_000)
        repeat(400) { at -> shared.get("q$at", { "" }) { Result.success("a") } }
        assertTrue("held ${shared.size()}", shared.size() <= 128)
    }

    /**
     * The incident this fix came from, with the track that produced it.
     *
     * ```
     * 07:37:01.321  the addon call starts for 'linda louca e mimada oriente'
     * 07:37:01.139  sound starts on the YouTube fallback, so the caller that
     *               started the request stops waiting - by design, and by
     *               cancellation
     * 07:37:20.511  addon search ... CACHE HIT
     *               the second look arrives and joins the request on the wire
     * 07:37:46.322  addon call failed: timeout (45 s ceiling)
     * ```
     *
     * Before the fix that failure stayed in the map for the whole ten-minute
     * TTL: every later lookup for the same query was answered from it without a
     * request leaving the device, which is the `CACHE HIT` logged beside a
     * timeout. After it, the failure is dropped where the *work* settles and the
     * next caller asks the addon again.
     */
    @Test
    fun `the Oriente search is asked again after the request died with its starter gone`() = runBlocking {
        val query = "linda louca e mimada oriente"
        val calls = AtomicInteger()
        val joined = AtomicInteger()
        val onTheWire = CompletableDeferred<Unit>()
        val ceiling = CompletableDeferred<Unit>()
        val shared = shared<String>(ttlMs = 10 * 60_000, onReuse = { joined.incrementAndGet() })

        // The live resolve asks, then gives up: sound is already playing on the
        // fallback, and a caller in this layer is meant to walk away.
        val live = async(Dispatchers.Default) {
            shared.get(query, { "addon search q=\"$query\"" }) {
                calls.incrementAndGet()
                onTheWire.complete(Unit)
                ceiling.await()
                Result.failure(IllegalStateException("timeout"))
            }
        }
        onTheWire.await()
        live.cancelAndJoin()

        // The second look arrives while the request is still running and joins
        // it. This is the caller still inside await() when the ceiling fires.
        val secondLook = async(Dispatchers.Default) {
            shared.get(query, { "addon search q=\"$query\"" }) {
                calls.incrementAndGet()
                Result.success("a second request, which would be a duplicate")
            }
        }
        while (joined.get() < 1) yield()
        ceiling.complete(Unit)

        assertTrue("the joined caller sees the failure", secondLook.await().isFailure)
        assertEquals("one request on the wire, not two", 1, calls.get())

        // Dropped where the work settles, not where a caller returns; give that
        // handler its turn. Without the fix the entry outlives the TTL below.
        withTimeout(5_000) { while (shared.size() != 0) delay(10) }

        // A replay, or the next track that asks the same question. A failure is
        // not an answer, so this asks the addon again.
        val answer = shared.get(query, { "addon search q=\"$query\"" }) {
            calls.incrementAndGet()
            Result.success("flac tidal:track:57852175")
        }
        assertEquals("flac tidal:track:57852175", answer.getOrNull())
        assertEquals("the failure did not become the answer", 2, calls.get())
    }
}
