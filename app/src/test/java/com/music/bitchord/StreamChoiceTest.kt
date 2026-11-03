package com.music.bitchord

import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.playback.StreamChoice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The promise [StreamChoice] makes: a track being served from one copy keeps
 * being served from that copy.
 *
 * Every live entry stands behind a half-filled cache entry on disk, and the
 * cost of breaking one is not a wrong bitrate but a corrupt file — the middle
 * of an MP4 appended to a WebM. So the tests here are about what happens under
 * pressure, which is where that promise was breakable.
 */
class StreamChoiceTest {

    private fun stream(host: String) = SourceStream(
        url = "https://$host/track.mp4",
        format = StreamFormat(codec = "mp4", kbps = 320),
    )

    @Before
    @After
    fun reset() {
        // Ids used below, cleared both ways round so a failure can't leak into
        // the next test through the shared object.
        (0..80).forEach { StreamChoice.forget("track-$it") }
    }

    @Test
    fun `a remembered choice is handed back`() {
        StreamChoice.remember("track-1", stream("aac.saavncdn.com"), substituted = true)
        assertEquals("https://aac.saavncdn.com/track.mp4", StreamChoice.of("track-1")?.url)
    }

    @Test
    fun `a track nothing has chosen for is free to resolve`() {
        assertNull(StreamChoice.of("track-2"))
    }

    /** Only the substituted ones, so the recovery path can tell them apart. */
    @Test
    fun `remembers whether the copy came from a substitute`() {
        StreamChoice.remember("track-3", stream("aac.saavncdn.com"), substituted = true)
        StreamChoice.remember("track-4", stream("googlevideo.com"), substituted = false)
        assertEquals(true, StreamChoice.isSubstitute("track-3"))
        assertEquals(false, StreamChoice.isSubstitute("track-4"))
    }

    /**
     * The eviction that mattered. Overflow used to `clear()` the whole map,
     * which releases every track being served — including ones with bytes
     * half-written under a key only their own stream may finish.
     *
     * Read-ahead made this reachable: it pins the next track and caches its
     * bytes before the listener gets there, so a pin now has to survive other
     * tracks being remembered in between. The most recent entries must still be
     * honoured after the map has been pushed well past its limit.
     */
    @Test
    fun `overflow drops the oldest choice rather than all of them`() {
        repeat(40) { StreamChoice.remember("track-$it", stream("host-$it.example"), substituted = true) }

        // The newest write is always honoured under either policy: it is the
        // track being read right now.
        assertNotNull(StreamChoice.of("track-39"))
        assertEquals("https://host-39.example/track.mp4", StreamChoice.of("track-39")?.url)

        // This is the assertion that separates evicting-the-oldest from
        // emptying the map. `track-20` is one of the twenty most recent writes,
        // so any bounded map worth the name still holds it — but a `clear()` on
        // overflow throws it out along with everything else, and the cache entry
        // it was standing behind becomes free for another source to finish.
        assertNotNull(
            "a recent choice was evicted, so overflow is still emptying the map wholesale",
            StreamChoice.of("track-20"),
        )
        assertEquals("https://host-20.example/track.mp4", StreamChoice.of("track-20")?.url)
    }

    /** Releasing a track lets the next resolve decide afresh. */
    @Test
    fun `forgetting a choice reopens the question`() {
        StreamChoice.remember("track-5", stream("aac.saavncdn.com"), substituted = true)
        StreamChoice.forget("track-5")
        assertNull(StreamChoice.of("track-5"))
    }

    /** An early lookup's pin, for a track nothing has settled yet, is taken. */
    @Test
    fun `a speculative pin is recorded when nothing has chosen`() {
        assertTrue(StreamChoice.rememberIfUnchosen("track-6", stream("addon.example"), substituted = true))
        assertEquals("https://addon.example/track.mp4", StreamChoice.of("track-6")?.url)
        assertEquals(true, StreamChoice.isSubstitute("track-6"))
    }

    /**
     * The guarantee the early lookup relies on. The player may settle a track
     * on YouTube while the addon search behind the speculative pin is still
     * running; the late answer must lose, because the YouTube bytes are already
     * being written under that choice.
     */
    @Test
    fun `a speculative pin never replaces a choice already made`() {
        StreamChoice.remember("track-7", stream("googlevideo.com"), substituted = false)

        assertFalse(StreamChoice.rememberIfUnchosen("track-7", stream("addon.example"), substituted = true))
        assertEquals("https://googlevideo.com/track.mp4", StreamChoice.of("track-7")?.url)
        assertEquals(true, StreamChoice.isYouTube("track-7"))
    }

    /** Speculative pins share [StreamChoice.remember]'s overflow policy. */
    @Test
    fun `speculative pins evict the oldest choice rather than all of them`() {
        repeat(40) {
            StreamChoice.rememberIfUnchosen("track-$it", stream("host-$it.example"), substituted = true)
        }
        assertEquals("https://host-39.example/track.mp4", StreamChoice.of("track-39")?.url)
        assertEquals("https://host-20.example/track.mp4", StreamChoice.of("track-20")?.url)
    }
}
