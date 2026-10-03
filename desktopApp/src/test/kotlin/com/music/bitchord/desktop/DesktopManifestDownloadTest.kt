package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A download whose URL is a manifest: recognised, and its best rendition chosen. */
class DesktopManifestDownloadTest {

    private fun rep(id: String, codecs: String, bandwidth: Int, last: Int) = """
        <Representation id="$id" codecs="$codecs" bandwidth="$bandwidth">
        <SegmentTemplate timescale="44100" initialization="https://cdn/$id/0.mp4?a=1&amp;b=2"
            media="https://cdn/$id/${'$'}Number${'$'}.mp4?a=1&amp;b=2" startNumber="1">
        <SegmentTimeline><S d="176128" r="2"/><S d="$last"/></SegmentTimeline></SegmentTemplate></Representation>
    """

    /** The shape Tidal served on 2026-10-01: HE-AAC first, FLAC last. */
    private val tidal = """<MPD type="static" mediaPresentationDuration="PT12S"><Period id="0"><AdaptationSet>
        ${rep("HEAACV1", "mp4a.40.5", 97002, 29960)}
        ${rep("AACLC", "mp4a.40.2", 321819, 26950)}
        ${rep("FLAC,44100,16", "flac", 900005, 24902)}
        </AdaptationSet></Period></MPD>"""

    @Test
    fun aManifestUrlIsNotTreatedAsAudio() {
        assertTrue(DesktopManifestDownload.isManifest(DesktopStream(url = "https://im-cf.manifest.tidal.com/1/x.mpd?token=a")))
        assertTrue(DesktopManifestDownload.isManifest(DesktopStream(url = "https://addon/dash/42", transport = "dash")))
        assertTrue(DesktopManifestDownload.isManifest(DesktopStream(url = "https://addon/x.m3u8")))
        assertFalse(DesktopManifestDownload.isManifest(DesktopStream(url = "https://cdn/track.flac")))
    }

    @Test
    fun keepsTheFlacRenditionNotTheFirstListed() {
        val plan = DesktopManifestDownload.parseDash(tidal)
        assertEquals("https://cdn/FLAC,44100,16/0.mp4?a=1&b=2", plan.initialization)
        assertEquals(4, plan.media.size)
        assertEquals("https://cdn/FLAC,44100,16/4.mp4?a=1&b=2", plan.media.last())
    }

    @Test
    fun withoutALosslessRenditionTakesTheHighestBandwidth() {
        val plan = DesktopManifestDownload.parseDash(
            tidal.replace(Regex("""<Representation id="FLAC.*?</Representation>""", RegexOption.DOT_MATCHES_ALL), ""),
        )
        assertEquals("https://cdn/AACLC/0.mp4?a=1&b=2", plan.initialization)
    }

    @Test
    fun hlsListsItsMapAndSegments() {
        val (init, media) = DesktopManifestDownload.hlsSegments(
            "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4.0,\nseg-1.m4s\n#EXTINF:4.0,\nseg-2.m4s\n#EXT-X-ENDLIST\n",
        )
        assertEquals("init.mp4", init)
        assertEquals(listOf("seg-1.m4s", "seg-2.m4s"), media)
    }

    @Test
    fun aFileThatIsNotFragmentedIsLeftAlone() {
        val notFragmented = byteArrayOf(0, 0, 0, 8) + "moov".toByteArray()
        assertSame(notFragmented, DesktopManifestDownload.Mp4.withSidx(notFragmented))
    }
}
