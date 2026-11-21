package com.music.bitchord.data.stats

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.YearMonth

class ListeningStatsScopeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun song(id: String, title: String, artist: String) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        fromAutoplay = false,
    )

    @Test
    fun `accounts and brand profiles keep isolated stats`() = runBlocking {
        val baseDir = tempFolder.newFolder("listening")
        ListeningStats.initForTest(baseDir, "acctA:personal")

        ListeningStats.record(song("aaaaaaaaaaa", "Track A", "Artist A"), playedMs = 60_000L, countsAsPlay = true)
        ListeningStats.setScope("acctA:brand")
        ListeningStats.awaitWritesForTest()

        val brandSummary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertTrue(brandSummary.isEmpty)

        ListeningStats.record(song("bbbbbbbbbbb", "Track B", "Artist B"), playedMs = 90_000L, countsAsPlay = true)
        ListeningStats.setScope("acctB:personal")
        ListeningStats.awaitWritesForTest()

        assertTrue(ListeningStats.summary(ReplayPeriod.ALL_TIME).isEmpty)

        ListeningStats.setScope("acctA:personal")
        val personalSummary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(listOf("aaaaaaaaaaa"), personalSummary.songs.map { it.song.videoId })
        assertEquals(60_000L, personalSummary.totalMs)

        ListeningStats.setScope("acctA:brand")
        val brandReloaded = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(listOf("bbbbbbbbbbb"), brandReloaded.songs.map { it.song.videoId })
        assertEquals(90_000L, brandReloaded.totalMs)
    }

    @Test
    fun `guest scope is isolated from signed in accounts`() = runBlocking {
        val baseDir = tempFolder.newFolder("listening-guest")
        ListeningStats.initForTest(baseDir, null)

        ListeningStats.record(song("ggggggggggg", "Guest Track", "Guest Artist"), playedMs = 45_000L, countsAsPlay = true)
        ListeningStats.setScope("acctA:personal")
        ListeningStats.awaitWritesForTest()

        assertTrue(ListeningStats.summary(ReplayPeriod.ALL_TIME).isEmpty)

        ListeningStats.setScope(null)
        val guestSummary = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(listOf("ggggggggggg"), guestSummary.songs.map { it.song.videoId })
    }

    @Test
    fun `legacy single-folder buckets migrate once into initial scope without leaking to other accounts`() = runBlocking {
        val baseDir = tempFolder.newFolder("listening-legacy")
        val month = YearMonth.now().toString()
        val legacyBucket = StoredBucket(
            month = month,
            tracks = listOf(
                TrackEntry(
                    id = "legacy00001",
                    title = "Legacy Song",
                    artist = "Legacy Artist",
                    ms = 120_000L,
                    plays = 2,
                ),
            ),
            artists = listOf(
                NameEntry(name = "Legacy Artist", ms = 120_000L, plays = 2),
            ),
        )
        val legacyFile = File(baseDir, "$month.json")
        legacyFile.writeText(Json.encodeToString(StoredBucket.serializer(), legacyBucket))

        ListeningStats.initForTest(baseDir, "acctA:personal")
        assertFalse(legacyFile.exists())

        val migrated = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertEquals(listOf("legacy00001"), migrated.songs.map { it.song.videoId })
        assertEquals(120_000L, migrated.totalMs)

        ListeningStats.setScope("acctB:personal")
        val otherAccount = ListeningStats.summary(ReplayPeriod.ALL_TIME)
        assertTrue(otherAccount.isEmpty)
    }

    @Test
    fun `scopeRevision increments on scope change and ignores redundant switches`() = runBlocking {
        val baseDir = tempFolder.newFolder("listening-rev")
        ListeningStats.initForTest(baseDir, "acctA:personal")
        val before = ListeningStats.scopeRevision.value

        ListeningStats.setScope("acctA:personal")
        assertEquals(before, ListeningStats.scopeRevision.value)

        ListeningStats.setScope("acctB:personal")
        assertEquals(before + 1, ListeningStats.scopeRevision.value)
    }
}
