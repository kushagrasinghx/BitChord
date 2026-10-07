package com.music.bitchord

import com.music.bitchord.download.OfflineDash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MPD reader, against the manifests that actually reach a download.
 *
 * Worth testing at this level because the failure it replaces was silent: a
 * `.mpd` used to be fetched as though it were audio and written into a file
 * named `.flac`, and nothing anywhere said so — the download reported success
 * and the 2.7 KB result simply refused to play. A parser that drops the last
 * segment or overstates a duration fails the same quiet way, so the numbers
 * below are checked against a real Tidal manifest rather than a made-up one.
 */
class OfflineDashTest {

    private companion object {
        /**
         * Tidal's manifest for The Weeknd's "Starboy" at `quality=LOSSLESS`,
         * September 2026, with the signed CloudFront URLs shortened. Everything
         * that matters to the reader — the timeline, the timescale, the
         * `$Number$` template and the escaped `&amp;` in the query — is
         * verbatim.
         */
        const val STARBOY = """<?xml version='1.0' encoding='UTF-8'?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011" """ +
            """profiles="urn:mpeg:dash:profile:isoff-main:2011" type="static" minBufferTime="PT3.993S" """ +
            """mediaPresentationDuration="PT3M50.461S"><Period id="0"><AdaptationSet id="0" contentType="audio" """ +
            """mimeType="audio/mp4" lang="und" group="main" segmentAlignment="true">""" +
            """<Representation id="FLAC,44100,16" codecs="flac" bandwidth="928340" audioSamplingRate="44100">""" +
            """<SegmentTemplate timescale="44100" """ +
            """initialization="https://sp-ad-cf.audio.tidal.com/mediatracks/BLOB/0.mp4?Policy=P&amp;Signature=S" """ +
            """media="https://sp-ad-cf.audio.tidal.com/mediatracks/BLOB/${'$'}Number${'$'}.mp4?Policy=P&amp;Signature=S" """ +
            """startNumber="1"><SegmentTimeline><S d="176128" r="56"/><S d="124050"/></SegmentTimeline>""" +
            """</SegmentTemplate></Representation></AdaptationSet></Period></MPD>"""
    }

    @Test
    fun `a manifest is recognised by its extension, query string and all`() {
        assertTrue(OfflineDash.handles("https://im-cf.manifest.tidal.com/1/manifests/AbC.mpd?Expires=1&Signature=x"))
        assertTrue(OfflineDash.handles("https://host/a.MPD"))
        assertFalse(OfflineDash.handles("https://im-cf.manifest.tidal.com/1/manifests/AbC.m3u8?Expires=1"))
        assertFalse(OfflineDash.handles("https://aac.saavncdn.com/820/abc_320.mp4"))
    }

    /**
     * `r` counts *repeats*, not segments, so `r="56"` is 57 of them. Read as a
     * count it silently truncates every download by one segment — four seconds
     * of the end of the song, on a file that otherwise looks complete.
     */
    @Test
    fun `the timeline expands to one entry per segment`() {
        val plan = OfflineDash.parse(STARBOY)
        assertEquals(58, plan.media.size)
        assertEquals(58, plan.seconds.size)

        // 57 full segments at 176128/44100, then a short one at 124050/44100.
        assertEquals(3.994, plan.seconds.first(), 0.001)
        assertEquals(3.994, plan.seconds[56], 0.001)
        assertEquals(2.813, plan.seconds.last(), 0.001)

        // And they add up to the runtime the manifest states, which is the one
        // number that catches an off-by-one anywhere in the expansion.
        assertEquals(230.461, plan.seconds.sum(), 0.01)
    }

    /**
     * `$Number$` starts at `startNumber`, and the initialisation segment is a
     * separate URL rather than number zero — getting that wrong fetches the
     * init segment twice and loses the last one.
     */
    @Test
    fun `segment urls come off the template unescaped`() {
        val plan = OfflineDash.parse(STARBOY)
        assertEquals("https://sp-ad-cf.audio.tidal.com/mediatracks/BLOB/0.mp4?Policy=P&Signature=S", plan.initialization)
        assertEquals("https://sp-ad-cf.audio.tidal.com/mediatracks/BLOB/1.mp4?Policy=P&Signature=S", plan.media.first())
        assertEquals("https://sp-ad-cf.audio.tidal.com/mediatracks/BLOB/58.mp4?Policy=P&Signature=S", plan.media.last())
    }

    /**
     * The whole point of the conversion: what gets written is a playlist the
     * existing offline-package code already understands, so nothing else in
     * the download or playback paths has to learn about DASH.
     */
    @Test
    fun `the package is written as an HLS playlist over the saved segments`() {
        val text = OfflineDash.playlist(OfflineDash.parse(STARBOY))
        val lines = text.trim().lines()

        assertEquals("#EXTM3U", lines.first())
        assertTrue("""#EXT-X-MAP:URI="segment-00000.m4s"""" in lines)
        assertTrue("#EXT-X-TARGETDURATION:4" in lines)
        assertEquals("#EXT-X-ENDLIST", lines.last())

        // The init segment is 00000 and the media segments follow it, so the
        // numbering matches what OfflineHls writes for the same stream.
        assertTrue("segment-00001.m4s" in lines)
        assertTrue("segment-00058.m4s" in lines)
        assertFalse("segment-00059.m4s" in lines)
        assertEquals(58, lines.count { it.startsWith("#EXTINF:") })
        assertTrue("#EXTINF:3.994," in lines)
        assertTrue("#EXTINF:2.813," in lines)
    }

    /** Half a song is worse than a refusal, because only the refusal is recoverable. */
    @Test
    fun `manifests that cannot be saved whole are refused`() {
        val encrypted = STARBOY.replace("<SegmentTemplate", """<ContentProtection schemeIdUri="urn:x"/><SegmentTemplate""")
        assertEquals(
            "Encrypted DASH cannot be saved",
            runCatching { OfflineDash.parse(encrypted) }.exceptionOrNull()?.message,
        )

        val twoPeriods = STARBOY.replace("</MPD>", """<Period id="1"></Period></MPD>""")
        assertEquals(
            "Multi-period DASH cannot be saved",
            runCatching { OfflineDash.parse(twoPeriods) }.exceptionOrNull()?.message,
        )

        val timeBased = STARBOY.replace("${'$'}Number${'$'}", "${'$'}Time${'$'}")
        assertEquals(
            "Unsupported DASH media template",
            runCatching { OfflineDash.parse(timeBased) }.exceptionOrNull()?.message,
        )
    }
}
