package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.offlinebackup.OfflineBackupDao
import com.music.bitchord.data.offlinebackup.OfflineBackupRepository
import com.music.bitchord.data.offlinebackup.OfflineBackupTrack
import com.music.bitchord.playback.offlinebackup.OfflineBackupCacheManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class OfflineBackupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * In-memory [OfflineBackupDao] mirroring the SQLite queries and ordering
     * in [com.music.bitchord.data.offlinebackup.OfflineBackupDatabase].
     */
    private class FakeOfflineBackupDao : OfflineBackupDao {
        private val rows = linkedMapOf<String, OfflineBackupTrack>()

        override fun recordPlay(
            songId: String,
            title: String,
            artist: String,
            remoteUrl: String?,
            timestampMs: Long,
            thumbnailUrl: String?,
            durationText: String?,
            albumName: String?,
        ) {
            val existing = rows[songId]
            rows[songId] = if (existing == null) {
                OfflineBackupTrack(
                    songId = songId,
                    title = title,
                    artist = artist,
                    remoteUrl = remoteUrl,
                    localCachePath = null,
                    lastPlayedTimestamp = timestampMs,
                    playCount = 1,
                    fileSizeBytes = 0L,
                    thumbnailUrl = thumbnailUrl,
                    durationText = durationText,
                    albumName = albumName,
                )
            } else {
                existing.copy(
                    title = title.ifBlank { existing.title },
                    artist = artist.ifBlank { existing.artist },
                    remoteUrl = remoteUrl?.takeIf { it.isNotBlank() } ?: existing.remoteUrl,
                    lastPlayedTimestamp = maxOf(existing.lastPlayedTimestamp, timestampMs),
                    playCount = existing.playCount + 1,
                )
            }
        }

        override fun markCached(songId: String, localCachePath: String, fileSizeBytes: Long) {
            val existing = rows[songId] ?: return
            rows[songId] = existing.copy(
                localCachePath = localCachePath,
                fileSizeBytes = fileSizeBytes,
            )
        }

        override fun clearCachePaths(songIds: Collection<String>) {
            for (id in songIds) {
                val existing = rows[id] ?: continue
                rows[id] = existing.copy(localCachePath = null, fileSizeBytes = 0L)
            }
        }

        override fun getOfflineBackupTracks(): List<OfflineBackupTrack> =
            rows.values
                .filter { !it.localCachePath.isNullOrBlank() }
                .sortedWith(
                    compareByDescending<OfflineBackupTrack> { it.lastPlayedTimestamp }
                        .thenByDescending { it.playCount },
                )

        override fun getCachedTracksLeastRecentlyUsed(): List<OfflineBackupTrack> =
            rows.values
                .filter { !it.localCachePath.isNullOrBlank() }
                .sortedWith(
                    compareBy<OfflineBackupTrack> { it.lastPlayedTimestamp }
                        .thenBy { it.playCount }
                        .thenBy { it.songId },
                )

        override fun getTrack(songId: String): OfflineBackupTrack? = rows[songId]
    }

    @Test
    fun `recordPlay increments play_count and updates timestamp`() {
        var now = 1_000L
        val dao = FakeOfflineBackupDao()
        val repo = OfflineBackupRepository(dao, cacheManager = null, clock = { now })
        val song = Song(
            videoId = "v1",
            title = "Song One",
            artist = "Artist A",
            thumbnailUrl = null,
            fromAutoplay = false,
        )

        repo.onTrackStreamedOnline(song, "https://example.com/v1.m4a")
        val first = dao.getTrack("v1")
        assertNotNull(first)
        assertEquals(1, first!!.playCount)
        assertEquals(1_000L, first.lastPlayedTimestamp)
        assertEquals("https://example.com/v1.m4a", first.remoteUrl)

        now = 5_000L
        repo.onTrackStreamedOnline(song, null)
        val second = dao.getTrack("v1")
        assertNotNull(second)
        assertEquals(2, second!!.playCount)
        assertEquals(5_000L, second.lastPlayedTimestamp)
        assertEquals("https://example.com/v1.m4a", second.remoteUrl)
    }

    @Test
    fun `getOfflineBackupPlaylist orders by timestamp and play_count and prunes missing files`() {
        val cacheDir = tempFolder.newFolder("playlist_cache")
        val dao = FakeOfflineBackupDao()
        val repo = OfflineBackupRepository(dao, cacheManager = null)

        val fileA = File(cacheDir, "a.audio").apply { writeBytes(ByteArray(32)) }
        val fileB = File(cacheDir, "b.audio").apply { writeBytes(ByteArray(32)) }
        val fileC = File(cacheDir, "c.audio").apply { writeBytes(ByteArray(32)) }
        val missingFile = File(cacheDir, "missing.audio") // Never created on disk

        dao.recordPlay("a", "A", "Artist", null, timestampMs = 100L)
        dao.markCached("a", fileA.absolutePath, 32L)

        // Same timestamp as C, but fewer plays.
        dao.recordPlay("b", "B", "Artist", null, timestampMs = 200L)
        dao.markCached("b", fileB.absolutePath, 32L)

        dao.recordPlay("c", "C", "Artist", null, timestampMs = 200L)
        dao.recordPlay("c", "C", "Artist", null, timestampMs = 200L)
        dao.markCached("c", fileC.absolutePath, 32L)

        dao.recordPlay("missing", "Missing", "Artist", null, timestampMs = 300L)
        dao.markCached("missing", missingFile.absolutePath, 32L)

        val playlist = repo.getOfflineBackupPlaylist()
        assertEquals(listOf("c", "b", "a"), playlist.map { it.videoId })
        assertNull(dao.getTrack("missing")?.localCachePath)
        assertEquals(fileC.absolutePath, repo.getValidLocalCachePath("c"))
        assertNull(repo.getValidLocalCachePath("missing"))
    }

    @Test
    fun `lru cache enforces maxTracks cap and clears evicted db records`() {
        val cacheDir = tempFolder.newFolder("lru_count_cache")
        val dao = FakeOfflineBackupDao()
        val manager = OfflineBackupCacheManager(
            cacheDir = cacheDir,
            dao = dao,
            maxBytes = 10L * 1024L * 1024L,
            maxTracks = 2,
        )

        dao.recordPlay("t1", "Track 1", "Artist", "https://example.com/1", timestampMs = 100L)
        val f1 = manager.cacheNow("t1", "https://example.com/1") { _, _, sink ->
            sink.write(ByteArray(64) { 1 })
        }
        assertNotNull(f1)

        dao.recordPlay("t2", "Track 2", "Artist", "https://example.com/2", timestampMs = 200L)
        val f2 = manager.cacheNow("t2", "https://example.com/2") { _, _, sink ->
            sink.write(ByteArray(64) { 2 })
        }
        assertNotNull(f2)

        dao.recordPlay("t3", "Track 3", "Artist", "https://example.com/3", timestampMs = 300L)
        val f3 = manager.cacheNow("t3", "https://example.com/3") { _, _, sink ->
            sink.write(ByteArray(64) { 3 })
        }
        assertNotNull(f3)

        // Oldest track t1 must be evicted on disk and cleared in DB.
        assertFalse(f1!!.exists())
        assertTrue(f2!!.exists())
        assertTrue(f3!!.exists())
        assertNull(dao.getTrack("t1")?.localCachePath)
        assertEquals(0L, dao.getTrack("t1")?.fileSizeBytes)
        assertNotNull(dao.getTrack("t2")?.localCachePath)
        assertNotNull(dao.getTrack("t3")?.localCachePath)
    }

    @Test
    fun `lru cache enforces maxBytes cap`() {
        val cacheDir = tempFolder.newFolder("lru_bytes_cache")
        val dao = FakeOfflineBackupDao()
        val manager = OfflineBackupCacheManager(
            cacheDir = cacheDir,
            dao = dao,
            maxBytes = 250L,
            maxTracks = 50,
        )

        dao.recordPlay("s1", "S1", "Artist", "https://example.com/1", timestampMs = 10L)
        val f1 = manager.cacheNow("s1", "https://example.com/1") { _, _, sink ->
            sink.write(ByteArray(100))
        }!!

        dao.recordPlay("s2", "S2", "Artist", "https://example.com/2", timestampMs = 20L)
        val f2 = manager.cacheNow("s2", "https://example.com/2") { _, _, sink ->
            sink.write(ByteArray(100))
        }!!

        // Adding 120 bytes pushes total to 320 > 250 -> s1 (oldest) evicted -> 220 <= 250.
        dao.recordPlay("s3", "S3", "Artist", "https://example.com/3", timestampMs = 30L)
        val f3 = manager.cacheNow("s3", "https://example.com/3") { _, _, sink ->
            sink.write(ByteArray(120))
        }!!

        assertFalse(f1.exists())
        assertTrue(f2.exists())
        assertTrue(f3.exists())
        assertNull(dao.getTrack("s1")?.localCachePath)
        assertNotNull(dao.getTrack("s2")?.localCachePath)
        assertNotNull(dao.getTrack("s3")?.localCachePath)
    }

    @Test
    fun `interrupted or failed download cleans up partial temp file`() {
        val cacheDir = tempFolder.newFolder("partial_cleanup_cache")
        val staleTmp = File(cacheDir, "stale.123.tmp").apply { writeBytes(ByteArray(16)) }
        val dao = FakeOfflineBackupDao()
        val manager = OfflineBackupCacheManager(
            cacheDir = cacheDir,
            dao = dao,
            maxBytes = 1024L * 1024L,
            maxTracks = 10,
        )

        // Init sweeps stale .tmp files.
        assertFalse(staleTmp.exists())

        dao.recordPlay("bad", "Bad", "Artist", "https://example.com/bad", timestampMs = 500L)
        val result = manager.cacheNow("bad", "https://example.com/bad") { _, _, sink ->
            sink.write(ByteArray(40))
            throw IOException("Network dropped mid-stream")
        }

        assertNull(result)
        assertNull(dao.getTrack("bad")?.localCachePath)
        assertEquals(0, cacheDir.listFiles()?.size ?: 0)
    }
}
