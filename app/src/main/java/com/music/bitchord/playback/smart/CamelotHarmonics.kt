package com.music.bitchord.playback.smart

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Camelot Wheel harmonic mapping and key-shift calculation for DJ AutoMix.
 *
 * Provides Camelot notation (1A-12A for minor, 1B-12B for major),
 * harmonic relationship labeling, and dynamic pitch shift factors.
 */
object CamelotHarmonics {

    private val KEY_TO_PITCH = mapOf(
        "C" to 0, "C♯" to 1, "C#" to 1, "D♭" to 1, "Db" to 1,
        "D" to 2, "D♯" to 3, "D#" to 3, "E♭" to 3, "Eb" to 3,
        "E" to 4,
        "F" to 5, "F♯" to 6, "F#" to 6, "G♭" to 6, "Gb" to 6,
        "G" to 7, "G♯" to 8, "G#" to 8, "A♭" to 8, "Ab" to 8,
        "A" to 9, "A♯" to 10, "A#" to 10, "B♭" to 10, "Bb" to 10,
        "B" to 11,
    )

    // Camelot Major (B) keys: 0->8B, 1->3B, 2->10B, 3->5B, 4->12B, 5->7B, 6->2B, 7->9B, 8->4B, 9->11B, 10->6B, 11->1B
    private val MAJOR_CAMELOT = mapOf(
        0 to "8B", 1 to "3B", 2 to "10B", 3 to "5B", 4 to "12B", 5 to "7B",
        6 to "2B", 7 to "9B", 8 to "4B", 9 to "11B", 10 to "6B", 11 to "1B",
    )

    // Camelot Minor (A) keys: 0->5A, 1->12A, 2->7A, 3->2A, 4->9A, 5->4A, 6->11A, 7->6A, 8->1A, 9->8A, 10->3A, 11->10A
    private val MINOR_CAMELOT = mapOf(
        0 to "5A", 1 to "12A", 2 to "7A", 3 to "2A", 4 to "9A", 5 to "4A",
        6 to "11A", 7 to "6A", 8 to "1A", 9 to "8A", 10 to "3A", 11 to "10A",
    )

    data class ParsedKey(val root: String, val pitchClass: Int, val isMinor: Boolean)

    fun parseKey(key: String): ParsedKey? {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(Regex("\\s+"))
        val rootPart = parts.firstOrNull() ?: return null
        val pitch = KEY_TO_PITCH[rootPart] ?: return null
        val isMinor = parts.drop(1).any {
            val p = it.lowercase()
            p == "m" || p == "min" || p == "minor" || p == "moll"
        } || (parts.size == 1 && trimmed.endsWith("m") && !trimmed.endsWith("Major", ignoreCase = true))
        return ParsedKey(rootPart, pitch, isMinor)
    }

    /** Returns the Camelot notation string (e.g. "8A", "9B") or null. */
    fun toCamelot(key: String): String? {
        val parsed = parseKey(key) ?: return null
        return if (parsed.isMinor) MINOR_CAMELOT[parsed.pitchClass] else MAJOR_CAMELOT[parsed.pitchClass]
    }

    /**
     * Calculates the harmonic relationship label between two keys.
     */
    fun describeHarmonicMatch(outgoingKey: String, incomingKey: String): String {
        val k1 = parseKey(outgoingKey) ?: return ""
        val k2 = parseKey(incomingKey) ?: return ""
        val c1 = if (k1.isMinor) MINOR_CAMELOT[k1.pitchClass] ?: "" else MAJOR_CAMELOT[k1.pitchClass] ?: ""
        val c2 = if (k2.isMinor) MINOR_CAMELOT[k2.pitchClass] ?: "" else MAJOR_CAMELOT[k2.pitchClass] ?: ""

        if (k1.pitchClass == k2.pitchClass && k1.isMinor == k2.isMinor) {
            return "$c1 ➔ $c2 · Tono Idéntico"
        }
        if (k1.isMinor != k2.isMinor) {
            val dist = min((k1.pitchClass - k2.pitchClass + 12) % 12, (k2.pitchClass - k1.pitchClass + 12) % 12)
            if (dist == 3) return "$c1 ➔ $c2 · Relativa Armónica"
            if (dist == 0) return "$c1 ➔ $c2 · Tonalidad Paralela"
        }
        val p1 = c1.dropLast(1).toIntOrNull() ?: 0
        val p2 = c2.dropLast(1).toIntOrNull() ?: 0
        val camelotDiff = (p2 - p1 + 12) % 12
        if (camelotDiff == 1) return "$c1 ➔ $c2 · Quinta (+1 Energía)"
        if (camelotDiff == 11) return "$c1 ➔ $c2 · Cuarta (-1 Relajación)"
        if (camelotDiff == 2) return "$c1 ➔ $c2 · Subida de Energía (+2)"

        val semitones = semitonesDiff(k1.pitchClass, k2.pitchClass)
        if (abs(semitones) <= 2) {
            return "$c1 ➔ $c2 · Key-Shift (${if (semitones > 0) "+$semitones" else "$semitones"}st)"
        }
        return "$c1 ➔ $c2"
    }

    private fun semitonesDiff(p1: Int, p2: Int): Int {
        var diff = (p1 - p2) % 12
        if (diff > 6) diff -= 12
        if (diff < -6) diff += 12
        return diff
    }

    /**
     * Calculates the required pitch shift in semitones and pitch multiplier
     * so that the incoming track harmonically locks with the outgoing track.
     */
    fun calculateKeyShift(outgoingKey: String, incomingKey: String): Pair<Int, Double> {
        val k1 = parseKey(outgoingKey) ?: return 0 to 1.0
        val k2 = parseKey(incomingKey) ?: return 0 to 1.0

        // If same mode and within +/- 2 semitones, shift incoming to match outgoing key root
        if (k1.isMinor == k2.isMinor) {
            val diff = semitonesDiff(k1.pitchClass, k2.pitchClass)
            if (abs(diff) in 1..2) {
                val factor = 2.0.pow(diff / 12.0)
                return diff to factor
            }
        }
        return 0 to 1.0
    }
}
