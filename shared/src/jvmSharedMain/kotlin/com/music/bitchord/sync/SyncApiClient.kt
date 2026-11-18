package com.music.bitchord.sync

import java.net.URI
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.http.contentType
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Thin transport client for the community sync API. It accepts ciphertext, never plaintext profile data. */
class SyncApiClient(
    private val client: HttpClient,
    baseUrl: String,
) : SyncRemote {
    private val baseUrl = validateBaseUrl(baseUrl)

    override suspend fun createProfile(): CreatedProfile = request {
        client.post("$baseUrl/v1/profiles") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
    }

    override suspend fun createPairing(profileId: String, deviceToken: String): PairingTicket {
        require(deviceToken.isNotBlank()) { "Device token is required" }
        return request {
            client.post(profilePath(profileId, "/pairings")) {
                bearerAuth(deviceToken)
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        }
    }

    override suspend fun claimPairing(profileId: String, pairingToken: String): ClaimedDevice {
        require(pairingToken.isNotBlank()) { "Pairing token is required" }
        return request {
            client.post("$baseUrl/v1/pairings/claim") {
                contentType(ContentType.Application.Json)
                setBody(ClaimPairingRequest(profileId, pairingToken))
            }
        }
    }

    override suspend fun listDevices(profileId: String, deviceToken: String): List<SyncDevice> {
        require(deviceToken.isNotBlank()) { "Device token is required" }
        return request<DeviceListResponse> {
            client.get(profilePath(profileId, "/devices")) { bearerAuth(deviceToken) }
        }.devices
    }

    override suspend fun revokeDevice(profileId: String, deviceToken: String, targetDeviceId: String) {
        require(deviceToken.isNotBlank()) { "Device token is required" }
        require(targetDeviceId.isNotBlank() && targetDeviceId.length <= MAX_DEVICE_ID_LENGTH) {
            "Invalid target device ID"
        }
        request<Unit> {
            client.delete(profilePath(profileId, "/devices/${targetDeviceId.encodeURLPathPart()}")) {
                bearerAuth(deviceToken)
            }
        }
    }

    override suspend fun getSnapshot(profileId: String, deviceToken: String): RemoteSnapshot {
        require(deviceToken.isNotBlank()) { "Device token is required" }
        return request {
            client.get(profilePath(profileId, "/snapshot")) {
                bearerAuth(deviceToken)
            }
        }
    }

    /** Upload an already-encrypted envelope using revision compare-and-swap. */
    override suspend fun putSnapshot(
        profileId: String,
        deviceToken: String,
        expectedRevision: Long,
        schemaVersion: Int,
        ciphertext: String,
    ): Long {
        require(deviceToken.isNotBlank()) { "Device token is required" }
        require(expectedRevision >= 0) { "Snapshot revision cannot be negative" }
        require(schemaVersion == SyncSnapshot.CURRENT_SCHEMA_VERSION) { "Unsupported sync schema: $schemaVersion" }
        require(ciphertext.isNotBlank()) { "Encrypted snapshot is required" }
        val response: RevisionResponse = request {
            client.put(profilePath(profileId, "/snapshot")) {
                bearerAuth(deviceToken)
                contentType(ContentType.Application.Json)
                setBody(PutSnapshotRequest(expectedRevision, schemaVersion, ciphertext))
            }
        }
        return response.revision
    }

    override suspend fun deleteProfile(profileId: String, deviceToken: String) {
        require(deviceToken.isNotBlank()) { "Device token is required" }
        request<Unit> {
            client.delete(profilePath(profileId, "")) { bearerAuth(deviceToken) }
        }
    }

    override fun close() = client.close()

    private fun profilePath(profileId: String, suffix: String): String {
        require(profileId.isNotBlank() && profileId.length <= MAX_PROFILE_ID_LENGTH) { "Invalid sync profile ID" }
        return "$baseUrl/v1/profiles/${profileId.encodeURLPathPart()}$suffix"
    }

    private suspend inline fun <reified T> request(crossinline call: suspend () -> HttpResponse): T {
        val response = call()
        if (response.status == HttpStatusCode.Conflict) {
            val error = runCatching { response.body<ApiError>() }.getOrNull()
            if (error?.error == "revision_conflict") {
                throw RevisionConflict(error.revision ?: error("Conflict response omitted its revision"))
            }
        }
        if (!response.status.isSuccess()) {
            val error = runCatching { response.body<ApiError>() }.getOrNull()
            throw SyncApiException(response.status.value, error?.error ?: "http_error")
        }
        if (response.status == HttpStatusCode.NoContent) return Unit as T
        return response.body<T>()
    }

    companion object {
        private const val MAX_PROFILE_ID_LENGTH = 128
        private const val MAX_DEVICE_ID_LENGTH = 128
        private val clientJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Creates the shared OkHttp/JSON client used by Android and JVM clients. */
        fun create(baseUrl: String): SyncApiClient {
            val http = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
                install(ContentNegotiation) { json(clientJson) }
            }
            return SyncApiClient(http, baseUrl)
        }

        private fun validateBaseUrl(raw: String): String {
            val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("Invalid sync server URL") }
            val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Sync server URL must include a host")
            val localHttp = uri.scheme.equals("http", ignoreCase = true) && host in LOCAL_HTTP_HOSTS
            require(uri.scheme.equals("https", ignoreCase = true) || localHttp) {
                "Sync API requires HTTPS except for loopback development URLs"
            }
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "Sync server URL cannot contain credentials, query, or fragment"
            }
            return raw.trimEnd('/')
        }

        private val LOCAL_HTTP_HOSTS = setOf("localhost", "127.0.0.1", "::1")
    }
}

@Serializable
data class CreatedProfile(
    val profileId: String,
    val deviceId: String,
    val deviceToken: String,
    val revision: Long,
)

@Serializable
data class PairingTicket(val pairingToken: String, val expiresAt: String)

@Serializable
data class ClaimedDevice(val profileId: String, val deviceId: String, val deviceToken: String)

@Serializable
data class SyncDevice(val deviceId: String, val createdAt: String, val current: Boolean)

@Serializable
private data class DeviceListResponse(val devices: List<SyncDevice>)

@Serializable
data class SnapshotEnvelope(val schemaVersion: Int, val ciphertext: String)

@Serializable
data class RemoteSnapshot(val revision: Long, val snapshot: SnapshotEnvelope?)

@Serializable
private data class ClaimPairingRequest(val profileId: String, val pairingToken: String)

@Serializable
private data class PutSnapshotRequest(
    val expectedRevision: Long,
    val schemaVersion: Int,
    val ciphertext: String,
)

@Serializable
private data class RevisionResponse(val revision: Long)

@Serializable
private data class ApiError(val error: String, val revision: Long? = null)

class RevisionConflict(val currentRevision: Long) : Exception("Sync snapshot is stale; current revision is $currentRevision")

class SyncApiException(val statusCode: Int, val errorCode: String) :
    Exception("Sync API returned HTTP $statusCode ($errorCode)")
