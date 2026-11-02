package com.music.bitchord.data.model

import kotlinx.serialization.Serializable

/**
 * A YouTube Music watch queue to start: what the `next` endpoint is asked for
 * when a station card is tapped. Mixes and playlist radios need only the
 * playlist; an artist's Mix and Shuffle buttons carry a seed track and params
 * as well, and are sent back exactly as the page gave them.
 */
@Serializable
data class WatchTarget(
    val playlistId: String,
    val videoId: String? = null,
    val params: String? = null,
)

/**
 * The cards of Home's "Top Picks for You" row, in the order they're laid out.
 * Each kind has its own artwork treatment and its own line above the title.
 */
@Serializable
enum class TopPickKind {
    /** The newest single or album from one of the listener's top artists. Opens the release. */
    LATEST_RELEASE,

    /** The account's own station — YouTube's My Supermix. */
    PERSONAL_STATION,

    /** A mood station, chosen for the time of day. */
    MOOD,

    /** An artist's Mix: the artist and the people their fans also play. */
    ARTIST_AND_SIMILAR,

    /** One artist on their own, their catalogue shuffled. */
    FEATURED_ARTIST,

    /** YouTube's New Release Mix, billed by the artists in it. */
    NEW_RELEASE_MIX,

    /** Music the listener hasn't played yet — YouTube's Discover Mix. */
    DISCOVERY_STATION,
}

/**
 * The moods a Mood for You card — or a Find Your Mood tile — can be: Apple
 * Music's own set. The first seven are its "Find Your Mood" stations; the
 * rest are the activities in its Moods and Activities room. Each is played
 * from one of YouTube Music's eleven mood categories.
 *
 * [params] is the category's id on YouTube Music's Moods & moments page,
 * which is the same in every interface language — the label beside it is
 * not — so a mood is found by this alone, without fetching the category list.
 * Many of Apple's moods share a YouTube category; [shelfWords] steer each to
 * the shelves inside it that fit (Heartbreak to "Breakups & heartbreak",
 * Wellbeing to "Mindfulness & meditation" and "Yoga", …).
 */
@Serializable
enum class TopPickMood(val params: String, val shelfWords: List<String> = emptyList()) {
    // Find Your Mood
    ENERGY(ENERGIZE),
    RELAX(CHILL_CATEGORY, listOf("unwind", "peaceful", "relaxing", "laidback")),
    FEELING_BLUE(SAD, listOf("sad", "melanchol", "lonely")),
    FEEL_GOOD(FEEL_GOOD_CATEGORY),
    FOCUS(FOCUS_CATEGORY),
    HEARTBREAK(SAD, listOf("heartbreak", "breakup", "break-up", "broken", "heartache")),
    LOVE(ROMANCE),

    // Moods and Activities
    AFTER_HOURS(CHILL_CATEGORY, listOf("deep grooves", "electronic", "jazz", "r&b")),
    ALONE_TIME(CHILL_CATEGORY, listOf("mellow", "acoustic", "laidback")),
    CHILL(CHILL_CATEGORY, listOf("chill", "chilled")),
    COMMUTING(COMMUTE),
    EATING_COOKING(CHILL_CATEGORY, listOf("coffee", "soul", "jazz", "r&b")),
    EVENING(CHILL_CATEGORY, listOf("unwind", "acoustic", "laidback", "jazz")),
    FITNESS(WORKOUT, listOf("gym", "cardio", "essentials")),
    GAMING(GAMING_CATEGORY),
    HOME(CHILL_CATEGORY, listOf("coffee shop", "chill pop", "laidback")),
    MORNING(FEEL_GOOD_CATEGORY, listOf("happy", "confident", "pop")),
    MOTIVATION(ENERGIZE, listOf("motivation", "power", "beast")),
    OUTDOORS(WORKOUT, listOf("outdoor", "running")),
    PARTY(PARTY_CATEGORY),
    SLEEP(SLEEP_CATEGORY),
    SOCIAL(PARTY_CATEGORY, listOf("party today", "chill party", "throwback")),
    SPORTS(ENERGIZE, listOf("beast", "power", "hip-hop")),
    SUMMERTIME(FEEL_GOOD_CATEGORY, listOf("dance", "kicking back", "feel-good pop")),
    HOLIDAY(FEEL_GOOD_CATEGORY, listOf("throwback", "happy")),
    WEEKEND(PARTY_CATEGORY, listOf("party today", "dance")),
    WELLBEING(CHILL_CATEGORY, listOf("mindfulness", "meditation", "yoga")),
    WORK(FOCUS_CATEGORY, listOf("flow", "instrumental", "reading")),
    ;

    /** One of Apple's seven "Find Your Mood" stations, rather than an activity. */
    val isCoreMood: Boolean get() = ordinal <= LOVE.ordinal

    companion object {
        const val CATEGORY_BROWSE_ID = "FEmusic_moods_and_genres_category"
    }
}

// YouTube Music's mood categories, by id.
private const val CHILL_CATEGORY = "ggMPOg1uX1JOQWZFeDByc2Jm"
private const val COMMUTE = "ggMPOg1uX044Z2o5WERLckpU"
private const val ENERGIZE = "ggMPOg1uX2lRZUZiMnNrQnJW"
private const val FEEL_GOOD_CATEGORY = "ggMPOg1uXzZQbDB5eThLRTQ3"
private const val FOCUS_CATEGORY = "ggMPOg1uX0NvNGNhWThMYWRh"
private const val GAMING_CATEGORY = "ggMPOg1uX3NmUVV4Vzl3WGQ0"
private const val PARTY_CATEGORY = "ggMPOg1uX0pmQ0s2V0JRclZs"
private const val ROMANCE = "ggMPOg1uX0FzQ2FhZWtUY211"
private const val SAD = "ggMPOg1uX0JLQ0gySWZKZVY1"
private const val SLEEP_CATEGORY = "ggMPOg1uX1MxaFQ3Z0JMZkN4"
private const val WORKOUT = "ggMPOg1uX09LWkhnTjRGRUJh"

/**
 * One card of the Top Picks row.
 *
 * Holds only what the card shows and what a tap needs. The queue behind a
 * station is fetched when the card is tapped, never ahead of time, so the row
 * costs nothing beyond the requests that built it.
 */
@Serializable
data class TopPick(
    val kind: TopPickKind,
    /** The release, artist or mix name. Empty where the card names itself (stations, moods). */
    val title: String = "",
    /** The line under the title: the release's artist, or the artists a mix is made of. */
    val subtitle: String = "",
    /** The artist a card is billed under — "Featuring …", "… & Similar Artists". */
    val artistName: String? = null,
    /**
     * [artistName]'s YouTube channel, on the artist cards — what their Apple
     * Music clip is looked up and proved against.
     */
    val artistBrowseId: String? = null,
    /**
     * Pictures for the artwork. The release cover or the artist photo first;
     * an artist-and-similar card follows it with up to two related artists.
     */
    val images: List<String> = emptyList(),
    /** The page a tap opens, for a card that is a release rather than a station. */
    val browseId: String? = null,
    /** The queue a tap starts, for a station. */
    val watch: WatchTarget? = null,
    val mood: TopPickMood? = null,
    /**
     * For a Discovery Station with no Discover Mix behind it: track ids to
     * leave out of the queue (what the listener has already played). The
     * seed's [artistName] is left out as well, so a station built off an
     * artist's Mix stays a discovery.
     */
    val exclude: List<String> = emptyList(),
    /** Words that steer a mood station toward the listener's own scene, e.g. "bollywood". */
    val hints: List<String> = emptyList(),
)
