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

// Sample-rate conversion for the DSP analyzer's front end.
//
// The analyzer accepts exactly kAnalysisSampleRate (see audio_analysis.h /
// TrackFeatures.kt) and refuses anything else, so something has to convert;
// streams arrive at 44.1 or 48 kHz. Doing it by dropping samples would fold
// everything above the analyzer's Nyquist back down into the band it reads,
// and an aliased spectrum produces a wrong tempo/key/structure reading rather
// than a noisy one -- the transition planner would then take that reading at
// face value. So this is a windowed-sinc resampler rather than decimation:
// the anti-alias filter is the point of it.
//
// Calls borrow the input only until they return, own the returned storage,
// and are reentrant. The work is O(taps * output) and allocates, so it
// belongs on a worker thread alongside the rest of analysis.

#pragma once

#include <cstddef>
#include <vector>

namespace bitchord::smart {

// Zero crossings kept either side of each output sample. Higher is a better
// stopband at linear cost; 32 puts the aliasing well below the noise floor of
// anything that reaches this code as lossy audio.
inline constexpr size_t kResamplerZeroCrossings = 32;

/**
 * Resamples contiguous mono float PCM from `input_rate` to `output_rate`.
 *
 * Returns the input unchanged when the rates already match, and an empty
 * vector when either rate is not positive or the input is empty. There is no
 * error channel: callers treat empty as "no analysis available", which is
 * what every other stage of this pipeline does.
 */
std::vector<float> Resample(
  const std::vector<float>& input,
  double input_rate,
  double output_rate
);

}  // namespace bitchord::smart
