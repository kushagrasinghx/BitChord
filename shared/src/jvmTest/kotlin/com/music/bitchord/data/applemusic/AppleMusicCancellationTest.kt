package com.music.bitchord.data.applemusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleMusicCancellationTest {
    /** Holds the callback until the test delivers a response or failure, even after cancellation. */
    private class PendingCall(
        private val backingCall: Call = OkHttpClient().newCall(
            Request.Builder().url(appleMusicUrl("me", "library", "songs")).build(),
        ),
    ) : Call by backingCall {
        private val request = backingCall.request()
        private var callback: Callback? = null
        @Volatile private var cancelled = false
        var blockingExecuteUsed = false
            private set

        override fun request(): Request = request
        override fun execute(): Response {
            blockingExecuteUsed = true
            error("The transport must use enqueue")
        }
        override fun enqueue(responseCallback: Callback) {
            check(callback == null)
            callback = responseCallback
        }
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = callback != null || blockingExecuteUsed
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = PendingCall()

        fun respond(body: ResponseBody) {
            checkNotNull(callback).onResponse(this, response(request, body))
        }

        fun fail() {
            checkNotNull(callback).onFailure(this, IOException("developer-secret user-secret"))
        }
    }

    private class TestBody(private val releaseRead: CountDownLatch? = null) : ResponseBody() {
        val readStarted = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val closed = AtomicBoolean()
        val text = """{"data":[]}"""
        private val bodySource = object : ForwardingSource(Buffer().writeUtf8(text)) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads.incrementAndGet()
                readStarted.complete(Unit)
                if (releaseRead != null && !releaseRead.await(5, TimeUnit.SECONDS)) {
                    throw IOException("Test body was not released")
                }
                return super.read(sink, byteCount)
            }

            override fun close() {
                closed.set(true)
                super.close()
            }
        }.buffer()

        override fun contentType() = "application/json".toMediaType()
        override fun contentLength(): Long = text.toByteArray().size.toLong()
        override fun source(): BufferedSource = bodySource
    }

    @Test
    fun timeoutCancelsAnEnqueuedCallWithoutWaitingForAnyResponse() = runBlocking {
        val call = PendingCall()
        val result = withTimeoutOrNull(100) { call.awaitAppleMusicBody() }
        assertNull(result)
        assertTrue(call.isExecuted())
        assertTrue(call.isCanceled())
        assertFalse(call.blockingExecuteUsed)
    }

    @Test
    fun explicitCancellationClosesALateResponseWithoutReadingOrDeliveringIt() = runBlocking<Unit> {
        val call = PendingCall()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { call.awaitAppleMusicBody() }
        assertTrue(call.isExecuted())
        withTimeout(1_000) { pending.cancelAndJoin() }
        assertTrue(call.isCanceled())

        val late = TestBody()
        call.respond(late)
        assertTrue(late.closed.get())
        assertEquals(0, late.reads.get())
        assertTrue(pending.isCancelled)
        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test
    fun aFailureCallbackAfterCancellationDoesNotReplaceCancellation() = runBlocking {
        val call = PendingCall()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { call.awaitAppleMusicBody() }
        pending.cancelAndJoin()
        call.fail()
        val error = assertFailsWith<CancellationException> { pending.await() }
        assertFalse(error.toString().contains("developer-secret"))
        assertFalse(error.toString().contains("user-secret"))
        assertTrue(call.isCanceled())
    }

    @Test
    fun successfulCallbacksReadAndCloseTheBodyBeforeReturningText() = runBlocking {
        val call = PendingCall()
        val body = TestBody()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { call.awaitAppleMusicBody() }
        call.respond(body)
        assertEquals(body.text, pending.await())
        assertTrue(body.closed.get())
        assertTrue(body.reads.get() > 0)
        assertFalse(call.isCanceled())
    }

    @Test
    fun failureCallbacksExposeOnlyASanitizedException() = runBlocking {
        val call = PendingCall()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { call.awaitAppleMusicBody() }
        }
        call.fail()
        val error = pending.await().exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("Apple Music request failed", error.message)
        val text = error.stackTraceToString()
        assertFalse(text.contains("developer-secret"))
        assertFalse(text.contains("user-secret"))
        generateSequence(error.cause) { it.cause }.forEach { cause ->
            assertTrue(cause is IllegalStateException)
            assertEquals(error.message, cause.message)
        }
    }

    @Test
    fun restTimeoutReturnsAndCancelsTheCallEvenWhileItsCallbackIsReadingABody() = runBlocking {
        val releaseRead = CountDownLatch(1)
        val body = TestBody(releaseRead)
        val startedCall = CompletableDeferred<Call>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            startedCall.complete(chain.call())
            response(chain.request(), body)
        }.build()
        val auth = object : AppleMusicLibrary.Auth {
            override suspend fun developerToken(): String = "developer-secret"
            override suspend fun userToken(): String = "user-secret"
        }
        val rest = AppleMusicRestClient(client, { auth })
        try {
            val pending = async {
                withTimeoutOrNull(1_000) { rest.get(appleMusicUrl("me", "library", "songs")) }
            }
            withTimeout(2_000) { body.readStarted.await() }
            // The read is deliberately still blocked: returning must not wait for its completion.
            assertNull(withTimeout(2_000) { pending.await() })
            assertEquals(1L, releaseRead.count)
            assertTrue(startedCall.await().isCanceled())
        } finally {
            releaseRead.countDown()
            client.dispatcher.executorService.shutdown()
            assertTrue(client.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS))
            client.connectionPool.evictAll()
        }
        assertTrue(body.closed.get())
    }

    private companion object {
        fun response(request: Request, body: ResponseBody): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("Test response")
            .body(body)
            .build()
    }
}
