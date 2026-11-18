package com.music.bitchord.data.settings

import com.music.bitchord.sync.SyncApiException
import com.music.bitchord.sync.SyncConflictRetriesExhausted
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkRetryPolicyTest {
    @Test
    fun retriesTransientTransportAndConflictFailures() {
        assertTrue(SyncWorkRetryPolicy.shouldRetry(IOException("offline")))
        assertTrue(SyncWorkRetryPolicy.shouldRetry(HttpRequestTimeoutException(HttpRequestBuilder())))
        assertTrue(SyncWorkRetryPolicy.shouldRetry(SyncConflictRetriesExhausted()))
        assertTrue(SyncWorkRetryPolicy.shouldRetry(SyncApiException(408, "timeout")))
        assertTrue(SyncWorkRetryPolicy.shouldRetry(SyncApiException(425, "too_early")))
        assertTrue(SyncWorkRetryPolicy.shouldRetry(SyncApiException(429, "rate_limited")))
        assertTrue(SyncWorkRetryPolicy.shouldRetry(SyncApiException(503, "unavailable")))
    }

    @Test
    fun doesNotRetryPermanentServerOrProgrammingFailures() {
        assertFalse(SyncWorkRetryPolicy.shouldRetry(SyncApiException(401, "unauthorized")))
        assertFalse(SyncWorkRetryPolicy.shouldRetry(SyncApiException(404, "not_found")))
        assertFalse(SyncWorkRetryPolicy.shouldRetry(IllegalArgumentException("invalid local state")))
    }
}
