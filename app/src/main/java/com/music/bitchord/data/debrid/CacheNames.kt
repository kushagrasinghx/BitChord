package com.music.bitchord.data.debrid

import com.music.bitchord.data.sources.StreamFormat
import java.security.MessageDigest

/**
 * The deterministic-filename cache check: which file names to hash, and how
 * to read the answer back.
 *
 * A debrid cache is keyed by torrent infohash, which this app can never know
 * up front — nobody publishes the infohash of the file holding one track.
 * What it *can* know is the exact file name a seeder used, when that name is
 * built by convention: `{Clean Title} - {Clean Artist} [Tag {Catalogue Id} -
 * {Quality Label}].{ext}`. The MD5 of that name is what the cache is asked
 * about, and a hit whose reported name equals the asked name is the track.
 *
 * The `BitTor` tag inside [filename] is a byte-exact protocol constant,
 * not branding: seeders name their files with it, so any other tag hashes
 * differently and never hits. Everything else here is pure string and hash
 * work, so it stays unit-testable on the JVM with no Android or network in
 * the loop.
 */
object CacheNames {

    /** One quality rung a cache may hold a track at. */
    data class Tier(
        /** As it appears in the file name, e.g. `Hi-Res`. */
        val label: String,
        /** File extension, e.g. `flac`. */
        val ext: String,
        /** Bitrate the rung really delivers, null when the container says lossless. */
        val kbps: Int?,
        /** Container/codec for [StreamFormat]. */
        val codec: String,
    )

    /** Highest fidelity first — the order a stream is picked in. */
    val TIERS = listOf(
        Tier("Hi-Res", "flac", kbps = null, codec = "flac"),
        Tier("HiFi CD", "flac", kbps = null, codec = "flac"),
        Tier("320kbps", "m4a", kbps = 320, codec = "mp4"),
        Tier("96kbps", "m4a", kbps = 96, codec = "mp4"),
    )

    /** One asked file: its tier, its exact name, and the hash the cache is asked about. */
    data class Candidate(val tier: Tier, val name: String, val md5: String)

    /**
     * Filesystem-hostile characters, stripped from titles and artists before
     * a name is built. Matches the seeder convention exactly — one missed
     * character is a hash that can never hit.
     */
    fun clean(value: String): String =
        value.replace(Regex("[/\\\\?%*:|\"<>]"), "").trim()

    /** The exact file name a seeder uses for [catalogId] at [tier]. */
    fun filename(title: String, artist: String, catalogId: Long, tier: Tier): String =
        "${clean(title)} - ${clean(artist)} [BitTor $catalogId - ${tier.label}].${tier.ext}"

    fun md5Hex(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Every tier's candidate for one catalogue track. */
    fun candidates(title: String, artist: String, catalogId: Long): List<Candidate> =
        TIERS.map { tier ->
            val name = filename(title, artist, catalogId, tier)
            Candidate(tier, name, md5Hex(name))
        }

    /**
     * Which tiers came back cached, best first.
     *
     * @param wanted the asked candidates, by hash.
     * @param hits the cache answer: asked hash to the file name it holds.
     *
     * A hit only counts when the held name *equals* the asked name. The cache
     * keys its answer by infohash, so a bare presence only says *a* file with
     * that hash exists; the name is what says it is this track at this tier.
     */
    fun matchedTiers(
        wanted: Map<String, Candidate>,
        hits: Map<String, String>,
    ): List<Tier> =
        wanted
            .filter { (md5, candidate) -> hits[md5] == candidate.name }
            .values
            .map { it.tier }
            .distinct()
            .sortedBy { TIERS.indexOf(it) }

    fun formatOf(tier: Tier): StreamFormat = StreamFormat(codec = tier.codec, kbps = tier.kbps)

    /**
     * Whether [query]'s words show up in a `title artist` filing at all.
     * A light pre-filter before spending a cache check: catalogue answers
     * are relevance-ordered, and anything past the first rows that shares no
     * words with the question is a different song with the same chorus.
     */
    fun queryOverlap(query: String, title: String, artist: String): Double {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (words.isEmpty()) return 0.0
        val haystack = "$title $artist".lowercase()
        val hit = words.count { haystack.contains(it) }
        return hit.toDouble() / words.size
    }
}
