package com.music.bitchord

import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.StreamLengthProbe
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamLengthProbeTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val mp3 = StreamFormat(codec = "mp3", kbps = 320)
    private val flac = StreamFormat(codec = "flac")

    @Test
    fun `a 30 second preview cannot pass for a full song`() {
        val preview = 30L * 128_000 / 8 // 480 kB
        assertTrue(StreamLengthProbe.tooShort(preview, mp3, expectedSec = 210, slackSec = 5))
        assertTrue(StreamLengthProbe.tooShort(preview, flac, expectedSec = 210, slackSec = 5))
    }

    @Test
    fun `a full low-bitrate copy is never refused for an optimistic bitrate claim`() {
        // Claimed 320 kbps, actually 96: still room for the whole song at the floor.
        val full = 210L * 96_000 / 8
        assertFalse(StreamLengthProbe.tooShort(full, mp3, expectedSec = 210, slackSec = 5))
    }

    @Test
    fun `a full FLAC of quiet material is kept`() {
        val full = 210L * 300_000 / 8
        assertFalse(StreamLengthProbe.tooShort(full, flac, expectedSec = 210, slackSec = 5))
    }

    @Test
    fun `nothing to measure is not short`() {
        assertFalse(StreamLengthProbe.tooShort(0, mp3, expectedSec = 210, slackSec = 5))
        assertFalse(StreamLengthProbe.tooShort(1_000_000, mp3, expectedSec = 0, slackSec = 5))
    }

    @Test
    fun `reads the total from a ranged answer`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/480000").setBody("x"))
        val bytes = StreamLengthProbe.bytesOf(SourceStream(server.url("/a.mp3").toString(), headers = mapOf("X-T" to "1")))
        assertEquals(480_000L, bytes)
        val request = server.takeRequest()
        assertEquals("bytes=0-0", request.getHeader("Range"))
        assertEquals("1", request.getHeader("X-T"))
    }

    @Test
    fun `reads the length from a server that ignores the range`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("0123456789"))
        assertEquals(10L, StreamLengthProbe.bytesOf(SourceStream(server.url("/a.mp3").toString())))
    }

    @Test
    fun `a failed probe is unmeasured`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertNull(StreamLengthProbe.bytesOf(SourceStream(server.url("/a.mp3").toString())))
    }
}
