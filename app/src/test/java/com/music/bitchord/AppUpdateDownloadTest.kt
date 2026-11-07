package com.music.bitchord

import com.music.bitchord.data.AppUpdateChecker
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * The update download's resume path against a local server: a plain download,
 * continuing a partial file with a Range request, a server that ignores Range,
 * a partial that is already complete (416), and a connection that drops
 * mid-body.
 */
class AppUpdateDownloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val ranges = mutableListOf<String?>()
    private val payload = ByteArray(300_000) { (it % 251).toByte() }

    /** Whether the server honours Range; flipped off to simulate one that doesn't. */
    private var supportsRange = true

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                ranges += range
                val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toLongOrNull()
                if (!supportsRange || start == null) return body(payload, 200)
                if (start >= payload.size) {
                    return MockResponse().setResponseCode(416)
                        .setHeader("Content-Range", "bytes */${payload.size}")
                }
                return body(payload.copyOfRange(start.toInt(), payload.size), 206)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun body(bytes: ByteArray, code: Int) =
        MockResponse().setResponseCode(code).setBody(okio.Buffer().write(bytes))

    private fun url() = server.url("/BitChord.apk").toString()

    @Test
    fun freshDownloadWritesWholeFile() {
        val file = tmp.newFile().also { it.delete() }
        assertTrue(AppUpdateChecker.transfer(url(), file))
        assertArrayEquals(payload, file.readBytes())
        assertNull(ranges.single())
    }

    @Test
    fun partialFileIsContinuedWithRange() {
        val file = tmp.newFile()
        file.writeBytes(payload.copyOfRange(0, 100_000))
        assertTrue(AppUpdateChecker.transfer(url(), file))
        assertArrayEquals(payload, file.readBytes())
        assertEquals("bytes=100000-", ranges.single())
    }

    @Test
    fun serverIgnoringRangeRestartsFromScratch() {
        supportsRange = false
        val file = tmp.newFile()
        file.writeBytes(ByteArray(100_000) { 7 })
        assertTrue(AppUpdateChecker.transfer(url(), file))
        assertArrayEquals(payload, file.readBytes())
    }

    @Test
    fun alreadyCompletePartialIsAccepted() {
        val file = tmp.newFile()
        file.writeBytes(payload)
        assertTrue(AppUpdateChecker.transfer(url(), file))
        assertArrayEquals(payload, file.readBytes())
    }

    @Test
    fun stalePartialIsDiscardedOn416() {
        val file = tmp.newFile()
        file.writeBytes(ByteArray(payload.size + 10))
        try {
            AppUpdateChecker.transfer(url(), file)
            org.junit.Assert.fail("expected a retryable IOException")
        } catch (_: IOException) {
        }
        assertFalse(file.exists())
        assertTrue(AppUpdateChecker.transfer(url(), file))
        assertArrayEquals(payload, file.readBytes())
    }

    @Test
    fun droppedConnectionKeepsBytesAndResumes() {
        server.dispatcher = object : Dispatcher() {
            private var first = true
            override fun dispatch(request: RecordedRequest): MockResponse {
                ranges += request.getHeader("Range")
                if (first) {
                    first = false
                    return body(payload, 200).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                }
                val start = request.getHeader("Range")!!.removePrefix("bytes=").removeSuffix("-").toInt()
                return body(payload.copyOfRange(start, payload.size), 206)
            }
        }
        val file = tmp.newFile().also { it.delete() }
        try {
            AppUpdateChecker.transfer(url(), file)
            org.junit.Assert.fail("expected the truncated body to fail")
        } catch (_: IOException) {
        }
        assertTrue(file.length() < payload.size)
        assertTrue(AppUpdateChecker.transfer(url(), file))
        assertArrayEquals(payload, file.readBytes())
        assertTrue(ranges.last()!!.startsWith("bytes="))
    }
}
