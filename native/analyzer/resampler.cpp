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

#include "resampler.h"

#include <algorithm>
#include <cmath>

namespace bitchord::smart {
namespace {

constexpr double kPi = 3.14159265358979323846;

// Kernel table entries per unit of input-sample offset. At 512 the spacing
// between entries is ~0.002 of a sample, and linear interpolation across that
// is orders of magnitude below the quantization already in the audio.
constexpr double kKernelResolution = 512.0;

double Sinc(double x) {
  if (std::abs(x) < 1e-12) return 1.0;
  const double scaled = kPi * x;
  return std::sin(scaled) / scaled;
}

// Blackman rather than Hann: the extra term buys roughly 20 dB of stopband
// attenuation for one more cosine per tap, and the stopband is the only reason
// this filter exists.
double Blackman(double position) {
  // `position` runs 0..1 across the whole window.
  return 0.42 - 0.5 * std::cos(2.0 * kPi * position) +
         0.08 * std::cos(4.0 * kPi * position);
}

}  // namespace

std::vector<float> Resample(
  const std::vector<float>& input,
  double input_rate,
  double output_rate
) {
  if (input.empty() || input_rate <= 0.0 || output_rate <= 0.0) return {};
  if (std::abs(input_rate - output_rate) < 1e-6) return input;

  const double ratio = output_rate / input_rate;
  // Cutoff in cycles per *input* sample. When downsampling the limit is the
  // output Nyquist, which is what removes the content that would otherwise
  // alias; when upsampling there is nothing above the input Nyquist to remove.
  const double cutoff = 0.5 * std::min(1.0, ratio);
  const double half_width = static_cast<double>(kResamplerZeroCrossings) / (2.0 * cutoff);

  const size_t output_count =
    static_cast<size_t>(std::floor(static_cast<double>(input.size()) * ratio));
  if (output_count == 0) return {};

  // The kernel is precomputed rather than evaluated per tap. Evaluating it
  // inline costs a sin and two cos for every tap, and at ~128 taps per output
  // sample that is a quarter of a billion transcendental calls for a thirty
  // second window -- absorbed on a desktop, emphatically not on a phone. The
  // kernel is symmetric about the centre, so a table over |offset| covers it,
  // and linear interpolation between entries is far below the error already
  // present in the audio.
  const size_t table_size =
    static_cast<size_t>(std::ceil(half_width * kKernelResolution)) + 2;
  std::vector<double> kernel(table_size);
  for (size_t index = 0; index < table_size; ++index) {
    const double offset = static_cast<double>(index) / kKernelResolution;
    const double window = Blackman((offset + half_width) / (2.0 * half_width));
    kernel[index] = 2.0 * cutoff * Sinc(2.0 * cutoff * offset) * window;
  }

  std::vector<float> output(output_count, 0.0f);
  const auto last = static_cast<long>(input.size()) - 1;

  for (size_t index = 0; index < output_count; ++index) {
    // Where this output sample sits on the input timeline.
    const double centre = static_cast<double>(index) / ratio;
    const long first_tap = static_cast<long>(std::ceil(centre - half_width));
    const long last_tap = static_cast<long>(std::floor(centre + half_width));

    double sum = 0.0;
    double weight_sum = 0.0;
    for (long tap = first_tap; tap <= last_tap; ++tap) {
      const double offset = std::abs(centre - static_cast<double>(tap));
      const double scaled = offset * kKernelResolution;
      const auto slot = static_cast<size_t>(scaled);
      if (slot + 1 >= table_size) continue;
      const double fraction = scaled - static_cast<double>(slot);
      const double weight =
        kernel[slot] + fraction * (kernel[slot + 1] - kernel[slot]);

      // Clamping rather than zero-padding at the edges: zeros would read as a
      // hard cut and ring, which at the start of a track is exactly where the
      // beat tracker is looking for the first onset.
      const long clamped = std::min(std::max(tap, 0L), last);
      sum += weight * static_cast<double>(input[static_cast<size_t>(clamped)]);
      weight_sum += weight;
    }

    // Normalizing by the realized window keeps unity gain even where the taps
    // were clamped, so the edges do not come out quieter than the middle.
    output[index] = static_cast<float>(weight_sum != 0.0 ? sum / weight_sum : 0.0);
  }

  return output;
}

}  // namespace bitchord::smart
