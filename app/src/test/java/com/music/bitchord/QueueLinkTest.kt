package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.LinkRequest
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.playback.resolveQueueLink
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLinkTest {
    @Test fun `queries come back decoded and in order, with the title`() {
        val q = MusicLink.queue("title=Songs%20in%20S1%20%C2%B7%20E1&q=Camillano%20Peter%20Reno&q=A%20%26%20B&q=Tamacun")
        assertEquals(LinkRequest.Queue(listOf("Camillano Peter Reno", "A & B", "Tamacun"), "Songs in S1 · E1"), q)
    }

    @Test fun `blank, malformed and unknown parameters are ignored`() {
        val q = MusicLink.queue("q=&q=%20%20&junk&x=1&q=One&q=%ZZ")
        assertEquals(LinkRequest.Queue(listOf("One"), null), q)
    }

    @Test fun `a plus is a space, as in a form-encoded query`() {
        assertEquals(listOf("Get Low"), MusicLink.queue("q=Get+Low")?.queries)
    }

    @Test fun `no songs is no request`() {
        assertNull(MusicLink.queue(null))
        assertNull(MusicLink.queue(""))
        assertNull(MusicLink.queue("title=Only%20a%20title"))
    }

    @Test fun `a long link keeps the first songs`() {
        val raw = (1..80).joinToString("&") { "q=s$it" }
        val q = MusicLink.queue(raw)!!
        assertEquals(MusicLink.MAX_QUEUE, q.queries.size)
        assertEquals("s1", q.queries.first())
    }

    private fun song(id: String) = Song(videoId = id, title = id, artist = "a", thumbnailUrl = null)

    @Test fun `matches keep the link order, misses and repeats are dropped`() = runTest {
        val songs = resolveQueueLink(listOf("slow", "missing", "fast", "boom", "again-fast")) { query ->
            when (query) {
                "slow" -> { delay(50); song("v-slow") }
                "fast" -> song("v-fast")
                "again-fast" -> song("v-fast")
                "boom" -> throw IOException("offline")
                else -> null
            }
        }
        assertEquals(listOf("v-slow", "v-fast"), songs.map { it.videoId })
    }

    @Test fun `searches run a few at a time`() = runTest {
        val running = AtomicInteger()
        var most = 0
        resolveQueueLink(List(12) { "q$it" }, parallel = 3) { query ->
            most = maxOf(most, running.incrementAndGet())
            delay(10)
            running.decrementAndGet()
            song(query)
        }
        assertTrue("at most 3 at once, saw $most", most <= 3)
    }
}
