// BitChord: the Automix loop mix's renderer. See loop_mix_renderer.h.
//
// Copyright (C) 2026 Kushagra Singh. GPL-3.0-or-later, with the rest of BitChord.
#include "loopmix/loop_mix_renderer.h"

#include <algorithm>
#include <cmath>

namespace bitchord::loopmix {

namespace {

/** Filter coefficients are recomputed at most this often; gains ramp per sample. */
constexpr int kControlFrames = 64;
/** The incoming deck's last fade, to its own player: identical audio crossed this fast is heard as one. */
constexpr double kFadeSeconds = 0.025;
constexpr double kPi = 3.14159265358979323846;

}  // namespace

float Envelope::at(double time) const {
  if (times.empty()) return 0;
  if (time <= times.front()) return values.front();
  if (time >= times.back()) return values.back();
  // Envelopes are a handful of points; a linear scan beats a search's branches.
  size_t i = 1;
  while (i < times.size() && times[i] < time) ++i;
  const double t0 = times[i - 1], t1 = times[i];
  const double f = t1 > t0 ? (time - t0) / (t1 - t0) : 1.0;
  const double v0 = values[i - 1], v1 = values[i];
  if (exponential && v0 > 0 && v1 > 0) return float(v0 * std::pow(v1 / v0, f));
  return float(v0 + (v1 - v0) * f);
}

void Biquad::lowShelf(double sampleRate, double frequency, double gainDb) {
  // RBJ cookbook, shelf slope 1: the Web Audio lowshelf the web player's EQ uses.
  const double a = std::pow(10.0, gainDb / 40.0);
  const double w0 = 2 * kPi * frequency / sampleRate;
  const double cosw = std::cos(w0), sinw = std::sin(w0);
  const double alpha = sinw / 2 * std::sqrt(2.0);
  const double sq = 2 * std::sqrt(a) * alpha;
  const double a0 = (a + 1) + (a - 1) * cosw + sq;
  b0 = float(a * ((a + 1) - (a - 1) * cosw + sq) / a0);
  b1 = float(2 * a * ((a - 1) - (a + 1) * cosw) / a0);
  b2 = float(a * ((a + 1) - (a - 1) * cosw - sq) / a0);
  a1 = float(-2 * ((a - 1) + (a + 1) * cosw) / a0);
  a2 = float(((a + 1) + (a - 1) * cosw - sq) / a0);
}

void Biquad::highPass(double sampleRate, double frequency, double q) {
  const double w0 = 2 * kPi * frequency / sampleRate;
  const double cosw = std::cos(w0), sinw = std::sin(w0);
  const double alpha = sinw / (2 * q);
  const double a0 = 1 + alpha;
  b0 = float((1 + cosw) / 2 / a0);
  b1 = float(-(1 + cosw) / a0);
  b2 = b0;
  a1 = float(-2 * cosw / a0);
  a2 = float((1 - alpha) / a0);
}

int64_t Deck::sampleAt(int64_t unwrapped) const {
  int64_t index = unwrapped;
  const int64_t length = loopEnd - loopStart;
  if (length > 0 && unwrapped >= loopEnd) {
    if (unwrapped < int64_t(releaseAt)) {
      index = loopStart + (unwrapped - loopStart) % length;
    } else {
      // Let go: the song carries on from the loop's end.
      index = loopEnd + (unwrapped - int64_t(releaseAt));
    }
  }
  return (index >= 0 && index < int64_t(left.size())) ? index : -1;
}

double Deck::trackSecondsAt(double unwrapped, double sampleRate) const {
  double position = unwrapped;
  const double length = double(loopEnd - loopStart);
  if (length > 0 && unwrapped >= double(loopEnd)) {
    if (unwrapped < releaseAt) {
      position = loopStart + std::fmod(unwrapped - loopStart, length);
    } else {
      position = loopEnd + (unwrapped - releaseAt);
    }
  }
  return regionStart + position / sampleRate;
}

double Deck::releasedPositionOf(double seconds, double sampleRate) const {
  return releaseAt + ((seconds - regionStart) * sampleRate - double(loopEnd));
}

double Deck::knotHeard(double t) const {
  if (knotTimes.empty()) return 0;
  if (t <= knotTimes.front()) {
    return knotPositions.front() + (t - knotTimes.front()) * knotRate(knotTimes.front());
  }
  if (t >= knotTimes.back()) return knotPositions.back();
  size_t i = std::upper_bound(knotTimes.begin(), knotTimes.end(), t) - knotTimes.begin();
  const double f = (t - knotTimes[i - 1]) / (knotTimes[i] - knotTimes[i - 1]);
  return knotPositions[i - 1] + (knotPositions[i] - knotPositions[i - 1]) * f;
}

double Deck::knotRate(double t) const {
  if (knotTimes.size() < 2) return 1;
  size_t i = std::upper_bound(knotTimes.begin(), knotTimes.end(), t) - knotTimes.begin();
  i = std::clamp<size_t>(i, 1, knotTimes.size() - 1);
  return (knotPositions[i] - knotPositions[i - 1]) / (knotTimes[i] - knotTimes[i - 1]);
}

Renderer::Renderer(int sampleRate) : sampleRate_(sampleRate) {}

void Renderer::prepare() {
  for (Deck& deck : decks_) {
    // The cheaper preset with its computation spread evenly: the mix is
    // written well ahead of the speaker, so latency costs nothing, and an even
    // load keeps the playback thread's blocks short.
    deck.stretch.presetCheaper(2, float(sampleRate_), true);
    deck.started = false;
    deck.finished = deck.left.empty() || deck.knotTimes.size() < 2;
    deck.shelf.bypass();
    deck.highPass.bypass();
    deck.shelfDb = 0;
    deck.highPassFreq = 0;
  }
}

int64_t Renderer::firstFrame() const {
  double first = 0;
  for (const Deck& deck : decks_) {
    if (deck.knotTimes.empty()) continue;
    const double latency = deck.stretch.outputLatency() / double(sampleRate_);
    first = std::min(first, deck.knotTimes.front() / sampleRate_ - deck.preroll - latency);
  }
  return int64_t(std::floor(first * sampleRate_));
}

void Renderer::render(int64_t frame, float* interleaved, int frames) {
  if (int(mixLeft_.size()) < frames) {
    mixLeft_.resize(frames);
    mixRight_.resize(frames);
  }
  std::fill(mixLeft_.begin(), mixLeft_.begin() + frames, 0.f);
  std::fill(mixRight_.begin(), mixRight_.begin() + frames, 0.f);
  for (Deck& deck : decks_) renderDeck(deck, frame, frames);
  for (int i = 0; i < frames; ++i) {
    interleaved[2 * i] = mixLeft_[i];
    interleaved[2 * i + 1] = mixRight_[i];
  }
}

void Renderer::renderDeck(Deck& deck, int64_t frame, int frames) {
  if (deck.finished) return;
  const double sr = sampleRate_;
  const double blockStart = frame / sr;
  const double blockEnd = (frame + frames) / sr;
  if (blockStart >= deck.stopAt) {
    deck.finished = true;
    return;
  }
  const double inLatency = deck.stretch.inputLatency();
  const double outLatency = deck.stretch.outputLatency();
  const double startTime = deck.knotTimes.front() / sr - deck.preroll - outLatency / sr;
  const double frameStart = double(frame);
  const double frameEnd = double(frame + frames);
  if (!deck.started) {
    if (blockEnd <= startTime) return;
    // Seeded at this block's start, its read pointer the stretch's own latency
    // ahead of what will be heard: the first outLatency samples are pre-roll,
    // over by the time the deck's gain opens.
    const double rate = deck.knotRate(frameStart);
    deck.heardPos = deck.knotHeard(frameStart);
    deck.readPos = deck.heardPos + inLatency + rate * outLatency;
    const int seekLength = deck.stretch.seekLength();
    if (int(inLeft_.size()) < seekLength) {
      inLeft_.resize(seekLength);
      inRight_.resize(seekLength);
    }
    const int64_t first = int64_t(std::llround(deck.readPos)) - seekLength;
    for (int i = 0; i < seekLength; ++i) {
      const int64_t index = deck.sampleAt(first + i);
      inLeft_[i] = index >= 0 ? deck.left[index] : 0.f;
      inRight_[i] = index >= 0 ? deck.right[index] : 0.f;
    }
    float* seekInput[2] = {inLeft_.data(), inRight_.data()};
    deck.stretch.reset();
    deck.stretch.seek(seekInput, seekLength, rate);
    deck.readPos = double(first + seekLength);
    deck.started = true;
  }

  // Where the block must end, heard: on the knots, or (past the last one, the
  // incoming song running on) at rate 1.
  double heardEnd;
  double rateEnd;
  const double lastKnot = deck.knotTimes.back();
  if (frameEnd <= lastKnot) {
    heardEnd = deck.knotHeard(frameEnd);
    rateEnd = deck.knotRate(frameEnd - 1);
  } else if (deck.runOn) {
    const double from = std::max(frameStart, lastKnot);
    const double base = frameStart < lastKnot ? deck.knotPositions.back() : deck.heardPos;
    heardEnd = base + (frameEnd - from);
    rateEnd = 1;
  } else {
    heardEnd = deck.knotPositions.back();
    rateEnd = deck.knotRate(lastKnot - 1);
  }
  const double readEnd = heardEnd + inLatency + rateEnd * outLatency;
  int inputSamples = int(std::floor(readEnd - deck.readPos));
  inputSamples = std::clamp(inputSamples, 0, frames * 8);
  if (int(inLeft_.size()) < inputSamples) {
    inLeft_.resize(inputSamples);
    inRight_.resize(inputSamples);
  }
  const int64_t readFrom = int64_t(deck.readPos);
  for (int i = 0; i < inputSamples; ++i) {
    const int64_t index = deck.sampleAt(readFrom + i);
    inLeft_[i] = index >= 0 ? deck.left[index] : 0.f;
    inRight_[i] = index >= 0 ? deck.right[index] : 0.f;
  }
  deck.readPos += inputSamples;

  if (int(outLeft_.size()) < frames) {
    outLeft_.resize(frames);
    outRight_.resize(frames);
  }
  float* input[2] = {inLeft_.data(), inRight_.data()};
  float* output[2] = {outLeft_.data(), outRight_.data()};
  deck.stretch.process(input, inputSamples, output, frames);

  const double heardStart = deck.heardPos;
  deck.heardPos = heardEnd;
  deck.heard.store(deck.trackSecondsAt(heardEnd, sr), std::memory_order_relaxed);

  // Gain, bass shelf and high-pass, in control blocks; the gain ramps per sample.
  const double fadeAt = deck.fadeOutAt.load(std::memory_order_relaxed);
  const double fadeSamples = kFadeSeconds * sr;
  for (int at = 0; at < frames; at += kControlFrames) {
    const int count = std::min(kControlFrames, frames - at);
    const double t0 = (frame + at) / sr;
    const double t1 = (frame + at + count) / sr;
    if (!deck.lowDb.empty()) {
      const float db = deck.lowDb.at((t0 + t1) / 2);
      if (std::abs(db - deck.shelfDb) > 0.01f) {
        deck.shelfDb = db;
        if (std::abs(db) < 0.01f) {
          deck.shelf.bypass();
        } else {
          deck.shelf.lowShelf(sr, 250, db);
        }
      }
    }
    if (!deck.highPassHz.empty()) {
      const float hz = deck.highPassHz.at((t0 + t1) / 2);
      if (std::abs(hz - deck.highPassFreq) > 0.5f) {
        deck.highPassFreq = hz;
        if (hz <= 20.5f) {
          deck.highPass.bypass();
        } else {
          deck.highPass.highPass(sr, hz, 0.7);
        }
      }
    }
    const float g0 = deck.gain.empty() ? 1.f : deck.gain.at(t0);
    const float g1 = deck.gain.empty() ? 1.f : deck.gain.at(t1);
    for (int i = 0; i < count; ++i) {
      const int n = at + i;
      float g = (g0 + (g1 - g0) * (float(i) / count)) * deck.level;
      if (fadeAt >= 0) {
        const double heard = heardStart + (heardEnd - heardStart) * (double(n) / frames);
        const double fade = std::clamp((fadeAt + fadeSamples - heard) / fadeSamples, 0.0, 1.0);
        g *= float(fade);
      }
      float l = deck.shelf.tick(0, outLeft_[n]);
      float r = deck.shelf.tick(1, outRight_[n]);
      l = deck.highPass.tick(0, l);
      r = deck.highPass.tick(1, r);
      mixLeft_[n] += l * g;
      mixRight_[n] += r * g;
    }
  }
  if (fadeAt >= 0 && heardStart > fadeAt + fadeSamples) deck.finished = true;
}

}  // namespace bitchord::loopmix
