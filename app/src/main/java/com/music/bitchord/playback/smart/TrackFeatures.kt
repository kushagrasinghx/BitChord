/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-track envelope, structure and key analysis, from the native DSP
 * analyzer (`native/analyzer/audio_analysis.cpp`).
 *
 * This answers "where does the music actually end, where can a transition
 * enter and leave, how loud is it there, and is anyone singing" — the
 * transition policy needs a beat grid (also produced here, from
 * autocorrelation) to know how to mix, and these features to know *where*,
 * and through the energy curve, whether an interior mix-out anchor would
 * skip silence or skip a minute of music.
 */
object TrackFeatures {

    /** True when the native library loaded. Analysis is optional, so this is a fact, not a fault. */
    val available: Boolean = runCatching { System.loadLibrary("bitchord_analysis") }.isSuccess

    /**
     * The rate the analyzer's window and hop constants assume, deliberately
     * low, because envelope and structure work needs time resolution rather
     * than bandwidth, and an eighth of the samples is an eighth of the work.
     */
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 11_025.0 }

    /**
     * Analyses [samples], which must be mono float PCM at [sampleRate].
     *
     * Returns null when the native library is missing or the analyzer
     * declined the input. Callers treat that as "no evidence", which the
     * policy already degrades on.
     */
    fun analyze(samples: FloatArray, durationSeconds: Double): Features? {
        if (!available || samples.isEmpty()) return null
        val json = runCatching { nativeAnalyze(samples, sampleRate, durationSeconds) }
            .onFailure { Log.w(TAG, "Native analysis failed", it) }
            .getOrNull() ?: return null
        return runCatching { parse(JSONObject(json)) }
            .onFailure { Log.w(TAG, "Could not parse analysis output", it) }
            .getOrNull()
    }

    /**
     * Converts mono float PCM from [inputRate] to [sampleRate] (or any other
     * target), with an anti-aliasing windowed-sinc filter — see
     * `native/analyzer/resampler.cpp`.
     *
     * Returns the input unchanged when the rates already match, and null when
     * the native library is missing or the rates are unusable.
     */
    fun resample(samples: FloatArray, inputRate: Double, outputRate: Double = sampleRate): FloatArray? {
        if (!available || samples.isEmpty() || inputRate <= 0 || outputRate <= 0) return null
        return nativeResample(samples, inputRate, outputRate).takeIf { it.isNotEmpty() }
    }

    /** The subset of the analyzer's output the transition policy reads. */
    data class Features(
        val duration: Double,
        val bpm: Double,
        val beatInterval: Double,
        val firstBeat: Double,
        val beatConfidence: Double,
        val key: String,
        val keyConfidence: Double,
        val audibleStartTime: Double,
        val pickupTime: Double,
        val introEndTime: Double,
        val outroStartTime: Double,
        val contentEndTime: Double,
        val mixInTime: Double,
        val mixOutTime: Double,
        val vocalProbability: Double,
        val downbeats: List<Double>,
        val phraseBoundaries: List<Double>,
        val vocalActivityMask: List<Double>,
        val energyCurve: List<EnergySample>,
        val lowEnergyCurve: List<EnergySample>,
        val mixInCandidates: List<MixCandidate>,
        val mixOutCandidates: List<MixCandidate>,
    )

    fun parse(root: JSONObject): Features = Features(
        duration = root.optDouble("duration", 0.0).orZero(),
        bpm = root.optDouble("bpm", 0.0).orZero(),
        beatInterval = root.optDouble("beatInterval", 0.0).orZero(),
        firstBeat = root.optDouble("firstBeat", 0.0).orZero(),
        beatConfidence = root.optDouble("beatConfidence", 0.0).orZero(),
        key = root.optString("key", ""),
        keyConfidence = root.optDouble("keyConfidence", 0.0).orZero(),
        audibleStartTime = root.optDouble("audibleStartTime", 0.0).orZero(),
        pickupTime = root.optDouble("pickupTime", 0.0).orZero(),
        introEndTime = root.optDouble("introEndTime", 0.0).orZero(),
        outroStartTime = root.optDouble("outroStartTime", 0.0).orZero(),
        contentEndTime = root.optDouble("contentEndTime", 0.0).orZero(),
        mixInTime = root.optDouble("mixInTime", 0.0).orZero(),
        mixOutTime = root.optDouble("mixOutTime", 0.0).orZero(),
        vocalProbability = root.optDouble("vocalProbability", 0.0).orZero(),
        downbeats = root.doubles("downbeats"),
        phraseBoundaries = root.doubles("phraseBoundaries"),
        vocalActivityMask = root.doubles("vocalActivityMask"),
        energyCurve = root.energyCurve("energyCurve"),
        lowEnergyCurve = root.energyCurve("lowEnergyCurve"),
        mixInCandidates = root.cuePoints("mixInCandidates"),
        mixOutCandidates = root.cuePoints("mixOutCandidates"),
    )

    private fun JSONObject.doubles(name: String): List<Double> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optDouble(index).takeIf { it.isFinite() }?.let(::add)
            }
        }
    }

    private fun JSONObject.energyCurve(name: String): List<EnergySample> {
        val array: JSONArray = optJSONArray(name) ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val point = array.optJSONObject(index) ?: continue
                val time = point.optDouble("t", Double.NaN)
                val energy = point.optDouble("e", Double.NaN)
                if (time.isFinite() && energy.isFinite()) add(EnergySample(time, energy))
            }
        }
    }

    private fun JSONObject.cuePoints(name: String): List<MixCandidate> {
        val array: JSONArray = optJSONArray(name) ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val point = array.optJSONObject(index) ?: continue
                val time = point.optDouble("t", Double.NaN)
                if (!time.isFinite()) continue
                add(
                    MixCandidate(
                        time = time,
                        score = point.optDouble("s", 0.0).orZero(),
                        type = point.optString("y", ""),
                    ),
                )
            }
        }
    }

    private const val TAG = "BitChordTrackFeatures"

    @JvmStatic private external fun nativeAnalyze(
        samples: FloatArray,
        sampleRate: Double,
        duration: Double,
    ): String

    @JvmStatic private external fun nativeSampleRate(): Double

    @JvmStatic private external fun nativeResample(
        samples: FloatArray,
        inputRate: Double,
        outputRate: Double,
    ): FloatArray
}
