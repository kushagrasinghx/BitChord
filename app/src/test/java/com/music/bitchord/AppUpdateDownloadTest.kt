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
        // The cancel flag lives on the object, so it would leak between tests.
        AppUpdateChecker::class.java.getDeclaredField("downloadCancelled")
            .apply { isAccessible = true }
            .setBoolean(AppUpdateChecker, false)
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
                return tail(start.toInt())
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun body(bytes: ByteArray, code: Int) =
        MockResponse().setResponseCode(code).setBody(okio.Buffer().write(bytes))

    /** The 206 a real server sends: the tail of [payload] from [start], with its Content-Range. */
    private fun tail(start: Int, rangeStart: Int = start) =
        body(payload.copyOfRange(start, payload.size), 206)
            .setHeader("Content-Range", "bytes $rangeStart-${payload.size - 1}/${payload.size}")

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
                return tail(start)
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

    @Test
    fun asksForPlainBytes() {
        val file = tmp.newFile().also { it.delete() }
        AppUpdateChecker.transfer(url(), file)
        assertEquals("identity", server.takeRequest().getHeader("Accept-Encoding"))
    }

    @Test
    fun rangeAnswerStartingElsewhereIsRejected() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = tail(0, rangeStart = 0)
        }
        val file = tmp.newFile()
        file.writeBytes(payload.copyOfRange(0, 100_000))
        try {
            AppUpdateChecker.transfer(url(), file)
            org.junit.Assert.fail("expected the mismatched range to be refused")
        } catch (_: IOException) {
        }
        assertFalse(file.exists())
    }

    @Test
    fun transientStatusesAreRetryable() {
        for (code in listOf(408, 425, 429, 500, 503)) {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(code)
            }
            val file = tmp.newFile().also { it.delete() }
            try {
                AppUpdateChecker.transfer(url(), file)
                org.junit.Assert.fail("expected HTTP $code to be retryable")
            } catch (_: IOException) {
            }
        }
    }

    @Test
    fun otherClientErrorsFailAtOnce() {
        for (code in listOf(400, 403, 404)) {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(code)
            }
            val file = tmp.newFile().also { it.delete() }
            try {
                AppUpdateChecker.transfer(url(), file)
                org.junit.Assert.fail("expected HTTP $code to fail")
            } catch (_: IOException) {
                org.junit.Assert.fail("HTTP $code must not be retried")
            } catch (_: IllegalStateException) {
            }
        }
    }

    @Test
    fun cancelStopsAStalledConnection() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        }
        val file = tmp.newFile().also { it.delete() }
        var outcome: Result<Boolean>? = null
        val worker = Thread { outcome = runCatching { AppUpdateChecker.transfer(url(), file) } }
        worker.start()
        Thread.sleep(500)
        AppUpdateChecker.cancelDownload()
        worker.join(3_000)
        assertFalse("cancel should have interrupted the stalled request", worker.isAlive)
        // Either shape is fine: it stopped, and it didn't report success.
        assertTrue(outcome?.getOrNull() != true)
    }
}
