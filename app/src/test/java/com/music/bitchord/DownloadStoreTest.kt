package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.StreamRequest
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.SavedSongMetadata
import com.music.bitchord.download.resolvedDownloadDates
import com.music.bitchord.download.youtubeDownloadBadge
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions a download makes before a byte is fetched: what the file is
 * called, whether Android will keep one of it at all, what quality goes into it,
 * and whether the connection in hand is allowed to fetch it.
 *
 * The first two are worth pinning because both fail late and badly. A wrong
 * extension or MIME type is not a compile error and not a bad-sounding download
 * — it is `IllegalArgumentException: Unsupported MIME type` from inside a
 * `ContentResolver.insert`, several frames from anything naming the track, which
 * is precisely how every download in a build once failed while reporting itself
 * as a connection problem.
 *
 * The last two are worth pinning because they fail silently instead. A download
 * capped by the wrong setting is a file that plays perfectly and is not what was
 * asked for, and nothing about it looks wrong from outside.
 */
class DownloadStoreTest {

    private fun song(title: String, artist: String, videoId: String = "abc123") =
        Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = null)

    @Test
    fun `old download metadata remains readable without an added date`() {
        val old = Json.decodeFromString<SavedSongMetadata>(
            """{"videoId":"abc","title":"Song","artist":"Artist","uri":"file:///song.webm"}""",
        )

        assertNull(old.dateAddedSeconds)
    }

    @Test
    fun `existing downloads use file time when no creation time was recorded`() {
        val (added, modified) = resolvedDownloadDates(
            persistedAddedSeconds = null,
            fileModifiedMillis = 1_725_000_123_999,
        )

        assertEquals(1_725_000_123L, added)
        assertEquals(1_725_000_123L, modified)
    }

    @Test
    fun `persisted creation time survives later file modifications`() {
        val (added, modified) = resolvedDownloadDates(
            persistedAddedSeconds = 1_700_000_000,
            fileModifiedMillis = 1_725_000_123_999,
        )

        assertEquals(1_700_000_000L, added)
        assertEquals(1_725_000_123L, modified)
    }

    // ---- What Android will store -------------------------------------------

    @Test
    fun `flac and wav map to the types the media store actually accepts`() {
        val flac = DownloadStore.storable("flac")
        assertEquals("flac", flac?.extension)
        assertEquals("audio/flac", flac?.mimeType)

        // Not audio/x-wav's mirror image: the x- prefix is on the MIME type
        // here and not on the extension.
        val wav = DownloadStore.storable("wav")
        assertEquals("wav", wav?.extension)
        assertEquals("audio/x-wav", wav?.mimeType)
    }

    @Test
    fun `alac is filed as the mp4 it actually is`() {
        val alac = DownloadStore.storable("alac")
        assertEquals("m4a", alac?.extension)
        assertEquals("audio/mp4", alac?.mimeType)
    }

    @Test
    fun `codecs are matched however a source spells them`() {
        assertEquals("flac", DownloadStore.storable("FLAC")?.extension)
        assertEquals("flac", DownloadStore.storable(" x-flac ")?.extension)
    }

    @Test
    fun `an unknown or absent codec is nothing to file`() {
        // Every one of these falls the download through to YouTube's AAC, so
        // answering with a guess here would cost a file nothing can open.
        assertNull(DownloadStore.storable(null))
        assertNull(DownloadStore.storable(""))
        assertNull(DownloadStore.storable("opus"))
        assertNull(DownloadStore.storable("webm"))
        assertNull(DownloadStore.storable("dsf"))
    }

    @Test
    fun `youtube downloads retain their codec and resolved bitrate`() {
        assertEquals("OPUS · 160 kbps", youtubeDownloadBadge("webm", 160))
        assertEquals("AAC · 128 kbps", youtubeDownloadBadge("m4a", 128))
        assertNull(youtubeDownloadBadge("webm", 0))
    }

    // ---- What the file is called -------------------------------------------

    @Test
    fun `the name is artist then title, and carries the extension asked for`() {
        assertEquals(
            "Arijit Singh - Kesariya.flac",
            DownloadStore.fileNameFor(song("Kesariya", "Arijit Singh"), "flac"),
        )
    }

    @Test
    fun `characters a volume or a shell would object to are replaced`() {
        val name = DownloadStore.fileNameFor(song("A/B: C?", "D|E"), "m4a")
        assertEquals("D E - A B C.m4a", name)
    }

    @Test
    fun `a row with nothing to name it falls back to the video id`() {
        assertEquals("xyz789.m4a", DownloadStore.fileNameFor(song("", "", "xyz789"), "m4a"))
    }

    // ---- What quality is kept ----------------------------------------------

    /**
     * Only the top rung is worth a source's own file. The rungs below it are AAC
     * rungs, and YouTube's AAC ladder is the more reliable fetch for those — so
     * asking a module for a transcode it would have to make is worse on both
     * counts.
     */
    @Test
    fun `only lossless asks a source for the file it holds`() {
        assertEquals(
            StreamRequest.Lossless,
            SourceResolver.requestForDownload(DownloadQuality.LOSSLESS),
        )
        assertEquals(StreamRequest.Best, SourceResolver.requestForDownload(DownloadQuality.HIGH))
        assertEquals(
            StreamRequest.Capped(128),
            SourceResolver.requestForDownload(DownloadQuality.STANDARD),
        )
    }

    /**
     * High means "the best rung there is", and the sentinel that says so must
     * not survive into a request as a literal 2-billion-kbps cap — the
     * difference between a ceiling nothing exceeds and no ceiling at all is
     * invisible until something starts formatting the number.
     */
    @Test
    fun `high is uncapped rather than capped very high`() {
        assertEquals(Int.MAX_VALUE, DownloadQuality.HIGH.maxKbps)
        assertTrue(SourceResolver.requestForDownload(DownloadQuality.HIGH) !is StreamRequest.Capped)
    }

    /** Every rung has to say what it costs, or the picker shows a blank line. */
    @Test
    fun `every download rung is describable`() {
        DownloadQuality.entries.forEach { quality ->
            assertTrue(quality.label.isNotBlank())
            assertTrue(quality.detail.isNotBlank())
            assertTrue(quality.perTrack.isNotBlank())
        }
    }

    // ---- When a download may start -----------------------------------------

    /**
     * The Wi-Fi-only gate, including the case that is easiest to get backwards:
     * offline. A null [AppSettings.meteredConnection] means there is no active
     * network at all, and refusing there would blame a Wi-Fi setting for an
     * outage — the download is let through to fail on the network and say so.
     */
    @Test
    fun `wifi-only refuses metered connections and nothing else`() {
        val wifiOnly = AppSettings.wifiOnlyDownloads.value
        val metered = AppSettings.meteredConnection.value
        try {
            AppSettings.wifiOnlyDownloads.value = true
            AppSettings.meteredConnection.value = true
            assertFalse(AppSettings.downloadsAllowedNow)

            AppSettings.meteredConnection.value = false
            assertTrue(AppSettings.downloadsAllowedNow)

            // Offline: not this setting's business.
            AppSettings.meteredConnection.value = null
            assertTrue(AppSettings.downloadsAllowedNow)

            // Off, and mobile data is fair game again.
            AppSettings.wifiOnlyDownloads.value = false
            AppSettings.meteredConnection.value = true
            assertTrue(AppSettings.downloadsAllowedNow)
        } finally {
            AppSettings.wifiOnlyDownloads.value = wifiOnly
            AppSettings.meteredConnection.value = metered
        }
    }

    /**
     * The two settings are independent on purpose, and this is the pairing that
     * proves it: a capped mobile-data *stream* must not quietly downgrade a
     * *file*. It used to — the ceiling was what decided whether a download could
     * be lossless, so someone with their own FLAC server streamed the FLAC and
     * downloaded a transcode of the same recording.
     */
    @Test
    fun `a capped streaming ceiling no longer decides what a download keeps`() {
        val cellular = AppSettings.audioQualityCellular.value
        val metered = AppSettings.meteredConnection.value
        try {
            AppSettings.audioQualityCellular.value = AudioQuality.LOW
            AppSettings.meteredConnection.value = true
            assertEquals(AudioQuality.LOW, AppSettings.effectiveAudioQuality)
            assertEquals(
                StreamRequest.Lossless,
                SourceResolver.requestForDownload(DownloadQuality.LOSSLESS),
            )
        } finally {
            AppSettings.audioQualityCellular.value = cellular
            AppSettings.meteredConnection.value = metered
        }
    }
}
