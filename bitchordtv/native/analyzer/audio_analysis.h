/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard), whose native
 * analyzer this file is adapted from almost unchanged: the mix-out budget,
 * phrase detection and cue scoring below were tuned against real material,
 * and reimplementing them from a description would produce different numbers
 * that BitChord's transition policy is not calibrated for.
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

// Offline whole-track analysis: envelope, transition, tempo, key, spectral,
// and structure features, entirely DSP -- no ML model. `samples` is
// contiguous, non-interleaved mono Float32 PCM. Callers normally supply
// finite normalized amplitudes in [-1, 1], normally at 11,025 Hz, and a
// duration in seconds equal to samples.size() / sample_rate. The
// implementation guards empty/very-low-rate input, but the JNI bridge owns
// stricter validation; it does not scan every sample or reconcile
// inconsistent duration metadata.
//
// Calls borrow the input vector only until they return and produce results
// that own all vector/string storage. Analysis is reentrant because every
// mutable value is call-local. It intentionally allocates and performs O(n)
// work, so it belongs on a worker thread and must never run in a real-time
// audio callback.

#pragma once

#include <string>
#include <vector>

namespace bitchord::smart {

// Times are seconds, confidence/probability values are nominally in [0, 1],
// and ordered event vectors use playback order unless stated otherwise.
struct EnergyPoint {
  double time = 0;
  // Energy relative to the corresponding whole-track reference, capped at 1.5.
  // `energy_curve` uses RMS; spectral band curves use FFT-band energy.
  double energy = 0;
};

struct Phrase {
  double start = 0;
  double end = 0;
  std::string type;
  double confidence = 0;
};

struct TempoResult {
  double bpm = 0;
  // Seconds per beat; zero means that no defensible tempo was found.
  double beat_interval = 0;
  double first_beat = 0;
  double confidence = 0;
  std::vector<double> beats;
  std::vector<double> downbeats;
};

struct MixCuePoint {
  double time = 0;
  double score = 0;
  std::string type;
};

struct AnalysisResult {
  double duration = 0;
  double bpm = 0;
  double beat_interval = 0;
  double first_beat = 0;
  double beat_confidence = 0;
  std::vector<double> beats;
  std::vector<double> downbeats;
  std::vector<double> phrase_boundaries;
  std::vector<Phrase> phrases;
  std::string key;
  double key_confidence = 0;
  // Sum-normalized C through B pitch-class energy in chromatic order.
  std::vector<double> chroma;
  double audible_start_time = 0;
  double pickup_time = 0;
  double pickup_confidence = 0;
  double mix_in_time = 0;
  double mix_in_confidence = 0;
  double intro_end_time = 0;
  double outro_start_time = 0;
  double content_end_time = 0;
  double mix_out_time = 0;
  // RMS dBFS with a -0.691 offset, not gated/K-weighted integrated LUFS.
  double loudness_lufs = -70;
  double peak_dbfs = -70;
  double dynamic_range_db = 0;
  std::vector<EnergyPoint> energy_curve;
  std::vector<EnergyPoint> low_energy_curve;
  std::vector<EnergyPoint> mid_energy_curve;
  std::vector<EnergyPoint> high_energy_curve;
  // A DSP heuristic (spectral band ratios + flatness, see AnalyzeKeyAndTimbre
  // in audio_analysis.cpp), not a trained classifier's output. Good enough to
  // gate vocal-clash avoidance in the transition planner; a future ML pass
  // (open-unmix) can replace it without changing what reads it.
  std::vector<double> vocal_activity_mask;
  std::vector<MixCuePoint> mix_in_candidates;
  std::vector<MixCuePoint> mix_out_candidates;
  double vocal_probability = 0;
};

/**
 * Extracts envelope, transition, tempo, key, spectral, and structure
 * features. Invalid top-level input returns the default result rather than
 * throwing.
 */
AnalysisResult AnalyzeAudio(
  const std::vector<float>& samples,
  double sample_rate,
  double supplied_duration
);

/**
 * Estimates tempo from at most the first 180 seconds, then *tracks* the beat
 * grid through `duration` with a phase-locked loop over the onset envelope
 * rather than extrapolating a fixed interval. `audible_start` is used only to
 * align the first reported beat near meaningful content.
 *
 * `beats` is therefore not uniformly spaced, and `beat_interval` is the
 * median locked interval rather than a spacing every entry honours. Callers
 * that need a specific beat's position must read `beats`; callers that need
 * a tempo (a time-stretch ratio, say) want `bpm`.
 */
TempoResult AnalyzeTempo(
  const std::vector<float>& samples,
  double sample_rate,
  double duration,
  double audible_start
);

}  // namespace bitchord::smart
