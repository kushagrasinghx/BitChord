package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.LinkRequest
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.playback.findAskedSong
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
        assertEquals(listOf("Camillano Peter Reno", "A & B", "Tamacun"), q?.queries)
        assertEquals("Songs in S1 · E1", q?.title)
    }

    @Test fun `blank, malformed and unknown parameters are ignored`() {
        val q = MusicLink.queue("q=&q=%20%20&junk&x=1&q=One&q=%ZZ")
        assertEquals(listOf("One"), q?.queries)
        assertNull(q?.title)
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

    private fun song(id: String, title: String = id, artist: String = "a") =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null)

    private fun asked(query: String) = LinkRequest.QueueSong(query)

    @Test fun `each song's title and artist follow its query`() {
        val q = MusicLink.queue("title=S1&q=Stick%20N%20A%20Brush%20Kenny%20Werner&t=Stick%20%27N%27%20A%20Brush&a=Kenny%20Werner&q=Tamacun&a=Rodrigo%20y%20Gabriela")!!
        assertEquals(
            listOf(
                LinkRequest.QueueSong("Stick N A Brush Kenny Werner", "Stick 'N' A Brush", "Kenny Werner"),
                LinkRequest.QueueSong("Tamacun", null, "Rodrigo y Gabriela"),
            ),
            q.songs,
        )
        // A t or a before any q belongs to no song.
        assertEquals(listOf(LinkRequest.QueueSong("One")), MusicLink.queue("t=Stray&q=One")?.songs)
    }

    private fun catalogue(songs: List<Song>, videos: List<Song> = emptyList()): suspend (String, SearchFilter) -> List<Song> =
        { _, filter -> if (filter == SearchFilter.SONGS) songs else videos }

    @Test fun `a named song plays only that recording, never a stranger`() = runTest {
        // What YouTube Music answered on the emulator for two Breaking Bad songs.
        val wrong = catalogue(listOf(song("v1", "De Paint Brush", "Kenny J"), song("v2", "Tempodium", "Peter Reno, De Wolfe Music")))
        assertNull(findAskedSong("Stick 'N' A Brush Kenny Werner", "Stick 'N' A Brush", "Kenny Werner", wrong))
        assertNull(findAskedSong("Camillano Peter Reno", "Camillano", "Peter Reno", wrong))
    }

    @Test fun `the right recording wins even when it is not the top row`() = runTest {
        val rows = catalogue(listOf(song("cover", "Tamacun (Cover)", "Some Guitarist"), song("real", "Tamacun", "Rodrigo y Gabriela")))
        assertEquals("real", findAskedSong("Tamacun Rodrigo y Gabriela", "Tamacun", "Rodrigo y Gabriela", rows)?.videoId)
    }

    @Test fun `a song missing from the songs shelf is found among the videos`() = runTest {
        val rows = catalogue(
            songs = listOf(song("v1", "De Paint Brush", "Kenny J")),
            videos = listOf(song("upload", "Stick 'N' A Brush", "Kenny Werner")),
        )
        assertEquals("upload", findAskedSong("q", "Stick 'N' A Brush", "Kenny Werner", rows)?.videoId)
    }

    @Test fun `words alone still play the top song, and a failed search is no song`() = runTest {
        assertEquals("v1", findAskedSong("anything", null, null, catalogue(listOf(song("v1"), song("v2"))))?.videoId)
        assertNull(findAskedSong("x", "T", "A") { _, _ -> throw IOException("offline") })
    }

    @Test fun `matches keep the link order, misses and repeats are dropped`() = runTest {
        val songs = resolveQueueLink(listOf("slow", "missing", "fast", "boom", "again-fast").map(::asked)) { asked ->
            when (asked.query) {
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
        resolveQueueLink(List(12) { asked("q$it") }, parallel = 3) { asked ->
            most = maxOf(most, running.incrementAndGet())
            delay(10)
            running.decrementAndGet()
            song(asked.query)
        }
        assertTrue("at most 3 at once, saw $most", most <= 3)
    }
}
