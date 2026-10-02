package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import java.util.Locale

/**
 * Builds the station that plays on after a one-off song.
 *
 * Two problems this exists to solve:
 *
 *  - YouTube's watch queues routinely carry the same recording twice — the
 *    official audio and the music video are separate videoIds with near
 *    identical titles — so de-duping on id alone lets a track play, then play
 *    again as its video. Songs are matched on a normalised title plus lead
 *    artist instead.
 *
 *  - A radio mix leans hard on the seed's own artist. Capping how many tracks
 *    any one artist contributes keeps a station from turning into a single
 *    artist's discography.
 */
object QueueBuilder {

    /** No more than this many tracks by one artist in a single batch. */
    private const val PER_ARTIST_LIMIT = 2

    /** The artist the station was seeded on gets more room, but not the run of it. */
    private const val SEED_ARTIST_LIMIT = 4

    /**
     * The subset of [candidates] worth appending after [existing]: nothing
     * already queued, nothing repeated, at most [limit] tracks.
     */
    fun extend(
        existing: List<Song>,
        candidates: List<Song>,
        limit: Int,
        /**
         * Titles — raw, as they read on screen — that must not turn up in this
         * batch or be handed back under a second name. AutoPlay passes the seed
         * and the queue it is topping up, because a radio answers a song with
         * its own title far too readily: a cover, a slowed cut, a re-upload
         * billed to somebody else. They are different ids by different artists,
         * so [isSameRecording] is right to call them different recordings, and
         * the listener who just heard "A" still does not want "A" again.
         */
        excludedTitles: Set<String> = emptySet(),
    ): List<Song> {
        val taken = existing.toMutableList()
        val perArtist = mutableMapOf<String, Int>()
        val seedArtists = existing.lastOrNull()?.artist?.let(::artistSet).orEmpty()
        val out = mutableListOf<Song>()
        val titles = excludedTitles.mapNotNullTo(mutableSetOf()) { baseTitle(it).ifEmpty { null } }

        // A mix pairs all but every track with its own music-video upload —
        // the same recording under a different id, titled far enough apart
        // ("Dildaara (Stand By Me)" against "Lyrical Video: Dildara Song")
        // that no title match will catch it. The catalogue cut is the one a
        // music app wants; videos only stand in when there is nothing else.
        val songsOnly = candidates.filterNot { it.isVideo }
        val ordered = songsOnly.ifEmpty { candidates }

        for (candidate in ordered) {
            if (out.size >= limit) break
            if (taken.any { isSameRecording(it, candidate) }) continue

            val title = baseTitle(candidate.title)
            if (title.isNotEmpty() && title in titles) continue

            val artists = artistSet(candidate.artist)
            val key = artists.minOrNull()
            if (key != null) {
                val cap = if (artists.any { it in seedArtists }) {
                    SEED_ARTIST_LIMIT
                } else {
                    PER_ARTIST_LIMIT
                }
                val played = perArtist[key] ?: 0
                if (played >= cap) continue
                perArtist[key] = played + 1
            }

            taken += candidate
            out += candidate
            if (title.isNotEmpty()) titles += title
        }
        return out
    }

    /**
     * Whether two entries are the same recording. Ids differ between the audio
     * and video cuts of a track, which is exactly the case id equality misses.
     */
    fun isSameRecording(a: Song, b: Song): Boolean {
        if (a.videoId == b.videoId) return true
        val title = normalisedTitle(a.title)
        if (title.isEmpty() || title != normalisedTitle(b.title)) return false
        // One shared name is enough. The two cuts of a track get billed in
        // whatever order the upload used — "Pritam, Arijit Singh & Shilpa Rao"
        // against "Shilpa Rao, Arijit Singh, & Pritam" is one song, twice.
        val left = artistSet(a.artist)
        val right = artistSet(b.artist)
        return left.isEmpty() || right.isEmpty() || left.any { it in right }
    }

    /**
     * `Kesariya (From "Brahmastra") | Official Video` and `Kesariya` are one
     * recording as far as a queue is concerned. Remix and cover markers are
     * deliberately left in — those really are different tracks.
     *
     * Han text is folded to one script first ([HanVariants]): a mainland release
     * billed in Simplified characters and the Taiwanese one of the same track
     * billed in Traditional are one recording, and no other rule here can see
     * that.
     */
    internal fun normalisedTitle(raw: String): String = HanVariants.fold(raw)
        .lowercase(Locale.ROOT)
        .substringBefore(" | ")
        .replace(NOISE, " ")
        .replace(PUNCTUATION, " ")
        .replace(SPACES, " ")
        .trim()

    /**
     * [normalisedTitle] with every qualifier taken off, so `A`, `A (Live)` and
     * `A (Slowed + Reverb)` all answer to `a`. Han text is folded here too, so a
     * name written either way round is one name.
     *
     * [normalisedTitle] keeps those markers on purpose: a remix is a different
     * recording worth queueing in its own right. This is the blunter comparison
     * AutoPlay needs, where the question is not whether two entries are the same
     * recording but whether the station is about to hand back the name the
     * listener is already hearing.
     */
    internal fun baseTitle(raw: String): String = normalisedTitle(raw.replace(BRACKETED, " "))

    /** The cast behind a credit, split out so billing order stops mattering. */
    internal fun artistSet(raw: String): Set<String> = HanVariants.fold(raw)
        .lowercase(Locale.ROOT)
        .replace(TOPIC, " ")
        .split(",", "&", "·", "•", ";", " feat", " ft.", " ft ", " x ", " with ")
        .map { it.replace(PUNCTUATION, " ").replace(SPACES, " ").trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val NOISE = Regex(
        """\((?:official|lyric|lyrics|lyrical|audio|video|visuali[sz]er|full song|hd|4k)[^)]*\)""" +
            """|\(from[^)]*\)""" +
            """|\[[^]]*]""" +
            """|\b(?:official (?:video|audio|music video)|lyrical video|full video|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TOPIC = Regex("""\s*-\s*topic\b""", RegexOption.IGNORE_CASE)
    /** Every `(...)` / `[...]` group, qualifier or not — see [baseTitle]. */
    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val PUNCTUATION = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")
}
