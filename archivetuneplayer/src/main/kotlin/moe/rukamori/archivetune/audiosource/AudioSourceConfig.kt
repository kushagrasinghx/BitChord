
package moe.rukamori.archivetune.audiosource

import java.text.Normalizer
import moe.rukamori.archivetune.constants.AudioSourceType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DirectStream(
    val uri: String,
    val mimeType: String,
    val codecs: String,
    val contentLength: Long?,

    val label: String,
    val source: AudioSourceType,

    val matchedTitle: String? = null,
    val matchedArtist: String? = null,
    val matchedAlbum: String? = null,
    val matchedDurationMs: Long? = null,

    val trustedDirectId: Boolean = false,

    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val bitrate: Int? = null,
)

fun DirectStream.pcmBitrateOrNull(channels: Int = 2): Int? {
    val rate = sampleRate?.takeIf { it > 0 } ?: return null
    val depth = bitDepth?.takeIf { it > 0 } ?: return null
    return rate * depth * channels
}

data class CurrentStreamInfo(
    val mediaId: String,
    val source: AudioSourceType,
    val label: String,
    val protocol: String? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
)

object TitleMatch {
    const val ACCEPT_THRESHOLD = 0.78
    private const val TITLE_ONLY_THRESHOLD = 0.95
    private const val MIN_TITLE_WITH_METADATA = 0.84
    private const val MIN_ARTIST = 0.72
    private const val DURATION_HARD_GATE_MS = 15_000L

    data class Result(
        val accepted: Boolean,
        val score: Double,
        val title: Double,
        val artist: Double?,
        val duration: Double?,
        val reason: String,
    )

    fun normalize(value: String?): String =
        (value ?: "")
            .lowercase()
            .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("""\b(feat|ft|featuring)\b.*$"""), "")
            .replace(Regex("""\b(explicit|clean|remaster|remastered|version|audio|official)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun ratio(
        wanted: String?,
        candidate: String?,
    ): Double {
        val a = normalize(wanted)
        val b = normalize(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val direct = jaroWinkler(a, b)

        val wantedSegments = splitRawTitleSegments(wanted)
        val candidateSegments = splitRawTitleSegments(candidate)
        val wantedNormalized = wantedSegments.map(::normalize).filter { it.length >= 3 }
        val candidateNormalized = candidateSegments.map(::normalize).filter { it.length >= 3 }

        val bestWantedSegment = wantedNormalized.maxOfOrNull { jaroWinkler(it, b) } ?: 0.0
        val bestCandidateSegment = candidateNormalized.maxOfOrNull { jaroWinkler(a, it) } ?: 0.0
        val bestCrossSegment =
            wantedNormalized.maxOfOrNull { ws ->
                candidateNormalized.maxOfOrNull { jaroWinkler(ws, it) } ?: 0.0
            } ?: 0.0
        return maxOf(direct, bestWantedSegment, bestCandidateSegment, bestCrossSegment)
    }

    private fun splitRawTitleSegments(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val split =
            raw
                .split(Regex("""\s*[-/|:]\s*|\s*[－／｜：]\s*"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        return if (split.size <= 1) emptyList() else split
    }

    fun evaluate(
        wantedTitle: String,
        wantedArtists: List<String>,
        wantedAlbum: String?,
        wantedDurationMs: Long?,
        stream: DirectStream,
    ): Result {
        if (stream.trustedDirectId) return Result(true, 1.0, 1.0, 1.0, 1.0, "trusted catalog id")
        val candidateTitle = stream.matchedTitle
            ?: return Result(false, 0.0, 0.0, null, null, "provider returned no matched metadata")
        if (hasVersionMismatch(wantedTitle, candidateTitle)) {
            return Result(false, 0.0, ratio(wantedTitle, candidateTitle), null, null, "version mismatch")
        }

        val titleScore = ratio(wantedTitle, candidateTitle)
        val durationScore = durationScore(wantedDurationMs, stream.matchedDurationMs)

        val wantedArtist = wantedArtists.joinToString(", ").takeIf { it.isNotBlank() }
        val candidateArtist = stream.matchedArtist?.takeIf { it.isNotBlank() }
        val artistScore =
            if (wantedArtist != null && candidateArtist != null) artistRatio(wantedArtist, candidateArtist) else null

        if (artistScore != null && artistScore < MIN_ARTIST) {
            return Result(false, 0.0, titleScore, artistScore, durationScore, "artist mismatch")
        }
        if (durationScore == 0.0) {
            return Result(false, 0.0, titleScore, artistScore, durationScore, "duration differs by more than 15s")
        }

        if (artistScore == null) {
            val accepted = titleScore >= TITLE_ONLY_THRESHOLD
            val score = titleScore * 0.8 + (durationScore ?: 0.5) * 0.2
            return Result(accepted, score, titleScore, null, durationScore, if (accepted) "strict title fallback" else "artist metadata unavailable")
        }
        if (titleScore < MIN_TITLE_WITH_METADATA && !containsTokenRun(candidateTitle, wantedTitle)) {
            return Result(false, 0.0, titleScore, artistScore, durationScore, "title mismatch")
        }

        val albumScore = albumRatio(wantedAlbum, stream.matchedAlbum)
        val score =
            titleScore * 0.45 +
                artistScore * 0.30 +
                (durationScore ?: 0.5) * 0.20 +
                (albumScore ?: 0.5) * 0.05
        return Result(score >= ACCEPT_THRESHOLD, score, titleScore, artistScore, durationScore, if (score >= ACCEPT_THRESHOLD) "metadata match" else "composite score too low")
    }

    private fun durationScore(wantedMs: Long?, candidateMs: Long?): Double? {
        if (wantedMs == null || wantedMs <= 0 || candidateMs == null || candidateMs <= 0) return null
        val difference = abs(wantedMs - candidateMs)
        return when {
            difference > DURATION_HARD_GATE_MS -> 0.0
            difference <= 3_000L -> 1.0
            difference <= 10_000L -> 0.8
            else -> 0.6
        }
    }

    private fun albumRatio(wanted: String?, candidate: String?): Double? {
        if (wanted.isNullOrBlank() || candidate.isNullOrBlank()) return null
        return ratio(wanted, candidate)
    }

    private fun artistRatio(wanted: String, candidate: String): Double {
        val wantedParts = artistParts(wanted)
        val candidateParts = artistParts(candidate.replace(" - Topic", "", ignoreCase = true))
        if (wantedParts.isEmpty() || candidateParts.isEmpty()) return 0.0
        return wantedParts.maxOf { target ->
            candidateParts.maxOf { option ->
                if (target == option || (target.length >= 4 && option.length >= 4 && (target in option || option in target))) {
                    1.0
                } else {
                    jaroWinkler(target, option)
                }
            }
        }
    }

    private fun artistParts(value: String): List<String> =
        value
            .split(Regex("""\s*[,;&/]\s*|\s+(?:and|x)\s+""", RegexOption.IGNORE_CASE))
            .map(::normalize)
            .filter { it.isNotBlank() }

    private fun containsTokenRun(haystack: String, needle: String): Boolean {
        val target = normalize(needle)
        val candidate = normalize(haystack)
        if (target.length < 3) return false
        if (candidate == target) return true
        if (candidate.contains(target)) return true

        return splitRawTitleSegments(needle).any { segment ->
            val n = normalize(segment)
            n.length >= 3 && (candidate == n || candidate.contains(n))
        }
    }

    private val versionMarkers =
        listOf(
            "live", "concert", "remix", "rework", "acoustic", "instrumental", "karaoke",
            "cover", "demo", "sped up", "nightcore", "slowed", "extended", "radio edit",
        )

    private fun hasVersionMismatch(wanted: String, candidate: String): Boolean {
        val target = " ${normalize(wanted)} "
        val option = " ${normalize(candidate)} "
        return versionMarkers.any { marker ->
            val token = " ${normalize(marker)} "
            (token in target) != (token in option)
        }
    }

    private fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val distance = max(a.length, b.length) / 2 - 1
        if (distance < 0) return 0.0
        val aMatches = BooleanArray(a.length)
        val bMatches = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            for (j in max(0, i - distance) until min(i + distance + 1, b.length)) {
                if (bMatches[j] || a[i] != b[j]) continue
                aMatches[i] = true
                bMatches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var j = 0
        for (i in a.indices) {
            if (!aMatches[i]) continue
            while (!bMatches[j]) j++
            if (a[i] != b[j]) transpositions++
            j++
        }
        val jaro =
            (matches.toDouble() / a.length +
                matches.toDouble() / b.length +
                (matches - transpositions / 2.0) / matches) / 3.0
        val prefix = a.zip(b).takeWhile { (left, right) -> left == right }.size.coerceAtMost(4)
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }
}

object AudioSourceConfig {
    val DEFAULT_ORDER: List<AudioSourceType> =
        listOf(
            AudioSourceType.TIDAL,
            AudioSourceType.QOBUZ,
            AudioSourceType.QOBUZ_BACKUP,
            AudioSourceType.DEEZER,
            AudioSourceType.APPLE,
            AudioSourceType.JIOSAAVN,
            AudioSourceType.SOUNDCLOUD,
            AudioSourceType.SPOTIFY,
            AudioSourceType.YOUTUBE,
        )

    private val ALWAYS_ENABLED = setOf(AudioSourceType.YOUTUBE)

    private fun parseType(name: String): AudioSourceType? =
        runCatching { AudioSourceType.valueOf(name.trim().uppercase()) }.getOrNull()

    fun parseOrder(rawOrder: String?): List<AudioSourceType> {
        val stored =
            rawOrder
                ?.split(',')
                ?.mapNotNull { parseType(it) }
                ?.distinct()
                .orEmpty()
        if (stored.isEmpty()) return DEFAULT_ORDER

        val missing = DEFAULT_ORDER.filterNot { it in stored }
        if (missing.isEmpty()) return stored

        val merged = mutableListOf<AudioSourceType>()
        var inserted = false
        for (source in stored) {
            if (!inserted && source == AudioSourceType.YOUTUBE) {
                merged.addAll(missing)
                inserted = true
            }
            merged.add(source)
        }

        if (!inserted) merged.addAll(missing)
        return merged
    }

    fun isEnabled(
        source: AudioSourceType,
        enabledSet: Set<String>?,
        default: Boolean,
    ): Boolean {
        if (source in ALWAYS_ENABLED) return true
        val set = enabledSet ?: return default
        return set.any { parseType(it) == source }
    }

    fun resolutionChain(
        rawOrder: String?,
        enabledSet: Set<String>?,
        defaults: Map<AudioSourceType, Boolean>,
    ): List<AudioSourceType> =
        parseOrder(rawOrder).filter { source ->
            isEnabled(source, enabledSet, defaults[source] ?: false)
        }

    fun withSourceAdded(
        rawOrder: String?,
        source: AudioSourceType,
    ): String {
        val parsed = parseOrder(rawOrder)
        if (source in parsed) return parsed.joinToString(",") { it.name }
        val above = parsed.filterNot { it == AudioSourceType.YOUTUBE }
        return (above + source + AudioSourceType.YOUTUBE).joinToString(",") { it.name }
    }
}

object SongSourceOverride {
    fun parse(raw: String?): Map<String, AudioSourceType> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, AudioSourceType>()
        raw.split(';').forEach { entry ->
            val idx = entry.indexOf('=')
            if (idx <= 0) return@forEach
            val id = entry.substring(0, idx).trim()
            val source =
                runCatching { AudioSourceType.valueOf(entry.substring(idx + 1).trim().uppercase()) }
                    .getOrNull()
            if (id.isNotEmpty() && source != null) out[id] = source
        }
        return out
    }

    fun serialize(map: Map<String, AudioSourceType>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    fun get(
        raw: String?,
        songId: String,
    ): AudioSourceType? = parse(raw)[songId]

    fun withOverride(
        raw: String?,
        songId: String,
        source: AudioSourceType?,
    ): String {
        val map = LinkedHashMap(parse(raw))
        if (source == null) map.remove(songId) else map[songId] = source
        return serialize(map)
    }
}

object SongCanvasDisabled {
    fun parse(raw: String?): Set<String> =
        raw
            ?.split(';')
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.toSet()
            ?: emptySet()

    fun serialize(ids: Set<String>): String = ids.joinToString(";")

    fun isDisabled(
        raw: String?,
        songId: String,
    ): Boolean = songId in parse(raw)

    fun withDisabled(
        raw: String?,
        songId: String,
        disabled: Boolean,
    ): String {
        val ids = parse(raw).toMutableSet()
        if (disabled) ids.add(songId) else ids.remove(songId)
        return serialize(ids)
    }
}

object SongSourceQobuzTrackId {
    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.split(';').forEach { entry ->
            val idx = entry.indexOf('=')
            if (idx <= 0) return@forEach
            val id = entry.substring(0, idx).trim()
            val trackId = entry.substring(idx + 1).trim()
            if (id.isNotEmpty() && trackId.isNotEmpty()) out[id] = trackId
        }
        return out
    }

    fun serialize(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun get(
        raw: String?,
        songId: String,
    ): String? = parse(raw)[songId]

    fun withOverride(
        raw: String?,
        songId: String,
        trackId: String?,
    ): String {
        val map = LinkedHashMap(parse(raw))
        if (trackId == null) map.remove(songId) else map[songId] = trackId
        return serialize(map)
    }
}

object SongSourceTidalTrackId {
    fun parse(raw: String?): Map<String, String> = SongSourceQobuzTrackId.parse(raw)

    fun serialize(map: Map<String, String>): String = SongSourceQobuzTrackId.serialize(map)

    fun get(
        raw: String?,
        songId: String,
    ): String? = SongSourceQobuzTrackId.get(raw, songId)

    fun withOverride(
        raw: String?,
        songId: String,
        trackId: String?,
    ): String = SongSourceQobuzTrackId.withOverride(raw, songId, trackId)
}

object SongSourceQobuzBackupVideoId {
    fun parse(raw: String?): Map<String, String> = SongSourceQobuzTrackId.parse(raw)

    fun serialize(map: Map<String, String>): String = SongSourceQobuzTrackId.serialize(map)

    fun get(
        raw: String?,
        songId: String,
    ): String? = SongSourceQobuzTrackId.get(raw, songId)

    fun withOverride(
        raw: String?,
        songId: String,
        videoId: String?,
    ): String = SongSourceQobuzTrackId.withOverride(raw, songId, videoId)
}

object SongSourceDeezerTrackId {
    fun parse(raw: String?): Map<String, String> = SongSourceQobuzTrackId.parse(raw)

    fun serialize(map: Map<String, String>): String = SongSourceQobuzTrackId.serialize(map)

    fun get(
        raw: String?,
        songId: String,
    ): String? = SongSourceQobuzTrackId.get(raw, songId)

    fun withOverride(
        raw: String?,
        songId: String,
        trackId: String?,
    ): String = SongSourceQobuzTrackId.withOverride(raw, songId, trackId)
}
