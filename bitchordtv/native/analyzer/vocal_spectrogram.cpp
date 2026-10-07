/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard), whose native STFT
 * front end this file is adapted from almost unchanged.
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

#include "vocal_spectrogram.h"

#include <cmath>
#include <complex>
#include <vector>

namespace bitchord::smart {
namespace {

constexpr double kPi = 3.14159265358979323846;

// Unnormalized in-place radix-2 FFT. kVocalSpectrogramFft is a power of two,
// so no generic padding is performed. Private to this file, matching the
// existing convention (tempo_analysis.cpp and mel_spectrogram.cpp each carry
// their own copy rather than sharing one across translation units).
void Fft(std::vector<std::complex<double>>& values) {
  const size_t size = values.size();
  for (size_t index = 1, swapped = 0; index < size; ++index) {
    size_t bit = size >> 1;
    for (; swapped & bit; bit >>= 1) swapped ^= bit;
    swapped ^= bit;
    if (index < swapped) std::swap(values[index], values[swapped]);
  }
  for (size_t length = 2; length <= size; length <<= 1) {
    const std::complex<double> root = std::polar(1.0, -2.0 * kPi / length);
    for (size_t start = 0; start < size; start += length) {
      std::complex<double> weight(1, 0);
      for (size_t offset = 0; offset < length / 2; ++offset) {
        const auto even = values[start + offset];
        const auto odd = values[start + offset + length / 2] * weight;
        values[start + offset] = even + odd;
        values[start + offset + length / 2] = even - odd;
        weight *= root;
      }
    }
  }
}

// Periodic Hann, matching torch.hann_window(periodic=True) -- the divisor is
// the window length, not length - 1. transforms.py's TorchSTFT uses this via
// `nn.Parameter(torch.hann_window(n_fft))`.
std::vector<double> HannWindow(size_t size) {
  std::vector<double> window(size);
  for (size_t index = 0; index < size; ++index) {
    window[index] = 0.5 - 0.5 * std::cos(2.0 * kPi * static_cast<double>(index) / size);
  }
  return window;
}

}  // namespace

VocalSpectrogram ComputeVocalSpectrogram(
  const std::vector<std::vector<float>>& channels,
  double sample_rate
) {
  VocalSpectrogram result;
  if (std::abs(sample_rate - kVocalSpectrogramSampleRate) > 1.0) return result;
  if (channels.size() != kVocalSpectrogramChannels) return result;
  const size_t input_length = channels.front().size();
  if (channels[1].size() != input_length) return result;

  // torch.stft(center=True, pad_mode="reflect") centres frame f on sample
  // f * hop, matching the mel front end's identical padding.
  const size_t pad = kVocalSpectrogramFft / 2;
  if (input_length <= pad + 1) return result;

  const auto window = HannWindow(kVocalSpectrogramFft);
  std::vector<std::vector<float>> padded(kVocalSpectrogramChannels);
  for (size_t channel = 0; channel < kVocalSpectrogramChannels; ++channel) {
    auto& out = padded[channel];
    const auto& in = channels[channel];
    out.reserve(input_length + 2 * pad);
    for (size_t index = pad; index >= 1; --index) out.push_back(in[index]);
    out.insert(out.end(), in.begin(), in.end());
    for (size_t index = 1; index <= pad; ++index) out.push_back(in[input_length - 1 - index]);
  }

  const size_t padded_length = padded.front().size();
  if (padded_length < kVocalSpectrogramFft) return result;
  const size_t frames = (padded_length - kVocalSpectrogramFft) / kVocalSpectrogramHop + 1;

  result.frames = frames;
  result.values.assign(kVocalSpectrogramChannels * frames * kVocalSpectrogramBins, 0.0f);

  std::vector<std::complex<double>> spectrum(kVocalSpectrogramFft);
  for (size_t channel = 0; channel < kVocalSpectrogramChannels; ++channel) {
    const auto& source = padded[channel];
    for (size_t frame = 0; frame < frames; ++frame) {
      const size_t start = frame * kVocalSpectrogramHop;
      for (size_t index = 0; index < kVocalSpectrogramFft; ++index) {
        spectrum[index] = std::complex<double>(source[start + index] * window[index], 0.0);
      }
      Fft(spectrum);

      // torch.stft is called with normalized=False: the raw DFT magnitude,
      // with no additional 1/sqrt(win_length) scaling. That is the one
      // difference from the Beat This mel front end's `normalized=True`
      // convention, and getting it backwards would feed the model a
      // spectrogram scaled by sqrt(4096) ~= 64x too loud.
      //
      // Written bin-major (stride `frames` apart) rather than contiguously,
      // to land directly in the [channel][bin][frame] layout the ONNX tensor
      // needs; the FFT still produces one whole frame's bins at a time, only
      // where each one is stored differs.
      const size_t channel_base = channel * kVocalSpectrogramBins * frames;
      for (size_t bin = 0; bin < kVocalSpectrogramBins; ++bin) {
        result.values[channel_base + bin * frames + frame] =
          static_cast<float>(std::abs(spectrum[bin]));
      }
    }
  }
  return result;
}

}  // namespace bitchord::smart
