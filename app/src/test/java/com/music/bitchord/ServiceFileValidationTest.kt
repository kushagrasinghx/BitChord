package com.music.bitchord

import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.service.ServiceConfig
import com.music.bitchord.data.service.ServiceFile
import com.music.bitchord.data.service.ServiceFileRequired
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The service file carries every endpoint and identity; the repo ships none.
 * Fixtures use RFC 2606 non-routable names throughout — no real host may
 * appear here, in any test, ever.
 */
class ServiceFileValidationTest {

    private fun fileText(
        musicBase: String = "https://music.example.invalid/inner/v1",
        tubeBase: String = "https://tube.example.invalid/inner/v1",
        musicOrigin: String = "https://music.example.invalid",
        tubeOrigin: String = "https://tube.example.invalid",
        loginOrigin: String = "https://login.example.invalid",
        loginUrl: String = "https://login.example.invalid/signin",
        cookieOrigins: String = "\"login.example.invalid\", \"tube.example.invalid\"",
        clients: String = CLIENTS,
        order: String = "\"ALPHA\", \"BETA\"",
        suffix: String = "stream.example.invalid",
        hosts: String = "\"music.example.invalid\", \"tube.example.invalid\"",
        short: String = "short.example.invalid",
        share: String = "https://music.example.invalid",
        sw: String = "https://tube.example.invalid/sw_data",
        app: String = "bitchord-service",
        version: Int = 1,
        catalogue: String = "null",
    ): String = """
        {
          "app": "$app",
          "configVersion": $version,
          "endpoints": {
            "musicBase": "$musicBase",
            "tubeBase": "$tubeBase",
            "musicOrigin": "$musicOrigin",
            "tubeOrigin": "$tubeOrigin"
          },
          "login": {
            "origin": "$loginOrigin",
            "loginUrl": "$loginUrl",
            "cookieOrigins": [$cookieOrigins]
          },
          "webClient": {
            "name": "WEB",
            "version": "9.9",
            "id": "99",
            "ua": "test-ua/9.9",
            "stats": {"cplayer": "P", "cbr": "B", "cbrver": "1", "cos": "O", "cosver": "2"}
          },
          "playerClients": [$clients],
          "clientOrder": [$order],
          "streamHostSuffix": "$suffix",
          "links": {
            "hosts": [$hosts],
            "shortHost": "$short",
            "shareOrigin": "$share"
          },
          "swDataUrl": "$sw",
          "catalogue": $catalogue
        }
    """.trimIndent()

    @After
    fun tearDown() {
        ServiceConfig.clearForTests()
    }

    @Test fun `valid file validates and serves getters`() {
        val file = ServiceConfig.validate(fileText()).getOrThrow()
        ServiceConfig.installForTests(file)

        assertEquals("https://music.example.invalid/inner/v1", ServiceConfig.musicBase())
        assertEquals("https://tube.example.invalid/inner/v1", ServiceConfig.tubeBase())
        val ordered = ServiceConfig.orderedClients()
        assertEquals(listOf("ALPHA", "BETA"), ordered.map { it.clientName })
        assertTrue(ordered.first() is PlayerClient)
        assertTrue(ServiceConfig.isStreamHost("https://media.stream.example.invalid/video?c=ALPHA"))
        assertTrue(ServiceConfig.isStreamHost("https://stream.example.invalid/video"))
        assertFalse(ServiceConfig.isStreamHost("https://evil.example.com/video"))
        assertEquals("BETA", ServiceConfig.clientForUrl("https://h/video?c=BETA&cver=1").clientName)
        // Unknown client falls back to the first ordered one, never nothing.
        assertEquals("ALPHA", ServiceConfig.clientForUrl("https://h/video").clientName)
        assertEquals(
            "https://music.example.invalid/watch?v=abc",
            ServiceConfig.watchUrl("abc"),
        )
        assertEquals(
            setOf("music.example.invalid", "tube.example.invalid", "short.example.invalid"),
            ServiceConfig.linkHosts(),
        )
        assertEquals("WEB", ServiceConfig.webName())
    }

    @Test fun `fingerprint is stable hex`() {
        // importText is storage-optional: with no prefs it still installs.
        val summary = ServiceConfig.importText(fileText()).getOrThrow()
        assertEquals(16, summary.fingerprint.length)
        assertTrue(summary.fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(
            summary.fingerprint,
            ServiceConfig.importText(fileText()).getOrThrow().fingerprint,
        )
    }

    @Test fun `web player client is browser shaped on the music base`() {
        ServiceConfig.installForTests(ServiceConfig.validate(fileText()).getOrThrow())
        val web = ServiceConfig.webPlayerClient()
        assertEquals("WEB", web.clientName)
        assertEquals("https://music.example.invalid", web.origin)
        assertTrue(web.apiBaseMusic)
        assertTrue(web.needsSignatureTimestamp)
    }

    @Test fun `non files are refused`() {
        assertTrue(ServiceConfig.validate("hello").isFailure)
        assertTrue(ServiceConfig.validate(fileText(app = "other-app")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(version = 999)).isFailure)
    }

    @Test fun `unsafe endpoints are refused`() {
        assertTrue(ServiceConfig.validate(fileText(musicBase = "http://music.example.invalid/v1")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(tubeBase = "https://user@music.example.invalid/v1")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(musicOrigin = "https://192.168.1.9")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(tubeOrigin = "https://localhost/")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(loginOrigin = "https://box.local/")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(sw = "https://127.0.0.1/sw")).isFailure)
        // Non-URL where a URL belongs.
        assertTrue(ServiceConfig.validate(fileText(share = "not a url")).isFailure)
    }

    @Test fun `bad tables are refused`() {
        assertTrue(ServiceConfig.validate(fileText(clients = "")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(order = "\"GHOST\"")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(order = "")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(suffix = "not a suffix!!")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(suffix = "192.168.0.1")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(hosts = "")).isFailure)
        assertTrue(
            ServiceConfig.validate(
                fileText(clients = CLIENTS.replace("\"music\"", "\"mars\"")),
            ).isFailure,
        )
    }

    @Test fun `cookie origins must be bare public hosts`() {
        assertTrue(ServiceConfig.validate(fileText(cookieOrigins = "\"https://login.example.invalid\"")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(cookieOrigins = "\"10.0.0.9\"")).isFailure)
        assertTrue(ServiceConfig.validate(fileText(cookieOrigins = "")).isFailure)
    }

    @Test fun `auth guard admits suffixes and subdomains only`() {
        assertTrue(ServiceConfig.canSendAuth("google.com"))
        assertTrue(ServiceConfig.canSendAuth("accounts.google.com"))
        assertTrue(ServiceConfig.canSendAuth("youtube.com"))
        assertTrue(ServiceConfig.canSendAuth("music.youtube.com"))
        assertTrue(ServiceConfig.canSendAuth("x.googlevideo.com"))
        assertFalse(ServiceConfig.canSendAuth("evil.com"))
        assertFalse(ServiceConfig.canSendAuth("notyoutube.com"))
        assertFalse(ServiceConfig.canSendAuth("youtube.com.evil.com"))
        assertFalse(ServiceConfig.canSendAuth("google.com.evil.com"))
        assertFalse(ServiceConfig.canSendAuth(""))
    }

    @Test fun `getters throw ServiceFileRequired while empty`() {
        try {
            ServiceConfig.musicBase()
            fail("expected ServiceFileRequired")
        } catch (e: ServiceFileRequired) {
            assertTrue(e.message!!.contains("Service file required"))
        }
        try {
            ServiceConfig.orderedClients()
            fail("expected ServiceFileRequired")
        } catch (e: ServiceFileRequired) {
            // expected
        }
    }

    @Test fun `installed file reports its fingerprint`() {
        val file = ServiceConfig.validate(fileText()).getOrThrow()
        ServiceConfig.installForTests(file)
        // Test installs carry a fixed marker; real fingerprints come from importText.
        assertEquals("test", ServiceConfig.requireReady().fingerprint)
    }

    @Test fun `import links must be public https`() {
        assertTrue(ServiceConfig.checkImportUrl("https://example.invalid/bitchord-service.json").isSuccess)
        assertTrue(ServiceConfig.checkImportUrl("  https://example.invalid/f.json  ").isSuccess)
        assertTrue(ServiceConfig.checkImportUrl("http://example.invalid/f.json").isFailure)
        assertTrue(ServiceConfig.checkImportUrl("https://user@example.invalid/f.json").isFailure)
        assertTrue(ServiceConfig.checkImportUrl("https://192.168.1.9/f.json").isFailure)
        assertTrue(ServiceConfig.checkImportUrl("https://localhost/f.json").isFailure)
        assertTrue(ServiceConfig.checkImportUrl("https://box.local/f.json").isFailure)
        assertTrue(ServiceConfig.checkImportUrl("not a url").isFailure)
        assertTrue(ServiceConfig.checkImportUrl("").isFailure)
    }

    @Test fun `catalogue block validates when present`() {
        val file = ServiceConfig.validate(fileText(catalogue = CATALOGUE_JSON)).getOrThrow()
        ServiceConfig.installForTests(file)
        val catalogue = ServiceConfig.catalogue()
        assertEquals("https://catalogue.example.invalid/api", catalogue.apiBase)
        assertEquals("search.getResults", catalogue.searchCall)
        assertEquals("song.getDetails", catalogue.detailsCall)
        assertEquals("01234567", catalogue.urlKey)
    }

    @Test fun `bad catalogue blocks are refused`() {
        assertTrue(
            ServiceConfig.validate(fileText(catalogue = CATALOGUE_JSON.replace("search.getResults", "bad call!!"))).isFailure,
        )
        assertTrue(
            ServiceConfig.validate(fileText(catalogue = CATALOGUE_JSON.replace("01234567", "short"))).isFailure,
        )
        assertTrue(
            ServiceConfig.validate(
                fileText(catalogue = CATALOGUE_JSON.replace("https://catalogue.example.invalid/api", "http://catalogue.example.invalid/api")),
            ).isFailure,
        )
        assertTrue(
            ServiceConfig.validate(
                fileText(catalogue = CATALOGUE_JSON.replace("203.0.113.9", "10.0.0.9")),
            ).isFailure,
        )
    }

    @Test fun `catalogue accessor requires the block`() {
        ServiceConfig.installForTests(ServiceConfig.validate(fileText()).getOrThrow())
        try {
            ServiceConfig.catalogue()
            fail("expected ServiceFileRequired")
        } catch (e: ServiceFileRequired) {
            // expected: file present, catalogue section absent
        }
    }

    companion object {
        private const val CATALOGUE_JSON = """
            {
              "apiBase": "https://catalogue.example.invalid/api",
              "searchCall": "search.getResults",
              "detailsCall": "song.getDetails",
              "urlKey": "01234567",
              "userAgent": "test-catalogue/1.0",
              "forwardedFor": "203.0.113.9",
              "cookie": "explicit_content=1"
            }
        """

        private const val CLIENTS = """
            {
              "name": "ALPHA", "version": "1.0", "id": "10", "ua": "alpha/1.0",
              "osName": "TestOS", "osVersion": "1", "deviceMake": "Test",
              "deviceModel": "T1", "apiBase": "music"
            },
            {
              "name": "BETA", "version": "2.0", "id": "20", "ua": "beta/2.0",
              "origin": "https://music.example.invalid",
              "apiBase": "tube", "needsSignatureTimestamp": true
            }
        """
    }
}
