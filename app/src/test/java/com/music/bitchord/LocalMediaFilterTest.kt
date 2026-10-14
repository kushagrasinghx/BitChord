package com.music.bitchord

import com.music.bitchord.data.LocalMediaRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaFilterTest {

    @Test
    fun rejectsShortAudio() {
        assertFalse(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 29_999,
                displayName = "effect.mp3",
                path = "/storage/emulated/0/Music/effect.mp3",
            ),
        )
    }

    @Test
    fun rejectsWaveAndRecorderFiles() {
        assertFalse(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 180_000,
                displayName = "recording.wav",
                path = "/storage/emulated/0/Recordings/recording.wav",
            ),
        )
        assertFalse(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 180_000,
                displayName = "voice-note.mp3",
                path = "/storage/emulated/0/Voice Recorder/voice-note.mp3",
            ),
        )
    }

    @Test
    fun keepsSupportedMusicFiles() {
        assertTrue(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 50_000,
                displayName = "Song.flac",
                path = "/storage/emulated/0/Music/Artist/Song.flac",
            ),
        )
    }

    @Test
    fun keepsAudioOnlyMp4AndDsd() {
        for (name in listOf("Song.mp4", "Song.dsf", "Song.dff")) {
            assertTrue(
                name,
                LocalMediaRepository.isEligibleLocalMusic(
                    durationMs = 180_000,
                    displayName = name,
                    path = "/storage/emulated/0/Music/$name",
                ),
            )
        }
    }

    @Test
    fun keepsTracksTheScannerCouldNotTime() {
        // Fragmented-MP4 ALAC scans with duration 0, high-rate DSD with NULL
        // (read back as 0): an unknown length, not a short clip.
        assertTrue(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 0,
                displayName = "Starboy.m4a",
                path = "/storage/emulated/0/Music/Starboy.m4a",
            ),
        )
    }

    @Test
    fun isInSelectedFolderNormalizesSdcardAndSelfPrimary() {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        assertTrue(LocalMediaRepository.isInSelectedFolder("/storage/emulated/0/Music/Song.mp3", treeUri))
        assertTrue(LocalMediaRepository.isInSelectedFolder("/sdcard/Music/Song.mp3", treeUri))
        assertTrue(LocalMediaRepository.isInSelectedFolder("/storage/self/primary/Music/Song.mp3", treeUri))
        assertFalse(LocalMediaRepository.isInSelectedFolder("/storage/emulated/0/Download/Song.mp3", treeUri))
        assertFalse(LocalMediaRepository.isInSelectedFolder("/sdcard/Download/Song.mp3", treeUri))
    }

    @Test
    fun isInSelectedFolderHandlesUrlEncodedTreeIds() {
        val encodedUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FSubfolder"
        assertTrue(LocalMediaRepository.isInSelectedFolder("/storage/emulated/0/Music/Subfolder/Song.mp3", encodedUri))
        assertTrue(LocalMediaRepository.isInSelectedFolder("/sdcard/Music/Subfolder/Song.mp3", encodedUri))
        assertFalse(LocalMediaRepository.isInSelectedFolder("/storage/emulated/0/Music/Other/Song.mp3", encodedUri))
    }

    @Test
    fun isInSelectedFolderRejectsNullOrBlankPathsWhenFolderSet() {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        assertFalse(LocalMediaRepository.isInSelectedFolder(null, treeUri))
        assertFalse(LocalMediaRepository.isInSelectedFolder("", treeUri))
        assertTrue(LocalMediaRepository.isInSelectedFolder("/storage/emulated/0/Music/Song.mp3", null))
        assertTrue(LocalMediaRepository.isInSelectedFolder("/storage/emulated/0/Music/Song.mp3", ""))
    }

    @Test
    fun isInSelectedFolderHandlesSecondaryVolumeSdCard() {
        val sdTreeUri = "content://com.android.externalstorage.documents/tree/1234-5678%3AMusic"
        assertTrue(LocalMediaRepository.isInSelectedFolder("/storage/1234-5678/Music/Song.mp3", sdTreeUri))
        assertFalse(LocalMediaRepository.isInSelectedFolder("/storage/1234-5678/Podcasts/Song.mp3", sdTreeUri))
    }

    @Test
    fun resolvePhysicalPathDecodesPrimarySdCardAndRawDocIds() {
        org.junit.Assert.assertEquals(
            "/storage/emulated/0/Music/Album/Song.mp3",
            LocalMediaRepository.resolvePhysicalPath("primary:Music/Album/Song.mp3"),
        )
        org.junit.Assert.assertEquals(
            "/storage/1234-5678/Music/Song.flac",
            LocalMediaRepository.resolvePhysicalPath("1234-5678:Music/Song.flac"),
        )
        org.junit.Assert.assertEquals(
            "/storage/emulated/0/Music/Song.m4a",
            LocalMediaRepository.resolvePhysicalPath("raw:/storage/emulated/0/Music/Song.m4a"),
        )
    }

    @Test
    fun isInSelectedFolderMatchesRelativePathWhenPhysicalPathNull() {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        assertTrue(LocalMediaRepository.isInSelectedFolder(null, "Music/Album/", treeUri))
        assertTrue(LocalMediaRepository.isInSelectedFolder(null, "Music", treeUri))
        assertFalse(LocalMediaRepository.isInSelectedFolder(null, "Download/Album/", treeUri))
    }

    @Test
    fun parseMetadataFromPathExtractsArtistAndTitle() {
        with(LocalMediaRepository) {
            val res1 = parseMetadataFromPath("Queen - Bohemian Rhapsody.mp3", null)
            org.junit.Assert.assertEquals("Bohemian Rhapsody", res1.title)
            org.junit.Assert.assertEquals("Queen", res1.artist)

            val res2 = parseMetadataFromPath("01 - Bohemian Rhapsody.flac", null)
            org.junit.Assert.assertEquals("Bohemian Rhapsody", res2.title)

            val res3 = parseMetadataFromPath("01. Song.mp3", null)
            org.junit.Assert.assertEquals("Song", res3.title)

            val res4 = parseMetadataFromPath("1984.mp3", null)
            org.junit.Assert.assertEquals("1984", res4.title)
        }
    }
}

