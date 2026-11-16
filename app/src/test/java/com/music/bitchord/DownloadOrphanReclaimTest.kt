package com.music.bitchord

import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Offline packages with nothing pointing at them are reclaimed — and a package
 * a download is still filling is not.
 *
 * A package directory holds the playlist, every segment, the lyrics and the
 * cover, so a package left behind by a process death is the largest garbage this
 * app creates; but the record that would make it "referenced" is written only
 * after the bytes, so a sweep that trusted references alone would delete a
 * download the moment it started.
 */
class DownloadOrphanReclaimTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private val day = DownloadStore.ORPHAN_MIN_AGE_MS

    private fun packageDir(name: String, ageMs: Long): File {
        val dir = File(temp.root, name)
        assertTrue(dir.mkdirs())
        File(dir, "playlist.m3u8").writeText("#EXTM3U\n#EXTINF:1,\nsegment-00000.m4s\n")
        File(dir, "segment-00000.m4s").writeBytes(ByteArray(1024))
        File(dir, "cover.jpg").writeBytes(ByteArray(2048))
        File(dir, "lyrics.lrc").writeText("[00:01.00]line")
        assertTrue(dir.setLastModified(now - ageMs))
        return dir
    }

    @Test
    fun `an old package nothing points at is reclaimed whole`() {
        val orphan = packageDir("a69b0f1c2d3e4f50", ageMs = 3 * day)

        val removed = DownloadStore.removePackages(DownloadStore.orphanedPackages(temp.root, emptySet(), now))

        assertEquals(1, removed)
        assertFalse("the directory, its segments and its sidecars all go", orphan.exists())
    }

    @Test
    fun `a referenced package is kept however old it is`() {
        val referenced = packageDir("1111111111111111", ageMs = 400 * day)

        val removed = DownloadStore.removePackages(DownloadStore.orphanedPackages(temp.root, setOf("1111111111111111"), now))

        assertEquals(0, removed)
        assertTrue(File(referenced, "cover.jpg").exists())
    }

    @Test
    fun `a fresh unreferenced package is treated as a download in progress`() {
        val inFlight = packageDir("2222222222222222", ageMs = 5 * 60 * 1000L)

        val removed = DownloadStore.removePackages(DownloadStore.orphanedPackages(temp.root, emptySet(), now))

        assertEquals("a package being written has no record yet", 0, removed)
        assertTrue(File(inFlight, "playlist.m3u8").exists())
    }

    @Test
    fun `the age boundary itself is reclaimable`() {
        packageDir("3333333333333333", ageMs = day)
        assertEquals(1, DownloadStore.removePackages(DownloadStore.orphanedPackages(temp.root, emptySet(), now)))
    }

    @Test
    fun `only packages are considered, not loose files`() {
        val root = temp.root
        assertTrue(File(root, "stray.txt").createNewFile())
        assertTrue(File(root, "stray.txt").setLastModified(now - 10 * day))

        assertEquals(0, DownloadStore.removePackages(DownloadStore.orphanedPackages(root, emptySet(), now)))
        assertTrue(File(root, "stray.txt").exists())
    }

    @Test
    fun `package names come from the saved records`() {
        val saved = mapOf(
            "video-a" to "file:///data/user/0/app/files/offline-hls/aaaa1111/playlist.m3u8",
            // One file answers to two ids when a music video is swapped for its
            // catalogue track; both name the same package.
            "video-b" to "file:///data/user/0/app/files/offline-hls/aaaa1111/playlist.m3u8",
            "video-c" to "file:///data/user/0/app/files/offline-hls/bbbb2222/playlist.m3u8",
            "plain" to "file:///storage/emulated/0/Music/BitChord/Artist - Title.flac",
        )

        assertEquals(setOf("aaaa1111", "bbbb2222"), Downloads.referencedPackageNames(saved))
        val empty = emptyMap<String, String>()
        assertTrue(Downloads.referencedPackageNames(empty).isEmpty())
    }

    @Test
    fun `a package named by the legacy hashCode scheme is still preserved`() {
        // Devices upgrading from the old naming have records pointing at this
        // directory. Reclamation must read the record, not recompute a name.
        val legacyName = "legacy-video".hashCode().toUInt().toString(16)
        val legacy = packageDir(legacyName, ageMs = 400 * day)

        val removed = DownloadStore.removePackages(
            DownloadStore.orphanedPackages(temp.root, setOf(legacyName), now),
        )

        assertEquals(0, removed)
        assertTrue(File(legacy, "playlist.m3u8").exists())
    }

    @Test
    fun `a reclaimed orphan frees its bytes`() {
        packageDir("4444444444444444", ageMs = 2 * day)
        val before = temp.root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertTrue(before >= 1024 + 2048)

        DownloadStore.removePackages(DownloadStore.orphanedPackages(temp.root, emptySet(), now))

        assertEquals(0L, temp.root.walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }
}
