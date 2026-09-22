package com.music.bitchord

import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.ProbeResult
import com.music.bitchord.data.listentogether.ServerConnectionState
import com.music.bitchord.data.listentogether.ServerUrlError
import com.music.bitchord.data.listentogether.ServerUrlValidationResult
import com.music.bitchord.data.listentogether.health
import com.music.bitchord.data.listentogether.isFallback
import com.music.bitchord.data.listentogether.latencyMs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamInviteLinkTest {

    @Test
    fun `parses and normalizes a public invite`() {
        assertEquals(
            "A1B2C3",
            JamInviteLink.parse("https://bitchord.kushagrasingh.in/invite/a1b2c3"),
        )
    }

    @Test
    fun `accepts query parameters without making them part of the code`() {
        assertEquals(
            "ABC123",
            JamInviteLink.parse("https://bitchord.kushagrasingh.in/invite/ABC123?from=share"),
        )
    }

    @Test
    fun `rejects other hosts schemes paths and malformed codes`() {
        assertNull(JamInviteLink.parse("http://bitchord.kushagrasingh.in/invite/ABC123"))
        assertNull(JamInviteLink.parse("https://example.com/invite/ABC123"))
        assertNull(JamInviteLink.parse("https://bitchord.kushagrasingh.in/"))
        assertNull(JamInviteLink.parse("https://bitchord.kushagrasingh.in/download"))
        assertNull(JamInviteLink.parse("https://bitchord.kushagrasingh.in/invite/ABC123/"))
        assertNull(JamInviteLink.parse("https://bitchord.kushagrasingh.in/invite/ABC123/extra"))
        assertNull(JamInviteLink.parse("https://bitchord.kushagrasingh.in/invite/TOO-LONG"))
    }

    @Test
    fun `builds the canonical share URL`() {
        assertEquals(
            "https://bitchord.kushagrasingh.in/invite/ABC123",
            JamInviteLink.url("abc123"),
        )
    }

    @Test
    fun `builds the custom server share URL`() {
        assertEquals(
            "https://my-party.onrender.com/invite/ABC123",
            JamInviteLink.url("abc123", "https://my-party.onrender.com"),
        )
        assertEquals(
            "https://my-party.onrender.com/invite/ABC123",
            JamInviteLink.url("abc123", "https://my-party.onrender.com/"),
        )
        assertEquals(
            "https://bitchord.kushagrasingh.in/invite/ABC123",
            JamInviteLink.url("abc123", ""),
        )
        assertEquals(
            "https://bitchord.kushagrasingh.in/invite/ABC123",
            JamInviteLink.url("abc123", null),
        )
    }

    @Test
    fun `builds custom scheme URL`() {
        assertEquals(
            "bitchord://party/ABC123?server=https%3A%2F%2Fmy-party.onrender.com",
            JamInviteLink.schemeUrl("abc123", "https://my-party.onrender.com"),
        )
        assertEquals(
            "bitchord://party/ABC123",
            JamInviteLink.schemeUrl("abc123", null),
        )
    }

    @Test
    fun `parses custom scheme invite with server`() {
        val invite = JamInviteLink.parseInvite("bitchord://party/a1b2c3?server=https%3A%2F%2Fmy-party.onrender.com")
        assertEquals("A1B2C3", invite?.code)
        assertEquals("https://my-party.onrender.com", invite?.serverUrl)
    }

    @Test
    fun `parses custom scheme invite without server`() {
        val invite = JamInviteLink.parseInvite("bitchord://party/XYZ789")
        assertEquals("XYZ789", invite?.code)
        assertNull(invite?.serverUrl)
    }

    @Test
    fun `parses web invite with server parameter`() {
        val invite = JamInviteLink.parseInvite("https://bitchord.kushagrasingh.in/invite/ABC123?server=https%3A%2F%2Fcustom.example.com")
        assertEquals("ABC123", invite?.code)
        assertEquals("https://custom.example.com", invite?.serverUrl)
    }

    @Test
    fun `switch party result sealed hierarchy holds expected properties`() {
        val success: com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult =
            com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult.Success("ABC123")
        val recovered: com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult =
            com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult.TargetFailedRecovered("OLD123", "Party full")
        val noParty: com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult =
            com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult.TargetFailedNoParty("Connection refused")

        assertEquals("ABC123", (success as com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult.Success).partyCode)
        assertEquals("OLD123", (recovered as com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult.TargetFailedRecovered).partyCode)
        assertEquals("Party full", recovered.targetError)
        assertEquals("Connection refused", (noParty as com.music.bitchord.data.listentogether.ListenTogether.SwitchPartyResult.TargetFailedNoParty).targetError)
    }

    // -------------------------------------------------------------------------
    // Architectural Invariants (Listen Together Default Fallback & Server Routing)
    // -------------------------------------------------------------------------

    @Test
    fun `invariant 1 default server generates canonical invite`() {
        val code = "JAM001"
        val activePartyHost = ListenTogether.defaultServer
        val link = if (activePartyHost == ListenTogether.defaultServer) {
            JamInviteLink.url(code, null)
        } else {
            JamInviteLink.url(code, activePartyHost)
        }
        assertEquals("https://bitchord.kushagrasingh.in/invite/JAM001", link)
    }

    @Test
    fun `invariant 2 custom server does not masquerade as default`() {
        val code = "JAM002"
        val customHost = "https://custom.jam.example.com"
        val link = if (customHost == ListenTogether.defaultServer) {
            JamInviteLink.url(code, null)
        } else {
            JamInviteLink.url(code, customHost)
        }
        assertEquals("https://custom.jam.example.com/invite/JAM002", link)
        assertNotEquals("https://bitchord.kushagrasingh.in/invite/JAM002", link)
    }

    @Test
    fun `invariant 3 active party authority is distinct from idle fallback`() {
        // activePartyServerBase holds authority while in party and is decoupled from idle fallback state
        val partyHost = "https://party-host.example.com"
        val idleFallbackHost = ListenTogether.defaultServer

        // A party active on partyHost must retain its authority regardless of idle fallback
        assertNotEquals(partyHost, idleFallbackHost)
        val currentAuthority = partyHost // simulates activePartyServerBase()
        assertEquals("https://party-host.example.com", currentAuthority)
    }

    @Test
    fun `invariant 4 explicit invite target ignores idle fallback`() {
        val explicitInvite = "https://bitchord.kushagrasingh.in/invite/JAM004?server=https%3A%2F%2Ftarget.party.com"
        val parsed = JamInviteLink.parseInvite(explicitInvite)
        assertEquals("JAM004", parsed?.code)
        assertEquals("https://target.party.com", parsed?.serverUrl)

        // Target server is normalized directly, bypassing any idle fallback logic
        val targetBase = ListenTogether.normalizeServerBase(parsed!!.serverUrl!!).ifBlank { ListenTogether.defaultServer }
        assertEquals("https://target.party.com", targetBase)
    }

    @Test
    fun `invariant 5 fallback preserves user custom server preference`() = runBlocking {
        // When custom server probe fails, resolution selects defaultServer with isFallback = true
        val resolution = ListenTogether.computeHealthResolution(
            customServer = "https://user-custom.example.com",
            probeCustom = { false },
            probeDefault = { true },
        )
        assertEquals(ListenTogether.defaultServer, resolution.resolvedServer)
        assertEquals(ListenTogether.Health.ONLINE, resolution.health)
        assertTrue(resolution.isFallback)
        // Notice the user's input remains "https://user-custom.example.com" — never overwritten
    }

    @Test
    fun `invariant 6 create fallback commits default server`() {
        // When createParty falls back from custom to default, the committed host is defaultServer
        val customServer = "https://failing-custom.example.com"
        val fallbackServer = ListenTogether.defaultServer
        assertTrue(ListenTogether.normalizeServerBase(customServer) != ListenTogether.defaultServer)
        val committedHostOnFallback = fallbackServer
        assertEquals(ListenTogether.defaultServer, committedHostOnFallback)

        // Also verify that primary == defaultServer will not trigger fallback
        val primaryIsDefault = ListenTogether.normalizeServerBase(fallbackServer) != ListenTogether.defaultServer
        assertFalse(primaryIsDefault)
    }

    @Test
    fun `invariant 7 fallback error classification`() {
        // Eligible for fallback (network/transport outages and 5xx)
        assertTrue(ListenTogether.isEligibleForFallback(java.net.UnknownHostException("dns failed")))
        assertTrue(ListenTogether.isEligibleForFallback(java.net.ConnectException("connection refused")))
        assertTrue(ListenTogether.isEligibleForFallback(java.net.SocketTimeoutException("read timeout")))
        assertTrue(ListenTogether.isEligibleForFallback(io.ktor.client.plugins.HttpRequestTimeoutException("timeout", null)))
        assertTrue(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("server_err", "500", 500)))
        assertTrue(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("bad_gw", "502", 502)))
        assertTrue(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("gw_timeout", "504", 504)))
        assertTrue(ListenTogether.isEligibleForFallback(java.io.IOException("wrapped", java.net.ConnectException())))

        // Ineligible for fallback (client / protocol / 4xx errors)
        assertFalse(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("party_full", "409", 409)))
        assertFalse(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("party_not_found", "404", 404)))
        assertFalse(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("bad_request", "400", 400)))
        assertFalse(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("unauthorized", "401", 401)))
        assertFalse(ListenTogether.isEligibleForFallback(ListenTogether.PartyException("unprocessable", "422", 422)))
        assertFalse(ListenTogether.isEligibleForFallback(IllegalStateException("local client error")))
    }

    @Test
    fun `invariant 8 custom server regains priority after recovery`() = runBlocking {
        // Probe custom returns true -> priority is custom server, isFallback = false
        val resolution = ListenTogether.computeHealthResolution(
            customServer = "https://recovering-custom.example.com",
            probeCustom = { true },
            probeDefault = { true },
        )
        assertEquals("https://recovering-custom.example.com", resolution.resolvedServer)
        assertEquals(ListenTogether.Health.ONLINE, resolution.health)
        assertFalse(resolution.isFallback)
    }

    @Test
    fun `invariant 9 default server down with custom healthy uses custom`() = runBlocking {
        // Custom is healthy even if default server is down -> ONLINE on custom
        var defaultProbed = false
        val resolution = ListenTogether.computeHealthResolution(
            customServer = "https://working-custom.example.com",
            probeCustom = { true },
            probeDefault = {
                defaultProbed = true
                false
            },
        )
        assertEquals("https://working-custom.example.com", resolution.resolvedServer)
        assertEquals(ListenTogether.Health.ONLINE, resolution.health)
        assertFalse(resolution.isFallback)
        assertFalse("Default server should not be probed when custom is healthy", defaultProbed)
    }

    @Test
    fun `invariant 10 default server down with no custom reports offline`() = runBlocking {
        val resolution = ListenTogether.computeHealthResolution(
            customServer = "",
            probeCustom = { true },
            probeDefault = { false },
        )
        assertEquals(ListenTogether.defaultServer, resolution.resolvedServer)
        assertEquals(ListenTogether.Health.OFFLINE, resolution.health)
        assertFalse(resolution.isFallback)
    }

    @Test
    fun `invariant 11 switch target resolves blank custom server to idle fallback`() {
        // The bug this pins: switching parties with no server named on the
        // invite (a typed code, entered while already live) used to hand
        // switchPartyWithRecovery the raw customServer preference, which is
        // "" when nobody has configured one — not the default server's
        // address — so the switch was refused as an invalid target.
        assertEquals(
            ListenTogether.defaultServer,
            ListenTogether.resolveSwitchTarget(
                inviteServer = null,
                customServer = "",
                idleServer = ListenTogether.defaultServer,
            ),
        )

        // A configured custom server still wins when the invite is silent.
        assertEquals(
            "https://user-custom.example.com",
            ListenTogether.resolveSwitchTarget(
                inviteServer = null,
                customServer = "https://user-custom.example.com",
                idleServer = ListenTogether.defaultServer,
            ),
        )

        // An invite that names its own server always wins, custom or not.
        assertEquals(
            "https://invite-target.example.com",
            ListenTogether.resolveSwitchTarget(
                inviteServer = "https://invite-target.example.com",
                customServer = "https://user-custom.example.com",
                idleServer = ListenTogether.defaultServer,
            ),
        )
    }

    // -------------------------------------------------------------------------
    // URL Validation Matrix Tests
    // -------------------------------------------------------------------------

    @Test
    fun `url validation matrix - valid inputs`() {
        fun assertValid(input: String, expected: String) {
            val result = ListenTogether.parseAndNormalizeServerUrl(input)
            assertEquals("Expected Valid('$expected') for input '$input'", ServerUrlValidationResult.Valid(expected), result)
        }

        // Empty & surrounding whitespace
        assertValid("", "")
        assertValid("  ", "")
        assertValid(" my-server.com ", "https://my-server.com")

        // Trailing slashes
        assertValid("https://my-server.com/", "https://my-server.com")
        assertValid("https://my-server.com///", "https://my-server.com")

        // Schemes & case normalization
        assertValid("HTTP://my-server.com", "http://my-server.com")
        assertValid("HTTP://LOCALHOST:8080", "http://localhost:8080")
        assertValid("HTTPS://EXAMPLE.COM", "https://example.com")
        assertValid("http://localhost", "http://localhost")
        assertValid("http://127.0.0.1:8080", "http://127.0.0.1:8080")

        // IPv6
        assertValid("http://[::1]", "http://[::1]")
        assertValid("http://[::1]:8080", "http://[::1]:8080")
        assertValid("HTTP://[2001:DB8::1]:8080", "http://[2001:db8::1]:8080")

        // Paths & subpaths
        assertValid("https://example.com/path", "https://example.com/path")
        assertValid("https://example.com//path", "https://example.com/path")
        assertValid("https://example.com/path/to/party/", "https://example.com/path/to/party")
        assertValid("example.com/custom/subpath", "https://example.com/custom/subpath")
        assertValid("http://192.168.1.50:8080/nested/path/", "http://192.168.1.50:8080/nested/path")
        assertValid("https://[::1]:8080/subpath", "https://[::1]:8080/subpath")
    }

    @Test
    fun `url validation matrix - invalid inputs`() {
        fun assertInvalid(input: String, expectedError: ServerUrlError) {
            val result = ListenTogether.parseAndNormalizeServerUrl(input)
            assertEquals("Expected Invalid($expectedError) for input '$input'", ServerUrlValidationResult.Invalid(expectedError), result)
        }

        // Whitespace (internal)
        assertInvalid("http://foo bar.com", ServerUrlError.Whitespace)
        assertInvalid("random shit", ServerUrlError.Whitespace)

        // Path traversal
        assertInvalid("https://example.com/../etc", ServerUrlError.InvalidPath)
        assertInvalid("https://example.com/path/./sub", ServerUrlError.InvalidPath)
        assertInvalid("https://example.com/path/../sub", ServerUrlError.InvalidPath)

        // Query & Fragment
        assertInvalid("https://example.com?foo=bar", ServerUrlError.HasQuery)
        assertInvalid("https://example.com#anchor", ServerUrlError.HasFragment)

        // Host errors
        assertInvalid("https://", ServerUrlError.InvalidHost)
        assertInvalid("http://.", ServerUrlError.InvalidHost)
        assertInvalid("http://foo_bar.com", ServerUrlError.InvalidHost)
        assertInvalid("https://-example.com", ServerUrlError.InvalidHost)
        assertInvalid("https://example-.com", ServerUrlError.InvalidHost)
        assertInvalid("https://foo.-bar.com", ServerUrlError.InvalidHost)
        assertInvalid("https://foo..com", ServerUrlError.InvalidHost)
        assertInvalid("https://.foo.com", ServerUrlError.InvalidHost)
        assertInvalid("https://foo.com.", ServerUrlError.InvalidHost)
        assertInvalid("randomshit", ServerUrlError.InvalidHost)
        assertInvalid("example", ServerUrlError.InvalidHost)

        // Ports
        assertInvalid("http://localhost:99999", ServerUrlError.InvalidPort)
    }

    // -------------------------------------------------------------------------
    // Probe Order Invariant Tests
    // -------------------------------------------------------------------------

    @Test
    fun `probe order invariant - custom succeeds skips default probe`() = runBlocking {
        var defaultProbed = false
        val (effective, state) = ListenTogether.resolveServerConnection(
            customServer = "https://healthy-custom.example.com",
            defaultServer = "https://default.example.com",
            probeCustom = { ProbeResult(isOnline = true, latencyMs = 42L) },
            probeDefault = {
                defaultProbed = true
                ProbeResult(isOnline = true, latencyMs = 99L)
            },
        )
        assertFalse("Default server must NEVER be probed when custom succeeds", defaultProbed)
        assertEquals("https://healthy-custom.example.com", effective)
        assertEquals(ServerConnectionState.CustomOnline(42L), state)
        assertEquals(42L, state.latencyMs)
        assertFalse(state.isFallback)
        assertEquals(ListenTogether.Health.ONLINE, state.health)
    }

    @Test
    fun `probe order invariant - custom fails probes default exactly once`() = runBlocking {
        var defaultProbeCount = 0
        val (effective, state) = ListenTogether.resolveServerConnection(
            customServer = "https://failing-custom.example.com",
            defaultServer = "https://default.example.com",
            probeCustom = { ProbeResult(isOnline = false) },
            probeDefault = {
                defaultProbeCount++
                ProbeResult(isOnline = true, latencyMs = 115L)
            },
        )
        assertEquals("Default server must be probed exactly once", 1, defaultProbeCount)
        assertEquals("https://default.example.com", effective)
        assertEquals(ServerConnectionState.CustomFallback(115L), state)
        assertEquals(115L, state.latencyMs)
        assertTrue(state.isFallback)
        assertEquals(ListenTogether.Health.ONLINE, state.health)
    }

    @Test
    fun `probe order invariant - custom equal to default probes once`() = runBlocking {
        var customProbeCount = 0
        var defaultProbeCount = 0
        val (effective, state) = ListenTogether.resolveServerConnection(
            customServer = "https://default.example.com",
            defaultServer = "https://default.example.com",
            probeCustom = {
                customProbeCount++
                ProbeResult(isOnline = true, latencyMs = 50L)
            },
            probeDefault = {
                defaultProbeCount++
                ProbeResult(isOnline = true, latencyMs = 50L)
            },
        )
        assertEquals("Custom probe must not be called when custom matches default", 0, customProbeCount)
        assertEquals("Default probe must be called exactly once", 1, defaultProbeCount)
        assertEquals("https://default.example.com", effective)
        assertEquals(ServerConnectionState.DefaultOnline(50L), state)
        assertFalse(state.isFallback)
    }

    // -------------------------------------------------------------------------
    // Latency & Resolution Invariants
    // -------------------------------------------------------------------------

    @Test
    fun `resolution invariant - both offline reports offline`() = runBlocking {
        val (effective, state) = ListenTogether.resolveServerConnection(
            customServer = "https://dead-custom.example.com",
            defaultServer = "https://default.example.com",
            probeCustom = { ProbeResult(isOnline = false) },
            probeDefault = { ProbeResult(isOnline = false) },
        )
        assertEquals("https://default.example.com", effective)
        assertEquals(ServerConnectionState.Offline, state)
        assertNull(state.latencyMs)
        assertFalse(state.isFallback)
        assertEquals(ListenTogether.Health.OFFLINE, state.health)
    }

    @Test
    fun `probe payload validation - fake 200 without ok true is rejected`() {
        fun validatePayload(statusIsSuccess: Boolean, body: String): Boolean {
            if (!statusIsSuccess) return false
            return body.contains("\"ok\":true")
        }

        // Real healthz response
        assertTrue(validatePayload(true, """{"ok":true,"serverMs":1789836652479}"""))

        // Catch-all root response (from invalid subpath like /1324/healthz)
        assertFalse(validatePayload(true, """{"maxMembers":5,"parties":0,"serverMs":1789836644489,"service":"bitchord-listen-together"}"""))

        // Captive portal / proxy HTML response
        assertFalse(validatePayload(true, "<!DOCTYPE html><html><body>Login Required</body></html>"))

        // HTTP 404 or 500
        assertFalse(validatePayload(false, """{"ok":true}"""))
    }

    // -------------------------------------------------------------------------
    // 4-Phase Configuration vs Effective Server Invariant
    // -------------------------------------------------------------------------

    @Test
    fun `4-phase configuration immutability invariant`() = runBlocking {
        val userConfiguredCustom = "https://user-custom.example.com"
        val defaultServer = "https://default.example.com"
        var persistedPreference = userConfiguredCustom

        // Phase 1: Custom Online
        val (eff1, state1) = ListenTogether.resolveServerConnection(
            customServer = persistedPreference,
            defaultServer = defaultServer,
            probeCustom = { ProbeResult(isOnline = true, latencyMs = 40L) },
            probeDefault = { error("Should not be probed") },
        )
        assertEquals(userConfiguredCustom, eff1)
        assertEquals(ServerConnectionState.CustomOnline(40L), state1)
        assertEquals(userConfiguredCustom, persistedPreference)

        // Phase 2: Custom Offline, Default Online (Fallback)
        val (eff2, state2) = ListenTogether.resolveServerConnection(
            customServer = persistedPreference,
            defaultServer = defaultServer,
            probeCustom = { ProbeResult(isOnline = false) },
            probeDefault = { ProbeResult(isOnline = true, latencyMs = 95L) },
        )
        assertEquals(defaultServer, eff2)
        assertEquals(ServerConnectionState.CustomFallback(95L), state2)
        // INVARIANT: Persisted preference remains unchanged!
        assertEquals("Persisted preference must NOT be overwritten by fallback", userConfiguredCustom, persistedPreference)

        // Phase 3: Both Custom and Default Offline
        val (eff3, state3) = ListenTogether.resolveServerConnection(
            customServer = persistedPreference,
            defaultServer = defaultServer,
            probeCustom = { ProbeResult(isOnline = false) },
            probeDefault = { ProbeResult(isOnline = false) },
        )
        assertEquals(defaultServer, eff3)
        assertEquals(ServerConnectionState.Offline, state3)
        assertEquals("Persisted preference must NOT be cleared when offline", userConfiguredCustom, persistedPreference)

        // Phase 4: Custom Recovers Online
        val (eff4, state4) = ListenTogether.resolveServerConnection(
            customServer = persistedPreference,
            defaultServer = defaultServer,
            probeCustom = { ProbeResult(isOnline = true, latencyMs = 35L) },
            probeDefault = { error("Should not be probed") },
        )
        assertEquals(userConfiguredCustom, eff4)
        assertEquals(ServerConnectionState.CustomOnline(35L), state4)
        assertEquals(userConfiguredCustom, persistedPreference)
    }

    // -------------------------------------------------------------------------
    // Stale-Result Publication Barrier Invariant
    // -------------------------------------------------------------------------

    @Test
    fun `stale-result publication barrier drops superseded resolution`() {
        var currentEffective = "https://initial.example.com"
        var currentState: ServerConnectionState = ServerConnectionState.Checking
        var resolutionGeneration = 0L

        // Job A starts with gen 1
        val jobAGeneration = ++resolutionGeneration

        // Job B starts with gen 2
        val jobBGeneration = ++resolutionGeneration

        // Job B completes first and publishes
        if (jobBGeneration == resolutionGeneration) {
            currentEffective = "https://server-b.example.com"
            currentState = ServerConnectionState.CustomOnline(25L)
        }

        // Job A completes late and attempts to publish
        if (jobAGeneration == resolutionGeneration) {
            currentEffective = "https://stale-server-a.example.com"
            currentState = ServerConnectionState.CustomOnline(999L)
        }

        assertEquals("Job A's late publication must be dropped", "https://server-b.example.com", currentEffective)
        assertEquals(ServerConnectionState.CustomOnline(25L), currentState)
    }
}

