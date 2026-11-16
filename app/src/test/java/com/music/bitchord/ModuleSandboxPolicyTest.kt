package com.music.bitchord

import com.music.bitchord.data.sources.module.ModuleSandboxPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleSandboxPolicyTest {

    @Test
    fun `public HTTPS module may reach public HTTPS endpoints`() {
        assertTrue(ModuleSandboxPolicy.allowsDestination("https://api.example.com/x", "https://modules.example"))
        assertFalse(ModuleSandboxPolicy.allowsDestination("http://api.example.com/x", "https://modules.example"))
        assertFalse(ModuleSandboxPolicy.allowsDestination("file:///etc/passwd", "https://modules.example"))
    }

    @Test
    fun `remote module cannot pivot into loopback, LAN or metadata endpoints`() {
        val base = "https://modules.example"
        assertFalse(ModuleSandboxPolicy.allowsDestination("http://127.0.0.1:8080", base))
        assertFalse(ModuleSandboxPolicy.allowsDestination("https://192.168.1.1/admin", base))
        assertFalse(ModuleSandboxPolicy.allowsDestination("https://10.0.0.5/", base))
        assertFalse(ModuleSandboxPolicy.allowsDestination("https://172.20.1.1/", base))
        assertFalse(ModuleSandboxPolicy.allowsDestination("https://169.254.169.254/latest/meta-data", base))
        assertFalse(ModuleSandboxPolicy.allowsDestination("https://[::1]/", base))
    }

    @Test
    fun `self-hosted module keeps access to its own local backend`() {
        val base = "http://192.168.1.10:9000"
        assertTrue(ModuleSandboxPolicy.allowsDestination("http://192.168.1.10:8080/api", base))
        assertTrue(ModuleSandboxPolicy.allowsDestination("http://localhost:8080/api", base))
        assertFalse(ModuleSandboxPolicy.allowsDestination("https://169.254.169.254/latest/meta-data", base))
    }

    @Test
    fun `cross-host redirects strip credentials but same-host keeps them`() {
        val headers = mapOf("Authorization" to "Bearer secret", "X-Api-Key" to "k", "User-Agent" to "M")
        assertEquals(headers, ModuleSandboxPolicy.headersForRedirect("https://a.example/x", "https://a.example/y", headers))
        assertEquals(
            mapOf("User-Agent" to "M"),
            ModuleSandboxPolicy.headersForRedirect("https://a.example/x", "https://b.example/y", headers),
        )
    }

    @Test
    fun `module downloads require HTTPS unless self-hosted`() {
        assertTrue(ModuleSandboxPolicy.allowsModuleDownload("https://modules.example/a.js"))
        assertFalse(ModuleSandboxPolicy.allowsModuleDownload("http://modules.example/a.js"))
        assertTrue(ModuleSandboxPolicy.allowsModuleDownload("http://127.0.0.1:8080/a.js"))
    }
}
