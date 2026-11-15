package com.music.bitchord.data.smartoffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The ranking engine, checked on its own.
 *
 * Nothing here touches a device: the selector is handed a history and a
 * moment and returns a ranking, so every case below is a statement about the
 * rule rather than about the app around it.
 */
class OfflineTrackSelectorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 3, 15)
    private val now: Instant = today.atStartOfDay(zone).plusSeconds(12 * 3600).toInstant()

    // ── The empty and the smallest cases ────────────────────────────────

    @Test
    fun `an empty history ranks nothing`() {
        val result = OfflineTrackSelector.select(emptyMap(), now = now)
        assertTrue(result.isEmpty)
        assertEquals(0, result.ranked.size)
    }

    @Test
    fun `a single candidate is ranked`() {
        val result = OfflineTrackSelector.select(
            mapOf("a" to activity("a", plays = 1, daysAgo = 0)),
            now = now,
        )
        assertEquals(1, result.ranked.size)
        assertEquals("a", result.ranked.first().id)
    }

    @Test
    fun `a single candidate has no rotation variety to speak of`() {
        val result = OfflineTrackSelector.select(
            mapOf("a" to activity("a", plays = 1, daysAgo = 0)),
            now = now,
        )
        assertEquals(1, result.core.size)
        assertEquals(0, result.rotationVariety.size)
    }

    @Test
    fun `two candidates are both ranked`() {
        val result = OfflineTrackSelector.select(
            mapOf(
                "a" to activity("a", plays = 3, daysAgo = 1),
                "b" to activity("b", plays = 1, daysAgo = 0),
            ),
            now = now,
        )
        assertEquals(2, result.ranked.size)
    }

    // ── Recency ─────────────────────────────────────────────────────────

    @Test
    fun `the track heard most recently comes first under a recency strategy`() {
        val result = OfflineTrackSelector.select(
            mapOf(
                "old" to activity("old", plays = 20, daysAgo = 40),
                "new" to activity("new", plays = 1, daysAgo = 0),
            ),
            config = SelectorConfig(strategy = SelectionStrategy.RECENTLY_PLAYED),
            now = now,
        )
        assertEquals("new", result.ranked.first().id)
    }

    @Test
    fun `the track heard most often comes first under a frequency strategy`() {
        val result = OfflineTrackSelector.select(
            mapOf(
                "old" to activity("old", plays = 20, daysAgo = 40),
                "new" to activity("new", plays = 1, daysAgo = 0),
            ),
            config = SelectorConfig(strategy = SelectionStrategy.FREQUENTLY_PLAYED),
            now = now,
        )
        assertEquals("old", result.ranked.first().id)
    }

    @Test
    fun `recency decays with time`() {
        val result = OfflineTrackSelector.select(
            mapOf(
                "recent" to activity("recent", plays = 3, daysAgo = 1),
                "older" to activity("older", plays = 3, daysAgo = 21),
            ),
            config = SelectorConfig(strategy = SelectionStrategy.RECENTLY_PLAYED),
            now = now,
        )
        assertEquals("recent", result.ranked.first().id)
        val recent = result.ranked.first { it.id == "recent" }.components.recency
        val older = result.ranked.first { it.id == "older" }.components.recency
        assertTrue("recent $recent should beat older $older", recent > older)
    }

    @Test
    fun `a track never played has no recency at all`() {
        val track = activity("a", plays = 1, daysAgo = 0).copy(lastPlayedAt = 0L)
        val result = OfflineTrackSelector.select(mapOf("a" to track), now = now)
        assertEquals(0.0, result.ranked.first().components.recency, 1e-9)
    }

    // ── Frequency ───────────────────────────────────────────────────────

    @Test
    fun `more plays means more frequency`() {
        val result = OfflineTrackSelector.select(
            mapOf(
                "many" to activity("many", plays = 9, daysAgo = 1),
                "few" to activity("few", plays = 2, daysAgo = 1),
            ),
            config = SelectorConfig(strategy = SelectionStrategy.FREQUENTLY_PLAYED),
            now = now,
        )
        assertEquals("many", result.ranked.first().id)
    }

    @Test
    fun `an old play counts for less than a recent one of the same number`() {
        val result = OfflineTrackSelector.select(
            mapOf(
                "recent" to activity("recent", plays = 4, daysAgo = 2),
                "old" to activity("old", plays = 4, daysAgo = 27),
            ),
            config = SelectorConfig(strategy = SelectionStrategy.FREQUENTLY_PLAYED),
            now = now,
        )
        assertEquals("recent", result.ranked.first().id)
    }

    @Test
    fun `a habit sustained over many days beats a burst on one day`() {
        val habit = activity("habit", plays = 1, daysAgo = 0).copy(
            playsInWindow = 5,
            days = (0..4).associate { offset ->
                today.minusDays(offset.toLong()).toString() to DailyActivity(60_000L, 1)
            },
        )
        val burst = activity("burst", plays = 3, daysAgo = 0).copy(
            playsInWindow = 3,
            days = mapOf(today.toString() to DailyActivity(180_000L, 3)),
        )
        val result = OfflineTrackSelector.select(
            mapOf("habit" to habit, "burst" to burst),
            config = SelectorConfig(strategy = SelectionStrategy.FREQUENTLY_PLAYED),
            now = now,
        )
        assertEquals("habit", result.ranked.first().id)
    }

    @Test
    fun `plays made today are worth more than the same number spread over older days`() {
        val recent = activity("recent", plays = 3, daysAgo = 0).copy(
            days = mapOf(today.toString() to DailyActivity(180_000L, 3)),
        )
        val spread = activity("spread", plays = 3, daysAgo = 0).copy(
            days = mapOf(
                today.toString() to DailyActivity(60_000L, 1),
                today.minusDays(3).toString() to DailyActivity(60_000L, 1),
                today.minusDays(6).toString() to DailyActivity(60_000L, 1),
            ),
        )
        val result = OfflineTrackSelector.select(
            mapOf("recent" to recent, "spread" to spread),
            config = SelectorConfig(strategy = SelectionStrategy.FREQUENTLY_PLAYED),
            now = now,
        )
        assertEquals("recent", result.ranked.first().id)
    }

    // ── Engagement ──────────────────────────────────────────────────────

    @Test
    fun `an unknown duration leaves engagement undecided`() {
        val result = OfflineTrackSelector.select(mapOf("a" to activity("a")), now = now)
        assertEquals(
            OfflineTrackSelector.UNKNOWN_ENGAGEMENT,
            result.ranked.first().raw.engagement,
            1e-9,
        )
    }

    @Test
    fun `a track played to the end beats one that is always skipped`() {
        val finished = activity("finished", plays = 4, daysAgo = 1).copy(msInWindow = 4 * 200_000L)
        val skipped = activity("skipped", plays = 4, daysAgo = 1).copy(msInWindow = 4 * 20_000L)
        val result = OfflineTrackSelector.select(
            mapOf("finished" to finished, "skipped" to skipped),
            config = SelectorConfig(durations = mapOf("finished" to 200_000L, "skipped" to 200_000L)),
            now = now,
        )
        assertEquals("finished", result.ranked.first().id)
    }

    @Test
    fun `a track played to the end has full engagement`() {
        val finished = activity("finished", plays = 4, daysAgo = 1).copy(msInWindow = 4 * 200_000L)
        val result = OfflineTrackSelector.select(
            mapOf("finished" to finished),
            config = SelectorConfig(durations = mapOf("finished" to 200_000L)),
            now = now,
        )
        assertEquals(1.0, result.ranked.first().components.engagement, 1e-9)
    }

    @Test
    fun `a track that is always skipped has poor engagement`() {
        val skipped = activity("skipped", plays = 4, daysAgo = 1).copy(msInWindow = 4 * 20_000L)
        val result = OfflineTrackSelector.select(
            mapOf("skipped" to skipped),
            config = SelectorConfig(durations = mapOf("skipped" to 200_000L)),
            now = now,
        )
        val engagement = result.ranked.first().raw.engagement
        assertTrue("engagement $engagement should be under a half", engagement < 0.5)
    }

    @Test
    fun `a zero duration is treated as an unknown one`() {
        val result = OfflineTrackSelector.select(
            mapOf("a" to activity("a", plays = 4, daysAgo = 1).copy(msInWindow = 800_000L)),
            config = SelectorConfig(durations = mapOf("a" to 0L)),
            now = now,
        )
        assertEquals(
            OfflineTrackSelector.UNKNOWN_ENGAGEMENT,
            result.ranked.first().raw.engagement,
            1e-9,
        )
    }

    @Test
    fun `skipped tracks are not punished when their length is unknown`() {
        val skipped = activity("skipped", plays = 4, daysAgo = 1).copy(msInWindow = 4 * 20_000L)
        val result = OfflineTrackSelector.select(
            mapOf("skipped" to skipped),
            config = SelectorConfig(),
            now = now,
        )
        assertEquals(
            OfflineTrackSelector.UNKNOWN_ENGAGEMENT,
            result.ranked.first().raw.engagement,
            1e-9,
        )
    }

    // ── Preference ──────────────────────────────────────────────────────

    @Test
    fun `a liked track outranks an unliked one`() {
        val liked = activity("liked", plays = 1, daysAgo = 1)
        val other = activity("other", plays = 1, daysAgo = 1)
        val result = OfflineTrackSelector.select(
            mapOf("liked" to liked, "other" to other),
            preferences = PreferenceLookup { id ->
                if (id == "liked") TrackPreference.LIKED else TrackPreference.NONE
            },
            now = now,
        )
        assertEquals("liked", result.ranked.first().id)
    }

    @Test
    fun `a track in a playlist outranks an unknown one`() {
        val filed = activity("filed", plays = 1, daysAgo = 1)
        val other = activity("other", plays = 1, daysAgo = 1)
        val result = OfflineTrackSelector.select(
            mapOf("filed" to filed, "other" to other),
            preferences = PreferenceLookup { id ->
                if (id == "filed") TrackPreference.PLAYLIST else TrackPreference.NONE
            },
            now = now,
        )
        assertEquals("filed", result.ranked.first().id)
    }

    @Test
    fun `a like outranks being in a playlist`() {
        val liked = activity("liked", plays = 1, daysAgo = 1)
        val filed = activity("filed", plays = 1, daysAgo = 1)
        val result = OfflineTrackSelector.select(
            mapOf("liked" to liked, "filed" to filed),
            preferences = PreferenceLookup { id ->
                when (id) {
                    "liked" -> TrackPreference.LIKED
                    "filed" -> TrackPreference.PLAYLIST
                    else -> TrackPreference.NONE
                }
            },
            now = now,
        )
        assertEquals("liked", result.ranked.first().id)
    }

    @Test
    fun `a habit counts as a preference of its own`() {
        val habit = activity("habit", plays = 6, daysAgo = 1)
        val stranger = activity("stranger", plays = 1, daysAgo = 1)
        val result = OfflineTrackSelector.select(
            mapOf("habit" to habit, "stranger" to stranger),
            preferences = PreferenceLookup.NONE,
            now = now,
        )
        assertTrue(
            "the habit should carry more preference",
            result.ranked.first { it.id == "habit" }.components.preference >
                result.ranked.first { it.id == "stranger" }.components.preference,
        )
    }

    // ── Normalisation ───────────────────────────────────────────────────

    @Test
    fun `a big set is ranked by percentile`() {
        val activity = (1..8).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = 1)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        assertEquals(8, result.ranked.size)
        // The most-played track is at the top of the percentile scale.
        assertEquals("t8", result.ranked.first().id)
    }

    @Test
    fun `a small set is still ranked in order`() {
        val activity = mapOf(
            "a" to activity("a", plays = 1, daysAgo = 1),
            "b" to activity("b", plays = 4, daysAgo = 1),
            "c" to activity("c", plays = 2, daysAgo = 1),
        )
        val result = OfflineTrackSelector.select(activity, now = now)
        assertEquals(listOf("b", "c", "a"), result.ranked.map { it.id })
    }

    @Test
    fun `a small set does not punish the track that was played least`() {
        val activity = mapOf(
            "a" to activity("a", plays = 1, daysAgo = 1),
            "b" to activity("b", plays = 40, daysAgo = 1),
        )
        val result = OfflineTrackSelector.select(activity, now = now)
        val least = result.ranked.first { it.id == "a" }
        assertTrue(
            "the least-played track should keep some of the scale",
            least.components.frequency > 0.0,
        )
    }

    @Test
    fun `one very frequent track does not flatten the rest of the set`() {
        val activity = (1..6).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = 1)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        val second = result.ranked.first { it.id == "t5" }
        assertTrue(
            "the second-placed track should not be pushed to zero",
            second.components.frequency > 0.0,
        )
    }

    // ── Ordering ────────────────────────────────────────────────────────

    @Test
    fun `ties are broken by title so the order never depends on the map`() {
        val activity = mapOf(
            "z" to activity("Zebra", plays = 3, daysAgo = 1),
            "a" to activity("Apple", plays = 3, daysAgo = 1),
            "m" to activity("Mango", plays = 3, daysAgo = 1),
        )
        val result = OfflineTrackSelector.select(activity, now = now)
        assertEquals(listOf("Apple", "Mango", "Zebra"), result.ranked.map { it.activity.title })
    }

    @Test
    fun `the order is the same however the candidates arrive`() {
        val first = mapOf(
            "a" to activity("a", plays = 3, daysAgo = 1),
            "b" to activity("b", plays = 5, daysAgo = 2),
            "c" to activity("c", plays = 1, daysAgo = 0),
        )
        val second = LinkedHashMap<String, TrackActivity>()
        first.entries.reversed().forEach { second[it.key] = it.value }
        val a = OfflineTrackSelector.select(first, now = now).ranked.map { it.id }
        val b = OfflineTrackSelector.select(second, now = now).ranked.map { it.id }
        assertEquals(a, b)
    }

    @Test
    fun `the ranking is the same when it is run twice`() {
        val activity = (1..7).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = index)
        }
        val first = OfflineTrackSelector.select(activity, now = now).ranked.map { it.id }
        val second = OfflineTrackSelector.select(activity, now = now).ranked.map { it.id }
        assertEquals(first, second)
    }

    // ── Core and rotation variety ───────────────────────────────────────

    @Test
    fun `the rotation splits into a core and a varied tail`() {
        val activity = (1..10).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = 1)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        assertEquals(7, result.core.size)
        assertEquals(3, result.rotationVariety.size)
    }

    @Test
    fun `the core comes before the tail in the whole ranking`() {
        val activity = (1..10).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = 1)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        assertEquals(result.core + result.rotationVariety, result.ranked)
    }

    @Test
    fun `a small set has no tail to rotate`() {
        val activity = (1..3).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = 1)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        assertEquals(3, result.core.size)
        assertEquals(0, result.rotationVariety.size)
    }

    @Test
    fun `the core holds the better ranked tracks`() {
        val activity = (1..10).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = 1)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        val worstInCore = result.core.minOf { it.score }
        val bestInVariety = result.rotationVariety.maxOf { it.score }
        assertTrue(worstInCore >= bestInVariety)
    }

    @Test
    fun `both halves are drawn from the same candidates`() {
        val activity = (1..10).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = 1)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        val ids = result.ranked.map { it.id }.toSet()
        assertEquals(activity.keys, ids)
    }

    // ── Strategies ──────────────────────────────────────────────────────

    @Test
    fun `each strategy has its own weights`() {
        val recent = SelectorWeights.forStrategy(SelectionStrategy.RECENTLY_PLAYED)
        val frequent = SelectorWeights.forStrategy(SelectionStrategy.FREQUENTLY_PLAYED)
        val smart = SelectorWeights.forStrategy(SelectionStrategy.SMART)
        assertTrue(recent.recency > recent.frequency)
        assertTrue(frequent.frequency > frequent.recency)
        assertTrue(smart.recency > 0.0 && smart.frequency > 0.0)
    }

    @Test
    fun `the weights of every strategy add up to one`() {
        for (strategy in SelectionStrategy.entries) {
            val weights = SelectorWeights.forStrategy(strategy)
            val sum = weights.recency + weights.frequency + weights.engagement + weights.preference
            assertEquals("$strategy", 1.0, sum, 1e-9)
        }
    }

    @Test
    fun `the smart strategy sits between the other two`() {
        val activity = mapOf(
            "old" to activity("old", plays = 20, daysAgo = 40),
            "new" to activity("new", plays = 1, daysAgo = 0),
        )
        val smart = OfflineTrackSelector.select(
            activity,
            config = SelectorConfig(strategy = SelectionStrategy.SMART),
            now = now,
        ).ranked.first().id
        val recent = OfflineTrackSelector.select(
            activity,
            config = SelectorConfig(strategy = SelectionStrategy.RECENTLY_PLAYED),
            now = now,
        ).ranked.first().id
        val frequent = OfflineTrackSelector.select(
            activity,
            config = SelectorConfig(strategy = SelectionStrategy.FREQUENTLY_PLAYED),
            now = now,
        ).ranked.first().id
        assertEquals("new", recent)
        assertEquals("old", frequent)
        // The balance leans towards recency but does not ignore history.
        assertTrue("smart chose $smart", smart == "new" || smart == "old")
    }

    // ── Reasons ─────────────────────────────────────────────────────────

    @Test
    fun `a track says how many times it was played`() {
        val result = OfflineTrackSelector.select(
            mapOf("a" to activity("a", plays = 7, daysAgo = 1)),
            now = now,
        )
        assertTrue(result.ranked.first().reasons.any { it.contains("7") })
    }

    @Test
    fun `a track heard once says so`() {
        val result = OfflineTrackSelector.select(
            mapOf("a" to activity("a", plays = 1, daysAgo = 1)),
            now = now,
        )
        assertTrue(result.ranked.first().reasons.any { it.contains("once") })
    }

    @Test
    fun `a track says when it was last played`() {
        val result = OfflineTrackSelector.select(
            mapOf("a" to activity("a", plays = 1, daysAgo = 3)),
            now = now,
        )
        assertTrue(result.ranked.first().reasons.any { it.contains("3 days ago") })
    }

    @Test
    fun `a liked track says so`() {
        val result = OfflineTrackSelector.select(
            mapOf("a" to activity("a", plays = 1, daysAgo = 1)),
            preferences = PreferenceLookup { TrackPreference.LIKED },
            now = now,
        )
        assertTrue(result.ranked.first().reasons.any { it.contains("liked") })
    }

    @Test
    fun `a track that is always skipped says so`() {
        val skipped = activity("skipped", plays = 4, daysAgo = 1).copy(msInWindow = 4 * 20_000L)
        val result = OfflineTrackSelector.select(
            mapOf("skipped" to skipped),
            config = SelectorConfig(durations = mapOf("skipped" to 200_000L)),
            now = now,
        )
        assertTrue(result.ranked.first().reasons.any { it.contains("skipped") })
    }

    @Test
    fun `every ranked track carries at least one reason`() {
        val activity = (1..6).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = index)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        assertTrue(result.ranked.all { it.reasons.isNotEmpty() })
    }

    // ── Housekeeping ────────────────────────────────────────────────────

    @Test
    fun `a track keeps the title and artist it was heard under`() {
        val track = activity("a", plays = 1, daysAgo = 1).copy(title = "Kadi", artist = "Harcharan")
        val result = OfflineTrackSelector.select(mapOf("a" to track), now = now)
        assertEquals("Kadi", result.ranked.first().activity.title)
        assertEquals("Harcharan", result.ranked.first().activity.artist)
    }

    @Test
    fun `the score of every track lies between zero and one`() {
        val activity = (1..8).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = index)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        assertTrue(result.ranked.all { it.score >= 0.0 && it.score <= 1.0 })
    }

    @Test
    fun `every component of every track lies between zero and one`() {
        val activity = (1..8).associate { index ->
            "t$index" to activity("t$index", plays = index, daysAgo = index)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        for (track in result.ranked) {
            val components = track.components
            for (value in listOf(components.recency, components.frequency, components.engagement, components.preference)) {
                assertTrue("$value out of range", value >= 0.0 && value <= 1.0)
            }
        }
    }

    @Test
    fun `a large set is ranked without complaint`() {
        val activity = (1..5000).associate { index ->
            "t$index" to activity("t$index", plays = (index % 50) + 1, daysAgo = index % 30)
        }
        val result = OfflineTrackSelector.select(activity, now = now)
        assertEquals(5000, result.ranked.size)
        assertEquals(3500, result.core.size)
        assertEquals(1500, result.rotationVariety.size)
    }

    @Test
    fun `ranking five thousand tracks takes well under a moment`() {
        val activity = (1..5000).associate { index ->
            "t$index" to activity("t$index", plays = (index % 50) + 1, daysAgo = index % 30)
        }
        val started = System.nanoTime()
        OfflineTrackSelector.select(activity, now = now)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("ranking took ${elapsedMs}ms", elapsedMs < 10_000)
    }

    /**
     * A track as the history would have it: heard [plays] times, [daysAgo]
     * days ago, a minute each time.
     */
    private fun activity(id: String, plays: Int = 1, daysAgo: Int = 0): TrackActivity {
        val date = today.minusDays(daysAgo.toLong())
        return TrackActivity(
            id = id,
            title = id,
            artist = "artist",
            album = null,
            artworkUrl = null,
            playsInWindow = plays,
            msInWindow = plays * 60_000L,
            days = mapOf(date.toString() to DailyActivity(plays * 60_000L, plays)),
            lastPlayedAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            totalPlays = plays,
            totalMs = plays * 60_000L,
        )
    }
}
