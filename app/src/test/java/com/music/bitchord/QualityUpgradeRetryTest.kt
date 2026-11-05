package com.music.bitchord

import com.music.bitchord.data.sources.RetryHints
import com.music.bitchord.playback.QualityUpgrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The second look's "ask again later": how long it waits on a source that
 * said "not yet", and how that word reaches it through the racing lookups.
 */
class QualityUpgradeRetryTest {

    private fun hint(sec: Int) = RetryHints.Hint(sec, "Pulsar", "Downloading on TorBox: 40%")

    @Test
    fun `no hint means no retry`() {
        assertNull(QualityUpgrade.retryDelayMs(null, waitedMs = 0))
    }

    @Test
    fun `the addon's figure is used within bounds`() {
        assertEquals(30_000L, QualityUpgrade.retryDelayMs(hint(30), waitedMs = 0))
        // Never a busy loop over the catalogue…
        assertEquals(QualityUpgrade.RETRY_MIN_MS, QualityUpgrade.retryDelayMs(hint(1), waitedMs = 0))
        // …and never so long that the song is over before the next look.
        assertEquals(QualityUpgrade.RETRY_MAX_MS, QualityUpgrade.retryDelayMs(hint(3600), waitedMs = 0))
    }

    @Test
    fun `waiting stops at the window`() {
        val nearlyDone = QualityUpgrade.RETRY_WINDOW_MS - 20_000
        assertEquals(10_000L, QualityUpgrade.retryDelayMs(hint(10), waitedMs = nearlyDone))
        assertNull(QualityUpgrade.retryDelayMs(hint(30), waitedMs = nearlyDone))
    }

    /**
     * The hint has to cross the resolver's racing `async` children, which is
     * the reason it is a context element: each source reports from its own
     * coroutine, and the soonest wins.
     */
    @Test
    fun `hints from racing children reach the asker and the soonest wins`() = runBlocking {
        val hints = RetryHints()
        withContext(hints) {
            coroutineScope {
                listOf(90 to "Slow", 30 to "Pulsar", 60 to "Other").map { (sec, name) ->
                    async(Dispatchers.Default) {
                        withContext(Dispatchers.IO) {
                            currentCoroutineContext()[RetryHints]?.note(sec, name, null)
                        }
                    }
                }.awaitAll()
            }
        }
        assertEquals(30, hints.soonestHint?.afterSec)
        assertEquals("Pulsar", hints.soonestHint?.source)
    }
}
