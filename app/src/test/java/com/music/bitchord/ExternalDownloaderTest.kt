package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.DownloadMode
import com.music.bitchord.data.settings.ExternalDownloaderApp
import com.music.bitchord.download.ExternalDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDownloaderTest {

    private fun sampleSong(id: String) = Song(
        videoId = id,
        title = "Test Song",
        artist = "Test Artist",
        thumbnailUrl = null,
    )

    @Test
    fun `buildMediaUrl constructs watch url for track`() {
        val song = sampleSong("dQw4w9WgXcQ")
        val url = ExternalDownloader.buildMediaUrl(song)
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", url)
    }

    @Test
    fun `buildMediaUrl constructs playlist url without VL prefix`() {
        val url = ExternalDownloader.buildMediaUrl(null, "VLPLabc123xyz")
        assertEquals("https://music.youtube.com/playlist?list=PLabc123xyz", url)
    }

    @Test
    fun `buildMediaUrl prioritizes playlist over song when both present`() {
        val song = sampleSong("dQw4w9WgXcQ")
        val url = ExternalDownloader.buildMediaUrl(song, "PLplaylistId")
        assertEquals("https://music.youtube.com/playlist?list=PLplaylistId", url)
    }

    @Test
    fun `buildMediaUrl returns empty on missing identifiers`() {
        assertEquals("", ExternalDownloader.buildMediaUrl(null, null))
        assertEquals("", ExternalDownloader.buildMediaUrl(sampleSong(""), null))
    }

    @Test
    fun `external downloader enums and packages are configured correctly`() {
        assertEquals("com.deniscerri.ytdl", ExternalDownloaderApp.YTDLNIS.defaultPackage)
        assertEquals("com.junkfood.seal", ExternalDownloaderApp.SEAL.defaultPackage)
        assertEquals("com.junkfood.sealplus", ExternalDownloaderApp.SEAL_PLUS.defaultPackage)
        assertEquals("", ExternalDownloaderApp.OTHER.defaultPackage)

        assertTrue(DownloadMode.entries.contains(DownloadMode.IN_APP))
        assertTrue(DownloadMode.entries.contains(DownloadMode.EXTERNAL))
    }
}
