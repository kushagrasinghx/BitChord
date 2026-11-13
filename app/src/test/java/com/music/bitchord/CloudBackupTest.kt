package com.music.bitchord

import com.music.bitchord.data.backup.CloudBackupSettings
import com.music.bitchord.data.backup.S3Client
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CloudBackupTest {

    private val config = S3Client.Config(
        endpoint = "https://account.r2.cloudflarestorage.com",
        region = "auto",
        bucket = "backups",
        accessKey = "AKIDEXAMPLE",
        secretKey = "test-secret",
    )

    private val now = Instant.parse("2026-11-13T04:05:06Z")

    @Test
    fun signsPathStyleGet() {
        val request = S3Client.signedRequest(
            config,
            method = "GET",
            key = "BitChord/bitchord-backup.json",
            emptyPayload = true,
            now = now,
        )

        assertEquals(
            "https://account.r2.cloudflarestorage.com/backups/BitChord/bitchord-backup.json",
            request.url.toString(),
        )
        assertEquals("20261113T040506Z", request.header("x-amz-date"))
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261113/auto/s3/aws4_request, " +
                "SignedHeaders=host;x-amz-content-sha256;x-amz-date, " +
                "Signature=56f872a80326e880c9059117de420225aba6008dcce62a995dd7b359f5159f21",
            request.header("Authorization"),
        )
        // An unsigned content-type must not sneak into the signed-header list.
        assertFalse(request.header("Authorization")!!.contains("content-type"))
    }

    @Test
    fun signsPutWithThePayloadHash() {
        val payload = """{"app":"BitChord"}""".toByteArray()
        val request = S3Client.signedRequest(
            config,
            method = "PUT",
            key = "BitChord/bitchord-backup.json",
            emptyPayload = false,
            payload = payload,
            now = now,
        )

        assertEquals(
            "b3dd5b6e9d7adcfead6d7b8aaf1ead0bc9982ee83b2421312ac4fd05860e29a7",
            request.header("x-amz-content-sha256"),
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261113/auto/s3/aws4_request, " +
                "SignedHeaders=host;x-amz-content-sha256;x-amz-date, " +
                "Signature=eea77bde98d15205d6b8e5ec49046d5c7db359aba994f24c35e599c99e37d111",
            request.header("Authorization"),
        )
    }

    @Test
    fun encodesKeySegments() {
        val request = S3Client.signedRequest(
            config,
            method = "GET",
            key = "BitChord/backup 2026.json",
            emptyPayload = true,
            now = now,
        )

        assertEquals(
            "https://account.r2.cloudflarestorage.com/backups/BitChord/backup%202026.json",
            request.url.toString(),
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20261113/auto/s3/aws4_request, " +
                "SignedHeaders=host;x-amz-content-sha256;x-amz-date, " +
                "Signature=4a2ee89f6f3bb6895e96f054061e54671765eb74c365a8bf4210051fb4fecd59",
            request.header("Authorization"),
        )
    }

    @Test
    fun virtualHostedStyleMovesTheBucketIntoTheHost() {
        val request = S3Client.signedRequest(
            config.copy(pathStyle = false),
            method = "GET",
            key = "BitChord/bitchord-backup.json",
            emptyPayload = true,
            now = now,
        )

        assertEquals(
            "https://backups.account.r2.cloudflarestorage.com/BitChord/bitchord-backup.json",
            request.url.toString(),
        )
        assertTrue(
            request.header("Authorization")!!.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"),
        )
    }

    @Test
    fun signsTemporaryCredentialsToo() {
        val request = S3Client.signedRequest(
            config.copy(sessionToken = "session-token"),
            method = "GET",
            key = "BitChord/bitchord-backup.json",
            emptyPayload = true,
            now = now,
        )

        assertEquals("session-token", request.header("x-amz-security-token"))
        assertTrue(
            request.header("Authorization")!!
                .contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-security-token"),
        )
    }

    @Test
    fun acceptsTheEndpointWithoutAScheme() {
        val request = S3Client.signedRequest(
            config.copy(endpoint = "account.r2.cloudflarestorage.com/"),
            method = "GET",
            key = "BitChord/bitchord-backup.json",
            emptyPayload = true,
            now = now,
        )

        assertEquals(
            "https://account.r2.cloudflarestorage.com/backups/BitChord/bitchord-backup.json",
            request.url.toString(),
        )
    }

    @Test
    fun normalizesDestinationConfig() {
        val normalized = CloudBackupSettings.Config(
            webDavFolder = " /Music//Backups/ ",
            s3Endpoint = " https://s3.example.com/ ",
            s3Region = "  ",
            s3Bucket = "/backups/",
            s3Prefix = "/BitChord//Daily/",
            s3AccessKey = " key ",
        ).normalized()

        assertEquals("Music/Backups", normalized.webDavFolder)
        assertEquals("https://s3.example.com", normalized.s3Endpoint)
        assertEquals("auto", normalized.s3Region)
        assertEquals("backups", normalized.s3Bucket)
        assertEquals("BitChord/Daily", normalized.s3Prefix)
        assertEquals("key", normalized.s3AccessKey)
    }

    @Test
    fun normalizesFolderAndPrefixPaths() {
        assertEquals("", CloudBackupSettings.normalizePath("   "))
        assertEquals("", CloudBackupSettings.normalizePath("///"))
        assertEquals("BitChord", CloudBackupSettings.normalizePath("/BitChord/"))
        assertEquals("a/b/c", CloudBackupSettings.normalizePath(" a / b // c "))
    }

    @Test
    fun requiresACompleteDestination() {
        // S3 needs a bucket and endpoint; region and path style have defaults.
        // The Sources server is irrelevant to it, even when unparseable.
        assertFalse(
            CloudBackupSettings.isConfigured(
                CloudBackupSettings.Config(
                    provider = CloudBackupSettings.Provider.S3,
                    s3Endpoint = "https://s3.example.com",
                ),
                "not a url",
            ),
        )
        assertFalse(
            CloudBackupSettings.isConfigured(
                CloudBackupSettings.Config(
                    provider = CloudBackupSettings.Provider.S3,
                    s3Bucket = "backups",
                ),
                "",
            ),
        )
        assertTrue(
            CloudBackupSettings.isConfigured(
                CloudBackupSettings.Config(
                    provider = CloudBackupSettings.Provider.S3,
                    s3Endpoint = "https://s3.example.com",
                    s3Bucket = "backups",
                ),
                "not a url",
            ),
        )
        // WebDAV reads the Sources server, passed in by the caller: it needs a
        // parseable http(s) URL, not merely a non-empty string.
        assertFalse(CloudBackupSettings.isConfigured(CloudBackupSettings.Config(), ""))
        assertFalse(CloudBackupSettings.isConfigured(CloudBackupSettings.Config(), "not a url"))
        assertTrue(
            CloudBackupSettings.isConfigured(
                CloudBackupSettings.Config(),
                "cloud.example.com/dav",
            ),
        )
        // The folder is optional: an empty one backs up into the server root.
        assertTrue(
            CloudBackupSettings.isConfigured(
                CloudBackupSettings.Config(webDavFolder = ""),
                "https://cloud.example.com/dav",
            ),
        )
    }

    @Test
    fun ignoresAMissingSessionToken() {
        val request = S3Client.signedRequest(
            config.copy(sessionToken = null),
            method = "GET",
            key = "BitChord/bitchord-backup.json",
            emptyPayload = true,
            now = now,
        )

        assertNull(request.header("x-amz-security-token"))
    }

    @Test
    fun connectionTestAcceptsAnObjectScopedToken() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val reachable = config.copy(endpoint = server.url("/").toString().trimEnd('/'))

            // Bucket check is forbidden for this token; the object check is
            // allowed and simply finds nothing uploaded yet.
            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(S3Client.test(reachable, "BitChord/bitchord-backup.json").isSuccess)
            assertEquals("/backups", server.takeRequest().path)
            assertEquals("/backups/BitChord/bitchord-backup.json", server.takeRequest().path)

            server.enqueue(MockResponse().setResponseCode(200))
            assertTrue(S3Client.test(reachable, "BitChord/bitchord-backup.json").isSuccess)
            assertEquals("/backups", server.takeRequest().path)
        }
    }

    @Test
    fun connectionTestRejectsAMissingBucket() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val missing = config.copy(endpoint = server.url("/").toString().trimEnd('/'))

            server.enqueue(MockResponse().setResponseCode(404))
            val result = S3Client.test(missing, "BitChord/bitchord-backup.json")
            assertTrue(result.isFailure)
            assertEquals("Bucket not found", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun connectionTestRejectsForbiddenCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val forbidden = config.copy(endpoint = server.url("/").toString().trimEnd('/'))

            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().setResponseCode(403))
            val result = S3Client.test(forbidden, "BitChord/bitchord-backup.json")
            assertTrue(result.isFailure)
            assertEquals("Connection test failed (403)", result.exceptionOrNull()?.message)
        }
    }
}
