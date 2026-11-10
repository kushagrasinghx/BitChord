
package moe.rukamori.archivetune.audiosource

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal object TrackMatching {
    const val MIN_MATCH_SCORE = 60

    private val STOP_WORDS = setOf("the", "a", "an", "of", "and", "feat", "ft", "featuring", "with")

    private val VERSION_TOKENS =
        setOf("remix", "acoustic", "live", "instrumental", "demo", "edit", "mix", "version")

    private const val DURATION_TOLERANCE_MS = 45_000L

    fun score(
        wantedTitle: String,
        wantedArtists: List<String>,
        candidateTitle: String,
        candidateArtist: String,
        wantedDurationMs: Long?,
        candidateDurationMs: Long?,
    ): Int {
        if (wantedTitle.isBlank() || candidateTitle.isBlank()) return Int.MIN_VALUE
        if (hasVersionMismatch(" $wantedTitle ", " $candidateTitle ")) return Int.MIN_VALUE
        val titleOverlap = tokenOverlap(significantTokens(wantedTitle), significantTokens(candidateTitle))
        if (titleOverlap < 0.5) return Int.MIN_VALUE
        var score = (titleOverlap * 100).toInt()
        if (wantedArtists.isNotEmpty() && candidateArtist.isNotBlank()) {
            val artistOverlap =
                wantedArtists.maxOf { tokenOverlap(significantTokens(it), significantTokens(candidateArtist)) }
            score += (artistOverlap * 60).toInt()
        }
        if (wantedDurationMs != null && candidateDurationMs != null) {
            if (durationMatches(wantedDurationMs, candidateDurationMs)) score += 30 else score -= 40
        }
        return score
    }

    data class Target(
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    )

    data class Candidate(
        val id: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationMs: Long?,
    )

    fun best(
        target: Target,
        candidates: List<Candidate>,
    ): Candidate? =
        candidates
            .asSequence()
            .map { candidate ->
                candidate to
                    score(
                        wantedTitle = normalizeTitle(target.title),
                        wantedArtists = target.artists.map { normalize(it) },
                        candidateTitle = normalizeTitle(candidate.title),
                        candidateArtist = normalize(candidate.artists.firstOrNull()),
                        wantedDurationMs = target.durationMs,
                        candidateDurationMs = candidate.durationMs,
                    )
            }.filter { (_, score) -> score >= MIN_MATCH_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first

    private fun significantTokens(value: String): Set<String> =
        value
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()

    private fun tokenOverlap(
        wanted: Set<String>,
        candidate: Set<String>,
    ): Double {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0.0
        val shared = wanted.intersect(candidate).size

        return shared.toDouble() / wanted.size.coerceAtLeast(candidate.size).toDouble()
    }

    fun durationMatches(
        wantedDurationMs: Long?,
        candidateDurationMs: Long?,
    ): Boolean {
        if (wantedDurationMs == null || candidateDurationMs == null) return true
        return abs(wantedDurationMs - candidateDurationMs) <= DURATION_TOLERANCE_MS
    }

    private fun hasVersionMismatch(
        wanted: String,
        candidate: String,
    ): Boolean = VERSION_TOKENS.any { token -> wanted.contains(" $token ") != candidate.contains(" $token ") }

    fun normalize(value: String?): String =
        value
            ?.lowercase(Locale.US)
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(Regex("\\p{Mn}+"), "")
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.trim()
            .orEmpty()

    fun normalizeTitle(value: String): String =
        normalize(value)
            .replace(Regex("""\b(feat|ft|featuring)\b.*$"""), "")
            .replace(Regex("""\b(explicit|clean|remaster|remastered|version|audio|official)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun searchTitle(value: String): String =
        value
            .trim()
            .replace(Regex("""\s*[\[(]\s*(feat\.?|ft\.?|featuring)\b.*?[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(
                Regex("""\s*-\s*(explicit|clean|remaster(?:ed)?|audio|official)\b.*$""", RegexOption.IGNORE_CASE),
                "",
            ).replace(Regex("\\s+"), " ")
            .trim()

    fun searchArtist(value: String): String =
        value
            .trim()
            .substringBefore(',')
            .replace(Regex("\\s+"), " ")
            .trim()
}
