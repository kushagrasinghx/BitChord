package com.music.bitchord.data.stats

import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pins the listening rules the phone and the web player share. */
class ListeningSharedTest {

    // ---- The tally (ListeningRecorder's rule) -------------------------------

    @Test
    fun aTracksFirstTickOnlyAnchorsTheClock() {
        val tally = ListeningTally()
        assertNull(tally.sample("a", 1_000, 200_000))
        assertEquals(5_000, tally.sample("a", 6_000, 200_000)?.playedMs)
    }

    @Test
    fun aGapAcrossAPauseCountsNoMoreThanOneStep() {
        val tally = ListeningTally()
        tally.sample("a", 0, 200_000)
        assertEquals(ListeningTally.MAX_STEP_MS, tally.sample("a", 3_600_000, 200_000)?.playedMs)
        // A clock that went backwards adds nothing.
        assertNull(tally.sample("a", 3_500_000, 200_000))
    }

    @Test
    fun aPlayIsHalfTheTrackButNeverUnderThirtySecondsOrOverFourMinutes() {
        fun ticksToAPlay(lengthMs: Long): Int {
            val tally = ListeningTally()
            tally.sample("a", 0, lengthMs)
            var t = 0L
            repeat(100) { i ->
                t += 5_000
                if (tally.sample("a", t, lengthMs)?.countsAsPlay == true) return i + 1
            }
            return -1
        }
        assertEquals(6, ticksToAPlay(40_000)) // half is 20 s: the 30 s floor
        assertEquals(20, ticksToAPlay(200_000)) // half: 100 s
        assertEquals(48, ticksToAPlay(600_000)) // half is 5 min: the 4 min ceiling
        assertEquals(6, ticksToAPlay(0)) // no length: the floor
    }

    @Test
    fun aPlayCountsOnceAndAFlushComesEverySixTicks() {
        val tally = ListeningTally()
        tally.sample("a", 0, 40_000)
        val steps = (1..12).map { tally.sample("a", it * 5_000L, 40_000)!! }
        assertEquals(1, steps.count { it.countsAsPlay })
        assertEquals(listOf(6, 12), steps.withIndex().filter { it.value.flush }.map { it.index + 1 })
        tally.stop()
        assertNull(tally.sample("a", 70_000, 40_000))
    }

    // ---- The month on disk ---------------------------------------------------

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** A month as ListeningStats writes it: this exact text is what moves between devices. */
    private val written =
        """{"version":1,"month":"2026-11","tracks":[{"id":"v","title":"T","artist":"A","album":null,""" +
            """"albumId":null,"artistId":null,"art":null,"ms":5000,"plays":1,"last":9,""" +
            """"days":{"2026-11-03":{"ms":5000,"plays":1}}}],"artists":[],"albums":[],""" +
            """"hours":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,5000,0,0,0],"days":{"3":5000},""" +
            """"trackDays":null,"artistDays":null,"albumDays":null}"""

    @Test
    fun aMonthIsWrittenInTheSameShape() {
        val bucket = StoredBucket(
            month = "2026-11",
            tracks = listOf(
                TrackEntry(id = "v", title = "T", artist = "A", ms = 5_000, plays = 1, last = 9, days = mutableMapOf("2026-11-03" to DayStats(5_000, 1))),
            ),
            hours = List(24) { if (it == 20) 5_000L else 0L },
            days = mapOf(3 to 5_000L),
        )
        assertEquals(written, json.encodeToString(StoredBucket.serializer(), bucket))
        assertEquals(bucket, json.decodeFromString(StoredBucket.serializer(), written))
    }

    // ---- Recording and adding up --------------------------------------------

    private fun song(id: String, artist: String, album: String? = null, albumId: String? = null) =
        Song(videoId = id, title = "Song $id", artist = artist, thumbnailUrl = null, albumName = album, albumId = albumId)

    @Test
    fun aSampleIsFiledUnderItsLeadArtistItsAlbumItsHourAndItsDay() {
        val open = OpenBucket.of("2026-11", StoredBucket(month = "2026-11"))
        val noticed = mutableListOf<String>()
        open.record(song("a", "Cheema Y & Gur Sidhu", "Album"), 5_000, true, 9, "2026-11-03", 20, 3, noticed::add)
        val stored = open.snapshot()
        assertEquals(listOf("Cheema Y"), noticed)
        assertEquals("Cheema Y", stored.artists.single().name)
        assertEquals("cheema y", stored.artists.single().key)
        assertEquals("Album", stored.albums.single().name)
        assertEquals(5_000L, stored.hours[20])
        assertEquals(mapOf(3 to 5_000L), stored.days)
        assertEquals(DayStats(5_000, 1), stored.tracks.single().days["2026-11-03"])
    }

    @Test
    fun monthsAddUpIntoChartsByTimeAndFoldOneAlbumIntoOneRow() {
        fun month(key: String, vararg plays: Pair<Song, Long>) = OpenBucket.of(key, StoredBucket(month = key)).apply {
            plays.forEach { (s, ms) -> record(s, ms, true, 0, "$key-01", 12, 1) }
        }.snapshot()
        val october = month("2026-10", song("a", "Band", "Record") to 1_000L, song("b", "Band feat. Guest") to 9_000L)
        val november = month("2026-11", song("a", "Band", "Record", albumId = "MPRE1") to 5_000L)
        val merged = MergedListening().apply {
            add(october)
            add(november)
        }
        assertEquals(listOf("b", "a"), merged.songs().map { it.song.videoId })
        assertEquals(6_000L, merged.songs()[1].ms)
        assertEquals(listOf("Band"), merged.artists().map { it.title })
        // Filed by name in October and by id in November, drawn as one row with the id.
        val album = merged.albums().single()
        assertEquals(6_000L, album.ms)
        assertEquals("MPRE1", album.browseId)
        assertEquals("2026-10", merged.earliest)
        assertEquals("2026-10-01" to 10_000L, merged.busiestDay())
    }

    @Test
    fun aWeekCountsOnlyItsOwnDays() {
        val open = OpenBucket.of("2026-11", StoredBucket(month = "2026-11"))
        open.record(song("a", "Band"), 5_000, true, 0, "2026-11-02", 12, 2)
        open.record(song("b", "Band"), 7_000, true, 0, "2026-11-10", 12, 10)
        val merged = MergedListening()
        val week: (String) -> Boolean = { it >= "2026-11-09" && it <= "2026-11-15" }
        merged.add(open.snapshot(), week)
        assertEquals(listOf("b"), merged.songs(week).map { it.song.videoId })
        assertEquals(mapOf("2026-11-10" to 7_000L), merged.days.toMap())
        assertTrue(merged.songs().size == 2)
        assertFalse(merged.artists(week).isEmpty())
        assertNotNull(YearListening.busiestDaySongId(listOf(open.snapshot()), "2026-11-10"))
        assertEquals(mapOf("2026-11-02" to 5_000L, "2026-11-10" to 7_000L), YearListening.dayMs(listOf(open.snapshot())))
    }
}
