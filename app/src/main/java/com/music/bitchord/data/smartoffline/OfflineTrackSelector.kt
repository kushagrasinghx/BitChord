package com.music.bitchord.data.smartoffline

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.pow

/**
 * Which way the rotation is tilted.
 *
 * Three readings of the same listening history, and nothing more — no
 * "recommended" and no discovery of music the user has never heard. Every
 * strategy ranks the same candidates: tracks already in the user's history.
 */
enum class SelectionStrategy {
    /** What was played last, and played recently. */
    RECENTLY_PLAYED,

    /** What is played most, and has been for a while. */
    FREQUENTLY_PLAYED,

    /** The balance of the two, with listening time and preference weighed in. */
    SMART,
}

/**
 * How much each signal counts.
 *
 * The weights sum to one, so a score is on the same scale whichever strategy
 * produced it and a weight can be read as "how much of the decision this is".
 */
data class SelectorWeights(
    val recency: Double,
    val frequency: Double,
    val engagement: Double,
    val preference: Double,
) {
    companion object {
        fun forStrategy(strategy: SelectionStrategy): SelectorWeights = when (strategy) {
            SelectionStrategy.RECENTLY_PLAYED -> SelectorWeights(0.70, 0.10, 0.10, 0.10)
            SelectionStrategy.FREQUENTLY_PLAYED -> SelectorWeights(0.20, 0.55, 0.15, 0.10)
            SelectionStrategy.SMART -> SelectorWeights(0.35, 0.30, 0.15, 0.20)
        }
    }
}

/** What the user has said about a track, durably. */
enum class TrackPreference {
    /** Nothing recorded. */
    NONE,

    /** The track is in one of the user's own playlists. */
    PLAYLIST,

    /** The user has liked the track, and the like is still there tomorrow. */
    LIKED,
}

/**
 * Where a preference comes from.
 *
 * A function rather than a set so the caller decides what "liked" means —
 * today that is the app's own persisted list of liked device files, and it is
 * deliberately *not* the session-scoped YouTube rating, which is empty after
 * a cold start and cannot define a rotation that is meant to last.
 */
fun interface PreferenceLookup {
    fun preferenceOf(id: String): TrackPreference

    companion object {
        /** Nothing is liked and nothing is in a playlist. */
        val NONE: PreferenceLookup = PreferenceLookup { TrackPreference.NONE }
    }
}

/**
 * How the ranking is configured.
 *
 * The numbers are all provisional: they are the shape the ranking was
 * designed with, not the shape it has been tuned to, and they are meant to be
 * moved once the ranking has been run against a real listener's history.
 */
data class SelectorConfig(
    val strategy: SelectionStrategy = SelectionStrategy.SMART,
    /** How many days of history to look at. */
    val windowDays: Int = 28,
    /** How quickly a track stops counting as recent. */
    val recencyHalfLifeDays: Double = 7.0,
    /** How quickly an old play stops counting towards frequency. */
    val frequencyHalfLifeDays: Double = 21.0,
    /** How much of the rotation is the stable core; the rest rotates. */
    val coreShare: Double = 0.7,
    /**
     * Known track durations in milliseconds, where they are known.
     *
     * The listening history does not keep a duration, so a track whose length
     * is unknown cannot be judged on how much of it was heard — see
     * [OfflineTrackSelector.UNKNOWN_ENGAGEMENT].
     */
    val durations: Map<String, Long> = emptyMap(),
    /** The zone the day buckets are counted in. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** The four signals behind a score, each already normalised to 0..1. */
data class ScoreComponents(
    val recency: Double,
    val frequency: Double,
    val engagement: Double,
    val preference: Double,
)

/** One track, with the score it earned and why. */
data class ScoredTrack(
    val id: String,
    val score: Double,
    /** The four signals as they were weighed, each on its own scale. */
    val components: ScoreComponents,
    /**
     * The same four signals before they were put on a common scale.
     *
     * The normalised components are what the ranking used; these are the
     * facts underneath them, and they are what a track's reasons are written
     * from. Engagement in particular only means something here: "half" says
     * the track's length was never known, while a normalised "one" would say
     * it was the most engaging track in the set, which is a different claim.
     */
    val raw: ScoreComponents,
    /** Plain-language reasons, for the person looking at the list. */
    val reasons: List<String>,
    val activity: TrackActivity,
)

/**
 * The ranked rotation: a stable core, and the tail that keeps changing.
 *
 * Both parts come from the same candidates — the tracks already in the
 * listener's history — so "rotation variety" means *ranked lower and weighted
 * to keep changing*, not music the listener has never heard.
 */
data class SelectionResult(
    val core: List<ScoredTrack>,
    val rotationVariety: List<ScoredTrack>,
) {
    /** The whole rotation, core first. */
    val ranked: List<ScoredTrack> get() = core + rotationVariety

    val isEmpty: Boolean get() = core.isEmpty() && rotationVariety.isEmpty()
}

/**
 * Ranks a listener's recent history into a rotation.
 *
 * ## Pure, and deliberately so
 *
 * No Android, no clock, no I/O: the selector is handed the history, the
 * preferences and the moment to measure against, and returns the ranking. That
 * is what makes it testable without a device, and it is what keeps the
 * decision explainable — every score can be taken apart and asked why.
 *
 * ## The four signals
 *
 * - **Recency** — how long ago the track was last heard, on a half-life.
 * - **Frequency** — how many times it was heard in the window, with each play
 *   worth less as it gets older.
 * - **Engagement** — how much of the track was actually heard, which is what
 *   separates a song that is played to the end from one that is skipped every
 *   time.
 * - **Preference** — what the listener has said about it, durably.
 *
 * ## What it will not do
 *
 * It does not recommend, and it does not exclude: there is no store of
 * excluded tracks yet, and adding one would be a different feature. It does
 * not download, delete, or keep anything — it ranks, and explains.
 */
object OfflineTrackSelector {

    /**
     * What engagement is worth when the track's length is not known.
     *
     * Half, not zero: a track whose duration was never recorded is not a
     * track the listener skipped, and scoring it as one would rank every
     * unknown-length track below every known-length one for a reason that has
     * nothing to do with how much of it was heard. Half says "no opinion" in
     * the only way a number can.
     */
    const val UNKNOWN_ENGAGEMENT = 0.5

    /** How much of a track counts as "heard" for the engagement figure. */
    private const val ENGAGED_FRACTION = 0.75

    /** Plays in the window that make a track a favourite on its own. */
    private const val FAMILIAR_PLAYS = 5

    /**
     * How few candidates a set needs before percentiles mean anything.
     *
     * Below this, ranking by percentile would be ranking by noise — the
     * difference between first and second out of three tracks is not a
     * statement about anything.
     */
    private const val PERCENTILE_MIN_SET = 5

    private const val MILLIS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0

    /**
     * Ranks [activity] into a rotation.
     *
     * @param now the moment to measure recency against. The zone the days are
     *   counted in comes from [config], so the whole call is reproducible from
     *   its arguments.
     */
    fun select(
        activity: Map<String, TrackActivity>,
        preferences: PreferenceLookup = PreferenceLookup.NONE,
        config: SelectorConfig = SelectorConfig(),
        now: Instant = Instant.now(),
    ): SelectionResult {
        if (activity.isEmpty()) return SelectionResult(emptyList(), emptyList())

        val zone = config.zone
        val today = now.atZone(zone).toLocalDate()
        val nowMillis = now.toEpochMilli()
        val weights = SelectorWeights.forStrategy(config.strategy)

        val measured = activity.values.map { entry ->
            val duration = config.durations[entry.id]
            val kind = preferences.preferenceOf(entry.id)
            Measured(
                activity = entry,
                recency = recency(entry, nowMillis, config.recencyHalfLifeDays),
                frequency = frequency(entry, today, config.frequencyHalfLifeDays),
                engagement = engagement(entry, duration),
                preferenceKind = kind,
                preferenceValue = preferenceScore(entry, kind),
                nowMillis = nowMillis,
                durationKnown = duration != null && duration > 0L,
            )
        }

        // Each signal is put on its own scale before it is weighed, so that a
        // track with twenty plays does not simply drown out one with two.
        val recencies = measured.associate { it.activity.id to it.recency }
        val frequencies = measured.associate { it.activity.id to it.frequency }
        val engagements = measured.associate { it.activity.id to it.engagement }
        val preferencesById = measured.associate { it.activity.id to it.preferenceValue }
        val normalisedRecency = normalise(recencies)
        val normalisedFrequency = normalise(frequencies)
        val normalisedEngagement = normalise(engagements)
        val normalisedPreference = normalise(preferencesById)

        val scored = measured.map { m ->
            val id = m.activity.id
            val components = ScoreComponents(
                recency = normalisedRecency.getValue(id),
                frequency = normalisedFrequency.getValue(id),
                engagement = normalisedEngagement.getValue(id),
                preference = normalisedPreference.getValue(id),
            )
            val score = components.recency * weights.recency +
                components.frequency * weights.frequency +
                components.engagement * weights.engagement +
                components.preference * weights.preference
            ScoredTrack(
                id = id,
                score = score,
                components = components,
                raw = ScoreComponents(
                    recency = m.recency,
                    frequency = m.frequency,
                    engagement = m.engagement,
                    preference = m.preferenceValue,
                ),
                reasons = reasons(m, config),
                activity = m.activity,
            )
        }.sortedWith(order)

        val total = scored.size
        val varietyCount = (total * (1.0 - config.coreShare)).toInt().coerceIn(0, total)
        val coreCount = total - varietyCount
        return SelectionResult(
            core = scored.take(coreCount),
            rotationVariety = scored.drop(coreCount),
        )
    }

    /**
     * How the ranking is ordered.
     *
     * Score first, then the signals that matter most when scores are close —
     * engagement, because finishing a track says more than starting it —
     * then recency, then frequency. The last two comparisons are on the
     * title and the artist, and they are there so the order never depends on
     * which way a hash table happened to be walked.
     */
    private val order: Comparator<ScoredTrack> = Comparator<ScoredTrack> { a, b ->
        var result = b.score.compareTo(a.score)
        if (result != 0) return@Comparator result
        result = b.components.engagement.compareTo(a.components.engagement)
        if (result != 0) return@Comparator result
        result = b.activity.lastPlayedAt.compareTo(a.activity.lastPlayedAt)
        if (result != 0) return@Comparator result
        result = b.components.frequency.compareTo(a.components.frequency)
        if (result != 0) return@Comparator result
        result = a.activity.title.compareTo(b.activity.title)
        if (result != 0) return@Comparator result
        a.activity.artist.compareTo(b.activity.artist)
    }

    /** How recently the track was heard, on a half-life. */
    private fun recency(activity: TrackActivity, nowMillis: Long, halfLifeDays: Double): Double {
        if (activity.lastPlayedAt <= 0L) return 0.0
        val halfLife = halfLifeDays * MILLIS_PER_DAY
        if (halfLife <= 0.0) return 0.0
        val age = (nowMillis - activity.lastPlayedAt).coerceAtLeast(0L)
        return 2.0.pow(-age.toDouble() / halfLife).coerceIn(0.0, 1.0)
    }

    /** How often it was heard in the window, with older plays worth less. */
    private fun frequency(activity: TrackActivity, today: LocalDate, halfLifeDays: Double): Double {
        val halfLife = if (halfLifeDays > 0.0) halfLifeDays else 1.0
        var total = 0.0
        for ((key, day) in activity.days) {
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            val age = ChronoUnit.DAYS.between(date, today).toDouble()
            if (age < 0.0) continue
            total += day.plays * 2.0.pow(-age / halfLife)
        }
        return total
    }

    /**
     * How much of the track was heard.
     *
     * The expected figure is three quarters of the track for every play that
     * was counted, so a track heard all the way through scores one and a track
     * skipped at the chorus scores a fraction. A play that never reached the
     * threshold still cost time, so the expectation is never below one track's
     * worth — otherwise a run of skipped tracks would divide by nothing and
     * score as though they were finished.
     */
    private fun engagement(activity: TrackActivity, durationMs: Long?): Double {
        if (durationMs == null || durationMs <= 0L) return UNKNOWN_ENGAGEMENT
        val expected = max(activity.playsInWindow, 1).toDouble() * durationMs * ENGAGED_FRACTION
        if (expected <= 0.0) return UNKNOWN_ENGAGEMENT
        return (activity.msInWindow.toDouble() / expected).coerceIn(0.0, 1.0)
    }

    /** What the listener has said about it, durably. */
    private fun preferenceScore(activity: TrackActivity, kind: TrackPreference): Double = when (kind) {
        TrackPreference.LIKED -> 1.0
        TrackPreference.PLAYLIST -> 0.6
        TrackPreference.NONE -> if (activity.playsInWindow >= FAMILIAR_PLAYS) 0.3 else 0.0
    }

    /**
     * Puts a set of values on a common scale.
     *
     * With enough candidates to rank, each value is replaced by the share of
     * candidates it beats — a percentile — which does not care how long the
     * scale is, so one track played fifty times does not flatten everything
     * else into zero.
     *
     * With too few candidates for a percentile to mean anything, each value is
     * scaled against the best in the set instead, which keeps the ordering
     * right and says only that the gaps are not meaningful.
     */
    private fun normalise(values: Map<String, Double>): Map<String, Double> {
        val count = values.size
        if (count == 0) return emptyMap()
        if (count < PERCENTILE_MIN_SET) {
            val best = values.values.max()
            if (best <= 0.0) return values.mapValues { 0.0 }
            return values.mapValues { (_, value) -> (value / best).coerceIn(0.0, 1.0) }
        }
        return values.mapValues { (_, value) ->
            val less = values.values.count { it < value }
            val equal = values.values.count { it == value }
            (less + 0.5 * equal) / count
        }
    }

    /**
     * Why this track is on the list, in words.
     *
     * The reasons are about facts, not scores: a number the listener can
     * check. A track that was heard once says so, because "played once" and
     * "played forty times" are different stories and the list should tell
     * which one it is.
     */
    private fun reasons(measured: Measured, config: SelectorConfig): List<String> {
        val activity = measured.activity
        val reasons = mutableListOf<String>()

        when (activity.playsInWindow) {
            0 -> reasons += "Heard in the last ${config.windowDays} days"
            1 -> reasons += "Heard once in the last ${config.windowDays} days"
            else -> reasons += "Played ${activity.playsInWindow} times in the last ${config.windowDays} days"
        }

        val minutes = activity.msInWindow / 60_000L
        if (minutes >= 1L) {
            val hours = minutes / 60L
            reasons += if (hours >= 1L) {
                "Listened for ${hours}h ${minutes % 60L}m"
            } else {
                "Listened for ${minutes}m"
            }
        }

        if (activity.lastPlayedAt > 0L) {
            reasons += lastPlayedPhrase(activity.lastPlayedAt, measured.nowMillis, config.zone)
        }

        when {
            measured.durationKnown && measured.engagement >= 0.8 ->
                reasons += "Usually played to the end"
            measured.durationKnown && measured.engagement < 0.5 ->
                reasons += "Often skipped before the end"
        }

        when (measured.preferenceKind) {
            TrackPreference.LIKED -> reasons += "You liked this"
            TrackPreference.PLAYLIST -> reasons += "In one of your playlists"
            TrackPreference.NONE -> Unit
        }

        return reasons
    }

    /** "Played today", "Played 3 days ago", and so on. */
    private fun lastPlayedPhrase(lastPlayedAt: Long, nowMillis: Long, zone: ZoneId): String {
        val days = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(lastPlayedAt).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate(),
        )
        return when {
            days <= 0L -> "Played today"
            days == 1L -> "Played yesterday"
            days < 7L -> "Played $days days ago"
            days < 14L -> "Played last week"
            days < 60L -> "Played ${days / 7L} weeks ago"
            else -> "Played ${days / 30L} months ago"
        }
    }

    /** The signals for one track, before they are weighed. */
    private class Measured(
        val activity: TrackActivity,
        val recency: Double,
        val frequency: Double,
        val engagement: Double,
        val preferenceKind: TrackPreference,
        val preferenceValue: Double,
        val nowMillis: Long,
        val durationKnown: Boolean,
    )
}
