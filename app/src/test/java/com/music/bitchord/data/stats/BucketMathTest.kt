package com.music.bitchord.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic multi-device sync rests on: months add up exactly, and an
 * earlier copy of a month taken away leaves exactly what was played since.
 */
class BucketMathTest {

    private fun track(id: String, ms: Long, plays: Int, day: String = "2026-11-03") = TrackEntry(
        id = id,
        title = "Song $id",
        artist = "Artist",
        ms = ms,
        plays = plays,
        days = mutableMapOf(day to DayStats(ms, plays)),
    )

    private fun artist(name: String, ms: Long, plays: Int) = NameEntry(name = name, ms = ms, plays = plays)

    private fun bucket(vararg tracks: TrackEntry, hour: Int = 20) = StoredBucket(
        month = "2026-11",
        tracks = tracks.toList(),
        artists = listOf(artist("Artist", tracks.sumOf { it.ms }, tracks.sumOf { it.plays })),
        hours = List(24) { if (it == hour) tracks.sumOf { t -> t.ms } else 0L },
        days = mapOf(3 to tracks.sumOf { it.ms }),
        trackDays = mapOf("2026-11-03" to DayStats(tracks.sumOf { it.ms }, tracks.sumOf { it.plays })),
    )

    private fun StoredBucket.trackMs() = tracks.associate { it.id to it.ms }
    private fun StoredBucket.trackPlays() = tracks.associate { it.id to it.plays }

    @Test
    fun plus_addsEveryCounter() {
        val phone = bucket(track("a", 1_000, 1), track("b", 2_000, 2))
        val tablet = bucket(track("a", 500, 1), track("c", 3_000, 3), hour = 8)

        val sum = BucketMath.plus(phone, tablet)

        assertEquals(mapOf("a" to 1_500L, "b" to 2_000L, "c" to 3_000L), sum.trackMs())
        assertEquals(mapOf("a" to 2, "b" to 2, "c" to 3), sum.trackPlays())
        assertEquals(3_000L, sum.hours[20])
        assertEquals(3_500L, sum.hours[8])
        assertEquals(6_500L, sum.days[3])
        assertEquals(6_500L, sum.artists.single().ms)
        assertEquals(DayStats(6_500L, 7), sum.trackDays!!["2026-11-03"])
        assertEquals(DayStats(1_500L, 2), sum.tracks.first { it.id == "a" }.days["2026-11-03"])
    }

    @Test
    fun plus_doesNotTouchItsArguments() {
        val phone = bucket(track("a", 1_000, 1))
        val tablet = bucket(track("a", 500, 1))

        BucketMath.plus(phone, tablet)

        assertEquals(1_000L, phone.tracks.single().ms)
        assertEquals(DayStats(1_000L, 1), phone.tracks.single().days["2026-11-03"])
        assertEquals(500L, tablet.tracks.single().ms)
    }

    @Test
    fun minus_ofAnEarlierCopy_leavesOnlyWhatWasPlayedSince() {
        val before = bucket(track("a", 1_000, 1))
        val since = bucket(track("a", 400, 1), track("b", 900, 1))
        val now = BucketMath.plus(before, since)

        val delta = BucketMath.minus(now, before)

        assertEquals(since.trackMs(), delta.trackMs())
        assertEquals(since.trackPlays(), delta.trackPlays())
        assertEquals(1_300L, delta.hours[20])
        assertEquals(1_300L, delta.days[3])
    }

    @Test
    fun minus_clampsAtZero_andDropsEntriesLeftEmpty() {
        val small = bucket(track("a", 100, 0))
        val big = bucket(track("a", 5_000, 3))

        val delta = BucketMath.minus(small, big)

        assertTrue(delta.tracks.isEmpty())
        assertTrue(delta.hours.all { it == 0L })
        assertTrue(BucketMath.isEmpty(delta))
    }

    /**
     * Restoring as a device onto a phone that already holds an older copy of
     * that same device (Android's own backup) must not double it.
     */
    @Test
    fun foldingAnOlderCopyOfTheSameDevice_doesNotDoubleIt() {
        val older = bucket(track("a", 1_000, 1))
        val current = BucketMath.plus(older, bucket(track("a", 200, 1), track("b", 700, 1)))

        val folded = BucketMath.plus(current, BucketMath.minus(older, current))

        assertEquals(current.trackMs(), folded.trackMs())
        assertEquals(current.trackPlays(), folded.trackPlays())
    }

    @Test
    fun foldingIndependentPlays_keepsThem() {
        val downloaded = bucket(track("a", 1_000, 1))
        val ownOnly = bucket(track("z", 800, 2))

        val folded = BucketMath.plus(downloaded, BucketMath.minus(ownOnly, downloaded))

        assertEquals(mapOf("a" to 1_000L, "z" to 800L), folded.trackMs())
    }

    @Test
    fun canonicalBytes_hashTheSameForTheSameMonth() {
        val a = bucket(track("a", 1_000, 1))
        val b = bucket(track("a", 1_000, 1))
        val c = bucket(track("a", 1_001, 1))

        assertEquals(BucketMath.hash(BucketMath.canonicalBytes(a)), BucketMath.hash(BucketMath.canonicalBytes(b)))
        assertNotEquals(BucketMath.hash(BucketMath.canonicalBytes(a)), BucketMath.hash(BucketMath.canonicalBytes(c)))
        assertEquals(a, BucketMath.decode(BucketMath.canonicalBytes(a)))
    }

    @Test
    fun mergeKey_foldsTheSameLocalFileFromTwoPhones_butNotStreamedTracks() {
        val phoneFile = TrackEntry(id = "content://media/external/audio/media/12", title = "Song", artist = "Band")
        val tabletFile = TrackEntry(id = "content://media/external/audio/media/98", title = "song", artist = "band")
        val streamed = TrackEntry(id = "dQw4w9WgXcQ", title = "Song", artist = "Band")

        assertEquals(ListeningStats.mergeKey(phoneFile), ListeningStats.mergeKey(tabletFile))
        assertEquals("dQw4w9WgXcQ", ListeningStats.mergeKey(streamed))
    }
}
