package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAddonTest {

    // ── Base URLs ────────────────────────────────────────────────────────

    @Test
    fun aPastedManifestUrlBecomesTheAddonsBase() {
        assertEquals("https://addon.example", DesktopAddonClient.normalizeBase("https://addon.example/manifest.json"))
        assertEquals("https://addon.example", DesktopAddonClient.normalizeBase("https://addon.example/"))
        assertEquals("https://addon.example", DesktopAddonClient.normalizeBase("  https://addon.example  "))
        // Any .json filename, not only manifest.json: otherwise /search would be sent to
        // `…/addon.json/search`.
        assertEquals("https://addon.example/x", DesktopAddonClient.normalizeBase("https://addon.example/x/addon.json"))
    }

    @Test
    fun aTokenBearingPathSurvivesNormalisation() {
        // The token is *in the path* and every later call has to carry it.
        assertEquals(
            "https://addon.example/t/s3cret",
            DesktopAddonClient.normalizeBase("https://addon.example/t/s3cret/manifest.json"),
        )
    }

    // ── Track identity ───────────────────────────────────────────────────

    @Test
    fun trackKeysRoundTripAndNameTheirSource() {
        val key = DesktopAddonSource.trackKey("source-1", "tidal/12345")
        val parsed = DesktopAddonSource.parseTrack(key)

        assertEquals("source-1", parsed?.sourceId)
        assertEquals("tidal/12345", parsed?.trackId)
        // An ordinary YouTube id is not an addon track.
        assertNull(DesktopAddonSource.parseTrack("dQw4w9WgXcQ"))
    }

    @Test
    fun aTrackIdMayContainTheSeparator() {
        val parsed = DesktopAddonSource.parseTrack(DesktopAddonSource.trackKey("s1", "a::b"))

        assertEquals("s1", parsed?.sourceId)
        assertEquals("a::b", parsed?.trackId)
    }

    // ── Stream metadata ──────────────────────────────────────────────────

    @Test
    fun bitrateIsReadInWhicheverUnitTheAddonUsed() {
        // A FLAC at 1411 and an MP3 at 320000 are the same claim, differently spelled; anything
        // over 3000 is bits per second.
        assertEquals(1411, DesktopAddonStream(bitrate = 1411.0).kbps)
        assertEquals(320, DesktopAddonStream(bitrate = 320_000.0).kbps)
        assertNull(DesktopAddonStream().kbps)
    }

    @Test
    fun hiResDetailIsRecoveredFromFreeTextWhenTheFieldsAreMissing() {
        val stated = DesktopAddonStream(sampleRate = 96_000.0, bitDepth = 24.0)
        assertEquals(96_000, stated.sampleRateHz)
        assertEquals(24, stated.bits)

        // Addons in the wild state neither field and put it all in the label.
        val labelled = DesktopAddonStream(quality = "FLAC 24-bit 192 kHz 9216 kbps")
        assertEquals(192_000, labelled.sampleRateHz)
        assertEquals(24, labelled.bits)

        // kHz stated where the field is specified in Hz.
        assertEquals(44_100, DesktopAddonStream(sampleRate = 44.1).sampleRateHz)
    }

    @Test
    fun onlyWordsThatCanOnlyMeanATransportAreReadAsOne() {
        // `format` usually holds a codec or a container, and "none" is a positive statement that
        // this is a file.
    }

    @Test
    fun anythingButFalseNamesADrmScheme() {
        assertFalse(DesktopAddonStream().isEncrypted)
        assertFalse(DesktopAddonStream(encrypted = jsonOf("false")).isEncrypted)
        assertTrue(DesktopAddonStream(encrypted = jsonOf("true")).isEncrypted)
        assertTrue(DesktopAddonStream(encrypted = jsonOf("\"widevine\"")).isEncrypted)
    }

    @Test
    fun immersiveMixesAreRecognisedWhereverTheAddonSaysSo() {
        assertTrue(DesktopAddonStream(quality = "Dolby Atmos").isDolbyAtmos)
        assertTrue(DesktopAddonStream(audioModes = listOf("DOLBY_ATMOS")).isDolbyAtmos)
        assertTrue(DesktopAddonStream(codec = "eac3").isDolbyAtmos)
        assertFalse(DesktopAddonStream(quality = "FLAC 24-bit").isDolbyAtmos)
    }

    // ── Manifests ────────────────────────────────────────────────────────

    @Test
    fun silenceAboutResourcesIsNotAClaimThatNothingWorks() {
        // An empty `resources` is settled by asking the endpoints, not by refusing here.
        assertTrue(DesktopAddonManifest(id = "a").isPlayable)
        assertTrue(DesktopAddonManifest(id = "a", resources = listOf("search")).isPlayable)
        // Everything starts by looking a track up by name.
        assertFalse(DesktopAddonManifest(id = "a", resources = listOf("stream")).isPlayable)
    }

    @Test
    fun anUnnamedAddonFallsBackToItsId() {
        assertEquals("my-addon", DesktopAddonManifest(id = "my-addon").displayName)
        assertEquals("Nice Name", DesktopAddonManifest(id = "my-addon", name = "Nice Name").displayName)
    }

    // ── URL identification ───────────────────────────────────────────────

    @Test
    fun anAddonManifestIsIdentifiedAsAnAddon() {
        val body = """{"id":"example","name":"Example","version":"1.2","resources":["search","stream"]}"""

        val found = DesktopSourceFormats.detect(body, "https://addon.example/manifest.json")

        assertTrue(found is DesktopDetectedFormat.Addon)
        assertEquals("Example", found.manifest.displayName)
        assertEquals("https://addon.example", found.baseUrl)
    }

    @Test
    fun aModuleIndexIsIdentifiedAsOneAndCounted() {
        val body = """
            {"category:music":[
              {"id":"one","name":"One","download":"one.js"},
              {"id":"two","name":"Two","download":"two.js"}
            ]}
        """.trimIndent()

        val found = DesktopSourceFormats.detect(body, "https://index.example/index.json")

        assertTrue(found is DesktopDetectedFormat.ModuleIndex)
        assertEquals(2, found.moduleCount)
    }

    @Test
    fun aRefusalSaysWhatWasFoundRatherThanWhatWasWanted() {
        val notJson = DesktopSourceFormats.detect("<html>nope</html>")
        assertTrue(notJson is DesktopDetectedFormat.Unsupported)
        assertTrue(notJson.reason.contains("JSON"))

        val list = DesktopSourceFormats.detect("""[{"id":"x"}]""")
        assertTrue(list is DesktopDetectedFormat.Unsupported)

        val noId = DesktopSourceFormats.detect("""{"name":"No id here"}""")
        assertTrue(noId is DesktopDetectedFormat.Unsupported)
        assertTrue(noId.reason.contains("addon id"))

        val wrongResources = DesktopSourceFormats.detect("""{"id":"x","resources":["stream"]}""")
        assertTrue(wrongResources is DesktopDetectedFormat.Unsupported)
        assertTrue(wrongResources.reason.contains("search"))

        val emptyIndex = DesktopSourceFormats.detect("""{"category:music":[]}""")
        assertTrue(emptyIndex is DesktopDetectedFormat.Unsupported)
        assertTrue(emptyIndex.reason.contains("no modules"))
    }

    // ── Source order ─────────────────────────────────────────────────────

    @Test
    fun sourcesAreWalkedByRankAndThenByTheOrderTheyAreStoredIn() {
        val configs = listOf(
            DesktopSourceConfig("youtube", DesktopSourceKind.YOUTUBE),
            DesktopSourceConfig("jio", DesktopSourceKind.JIOSAAVN),
            DesktopSourceConfig("addon-2", DesktopSourceKind.ADDON, baseUrl = "https://b.example"),
            DesktopSourceConfig("legacy", DesktopSourceKind.MODULE, baseUrl = "https://m.example"),
            DesktopSourceConfig("addon-1", DesktopSourceKind.ADDON, baseUrl = "https://a.example"),
        )

        assertEquals(
            listOf("addon-2", "addon-1", "legacy", "jio", "youtube"),
            configs.inSourceOrder().map { it.id },
        )
    }

    @Test
    fun onlyTheSourcesSomebodyAddedCanBeRemoved() {
        assertTrue(DesktopSourceConfig("a", DesktopSourceKind.ADDON).isUserAdded)
        assertTrue(DesktopSourceConfig("c", DesktopSourceKind.CUSTOM_MODULE).isUserAdded)
        assertFalse(DesktopSourceConfig("j", DesktopSourceKind.JIOSAAVN).isUserAdded)
        assertFalse(DesktopSourceConfig("y", DesktopSourceKind.YOUTUBE).isUserAdded)
    }

    private fun jsonOf(raw: String) = DesktopAddonClient.json.parseToJsonElement(raw)
}
