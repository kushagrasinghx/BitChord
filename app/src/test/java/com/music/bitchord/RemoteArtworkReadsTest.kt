package com.music.bitchord

import com.music.bitchord.data.remote.RemoteArtworkReads
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteArtworkReadsTest {
    @Test fun foregroundReusesPendingSpeculativeSuccess() = runBlocking {
        val reads = RemoteArtworkReads()
        val release = CompletableDeferred<String?>()
        val warm = async(start = CoroutineStart.UNDISPATCHED) { reads.read("cover", true) { release.await() } }
        val foreground = async(start = CoroutineStart.UNDISPATCHED) {
            reads.read("cover", false) { error("Successful speculative artwork should be reused") }
        }
        release.complete("warm pixels")
        assertEquals("warm pixels", foreground.await())
        assertEquals("warm pixels", warm.await())
    }

    @Test fun foregroundRecoversFromSpeculativeDnsMiss() = runBlocking {
        val reads = RemoteArtworkReads()
        val release = CompletableDeferred<Unit>()
        val warm = async(start = CoroutineStart.UNDISPATCHED) {
            reads.read("cover", true) { release.await(); throw UnknownHostException() }
        }
        var normalCalls = 0
        val foreground = async(start = CoroutineStart.UNDISPATCHED) {
            reads.read("cover", false) { normalCalls++; "foreground pixels" }
        }
        release.complete(Unit)
        assertEquals("foreground pixels", foreground.await())
        assertNull(warm.await())
        assertEquals(1, normalCalls)
    }

    @Test fun foregroundRecoversWhenSpeculativeOwnerIsCancelled() = runBlocking {
        val reads = RemoteArtworkReads()
        val release = CompletableDeferred<String?>()
        val warm = async(start = CoroutineStart.UNDISPATCHED) { reads.read("cover", true) { release.await() } }
        val foreground = async(start = CoroutineStart.UNDISPATCHED) {
            reads.read("cover", false) { "foreground pixels" }
        }
        warm.cancelAndJoin()
        assertEquals("foreground pixels", foreground.await())
    }

    @Test fun twoForegroundWaitersShareSingleFallbackEvenWhenItFails() = runBlocking {
        val reads = RemoteArtworkReads()
        val miss = CompletableDeferred<String?>()
        val normal = CompletableDeferred<String?>()
        val warm = async(start = CoroutineStart.UNDISPATCHED) { reads.read("cover", true) { miss.await() } }
        var normalCalls = 0
        val foreground = List(2) {
            async(start = CoroutineStart.UNDISPATCHED) { reads.read("cover", false) { normalCalls++; normal.await() } }
        }
        miss.complete(null)
        warm.await()
        normal.complete(null)
        foreground.forEach { assertNull(it.await()) }
        assertEquals(1, normalCalls)
    }

    @Test fun speculativeWaiterCannotStrandForegroundAfterForegroundOwnerCancellation() = runBlocking {
        val reads = RemoteArtworkReads()
        val miss = CompletableDeferred<String?>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            reads.read("cover", false) { CompletableDeferred<String?>().await() }
        }
        val warm = async(start = CoroutineStart.UNDISPATCHED) { reads.read("cover", true) { miss.await() } }
        val foreground = async(start = CoroutineStart.UNDISPATCHED) {
            reads.read("cover", false) { "foreground pixels" }
        }
        owner.cancelAndJoin()
        yield()
        miss.complete(null)
        assertEquals("foreground pixels", foreground.await())
        warm.await()
        Unit
    }
}
