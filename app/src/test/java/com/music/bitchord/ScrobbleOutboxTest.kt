package com.music.bitchord

import com.music.bitchord.data.scrobbling.PendingScrobble
import com.music.bitchord.data.scrobbling.ScrobbleDelivery
import com.music.bitchord.data.scrobbling.ScrobbleOutbox
import com.music.bitchord.data.scrobbling.ScrobbleProvider
import com.music.bitchord.data.scrobbling.ScrobbleStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The durable half of scrobbling: what is kept, for how long, and for whom.
 *
 * Every test drives the real [ScrobbleOutbox] with a fake store, a fake clock
 * and a scripted service, because the bugs that matter here are all about
 * time and identity rather than about HTTP.
 */
class ScrobbleOutboxTest {
    private class FakeStore(
        var text: String? = null,
    ) : ScrobbleStore {
        var writes = 0

        override fun read(): String? = text

        override fun write(text: String) {
            this.text = text
            writes++
        }
    }

    private fun event(
        track: String = "Track",
        provider: ScrobbleProvider = ScrobbleProvider.LASTFM,
        account: String = "lastfm:alice",
        timestamp: Long = 1_700_000_000L,
    ) = PendingScrobble(
        provider = provider,
        account = account,
        artist = "Artist",
        track = track,
        album = "Album",
        durationSeconds = 210,
        timestampSeconds = timestamp,
        listenedAtSeconds = timestamp,
        startMs = timestamp * 1000L,
        endMs = timestamp * 1000L,
    )

    private fun box(
        store: FakeStore,
        now: () -> Long = { 0L },
        capacity: Int = 100,
        maxAttempts: Int = 4,
    ) = ScrobbleOutbox(
        store = store,
        clock = now,
        capacity = capacity,
        baseBackoffMs = 1_000L,
        maxBackoffMs = 60_000L,
        maxAttempts = maxAttempts,
        jitter = { 0L },
    )

    @Test
    fun `a sent listen leaves the queue`() =
        runTest {
            val store = FakeStore()
            val outbox = box(store)
            assertTrue(outbox.enqueue(event()))

            val result = outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Sent }

            assertEquals(1, result.sent)
            assertEquals(0, result.remaining)
            assertEquals(0, outbox.pendingCount())
        }

    @Test
    fun `an offline listen survives a new instance`() =
        runTest {
            val store = FakeStore()
            box(store).enqueue(event())

            val reopened = box(store)

            assertEquals(1, reopened.pendingCount())
            assertEquals("Track", reopened.snapshot().single().track)
        }

    @Test
    fun `a transient failure keeps the listen until it is due again`() =
        runTest {
            var now = 0L
            val store = FakeStore()
            val outbox = box(store, now = { now })
            outbox.enqueue(event())

            outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Transient("offline") }
            assertEquals(1, outbox.pendingCount())

            now = 500L
            assertEquals(0, outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Sent }.sent)

            now = 1_001L
            assertEquals(1, outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Sent }.sent)
            assertEquals(0, outbox.pendingCount())
        }

    @Test
    fun `the same play queued twice is stored once`() =
        runTest {
            val outbox = box(FakeStore())

            assertTrue(outbox.enqueue(event()))
            assertFalse(outbox.enqueue(event()))

            assertEquals(1, outbox.pendingCount())
        }

    @Test
    fun `the same track played again is a second listen`() =
        runTest {
            val outbox = box(FakeStore())

            assertTrue(outbox.enqueue(event(timestamp = 1_700_000_000L)))
            assertTrue(outbox.enqueue(event(timestamp = 1_700_000_300L)))

            assertEquals(2, outbox.pendingCount())
        }

    @Test
    fun `backoff doubles and is capped`() {
        val outbox = box(FakeStore())

        assertEquals(1_000L, outbox.backoffMs(1))
        assertEquals(2_000L, outbox.backoffMs(2))
        assertEquals(4_000L, outbox.backoffMs(3))
        assertEquals(60_000L, outbox.backoffMs(20))
    }

    @Test
    fun `a service retry-after wins over our own curve`() {
        val outbox = box(FakeStore())

        assertEquals(5_000L, outbox.backoffMs(4, retryAfterMs = 5_000L))
    }

    @Test
    fun `a rejected listen is dropped rather than retried forever`() =
        runTest {
            val outbox = box(FakeStore())
            outbox.enqueue(event())

            val result =
                outbox.drain(setOf("lastfm:alice")) {
                    ScrobbleDelivery.Permanent("invalid session")
                }

            assertEquals(1, result.dropped)
            assertEquals(0, outbox.pendingCount())
        }

    @Test
    fun `retry exhaustion drops the listen`() =
        runTest {
            var now = 0L
            val outbox = box(FakeStore(), now = { now }, maxAttempts = 2)
            outbox.enqueue(event())

            outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Transient("down") }
            assertEquals(1, outbox.pendingCount())
            assertEquals(1, outbox.snapshot().single().attempts)

            now = 10_000L
            val exhausted =
                outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Transient("down") }

            assertEquals(1, exhausted.dropped)
            assertEquals(0, outbox.pendingCount())
        }

    @Test
    fun `one provider failing does not hold the other back`() =
        runTest {
            val outbox = box(FakeStore())
            outbox.enqueue(event(provider = ScrobbleProvider.LASTFM, account = "lastfm:alice"))
            outbox.enqueue(
                event(provider = ScrobbleProvider.LISTENBRAINZ, account = "listenbrainz:abc", track = "Other"),
            )

            val result =
                outbox.drain(setOf("lastfm:alice", "listenbrainz:abc")) { queued ->
                    if (queued.provider == ScrobbleProvider.LASTFM) {
                        ScrobbleDelivery.Permanent("suspended")
                    } else {
                        ScrobbleDelivery.Sent
                    }
                }

            assertEquals(1, result.dropped)
            assertEquals(1, result.sent)
            assertEquals(0, outbox.pendingCount())
        }

    @Test
    fun `a drain only sends the current account's listens`() =
        runTest {
            val outbox = box(FakeStore())
            outbox.enqueue(event(account = "lastfm:alice", track = "Alice's"))
            outbox.enqueue(event(account = "lastfm:bob", track = "Bob's"))

            val sent = mutableListOf<String>()
            val result =
                outbox.drain(setOf("lastfm:bob")) { queued ->
                    sent += queued.track
                    ScrobbleDelivery.Sent
                }

            assertEquals(1, result.sent)
            assertEquals(listOf("Bob's"), sent)
            assertEquals(1, outbox.pendingCount("lastfm:alice"))
        }

    @Test
    fun `signing out forgets that account only`() =
        runTest {
            val outbox = box(FakeStore())
            outbox.enqueue(event(account = "lastfm:alice"))
            outbox.enqueue(event(account = "lastfm:bob"))

            assertEquals(1, outbox.clearAccount("lastfm:alice"))

            assertEquals(0, outbox.pendingCount("lastfm:alice"))
            assertEquals(1, outbox.pendingCount("lastfm:bob"))
        }

    @Test
    fun `a replaced credential cannot leave another account's listens behind`() =
        runTest {
            val outbox = box(FakeStore())
            outbox.enqueue(event(account = "lastfm:alice"))
            outbox.enqueue(event(account = "lastfm:bob"))

            val removed = outbox.retainAccounts(setOf("lastfm:bob"))

            assertEquals(1, removed)
            assertEquals(1, outbox.pendingCount())
            assertEquals(1, outbox.pendingCount("lastfm:bob"))
        }

    @Test
    fun `one unreadable row does not cost the others`() {
        val store = FakeStore()
        store.text =
            """
            {"version":1,"events":[
              {"provider":"LASTFM","account":"lastfm:alice","artist":"A","track":"Good","timestampSeconds":5},
              {"provider":"SOMETHING_ELSE","account":"x"}
            ]}
            """.trimIndent()

        val outbox = box(store)

        assertEquals(1, outbox.pendingCount())
        assertEquals("Good", outbox.snapshot().single().track)
    }

    @Test
    fun `a blob that is not JSON reads as an empty queue`() {
        val outbox = box(FakeStore(text = "not json at all"))

        assertEquals(0, outbox.pendingCount())
    }

    @Test
    fun `duplicate rows already on disk collapse on load`() {
        val row =
            """{"provider":"LASTFM","account":"lastfm:alice","artist":"A","track":"T","timestampSeconds":5}"""
        val outbox = box(FakeStore(text = """{"version":1,"events":[$row,$row]}"""))

        assertEquals(1, outbox.pendingCount())
    }

    @Test
    fun `the queue is bounded, keeping the newest listens`() =
        runTest {
            val outbox = box(FakeStore(), capacity = 2)
            outbox.enqueue(event(track = "first", timestamp = 1L))
            outbox.enqueue(event(track = "second", timestamp = 2L))
            outbox.enqueue(event(track = "third", timestamp = 3L))

            assertEquals(2, outbox.pendingCount())
            assertEquals(listOf("second", "third"), outbox.snapshot().map { it.track })
        }

    @Test
    fun `an exception from the sender is treated as transient`() =
        runTest {
            val outbox = box(FakeStore())
            outbox.enqueue(event())

            val result =
                outbox.drain(setOf("lastfm:alice")) {
                    throw IOException("connection reset")
                }

            assertEquals(1, result.retrying)
            assertEquals(1, outbox.pendingCount())
            assertNotNull(outbox.snapshot().single().lastError)
        }

    @Test
    fun `a large backlog drains completely`() =
        runTest {
            val outbox = box(FakeStore(), capacity = 500)
            repeat(200) { index -> outbox.enqueue(event(track = "Track $index", timestamp = index.toLong())) }

            val result = outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Sent }

            assertEquals(200, result.sent)
            assertEquals(0, outbox.pendingCount())
        }

    @Test
    fun `successful delivery is persisted, not just forgotten in memory`() =
        runTest {
            val store = FakeStore()
            val outbox = box(store)
            outbox.enqueue(event())
            outbox.drain(setOf("lastfm:alice")) { ScrobbleDelivery.Sent }

            assertEquals(0, box(store).pendingCount())
        }

    @Test
    fun `an account fingerprint never contains the credential`() {
        val sessionKey = "b25b959554ed76058ac220b7b2e0a026"

        val fingerprint = ScrobbleOutbox.fingerprint(sessionKey)

        assertNotEquals(sessionKey, fingerprint)
        assertFalse(fingerprint.contains(sessionKey))
        assertEquals(16, fingerprint.length)
        assertEquals(fingerprint, ScrobbleOutbox.fingerprint(sessionKey))
        assertEquals("", ScrobbleOutbox.fingerprint(""))
    }

    @Test
    fun `a duplicate callback after a restart is still a duplicate`() =
        runTest {
            val store = FakeStore()
            box(store).enqueue(event())

            assertFalse(box(store).enqueue(event()))
        }
}
