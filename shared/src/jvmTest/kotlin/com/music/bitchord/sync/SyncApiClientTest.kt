package com.music.bitchord.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncApiClientTest {
    @Test
    fun createProfileUsesVersionedRouteAndNeverSendsBearerToken() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(201, """{"profileId":"p-1","deviceId":"d-1","deviceToken":"secret","revision":0}"""))
            val client = testClient(server)

            val created = client.createProfile()
            val request = server.takeRequest()

            assertEquals("p-1", created.profileId)
            assertEquals("POST", request.method)
            assertEquals("/sync/v1/profiles", request.path)
            assertFalse(request.headers.names().any { it.equals("Authorization", ignoreCase = true) })
            assertEquals("{}", request.body.readUtf8())
            client.close()
        }
    }

    @Test
    fun snapshotFetchUsesBearerHeaderAndParsesOpaqueCiphertext() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(200, """{"revision":7,"snapshot":{"schemaVersion":1,"ciphertext":"base64-ciphertext"}}"""))
            val client = testClient(server)

            val snapshot = client.getSnapshot("p-1", "device-secret")
            val request = server.takeRequest()

            assertEquals(7L, snapshot.revision)
            assertEquals("base64-ciphertext", snapshot.snapshot?.ciphertext)
            assertEquals("Bearer device-secret", request.getHeader("Authorization"))
            assertEquals("/sync/v1/profiles/p-1/snapshot", request.path)
            client.close()
        }
    }

    @Test
    fun staleRevisionBecomesTypedConflictAndSendsOnlyCiphertextEnvelope() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(409, """{"error":"revision_conflict","revision":9}"""))
            val client = testClient(server)

            val error = assertThrows(RevisionConflict::class.java) {
                runBlocking { client.putSnapshot("p-1", "device-secret", 8, 1, "encrypted-only") }
            }
            val request = server.takeRequest()
            assertEquals(9L, error.currentRevision)
            assertEquals("Bearer device-secret", request.getHeader("Authorization"))
            assertEquals(
                """{"expectedRevision":8,"schemaVersion":1,"ciphertext":"encrypted-only"}""",
                request.body.readUtf8(),
            )
            client.close()
        }
    }

    @Test
    fun pairingUsesBearerForIssuanceAndOneTimeTokenForClaim() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(201, """{"pairingToken":"once","expiresAt":"2030-01-01T00:00:00Z"}"""))
            server.enqueue(jsonResponse(201, """{"profileId":"p-1","deviceId":"d-2","deviceToken":"new-secret"}"""))
            val client = testClient(server)

            assertEquals("once", client.createPairing("p-1", "device-secret").pairingToken)
            val issueRequest = server.takeRequest()
            assertEquals("POST", issueRequest.method)
            assertEquals("/sync/v1/profiles/p-1/pairings", issueRequest.path)
            assertEquals("Bearer device-secret", issueRequest.getHeader("Authorization"))
            assertEquals("{}", issueRequest.body.readUtf8())

            val claimed = client.claimPairing("p-1", "once")
            val claimRequest = server.takeRequest()
            assertEquals("d-2", claimed.deviceId)
            assertEquals("/sync/v1/pairings/claim", claimRequest.path)
            assertFalse(claimRequest.headers.names().any { it.equals("Authorization", ignoreCase = true) })
            assertEquals("""{"profileId":"p-1","pairingToken":"once"}""", claimRequest.body.readUtf8())
            client.close()
        }
    }

    @Test
    fun listsDevicesAndCanRevokeAnotherDeviceWithBearer() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(200, """{"devices":[{"deviceId":"d-1","createdAt":"2026-11-01T12:00:00Z","current":true},{"deviceId":"d-2","createdAt":"2026-11-02T12:00:00Z","current":false}]}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val client = testClient(server)

            val devices = client.listDevices("p-1", "device-secret")
            assertEquals(2, devices.size)
            assertEquals("d-1", devices.first().deviceId)
            assertTrue(devices.first().current)
            assertFalse(devices.last().current)
            val listRequest = server.takeRequest()
            assertEquals("GET", listRequest.method)
            assertEquals("/sync/v1/profiles/p-1/devices", listRequest.path)
            assertEquals("Bearer device-secret", listRequest.getHeader("Authorization"))

            client.revokeDevice("p-1", "device-secret", "d-2")
            val revokeRequest = server.takeRequest()
            assertEquals("DELETE", revokeRequest.method)
            assertEquals("/sync/v1/profiles/p-1/devices/d-2", revokeRequest.path)
            assertEquals("Bearer device-secret", revokeRequest.getHeader("Authorization"))
            client.close()
        }
    }

    @Test
    fun profileDeletionUsesBearerAndAcceptsNoContent() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            val client = testClient(server)

            client.deleteProfile("p-1", "device-secret")
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/sync/v1/profiles/p-1", request.path)
            assertEquals("Bearer device-secret", request.getHeader("Authorization"))
            client.close()
        }
    }

    @Test
    fun refusesCleartextRemoteSyncEndpoints() {
        val http = HttpClient(OkHttp)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                SyncApiClient(http, "http://sync.example.com")
            }
        } finally {
            http.close()
        }
    }

    private fun testClient(server: MockWebServer): SyncApiClient {
        val http = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }
        return SyncApiClient(http, server.url("/sync").toString().trimEnd('/'))
    }

    private fun jsonResponse(status: Int, body: String) =
        MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json").setBody(body)
}
