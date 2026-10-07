/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard), whose native mel
 * front end this file is adapted from almost unchanged: every constant here
 * is dictated by the Beat This! model's training data, not a preference, so
 * reimplementing it from a description would silently retrain the input
 * distribution the network has never seen.
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

// Log-mel spectrogram front end for the Beat This! beat/downbeat model.
//
// Every constant here is dictated by the trained network, not chosen: the
// model was trained on torchaudio's MelSpectrogram with these exact
// settings, and a front end that differs even in window convention or
// normalization feeds it something it has never seen. They mirror
// `LogMelSpect` in the upstream project (CPJKU/beat_this, MIT) and its C++
// port (mosynthkey/beat_this_cpp, MIT), which is the reference this was
// written against.
//
// `samples` is contiguous mono Float32 PCM that must already be at
// kBeatSpectrogramSampleRate; the caller owns resampling. Calls borrow the
// input only until they return, own all returned storage, and are
// reentrant. The work is O(n log n) and allocates, so it belongs on a
// worker thread.

#pragma once

#include <cstddef>
#include <vector>

namespace bitchord::smart {

// The model's input rate. Audio at any other rate is refused rather than
// resampled here, because resampling belongs upstream where the decoded
// buffer still exists at full bandwidth.
inline constexpr double kBeatSpectrogramSampleRate = 22050;
inline constexpr size_t kBeatSpectrogramMels = 128;
inline constexpr size_t kBeatSpectrogramFft = 1024;
// 20 ms exactly, so the model's frame rate is 50 per second.
inline constexpr size_t kBeatSpectrogramHop = 441;

struct BeatSpectrogram {
  // Row-major [frames][kBeatSpectrogramMels], flattened: frame f band b is at
  // index f * kBeatSpectrogramMels + b. Flat because the only consumer hands
  // it straight to an ONNX tensor of that shape.
  std::vector<float> values;
  size_t frames = 0;
};

/**
 * Computes the log-mel spectrogram the beat model expects.
 *
 * Returns an empty result -- not an error -- when the sample rate is not
 * kBeatSpectrogramSampleRate or the input is shorter than one padded frame.
 * Callers treat that as "no model prediction available" and fall back to
 * the autocorrelation grid.
 */
BeatSpectrogram ComputeBeatSpectrogram(
  const std::vector<float>& samples,
  double sample_rate
);

}  // namespace bitchord::smart
