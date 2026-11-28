package com.music.bitchord.data.proxy

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The proxy model has to be right before any request is made: a host that
 * slips through validation becomes a connection error much later, and a
 * credential that slips into [toString] becomes a leak in a bug report.
 */
class ProxyConfigTest {

    @Test
    fun `hostnames and addresses a proxy can live at are accepted`() {
        listOf(
            "proxy.example.com",
            "proxy",
            "localhost",
            "10.0.0.1",
            "192.0.2.10",
            "2001:db8::1",
            "[2001:db8::1]",
            "::1",
        ).forEach { host ->
            assertTrue(isValidProxyHost(host), "expected $host to be valid")
        }
    }

    @Test
    fun `things that are not a host are rejected`() {
        listOf(
            "",
            " ",
            "bad host",
            "http://proxy.example.com",
            "proxy.example.com:8080",
            "999.999.999.999",
            "256.0.0.1",
            "1.2.3",
            "-proxy.example.com",
            "proxy-.example.com",
            "proxy..example.com",
        ).forEach { host ->
            assertFalse(isValidProxyHost(host), "expected $host to be invalid")
        }
    }

    @Test
    fun `brackets around an IPv6 literal are removed for storage`() {
        assertEquals("2001:db8::1", normalizeProxyHost("[2001:db8::1]"))
        assertEquals("proxy.example.com", normalizeProxyHost("  proxy.example.com  "))
    }

    @Test
    fun `an enabled proxy needs a host and a port`() {
        assertEquals(
            ProxyValidationError.HOST_REQUIRED,
            ProxyConfig(enabled = true).validationError(),
        )
        assertEquals(
            ProxyValidationError.HOST_INVALID,
            ProxyConfig(enabled = true, host = "bad host", port = 8080).validationError(),
        )
        assertEquals(
            ProxyValidationError.PORT_REQUIRED,
            ProxyConfig(enabled = true, host = "proxy.example.com").validationError(),
        )
        assertEquals(
            ProxyValidationError.PORT_INVALID,
            ProxyConfig(enabled = true, host = "proxy.example.com", port = 70_000).validationError(),
        )
        assertNull(
            ProxyConfig(enabled = true, host = "proxy.example.com", port = 8080).validationError(),
        )
    }

    @Test
    fun `a disabled proxy may keep an unfinished endpoint`() {
        val disabled = ProxyConfig(enabled = false, host = "", port = 0)
        assertNull(disabled.validationError())
        assertFalse(disabled.isConfigured)
        assertFalse(disabled.isUsable)
    }

    @Test
    fun `protocol chooses the Java proxy type`() {
        val http = ProxyConfig(enabled = true, host = "proxy.example.com", port = 8080)
        assertEquals(Proxy.Type.HTTP, http.toJavaProxy().type())

        val https = http.copy(protocol = ProxyProtocol.HTTPS)
        assertEquals(Proxy.Type.HTTP, https.toJavaProxy().type())

        val socks = http.copy(protocol = ProxyProtocol.SOCKS5)
        assertEquals(Proxy.Type.SOCKS, socks.toJavaProxy().type())

        // The host is not resolved here: a name that only resolves through a
        // VPN must still be usable once the VPN is up.
        val address = http.toJavaProxy().address() as InetSocketAddress
        assertTrue(address.isUnresolved)
        assertEquals("proxy.example.com", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun `per-source mode decides whether an endpoint exists`() {
        val inherit = SourceProxy(mode = ProxyMode.INHERIT, host = "ignored.example", port = 8080)
        assertNull(inherit.customConfig())
        assertNull(inherit.validationError())

        val direct = inherit.copy(mode = ProxyMode.DIRECT)
        assertNull(direct.customConfig())

        val custom = inherit.copy(mode = ProxyMode.CUSTOM, host = "proxy.example.com")
        assertEquals("proxy.example.com", custom.customConfig()?.host)
        assertNull(custom.validationError())

        val incomplete = custom.copy(port = 0)
        assertEquals(ProxyValidationError.PORT_REQUIRED, incomplete.validationError())
    }

    @Test
    fun `credentials are redacted from logs`() {
        val config = ProxyConfig(
            enabled = true,
            host = "proxy.example.com",
            port = 8080,
            username = "alice",
            password = "hunter2",
        )
        val source = SourceProxy(
            mode = ProxyMode.CUSTOM,
            host = "proxy.example.com",
            port = 8080,
            username = "alice",
            password = "hunter2",
        )
        assertFalse(config.toString().contains("alice"))
        assertFalse(config.toString().contains("hunter2"))
        assertFalse(source.toString().contains("alice"))
        assertFalse(source.toString().contains("hunter2"))
    }

    @Test
    fun `a source proxy round-trips through the encrypted config's serializer`() {
        val original = SourceProxy(
            mode = ProxyMode.CUSTOM,
            protocol = ProxyProtocol.SOCKS5,
            host = "proxy.example.com",
            port = 1080,
            username = "alice",
            password = "hunter2",
        )
        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<SourceProxy>(encoded)
        assertEquals(original, decoded)
    }
}
