/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard), whose native STFT
 * front end this file is adapted from almost unchanged: every constant here
 * is dictated by the open-unmix model's training data, not a preference, so
 * reimplementing it from a description would feed the network an input
 * distribution it has never seen.
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

// Linear-frequency STFT magnitude front end for the open-unmix vocal
// separation model.
//
// Every constant here is dictated by the trained network, not chosen: the
// model was trained on exactly this STFT (torch.stft with these settings,
// see openunmix/transforms.py's TorchSTFT, MIT-licensed, Inria/SigSep), and
// a front end that differs in window convention or normalization feeds it
// something it has never seen -- the same lesson the Beat This mel front end
// already encodes, see mel_spectrogram.h.
//
// `channels` is planar (non-interleaved) stereo Float32 PCM that must
// already be at kVocalSpectrogramSampleRate; the caller owns resampling and
// mono duplication. Calls borrow the input only until they return, own all
// returned storage, and are reentrant. The work is O(n log n) and allocates,
// so it belongs on a worker thread.

#pragma once

#include <cstddef>
#include <vector>

namespace bitchord::smart {

inline constexpr double kVocalSpectrogramSampleRate = 44100;
inline constexpr size_t kVocalSpectrogramChannels = 2;
inline constexpr size_t kVocalSpectrogramFft = 4096;
inline constexpr size_t kVocalSpectrogramBins = kVocalSpectrogramFft / 2 + 1;
inline constexpr size_t kVocalSpectrogramHop = 1024;

struct VocalSpectrogram {
  // Row-major [channel][bin][frame], flattened: channel c, bin b, frame f is
  // at index (c * kVocalSpectrogramBins + b) * frames + f. This is bin-major
  // rather than the more natural frame-major order an STFT computes one frame
  // at a time in, specifically so it matches the ONNX model's expected tensor
  // shape [1, 2, kVocalSpectrogramBins, frames] exactly -- the caller hands
  // this straight to an inference call with no transpose.
  std::vector<float> values;
  size_t frames = 0;
};

/**
 * Computes the linear-magnitude STFT the vocal-separation model expects.
 *
 * Returns an empty result -- not an error -- when the sample rate is not
 * kVocalSpectrogramSampleRate, the channel count is not
 * kVocalSpectrogramChannels, or the input is shorter than one padded frame.
 * Callers treat that as "no mask available" and skip the vocal duck.
 */
VocalSpectrogram ComputeVocalSpectrogram(
  const std::vector<std::vector<float>>& channels,
  double sample_rate
);

}  // namespace bitchord::smart
