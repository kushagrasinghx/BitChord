/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard), whose native mel
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

#include "mel_spectrogram.h"

#include <algorithm>
#include <cmath>
#include <complex>
#include <vector>

namespace bitchord::smart {
namespace {

constexpr double kPi = 3.14159265358979323846;

constexpr double kMinHz = 30.0;
constexpr double kMaxHz = 11000.0;
// The model was trained on log1p(1000 * magnitude); the multiplier is what
// spreads quiet detail across the network's useful input range, so it is
// part of the model contract rather than a taste setting.
constexpr double kLogMultiplier = 1000.0;
constexpr double kAmplitudeFloor = 1e-10;

// Unnormalized in-place radix-2 FFT. kBeatSpectrogramFft is a power of two,
// so no generic padding is performed.
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

// Slaney mel scale: linear below 1 kHz, logarithmic above. This is the
// variant torchaudio uses by default, and it is not interchangeable with
// the HTK formula -- picking the wrong one silently shifts every filter.
double HzToMel(double hz) {
  constexpr double f_sp = 200.0 / 3.0;
  constexpr double min_log_hz = 1000.0;
  const double min_log_mel = min_log_hz / f_sp;
  const double logstep = std::log(6.4) / 27.0;
  if (hz >= min_log_hz) return min_log_mel + std::log(hz / min_log_hz) / logstep;
  return hz / f_sp;
}

double MelToHz(double mel) {
  constexpr double f_sp = 200.0 / 3.0;
  constexpr double min_log_hz = 1000.0;
  const double min_log_mel = min_log_hz / f_sp;
  const double logstep = std::log(6.4) / 27.0;
  if (mel >= min_log_mel) return min_log_hz * std::exp(logstep * (mel - min_log_mel));
  return f_sp * mel;
}

// One triangular mel filter, stored as the run of FFT bins it actually
// covers.
//
// Sparse rather than a dense [bins][mels] matrix on purpose: a triangle
// spans a handful of bins, so the dense form is 98% zeros and costs
// 513 x 128 multiplies per frame -- around 800 million for a four-minute
// track, several seconds of pure zero-multiplying. Storing the run makes
// the same work about 1,000 multiplies per frame.
struct MelFilter {
  size_t first_bin = 0;
  std::vector<double> weights;
};

// Triangular filters, deliberately *not* area-normalized: torchaudio's
// `norm=None` default leaves the triangles at unit peak, and the model was
// trained on that. Slaney-normalizing here would scale every band by its
// own width and quietly change the input distribution.
std::vector<MelFilter> MelFilterbank(double sample_rate) {
  const size_t bins = kBeatSpectrogramFft / 2 + 1;
  const double mel_min = HzToMel(kMinHz);
  const double mel_max = HzToMel(kMaxHz);

  std::vector<double> edges(kBeatSpectrogramMels + 2);
  for (size_t index = 0; index < edges.size(); ++index) {
    edges[index] = MelToHz(
      mel_min + (mel_max - mel_min) * static_cast<double>(index) / (kBeatSpectrogramMels + 1)
    );
  }

  std::vector<MelFilter> filters(kBeatSpectrogramMels);
  for (size_t mel = 0; mel < kBeatSpectrogramMels; ++mel) {
    const double left = edges[mel];
    const double centre = edges[mel + 1];
    const double right = edges[mel + 2];
    // The triangle is non-zero strictly between its outer edges.
    const auto to_bin = [&](double hz) {
      return hz * kBeatSpectrogramFft / sample_rate;
    };
    const size_t first = static_cast<size_t>(std::max(0.0, std::floor(to_bin(left))));
    const size_t last = std::min(bins - 1, static_cast<size_t>(std::ceil(to_bin(right))));
    if (last < first) continue;

    MelFilter filter;
    filter.first_bin = first;
    filter.weights.reserve(last - first + 1);
    for (size_t bin = first; bin <= last; ++bin) {
      const double hz = static_cast<double>(bin) * sample_rate / kBeatSpectrogramFft;
      const double rising = centre > left ? (hz - left) / (centre - left) : 0.0;
      const double falling = right > centre ? (right - hz) / (right - centre) : 0.0;
      filter.weights.push_back(std::max(0.0, std::min(rising, falling)));
    }
    filters[mel] = std::move(filter);
  }
  return filters;
}

// Periodic Hann, matching torch.hann_window(periodic=True): the divisor is
// the window length, not length - 1. The symmetric variant used elsewhere in
// this addon would be a different window and a different spectrum.
std::vector<double> HannWindow(size_t size) {
  std::vector<double> window(size);
  for (size_t index = 0; index < size; ++index) {
    window[index] = 0.5 - 0.5 * std::cos(2.0 * kPi * static_cast<double>(index) / size);
  }
  return window;
}

}  // namespace

BeatSpectrogram ComputeBeatSpectrogram(
  const std::vector<float>& samples,
  double sample_rate
) {
  BeatSpectrogram result;
  if (std::abs(sample_rate - kBeatSpectrogramSampleRate) > 1.0) return result;

  // torchaudio's stft(center=True, pad_mode="reflect") centres frame f on
  // sample f * hop, which is what puts a predicted beat at f / 50 seconds
  // rather than half a window later.
  const size_t pad = kBeatSpectrogramFft / 2;
  if (samples.size() <= pad + 1) return result;

  std::vector<float> padded;
  padded.reserve(samples.size() + 2 * pad);
  for (size_t index = pad; index >= 1; --index) padded.push_back(samples[index]);
  padded.insert(padded.end(), samples.begin(), samples.end());
  for (size_t index = 1; index <= pad; ++index) {
    padded.push_back(samples[samples.size() - 1 - index]);
  }

  if (padded.size() < kBeatSpectrogramFft) return result;
  const size_t frames = (padded.size() - kBeatSpectrogramFft) / kBeatSpectrogramHop + 1;

  const auto window = HannWindow(kBeatSpectrogramFft);
  const auto filters = MelFilterbank(sample_rate);
  const size_t bins = kBeatSpectrogramFft / 2 + 1;
  // torchaudio's `normalized=True` divides the transform by the square root
  // of the window length.
  const double normalization = std::sqrt(static_cast<double>(kBeatSpectrogramFft));

  result.frames = frames;
  result.values.assign(frames * kBeatSpectrogramMels, 0.0f);

  std::vector<std::complex<double>> spectrum(kBeatSpectrogramFft);
  std::vector<double> magnitude(bins);

  for (size_t frame = 0; frame < frames; ++frame) {
    const size_t start = frame * kBeatSpectrogramHop;
    for (size_t index = 0; index < kBeatSpectrogramFft; ++index) {
      spectrum[index] = std::complex<double>(padded[start + index] * window[index], 0.0);
    }
    Fft(spectrum);
    for (size_t bin = 0; bin < bins; ++bin) {
      magnitude[bin] = std::abs(spectrum[bin]) / normalization;
    }
    float* row = result.values.data() + frame * kBeatSpectrogramMels;
    for (size_t mel = 0; mel < kBeatSpectrogramMels; ++mel) {
      const auto& filter = filters[mel];
      double energy = 0;
      for (size_t offset = 0; offset < filter.weights.size(); ++offset) {
        energy += magnitude[filter.first_bin + offset] * filter.weights[offset];
      }
      row[mel] = static_cast<float>(
        std::log1p(kLogMultiplier * std::max(energy, kAmplitudeFloor))
      );
    }
  }
  return result;
}

}  // namespace bitchord::smart
