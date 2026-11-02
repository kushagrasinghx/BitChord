// From AutoMix (https://github.com/heydavidf/automix), Copyright (C) 2026 David Finnegan.
// AGPL-3.0-only: see ./LICENSE and ./NOTICE.md in playback/automix.

/** Per-window RMS energy curve (normalized to peak = 1) + intro/outro detection. */

export function energyCurve(samples: Float32Array, sampleRate: number, windowSec: number): number[] {
  const windowSamples = Math.max(1, Math.round(windowSec * sampleRate));
  const curve: number[] = [];
  for (let start = 0; start < samples.length; start += windowSamples) {
    const end = Math.min(samples.length, start + windowSamples);
    let acc = 0;
    for (let i = start; i < end; i++) acc += samples[i]! * samples[i]!;
    curve.push(Math.sqrt(acc / (end - start)));
  }
  const peak = Math.max(...curve, 1e-9);
  return curve.map((v) => v / peak);
}

export interface IntroOutro {
  introEndSec: number;
  outroStartSec: number;
}

/**
 * Intro = leading low-energy span, outro = trailing low-energy span.
 * "Full energy" = sustained >= 60% of peak for two consecutive windows.
 */
export function detectIntroOutro(curve: number[], windowSec: number, durationSec: number): IntroOutro {
  const THRESH = 0.6;
  let introEnd = 0;
  for (let i = 0; i < curve.length - 1; i++) {
    if (curve[i]! >= THRESH && curve[i + 1]! >= THRESH) {
      introEnd = i * windowSec;
      break;
    }
  }
  let outroStart = durationSec;
  for (let i = curve.length - 2; i >= 0; i--) {
    if (curve[i]! >= THRESH && curve[i + 1]! >= THRESH) {
      outroStart = Math.min(durationSec, (i + 2) * windowSec);
      break;
    }
  }
  // sanity clamps: intro within first third, outro within last half
  introEnd = Math.min(introEnd, durationSec / 3);
  outroStart = Math.max(outroStart, durationSec / 2);
  return { introEndSec: introEnd, outroStartSec: outroStart };
}
