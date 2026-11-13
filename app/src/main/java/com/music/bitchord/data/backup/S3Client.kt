package com.music.bitchord.data.backup

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The smallest S3 client the backup feature can use: PUT, GET and HEAD of one
 * object, signed with AWS Signature Version 4.
 *
 * Deliberately not the AWS SDK. The SDK is tens of megabytes and a second
 * HTTP stack for three requests a day; the signature is a page of HMACs. The
 * same code works against Cloudflare R2, AWS S3, MinIO, Backblaze B2 and any
 * other S3-compatible endpoint, which is the "user choice" this destination
 * exists for.
 *
 * Path-style addressing is the default because R2's documented endpoint is
 * account-scoped (`https://<account>.r2.cloudflarestorage.com`) and takes the
 * bucket as the first path segment. Virtual-hosted style is a switch for
 * providers that require it; AWS buckets with dots in their name generally do.
 */
object S3Client {

    data class Config(
        val endpoint: String,
        val region: String,
        val bucket: String,
        val accessKey: String,
        val secretKey: String,
        val sessionToken: String? = null,
        val pathStyle: Boolean = true,
    )

    private const val SERVICE = "s3"
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US).withZone(ZoneOffset.UTC)
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        .withZone(ZoneOffset.UTC)

    suspend fun put(config: Config, key: String, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                execute(config, "PUT", key, bytes)
            }
        }

    suspend fun get(config: Config, key: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = signedRequest(config, "GET", key, emptyPayload = true)
                Http.client.newCall(request).execute().use { response ->
                    if (response.code !in 200..299) {
                        throw S3Exception(failureMessage("Download", response.code, response.body.string()))
                    }
                    response.body.bytes().takeIf { it.isNotEmpty() } ?: throw S3Exception("The server returned an empty backup")
                }
            }
        }

    /**
     * Checks that the bucket exists and answers with these credentials.
     *
     * A HEAD on the bucket is the direct question, but S3 grants that check
     * through `ListBucket`, which object-scoped credentials (the usual kind for
     * R2) intentionally lack. A 403 there therefore only means "not allowed to
     * ask", not "wrong"; the HEAD on the target object that follows still
     * distinguishes bad credentials (403) from a bucket with no backup in it
     * yet (404), which is a working destination.
     */
    suspend fun test(config: Config, key: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bucketCode = Http.client
                    .newCall(signedRequest(config, "HEAD", "", emptyPayload = true))
                    .execute().use { it.code }
                when (bucketCode) {
                    in 200..299 -> return@runCatching
                    404 -> throw S3Exception("Bucket not found")
                    // Object-scoped tokens cannot list the bucket; ask the
                    // object below instead.
                    403, 405 -> Unit
                    else -> throw S3Exception(failureMessage("Connection test", bucketCode, null))
                }

                val request = signedRequest(config, "HEAD", key, emptyPayload = true)
                Http.client.newCall(request).execute().use { response ->
                    if (response.code !in 200..299 && response.code != 404) {
                        throw S3Exception(failureMessage("Connection test", response.code, response.body.string()))
                    }
                }
            }
        }

    private fun execute(config: Config, method: String, key: String, bytes: ByteArray) {
        val request = signedRequest(config, method, key, emptyPayload = false, payload = bytes)
        Http.client.newCall(request).execute().use { response ->
            if (response.code !in 200..299) {
                throw S3Exception(failureMessage("Upload", response.code, response.body.string()))
            }
        }
    }

    internal fun signedRequest(
        config: Config,
        method: String,
        key: String,
        emptyPayload: Boolean,
        payload: ByteArray = ByteArray(0),
        now: Instant = Instant.now(),
    ): Request {
        require(config.accessKey.isNotBlank()) { "Enter an access key ID" }
        require(config.secretKey.isNotBlank()) { "Enter a secret access key" }
        val url = urlFor(config, key)
        val payloadHash = if (emptyPayload) EMPTY_SHA256 else sha256Hex(payload)
        val date = DATE.format(now)
        val stamp = STAMP.format(now)
        val host = hostHeader(url)
        val token = config.sessionToken?.trim().orEmpty()
        val headers = buildMap {
            put("host", host)
            put("x-amz-content-sha256", payloadHash)
            put("x-amz-date", stamp)
            if (token.isNotEmpty()) put("x-amz-security-token", token)
        }
        val canonicalHeaders = headers.entries
            .sortedBy { it.key }
            .joinToString("") { (name, value) -> "$name:${value.trim()}\n" }
        val signedHeaders = headers.keys.sorted().joinToString(";")
        val canonicalRequest = buildString {
            append(method).append('\n')
            append(url.encodedPath).append('\n')
            append('\n') // no query string
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(payloadHash)
        }
        val scope = "$date/${config.region.ifBlank { "auto" }}/$SERVICE/aws4_request"
        val stringToSign = buildString {
            append(ALGORITHM).append('\n')
            append(stamp).append('\n')
            append(scope).append('\n')
            append(sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)))
        }
        val signingKey = signingKey(config.secretKey, date, config.region.ifBlank { "auto" })
        val signature = hmacHex(signingKey, stringToSign)
        val authorization = "$ALGORITHM Credential=${config.accessKey}/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        val builder = Request.Builder()
            .url(url)
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", stamp)
            .header("Authorization", authorization)
        if (token.isNotEmpty()) builder.header("x-amz-security-token", token)
        return when (method) {
            "PUT" -> builder.put(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            "HEAD" -> builder.head().build()
            else -> builder.get().build()
        }
    }

    private fun urlFor(config: Config, key: String): HttpUrl {
        val raw = config.endpoint.trim()
        val endpoint = (if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            raw
        } else {
            "https://$raw"
        }).trimEnd('/')
        val base = endpoint.toHttpUrlOrNull()
            ?: throw S3Exception("Enter a valid S3 endpoint")
        require(config.bucket.isNotBlank()) { "Enter a bucket name" }

        val builder = base.newBuilder()
        if (config.pathStyle) {
            builder.addPathSegment(config.bucket)
        } else {
            builder.host("${config.bucket}.${base.host}")
        }
        key.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun hostHeader(url: HttpUrl): String {
        val defaultPort = if (url.scheme == "https") 443 else 80
        return if (url.port == defaultPort) url.host else "${url.host}:${url.port}"
    }

    private fun signingKey(secret: String, date: String, region: String): ByteArray {
        val dateKey = hmac(("AWS4$secret").toByteArray(Charsets.UTF_8), date)
        val regionKey = hmac(dateKey, region)
        val serviceKey = hmac(regionKey, SERVICE)
        return hmac(serviceKey, "aws4_request")
    }

    private fun hmac(key: ByteArray, value: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, value: String): String =
        hmac(key, value).joinToString("") { "%02x".format(Locale.US, it) }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(Locale.US, it) }

    private fun failureMessage(action: String, code: Int, body: String?): String {
        val detail = body?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
        return if (detail == null) "$action failed ($code)" else "$action failed ($code): $detail"
    }

    class S3Exception(message: String) : Exception(message)
}
